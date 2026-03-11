import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import { User, Edit, Trophy, AlertCircle } from 'lucide-react';

const Profile = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [player, setPlayer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchPlayerData = async () => {
      if (!currentPlayer?.id) {
        navigate('/login');
        return;
      }

      try {
        setLoading(true);
        const response = await playerAPI.getById(currentPlayer.id);
        setPlayer(response.data);
      } catch (err) {
        console.error('プロフィール情報の取得に失敗:', err);
        setError('プロフィール情報の取得に失敗しました');
      } finally {
        setLoading(false);
      }
    };

    fetchPlayerData();
  }, [currentPlayer, navigate]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-3">
        <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
        <p className="text-red-800">{error}</p>
      </div>
    );
  }

  if (!player) {
    return null;
  }

  return (
    <div className="space-y-6">
      {/* ヘッダー */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <User className="h-6 w-6 text-primary-600" />
          マイページ
        </h1>
        <button
          onClick={() => navigate('/profile/edit')}
          className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors font-semibold"
        >
          <Edit className="h-5 w-5" />
          編集
        </button>
      </div>

      {/* アカウント情報 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex items-center gap-2 mb-4">
          <User className="h-5 w-5 text-gray-600" />
          <h2 className="text-lg font-semibold text-gray-900">アカウント情報</h2>
        </div>
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              選手名
            </label>
            <p className="text-base text-gray-900">{player.name}</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              権限
            </label>
            <div>
              {player.role === 'SUPER_ADMIN' && (
                <span className="inline-block px-3 py-1 bg-purple-100 text-purple-800 text-sm font-medium rounded">
                  スーパー管理者
                </span>
              )}
              {player.role === 'ADMIN' && (
                <span className="inline-block px-3 py-1 bg-blue-100 text-blue-800 text-sm font-medium rounded">
                  管理者
                </span>
              )}
              {player.role === 'PLAYER' && (
                <span className="inline-block px-3 py-1 bg-gray-100 text-gray-800 text-sm font-medium rounded">
                  選手
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 基本情報 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex items-center gap-2 mb-4">
          <User className="h-5 w-5 text-blue-600" />
          <h2 className="text-lg font-semibold text-gray-900">基本情報</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              性別
            </label>
            <p className="text-base text-gray-900">{player.gender || '未設定'}</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              利き手
            </label>
            <p className="text-base text-gray-900">{player.dominantHand || '未設定'}</p>
          </div>
        </div>
      </div>

      {/* 競技情報 */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
        <div className="flex items-center gap-2 mb-4">
          <Trophy className="h-5 w-5 text-yellow-600" />
          <h2 className="text-lg font-semibold text-gray-900">競技情報</h2>
        </div>
        <div className="space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-500 mb-1">
                級位
              </label>
              <p className="text-base text-gray-900">{player.kyuRank || '未設定'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-500 mb-1">
                段位
              </label>
              <p className="text-base text-gray-900">{player.danRank || '未設定'}</p>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              所属かるた会
            </label>
            <p className="text-base text-gray-900">{player.karutaClub || '未設定'}</p>
          </div>
        </div>
      </div>

      {/* 備考 */}
      {player.remarks && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-3">備考</h2>
          <p className="text-base text-gray-700 whitespace-pre-wrap">{player.remarks}</p>
        </div>
      )}
    </div>
  );
};

export default Profile;
