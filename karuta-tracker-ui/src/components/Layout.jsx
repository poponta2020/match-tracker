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
  PlusSquare,
  Swords,
  Bell,
  Ticket,
  Clock,
} from 'lucide-react';
import { useState, useEffect } from 'react';
import { isSuperAdmin, isAdmin } from '../utils/auth';
import { notificationAPI } from '../api/notifications';

const Layout = ({ children }) => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  // 未読通知数を取得
  useEffect(() => {
    if (currentPlayer?.id) {
      notificationAPI.getUnreadCount(currentPlayer.id)
        .then(res => setUnreadCount(res.data.count || 0))
        .catch(() => {});
    }
  }, [currentPlayer, location.pathname]);

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
    { name: '抽選結果', href: '/lottery/results', icon: Ticket },
    { name: 'キャンセル待ち', href: '/lottery/waitlist', icon: Clock },
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
    if (path === '/matches') return '試合結果';
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
    if (path === '/lottery/results') return '抽選結果';
    if (path === '/lottery/waitlist') return 'キャンセル待ち状況';
    if (path === '/lottery/offer-response') return '繰り上げ参加';
    if (path === '/notifications') return '通知';

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

    return 'わすらログ';
  };

  // ボトムナビゲーションの項目定義
  const bottomNavItems = [
    { name: 'Home', href: '/', icon: Home },
    { name: 'Add', href: '/matches/new', icon: PlusSquare },
    { name: 'Match', href: '/matches/results', icon: Swords },
    { name: 'Schedule', href: '/practice', icon: Calendar },
    { name: 'Record', href: '/matches', icon: BarChart3 },
  ];

  // ボトムナビゲーションのアクティブ判定（パスの前方一致も考慮）
  const isBottomNavActive = (href) => {
    if (href === '/') {
      return location.pathname === '/';
    }
    // 完全一致または特定のサブパスのみアクティブにする
    if (href === '/matches/new') {
      return location.pathname === '/matches/new';
    }
    if (href === '/matches/results') {
      // /matches/results で始まる場合（/matches/results/:sessionId含む）
      return location.pathname.startsWith('/matches/results');
    }
    if (href === '/matches') {
      // /matches で始まるが /matches/new と /matches/results ではない場合
      return location.pathname.startsWith('/matches') &&
             location.pathname !== '/matches/new' &&
             !location.pathname.startsWith('/matches/results');
    }
    return location.pathname.startsWith(href);
  };

  return (
    <div className="min-h-screen bg-[#f2ede6]" style={{ paddingBottom: 'calc(3.5rem + env(safe-area-inset-bottom, 0px))' }}>
      {/* ヘッダーバー */}
      <header className="bg-[#4a6b5a] text-white sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between h-12">
          <h1 className="text-base font-bold truncate">{getPageTitle()}</h1>
          <div className="flex items-center gap-3">
            <Link to="/notifications" className="relative p-1">
              <Bell className="w-5 h-5 text-white" />
              {unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </Link>
            <Link to="/profile" className="p-1">
              <User className="w-5 h-5 text-white" />
            </Link>
          </div>
        </div>
      </header>

      {/* メインコンテンツ */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>

      {/* ボトムナビゲーション: bottom-0固定、safe-area分は背景のみ拡張 */}
      <nav className="fixed bottom-0 left-0 right-0 bg-[#4a6b5a] border-t border-[#3d5a4c] z-50 pb-[env(safe-area-inset-bottom)]">
        <div className="flex justify-around items-center h-14 max-w-7xl mx-auto">
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
                    active ? 'text-white' : 'text-[#b8ccbf]'
                  }`}
                  strokeWidth={active ? 2.5 : 2}
                />
                <span
                  className={`text-xs mt-0.5 ${
                    active ? 'text-white font-semibold' : 'text-[#b8ccbf]'
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
