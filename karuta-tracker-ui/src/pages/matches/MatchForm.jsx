import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { matchAPI, playerAPI, practiceAPI } from '../../api';
import { Trophy, Save, X, AlertCircle, Users } from 'lucide-react';

const MatchForm = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const isEdit = !!id;

  const [formData, setFormData] = useState({
    matchDate: new Date().toISOString().split('T')[0],
    opponentName: '',
    result: '勝ち',
    scoreDifference: 0,
    matchNumber: 1,
    notes: '',
  });

  const [players, setPlayers] = useState([]);
  const [availablePlayers, setAvailablePlayers] = useState([]);
  const [practiceSession, setPracticeSession] = useState(null);
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

        // 編集モードの場合、既存データを取得
        if (isEdit) {
          const matchRes = await matchAPI.getById(id);
          const match = matchRes.data;
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
        } else {
          setPracticeSession(null);
          // 練習セッションがない場合、全選手を表示
          setAvailablePlayers(players.filter(p => p.id !== currentPlayer.id));
        }
      } catch (err) {
        // 練習セッションがない場合は全選手を表示
        setPracticeSession(null);
        setAvailablePlayers(players.filter(p => p.id !== currentPlayer.id));
      }
    };

    if (players.length > 0) {
      fetchPracticeSession();
    }
  }, [formData.matchDate, players, currentPlayer]);

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
      const submitData = {
        ...formData,
        playerId: currentPlayer.id,
        scoreDifference: parseInt(formData.scoreDifference),
        matchNumber: parseInt(formData.matchNumber),
      };

      if (isEdit) {
        await matchAPI.update(id, submitData);
      } else {
        await matchAPI.create(submitData);
      }

      navigate('/matches');
    } catch (err) {
      console.error('保存エラー:', err);
      setError(
        err.response?.data?.message ||
          `試合記録の${isEdit ? '更新' : '登録'}に失敗しました`
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-2">
          <Trophy className="w-8 h-8 text-primary-600" />
          {isEdit ? '試合記録編集' : '試合記録登録'}
        </h1>
        <p className="text-gray-600 mt-1">
          {isEdit ? '試合記録を編集します' : '新しい試合記録を登録します'}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-lg shadow-sm p-6 space-y-6">
        {/* 試合日 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            試合日 <span className="text-red-500">*</span>
          </label>
          <div className="relative">
            <input
              type="date"
              name="matchDate"
              value={formData.matchDate}
              onChange={handleChange}
              className={`w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent ${
                formData.matchDate === new Date().toISOString().split('T')[0] ? 'text-transparent' : ''
              }`}
              required
            />
            {formData.matchDate === new Date().toISOString().split('T')[0] && (
              <div
                className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-900 pointer-events-none"
              >
                今日
              </div>
            )}
          </div>
        </div>

        {/* 対戦相手 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            対戦相手 <span className="text-red-500">*</span>
          </label>

          {formData.matchDate && practiceSession && (
            <div className="mb-2 p-3 bg-blue-50 border border-blue-200 rounded-lg flex items-center gap-2 text-blue-700">
              <Users className="w-4 h-4 flex-shrink-0" />
              <span className="text-sm">
                この日の練習参加者: {availablePlayers.length}名
              </span>
            </div>
          )}

          {formData.matchDate && !practiceSession && players.length > 0 && (
            <div className="mb-2 p-3 bg-yellow-50 border border-yellow-200 rounded-lg flex items-center gap-2 text-yellow-700">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span className="text-sm">
                この日は練習日として登録されていません。全選手から選択できます。
              </span>
            </div>
          )}

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
                    setFormData(prev => ({ ...prev, opponentName: player.name }));
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
                {practiceSession ? '該当する参加者がいません' : '選手が登録されていません'}
              </div>
            )}
          </div>

          {formData.opponentName && (
            <div className="mt-2 p-2 bg-gray-50 border border-gray-200 rounded text-sm">
              選択中: <span className="font-medium">{formData.opponentName}</span>
            </div>
          )}

          <p className="mt-2 text-sm text-gray-500">
            {practiceSession
              ? 'この日の練習参加者から選択できます'
              : '全ての登録済み選手から選択できます'}
          </p>
        </div>

        {/* 結果 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            結果 <span className="text-red-500">*</span>
          </label>
          <div className="grid grid-cols-3 gap-3">
            {['勝ち', '負け', '引き分け'].map((result) => (
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
                      : result === '負け'
                      ? 'border-red-500 bg-red-50 text-red-700'
                      : 'border-gray-500 bg-gray-50 text-gray-700'
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
          <div className="flex items-center gap-4">
            <input
              type="number"
              name="scoreDifference"
              value={formData.scoreDifference}
              onChange={handleChange}
              min="0"
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              required
            />
            <span className="text-gray-600">枚</span>
          </div>
        </div>

        {/* 試合番号 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            試合番号 <span className="text-red-500">*</span>
          </label>
          <div className="flex items-center gap-4">
            <span className="text-gray-600">第</span>
            <input
              type="number"
              name="matchNumber"
              value={formData.matchNumber}
              onChange={handleChange}
              min="1"
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              required
            />
            <span className="text-gray-600">試合</span>
          </div>
        </div>

        {/* メモ */}
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

        {/* エラーメッセージ */}
        {error && (
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* ボタン */}
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
      </form>
    </div>
  );
};

export default MatchForm;
