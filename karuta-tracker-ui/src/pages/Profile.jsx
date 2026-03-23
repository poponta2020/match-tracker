import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import { Edit, AlertCircle } from 'lucide-react';
import LoadingScreen from '../components/LoadingScreen';

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
    return <LoadingScreen />;
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-3">
        <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
        <p className="text-red-800">{error}</p>
      </div>
    );
  }

  if (!player) return null;

  const roleLabel = {
    SUPER_ADMIN: { text: 'スーパー管理者', cls: 'bg-purple-100 text-purple-700' },
    ADMIN: { text: '管理者', cls: 'bg-blue-100 text-blue-700' },
    PLAYER: { text: '選手', cls: 'bg-gray-100 text-gray-600' },
  }[player.role] || { text: player.role, cls: 'bg-gray-100 text-gray-600' };

  const items = [
    { label: '性別', value: player.gender },
    { label: '利き手', value: player.dominantHand },
    { label: '級位', value: player.kyuRank },
    { label: '段位', value: player.danRank },
    { label: '所属かるた会', value: player.karutaClub },
    { label: '備考', value: player.remarks },
  ].filter(item => item.value);

  return (
    <div className="space-y-6">
      {/* ヘッダー：名前 + ロール + 編集ボタン */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[#374151]">{player.name}</h1>
          <span className={`inline-block mt-1.5 px-2 py-0.5 text-xs font-medium rounded ${roleLabel.cls}`}>
            {roleLabel.text}
          </span>
        </div>
        <button
          onClick={() => navigate('/profile/edit')}
          className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-[#4a6b5a] border border-[#4a6b5a] rounded-lg hover:bg-[#4a6b5a] hover:text-white transition-colors"
        >
          <Edit className="w-3.5 h-3.5" />
          編集
        </button>
      </div>

      {/* 情報リスト（PlayerDetailと同じカードスタイル） */}
      {items.length > 0 && (
        <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-4">
          <div className="divide-y divide-[#e2d9d0]">
            {items.map((item) => (
              <div key={item.label} className="flex justify-between py-3 first:pt-0 last:pb-0">
                <span className="text-sm text-[#6b7280]">{item.label}</span>
                <span className="text-sm font-medium text-[#374151]">{item.value}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {items.length === 0 && (
        <p className="text-sm text-[#6b7280] text-center py-8">
          プロフィール情報が未設定です
        </p>
      )}
    </div>
  );
};

export default Profile;
