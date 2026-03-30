import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { calendarAPI } from '../api';
import {
  User,
  Building2,
  MessageSquare,
  RefreshCw,
  Calendar,
  Shuffle,
  ClipboardList,
  Settings,
  Users,
  MapPin,
  LogOut,
  X,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';

const SettingsPage = () => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const [calSyncing, setCalSyncing] = useState(false);
  const [calSyncMessage, setCalSyncMessage] = useState(null);
  const [calSyncError, setCalSyncError] = useState(false);

  // モバイル判定（スタンドアロンPWA含む）
  const isMobile = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent)
    || window.matchMedia('(display-mode: standalone)').matches
    || window.navigator.standalone === true;

  // 同期実行（トークン取得後の共通処理）
  const executeSyncWithToken = useCallback(async (accessToken, playerId) => {
    setCalSyncing(true);
    setCalSyncMessage(null);
    setCalSyncError(false);
    try {
      const result = await calendarAPI.sync(accessToken, playerId);
      const data = result.data;
      const parts = [];
      if (data.createdCount > 0) parts.push(`${data.createdCount}件作成`);
      if (data.updatedCount > 0) parts.push(`${data.updatedCount}件更新`);
      if (data.deletedCount > 0) parts.push(`${data.deletedCount}件削除`);
      if (data.unchangedCount > 0) parts.push(`${data.unchangedCount}件変更なし`);
      if (data.errorCount > 0) parts.push(`${data.errorCount}件エラー`);
      setCalSyncMessage(
        `カレンダー同期完了: ${parts.join('、') || '対象の予定なし'}`
      );
      setCalSyncError(data.errorCount > 0);
    } catch (err) {
      console.error('Calendar sync error:', err);
      setCalSyncMessage('カレンダー同期に失敗しました');
      setCalSyncError(true);
    } finally {
      setCalSyncing(false);
    }
  }, []);

  // Google OAuth リダイレクト戻り時のトークン処理
  useEffect(() => {
    const hash = window.location.hash;
    if (!hash || !hash.includes('access_token')) return;

    const params = new URLSearchParams(hash.substring(1));
    const accessToken = params.get('access_token');
    const savedPlayerId = sessionStorage.getItem('cal_sync_player_id');

    window.history.replaceState(null, '', window.location.pathname + window.location.search);
    sessionStorage.removeItem('cal_sync_player_id');

    if (accessToken && savedPlayerId) {
      executeSyncWithToken(accessToken, Number(savedPlayerId));
    }
  }, [executeSyncWithToken]);

  // Google Calendar同期ハンドラー
  const handleCalendarSync = useCallback(() => {
    if (!currentPlayer?.id) return;

    setCalSyncing(true);
    setCalSyncMessage(null);
    setCalSyncError(false);

    if (isMobile) {
      sessionStorage.setItem('cal_sync_player_id', String(currentPlayer.id));
      const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
      const redirectUri = encodeURIComponent(window.location.origin + '/');
      const scope = encodeURIComponent('https://www.googleapis.com/auth/calendar.events');
      const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=token&scope=${scope}&prompt=consent`;
      window.location.href = authUrl;
      return;
    }

    if (!window.google?.accounts?.oauth2) {
      setCalSyncMessage('Google認証の読み込みに失敗しました。ページを再読み込みしてください。');
      setCalSyncError(true);
      setCalSyncing(false);
      return;
    }

    const tokenClient = window.google.accounts.oauth2.initTokenClient({
      client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID,
      scope: 'https://www.googleapis.com/auth/calendar.events',
      callback: async (tokenResponse) => {
        if (tokenResponse.error) {
          setCalSyncMessage('Google認証がキャンセルされました');
          setCalSyncError(true);
          setCalSyncing(false);
          return;
        }
        await executeSyncWithToken(tokenResponse.access_token, currentPlayer.id);
      },
    });
    tokenClient.requestAccessToken();
  }, [currentPlayer, isMobile, executeSyncWithToken]);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // グリッド項目定義（ロール別表示制御）
  const gridItems = [
    { label: 'プロフィール', icon: User, path: '/profile', visible: true },
    { label: '参加練習会', icon: Building2, path: '/settings/organizations', visible: true },
    { label: '通知設定', icon: MessageSquare, path: '/settings/notifications', visible: true },
    { label: 'Googleカレンダー同期', icon: RefreshCw, action: handleCalendarSync, visible: true, syncing: calSyncing },
    { label: '練習日登録', icon: Calendar, path: '/practice/new', visible: isAdmin() },
    { label: '組み合わせ作成', icon: Shuffle, path: '/pairings', visible: isAdmin() },
    { label: 'LINE通知スケジュール', icon: MessageSquare, path: '/admin/line/schedule', visible: isAdmin() },
    { label: '伝助管理', icon: ClipboardList, path: '/admin/densuke', visible: isAdmin() },
    { label: 'システム設定', icon: Settings, path: '/admin/settings', visible: isAdmin() },
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

      {/* カレンダー同期メッセージ */}
      {calSyncMessage && (
        <div className={`mx-4 mb-4 p-3 text-sm rounded-lg flex justify-between items-center ${
          calSyncError
            ? 'bg-red-50 text-red-700 border border-red-200'
            : 'bg-[#d4ddd7] text-[#374151]'
        }`}>
          <span>{calSyncMessage}</span>
          <button
            onClick={() => setCalSyncMessage(null)}
            className={`ml-2 ${calSyncError ? 'text-red-400 hover:text-red-600' : 'text-[#6b7280] hover:text-[#374151]'}`}
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* グリッドコンテナ */}
      <div className="grid grid-cols-3 gap-4 px-4">
        {gridItems.map((item) => {
          const Icon = item.icon;
          const handleClick = () => {
            if (item.action) {
              item.action();
            } else {
              navigate(item.path);
            }
          };
          return (
            <button
              key={item.label}
              onClick={handleClick}
              disabled={item.syncing}
              className="flex flex-col items-center gap-2 py-4 rounded-xl hover:bg-[#f0f4f1] transition-colors disabled:opacity-50"
            >
              <div className="w-14 h-14 rounded-2xl bg-[#4a6b5a] flex items-center justify-center">
                <Icon className={`w-7 h-7 text-white ${item.syncing ? 'animate-spin' : ''}`} />
              </div>
              <span className="text-xs text-[#374151] text-center leading-tight">
                {item.syncing ? '同期中...' : item.label}
              </span>
            </button>
          );
        })}
      </div>
    </>
  );
};

export default SettingsPage;
