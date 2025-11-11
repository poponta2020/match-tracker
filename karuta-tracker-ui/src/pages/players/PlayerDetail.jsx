import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { User, Edit, ArrowLeft, Users } from 'lucide-react';

const PlayerDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [player, setPlayer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchPlayer();
  }, [id]);

  const fetchPlayer = async () => {
    try {
      setLoading(true);
      const response = await playerAPI.getById(id);
      setPlayer(response.data);
    } catch (err) {
      console.error('Failed to fetch player:', err);
      setError('選手情報の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const getGenderLabel = (gender) => {
    switch (gender) {
      case 'MALE':
        return '男性';
      case 'FEMALE':
        return '女性';
      case 'OTHER':
        return 'その他';
      default:
        return '未設定';
    }
  };

  const getDominantHandLabel = (hand) => {
    switch (hand) {
      case 'RIGHT':
        return '右利き';
      case 'LEFT':
        return '左利き';
      default:
        return '未設定';
    }
  };

  const getRankDisplay = (danRank, kyuRank) => {
    if (danRank) return danRank;
    if (kyuRank) return kyuRank;
    return '未設定';
  };

  const formatDate = (dateString) => {
    if (!dateString) return '未設定';
    const date = new Date(dateString);
    return date.toLocaleDateString('ja-JP', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-600">読み込み中...</div>
      </div>
    );
  }

  if (error || !player) {
    return (
      <div className="space-y-6">
        <div className="bg-red-50 border border-red-200 p-4 rounded-lg text-red-700">
          {error || '選手が見つかりませんでした'}
        </div>
        <button
          onClick={() => navigate('/players')}
          className="flex items-center gap-2 text-primary-600 hover:text-primary-800"
        >
          <ArrowLeft className="w-5 h-5" />
          選手一覧に戻る
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate('/players')}
          className="flex items-center gap-2 text-primary-600 hover:text-primary-800"
        >
          <ArrowLeft className="w-5 h-5" />
          選手一覧に戻る
        </button>
        <button
          onClick={() => navigate(`/players/${id}/edit`)}
          className="flex items-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors"
        >
          <Edit className="w-5 h-5" />
          編集
        </button>
      </div>

      <div className="bg-white shadow-sm rounded-lg overflow-hidden">
        <div className="bg-primary-600 text-white px-6 py-4">
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <User className="w-8 h-8" />
            {player.name}
          </h1>
        </div>

        <div className="p-6 space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                性別
              </label>
              <div className="text-lg text-gray-900">
                {getGenderLabel(player.gender)}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                利き手
              </label>
              <div className="text-lg text-gray-900">
                {getDominantHandLabel(player.dominantHand)}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                段位・級位
              </label>
              <div className="text-lg text-gray-900">
                {getRankDisplay(player.danRank, player.kyuRank)}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                所属かるた会
              </label>
              <div className="text-lg text-gray-900">
                {player.karutaClub || '未設定'}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                ロール
              </label>
              <div>
                <span
                  className={`inline-block px-3 py-1 text-sm font-semibold rounded-full ${
                    player.role === 'ADMIN'
                      ? 'bg-purple-100 text-purple-800'
                      : 'bg-green-100 text-green-800'
                  }`}
                >
                  {player.role === 'ADMIN' ? '管理者' : '一般ユーザー'}
                </span>
              </div>
            </div>
          </div>

          {player.remarks && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                備考
              </label>
              <div className="text-gray-900 bg-gray-50 p-4 rounded-lg whitespace-pre-wrap">
                {player.remarks}
              </div>
            </div>
          )}

          <div className="border-t pt-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
              <Users className="w-6 h-6 text-primary-600" />
              システム情報
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
              <div>
                <span className="text-gray-600">登録日:</span>
                <span className="ml-2 text-gray-900">
                  {formatDate(player.createdAt)}
                </span>
              </div>
              <div>
                <span className="text-gray-600">最終更新:</span>
                <span className="ml-2 text-gray-900">
                  {formatDate(player.updatedAt)}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default PlayerDetail;
