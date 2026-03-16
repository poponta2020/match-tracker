import { useEffect, useState, useRef, useCallback } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI, calendarAPI } from '../api';
import {
  Trophy,
  ArrowRight,
  Menu,
  Calendar,
  Clock,
  MapPin,
  Shuffle,
  Users,
  Swords,
  CheckCircle,
  User,
  LogOut,
  RefreshCw,
  X,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';

const Home = () => {
  const { currentPlayer, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [slowLoading, setSlowLoading] = useState(false);
  const [recentMatches, setRecentMatches] = useState([]);
  const [nextPractice, setNextPractice] = useState(null);
  const [nextPracticeParticipants, setNextPracticeParticipants] = useState([]);
  const [monthlyPracticeCount, setMonthlyPracticeCount] = useState(0);
  const [monthlyMatchCount, setMonthlyMatchCount] = useState(0);
  const [calSyncing, setCalSyncing] = useState(false);
  const [calSyncMessage, setCalSyncMessage] = useState(null);
  const [calSyncError, setCalSyncError] = useState(false);

  // Google Calendar同期ハンドラー
  const handleCalendarSync = useCallback(() => {
    if (!currentPlayer?.id) return;
    if (!window.google?.accounts?.oauth2) {
      setCalSyncMessage('Google認証の読み込みに失敗しました。ページを再読み込みしてください。');
      setCalSyncError(true);
      return;
    }

    setCalSyncing(true);
    setCalSyncMessage(null);
    setCalSyncError(false);

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

        try {
          const result = await calendarAPI.sync(
            tokenResponse.access_token,
            currentPlayer.id
          );
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
      },
    });

    tokenClient.requestAccessToken();
  }, [currentPlayer]);

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
        matchesRes,
        nextPracticeRes,
        participationsRes,
        monthlyMatchesRes,
      ] = await Promise.all([
        matchAPI.getByPlayerId(currentPlayer.id, { limit: 5 }).catch(() => ({ data: [] })),
        practiceAPI.getNextParticipation(currentPlayer.id).catch(() => ({ data: null, status: 204 })),
        practiceAPI.getPlayerParticipations(currentPlayer.id, year, month).catch(() => ({ data: {} })),
        matchAPI.getMatchCount(currentPlayer.id, startOfMonth, endOfMonth).catch(() => ({ data: 0 })),
      ]);

      // リクエストがキャンセルされた場合は状態更新をスキップ
      if (signal?.aborted) return;

      setRecentMatches(matchesRes.data.slice(0, 5));

      // 今月の参加回数 = セッション数（マップのキー数）
      const participationMap = participationsRes.data || {};
      setMonthlyPracticeCount(Object.keys(participationMap).length);

      // 今月の対戦数
      setMonthlyMatchCount(typeof monthlyMatchesRes.data === 'number' ? monthlyMatchesRes.data : 0);

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
  const iAmParticipating = nextPracticeParticipants.some(isMyself);

  const now = new Date();
  const monthLabel = `${now.getMonth() + 1}月`;

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-96 gap-4">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#4a6b5a]"></div>
        {slowLoading && (
          <div className="text-center">
            <p className="text-gray-600 font-medium">サーバーを起動中...</p>
            <p className="text-sm text-gray-400 mt-1">初回アクセス時は少し時間がかかります（最大30秒）</p>
          </div>
        )}
      </div>
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
        {/* 次の参加予定練習 */}
        {nextPractice && (
          <div className={`rounded-lg shadow-md p-6 mb-4 ${nextPractice.today ? 'bg-[#374151] text-white' : 'bg-[#f9f6f2]'}`}>
            {nextPractice.today ? (
              <div className="flex items-center gap-2 mb-3">
                <span className="bg-white text-[#374151] text-xs font-bold px-2 py-1 rounded-full">TODAY</span>
                <h2 className="text-lg font-bold">今日は練習日です</h2>
              </div>
            ) : (
              <h2 className={`text-lg font-bold flex items-center gap-2 mb-3 text-[#374151]`}>
                <Calendar className="w-5 h-5" />
                次の練習
              </h2>
            )}
            <div className="space-y-2">
              {!nextPractice.today && (
                <div className="flex items-center gap-2">
                  <Calendar className="w-4 h-4 opacity-70" />
                  <span className="font-medium">
                    {new Date(nextPractice.sessionDate).toLocaleDateString('ja-JP', {
                      month: 'long',
                      day: 'numeric',
                      weekday: 'short'
                    })}
                  </span>
                </div>
              )}
              {nextPractice.startTime && (
                <div className="flex items-center gap-2">
                  <Clock className="w-4 h-4 opacity-70" />
                  <span>{nextPractice.startTime}〜{nextPractice.endTime || ''}</span>
                </div>
              )}
              {nextPractice.venueName && (
                <div className="flex items-center gap-2">
                  <MapPin className="w-4 h-4 opacity-70" />
                  <span>{nextPractice.venueName}</span>
                </div>
              )}
              {nextPractice.matchNumbers && nextPractice.matchNumbers.length > 0 && (
                <div className={`mt-2 pt-2 border-t ${nextPractice.today ? 'border-white/20' : 'border-gray-200'}`}>
                  <span className="text-sm opacity-80">参加試合：</span>
                  <span className="font-medium ml-1">
                    {nextPractice.matchNumbers.map(n => `${n}試合目`).join('、')}
                  </span>
                </div>
              )}
              {nextPractice.today && isAdmin() && (
                <Link
                  to={`/pairings?date=${nextPractice.sessionDate}`}
                  className="mt-3 flex items-center justify-center gap-2 bg-white text-[#374151] font-medium py-2.5 rounded-lg hover:bg-gray-100 transition-colors"
                >
                  <Shuffle className="w-4 h-4" />
                  組み合わせを作成
                </Link>
              )}
            </div>
          </div>
        )}

        {/* 組み合わせ作成リンク（管理者のみ、TODAYカード内にボタンがない場合） */}
        {isAdmin() && !(nextPractice?.today) && (
          <Link
            to={nextPractice ? `/pairings?date=${nextPractice.sessionDate}` : '/pairings'}
            className="flex items-center justify-between bg-[#f9f6f2] border border-[#d4ddd7] px-5 py-3 rounded-lg hover:bg-[#e5ebe7] transition-colors mb-4"
          >
            <div className="flex items-center gap-2 text-[#374151] font-medium">
              <Shuffle className="w-4 h-4 text-[#4a6b5a]" />
              組み合わせ作成
            </div>
            <ArrowRight className="w-4 h-4 text-[#6b7280]" />
          </Link>
        )}

        {/* 今月のアクティビティ */}
        <div className="grid grid-cols-2 gap-3 mb-4">
          <div className="bg-[#f9f6f2] p-4 rounded-lg shadow-sm">
            <div className="flex items-center gap-2 mb-1">
              <Calendar className="w-4 h-4 text-[#4a6b5a]" />
              <span className="text-xs text-[#6b7280]">{monthLabel}の参加</span>
            </div>
            <p className="text-2xl font-bold text-[#374151]">
              {monthlyPracticeCount}<span className="text-sm font-normal text-[#6b7280] ml-1">回</span>
            </p>
          </div>
          <div className="bg-[#f9f6f2] p-4 rounded-lg shadow-sm">
            <div className="flex items-center gap-2 mb-1">
              <Swords className="w-4 h-4 text-[#4a6b5a]" />
              <span className="text-xs text-[#6b7280]">{monthLabel}の対戦</span>
            </div>
            <p className="text-2xl font-bold text-[#374151]">
              {monthlyMatchCount}<span className="text-sm font-normal text-[#6b7280] ml-1">試合</span>
            </p>
          </div>
        </div>

        {/* 次回の参加者 */}
        {nextPractice && nextPracticeParticipants.length > 0 && (
          <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-5 mb-4">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-bold text-[#374151] flex items-center gap-2">
                <Users className="w-4 h-4 text-[#4a6b5a]" />
                {nextPractice.today ? '今日' : '次回'}の参加者
              </h2>
              <span className="text-sm text-[#6b7280]">{nextPracticeParticipants.length}名</span>
            </div>
            {iAmParticipating && (
              <div className="flex items-center gap-1.5 text-xs text-[#4a6b5a] mb-3">
                <CheckCircle className="w-3.5 h-3.5" />
                あなたも参加予定です
              </div>
            )}
            <div className="flex flex-wrap gap-1.5">
              {nextPracticeParticipants.map((p) => (
                <span
                  key={p.id}
                  className={`px-2.5 py-1 rounded-full text-xs ${
                    isMyself(p)
                      ? 'bg-[#4a6b5a] text-white font-medium'
                      : 'bg-white border border-[#d4ddd7] text-[#374151]'
                  }`}
                >
                  {p.name}
                </span>
              ))}
            </div>
          </div>
        )}

        {/* 最近の試合 */}
        <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold flex items-center gap-2">
              <Trophy className="w-5 h-5 text-[#4a6b5a]" />
              最近の試合
            </h2>
            <Link
              to="/matches"
              className="text-sm text-[#4a6b5a] hover:text-[#3d5a4c] flex items-center gap-1"
            >
              すべて見る
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          {recentMatches.length > 0 ? (
            <div className="space-y-3">
              {recentMatches.map((match) => (
                <Link
                  key={match.id}
                  to={`/matches/${match.id}`}
                  className="block p-3 hover:bg-[#eef2ef] rounded-lg transition-colors"
                >
                  <div className="flex justify-between items-center">
                    <div>
                      <p className="font-medium">
                        vs {match.opponentName}
                      </p>
                      <p className="text-sm text-gray-600">
                        {new Date(match.matchDate).toLocaleDateString('ja-JP')}
                      </p>
                    </div>
                    <span
                      className={`px-3 py-1 rounded-full text-sm font-medium ${
                        match.result === '勝ち'
                          ? 'bg-green-100 text-green-700'
                          : match.result === '負け'
                          ? 'bg-red-100 text-red-700'
                          : 'bg-gray-100 text-gray-700'
                      }`}
                    >
                      {match.result}
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          ) : (
            <p className="text-gray-500 text-center py-8">
              まだ試合記録がありません
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default Home;
