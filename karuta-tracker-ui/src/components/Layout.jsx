import { Link, useLocation } from 'react-router-dom';
import {
  Home,
  Swords,
  Calendar,
  BarChart3,
  Settings,
} from 'lucide-react';
import { useBottomNav } from '../context/BottomNavContext';

const Layout = ({ children }) => {
  const location = useLocation();
  const { isVisible } = useBottomNav();

  // ボトムナビゲーションの項目定義
  const bottomNavItems = [
    { name: 'Home', href: '/', icon: Home },
    { name: 'Match', href: '/matches/results', icon: Swords },
    { name: 'Schedule', href: '/practice', icon: Calendar },
    { name: 'Record', href: '/matches', icon: BarChart3 },
    { name: 'Settings', href: '/settings', icon: Settings },
  ];

  // ボトムナビゲーションのアクティブ判定（パスの前方一致も考慮）
  const isBottomNavActive = (href) => {
    if (href === '/') {
      return location.pathname === '/';
    }
    // 完全一致または特定のサブパスのみアクティブにする
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
    if (href === '/settings') {
      return location.pathname === '/settings';
    }
    return location.pathname.startsWith(href);
  };

  return (
    <div className="min-h-screen bg-[#f2ede6]" style={{ paddingBottom: 'calc(3.5rem + env(safe-area-inset-bottom, 0px))' }}>
      {/* ベースナビバー（各ページのナビバーが z-50 で上書きする。ローディング中のフォールバック） */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-40 px-4 py-4">
        <div className="max-w-7xl mx-auto h-7" />
      </div>

      {/* メインコンテンツ */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-16 pb-8">
        {children}
      </main>

      {/* ボトムナビゲーション: bottom-0固定、safe-area分は背景のみ拡張 */}
      <nav className={`fixed bottom-0 left-0 right-0 bg-[#4a6b5a] border-t border-[#3d5a4c] z-50 pb-[env(safe-area-inset-bottom)] transition-transform duration-300 ${isVisible ? 'translate-y-0' : 'translate-y-full'}`}>
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
