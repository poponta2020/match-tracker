import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Menu,
  Calendar,
  MapPin,
  Shuffle,
  Users,
  User,
  LogOut,
  RefreshCw,
  Bell,
  MessageCircle,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';
import { useAuth } from '../context/AuthContext';

/**
 * ホーム画面上部のナビゲーションバー
 *
 * @param {number} unreadCount - 未読通知数
 * @param {boolean} calSyncing - カレンダー同期中フラグ
 * @param {() => void} onCalendarSync - カレンダー同期ハンドラ
 */
const NavigationMenu = ({ unreadCount, calSyncing, onCalendarSync }) => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const menuItem = (icon, label, onClick, className = '') => (
    <button
      onClick={() => { setMenuOpen(false); onClick(); }}
      className={`w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors ${className}`}
    >
      {icon}
      {label}
    </button>
  );

  return (
    <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
      <div className="max-w-7xl mx-auto flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-lg font-semibold text-white">{currentPlayer?.name}</span>
        </div>
        <div className="flex items-center gap-1">
          <Link to="/notifications" className="relative p-2 hover:bg-[#3d5a4c] rounded-full transition-colors">
            <Bell className="w-5 h-5 text-white" />
            {unreadCount > 0 && (
              <span className="absolute top-0.5 right-0.5 bg-red-500 text-white text-[10px] font-bold rounded-full w-4 h-4 flex items-center justify-center">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </Link>
          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
            >
              <Menu className="w-6 h-6 text-white" />
            </button>
            {menuOpen && (
              <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-200 py-1 z-50">
                {menuItem(<User className="w-4 h-4 text-[#6b7280]" />, 'プロフィール', () => navigate('/profile'))}
                {isSuperAdmin() && (
                  <>
                    <div className="border-t border-gray-100 my-1" />
                    {menuItem(<Users className="w-4 h-4 text-[#6b7280]" />, '選手管理', () => navigate('/players'))}
                    {menuItem(<MapPin className="w-4 h-4 text-[#6b7280]" />, '会場管理', () => navigate('/venues'))}
                    {menuItem(<Calendar className="w-4 h-4 text-[#6b7280]" />, '練習日登録', () => navigate('/practice/new'))}
                  </>
                )}
                {isAdmin() && (
                  <>
                    <div className="border-t border-gray-100 my-1" />
                    {menuItem(<Shuffle className="w-4 h-4 text-[#6b7280]" />, '組み合わせ作成', () => navigate('/pairings'))}
                  </>
                )}
                <div className="border-t border-gray-100 my-1" />
                {menuItem(<MessageCircle className="w-4 h-4 text-[#6b7280]" />, 'LINE通知設定', () => navigate('/settings/line'))}
                {isSuperAdmin() && (
                  <>
                    {menuItem(<MessageCircle className="w-4 h-4 text-[#6b7280]" />, 'LINEチャネル管理', () => navigate('/admin/line/channels'))}
                    {menuItem(<MessageCircle className="w-4 h-4 text-[#6b7280]" />, 'LINE通知スケジュール', () => navigate('/admin/line/schedule'))}
                  </>
                )}
                <div className="border-t border-gray-100 my-1" />
                <button
                  onClick={() => { setMenuOpen(false); onCalendarSync(); }}
                  disabled={calSyncing}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors disabled:opacity-50"
                >
                  <RefreshCw className={`w-4 h-4 text-[#6b7280] ${calSyncing ? 'animate-spin' : ''}`} />
                  {calSyncing ? '同期中...' : 'Googleカレンダー同期'}
                </button>
                <div className="border-t border-gray-100 my-1" />
                <button
                  onClick={() => { setMenuOpen(false); logout(); navigate('/login'); }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  ログアウト
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default NavigationMenu;
