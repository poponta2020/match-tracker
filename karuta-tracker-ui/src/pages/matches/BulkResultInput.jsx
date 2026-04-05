import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { isAdmin, isSuperAdmin } from '../../utils/auth';
import { Save, AlertCircle, Pencil, X, BookOpen, User, Eye, UsersRound, MoreHorizontal } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const BulkResultInput = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();

  const [session, setSession] = useState(null);
  const [pairings, setPairings] = useState([]);
  const [matches, setMatches] = useState([]);
  const [currentMatchNumber, setCurrentMatchNumber] = useState(1);
  const [results, setResults] = useState({});
  const [changedMatches, setChangedMatches] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [showWarningDialog, setShowWarningDialog] = useState(false);
  const [missingScoreDiffs, setMissingScoreDiffs] = useState([]);

  // 抜け番活動
  const [byeActivities, setByeActivities] = useState({}); // `${matchNumber}-${playerId}` -> { activityType, freeText, id? }
  const [byePlayers, setByePlayers] = useState({}); // matchNumber -> [{ id, name }]

  // 対戦変更モード
  const [editMode, setEditMode] = useState(false);
  const [participants, setParticipants] = useState([]);
  const [selectingPairing, setSelectingPairing] = useState(null); // { pairingId, side: 'player1'|'player2' }
  const [updatingPairing, setUpdatingPairing] = useState(false);

  const ACTIVITY_TYPES = [
    { value: 'READING', label: '読み' },
    { value: 'SOLO_PICK', label: '一人取り' },
    { value: 'OBSERVING', label: '見学' },
    { value: 'ASSIST_OBSERVING', label: '見学対応' },
    { value: 'OTHER', label: 'その他' },
    { value: 'ABSENT', label: '休み' },
  ];

  // 抜け番選手の算出（共通関数）
  const computeByePlayers = (sessionData, allPairings, allParticipants) => {
    const totalMatches = sessionData?.totalMatches || 0;
    const result = {};
    for (let num = 1; num <= totalMatches; num++) {
      const matchPairings = allPairings.filter(p => p.matchNumber === num);
      const pairedIds = new Set();
      matchPairings.forEach(p => { pairedIds.add(p.player1Id); pairedIds.add(p.player2Id); });
      const matchPartEntries = sessionData.matchParticipants?.[String(num)] || [];
      const matchPartNames = matchPartEntries.map(p => typeof p === 'string' ? p : p.name);
      const bye = allParticipants
        .filter(p => matchPartNames.includes(p.name) && !pairedIds.has(p.id));
      if (bye.length > 0) {
        result[num] = bye.map(p => ({ id: p.id, name: p.name }));
      }
    }
    return result;
  };

  // 権限チェック
  useEffect(() => {
    if (!isAdmin() && !isSuperAdmin()) {
      alert('この機能は管理者のみ利用できます');
      navigate('/');
    }
  }, [navigate]);

  // データ取得
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);

        // 練習セッション情報取得
        const sessionResponse = await practiceAPI.getById(sessionId);
        const sessionData = sessionResponse.data;
        setSession(sessionData);

        // 対戦ペアリングと既存試合結果と参加者と抜け番活動を並列取得
        const [pairingsResponse, matchesResponse, participantsResponse, byeActivitiesResponse] = await Promise.all([
          pairingAPI.getByDate(sessionData.sessionDate, { light: true }),
          matchAPI.getByDate(sessionData.sessionDate),
          practiceAPI.getParticipants(sessionId),
          byeActivityAPI.getByDate(sessionData.sessionDate).catch(() => ({ data: [] })),
        ]);
        const allPairings = pairingsResponse.data || [];
        setPairings(allPairings);
        const allParticipants = participantsResponse.data || [];
        setParticipants(allParticipants);
        const sessionMatches = matchesResponse.data;
        setMatches(sessionMatches);

        // 既存結果を初期値として設定
        const initialResults = {};
        sessionMatches.forEach(match => {
          const key = `${match.matchNumber}-${match.player1Id}-${match.player2Id}`;
          initialResults[key] = {
            winnerId: match.winnerId,
            scoreDifference: match.scoreDifference,
            matchId: match.id,
          };
        });
        setResults(initialResults);

        // 抜け番選手を算出（各試合番号ごと）
        const newByePlayers = computeByePlayers(sessionData, allPairings, allParticipants);
        setByePlayers(newByePlayers);

        // 既存の抜け番活動を初期値として設定
        const initialBye = {};
        (byeActivitiesResponse.data || []).forEach(a => {
          initialBye[`${a.matchNumber}-${a.playerId}`] = {
            activityType: a.activityType,
            freeText: a.freeText || '',
            id: a.id,
          };
        });
        setByeActivities(initialBye);

      } catch (err) {
        console.error('データ取得エラー:', err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    if (sessionId) {
      fetchData();
    }
  }, [sessionId]);

  // 試合番号ごとのペアリングを取得
  const getPairingsForMatch = (matchNumber) => {
    return pairings.filter(p => p.matchNumber === matchNumber);
  };

  // 結果のキーを生成
  // 注: バックエンドは保存時に player1Id < player2Id を強制するため、
  //     フロントエンドでも同じ順序でキーを生成する必要がある
  const getResultKey = (matchNumber, player1Id, player2Id) => {
    const [smallerId, largerId] = player1Id < player2Id
      ? [player1Id, player2Id]
      : [player2Id, player1Id];
    return `${matchNumber}-${smallerId}-${largerId}`;
  };

  // 勝者を設定
  const setWinner = (matchNumber, player1Id, player2Id, winnerId) => {
    const key = getResultKey(matchNumber, player1Id, player2Id);
    setResults(prev => ({
      ...prev,
      [key]: {
        ...prev[key],
        winnerId,
        scoreDifference: prev[key]?.scoreDifference ?? null,
      }
    }));
    setChangedMatches(prev => new Set([...prev, key]));
  };

  // 枚数差を設定
  const setScoreDifference = (matchNumber, player1Id, player2Id, scoreDifference) => {
    const key = getResultKey(matchNumber, player1Id, player2Id);
    setResults(prev => ({
      ...prev,
      [key]: {
        ...prev[key],
        scoreDifference: parseInt(scoreDifference),
      }
    }));
    setChangedMatches(prev => new Set([...prev, key]));
  };

  // 結果を取得
  const getResult = (matchNumber, player1Id, player2Id) => {
    const key = getResultKey(matchNumber, player1Id, player2Id);
    return results[key] || { winnerId: null, scoreDifference: null };
  };

  // 試合が入力済みかチェック
  const isMatchCompleted = (matchNumber) => {
    const matchPairings = getPairingsForMatch(matchNumber);
    return matchPairings.every(pairing => {
      const result = getResult(matchNumber, pairing.player1Id, pairing.player2Id);
      return result.winnerId !== null && result.scoreDifference !== null;
    });
  };

  // 枚数差未選択チェック
  const checkMissingScoreDiffs = () => {
    const missing = [];
    changedMatches.forEach(key => {
      if (key.startsWith('bye-')) return; // 抜け番キーはスキップ
      const result = results[key];
      if (result.winnerId !== null && result.scoreDifference === null) {
        const [matchNumber, player1Id, player2Id] = key.split('-').map(Number);
        const pairing = pairings.find(
          p => p.matchNumber === matchNumber &&
               p.player1Id === player1Id &&
               p.player2Id === player2Id
        );
        if (pairing) {
          missing.push({
            matchNumber,
            player1Name: pairing.player1Name,
            player2Name: pairing.player2Name,
            key,
          });
        }
      }
    });
    return missing;
  };

  // 抜け番活動を設定
  const setByeActivityType = (matchNumber, playerId, activityType) => {
    const key = `${matchNumber}-${playerId}`;
    setByeActivities(prev => ({
      ...prev,
      [key]: { ...prev[key], activityType, freeText: activityType !== 'OTHER' ? '' : (prev[key]?.freeText || '') },
    }));
    setChangedMatches(prev => new Set([...prev, `bye-${key}`]));
  };

  const setByeFreeText = (matchNumber, playerId, freeText) => {
    const key = `${matchNumber}-${playerId}`;
    setByeActivities(prev => ({
      ...prev,
      [key]: { ...prev[key], freeText },
    }));
    setChangedMatches(prev => new Set([...prev, `bye-${key}`]));
  };

  // 対戦相手変更処理
  const handlePlayerChange = async (pairing, side, newPlayerId) => {
    try {
      setUpdatingPairing(true);
      const response = await pairingAPI.updatePlayer(pairing.id, newPlayerId, side);

      // ペアリングリストを更新
      setPairings(prev => prev.map(p =>
        p.id === pairing.id ? response.data : p
      ));

      // 旧ペアの結果キーを削除
      const oldKey = getResultKey(pairing.matchNumber, pairing.player1Id, pairing.player2Id);
      setResults(prev => {
        const next = { ...prev };
        delete next[oldKey];
        return next;
      });
      setChangedMatches(prev => {
        const next = new Set(prev);
        next.delete(oldKey);
        return next;
      });

      setSelectingPairing(null);

      // 抜け番を再計算（対戦変更でペア構成が変わるため）
      const updatedPairings = pairings.map(p => p.id === pairing.id ? response.data : p);
      const newByePlayers = computeByePlayers(session, updatedPairings, participants);
      setByePlayers(newByePlayers);
    } catch (err) {
      console.error('対戦相手変更エラー:', err);
      setError(err.response?.data?.message || '対戦相手の変更に失敗しました');
    } finally {
      setUpdatingPairing(false);
    }
  };

  // 選手選択リストに表示する参加者（現在のペアリングで既に使われている選手を除外）
  const getAvailablePlayers = (pairing, side) => {
    const currentMatchPairings = getPairingsForMatch(currentMatchNumber);
    const usedPlayerIds = new Set();
    currentMatchPairings.forEach(p => {
      // 編集対象のペアリングの、編集対象側は除外しない
      if (p.id === pairing.id) {
        if (side === 'player1') {
          usedPlayerIds.add(p.player2Id);
        } else {
          usedPlayerIds.add(p.player1Id);
        }
      } else {
        usedPlayerIds.add(p.player1Id);
        usedPlayerIds.add(p.player2Id);
      }
    });

    return participants.filter(p => !usedPlayerIds.has(p.id));
  };

  // 保存処理
  const handleSave = async (forceZeroScore = false) => {
    try {
      // 枚数差未選択チェック
      if (!forceZeroScore) {
        const missing = checkMissingScoreDiffs();
        if (missing.length > 0) {
          setMissingScoreDiffs(missing);
          setShowWarningDialog(true);
          return;
        }
      }

      setSaving(true);
      setError(null);

      const savePromises = [];
      const saveKeys = []; // 各promiseに対応するキーを記録

      for (const key of changedMatches) {
        if (key.startsWith('bye-')) continue; // 抜け番キーは後続のループで処理
        const result = results[key];
        if (!result.winnerId) continue; // 勝者未選択はスキップ

        const [matchNumber, player1Id, player2Id] = key.split('-').map(Number);
        const scoreDiff = result.scoreDifference ?? 0; // 未選択は0枚

        // ペアリング情報から対戦相手名を取得
        // 注: キーは player1Id < player2Id の順序だが、ペアリングは元の順序のため、
        //     両方のパターンで検索する
        const pairing = pairings.find(
          p => p.matchNumber === matchNumber &&
               ((p.player1Id === player1Id && p.player2Id === player2Id) ||
                (p.player1Id === player2Id && p.player2Id === player1Id))
        );

        if (!pairing) {
          console.error('ペアリング情報が見つかりません:', key);
          continue;
        }

        // 詳細版APIのデータ構造で送信
        const matchData = {
          matchDate: session.sessionDate,
          matchNumber,
          player1Id,
          player2Id,
          winnerId: result.winnerId,
          scoreDifference: scoreDiff,
          createdBy: currentPlayer.id,
        };

        if (result.matchId) {
          // 更新（詳細版）
          savePromises.push(
            matchAPI.updateDetailed(
              result.matchId,
              result.winnerId,
              scoreDiff,
              currentPlayer.id
            )
          );
        } else {
          // 新規作成（詳細版） - バックエンドがupsertするため重複しない
          savePromises.push(matchAPI.createDetailed(matchData));
        }
        saveKeys.push(key);
      }

      // 抜け番活動の保存
      const byePromises = [];
      const byeMatchNumbers = new Set();
      for (const key of changedMatches) {
        if (!key.startsWith('bye-')) continue;
        const byeKey = key.replace('bye-', '');
        const [mn, pid] = byeKey.split('-').map(Number);
        const activity = byeActivities[byeKey];
        if (activity?.activityType) {
          byeMatchNumbers.add(mn);
        }
      }
      // 一括保存（試合番号ごとにbatch API呼び出し）
      for (const mn of byeMatchNumbers) {
        const items = [];
        const matchByePlayers = byePlayers[mn] || [];
        matchByePlayers.forEach(p => {
          const activity = byeActivities[`${mn}-${p.id}`];
          if (activity?.activityType) {
            items.push({
              playerId: p.id,
              activityType: activity.activityType,
              freeText: activity.activityType === 'OTHER' ? activity.freeText : null,
            });
          }
        });
        if (items.length > 0) {
          byePromises.push(byeActivityAPI.createBatch(session.sessionDate, mn, items));
        }
      }

      const allPromises = await Promise.all([...savePromises, ...byePromises]);

      // 保存結果からmatchIdをstateに反映（ブラウザバック時の重複防止）
      const matchResponses = allPromises.slice(0, saveKeys.length);
      setResults(prev => {
        const updated = { ...prev };
        matchResponses.forEach((res, i) => {
          const key = saveKeys[i];
          if (res?.data?.id && updated[key]) {
            updated[key] = { ...updated[key], matchId: res.data.id };
          }
        });
        return updated;
      });
      setChangedMatches(new Set());

      // 保存成功後、試合結果詳細画面に遷移
      navigate(`/matches/results/${sessionId}`);

    } catch (err) {
      console.error('保存エラー:', err);
      setError(err.response?.data?.message || '保存に失敗しました');
      setSaving(false);
      setShowWarningDialog(false);
    }
  };

  if (loading) {
    return <LoadingScreen />;
  }

  if (error) {
    return (
      <div className="min-h-screen bg-[#f2ede6] flex items-center justify-center p-4">
        <div className="bg-red-50 border border-red-200 rounded-lg p-6 max-w-md">
          <div className="flex items-center gap-2 text-red-800 mb-2">
            <AlertCircle className="h-5 w-5" />
            <h2 className="font-semibold">エラー</h2>
          </div>
          <p className="text-red-700">{error}</p>
          <button
            onClick={() => navigate('/')}
            className="mt-4 px-4 py-2 bg-[#82655a] text-white rounded-lg hover:bg-[#6b5048]"
          >
            ホームに戻る
          </button>
        </div>
      </div>
    );
  }

  const currentPairings = getPairingsForMatch(currentMatchNumber);
  const totalMatches = session?.totalMatches || 0;

  return (
    <div className="min-h-screen bg-[#f2ede6] pb-20">
      {/* 固定ナビゲーションバー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
        <div className="max-w-7xl mx-auto">
          {/* 日付表示 + 対戦変更ボタン */}
          <div className="flex items-center justify-between py-3">
            <div className="w-10" />
            <span className="text-lg font-semibold text-white">
              {session && new Date(session.sessionDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                weekday: 'short'
              })}
            </span>
            <button
              onClick={() => {
                setEditMode(prev => !prev);
                setSelectingPairing(null);
              }}
              className={`flex items-center gap-1 px-2 py-1 rounded text-xs font-medium transition-colors ${
                editMode
                  ? 'bg-white text-[#4a6b5a]'
                  : 'bg-white/20 text-white hover:bg-white/30'
              }`}
            >
              {editMode ? <X className="w-3 h-3" /> : <Pencil className="w-3 h-3" />}
              {editMode ? '完了' : '対戦変更'}
            </button>
          </div>

          {/* タブバー */}
          {totalMatches > 0 && (
            <div className="flex overflow-x-auto -mb-px">
              {Array.from({ length: totalMatches }, (_, i) => i + 1).map(num => (
                <button
                  key={num}
                  onClick={() => {
                    setCurrentMatchNumber(num);
                    setSelectingPairing(null);
                  }}
                  className={`flex-shrink-0 px-4 py-2 text-sm font-medium transition-colors border-b-2 ${
                    currentMatchNumber === num
                      ? 'border-white text-white'
                      : 'border-transparent text-white/60 hover:text-white hover:border-white/50'
                  }`}
                >
                  {num}試合目{isMatchCompleted(num) ? ' ✓' : ''}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* メインコンテンツ */}
      <div className="max-w-4xl mx-auto px-6 pt-24 pb-6">
        <p className="text-xs text-[#9b8a7e] mb-3">
          {editMode ? '変更したい選手名をタップしてください' : '勝者の名前をタップ → 枚数差を選択'}
        </p>
        <div className="divide-y divide-[#e2d9d0]">
          {currentPairings.map((pairing, index) => {
            const result = getResult(currentMatchNumber, pairing.player1Id, pairing.player2Id);
            const isPlayer1Winner = result.winnerId === pairing.player1Id;
            const isPlayer2Winner = result.winnerId === pairing.player2Id;
            const hasWinner = result.winnerId !== null;
            const isSelectingPlayer1 = selectingPairing?.pairingId === pairing.id && selectingPairing?.side === 'player1';
            const isSelectingPlayer2 = selectingPairing?.pairingId === pairing.id && selectingPairing?.side === 'player2';

            return (
              <div key={pairing.id || index} className="py-4">
                <div className="flex items-center text-lg">
                  {/* 選手1 */}
                  <button
                    type="button"
                    onClick={() => {
                      if (editMode) {
                        setSelectingPairing(
                          isSelectingPlayer1 ? null : { pairingId: pairing.id, side: 'player1' }
                        );
                      } else {
                        setWinner(currentMatchNumber, pairing.player1Id, pairing.player2Id, pairing.player1Id);
                      }
                    }}
                    className={`flex-1 text-right pr-2 font-semibold truncate transition-colors ${
                      editMode
                        ? isSelectingPlayer1
                          ? 'text-[#5f3a2d] underline decoration-2'
                          : 'text-[#5f3a2d]'
                        : isPlayer1Winner ? 'text-green-600' : isPlayer2Winner ? 'text-gray-400' : 'text-gray-700'
                    }`}
                  >
                    {pairing.player1Name}
                  </button>

                  {/* 中央: 勝敗マーク + 枚数差 or vs */}
                  {!editMode && hasWinner ? (
                    <>
                      <div className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${isPlayer1Winner ? 'text-green-600' : 'text-red-600'}`}>
                        {isPlayer1Winner ? '〇' : '×'}
                      </div>
                      <select
                        value={result.scoreDifference ?? ''}
                        onChange={(e) => setScoreDifference(
                          currentMatchNumber,
                          pairing.player1Id,
                          pairing.player2Id,
                          e.target.value
                        )}
                        className="w-14 text-center font-bold text-gray-900 bg-transparent border-0 border-b border-[#d0c5b8] focus:ring-0 focus:border-[#82655a] flex-shrink-0 px-0 py-0 text-base"
                      >
                        <option value="">-</option>
                        {Array.from({ length: 26 }, (_, i) => i).map(num => (
                          <option key={num} value={num}>{num}</option>
                        ))}
                      </select>
                      <div className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${isPlayer2Winner ? 'text-green-600' : 'text-red-600'}`}>
                        {isPlayer2Winner ? '〇' : '×'}
                      </div>
                    </>
                  ) : (
                    <div className="text-sm font-medium text-[#b0a396] w-8 text-center flex-shrink-0">
                      vs
                    </div>
                  )}

                  {/* 選手2 */}
                  <button
                    type="button"
                    onClick={() => {
                      if (editMode) {
                        setSelectingPairing(
                          isSelectingPlayer2 ? null : { pairingId: pairing.id, side: 'player2' }
                        );
                      } else {
                        setWinner(currentMatchNumber, pairing.player1Id, pairing.player2Id, pairing.player2Id);
                      }
                    }}
                    className={`flex-1 text-left pl-2 font-semibold truncate transition-colors ${
                      editMode
                        ? isSelectingPlayer2
                          ? 'text-[#5f3a2d] underline decoration-2'
                          : 'text-[#5f3a2d]'
                        : isPlayer2Winner ? 'text-green-600' : isPlayer1Winner ? 'text-gray-400' : 'text-gray-700'
                    }`}
                  >
                    {pairing.player2Name}
                  </button>
                </div>

                {/* 選手選択リスト */}
                {(isSelectingPlayer1 || isSelectingPlayer2) && (
                  <div className="mt-3 bg-white rounded-lg border border-[#d0c5b8] shadow-sm max-h-48 overflow-y-auto">
                    {getAvailablePlayers(pairing, selectingPairing.side).length === 0 ? (
                      <p className="px-4 py-3 text-sm text-gray-500">選択可能な選手がいません</p>
                    ) : (
                      getAvailablePlayers(pairing, selectingPairing.side).map(player => (
                        <button
                          key={player.id}
                          onClick={() => handlePlayerChange(pairing, selectingPairing.side, player.id)}
                          disabled={updatingPairing}
                          className="w-full text-left px-4 py-3 text-sm hover:bg-[#f0ebe3] transition-colors border-b border-[#f0ebe3] last:border-b-0 disabled:opacity-50"
                        >
                          {player.name}
                        </button>
                      ))
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* 抜け番セクション */}
        {(byePlayers[currentMatchNumber] || []).length > 0 && !editMode && (
          <div className="mt-6 pt-4 border-t border-[#e2d9d0]">
            <p className="text-xs font-medium text-[#9b8a7e] mb-3">抜け番</p>
            <div className="space-y-3">
              {byePlayers[currentMatchNumber].map(player => {
                const byeKey = `${currentMatchNumber}-${player.id}`;
                const activity = byeActivities[byeKey] || {};
                return (
                  <div key={player.id} className="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
                    <div className="flex items-center gap-3">
                      <span className="font-semibold text-[#374151] min-w-[4rem]">{player.name}</span>
                      <select
                        value={activity.activityType || ''}
                        onChange={(e) => setByeActivityType(currentMatchNumber, player.id, e.target.value)}
                        className="flex-1 text-sm bg-white border border-[#d0c5b8] rounded px-2 py-1.5 focus:ring-0 focus:border-[#82655a]"
                      >
                        <option value="">活動を選択</option>
                        {ACTIVITY_TYPES.map(t => (
                          <option key={t.value} value={t.value}>{t.label}</option>
                        ))}
                      </select>
                    </div>
                    {activity.activityType === 'OTHER' && (
                      <input
                        type="text"
                        value={activity.freeText || ''}
                        onChange={(e) => setByeFreeText(currentMatchNumber, player.id, e.target.value)}
                        placeholder="内容を入力..."
                        className="mt-2 w-full text-sm bg-white border border-[#d0c5b8] rounded px-2 py-1.5 focus:ring-0 focus:border-[#82655a]"
                      />
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>

      {/* 固定保存ボタン（変更がある場合のみ表示、編集モード中は非表示） */}
      {!editMode && changedMatches.size > 0 && (
        <div className="fixed left-0 right-0 z-40 px-4 py-3 bg-white border-t border-gray-200 shadow-lg" style={{ bottom: 'calc(3.5rem + env(safe-area-inset-bottom, 0px))' }}>
          <button
            onClick={() => handleSave(false)}
            disabled={saving}
            className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-[#1A3654] text-white rounded-lg hover:bg-[#122740] transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
          >
            <Save className="w-5 h-5" />
            {saving ? '保存中...' : `保存する（${changedMatches.size}件）`}
          </button>
        </div>
      )}

      {/* 枚数差未選択警告ダイアログ */}
      {showWarningDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg max-w-md w-full p-6">
            <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-yellow-600" />
              確認
            </h3>
            <p className="text-gray-700 mb-4">
              以下の対戦で枚数差が未選択です。
            </p>
            <ul className="list-disc list-inside text-gray-700 mb-4 space-y-1">
              {missingScoreDiffs.map((item, index) => (
                <li key={index}>
                  第{item.matchNumber}試合: {item.player1Name} vs {item.player2Name}
                </li>
              ))}
            </ul>
            <p className="text-gray-700 mb-6">
              0枚差として保存しますか？
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setShowWarningDialog(false)}
                className="flex-1 px-4 py-2 border border-gray-300 rounded-lg hover:bg-[#f0ebe3]"
              >
                キャンセル
              </button>
              <button
                onClick={() => handleSave(true)}
                className="flex-1 px-4 py-2 bg-[#82655a] text-white rounded-lg hover:bg-[#6b5048]"
              >
                0枚差で保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BulkResultInput;
