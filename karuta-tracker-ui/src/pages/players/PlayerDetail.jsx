import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { ChevronLeft, Edit } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const PlayerDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [player, setPlayer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
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
    fetchPlayer();
  }, [id]);

  const getGenderLabel = (gender) => {
    switch (gender) {
      case 'MALE': return '男性';
      case 'FEMALE': return '女性';
      case 'OTHER': return 'その他';
      default: return null;
    }
  };

  const getDominantHandLabel = (hand) => {
    switch (hand) {
      case 'RIGHT': return '右利き';
      case 'LEFT': return '左利き';
      default: return null;
    }
  };

  const roleLabel = (role) => {
    const map = {
      SUPER_ADMIN: { text: 'スーパー管理者', cls: 'bg-purple-100 text-purple-700' },
      ADMIN: { text: '管理者', cls: 'bg-blue-100 text-blue-700' },
      PLAYER: { text: '選手', cls: 'bg-gray-100 text-gray-600' },
    };
    return map[role] || { text: role, cls: 'bg-gray-100 text-gray-600' };
  };

  if (loading) {
    return <LoadingScreen />;
  }

  if (error || !player) {
    return (
      <div className="space-y-4">
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 text-sm">
          {error || '選手が見つかりませんでした'}
        </div>
        <button
          onClick={() => navigate('/players')}
          className="flex items-center gap-1 text-sm text-[#4a6b5a] hover:text-[#3d5a4c]"
        >
          <ChevronLeft className="w-4 h-4" />
          選手一覧に戻る
        </button>
      </div>
    );
  }

  const role = roleLabel(player.role);

  const items = [
    { label: '性別', value: getGenderLabel(player.gender) },
    { label: '利き手', value: getDominantHandLabel(player.dominantHand) },
    { label: '級位', value: player.kyuRank },
    { label: '段位', value: player.danRank },
    { label: '所属かるた会', value: player.karutaClub },
    { label: '備考', value: player.remarks },
  ].filter(item => item.value);

  return (
    <div className="max-w-7xl mx-auto">
      {/* ナビゲーションバー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button
            onClick={() => navigate('/players')}
            className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-white" />
          </button>
          <h1 className="text-lg font-semibold text-white">選手詳細</h1>
          <button
            onClick={() => navigate(`/players/${id}/edit`)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-white border border-white/60 rounded-lg hover:bg-white/20 transition-colors"
          >
            <Edit className="w-3.5 h-3.5" />
            編集
          </button>
        </div>
      </div>

      {/* コンテンツ */}
      <div className="pt-20 px-4">
        {/* ヘッダー：名前 + ロール */}
        <div className="mb-6">
          <h2 className="text-2xl font-bold text-[#374151]">{player.name}</h2>
          <span className={`inline-block mt-1.5 px-2 py-0.5 text-xs font-medium rounded ${role.cls}`}>
            {role.text}
          </span>
        </div>

        {/* 情報リスト */}
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
            選手情報が未設定です
          </p>
        )}
      </div>
    </div>
  );
};

export default PlayerDetail;
