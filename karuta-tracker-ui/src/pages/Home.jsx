import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { homeAPI } from '../api';
import { notificationAPI } from '../api/notifications';
import {
  ArrowRight,
  ChevronsRight,
  Clock,
  Shuffle,
  Trophy,
  User,
} from 'lucide-react';
import { isAdmin } from '../utils/auth';
import { sortPlayersByRank } from '../utils/playerSort';
import PlayerChip from '../components/PlayerChip';
import LoadingScreen from '../components/LoadingScreen';
import NavigationMenu from '../components/NavigationMenu';

const Home = () => {
  const { currentPlayer } = useAuth();
  const [loading, setLoading] = useState(true);
  const [nextPractice, setNextPractice] = useState(null);
  const [nextPracticeParticipants, setNextPracticeParticipants] = useState([]);
  const [participationTop3, setParticipationTop3] = useState([]);
  const [myParticipationRate, setMyParticipationRate] = useState(null);
  const [hasPendingOffer, setHasPendingOffer] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  const fetchData = useCallback(async (signal) => {
    if (!currentPlayer?.id) return;
    try {
      const res = await homeAPI.getData(currentPlayer.id);

      // リクエストがキャンセルされた場合は状態更新をスキップ
      if (signal?.aborted) return;

      const data = res.data;

      // 参加率TOP3
      setParticipationTop3(data.participationTop3 || []);

      // 自分の参加率
      if (data.myParticipationRate) {
        setMyParticipationRate(data.myParticipationRate);
      }

      // 繰り上げオファー
      setHasPendingOffer(data.hasPendingOffer || false);

      // 未読通知数
      notificationAPI.getUnreadCount(currentPlayer.id)
        .then(r => { if (!signal?.aborted) setUnreadCount(r.data.count || 0); })
        .catch(() => {});

      // 次の練習情報（参加者リストも含まれている）
      if (data.nextPractice) {
        setNextPractice(data.nextPractice);
        if (data.nextPractice.participants) {
          setNextPracticeParticipants(data.nextPractice.participants);
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
    return <LoadingScreen />;
  }

  return (
    <div className="space-y-8">
      {/* ナビゲーションバー */}
      <NavigationMenu unreadCount={unreadCount} />

      {/* コンテンツ */}
      <div className="pt-16">
        {/* 繰り上げオファーバナー */}
        {hasPendingOffer && (
          <Link
            to="/notifications"
            className="block mb-4 p-4 bg-blue-50 border border-blue-200 rounded-lg"
          >
            <div className="flex items-center gap-3">
              <span className="text-2xl">📩</span>
              <div>
                <div className="font-bold text-blue-800 text-sm">繰り上げ参加のお知らせ</div>
                <div className="text-xs text-blue-600">練習に空きが出ました。通知を確認してください。</div>
              </div>
              <ArrowRight className="w-4 h-4 text-blue-400 ml-auto" />
            </div>
          </Link>
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
                    {nextPracticeParticipants.some(p => p.status !== 'WAITLISTED') && (
                      <div className="flex flex-wrap gap-1.5">
                        {sortPlayersByRank(nextPracticeParticipants.filter(p => p.status !== 'WAITLISTED')).map((p) => (
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
                    )}
                    {nextPracticeParticipants.some(p => p.status === 'WAITLISTED') && (
                      <div className="mt-2">
                        <div className="text-[10px] text-yellow-700 mb-1">キャンセル待ち</div>
                        <div className="flex flex-wrap gap-1.5">
                          {sortPlayersByRank(nextPracticeParticipants.filter(p => p.status === 'WAITLISTED')).map((p) => (
                            <PlayerChip
                              key={p.id}
                              name={p.name}
                              kyuRank={p.kyuRank}
                              className={`text-xs ${
                                isMyself(p)
                                  ? 'bg-yellow-200 text-yellow-900 font-medium'
                                  : 'bg-yellow-50 text-yellow-800'
                              }`}
                            />
                          ))}
                        </div>
                      </div>
                    )}
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
