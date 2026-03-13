import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { matchAPI, pairingAPI } from '../../api';
import apiClient from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import { isAdmin, isSuperAdmin } from '../../utils/auth';
import { Save, AlertCircle, CheckCircle } from 'lucide-react';

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
        const sessionResponse = await apiClient.get(`/practice-sessions/${sessionId}`);
        const sessionData = sessionResponse.data;
        setSession(sessionData);

        // 対戦ペアリングと既存試合結果を並列取得
        const [pairingsResponse, matchesResponse] = await Promise.all([
          pairingAPI.getByDate(sessionData.sessionDate),
          apiClient.get(`/matches?date=${sessionData.sessionDate}`),
        ]);
        setPairings(pairingsResponse.data || []);
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

      for (const key of changedMatches) {
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
          // 新規作成（詳細版）
          savePromises.push(matchAPI.createDetailed(matchData));
        }
      }

      await Promise.all(savePromises);

      // 保存成功後、試合結果詳細画面に遷移
      navigate(`/matches/results/${sessionId}`);

    } catch (err) {
      console.error('保存エラー:', err);
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setSaving(false);
      setShowWarningDialog(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <div className="bg-red-50 border border-red-200 rounded-lg p-6 max-w-md">
          <div className="flex items-center gap-2 text-red-800 mb-2">
            <AlertCircle className="h-5 w-5" />
            <h2 className="font-semibold">エラー</h2>
          </div>
          <p className="text-red-700">{error}</p>
          <button
            onClick={() => navigate('/')}
            className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
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
      {/* ヘッダー */}
      <div className="bg-[#e2d9d0] shadow-sm sticky top-0 z-30">
        <div className="max-w-4xl mx-auto px-4 py-4">
          {session && (
            <div className="space-y-1 text-sm text-gray-600">
              <p>📅 {session.sessionDate}</p>
              <p>🏛️ {session.venueName}</p>
              <p>👥 参加者: {pairings.length * 2}名</p>
            </div>
          )}
        </div>
      </div>

      {/* タブバー */}
      <div className="bg-[#e2d9d0] sticky top-[120px] z-20">
        <div className="max-w-4xl mx-auto px-4 overflow-x-auto">
          <div className="flex gap-1 pt-2">
            {Array.from({ length: totalMatches }, (_, i) => i + 1).map(num => (
              <button
                key={num}
                onClick={() => setCurrentMatchNumber(num)}
                className={`flex-shrink-0 px-4 py-2 rounded-t-lg transition-colors ${
                  currentMatchNumber === num
                    ? 'bg-[#f9f6f2] text-[#5f3a2d] font-semibold border-t-2 border-x-2 border-[#d0c5b8]'
                    : 'bg-[#d0c5b8] text-[#7a5f54] hover:bg-[#c5bab0]'
                }`}
              >
                <div className="text-center">
                  <div className="font-semibold">
                    {num}試合{isMatchCompleted(num) ? '✓' : ''}
                  </div>
                  <div className="text-xs opacity-80">
                    {session?.startTime && `${num === 1 ? session.startTime : ''}`}
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* メインコンテンツ */}
      <div className="max-w-4xl mx-auto px-4 py-6">
        <div className="bg-[#f9f6f2] rounded-b-lg rounded-tr-lg shadow-sm p-4 mb-4">
          <h2 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
            第{currentMatchNumber}試合 ({currentPairings.length * 2}名参加)
            {isMatchCompleted(currentMatchNumber) && (
              <CheckCircle className="h-5 w-5 text-green-600" />
            )}
          </h2>

          <div className="space-y-3">
            {currentPairings.map((pairing, index) => {
              const result = getResult(currentMatchNumber, pairing.player1Id, pairing.player2Id);
              return (
                <div
                  key={index}
                  className="bg-[#f9f6f2] border border-[#d0c5b8] rounded-lg p-4 hover:border-[#a5927f] transition-colors"
                >
                  <div className="flex items-center justify-between gap-4">
                    {/* 選手1 */}
                    <button
                      onClick={() => setWinner(
                        currentMatchNumber,
                        pairing.player1Id,
                        pairing.player2Id,
                        pairing.player1Id
                      )}
                      className={`flex-1 text-left px-4 py-2 rounded-lg transition-colors ${
                        result.winnerId === pairing.player1Id
                          ? 'bg-green-100 border-2 border-green-500 font-semibold'
                          : 'bg-[#f0ebe3] hover:bg-gray-100'
                      }`}
                    >
                      {result.winnerId === pairing.player1Id && '🟢 '}
                      {pairing.player1Name}
                    </button>

                    {/* 枚数差 */}
                    <select
                      value={result.scoreDifference ?? ''}
                      onChange={(e) => setScoreDifference(
                        currentMatchNumber,
                        pairing.player1Id,
                        pairing.player2Id,
                        e.target.value
                      )}
                      className={`px-3 py-2 border rounded-lg ${
                        result.scoreDifference === null
                          ? 'text-gray-400 border-gray-300'
                          : 'text-gray-900 border-gray-400'
                      }`}
                    >
                      <option value="">枚数差</option>
                      {Array.from({ length: 26 }, (_, i) => i).map(num => (
                        <option key={num} value={num}>{num}枚</option>
                      ))}
                    </select>

                    {/* 選手2 */}
                    <button
                      onClick={() => setWinner(
                        currentMatchNumber,
                        pairing.player1Id,
                        pairing.player2Id,
                        pairing.player2Id
                      )}
                      className={`flex-1 text-right px-4 py-2 rounded-lg transition-colors ${
                        result.winnerId === pairing.player2Id
                          ? 'bg-green-100 border-2 border-green-500 font-semibold'
                          : 'bg-[#f0ebe3] hover:bg-gray-100'
                      }`}
                    >
                      {result.winnerId === pairing.player2Id && '🟢 '}
                      {pairing.player2Name}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* 変更カウンター */}
        <div className="text-center text-gray-600 mb-4">
          📊 変更: {changedMatches.size}試合
        </div>

        {/* 保存ボタン */}
        <button
          onClick={() => handleSave(false)}
          disabled={changedMatches.size === 0 || saving}
          className={`w-full py-3 rounded-lg font-semibold flex items-center justify-center gap-2 ${
            changedMatches.size === 0
              ? 'bg-gray-300 text-gray-500 cursor-not-allowed'
              : 'bg-primary-600 text-white hover:bg-primary-700'
          }`}
        >
          <Save className="h-5 w-5" />
          {saving ? '保存中...' : 'すべて保存'}
        </button>
        {changedMatches.size === 0 && (
          <p className="text-center text-sm text-gray-500 mt-2">
            (変更がある場合のみ有効)
          </p>
        )}
      </div>

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
                className="flex-1 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
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
