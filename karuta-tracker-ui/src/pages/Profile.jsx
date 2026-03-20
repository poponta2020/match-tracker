import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import { Edit, AlertCircle } from 'lucide-react';

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
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-secondary mx-auto"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-status-danger-surface border border-status-danger/20 rounded-lg p-4 flex items-center gap-3">
        <AlertCircle className="h-5 w-5 text-status-danger flex-shrink-0" />
        <p className="text-status-danger">{error}</p>
      </div>
    );
  }

  if (!player) return null;

  const roleLabel = {
    SUPER_ADMIN: { text: 'スーパー管理者', cls: 'bg-status-info-surface text-status-info' },
    ADMIN: { text: '管理者', cls: 'bg-status-info-surface text-status-info' },
    PLAYER: { text: '選手', cls: 'bg-surface-disabled text-text-muted' },
  }[player.role] || { text: player.role, cls: 'bg-surface-disabled text-text-muted' };

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
          <h1 className="text-2xl font-bold text-text">{player.name}</h1>
          <span className={`inline-block mt-1.5 px-2 py-0.5 text-xs font-medium rounded ${roleLabel.cls}`}>
            {roleLabel.text}
          </span>
        </div>
        <button
          onClick={() => navigate('/profile/edit')}
          className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-secondary border border-secondary rounded-lg hover:bg-secondary hover:text-text-inverse transition-colors"
        >
          <Edit className="w-3.5 h-3.5" />
          編集
        </button>
      </div>

      {/* 情報リスト */}
      {items.length > 0 && (
        <div className="bg-surface rounded-lg shadow-sm p-4">
          <div className="divide-y divide-border-subtle">
            {items.map((item) => (
              <div key={item.label} className="flex justify-between py-3 first:pt-0 last:pb-0">
                <span className="text-sm text-text-muted">{item.label}</span>
                <span className="text-sm font-medium text-text">{item.value}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {items.length === 0 && (
        <p className="text-sm text-text-muted text-center py-8">
          プロフィール情報が未設定です
        </p>
      )}
    </div>
  );
};

export default Profile;
