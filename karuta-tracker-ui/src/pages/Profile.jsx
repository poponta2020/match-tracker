import { useState, useEffect } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import {
  User,
  Edit,
  Trophy,
  AlertCircle,
  Menu,
  X,
  Home,
  Calendar,
  BookOpen,
  BarChart3,
  Shuffle,
  ClipboardList,
  Users,
  MapPin,
  LogOut,
  PlusSquare,
} from 'lucide-react';
import { isSuperAdmin, isAdmin } from '../utils/auth';

const Profile = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentPlayer, logout } = useAuth();
  const [player, setPlayer] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // 基本ナビゲーション（全ユーザー共通）
  const baseNavigation = [
    { name: 'ホーム', href: '/', icon: Home },
    { name: '結果入力', href: '/matches', icon: Trophy },
    { name: '練習日程確認', href: '/practice', icon: BookOpen },
    { name: '練習参加登録', href: '/practice/participation', icon: Calendar },
    { name: '統計', href: '/statistics', icon: BarChart3 },
  ];

  // 管理者メニュー（ADMIN + SUPER_ADMIN）
  const adminNavigation = [
    { name: '対戦組み合わせ', href: '/pairings', icon: Shuffle },
    { name: '試合結果閲覧', href: '/matches/results', icon: ClipboardList },
  ];

  // スーパー管理者メニュー（SUPER_ADMINのみ）
  const superAdminNavigation = [
    { name: '選手管理', href: '/players', icon: Users },
    { name: '会場管理', href: '/venues', icon: MapPin },
  ];

  // ロールに応じてナビゲーションを組み立て
  const navigation = [
    ...baseNavigation,
    ...(isAdmin() ? adminNavigation : []),
    ...(isSuperAdmin() ? superAdminNavigation : []),
  ];

  const isActive = (path) => {
    return location.pathname === path;
  };

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
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#82655a] mx-auto"></div>
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
      {/* ハンバーガーメニューバー */}
      <div className="bg-[#e2d9d0] border-b border-[#d0c5b8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <h1 className="text-lg font-semibold text-[#5f3a2d]">マイページ</h1>
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="p-2 rounded-lg hover:bg-[#d0c5b8] transition-colors"
          >
            {mobileMenuOpen ? (
              <X className="w-6 h-6 text-[#5f3a2d]" />
            ) : (
              <Menu className="w-6 h-6 text-[#5f3a2d]" />
            )}
          </button>
        </div>

        {/* モバイルメニュー */}
        {mobileMenuOpen && (
          <div className="border-t border-[#d0c5b8] mt-4 pt-4 max-w-7xl mx-auto">
            <div className="space-y-1">
              {navigation.map((item) => {
                const Icon = item.icon;
                const active = isActive(item.href);
                return (
                  <Link
                    key={item.name}
                    to={item.href}
                    onClick={() => setMobileMenuOpen(false)}
                    className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium ${
                      active
                        ? 'bg-[#e8e1d8] text-[#82655a]'
                        : 'hover:bg-[#e2d9d0]'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    {item.name}
                  </Link>
                );
              })}
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm font-medium hover:bg-[#e2d9d0] rounded-lg"
              >
                <LogOut className="w-4 h-4" />
                ログアウト
              </button>
            </div>
          </div>
        )}
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-20 space-y-6">
      {/* ヘッダー */}
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold flex items-center gap-2">
          <User className="h-6 w-6 text-[#82655a]" />
          プロフィール情報
        </h2>
        <button
          onClick={() => navigate('/profile/edit')}
          className="flex items-center gap-2 px-4 py-2 bg-[#82655a] text-white rounded-lg hover:bg-[#6b5048] transition-colors font-semibold"
        >
          <Edit className="h-5 w-5" />
          編集
        </button>
      </div>

      {/* アカウント情報 */}
      <div className="bg-[#f9f6f2] rounded-xl shadow-sm border border-[#d0c5b8] p-6">
        <div className="flex items-center gap-2 mb-4">
          <User className="h-5 w-5 text-gray-600" />
          <h2 className="text-lg font-semibold">アカウント情報</h2>
        </div>
        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              選手名
            </label>
            <p className="text-base">{player.name}</p>
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
      <div className="bg-[#f9f6f2] rounded-xl shadow-sm border border-[#d0c5b8] p-6">
        <div className="flex items-center gap-2 mb-4">
          <User className="h-5 w-5 text-[#82655a]" />
          <h2 className="text-lg font-semibold">基本情報</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              性別
            </label>
            <p className="text-base">{player.gender || '未設定'}</p>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              利き手
            </label>
            <p className="text-base">{player.dominantHand || '未設定'}</p>
          </div>
        </div>
      </div>

      {/* 競技情報 */}
      <div className="bg-[#f9f6f2] rounded-xl shadow-sm border border-[#d0c5b8] p-6">
        <div className="flex items-center gap-2 mb-4">
          <Trophy className="h-5 w-5 text-yellow-600" />
          <h2 className="text-lg font-semibold">競技情報</h2>
        </div>
        <div className="space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-500 mb-1">
                級位
              </label>
              <p className="text-base">{player.kyuRank || '未設定'}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-500 mb-1">
                段位
              </label>
              <p className="text-base">{player.danRank || '未設定'}</p>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-500 mb-1">
              所属かるた会
            </label>
            <p className="text-base">{player.karutaClub || '未設定'}</p>
          </div>
        </div>
      </div>

      {/* 備考 */}
      {player.remarks && (
        <div className="bg-[#f9f6f2] rounded-xl shadow-sm border border-[#d0c5b8] p-6">
          <h2 className="text-lg font-semibold mb-3">備考</h2>
          <p className="text-base text-gray-700 whitespace-pre-wrap">{player.remarks}</p>
        </div>
      )}
    </div>
    </div>
  );
};

export default Profile;
