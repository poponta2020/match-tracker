import { useEffect, useState, useRef, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI, calendarAPI } from '../api';
import {
  ArrowRight,
  ChevronsRight,
  Menu,
  Calendar,
  Clock,
  MapPin,
  Shuffle,
  Users,
  Swords,
  Trophy,
  User,
  LogOut,
  RefreshCw,
  X,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';
import { sortPlayersByRank } from '../utils/playerSort';
import PlayerChip from '../components/PlayerChip';

const Home = () => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [slowLoading, setSlowLoading] = useState(false);
  const [nextPractice, setNextPractice] = useState(null);
  const [nextPracticeParticipants, setNextPracticeParticipants] = useState([]);
  const [monthlyPracticeCount, setMonthlyPracticeCount] = useState(0);
  const [monthlyMatchCount, setMonthlyMatchCount] = useState(0);
  const [calSyncing, setCalSyncing] = useState(false);
  const [calSyncMessage, setCalSyncMessage] = useState(null);
  const [calSyncError, setCalSyncError] = useState(false);
  const [participationTop3, setParticipationTop3] = useState([]);

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
      // PWA/ホーム画面ショートカット: GISライブラリが動作しないため、直接OAuth URLにリダイレクト
      sessionStorage.setItem('cal_sync_player_id', String(currentPlayer.id));
      const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
      const redirectUri = encodeURIComponent(window.location.origin + '/');
      const scope = encodeURIComponent('https://www.googleapis.com/auth/calendar.events');
      const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=token&scope=${scope}&prompt=consent`;
      window.location.href = authUrl;
      return;
    }

    // ブラウザ: GISライブラリのポップアップ方式
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

  // 3秒以上ローディングが続いたらコールドスタートメッセージを表示
  useEffect(() => {
    if (loading) {
      const timer = setTimeout(() => setSlowLoading(true), 3000);
      return () => clearTimeout(timer);
    } else {
      setSlowLoading(false);
    }
  }, [loading]);

  const fetchData = useCallback(async (signal) => {
    if (!currentPlayer?.id) return;
    try {
      const now = new Date();
      const year = now.getFullYear();
      const month = now.getMonth() + 1;
      const startOfMonth = `${year}-${String(month).padStart(2, '0')}-01`;
      const lastDay = new Date(year, month, 0).getDate();
      const endOfMonth = `${year}-${String(month).padStart(2, '0')}-${lastDay}`;

      // 並列で全データ取得
      const [
        nextPracticeRes,
        participationsRes,
        monthlyMatchesRes,
        top3Res,
      ] = await Promise.all([
        practiceAPI.getNextParticipation(currentPlayer.id).catch(() => ({ data: null, status: 204 })),
        practiceAPI.getPlayerParticipations(currentPlayer.id, year, month).catch(() => ({ data: {} })),
        matchAPI.getMatchCount(currentPlayer.id, startOfMonth, endOfMonth).catch(() => ({ data: 0 })),
        practiceAPI.getParticipationRateTop3(year, month).catch(() => ({ data: [] })),
      ]);

      // リクエストがキャンセルされた場合は状態更新をスキップ
      if (signal?.aborted) return;

      // 今月の参加回数 = セッション数（マップのキー数）
      const participationMap = participationsRes.data || {};
      setMonthlyPracticeCount(Object.keys(participationMap).length);

      // 今月の対戦数
      setMonthlyMatchCount(typeof monthlyMatchesRes.data === 'number' ? monthlyMatchesRes.data : 0);

      // 参加率TOP3
      setParticipationTop3(top3Res.data || []);

      // 次の練習情報（参加者リストも含まれている）
      if (nextPracticeRes.status !== 204 && nextPracticeRes.data) {
        setNextPractice(nextPracticeRes.data);
        if (nextPracticeRes.data.participants) {
          setNextPracticeParticipants(nextPracticeRes.data.participants);
        }
      }
    } catch (error) {
      if (error.name !== 'AbortError') {
        console.error('データ取得エラー:', error);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, [currentPlayer]);

  // 初回データ取得
  useEffect(() => {
    const abortController = new AbortController();
    if (currentPlayer?.id) {
      fetchData(abortController.signal);
    }
    return () => {
      abortController.abort();
    };
  }, [currentPlayer, fetchData]);

  // フォーカス復帰時のリフレッシュ（初回マウント直後は無視）
  useEffect(() => {
    const mountedAt = Date.now();
    let abortController = null;
    const handleFocus = () => {
      // マウントから2秒以内のfocusは無視（初回ロードとの重複防止）
      if (Date.now() - mountedAt < 2000) return;
      if (currentPlayer?.id) {
        // 前回のリクエストをキャンセル
        if (abortController) {
          abortController.abort();
        }
        abortController = new AbortController();
        setLoading(true);
        fetchData(abortController.signal);
      }
    };
    window.addEventListener('focus', handleFocus);
    return () => {
      window.removeEventListener('focus', handleFocus);
      if (abortController) {
        abortController.abort();
      }
    };
  }, [currentPlayer, fetchData]);

  const isMyself = (p) => p.id === currentPlayer?.id;

  const now = new Date();
  const monthLabel = `${now.getMonth() + 1}月`;

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-96 gap-4">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-secondary"></div>
        {slowLoading && (
          <div className="text-center">
            <p className="text-text-muted font-medium">サーバーを起動中...</p>
            <p className="text-sm text-text-placeholder mt-1">初回アクセス時は少し時間がかかります（最大30秒）</p>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* ナビゲーションバー */}
      <div className="bg-surface border-b border-border-subtle shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-lg font-semibold text-text">{currentPlayer?.name}</span>
          </div>
          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-2 hover:bg-border-subtle rounded-full transition-colors"
            >
              <Menu className="w-6 h-6 text-text" />
            </button>
            {menuOpen && (
              <div className="absolute right-0 mt-2 w-48 bg-bg rounded-lg shadow-lg border border-border-subtle py-1 z-50">
                <button
                  onClick={() => { setMenuOpen(false); navigate('/profile'); }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text hover:bg-surface transition-colors"
                >
                  <User className="w-4 h-4 text-text-muted" />
                  プロフィール
                </button>
                {isSuperAdmin() && (
                  <>
                    <div className="border-t border-border-subtle my-1" />
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/players'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text hover:bg-surface transition-colors"
                    >
                      <Users className="w-4 h-4 text-text-muted" />
                      選手管理
                    </button>
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/venues'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text hover:bg-surface transition-colors"
                    >
                      <MapPin className="w-4 h-4 text-text-muted" />
                      会場管理
                    </button>
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/practice/new'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text hover:bg-surface transition-colors"
                    >
                      <Calendar className="w-4 h-4 text-text-muted" />
                      練習日登録
                    </button>
                  </>
                )}
                {isAdmin() && (
                  <>
                    <div className="border-t border-border-subtle my-1" />
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/pairings'); }}
                      className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text hover:bg-surface transition-colors"
                    >
                      <Shuffle className="w-4 h-4 text-text-muted" />
                      組み合わせ作成
                    </button>
                  </>
                )}
                <div className="border-t border-border-subtle my-1" />
                <button
                  onClick={() => { setMenuOpen(false); handleCalendarSync(); }}
                  disabled={calSyncing}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-text hover:bg-surface transition-colors disabled:opacity-50"
                >
                  <RefreshCw className={`w-4 h-4 text-text-muted ${calSyncing ? 'animate-spin' : ''}`} />
                  {calSyncing ? '同期中...' : 'Googleカレンダー同期'}
                </button>
                <div className="border-t border-border-subtle my-1" />
                <button
                  onClick={() => { setMenuOpen(false); logout(); navigate('/login'); }}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-status-danger hover:bg-status-danger-surface transition-colors"
                >
                  <LogOut className="w-4 h-4" />
                  ログアウト
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* コンテンツ */}
      <div className="pt-16">
        {/* カレンダー同期メッセージ */}
        {calSyncMessage && (
          <div className={`mb-4 p-3 text-sm rounded-lg flex justify-between items-center ${
            calSyncError
              ? 'bg-status-danger-surface text-status-danger border border-status-danger/20'
              : 'bg-status-success-surface text-text'
          }`}>
            <span>{calSyncMessage}</span>
            <button
              onClick={() => setCalSyncMessage(null)}
              className={`ml-2 ${calSyncError ? 'text-status-danger hover:text-status-danger' : 'text-text-muted hover:text-text'}`}
            >
              <X size={16} />
            </button>
          </div>
        )}
        {/* 次の練習 + 参加者（統合カード） */}
        {nextPractice && (
          <div className="rounded-lg shadow-md overflow-hidden mb-4">
            {/* ヘッダー帯 */}
            {nextPractice.today ? (
              <div className="bg-primary px-5 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="bg-text-inverse text-primary text-xs font-bold px-2 py-0.5 rounded-full">TODAY</span>
                  <h2 className="text-base font-bold text-text-inverse">今日は練習日です</h2>
                </div>
                {nextPractice.registered === false ? (
                  <Link to="/practice/participation" className="text-xs font-semibold text-text-inverse/80 hover:text-text-inverse flex items-center gap-0.5">
                    参加登録 <ArrowRight className="w-3 h-3" />
                  </Link>
                ) : nextPractice.matchNumbers && nextPractice.matchNumbers.length > 0 && (
                  <span className="text-xs text-text-inverse/70">
                    {nextPractice.matchNumbers.join('、')}試合目に参加予定
                  </span>
                )}
              </div>
            ) : (
              <div className="bg-primary px-5 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ChevronsRight className="w-6 h-6 text-text-inverse/80" />
                  <h2 className="text-xl font-bold text-text-inverse tracking-wide underline underline-offset-2 decoration-text-inverse/80 decoration-2">NEXT</h2>
                  {nextPractice.matchNumbers && nextPractice.matchNumbers.length > 0 && (
                    <span className="text-xs text-text-inverse/70">
                      {nextPractice.matchNumbers.join('、')}試合目に参加予定
                    </span>
                  )}
                </div>
                {nextPractice.registered === false && (
                  <Link to="/practice/participation" className="text-xs font-semibold text-text-inverse/80 hover:text-text-inverse flex items-center gap-0.5">
                    参加登録 <ArrowRight className="w-3 h-3" />
                  </Link>
                )}
              </div>
            )}
            {/* ボディ */}
            <div className={`px-5 py-4 ${nextPractice.today ? 'bg-surface' : 'bg-surface'}`}>
              <div className="space-y-2">
                {!nextPractice.today && (
                  <div className="flex items-center gap-2">
                    <Calendar className="w-4 h-4 text-primary" />
                    <span className="font-semibold text-text">
                      {(() => {
                        const d = new Date(nextPractice.sessionDate);
                        const weekday = d.toLocaleDateString('ja-JP', { weekday: 'short' });
                        return `${d.getMonth() + 1}/${d.getDate()}(${weekday})`;
                      })()}
                    </span>
                  </div>
                )}
                {nextPractice.startTime && (
                  <div className="flex items-center gap-2">
                    <Clock className="w-4 h-4 text-primary" />
                    <span className="text-text">{nextPractice.startTime}〜{nextPractice.endTime || ''}</span>
                  </div>
                )}
                {nextPractice.venueName && (
                  <div className="flex items-center gap-2">
                    <MapPin className="w-4 h-4 text-primary" />
                    <span className="text-text">{nextPractice.venueName}</span>
                  </div>
                )}
                {/* 参加者セクション */}
                {nextPracticeParticipants.length > 0 && (
                  <div className="mt-3 pt-3 border-t border-border-subtle">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-sm font-medium flex items-center gap-1.5 text-text-muted">
                        <Users className="w-3.5 h-3.5" />
                        参加者
                      </span>
                      <span className="text-xs text-text-muted">{nextPracticeParticipants.length}名</span>
                    </div>

                    <div className="flex flex-wrap gap-1.5">
                      {sortPlayersByRank(nextPracticeParticipants).map((p) => (
                        <PlayerChip
                          key={p.id}
                          name={p.name}
                          kyuRank={p.kyuRank}
                          className={`text-xs ${
                            isMyself(p)
                              ? 'bg-primary text-text-inverse font-medium'
                              : 'bg-surface-disabled text-text'
                          }`}
                        />
                      ))}
                    </div>
                  </div>
                )}
                {nextPractice.today && isAdmin() && (
                  <Link
                    to={`/pairings?date=${nextPractice.sessionDate}`}
                    className="mt-3 flex items-center justify-center gap-2 bg-primary text-text-inverse font-medium py-2.5 rounded-lg hover:bg-primary-hover transition-colors shadow-sm"
                  >
                    <Shuffle className="w-4 h-4" />
                    組み合わせを作成
                  </Link>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 今月のアクティビティ */}
        <div className="grid grid-cols-2 gap-3 mb-4">
          <div className="bg-surface p-4 rounded-lg shadow-sm border-l-3 border-primary">
            <div className="flex items-center gap-2 mb-1">
              <Calendar className="w-4 h-4 text-primary" />
              <span className="text-xs text-text-muted">{monthLabel}の参加</span>
            </div>
            <p className="text-2xl font-bold text-text">
              {monthlyPracticeCount}<span className="text-sm font-normal text-text-muted ml-1">回</span>
            </p>
          </div>
          <div className="bg-surface p-4 rounded-lg shadow-sm border-l-3 border-primary">
            <div className="flex items-center gap-2 mb-1">
              <Swords className="w-4 h-4 text-primary" />
              <span className="text-xs text-text-muted">{monthLabel}の対戦</span>
            </div>
            <p className="text-2xl font-bold text-text">
              {monthlyMatchCount}<span className="text-sm font-normal text-text-muted ml-1">試合</span>
            </p>
          </div>
        </div>

        {/* 参加率TOP3 */}
        {participationTop3.length > 0 && (
          <div className="bg-surface rounded-lg shadow-md p-5 mb-4">
            <div className="flex items-center gap-2 mb-4">
              <Trophy className="w-5 h-5 text-primary" />
              <h2 className="text-base font-bold text-primary">{monthLabel} 参加率TOP3</h2>
            </div>
            <div className="space-y-3">
              {participationTop3.map((player, index) => {
                const rankColors = ['bg-primary text-text-inverse', 'bg-secondary text-text-inverse', 'bg-border-strong text-text-inverse'];
                const ratePercent = Math.round(player.rate * 100);
                return (
                  <div key={player.playerId} className="flex items-center gap-3">
                    <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 ${rankColors[index]}`}>{index + 1}</span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm font-semibold text-text truncate">{player.playerName}</span>
                        <span className="text-sm font-bold text-primary flex-shrink-0 ml-2">{ratePercent}%</span>
                      </div>
                      <div className="w-full bg-primary/15 rounded-full h-1.5">
                        <div
                          className="bg-primary h-1.5 rounded-full transition-all"
                          style={{ width: `${ratePercent}%` }}
                        />
                      </div>
                      <span className="text-xs text-text-muted mt-0.5 block">
                        {player.participatedMatches}/{player.totalScheduledMatches}試合
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

      </div>
    </div>
  );
};

export default Home;
