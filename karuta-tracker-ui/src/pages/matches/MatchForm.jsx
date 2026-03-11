import { useState, useEffect } from 'react';
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
    opponentId: initialData.opponentId || null, // 詳細試合作成用の対戦相手ID
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
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];

        // 選手一覧・練習セッション・（編集時）試合データを並列取得
        const promises = [playerAPI.getAll(), practiceAPI.getAll()];
        if (isEdit) promises.push(matchAPI.getById(id));
        const [playersRes, sessionsRes, matchRes] = await Promise.all(promises);

        setPlayers(
          playersRes.data.filter((p) => p.id !== currentPlayer.id)
        );

        const todaySessions = sessionsRes.data.filter(
          session => session.sessionDate === today
        );
        setPracticeSessions(todaySessions);

        // 編集モードの場合、既存データを反映
        if (isEdit && matchRes) {
          const match = matchRes.data;

          // 試合日が今日でない場合はエラー
          if (match.matchDate !== today) {
            setError('過去の試合記録は編集できません。試合記録の編集は当日のみ可能です。');
            setLoading(false);
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
        }
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
      }
    };

    fetchData();
  }, [id, isEdit, currentPlayer]);

  // 試合日が変更されたら、その日の練習セッションを取得
  useEffect(() => {
    const fetchPracticeSession = async () => {
      if (!formData.matchDate) return;

      try {
        const response = await practiceAPI.getByDate(formData.matchDate);
        if (response.data) {
          setPracticeSession(response.data);
          // 練習セッションがある場合、参加者を取得
          const participantsRes = await practiceAPI.getParticipants(response.data.id);
          const participants = participantsRes.data.filter(p => p.id !== currentPlayer.id);
          setAvailablePlayers(participants);

          // 試合番号をリセット
          setFormData(prev => ({ ...prev, matchNumber: 1 }));
        } else {
          setPracticeSession(null);
          setAvailablePlayers([]);
        }
      } catch (err) {
        setPracticeSession(null);
        setAvailablePlayers([]);
      }
    };

    if (players.length > 0) {
      fetchPracticeSession();
    }
  }, [formData.matchDate, players, currentPlayer]);

  // 試合番号が変更されたら、対戦組み合わせをチェック
  useEffect(() => {
    const checkPairing = async () => {
      if (!formData.matchDate || !formData.matchNumber) return;

      try {
        const response = await pairingAPI.getByDateAndMatchNumber(
          formData.matchDate,
          formData.matchNumber
        );

        if (response.data && response.data.length > 0) {
          // 自分が含まれる対戦組み合わせを探す
          const myPairing = response.data.find(
            p => p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id
          );

          if (myPairing) {
            setPairing(myPairing);
            // 対戦相手を自動設定
            const opponentId = myPairing.player1Id === currentPlayer.id
              ? myPairing.player2Id
              : myPairing.player1Id;
            const opponentName = myPairing.player1Id === currentPlayer.id
              ? myPairing.player2Name
              : myPairing.player1Name;

            setFormData(prev => ({
              ...prev,
              opponentId: opponentId,
              opponentName: opponentName
            }));
          } else {
            // 自分の組み合わせがない場合
            setPairing(null);
            setFormData(prev => ({
              ...prev,
              opponentId: null,
              opponentName: ''
            }));
          }
        } else {
          // 組み合わせが存在しない場合
          setPairing(null);
          setFormData(prev => ({
            ...prev,
            opponentId: null,
            opponentName: ''
          }));
        }
      } catch (err) {
        // 組み合わせが存在しない場合
        setPairing(null);
        setFormData(prev => ({
          ...prev,
          opponentId: null,
          opponentName: ''
        }));
      }
    };

    checkPairing();
  }, [formData.matchDate, formData.matchNumber, currentPlayer]);

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

    // バリデーション
    if (!formData.opponentName) {
      setError('対戦相手を選択してください');
      return;
    }

    setLoading(true);

    try {
      if (isEdit) {
        // 編集モードは簡易版APIを使用
        const submitData = {
          ...formData,
          playerId: currentPlayer.id,
          scoreDifference: parseInt(formData.scoreDifference),
          matchNumber: parseInt(formData.matchNumber),
        };
        await matchAPI.update(id, submitData);
        navigate('/matches');
      } else {
        // 新規登録時：opponentIdがあれば詳細版API、なければ簡易版APIを使用
        if (formData.opponentId) {
          // 詳細試合作成API（両選手がシステムに登録されている場合）
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
          // 簡易試合作成API（対戦相手が未登録の場合）
          const submitData = {
            ...formData,
            playerId: currentPlayer.id,
            scoreDifference: parseInt(formData.scoreDifference),
            matchNumber: parseInt(formData.matchNumber),
          };
          await matchAPI.create(submitData);
        }

        // 新規登録の場合はホーム画面に遷移して今日の対戦カードを更新
        navigate('/');
      }
    } catch (err) {
      console.error('保存エラー:', err);

      // 重複エラー（409 Conflict）の場合
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

  // 過去の試合記録編集エラーの場合
  if (error && isEdit) {
    return (
      <div className="max-w-2xl mx-auto">
        <div className="bg-white rounded-lg shadow-sm p-6 space-y-6">
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
          <div className="flex justify-center">
            <button
              onClick={() => navigate('/')}
              className="flex items-center justify-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors font-medium"
            >
              ホームに戻る
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-16 overflow-hidden">
      <form onSubmit={handleSubmit} className="h-full p-3 space-y-3 overflow-hidden">
        {/* 今日が練習日でない場合の警告 */}
        {!isEdit && practiceSessions.length === 0 && (
          <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
            <div className="flex items-center gap-2 text-yellow-800 mb-2">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span className="font-semibold">今日は練習日として登録されていません</span>
            </div>
            <p className="text-sm text-yellow-700">
              試合記録の登録は当日の練習日のみ可能です。練習日を登録してから試合記録を入力してください。
            </p>
          </div>
        )}

        {/* 日付と試合番号（1行表示） */}
        {practiceSessions.length > 0 && practiceSession && (
          <div className="flex items-center justify-between text-lg font-medium text-gray-900">
            <div>
              {new Date(formData.matchDate).toLocaleDateString('ja-JP', {
                month: 'long',
                day: 'numeric',
                weekday: 'short'
              })}
            </div>
            <select
              name="matchNumber"
              value={formData.matchNumber}
              onChange={handleChange}
              className="px-3 py-1 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent text-base"
              required
            >
              {Array.from({ length: practiceSession.totalMatches }, (_, i) => i + 1).map((num) => (
                <option key={num} value={num}>
                  第{num}試合
                </option>
              ))}
            </select>
          </div>
        )}

        {/* 対戦相手 */}
        {practiceSession && (
          <div>
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
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent text-base"
              required
            >
              <option value="">対戦相手を選択</option>
              {availablePlayers.map((player) => (
                <option key={player.id} value={player.id}>
                  {player.name}
                </option>
              ))}
            </select>
          </div>
        )}

        {/* 結果 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            結果 <span className="text-red-500">*</span>
          </label>
          <div className="grid grid-cols-2 gap-2">
            {['勝ち', '負け'].map((result) => (
              <button
                key={result}
                type="button"
                onClick={() =>
                  setFormData((prev) => ({ ...prev, result }))
                }
                className={`px-4 py-2 rounded-lg border-2 font-medium transition-all ${
                  formData.result === result
                    ? result === '勝ち'
                      ? 'border-green-500 bg-green-50 text-green-700'
                      : 'border-red-500 bg-red-50 text-red-700'
                    : 'border-gray-200 text-gray-600 hover:border-gray-300'
                }`}
              >
                {result}
              </button>
            ))}
          </div>
        </div>

        {/* 枚数差 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            枚数差 <span className="text-red-500">*</span>
          </label>
          <div className="relative flex items-center justify-center">
            {/* 選択インジケーター（中央の横線） */}
            <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 translate-y-3 pointer-events-none z-10">
              <div className="w-20 h-0.5 bg-primary-600"></div>
            </div>

            {/* スクロール可能なピッカー */}
            <div
              className="relative overflow-y-scroll h-24 w-full max-w-xs hide-scrollbar snap-y snap-mandatory"
              onScroll={(e) => {
                const scrollTop = e.target.scrollTop;
                const itemHeight = 32; // 各アイテムの高さ
                const centerOffset = 48; // 上部パディング（h-12 = 48px）
                const index = Math.round((scrollTop + 16 - centerOffset) / itemHeight); // 16px = h-8の半分
                const clampedIndex = Math.max(0, Math.min(25, index));
                setFormData(prev => ({ ...prev, scoreDifference: clampedIndex }));
              }}
              ref={(el) => {
                if (el && !el.dataset.initialized) {
                  el.dataset.initialized = 'true';
                  // 初期位置にスクロール（中央に来るように調整）
                  el.scrollTop = formData.scoreDifference * 32 + 48 - 16;
                }
              }}
            >
              {/* 上部パディング */}
              <div className="h-12"></div>

              {/* 0-25枚の選択肢 */}
              {Array.from({ length: 26 }, (_, i) => i).map((num) => (
                <div
                  key={num}
                  className="h-8 flex flex-col items-center justify-center snap-center cursor-pointer transition-all"
                  style={{
                    fontSize: formData.scoreDifference === num ? '1.5rem' : '1rem',
                    fontWeight: formData.scoreDifference === num ? '700' : '400',
                    color: formData.scoreDifference === num ? '#2563eb' : '#9ca3af',
                    opacity: Math.abs(formData.scoreDifference - num) > 2 ? 0.3 : 1,
                  }}
                  onClick={() => {
                    setFormData(prev => ({ ...prev, scoreDifference: num }));
                    // スムーズスクロール（中央に来るように調整）
                    const container = document.querySelector('.hide-scrollbar');
                    if (container) {
                      container.scrollTo({
                        top: num * 32 + 48 - 16,
                        behavior: 'smooth'
                      });
                    }
                  }}
                >
                  {num} 枚
                </div>
              ))}

              {/* 下部パディング */}
              <div className="h-12"></div>
            </div>
          </div>

          <style>{`
            .hide-scrollbar::-webkit-scrollbar {
              display: none;
            }
            .hide-scrollbar {
              -ms-overflow-style: none;
              scrollbar-width: none;
            }
          `}</style>
        </div>

        {/* ボタン */}
        {practiceSessions.length > 0 && (
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={loading}
            className="flex-1 flex items-center justify-center gap-2 bg-primary-600 text-white px-4 py-2 rounded-lg hover:bg-primary-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-medium"
          >
            <Save className="w-4 h-4" />
            {loading
              ? '保存中...'
              : isEdit
              ? '更新'
              : '登録'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/matches')}
            className="flex items-center justify-center gap-2 bg-gray-100 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-200 transition-colors font-medium"
          >
            <X className="w-4 h-4" />
            キャンセル
          </button>
        </div>
        )}
      </form>
    </div>
  );
};

export default MatchForm;
