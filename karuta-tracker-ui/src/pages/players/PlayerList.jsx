import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { inviteAPI } from '../../api/invite';
import { useAuth } from '../../context/AuthContext';
import { Search, UserPlus, ChevronRight, Link2, UserCheck, Copy, Check, X } from 'lucide-react';
import { sortPlayersByRank } from '../../utils/playerSort';
import LoadingScreen from '../../components/LoadingScreen';

const PlayerList = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [players, setPlayers] = useState([]);
  const [filteredPlayers, setFilteredPlayers] = useState([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [inviteMessage, setInviteMessage] = useState(null);
  const [inviteGenerating, setInviteGenerating] = useState(null);

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

  const generateInviteLink = async (type) => {
    setInviteGenerating(type);
    setInviteMessage(null);
    try {
      const response = await inviteAPI.createToken(type, currentPlayer.id);
      const url = `${window.location.origin}/register/${response.data.token}`;
      const label = type === 'MULTI_USE' ? 'グループ招待リンク' : '個人招待リンク';
      try {
        await navigator.clipboard.writeText(url);
        setInviteMessage({ text: `${label}をコピーしました`, success: true });
        setTimeout(() => setInviteMessage(null), 3000);
      } catch {
        setInviteMessage({ text: `${label}を生成しました（長押ししてコピーしてください）`, success: true, url });
      }
    } catch {
      setInviteMessage({ text: '招待リンクの生成に失敗しました', success: false });
      setTimeout(() => setInviteMessage(null), 3000);
    } finally {
      setInviteGenerating(null);
    }
  };

  if (loading) {
    return <LoadingScreen />;
  }

  return (
    <div className="min-h-screen bg-[#f2ede6]">
      {/* ナビゲーションバー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <h1 className="text-lg font-semibold text-white">選手管理</h1>
          <button
            onClick={() => navigate('/players/new')}
            className="flex items-center gap-1.5 px-4 py-2 bg-white text-[#4a6b5a] rounded-lg hover:bg-white/90 transition-colors text-sm font-medium"
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

        {/* 招待リンク */}
        <div className="mb-3 bg-white rounded-xl shadow-sm p-3">
          <p className="text-xs text-[#6b7280] mb-2">招待リンクを発行してLINE等で共有</p>
          <div className="flex gap-2">
            <button
              onClick={() => generateInviteLink('MULTI_USE')}
              disabled={inviteGenerating}
              className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] transition-colors text-xs font-medium disabled:opacity-50"
            >
              <Link2 className="w-3.5 h-3.5" />
              {inviteGenerating === 'MULTI_USE' ? '生成中...' : 'グループ用'}
            </button>
            <button
              onClick={() => generateInviteLink('SINGLE_USE')}
              disabled={inviteGenerating}
              className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 bg-[#f0f4f1] text-[#4a6b5a] rounded-lg hover:bg-[#e4ebe6] transition-colors text-xs font-medium disabled:opacity-50"
            >
              <UserCheck className="w-3.5 h-3.5" />
              {inviteGenerating === 'SINGLE_USE' ? '生成中...' : '個人用（1回限り）'}
            </button>
          </div>
          {inviteMessage && (
            <div className={`mt-2 text-xs px-2 py-1.5 rounded ${
              inviteMessage.success
                ? 'bg-[#d4ddd7] text-[#374151]'
                : 'bg-red-50 text-red-700'
            }`}>
              <span className="flex items-center gap-1">
                {inviteMessage.success ? <Check className="w-3.5 h-3.5" /> : <X className="w-3.5 h-3.5" />}
                {inviteMessage.text}
              </span>
              {inviteMessage.url && (
                <input
                  type="text"
                  readOnly
                  value={inviteMessage.url}
                  onFocus={(e) => e.target.select()}
                  className="mt-1.5 w-full px-2 py-1.5 bg-white border border-[#c5cfc9] rounded text-xs text-[#374151] select-all"
                />
              )}
            </div>
          )}
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
