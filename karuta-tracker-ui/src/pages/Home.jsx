import { useEffect, useState, useRef, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { practiceAPI, calendarAPI } from '../api';
import {
  ArrowRight,
  ChevronsRight,
  Menu,
  Calendar,
  Clock,
  MapPin,
  Shuffle,
  Users,
  Trophy,
  User,
  LogOut,
  RefreshCw,
  X,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';
import { sortPlayersByRank } from '../utils/playerSort';
import PlayerChip from '../components/PlayerChip';
import LoadingScreen from '../components/LoadingScreen';

const Home = () => {
  const { currentPlayer, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [slowLoading, setSlowLoading] = useState(false);
  const [nextPractice, setNextPractice] = useState(null);
  const [nextPracticeParticipants, setNextPracticeParticipants] = useState([]);
  const [calSyncing, setCalSyncing] = useState(false);
  const [calSyncMessage, setCalSyncMessage] = useState(null);
  const [calSyncError, setCalSyncError] = useState(false);
  const [participationTop3, setParticipationTop3] = useState([]);
  const [myParticipationRate, setMyParticipationRate] = useState(null);

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

      // 並列で全データ取得
      const [
        nextPracticeRes,
        participationsRes,
        top3Res,
      ] = await Promise.all([
        practiceAPI.getNextParticipation(currentPlayer.id).catch(() => ({ data: null, status: 204 })),
        practiceAPI.getPlayerParticipations(currentPlayer.id, year, month).catch(() => ({ data: {} })),
        practiceAPI.getParticipationRateTop3(year, month).catch(() => ({ data: [] })),
      ]);

      // リクエストがキャンセルされた場合は状態更新をスキップ
      if (signal?.aborted) return;

      // 参加率TOP3
      const top3 = top3Res.data || [];
      setParticipationTop3(top3);

      // 自分の参加率を計算
      const myselfInTop3 = top3.find((p) => p.playerId === currentPlayer.id);
      if (myselfInTop3) {
        setMyParticipationRate(myselfInTop3);
      } else if (top3.length > 0) {
        // TOP3にいない場合：分母はTOP3データから取得、分子は自分の参加数
        const totalScheduledMatches = top3[0].totalScheduledMatches;
        const participationMap = participationsRes.data || {};
        const participatedMatches = Object.keys(participationMap).length;
        const rate = totalScheduledMatches > 0 ? participatedMatches / totalScheduledMatches : 0;
        setMyParticipationRate({ participatedMatches, totalScheduledMatches, rate });
      } else {
        // TOP3データがない場合でも参加数が取れれば表示
        const participationMap = participationsRes.data || {};
        const participatedMatches = Object.keys(participationMap).length;
        if (participatedMatches > 0) {
          setMyParticipationRate({ participatedMatches, totalScheduledMatches: null, rate: null });
        }
      }

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
      <LoadingScreen
        message={slowLoading ? 'サーバーを起動中...' : '読み込み中...'}
        subMessage={slowLoading ? '初回アクセス時は少し時間がかかります（最大30秒）' : null}
      />
    );
  }

  return (
    <div className="space-y-8">
      {/* ナビゲーションバー */}
      <div className="bg-[#d4ddd7] border-b border-[#c5cec8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-lg font-semibold text-[#374151]">{currentPlayer?.name}</span>
          </div>
          <div className="relative" ref={menuRef}>
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-2 hover:bg-[#c5cec8] rounded-full transition-colors"
            >
              <Menu className="w-6 h-6 text-[#374151]" />
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

      {/* コンテンツ */}
      <div className="pt-16">
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
        {/* 次の練習 + 参加者（統合カード） */}
        {nextPractice && (
          <div className="rounded-lg shadow-md overflow-hidden mb-4">
            {/* ヘッダー帯 */}
            {nextPractice.today ? (
              <div className="bg-[#1A3654] px-5 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-white text-lg font-bold">TODAY</span>
                  <span className="text-sm font-semibold text-white/90">
                    {(() => {
                      const d = new Date(nextPractice.sessionDate);
                      const weekday = d.toLocaleDateString('ja-JP', { weekday: 'short' });
                      return `${d.getMonth() + 1}/${d.getDate()}(${weekday})`;
                    })()}
                  </span>
                  {nextPractice.venueName && (
                    <span className="text-sm text-white/75">{nextPractice.venueName}</span>
                  )}
                </div>
                {nextPractice.registered === false ? (
                  <Link to="/practice/participation" className="text-xs font-semibold text-white/80 hover:text-white flex items-center gap-0.5">
                    参加登録 <ArrowRight className="w-3 h-3" />
                  </Link>
                ) : nextPractice.matchNumbers && nextPractice.matchNumbers.length > 0 && (
                  <span className="text-xs text-white/70">
                    {nextPractice.matchNumbers.join('、')}試合目に参加予定
                  </span>
                )}
              </div>
            ) : (
              <div className="bg-[#f9f6f2] border-b border-[#1A3654]/20 px-5 py-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <ChevronsRight className="w-6 h-6 text-[#1A3654]/70" />
                  <h2 className="text-xl font-bold text-[#1A3654] tracking-wide underline underline-offset-2 decoration-[#1A3654]/60 decoration-2">NEXT</h2>
                  <span className="text-sm font-semibold text-[#1A3654]">
                    {(() => {
                      const d = new Date(nextPractice.sessionDate);
                      const weekday = d.toLocaleDateString('ja-JP', { weekday: 'short' });
                      return `${d.getMonth() + 1}/${d.getDate()}(${weekday})`;
                    })()}
                  </span>
                  {nextPractice.venueName && (
                    <span className="text-sm text-[#1A3654]/70">{nextPractice.venueName}</span>
                  )}
                </div>
                {nextPractice.registered === false && (
                  <Link to="/practice/participation" className="text-xs font-semibold text-[#1A3654]/70 hover:text-[#1A3654] flex items-center gap-0.5">
                    参加登録 <ArrowRight className="w-3 h-3" />
                  </Link>
                )}
              </div>
            )}
            {/* ボディ */}
            <div className={`px-5 py-4 ${nextPractice.today ? 'bg-[#1A3654]/5' : 'bg-[#f9f6f2]'}`}>
              <div className="space-y-2">
                {nextPractice.startTime && (
                  <div className="flex items-center gap-2">
                    <Clock className="w-4 h-4 text-[#1A3654]" />
                    <span className="text-[#374151]">{nextPractice.startTime}〜{nextPractice.endTime || ''}</span>
                  </div>
                )}
                {/* 参加者セクション */}
                {nextPracticeParticipants.length > 0 && (
                  <div className="mt-3 pt-3 border-t border-gray-200">
                    <div className="flex flex-wrap gap-1.5">
                      {sortPlayersByRank(nextPracticeParticipants).map((p) => (
                        <PlayerChip
                          key={p.id}
                          name={p.name}
                          kyuRank={p.kyuRank}
                          className={`text-xs ${
                            isMyself(p)
                              ? 'bg-[#1A3654] text-white font-medium'
                              : 'bg-[#e8ecef] text-[#374151]'
                          }`}
                        />
                      ))}
                    </div>
                  </div>
                )}
                {nextPractice.today && isAdmin() && (
                  <Link
                    to={`/pairings?date=${nextPractice.sessionDate}`}
                    className="mt-3 flex items-center justify-center gap-2 bg-[#1A3654] text-white font-medium py-2.5 rounded-lg hover:bg-[#122740] transition-colors shadow-sm"
                  >
                    <Shuffle className="w-4 h-4" />
                    組み合わせを作成
                  </Link>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 参加率TOP3 */}
        {participationTop3.length > 0 && (
          <div className="bg-[#f9f6f2] rounded-lg shadow-md p-5 mb-4">
            <div className="flex items-center gap-2 mb-4">
              <Trophy className="w-5 h-5 text-[#1A3654]" />
              <h2 className="text-base font-bold text-[#1A3654]">{monthLabel} 参加率TOP3</h2>
            </div>
            <div className="space-y-3">
              {participationTop3.map((player, index) => {
                const rankColors = ['bg-[#1A3654] text-white', 'bg-[#2d5a8a] text-white', 'bg-[#5a8ab5] text-white'];
                const ratePercent = Math.round(player.rate * 100);
                return (
                  <div key={player.playerId} className="flex items-center gap-3">
                    <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 ${rankColors[index]}`}>{index + 1}</span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm font-semibold text-[#374151] truncate">{player.playerName}</span>
                        <span className="text-sm font-bold text-[#1A3654] flex-shrink-0 ml-2">{ratePercent}%</span>
                      </div>
                      <div className="w-full bg-[#1A3654]/15 rounded-full h-1.5">
                        <div
                          className="bg-[#1A3654] h-1.5 rounded-full transition-all"
                          style={{ width: `${ratePercent}%` }}
                        />
                      </div>
                      <span className="text-xs text-[#6b7280] mt-0.5 block">
                        {player.participatedMatches}/{player.totalScheduledMatches}試合
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
            {/* 自分がTOP3にいない場合のみ自分の参加率を表示 */}
            {myParticipationRate && !participationTop3.some((p) => p.playerId === currentPlayer?.id) && (
              <>
                <div className="border-t border-gray-200 mt-3 pt-3">
                  <div className="flex items-center gap-3">
                    <span className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 bg-[#e8ecef]">
                      <User className="w-3 h-3 text-[#6b7280]" />
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm font-semibold text-[#374151] truncate">{currentPlayer?.name}</span>
                        {myParticipationRate.rate !== null && (
                          <span className="text-sm font-bold text-[#1A3654] flex-shrink-0 ml-2">{Math.round(myParticipationRate.rate * 100)}%</span>
                        )}
                      </div>
                      {myParticipationRate.rate !== null && (
                        <div className="w-full bg-[#1A3654]/15 rounded-full h-1.5">
                          <div
                            className="bg-[#6b7280] h-1.5 rounded-full transition-all"
                            style={{ width: `${Math.round(myParticipationRate.rate * 100)}%` }}
                          />
                        </div>
                      )}
                      <span className="text-xs text-[#6b7280] mt-0.5 block">
                        {myParticipationRate.totalScheduledMatches !== null
                          ? `${myParticipationRate.participatedMatches}/${myParticipationRate.totalScheduledMatches}試合`
                          : `${myParticipationRate.participatedMatches}試合参加`}
                      </span>
                    </div>
                  </div>
                </div>
              </>
            )}
          </div>
        )}

      </div>
    </div>
  );
};

export default Home;
