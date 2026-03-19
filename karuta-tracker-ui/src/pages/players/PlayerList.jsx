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
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#4a6b5a]"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f2ede6]">
      {/* ナビゲーションバー */}
      <div className="bg-[#d4ddd7] border-b border-[#c5cec8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <h1 className="text-lg font-semibold text-[#374151]">選手管理</h1>
          <button
            onClick={() => navigate('/register')}
            className="flex items-center gap-1.5 px-4 py-2 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors text-sm font-medium"
          >
            <UserPlus className="w-4 h-4" />
            新規登録
          </button>
        </div>
      </div>

      <div className="pt-20 pb-24 px-4 max-w-7xl mx-auto">
        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
            {error}
          </div>
        )}

        {/* 検索 */}
        <div className="relative mb-3">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#9ca3af]" />
          <input
            type="text"
            placeholder="選手名で検索..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 bg-white border border-[#d4ddd7] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent text-sm"
          />
        </div>

        <div className="text-xs text-[#6b7280] mb-3">
          {filteredPlayers.length}人の選手
        </div>

        {/* 選手リスト */}
        {filteredPlayers.length === 0 ? (
          <div className="bg-[#f9f6f2] rounded-xl p-12 text-center">
            <p className="text-[#6b7280]">
              {searchTerm ? '検索結果が見つかりませんでした' : '選手が登録されていません'}
            </p>
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm overflow-hidden divide-y divide-gray-100">
            {filteredPlayers.map((player) => {
              const role = getRoleBadge(player.role);
              const profileDone = isProfileSet(player);

              return (
                <div
                  key={player.id}
                  className="flex items-center px-4 py-3 hover:bg-[#f9f9f7] active:bg-[#f0f4f1] transition-colors cursor-pointer"
                  onClick={() => navigate(`/players/${player.id}`)}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-[#374151] truncate">
                        {player.name}
                      </span>
                      {player.kyuRank && (
                        <span className="flex-shrink-0 text-xs text-[#6b7280] bg-[#f0f4f1] px-1.5 py-0.5 rounded">
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
                    <div className="text-[11px] text-[#9ca3af] mt-0.5">
                      最終ログイン: {formatLastLogin(player.lastLoginAt)}
                    </div>
                  </div>

                  <ChevronRight className="w-4 h-4 text-[#d1d5db] ml-2 flex-shrink-0" />
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
