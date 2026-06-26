import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI } from '../../api';
import { useAuth } from '../../context/AuthContext';
import { Save, AlertCircle, Pencil } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import MatchCarousel from '../../components/MatchCarousel';
import { computeByePlayersByMatch } from './byePlayersLogic';
import { getCompletedMatchNumbers, defaultForBulkInput } from './defaultMatchNumber';
import { scrollActiveTabIntoView } from './tabScroll';

const BulkResultInput = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();

  const [session, setSession] = useState(null);
  const [pairings, setPairings] = useState([]);
  const [matches, setMatches] = useState([]);
  const [currentMatchNumber, setCurrentMatchNumber] = useState(1);
  const tabBarRef = useRef(null);
  const swipeAreaRef = useRef(null); // スワイプ検出面（共通ヘッダー/フッターを除くコンテンツ全域）
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

  const ACTIVITY_TYPES = [
    { value: 'READING', label: '読み' },
    { value: 'SOLO_PICK', label: '一人取り' },
    { value: 'OBSERVING', label: '見学' },
    { value: 'ASSIST_OBSERVING', label: '見学対応' },
    { value: 'OTHER', label: 'その他' },
    { value: 'ABSENT', label: '休み' },
  ];

  // 抜け番選手の算出（byePlayersLogic.js に切り出し済みの純粋関数を使用）
  const computeByePlayers = (sessionData, allPairings, allParticipants) =>
    computeByePlayersByMatch(sessionData, allPairings, allParticipants);

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
        const sessionMatches = matchesResponse.data;
        setMatches(sessionMatches);

        // 初期表示する試合番号を決定（入力済み最大+1 > 当日かつ会場スケジュールありで時刻ベース > 1試合目）。
        // fetchData は sessionId ごとに1回のみ実行されるため、以降のユーザーのタブ切替・スワイプは上書きしない。
        const completedMatchNumbers = getCompletedMatchNumbers({
          pairings: allPairings,
          matches: sessionMatches || [],
          totalMatches: sessionData.totalMatches,
        });
        setCurrentMatchNumber(
          defaultForBulkInput({
            completedMatchNumbers,
            totalMatches: sessionData.totalMatches,
            venueSchedules: sessionData.venueSchedules,
            sessionDate: sessionData.sessionDate,
            now: new Date(),
          })
        );

        // 既存結果を初期値として設定
        const initialResults = {};
        sessionMatches.forEach(match => {
          const key = `${match.matchNumber}-${match.player1Id}-${match.player2Id}`;
          initialResults[key] = {
            winnerId: match.winnerId,
            scoreDifference: match.scoreDifference,
            isLesson: match.isLesson === true,
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

  // 試合番号が変わったら、アクティブタブを画面内へ自動スクロール
  useEffect(() => {
    scrollActiveTabIntoView(tabBarRef.current);
  }, [currentMatchNumber]);

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

  // 枚数差を設定（"lesson" 選択時は指導試合フラグを立て枚数差を null にする）
  const setScoreDifference = (matchNumber, player1Id, player2Id, value) => {
    const key = getResultKey(matchNumber, player1Id, player2Id);
    setResults(prev => ({
      ...prev,
      [key]: value === 'lesson'
        ? { ...prev[key], isLesson: true, scoreDifference: null }
        : { ...prev[key], isLesson: false, scoreDifference: value === '' ? null : parseInt(value) }
    }));
    setChangedMatches(prev => new Set([...prev, key]));
  };

  // 結果を取得
  const getResult = (matchNumber, player1Id, player2Id) => {
    const key = getResultKey(matchNumber, player1Id, player2Id);
    return results[key] || { winnerId: null, scoreDifference: null, isLesson: false };
  };

  // 試合が入力済みかチェック
  const isMatchCompleted = (matchNumber) => {
    const matchPairings = getPairingsForMatch(matchNumber);
    return matchPairings.every(pairing => {
      const result = getResult(matchNumber, pairing.player1Id, pairing.player2Id);
      // 指導試合は枚数差を持たないため、勝者が決まっていれば完了扱い
      return result.winnerId !== null && (result.scoreDifference !== null || result.isLesson === true);
    });
  };

  // 枚数差未選択チェック
  const checkMissingScoreDiffs = () => {
    const missing = [];
    changedMatches.forEach(key => {
      if (key.startsWith('bye-')) return; // 抜け番キーはスキップ
      const result = results[key];
      if (result.winnerId !== null && result.scoreDifference === null && result.isLesson !== true) {
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

  // 対戦変更（組み合わせ作成画面へ遷移）。未保存の入力結果があれば確認する
  const handleChangePairing = () => {
    if (changedMatches.size > 0) {
      const ok = window.confirm('未保存の入力結果があります。保存せずに組み合わせ作成画面へ移動しますか？');
      if (!ok) return;
    }
    const from = encodeURIComponent(`/matches/bulk-input/${sessionId}`);
    navigate(`/pairings?date=${session.sessionDate}&matchNumber=${currentMatchNumber}&from=${from}`);
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
        const isLesson = result.isLesson === true;
        const scoreDiff = isLesson ? null : (result.scoreDifference ?? 0); // 指導試合は枚数差なし、通常の未選択は0枚

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
          isLesson,
          createdBy: currentPlayer.id,
        };

        if (result.matchId) {
          // 更新（詳細版）
          savePromises.push(
            matchAPI.updateDetailed(
              result.matchId,
              result.winnerId,
              scoreDiff,
              currentPlayer.id,
              undefined,
              undefined,
              isLesson
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

      // 保存成功後、試合結果一覧画面に遷移（保存元セッションの日付と入力していた試合番号を引き継ぐ）。
      // date を付けないと一覧側が dateParam||today で当日を解決し、過去日・未来日の保存で別日が開いてしまう。
      navigate(`/matches/results/${sessionId}?date=${session.sessionDate}&matchNumber=${currentMatchNumber}`);

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

  const totalMatches = session?.totalMatches || 0;

  // 1試合分の入力パネル（カルーセルの各ページ）。currentMatchNumber に依存せず
  // matchNumber 引数だけで描画できるようにし、隣の試合のチラ見えにも対応する。
  const renderMatchPanel = (matchNumber) => {
    const matchPairings = getPairingsForMatch(matchNumber);

    return (
      <>
        {matchPairings.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-[#9b8a7e] text-sm mb-4">
              この試合の対戦組み合わせが作成されていません
            </p>
            {session && (
              <button
                onClick={() => navigate(`/pairings?date=${session.sessionDate}&matchNumber=${matchNumber}`)}
                className="px-4 py-2 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] text-sm"
              >
                対戦組み合わせを作成する
              </button>
            )}
          </div>
        ) : (
        <>
        <p className="text-xs text-[#9b8a7e] mb-3">
          勝者の名前をタップ → 枚数差を選択
        </p>
        <div className="divide-y divide-[#e2d9d0]">
          {matchPairings.map((pairing, index) => {
            const result = getResult(matchNumber, pairing.player1Id, pairing.player2Id);
            const isPlayer1Winner = result.winnerId === pairing.player1Id;
            const isPlayer2Winner = result.winnerId === pairing.player2Id;
            const hasWinner = result.winnerId !== null;
            const isLesson = result.isLesson === true;

            return (
              <div key={pairing.id || index} className="py-4">
                <div className="flex items-center text-lg">
                  {/* 選手1 */}
                  <button
                    type="button"
                    onClick={() => setWinner(matchNumber, pairing.player1Id, pairing.player2Id, pairing.player1Id)}
                    className={`flex-1 text-right pr-2 font-semibold truncate transition-colors ${
                      isLesson ? 'text-gray-700' : isPlayer1Winner ? 'text-green-600' : isPlayer2Winner ? 'text-gray-400' : 'text-gray-700'
                    }`}
                  >
                    {pairing.player1Name}
                  </button>

                  {/* 中央: 勝敗マーク + 枚数差 or vs */}
                  {hasWinner ? (
                    <>
                      {isLesson ? (
                        <div className="w-8 flex-shrink-0" aria-hidden="true" />
                      ) : (
                        <div className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${isPlayer1Winner ? 'text-green-600' : 'text-red-600'}`}>
                          {isPlayer1Winner ? '〇' : '×'}
                        </div>
                      )}
                      <select
                        value={isLesson ? 'lesson' : (result.scoreDifference ?? '')}
                        onChange={(e) => setScoreDifference(
                          matchNumber,
                          pairing.player1Id,
                          pairing.player2Id,
                          e.target.value
                        )}
                        className={`text-center font-bold text-gray-900 bg-transparent border-0 border-b border-[#d0c5b8] focus:ring-0 focus:border-[#82655a] flex-shrink-0 px-0 py-0 text-base ${isLesson ? 'w-16' : 'w-14'}`}
                      >
                        <option value="">-</option>
                        {Array.from({ length: 26 }, (_, i) => i).map(num => (
                          <option key={num} value={num}>{num}</option>
                        ))}
                        <option value="lesson">指導</option>
                      </select>
                      {isLesson ? (
                        <div className="w-8 flex-shrink-0" aria-hidden="true" />
                      ) : (
                        <div className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${isPlayer2Winner ? 'text-green-600' : 'text-red-600'}`}>
                          {isPlayer2Winner ? '〇' : '×'}
                        </div>
                      )}
                    </>
                  ) : (
                    <div className="text-sm font-medium text-[#b0a396] w-8 text-center flex-shrink-0">
                      vs
                    </div>
                  )}

                  {/* 選手2 */}
                  <button
                    type="button"
                    onClick={() => setWinner(matchNumber, pairing.player1Id, pairing.player2Id, pairing.player2Id)}
                    className={`flex-1 text-left pl-2 font-semibold truncate transition-colors ${
                      isLesson ? 'text-gray-700' : isPlayer2Winner ? 'text-green-600' : isPlayer1Winner ? 'text-gray-400' : 'text-gray-700'
                    }`}
                  >
                    {pairing.player2Name}
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* 抜け番セクション */}
        {(byePlayers[matchNumber] || []).length > 0 && (
          <div className="mt-6 pt-4 border-t border-[#e2d9d0]">
            <p className="text-xs font-medium text-[#9b8a7e] mb-3">抜け番</p>
            <div className="space-y-3">
              {byePlayers[matchNumber].map(player => {
                const byeKey = `${matchNumber}-${player.id}`;
                const activity = byeActivities[byeKey] || {};
                return (
                  <div key={player.id} className="bg-yellow-50 border border-yellow-200 rounded-lg p-3">
                    <div className="flex items-center gap-3">
                      <span className="font-semibold text-[#374151] min-w-[4rem]">{player.name}</span>
                      <select
                        value={activity.activityType || ''}
                        onChange={(e) => setByeActivityType(matchNumber, player.id, e.target.value)}
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
                        onChange={(e) => setByeFreeText(matchNumber, player.id, e.target.value)}
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
        </>
        )}
      </>
    );
  };

  return (
    <div ref={swipeAreaRef} className="min-h-screen bg-[#f2ede6] pb-20">
      {/* 固定ナビゲーションバー */}
      <div data-swipe-ignore className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
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
              onClick={handleChangePairing}
              className="flex items-center gap-1 px-2 py-1 rounded text-xs font-medium transition-colors bg-white/20 text-white hover:bg-white/30"
            >
              <Pencil className="w-3 h-3" />
              対戦変更
            </button>
          </div>

          {/* タブバー */}
          {totalMatches > 0 && (
            <div ref={tabBarRef} className="flex overflow-x-auto -mb-px">
              {Array.from({ length: totalMatches }, (_, i) => i + 1).map(num => (
                <button
                  key={num}
                  data-active={currentMatchNumber === num}
                  onClick={() => setCurrentMatchNumber(num)}
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

      {/* メインコンテンツ（試合番号スワイプ対応カルーセル） */}
      <div className="max-w-4xl mx-auto px-6 pt-24 pb-6">
        {/* 横スワイプで試合移動できることの控えめな案内（2試合以上のときのみ） */}
        {totalMatches > 1 && (
          <p className="text-center text-xs text-[#9b8a7e] mb-3 select-none">
            ‹ スワイプで試合を切替 ›
          </p>
        )}
        <MatchCarousel
          totalMatches={totalMatches}
          currentMatchNumber={currentMatchNumber}
          onChange={setCurrentMatchNumber}
          renderPanel={renderMatchPanel}
          swipeAreaRef={swipeAreaRef}
        />
      </div>

      {/* 固定保存ボタン（変更がある場合のみ表示） */}
      {changedMatches.size > 0 && (
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
