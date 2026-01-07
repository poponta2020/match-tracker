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
        // 選手一覧を取得（対戦相手候補）
        const playersRes = await playerAPI.getAll();
        setPlayers(
          playersRes.data.filter((p) => p.id !== currentPlayer.id)
        );

        // 全練習セッションを取得（日付選択用）
        const sessionsRes = await practiceAPI.getAll();
        // 今日の日付のみフィルタリング
        const today = new Date().toISOString().split('T')[0];
        const todaySessions = sessionsRes.data.filter(
          session => session.sessionDate === today
        );
        setPracticeSessions(todaySessions);

        // 編集モードの場合、既存データを取得
        if (isEdit) {
          const matchRes = await matchAPI.getById(id);
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
    <div className="max-w-2xl mx-auto">
      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm p-6 space-y-6">
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

        {/* 試合日 */}
        {practiceSessions.length > 0 && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              練習日 <span className="text-red-500">*</span>
            </label>
            <select
              name="matchDate"
              value={formData.matchDate}
              onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              required
              disabled={isEdit}
            >
              <option value="">練習日を選択してください</option>
              {practiceSessions.map((session) => (
                <option key={session.id} value={session.sessionDate}>
                  今日 ({new Date(session.sessionDate).toLocaleDateString('ja-JP', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    weekday: 'short'
                  })})
                  {session.venueName ? ` - ${session.venueName}` : ''}
                </option>
              ))}
            </select>
            <p className="mt-1 text-sm text-gray-500">
              試合記録は当日の練習日のみ登録可能です
            </p>
          </div>
        )}

        {/* 試合番号 */}
        {practiceSession && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              試合番号 <span className="text-red-500">*</span>
            </label>
            <select
              name="matchNumber"
              value={formData.matchNumber}
              onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              required
            >
              {Array.from({ length: practiceSession.totalMatches }, (_, i) => i + 1).map((num) => (
                <option key={num} value={num}>
                  第{num}試合
                </option>
              ))}
            </select>
            <p className="mt-1 text-sm text-gray-500">
              この練習日は全{practiceSession.totalMatches}試合です
            </p>
          </div>
        )}

        {/* 対戦相手 */}
        {practiceSession && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              対戦相手 <span className="text-red-500">*</span>
            </label>

            {/* 対戦組み合わせがある場合 */}
            {pairing ? (
              <div>
                <div className="mb-2 p-3 bg-purple-50 border border-purple-200 rounded-lg flex items-center gap-2 text-purple-700">
                  <Lock className="w-4 h-4 flex-shrink-0" />
                  <span className="text-sm">
                    対戦組み合わせが設定されています
                  </span>
                </div>
                <div className="p-4 bg-gray-50 border-2 border-gray-300 rounded-lg">
                  <div className="text-center">
                    <p className="text-sm text-gray-600 mb-1">対戦相手</p>
                    <p className="text-xl font-bold text-gray-900">{formData.opponentName}</p>
                  </div>
                </div>
                <p className="mt-2 text-sm text-gray-500">
                  対戦組み合わせにより自動設定されています
                </p>
              </div>
            ) : (
              /* 対戦組み合わせがない場合 */
              <div>
                <div className="mb-2 p-3 bg-blue-50 border border-blue-200 rounded-lg flex items-center gap-2 text-blue-700">
                  <Users className="w-4 h-4 flex-shrink-0" />
                  <span className="text-sm">
                    この日の練習参加者: {availablePlayers.length}名
                  </span>
                </div>

                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="選手名を検索..."
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent mb-2"
                />

                <div className="border border-gray-300 rounded-lg max-h-48 overflow-y-auto">
                  {availablePlayers
                    .filter(p => p.name.toLowerCase().includes(searchTerm.toLowerCase()))
                    .map((player) => (
                      <button
                        key={player.id}
                        type="button"
                        onClick={() => {
                          setFormData(prev => ({
                            ...prev,
                            opponentName: player.name,
                            opponentId: player.id
                          }));
                          setSearchTerm('');
                        }}
                        className={`w-full text-left px-4 py-2 hover:bg-gray-50 transition-colors ${
                          formData.opponentName === player.name ? 'bg-primary-50 text-primary-700 font-medium' : ''
                        }`}
                      >
                        {player.name}
                        {player.rank && <span className="text-sm text-gray-500 ml-2">({player.rank})</span>}
                      </button>
                    ))}
                  {availablePlayers.filter(p => p.name.toLowerCase().includes(searchTerm.toLowerCase())).length === 0 && (
                    <div className="px-4 py-8 text-center text-gray-500">
                      該当する参加者がいません
                    </div>
                  )}
                </div>

                {formData.opponentName && (
                  <div className="mt-2 p-2 bg-gray-50 border border-gray-200 rounded text-sm">
                    選択中: <span className="font-medium">{formData.opponentName}</span>
                  </div>
                )}

                <p className="mt-2 text-sm text-gray-500">
                  この日の練習参加者から選択してください
                </p>
              </div>
            )}
          </div>
        )}

        {/* 結果 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            結果 <span className="text-red-500">*</span>
          </label>
          <div className="grid grid-cols-2 gap-3">
            {['勝ち', '負け'].map((result) => (
              <button
                key={result}
                type="button"
                onClick={() =>
                  setFormData((prev) => ({ ...prev, result }))
                }
                className={`px-4 py-3 rounded-lg border-2 font-medium transition-all ${
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
          <label className="block text-sm font-medium text-gray-700 mb-2">
            枚数差 <span className="text-red-500">*</span>
          </label>
          <div className="relative flex items-center justify-center py-4">
            {/* スクロール可能なピッカー */}
            <div
              className="relative overflow-y-scroll h-48 w-full max-w-xs hide-scrollbar snap-y snap-mandatory"
              onScroll={(e) => {
                const scrollTop = e.target.scrollTop;
                const itemHeight = 48; // 各アイテムの高さ
                const index = Math.round(scrollTop / itemHeight);
                setFormData(prev => ({ ...prev, scoreDifference: index }));
              }}
              ref={(el) => {
                if (el && !el.dataset.initialized) {
                  el.dataset.initialized = 'true';
                  // 初期位置にスクロール
                  el.scrollTop = formData.scoreDifference * 48;
                }
              }}
            >
              {/* 上部パディング */}
              <div className="h-24"></div>

              {/* 0-25枚の選択肢 */}
              {Array.from({ length: 26 }, (_, i) => i).map((num) => (
                <div
                  key={num}
                  className="h-12 flex items-center justify-center snap-center cursor-pointer transition-all"
                  style={{
                    fontSize: formData.scoreDifference === num ? '2rem' : '1.25rem',
                    fontWeight: formData.scoreDifference === num ? '700' : '400',
                    color: formData.scoreDifference === num ? '#2563eb' : '#9ca3af',
                    opacity: Math.abs(formData.scoreDifference - num) > 2 ? 0.3 : 1,
                  }}
                  onClick={() => {
                    setFormData(prev => ({ ...prev, scoreDifference: num }));
                    // スムーズスクロール
                    const container = document.querySelector('.hide-scrollbar');
                    if (container) {
                      container.scrollTo({
                        top: num * 48,
                        behavior: 'smooth'
                      });
                    }
                  }}
                >
                  {num} 枚
                </div>
              ))}

              {/* 下部パディング */}
              <div className="h-24"></div>
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

        {/* メモ */}
        {practiceSessions.length > 0 && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              メモ
            </label>
            <textarea
              name="notes"
              value={formData.notes}
              onChange={handleChange}
              rows="4"
              placeholder="試合の感想、反省点など..."
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
            ></textarea>
          </div>
        )}

        {/* ボタン */}
        {practiceSessions.length > 0 && (
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={loading}
            className="flex-1 flex items-center justify-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-medium"
          >
            <Save className="w-5 h-5" />
            {loading
              ? '保存中...'
              : isEdit
              ? '更新'
              : '登録'}
          </button>
          <button
            type="button"
            onClick={() => navigate('/matches')}
            className="flex items-center justify-center gap-2 bg-gray-100 text-gray-700 px-6 py-3 rounded-lg hover:bg-gray-200 transition-colors font-medium"
          >
            <X className="w-5 h-5" />
            キャンセル
          </button>
        </div>
        )}
      </form>
    </div>
  );
};

export default MatchForm;
