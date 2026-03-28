import { useState, useRef, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { calendarAPI } from '../api';
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
  X,
  MessageSquare,
  Settings,
  ClipboardList,
  Building2,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';

const NavigationMenu = ({ unreadCount }) => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
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

    // URLハッシュからトークンをパース
    const params = new URLSearchParams(hash.substring(1));
    const accessToken = params.get('access_token');
    const savedPlayerId = sessionStorage.getItem('cal_sync_player_id');

    // ハッシュをクリーンアップ
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

  // メニュー外クリックで閉じる
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <>
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
                <button
                  onClick={() => { setMenuOpen(false); navigate('/profile'); }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                >
                  <User className="w-4 h-4 text-[#6b7280]" />
                  プロフィール
                </button>
                {isSuperAdmin() && (
                  <>
                    <div className="border-t border-gray-100 my-1" />
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/players'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                    >
                      <Users className="w-4 h-4 text-[#6b7280]" />
                      選手管理
                    </button>
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/venues'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                    >
                      <MapPin className="w-4 h-4 text-[#6b7280]" />
                      会場管理
                    </button>
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/practice/new'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                    >
                      <Calendar className="w-4 h-4 text-[#6b7280]" />
                      練習日登録
                    </button>
                  </>
                )}
                {isAdmin() && (
                  <>
                    <div className="border-t border-gray-100 my-1" />
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/pairings'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                    >
                      <Shuffle className="w-4 h-4 text-[#6b7280]" />
                      組み合わせ作成
                    </button>
                  </>
                )}
                <div className="border-t border-gray-100 my-1" />
                <button
                  onClick={() => { setMenuOpen(false); navigate('/settings/organizations'); }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                >
                  <Building2 className="w-4 h-4 text-[#6b7280]" />
                  参加練習会
                </button>
                <button
                  onClick={() => { setMenuOpen(false); navigate('/settings/notifications'); }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                >
                  <MessageSquare className="w-4 h-4 text-[#6b7280]" />
                  通知設定
                </button>
                {isSuperAdmin() && (
                  <>
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/admin/line/channels'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                    >
                      <MessageSquare className="w-4 h-4 text-[#6b7280]" />
                      LINEチャネル管理
                    </button>
                  </>
                )}
                {isAdmin() && (
                  <button
                    onClick={() => { setMenuOpen(false); navigate('/admin/line/schedule'); }}
                    className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                  >
                    <MessageSquare className="w-4 h-4 text-[#6b7280]" />
                    LINE通知スケジュール
                  </button>
                )}
                {isAdmin() && (
                  <button
                    onClick={() => { setMenuOpen(false); navigate('/admin/densuke'); }}
                    className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                  >
                    <ClipboardList className="w-4 h-4 text-[#6b7280]" />
                    伝助管理
                  </button>
                )}
                {isAdmin() && (
                  <button
                    onClick={() => { setMenuOpen(false); navigate('/admin/settings'); }}
                    className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-[#374151] hover:bg-[#f0f4f1] transition-colors"
                  >
                    <Settings className="w-4 h-4 text-[#6b7280]" />
                    システム設定
                  </button>
                )}
                <div className="border-t border-gray-100 my-1" />
                <button
                  onClick={() => { setMenuOpen(false); handleCalendarSync(); }}
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

      {/* カレンダー同期メッセージ */}
      {calSyncMessage && (
        <div className={`mb-4 p-3 text-sm rounded-lg flex justify-between items-center ${
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
    </>
  );
};

export default NavigationMenu;
