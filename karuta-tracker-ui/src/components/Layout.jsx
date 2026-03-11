import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  Home,
  Users,
  Trophy,
  BookOpen,
  BarChart3,
  Shuffle,
  LogOut,
  Menu,
  X,
  Calendar,
  Shield,
  MapPin,
  ClipboardList,
  Settings,
  User,
} from 'lucide-react';
import { useState } from 'react';
import { isSuperAdmin, isAdmin } from '../utils/auth';

const Layout = ({ children }) => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
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

  // パスに応じた画面タイトルを取得
  const getPageTitle = () => {
    const path = location.pathname;

    // 完全一致
    if (path === '/') return 'ホーム';
    if (path === '/matches') return '結果入力';
    if (path === '/matches/new') return '結果入力';
    if (path === '/practice') return '練習日程確認';
    if (path === '/practice/participation') return '練習参加登録';
    if (path === '/statistics') return '統計';
    if (path === '/pairings') return '対戦組み合わせ';
    if (path === '/matches/results') return '試合結果閲覧';
    if (path === '/players') return '選手管理';
    if (path === '/venues') return '会場管理';
    if (path === '/profile') return 'マイページ';
    if (path === '/profile/edit') return 'プロフィール編集';

    // パターンマッチ
    if (path.startsWith('/matches/results/')) return '試合結果詳細';
    if (path.startsWith('/matches/bulk-input/')) return '試合結果一括入力';
    if (path.startsWith('/matches/') && path.includes('/edit')) return '結果編集';
    if (path.startsWith('/matches/')) return '結果詳細';
    if (path.startsWith('/practice/') && path.includes('/edit')) return '練習日程編集';
    if (path.startsWith('/practice/') && path !== '/practice/participation') return '練習日程詳細';
    if (path.startsWith('/players/') && path.includes('/edit')) return '選手編集';
    if (path.startsWith('/players/')) return '選手詳細';
    if (path.startsWith('/venues/') && path.includes('/edit')) return '会場編集';
    if (path.startsWith('/venues/new')) return '会場登録';
    if (path.startsWith('/venues/')) return '会場詳細';
    if (path.startsWith('/pairings/')) return '組み合わせ作成';

    return '競技かるた記録';
  };

  // ボトムナビゲーションの項目定義
  const bottomNavItems = [
    { name: 'Home', href: '/', icon: Home },
    { name: 'Result', href: '/matches', icon: Trophy },
    { name: 'Schedule', href: '/practice', icon: Calendar },
    { name: 'Profile', href: '/profile', icon: User },
  ];

  // ボトムナビゲーションのアクティブ判定（パスの前方一致も考慮）
  const isBottomNavActive = (href) => {
    if (href === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(href);
  };

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* ヘッダー */}
      <header className="bg-white shadow-sm sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <div className="flex items-center">
              <Link to="/" className="flex items-center">
                <div className="text-2xl font-bold text-primary-600">
                  {getPageTitle()}
                </div>
              </Link>
            </div>

            {/* デスクトップナビゲーション */}
            <nav className="hidden md:flex space-x-4">
              {navigation.map((item) => {
                const Icon = item.icon;
                const active = isActive(item.href);
                return (
                  <Link
                    key={item.name}
                    to={item.href}
                    className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                      active
                        ? 'bg-primary-50 text-primary-600'
                        : 'text-gray-700 hover:bg-gray-100'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    {item.name}
                  </Link>
                );
              })}
            </nav>

            <div className="flex items-center gap-4">
              <Link
                to="/profile"
                className="hidden md:flex items-center gap-2 px-3 py-2 text-sm hover:bg-gray-100 rounded-lg transition-colors"
              >
                <Settings className="w-4 h-4 text-gray-600" />
                <span className="font-medium text-gray-700">{currentPlayer?.name}</span>
                {currentPlayer?.role === 'SUPER_ADMIN' && (
                  <span className="px-2 py-1 bg-purple-100 text-purple-800 text-xs font-medium rounded">
                    スーパー管理者
                  </span>
                )}
                {currentPlayer?.role === 'ADMIN' && (
                  <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded">
                    管理者
                  </span>
                )}
              </Link>
              <button
                onClick={handleLogout}
                className="hidden md:flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
              >
                <LogOut className="w-4 h-4" />
                ログアウト
              </button>

              {/* モバイルメニューボタン */}
              <button
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                className="md:hidden p-2 rounded-lg hover:bg-gray-100"
              >
                {mobileMenuOpen ? (
                  <X className="w-6 h-6" />
                ) : (
                  <Menu className="w-6 h-6" />
                )}
              </button>
            </div>
          </div>
        </div>

        {/* モバイルメニュー */}
        {mobileMenuOpen && (
          <div className="md:hidden border-t border-gray-200">
            <div className="px-2 pt-2 pb-3 space-y-1">
              <Link
                to="/profile"
                onClick={() => setMobileMenuOpen(false)}
                className="px-3 py-2 flex items-center gap-2 hover:bg-gray-100 rounded-lg"
              >
                <Settings className="w-4 h-4 text-gray-600" />
                <span className="text-sm font-medium text-gray-900">{currentPlayer?.name}</span>
                {currentPlayer?.role === 'SUPER_ADMIN' && (
                  <span className="px-2 py-1 bg-purple-100 text-purple-800 text-xs font-medium rounded">
                    スーパー管理者
                  </span>
                )}
                {currentPlayer?.role === 'ADMIN' && (
                  <span className="px-2 py-1 bg-blue-100 text-blue-800 text-xs font-medium rounded">
                    管理者
                  </span>
                )}
              </Link>
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
                        ? 'bg-primary-50 text-primary-600'
                        : 'text-gray-700 hover:bg-gray-100'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    {item.name}
                  </Link>
                );
              })}
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 rounded-lg"
              >
                <LogOut className="w-4 h-4" />
                ログアウト
              </button>
            </div>
          </div>
        )}
      </header>

      {/* メインコンテンツ */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>

      {/* ボトムナビゲーション */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 z-50">
        <div className="flex justify-around items-center h-16 max-w-7xl mx-auto">
          {bottomNavItems.map((item) => {
            const Icon = item.icon;
            const active = isBottomNavActive(item.href);
            return (
              <Link
                key={item.name}
                to={item.href}
                className="flex flex-col items-center justify-center flex-1 h-full transition-colors"
              >
                <Icon
                  className={`w-6 h-6 ${
                    active ? 'text-primary-600' : 'text-gray-400'
                  }`}
                  strokeWidth={active ? 2.5 : 2}
                />
                <span
                  className={`text-xs mt-1 ${
                    active ? 'text-primary-600 font-semibold' : 'text-gray-500'
                  }`}
                >
                  {item.name}
                </span>
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
};

export default Layout;
