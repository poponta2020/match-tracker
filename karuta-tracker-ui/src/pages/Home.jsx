import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI } from '../api';
import {
  Trophy,
  TrendingUp,
  ArrowRight,
  Settings,
  Calendar,
  Clock,
  MapPin,
  Shuffle,
} from 'lucide-react';
import { isAdmin } from '../utils/auth';

const Home = () => {
  const { currentPlayer } = useAuth();
  const location = useLocation();
  const [stats, setStats] = useState({
    recentMatches: [],
    totalMatches: 0,
    totalWins: 0,
    winRate: 0,
    loading: true,
  });
  const [nextPractice, setNextPractice] = useState(null);
  const [slowLoading, setSlowLoading] = useState(false);

  // 3秒以上ローディングが続いたらコールドスタートメッセージを表示
  useEffect(() => {
    if (stats.loading) {
      const timer = setTimeout(() => setSlowLoading(true), 3000);
      return () => clearTimeout(timer);
    } else {
      setSlowLoading(false);
    }
  }, [stats.loading]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        // 必要なAPIのみ並列実行
        const [
          matchesRes,
          statisticsRes,
          nextPracticeRes,
        ] = await Promise.all([
          matchAPI.getByPlayerId(currentPlayer.id).catch(() => ({ data: [] })),
          matchAPI.getStatistics(currentPlayer.id).catch(() => ({ data: { totalMatches: 0, wins: 0, winRate: 0 } })),
          practiceAPI.getNextParticipation(currentPlayer.id).catch(() => ({ data: null, status: 204 })),
        ]);

        setStats({
          recentMatches: matchesRes.data.slice(0, 5),
          totalMatches: statisticsRes.data.totalMatches || 0,
          totalWins: statisticsRes.data.wins || 0,
          winRate: statisticsRes.data.winRate || 0,
          loading: false,
        });

        if (nextPracticeRes.status !== 204 && nextPracticeRes.data) {
          setNextPractice(nextPracticeRes.data);
        }

      } catch (error) {
        console.error('データ取得エラー:', error);
        setStats((prev) => ({ ...prev, loading: false }));
      }
    };

    if (currentPlayer?.id) {
      fetchData();
    }

    const handleFocus = () => {
      if (currentPlayer?.id) {
        fetchData();
      }
    };

    window.addEventListener('focus', handleFocus);
    return () => {
      window.removeEventListener('focus', handleFocus);
    };
  }, [currentPlayer, location.key]);

  const StatCard = ({ icon: Icon, title, value, color, link }) => (
    <Link
      to={link}
      className="bg-[#f9f6f2] p-6 rounded-lg shadow-sm hover:shadow-md transition-shadow"
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-600 mb-1">{title}</p>
          <p className="text-3xl font-bold">{value}</p>
        </div>
        <div className={`p-3 rounded-full ${color}`}>
          <Icon className="w-6 h-6 text-white" />
        </div>
      </div>
    </Link>
  );

  if (stats.loading) {
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
          <Link
            to="/profile"
            className="p-2 hover:bg-[#c5cec8] rounded-full transition-colors"
          >
            <Settings className="w-6 h-6 text-[#374151]" />
          </Link>
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-16">
      {/* 次の参加予定練習 */}
      {nextPractice && (
        <div className={`rounded-lg shadow-md p-6 mb-4 ${nextPractice.today ? 'bg-[#374151] text-white' : 'bg-[#f9f6f2]'}`}>
          {nextPractice.today ? (
            <div className="flex items-center gap-2 mb-3">
              <span className="bg-white text-[#374151] text-xs font-bold px-2 py-1 rounded-full">TODAY</span>
              <h2 className="text-lg font-bold">今日は練習日です</h2>
            </div>
          ) : (
            <h2 className={`text-lg font-bold flex items-center gap-2 mb-3 ${nextPractice.today ? '' : 'text-[#374151]'}`}>
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
                to="/pairings"
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
          to="/pairings"
          className="flex items-center justify-between bg-[#f9f6f2] border border-[#d4ddd7] px-5 py-3 rounded-lg hover:bg-[#e5ebe7] transition-colors mb-4"
        >
          <div className="flex items-center gap-2 text-[#374151] font-medium">
            <Shuffle className="w-4 h-4 text-[#4a6b5a]" />
            組み合わせ作成
          </div>
          <ArrowRight className="w-4 h-4 text-[#6b7280]" />
        </Link>
      )}

      {/* 統計カード */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <StatCard
          icon={Trophy}
          title="試合数"
          value={stats.totalMatches}
          color="bg-[#4a6b5a]"
          link="/matches"
        />
        <StatCard
          icon={TrendingUp}
          title="勝率"
          value={`${Math.round(stats.winRate)}%`}
          color="bg-[#6b7280]"
          link="/statistics"
        />
      </div>

      {/* 最近の活動 */}
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
        {stats.recentMatches.length > 0 ? (
          <div className="space-y-3">
            {stats.recentMatches.map((match) => (
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
