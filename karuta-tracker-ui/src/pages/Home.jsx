import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI, pairingAPI } from '../api';
import {
  Trophy,
  BookOpen,
  TrendingUp,
  Calendar,
  ArrowRight,
  Clock,
  MapPin,
  Settings,
} from 'lucide-react';

const Home = () => {
  const { currentPlayer } = useAuth();
  const location = useLocation();
  const [stats, setStats] = useState({
    recentMatches: [],
    recentPractices: [],
    todayPairings: [],
    totalMatches: 0,
    totalWins: 0,
    winRate: 0,
    upcomingPracticesCount: 0,
    loading: true,
  });
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
        const today = new Date();
        const todayStr = today.toISOString().split('T')[0];
        today.setHours(0, 0, 0, 0);
        const year = today.getFullYear();
        const month = today.getMonth() + 1;

        // 必要なAPIのみ並列実行
        const [
          matchesRes,
          statisticsRes,
          practicesRes,
          todayPairingsRes,
        ] = await Promise.all([
          matchAPI.getByPlayerId(currentPlayer.id).catch(() => ({ data: [] })),
          matchAPI.getStatistics(currentPlayer.id).catch(() => ({ data: { totalMatches: 0, wins: 0, winRate: 0 } })),
          practiceAPI.getUpcoming(todayStr).then(res => res.data).catch(() => []),
          pairingAPI.getByDate(todayStr).catch(() => ({ data: [] })),
        ]);

        const allPractices = Array.isArray(practicesRes) ? practicesRes : [];

        // 今後の練習
        const upcomingPractices = allPractices
          .filter(p => new Date(p.sessionDate) >= today)
          .sort((a, b) => new Date(a.sessionDate) - new Date(b.sessionDate))
          .slice(0, 5);

        setStats({
          recentMatches: matchesRes.data.slice(0, 5),
          recentPractices: upcomingPractices,
          todayPairings: todayPairingsRes.data || [],
          totalMatches: statisticsRes.data.totalMatches || 0,
          totalWins: statisticsRes.data.wins || 0,
          winRate: statisticsRes.data.winRate || 0,
          upcomingPracticesCount: upcomingPractices.length,
          loading: false,
        });

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
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#82655a]"></div>
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
      <div className="bg-[#e2d9d0] border-b border-[#d0c5b8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-lg font-semibold text-[#5f3a2d]">{currentPlayer?.name}</span>
          </div>
          <Link
            to="/profile"
            className="p-2 hover:bg-[#d0c5b8] rounded-full transition-colors"
          >
            <Settings className="w-6 h-6 text-[#5f3a2d]" />
          </Link>
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-16">
      {/* 次の練習会場情報 */}
      {stats.recentPractices.length > 0 && stats.recentPractices[0] && (
        <div className="bg-[#f9f6f2] rounded-lg shadow-md p-6">
          <h2 className="text-xl font-bold flex items-center gap-2 mb-4">
            <MapPin className="w-6 h-6 text-green-600" />
            次の練習
          </h2>
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Calendar className="w-5 h-5 text-gray-600" />
              <span className="text-lg font-medium">
                {new Date(stats.recentPractices[0].sessionDate).toLocaleDateString('ja-JP', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  weekday: 'short'
                })}
              </span>
            </div>
            {stats.recentPractices[0].startTime && (
              <div className="flex items-center gap-2">
                <Clock className="w-5 h-5 text-gray-600" />
                <span className="text-lg">
                  {stats.recentPractices[0].startTime}～{stats.recentPractices[0].endTime || ''}
                </span>
              </div>
            )}
            {stats.recentPractices[0].venueName && (
              <div className="flex items-center gap-2">
                <MapPin className="w-5 h-5 text-gray-600" />
                <span className="text-lg font-medium">
                  {stats.recentPractices[0].venueName}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* クイックアクション */}
      <div className="grid grid-cols-2 gap-4 mb-6">
        <Link
          to="/matches/new"
          className="flex items-center justify-between p-4 bg-[#f9f6f2] border-2 border-[#d0c5b8] rounded-lg hover:border-[#82655a] hover:bg-[#f0ebe3] transition-all"
        >
          <span className="font-semibold">結果入力</span>
          <Trophy className="h-5 w-5 text-[#82655a]" />
        </Link>
        <Link
          to="/practice"
          className="flex items-center justify-between p-4 bg-[#f9f6f2] border-2 border-[#d0c5b8] rounded-lg hover:border-[#82655a] hover:bg-[#f0ebe3] transition-all"
        >
          <span className="font-semibold">練習日程確認</span>
          <BookOpen className="h-5 w-5 text-[#82655a]" />
        </Link>
      </div>

      {/* 統計カード */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard
          icon={Trophy}
          title="試合数"
          value={stats.totalMatches}
          color="bg-[#82655a]"
          link="/matches"
        />
        <StatCard
          icon={BookOpen}
          title="今後の練習予定"
          value={stats.upcomingPracticesCount}
          color="bg-[#a5927f]"
          link="/practice"
        />
        <StatCard
          icon={TrendingUp}
          title="勝率"
          value={`${Math.round(stats.winRate)}%`}
          color="bg-[#8b7866]"
          link="/statistics"
        />
        <StatCard
          icon={Calendar}
          title="今日の対戦"
          value={stats.todayPairings.length}
          color="bg-[#9d8570]"
          link="/pairings"
        />
      </div>

      {/* 最近の活動 */}
      <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold flex items-center gap-2">
            <Trophy className="w-5 h-5 text-[#82655a]" />
            最近の試合
          </h2>
          <Link
            to="/matches"
            className="text-sm text-[#82655a] hover:text-[#6b5048] flex items-center gap-1"
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
                className="block p-3 hover:bg-[#f0ebe3] rounded-lg transition-colors"
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
