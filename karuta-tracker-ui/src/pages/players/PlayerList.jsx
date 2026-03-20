import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { Search, UserPlus, ChevronRight } from 'lucide-react';
import { sortPlayersByRank } from '../../utils/playerSort';

const PlayerList = () => {
  const navigate = useNavigate();
  const [players, setPlayers] = useState([]);
  const [filteredPlayers, setFilteredPlayers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchPlayers();
  }, []);

  useEffect(() => {
    const sorted = sortPlayersByRank(players);
    if (searchTerm) {
      setFilteredPlayers(
        sorted.filter(p => p.name.toLowerCase().includes(searchTerm.toLowerCase()))
      );
    } else {
      setFilteredPlayers(sorted);
    }
  }, [searchTerm, players]);

  const fetchPlayers = async () => {
    try {
      setLoading(true);
      const response = await playerAPI.getAll();
      setPlayers(response.data);
    } catch (err) {
      console.error('Failed to fetch players:', err);
      setError('選手一覧の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const getRoleBadge = (role) => {
    if (role === 'SUPER_ADMIN') return { label: '管理者', color: 'bg-purple-100 text-purple-700' };
    if (role === 'ADMIN') return { label: '幹部', color: 'bg-blue-100 text-blue-700' };
    return null;
  };

  const isProfileSet = (player) => {
    return !!(player.kyuRank || player.danRank);
  };

  const formatLastLogin = (lastLoginAt) => {
    if (!lastLoginAt) return '未ログイン';
    const date = new Date(lastLoginAt);
    const now = new Date();
    const diffMs = now - date;
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays === 0) return '今日';
    if (diffDays === 1) return '昨日';
    if (diffDays < 30) return `${diffDays}日前`;
    const diffMonths = Math.floor(diffDays / 30);
    if (diffMonths < 12) return `${diffMonths}ヶ月前`;
    return `${Math.floor(diffMonths / 12)}年前`;
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-secondary"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg">
      {/* ナビゲーションバー */}
      <div className="bg-surface border-b border-border-subtle shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <h1 className="text-lg font-semibold text-text">選手管理</h1>
          <button
            onClick={() => navigate('/register')}
            className="flex items-center gap-1.5 px-4 py-2 bg-primary text-text-inverse rounded-lg hover:bg-primary-hover transition-colors text-sm font-medium"
          >
            <UserPlus className="w-4 h-4" />
            新規登録
          </button>
        </div>
      </div>

      <div className="pt-20 pb-24 px-4 max-w-7xl mx-auto">
        {error && (
          <div className="mb-4 p-3 bg-status-danger-surface border border-status-danger/20 text-status-danger rounded-lg text-sm">
            {error}
          </div>
        )}

        {/* 検索 */}
        <div className="relative mb-3">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-text-placeholder" />
          <input
            type="text"
            placeholder="選手名で検索..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 bg-bg border border-border-subtle rounded-lg focus:outline-none focus:ring-2 focus:ring-focus focus:border-transparent text-sm"
          />
        </div>

        <div className="text-xs text-text-muted mb-3">
          {filteredPlayers.length}人の選手
        </div>

        {/* 選手リスト */}
        {filteredPlayers.length === 0 ? (
          <div className="bg-surface rounded-xl p-12 text-center">
            <p className="text-text-muted">
              {searchTerm ? '検索結果が見つかりませんでした' : '選手が登録されていません'}
            </p>
          </div>
        ) : (
          <div className="bg-bg rounded-xl shadow-sm overflow-hidden divide-y divide-border-subtle">
            {filteredPlayers.map((player) => {
              const role = getRoleBadge(player.role);
              const profileDone = isProfileSet(player);

              return (
                <div
                  key={player.id}
                  className="flex items-center px-4 py-3 hover:bg-surface active:bg-surface transition-colors cursor-pointer"
                  onClick={() => navigate(`/players/${player.id}`)}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-text truncate">
                        {player.name}
                      </span>
                      {player.kyuRank && (
                        <span className="flex-shrink-0 text-xs text-text-muted bg-surface px-1.5 py-0.5 rounded">
                          {player.kyuRank}
                        </span>
                      )}
                      {role && (
                        <span className={`flex-shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded ${role.color}`}>
                          {role.label}
                        </span>
                      )}
                      {!profileDone && (
                        <span className="flex-shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded bg-orange-100 text-orange-700">
                          未設定
                        </span>
                      )}
                    </div>
                    <div className="text-[11px] text-text-placeholder mt-0.5">
                      最終ログイン: {formatLastLogin(player.lastLoginAt)}
                    </div>
                  </div>

                  <ChevronRight className="w-4 h-4 text-border-subtle ml-2 flex-shrink-0" />
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default PlayerList;
