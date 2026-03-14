import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI, playerAPI, practiceAPI, pairingAPI } from '../../api';
import { Trophy, Save, X, AlertCircle, Users, Lock } from 'lucide-react';

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
        }

        if (isEdit && matchRes) {
          const match = matchRes.data;

          if (match.matchDate !== today) {
            setError('過去の試合記録は編集できません。試合記録の編集は当日のみ可能です。');
            setInitialLoading(false);
            return;
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
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#82655a] mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }

  // 過去の試合記録編集エラーの場合
  if (error && isEdit) {
    return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-6 space-y-6">
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <div className="flex justify-center">
            <button
              onClick={() => navigate('/')}
              className="flex items-center justify-center gap-2 bg-[#82655a] text-white px-6 py-3 rounded-lg hover:bg-[#6b5048] transition-colors font-medium"
            >
              ホームに戻る
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f2ede6] pb-16 overflow-hidden">
      {/* ナビゲーションバー */}
      <div className="bg-[#e2d9d0] border-b border-[#d0c5b8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
        <div className="max-w-7xl mx-auto">
          {/* 日付表示 */}
          <div className="flex items-center justify-center py-3">
            <span className="text-lg font-semibold text-[#5f3a2d]">
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
                      ? 'border-[#5f3a2d] text-[#5f3a2d]'
                      : 'border-transparent text-[#7a5f54] hover:text-[#5f3a2d] hover:border-[#a5927f]'
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
            <div className="text-xs font-medium text-[#8a7568] tracking-wide mb-2">対戦相手</div>
            {formData.opponentId ? (
              <div className="text-center py-2">
                <div className="text-3xl font-bold text-[#5f3a2d] tracking-wide">
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
                    className="mt-2 text-xs text-[#8a7568] underline underline-offset-2"
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
                className="w-full px-0 py-3 border-0 border-b border-[#d0c5b8] bg-transparent focus:ring-0 focus:border-[#82655a] text-lg text-[#5f3a2d]"
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
          <div className="text-xs font-medium text-[#8a7568] tracking-wide mb-3">結果</div>
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
                    : 'bg-[#ebe6df] text-[#b0a396]'
                }`}
              >
                {result === '勝ち' ? '〇' : '×'} {result}
              </button>
            ))}
          </div>
        </div>

        {/* 枚数差 */}
        <div>
          <div className="text-xs font-medium text-[#8a7568] tracking-wide mb-2">枚数差</div>
          <select
            name="scoreDifference"
            value={formData.scoreDifference}
            onChange={handleChange}
            className="w-full px-0 py-3 border-0 border-b border-[#d0c5b8] bg-transparent focus:ring-0 focus:border-[#82655a] text-lg text-[#5f3a2d]"
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
          <div className="text-xs font-medium text-[#8a7568] tracking-wide mb-2">メモ</div>
          <textarea
            name="notes"
            value={formData.notes}
            onChange={handleChange}
            rows="2"
            placeholder="試合の感想、反省点など..."
            className="w-full px-0 py-2 border-0 border-b border-[#d0c5b8] bg-transparent focus:ring-0 focus:border-[#82655a] resize-none text-[#5f3a2d] placeholder-[#c0b5a8]"
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
              className="flex-1 flex items-center justify-center gap-2 bg-[#82655a] text-white py-4 rounded-2xl hover:bg-[#6b5048] transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-bold text-lg shadow-md"
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
              className="flex items-center justify-center px-5 py-4 rounded-2xl text-[#8a7568] hover:bg-[#ebe6df] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        )}
      </form>
    </div>
  );
};

export default MatchForm;
