import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI, playerAPI, practiceAPI, pairingAPI } from '../../api';
import { Trophy, Save, X, AlertCircle, Users, Lock, UserPlus } from 'lucide-react';

const MatchForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { currentPlayer } = useAuth();
  const isEdit = !!id;

  // location.stateから初期値を取得
  const initialData = location.state || {};

  const [formData, setFormData] = useState({
    matchDate: initialData.matchDate || new Date().toISOString().split('T')[0],
    opponentName: initialData.opponentName || '',
    opponentId: initialData.opponentId || null,
    result: '勝ち',
    scoreDifference: 0,
    matchNumber: initialData.matchNumber || 1,
    notes: '',
  });

  const [players, setPlayers] = useState([]);
  const [availablePlayers, setAvailablePlayers] = useState([]);
  const [practiceSession, setPracticeSession] = useState(null);
  const [practiceSessions, setPracticeSessions] = useState([]);
  const [pairing, setPairing] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState('');
  const [participatingMatchNumbers, setParticipatingMatchNumbers] = useState([]);
  const [isExistingMatch, setIsExistingMatch] = useState(false);
  const [showParticipationDialog, setShowParticipationDialog] = useState(false);
  const [isRegistering, setIsRegistering] = useState(false);

  // 初期ロード完了フラグ（タブ切り替え時の重複API呼び出し防止用）
  const initialLoadDone = useRef(false);
  // 全試合データのキャッシュ（タブ即時切替用）
  const matchDataCache = useRef({});   // matchNumber -> { exists, data }
  const pairingCache = useRef({});     // matchNumber -> myPairing or null
  const allPairingsCache = useRef([]); // 全ペアリングデータ

  // useEffect 1: 選手一覧 + 今日の練習セッション取得
  useEffect(() => {
    const fetchData = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];

        const promises = [
          playerAPI.getAll(),
          practiceAPI.getByDate(today).catch(() => ({ data: null }))
        ];
        if (isEdit) promises.push(matchAPI.getById(id));
        const [playersRes, todaySessionRes, matchRes] = await Promise.all(promises);

        setPlayers(
          playersRes.data.filter((p) => p.id !== currentPlayer.id)
        );

        const todaySessions = todaySessionRes.data ? [todaySessionRes.data] : [];
        setPracticeSessions(todaySessions);

        // 練習セッションを直接セット（useEffect 2で再取得しない）
        if (todaySessionRes.data) {
          setPracticeSession(todaySessionRes.data);

          // 参加登録チェック（新規作成時のみ）
          if (!isEdit) {
            try {
              const now = new Date();
              const participationsRes = await practiceAPI.getPlayerParticipations(
                currentPlayer.id, now.getFullYear(), now.getMonth() + 1
              );
              const sessionParticipations = participationsRes.data?.[todaySessionRes.data.id] || [];
              if (sessionParticipations.length === 0) {
                setShowParticipationDialog(true);
              }
            } catch (e) {
              // 参加状況取得に失敗しても結果入力は許可
              console.warn('参加状況の取得に失敗:', e);
            }
          }
        }

        if (isEdit && matchRes) {
          const match = matchRes.data;

          // 過去の日付の場合、その日のセッションも取得
          if (match.matchDate !== today) {
            try {
              const editSessionRes = await practiceAPI.getByDate(match.matchDate);
              if (editSessionRes.data) {
                setPracticeSessions(prev => {
                  const exists = prev.some(s => s.id === editSessionRes.data.id);
                  return exists ? prev : [...prev, editSessionRes.data];
                });
                setPracticeSession(editSessionRes.data);
              }
            } catch (e) {
              // セッションが見つからなくても編集は許可
            }
          }

          setFormData({
            matchDate: match.matchDate,
            opponentName: match.opponentName,
            result: match.result,
            scoreDifference: match.scoreDifference,
            matchNumber: match.matchNumber,
            notes: match.notes || '',
          });
          setInitialLoading(false);
        }

        // 練習日がない場合、または編集モードの場合はここでローディング終了
        if (!todaySessionRes.data || isEdit) {
          setInitialLoading(false);
        }
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
        setInitialLoading(false);
      }
    };

    fetchData();
  }, [id, isEdit, currentPlayer]);

  // useEffect 2: 練習セッション詳細取得 + 既存試合/組み合わせチェック（初回のみ）
  useEffect(() => {
    const fetchSessionDetails = async () => {
      if (!practiceSession || isEdit) return;

      try {
        // 全ペアリングを先に取得（IDベースで参加試合番号を特定するため）
        let defaultMatchNumber = formData.matchNumber;
        const newMatchDataCache = {};
        const newPairingCache = {};

        const allPairingsRes = await pairingAPI.getByDate(formData.matchDate).catch(() => ({ data: [] }));
        const allPairings = allPairingsRes.data || [];
        allPairingsCache.current = allPairings;

        // 全試合番号をタブ表示用にセット
        const allMatchNumbers = Array.from({ length: practiceSession.totalMatches || 1 }, (_, i) => i + 1);
        setParticipatingMatchNumbers(allMatchNumbers);

        // ペアリングを試合番号ごとにキャッシュ
        allMatchNumbers.forEach(num => {
          const matchPairings = allPairings.filter(p => p.matchNumber === num);
          const myPairing = matchPairings.find(
            p => p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id
          );
          newPairingCache[num] = myPairing || null;
        });

        // 全試合の既存記録を一括取得
        const matchResults = await Promise.all(allMatchNumbers.map(num =>
          matchAPI.getByPlayerDateAndMatchNumber(currentPlayer.id, formData.matchDate, num)
            .then(res => ({ matchNumber: num, exists: true, data: res.data }))
            .catch(() => ({ matchNumber: num, exists: false }))
        ));

        // 既存記録をキャッシュ
        matchResults.forEach(r => { newMatchDataCache[r.matchNumber] = r; });

        // refにキャッシュ保存
        matchDataCache.current = newMatchDataCache;
        pairingCache.current = newPairingCache;

        // デフォルト試合番号を決定
        // 未記録の試合を優先表示
        const unrecordedMatch = matchResults.find(result => !result.exists);
        defaultMatchNumber = unrecordedMatch ? unrecordedMatch.matchNumber : allMatchNumbers[0];

        // デフォルト試合番号のデータを適用
        applyMatchData(defaultMatchNumber, newMatchDataCache, newPairingCache);

        initialLoadDone.current = true;
      } catch (err) {
        setAvailablePlayers([]);
        setParticipatingMatchNumbers([]);
      } finally {
        setInitialLoading(false);
      }
    };

    fetchSessionDetails();
  }, [practiceSession, currentPlayer.id, currentPlayer.name, isEdit]);

  // 試合番号のデータをstateに適用する共通関数
  const applyMatchData = (matchNumber, matchCache, pairCache) => {
    const existingResult = matchCache[matchNumber];
    if (existingResult && existingResult.exists) {
      const match = existingResult.data;
      setIsExistingMatch(true);
      setPairing(null);
      // opponentIdをplayer1Id/player2Idから算出
      const opponentId = match.player1Id === currentPlayer.id ? match.player2Id : match.player1Id;
      setFormData(prev => ({
        ...prev,
        matchNumber: matchNumber,
        opponentName: match.opponentName,
        opponentId: opponentId || null,
        result: match.result,
        scoreDifference: Number(match.scoreDifference),
        notes: match.notes || ''
      }));
    } else {
      setIsExistingMatch(false);
      const myPairing = pairCache[matchNumber];
      if (myPairing) {
        setPairing(myPairing);
        const opponentId = myPairing.player1Id === currentPlayer.id
          ? myPairing.player2Id
          : myPairing.player1Id;
        const opponentName = myPairing.player1Id === currentPlayer.id
          ? myPairing.player2Name
          : myPairing.player1Name;
        setFormData(prev => ({
          ...prev,
          matchNumber: matchNumber,
          opponentId: opponentId,
          opponentName: opponentName,
          result: '勝ち',
          scoreDifference: 0,
          notes: ''
        }));
      } else {
        setPairing(null);
        setAvailablePlayers(getMatchPlayers(matchNumber));
        setFormData(prev => ({
          ...prev,
          matchNumber: matchNumber,
          opponentId: null,
          opponentName: '',
          result: '勝ち',
          scoreDifference: 0,
          notes: ''
        }));
      }
    }
  };

  // useEffect 3: タブ切り替え時（キャッシュから即座に反映）
  useEffect(() => {
    if (!initialLoadDone.current) return;
    applyMatchData(formData.matchNumber, matchDataCache.current, pairingCache.current);
  }, [formData.matchNumber]);

  // 試合番号に対応する参加選手リストを構築（全ペアリングキャッシュから取得）
  const getMatchPlayers = (matchNumber) => {
    const matchPairings = allPairingsCache.current.filter(p => p.matchNumber === matchNumber);
    if (matchPairings.length === 0) return players;
    const playerIds = new Set();
    matchPairings.forEach(p => {
      playerIds.add(p.player1Id);
      playerIds.add(p.player2Id);
    });
    playerIds.delete(currentPlayer.id);
    return players.filter(p => playerIds.has(p.id));
  };

  // 参加登録を自動実行
  const handleAutoRegister = async () => {
    if (!practiceSession) return;
    setIsRegistering(true);
    try {
      const now = new Date();
      const year = now.getFullYear();
      const month = now.getMonth() + 1;

      // 既存の月間参加登録を取得
      const existingRes = await practiceAPI.getPlayerParticipations(currentPlayer.id, year, month);
      const existing = existingRes.data || {};

      // 今日のセッションの全試合番号を追加
      const totalMatches = practiceSession.totalMatches || 1;
      const todayMatchNumbers = Array.from({ length: totalMatches }, (_, i) => i + 1);
      const existingForSession = existing[practiceSession.id] || [];
      const merged = [...new Set([...existingForSession, ...todayMatchNumbers])];
      existing[practiceSession.id] = merged;

      // 全参加データを構築して保存
      const participationsList = [];
      Object.entries(existing).forEach(([sessionId, matchNumbers]) => {
        matchNumbers.forEach((matchNumber) => {
          participationsList.push({
            sessionId: Number(sessionId),
            matchNumber: matchNumber,
          });
        });
      });

      await practiceAPI.registerParticipations({
        playerId: currentPlayer.id,
        year,
        month,
        participations: participationsList,
      });

      setShowParticipationDialog(false);
    } catch (err) {
      console.error('参加登録エラー:', err);
      setError('参加登録に失敗しました');
      setShowParticipationDialog(false);
    } finally {
      setIsRegistering(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!formData.opponentName) {
      setError('対戦相手を選択してください');
      return;
    }

    setLoading(true);

    try {
      if (isEdit) {
        const submitData = {
          ...formData,
          playerId: currentPlayer.id,
          scoreDifference: parseInt(formData.scoreDifference),
          matchNumber: parseInt(formData.matchNumber),
        };
        await matchAPI.update(id, submitData);
        navigate('/matches');
      } else {
        if (formData.opponentId) {
          const player1Id = currentPlayer.id;
          const player2Id = formData.opponentId;
          const winnerId = formData.result === '勝ち' ? currentPlayer.id : formData.opponentId;

          const detailedData = {
            matchDate: formData.matchDate,
            matchNumber: parseInt(formData.matchNumber),
            player1Id: player1Id,
            player2Id: player2Id,
            winnerId: winnerId,
            scoreDifference: parseInt(formData.scoreDifference),
            notes: formData.notes,
            createdBy: currentPlayer.id,
            updatedBy: currentPlayer.id
          };

          await matchAPI.createDetailed(detailedData);
        } else {
          const submitData = {
            ...formData,
            playerId: currentPlayer.id,
            scoreDifference: parseInt(formData.scoreDifference),
            matchNumber: parseInt(formData.matchNumber),
          };
          await matchAPI.create(submitData);
        }

        navigate('/');
      }
    } catch (err) {
      console.error('保存エラー:', err);

      if (err.response?.status === 409 && err.response?.data?.existingMatchId) {
        const existingMatchId = err.response.data.existingMatchId;
        const confirmMessage = `${err.response.data.message}\n\n既存の記録を上書きしますか？`;

        if (window.confirm(confirmMessage)) {
          try {
            const submitData = {
              ...formData,
              playerId: currentPlayer.id,
              scoreDifference: parseInt(formData.scoreDifference),
              matchNumber: parseInt(formData.matchNumber),
            };

            await matchAPI.update(existingMatchId, submitData);
            navigate('/');
          } catch (updateErr) {
            console.error('更新エラー:', updateErr);
            setError(
              updateErr.response?.data?.message || '試合記録の更新に失敗しました'
            );
          }
        }
      } else {
        setError(
          err.response?.data?.message ||
            `試合記録の${isEdit ? '更新' : '登録'}に失敗しました`
        );
      }
    } finally {
      setLoading(false);
    }
  };

  // 初期ローディング中
  if (initialLoading) {
    return (
      <div className="min-h-screen bg-[#f2ede6] pb-16 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#4a6b5a] mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }


  return (
    <div className="min-h-screen bg-[#f2ede6] pb-16 overflow-hidden">
      {/* ナビゲーションバー */}
      <div className="bg-[#d4ddd7] border-b border-[#c5cec8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
        <div className="max-w-7xl mx-auto">
          {/* 日付表示 */}
          <div className="flex items-center justify-center py-3">
            <span className="text-lg font-semibold text-[#374151]">
              {new Date(formData.matchDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
                weekday: 'short'
              })}
            </span>
          </div>

          {/* 試合番号タブ */}
          {practiceSession && (
            <div className="flex overflow-x-auto -mb-px">
              {(participatingMatchNumbers.length > 0
                ? participatingMatchNumbers
                : Array.from({ length: practiceSession.totalMatches }, (_, i) => i + 1)
              ).map((num) => (
                <button
                  key={num}
                  type="button"
                  onClick={() => setFormData(prev => ({ ...prev, matchNumber: num }))}
                  className={`flex-shrink-0 px-4 py-2 text-sm font-medium transition-colors border-b-2 ${
                    formData.matchNumber === num
                      ? 'border-[#374151] text-[#374151]'
                      : 'border-transparent text-[#6b7280] hover:text-[#374151] hover:border-[#8a9e90]'
                  }`}
                >
                  第{num}試合
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <form onSubmit={handleSubmit} className="h-full px-6 overflow-hidden pt-28 space-y-6">
        {/* 今日が練習日でない場合の警告 */}
        {!isEdit && practiceSessions.length === 0 && (
          <div className="p-4 bg-yellow-50 rounded-lg">
            <div className="flex items-center gap-2 text-yellow-800 mb-1">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span className="font-semibold text-sm">今日は練習日として登録されていません</span>
            </div>
            <p className="text-xs text-yellow-700 ml-7">
              練習日を登録してから試合記録を入力してください。
            </p>
          </div>
        )}

        {/* 既存試合の警告メッセージ */}
        {!isEdit && isExistingMatch && (
          <div className="p-3 bg-blue-50 rounded-lg flex items-center gap-2 text-blue-700">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span className="text-sm">入力済みの試合です。保存で上書きされます。</span>
          </div>
        )}

        {/* 対戦相手 */}
        {practiceSession && (
          <div>
            <div className="text-xs font-medium text-[#6b7280] tracking-wide mb-2">対戦相手</div>
            {formData.opponentId ? (
              <div className="text-center py-2">
                <div className="text-3xl font-bold text-[#374151] tracking-wide">
                  {formData.opponentName}
                </div>
                {pairing && (
                  <button
                    type="button"
                    onClick={() => {
                      setPairing(null);
                      setAvailablePlayers(getMatchPlayers(formData.matchNumber));
                      setFormData(prev => ({ ...prev, opponentId: null, opponentName: '' }));
                    }}
                    className="mt-2 text-xs text-[#6b7280] underline underline-offset-2"
                  >
                    変更する
                  </button>
                )}
              </div>
            ) : (
              <select
                value={formData.opponentId || ''}
                onChange={(e) => {
                  const selectedPlayer = availablePlayers.find(p => p.id === parseInt(e.target.value));
                  if (selectedPlayer) {
                    setFormData(prev => ({
                      ...prev,
                      opponentName: selectedPlayer.name,
                      opponentId: selectedPlayer.id
                    }));
                  }
                }}
                className="w-full px-0 py-3 border-0 border-b border-[#c5cec8] bg-transparent focus:ring-0 focus:border-[#4a6b5a] text-lg text-[#374151]"
                required
              >
                <option value="">選択してください</option>
                {availablePlayers.map((player) => (
                  <option key={player.id} value={player.id}>
                    {player.name}
                  </option>
                ))}
              </select>
            )}
          </div>
        )}

        {/* 結果 */}
        <div>
          <div className="text-xs font-medium text-[#6b7280] tracking-wide mb-3">結果</div>
          <div className="grid grid-cols-2 gap-4">
            {['勝ち', '負け'].map((result) => (
              <button
                key={result}
                type="button"
                onClick={() =>
                  setFormData((prev) => ({ ...prev, result }))
                }
                className={`py-5 rounded-2xl font-bold text-xl transition-all ${
                  formData.result === result
                    ? result === '勝ち'
                      ? 'bg-green-500 text-white shadow-lg shadow-green-200'
                      : 'bg-red-500 text-white shadow-lg shadow-red-200'
                    : 'bg-[#e5ebe7] text-[#9ca3af]'
                }`}
              >
                {result === '勝ち' ? '〇' : '×'} {result}
              </button>
            ))}
          </div>
        </div>

        {/* 枚数差 */}
        <div>
          <div className="text-xs font-medium text-[#6b7280] tracking-wide mb-2">枚数差</div>
          <select
            name="scoreDifference"
            value={formData.scoreDifference}
            onChange={handleChange}
            className="w-full px-0 py-3 border-0 border-b border-[#c5cec8] bg-transparent focus:ring-0 focus:border-[#4a6b5a] text-lg text-[#374151]"
            required
          >
            {Array.from({ length: 26 }, (_, i) => i).map((num) => (
              <option key={num} value={num}>
                {num} 枚
              </option>
            ))}
          </select>
        </div>

        {/* メモ */}
        <div>
          <div className="text-xs font-medium text-[#6b7280] tracking-wide mb-2">メモ</div>
          <textarea
            name="notes"
            value={formData.notes}
            onChange={handleChange}
            rows="2"
            placeholder="試合の感想、反省点など..."
            className="w-full px-0 py-2 border-0 border-b border-[#c5cec8] bg-transparent focus:ring-0 focus:border-[#4a6b5a] resize-none text-[#374151] placeholder-[#9ca3af]"
          ></textarea>
        </div>

        {/* エラー表示 */}
        {error && !isEdit && (
          <div className="p-3 bg-red-50 rounded-lg flex items-center gap-2 text-red-700">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span className="text-sm">{error}</span>
          </div>
        )}

        {/* ボタン */}
        {practiceSessions.length > 0 && (
          <div className="flex gap-3 pt-2">
            <button
              type="submit"
              disabled={loading}
              className="flex-1 flex items-center justify-center gap-2 bg-[#4a6b5a] text-white py-4 rounded-2xl hover:bg-[#3d5a4c] transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-bold text-lg shadow-md"
            >
              {loading
                ? '保存中...'
                : isEdit
                ? '更新する'
                : '登録する'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/matches')}
              className="flex items-center justify-center px-5 py-4 rounded-2xl text-[#6b7280] hover:bg-[#e5ebe7] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        )}
      </form>

      {/* 参加未登録ダイアログ */}
      {showParticipationDialog && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[60] p-4">
          <div className="bg-white rounded-xl max-w-sm w-full p-6 shadow-xl">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full bg-yellow-100 flex items-center justify-center">
                <UserPlus className="w-5 h-5 text-yellow-600" />
              </div>
              <h3 className="text-lg font-semibold text-[#374151]">参加未登録</h3>
            </div>
            <p className="text-sm text-[#6b7280] mb-6">
              本日の練習に参加登録されていません。参加登録を行いますか？
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => { setShowParticipationDialog(false); navigate('/'); }}
                className="flex-1 px-4 py-2.5 border border-gray-300 rounded-lg text-[#6b7280] hover:bg-gray-50 transition-colors text-sm font-medium"
              >
                戻る
              </button>
              <button
                onClick={handleAutoRegister}
                disabled={isRegistering}
                className="flex-1 px-4 py-2.5 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:opacity-50 text-sm font-medium"
              >
                {isRegistering ? '登録中...' : '登録して入力する'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MatchForm;
