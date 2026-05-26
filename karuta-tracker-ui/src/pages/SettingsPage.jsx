import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  User,
  Building2,
  Bell,
  MessageSquare,
  Calendar,
  Shuffle,
  ClipboardList,
  Dices,
  Users,
  MapPin,
  LogOut,
  Rss,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';

const SettingsPage = () => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // グリッド項目定義（ロール別表示制御）
  const gridItems = [
    { label: 'プロフィール', icon: User, path: '/profile', visible: true },
    { label: '参加練習会', icon: Building2, path: '/settings/organizations', visible: true },
    { label: '通知設定', icon: Bell, path: '/settings/notifications', visible: true },
    { label: 'メンター管理', icon: Users, path: '/settings/mentor', visible: true },
    { label: 'カレンダー購読', icon: Rss, path: '/settings/calendar', visible: true },
    { label: '練習日登録', icon: Calendar, path: '/practice/new', visible: isAdmin() },
    { label: '組み合わせ作成', icon: Shuffle, path: '/pairings', visible: true },
    { label: 'LINE通知スケジュール', icon: MessageSquare, path: '/admin/line/schedule', visible: isAdmin() },
    { label: '伝助管理', icon: ClipboardList, path: '/admin/densuke', visible: isAdmin() },
    { label: '抽選管理', icon: Dices, path: '/admin/lottery', visible: isAdmin() },
    { label: '選手管理', icon: Users, path: '/players', visible: isSuperAdmin() },
    { label: '会場管理', icon: MapPin, path: '/venues', visible: isSuperAdmin() },
    { label: 'LINEチャネル管理', icon: MessageSquare, path: '/admin/line/channels', visible: isSuperAdmin() },
  ].filter(item => item.visible);

  return (
    <>
      {/* 独自ヘッダー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <span className="text-lg font-semibold text-white">Settings</span>
          <button
            onClick={handleLogout}
            className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
          >
            <LogOut className="w-5 h-5 text-white" />
          </button>
        </div>
      </div>

      {/* グリッドコンテナ */}
      <div className="grid grid-cols-3 gap-4 px-4 pt-2">
        {gridItems.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.label}
              onClick={() => navigate(item.path)}
              className="flex flex-col items-center gap-2 py-4 rounded-xl hover:bg-[#f0f4f1] transition-colors"
            >
              <div className="w-14 h-14 rounded-2xl bg-[#4a6b5a] flex items-center justify-center">
                <Icon className="w-7 h-7 text-white" />
              </div>
              <span className="text-xs text-[#374151] text-center leading-tight">
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </>
  );
};

export default SettingsPage;
