import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI, pairingAPI } from '../api';
import {
  Trophy,
  BookOpen,
  TrendingUp,
  Calendar,
  Plus,
  ArrowRight,
} from 'lucide-react';

const Home = () => {
  const { currentPlayer } = useAuth();
  const [stats, setStats] = useState({
    recentMatches: [],
    recentPractices: [],
    todayPairings: [],
    loading: true,
  });

  useEffect(() => {
    const fetchData = async () => {
      try {
        // 今日の日付を取得
        const today = new Date();
        const todayStr = today.toISOString().split('T')[0];
        today.setHours(0, 0, 0, 0);

        const [matchesRes, practicesRes, todayPairingsRes] = await Promise.all([
          matchAPI.getByPlayerId(currentPlayer.id).catch(() => ({ data: [] })),
          // 練習記録APIは全件取得のみサポート
          fetch('http://localhost:8080/api/practice-sessions')
            .then(res => res.json())
            .catch(() => []),
          // 今日の対戦組み合わせを取得
          pairingAPI.getByDate(todayStr).catch(() => ({ data: [] })),
        ]);

        // 未来の練習のみをフィルタリングして日付順にソート
        const upcomingPractices = Array.isArray(practicesRes)
          ? practicesRes
              .filter(p => new Date(p.sessionDate) >= today)
              .sort((a, b) => new Date(a.sessionDate) - new Date(b.sessionDate))
              .slice(0, 5)
          : [];

        setStats({
          recentMatches: matchesRes.data.slice(0, 5),
          recentPractices: upcomingPractices,
          todayPairings: todayPairingsRes.data || [],
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
  }, [currentPlayer]);

  const StatCard = ({ icon: Icon, title, value, color, link }) => (
    <Link
      to={link}
      className="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition-shadow"
    >
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-600 mb-1">{title}</p>
          <p className="text-3xl font-bold text-gray-900">{value}</p>
        </div>
        <div className={`p-3 rounded-full ${color}`}>
          <Icon className="w-6 h-6 text-white" />
        </div>
      </div>
    </Link>
  );

  if (stats.loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* ウェルカムメッセージ */}
      <div className="bg-gradient-to-r from-primary-600 to-primary-700 text-white p-6 rounded-lg shadow-md">
        <h1 className="text-3xl font-bold mb-2">
          ようこそ、{currentPlayer?.name}さん
        </h1>
        <p className="text-primary-100">
          今日も練習頑張りましょう！
        </p>
      </div>

      {/* クイックアクション */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Link
          to="/matches/new"
          className="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition-shadow flex items-center justify-between group"
        >
          <div className="flex items-center gap-3">
            <div className="p-3 bg-primary-50 rounded-full">
              <Trophy className="w-6 h-6 text-primary-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">試合記録</h3>
              <p className="text-sm text-gray-600">新しい試合を記録</p>
            </div>
          </div>
          <Plus className="w-5 h-5 text-gray-400 group-hover:text-primary-600 transition-colors" />
        </Link>

        <Link
          to="/practice"
          className="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition-shadow flex items-center justify-between group"
        >
          <div className="flex items-center gap-3">
            <div className="p-3 bg-green-50 rounded-full">
              <BookOpen className="w-6 h-6 text-green-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">練習記録</h3>
              <p className="text-sm text-gray-600">カレンダーを確認</p>
            </div>
          </div>
          <ArrowRight className="w-5 h-5 text-gray-400 group-hover:text-green-600 transition-colors" />
        </Link>

        <Link
          to="/pairings/generate"
          className="bg-white p-6 rounded-lg shadow-sm hover:shadow-md transition-shadow flex items-center justify-between group"
        >
          <div className="flex items-center gap-3">
            <div className="p-3 bg-purple-50 rounded-full">
              <Calendar className="w-6 h-6 text-purple-600" />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900">組み合わせ</h3>
              <p className="text-sm text-gray-600">対戦表を作成</p>
            </div>
          </div>
          <Plus className="w-5 h-5 text-gray-400 group-hover:text-purple-600 transition-colors" />
        </Link>
      </div>

      {/* 統計カード */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard
          icon={Trophy}
          title="試合数"
          value={stats.recentMatches.length}
          color="bg-primary-500"
          link="/matches"
        />
        <StatCard
          icon={BookOpen}
          title="練習回数"
          value={stats.recentPractices.length}
          color="bg-green-500"
          link="/practice"
        />
        <StatCard
          icon={TrendingUp}
          title="勝率"
          value={
            stats.recentMatches.length > 0
              ? `${Math.round(
                  (stats.recentMatches.filter((m) => m.result === '勝ち')
                    .length /
                    stats.recentMatches.length) *
                    100
                )}%`
              : '0%'
          }
          color="bg-blue-500"
          link="/statistics"
        />
        <StatCard
          icon={Calendar}
          title="今日の対戦"
          value={stats.todayPairings.length}
          color="bg-purple-500"
          link="/pairings/generate"
        />
      </div>

      {/* 今日の対戦 */}
      {stats.todayPairings.length > 0 && (
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
              <Calendar className="w-5 h-5 text-purple-600" />
              今日の対戦
            </h2>
            <Link
              to="/pairings/generate"
              className="text-sm text-purple-600 hover:text-purple-700 flex items-center gap-1"
            >
              詳細を見る
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          <div className="space-y-3">
            {stats.todayPairings.map((pairing) => (
              <div
                key={pairing.id}
                className="p-4 bg-purple-50 rounded-lg border border-purple-100"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="text-center">
                      <p className="font-medium text-gray-900">
                        {pairing.player1Name}
                      </p>
                    </div>
                    <div className="text-gray-400 font-bold">VS</div>
                    <div className="text-center">
                      <p className="font-medium text-gray-900">
                        {pairing.player2Name}
                      </p>
                    </div>
                  </div>
                  <div className="text-sm text-gray-600">
                    試合 {pairing.matchNumber}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 最近の活動 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 最近の試合 */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
              <Trophy className="w-5 h-5 text-primary-600" />
              最近の試合
            </h2>
            <Link
              to="/matches"
              className="text-sm text-primary-600 hover:text-primary-700 flex items-center gap-1"
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
                  className="block p-3 hover:bg-gray-50 rounded-lg transition-colors"
                >
                  <div className="flex justify-between items-center">
                    <div>
                      <p className="font-medium text-gray-900">
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

        {/* 次の練習 */}
        <div className="bg-white rounded-lg shadow-sm p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2">
              <BookOpen className="w-5 h-5 text-green-600" />
              次の練習
            </h2>
            <Link
              to="/practice"
              className="text-sm text-green-600 hover:text-green-700 flex items-center gap-1"
            >
              すべて見る
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
          {stats.recentPractices.length > 0 ? (
            <div className="space-y-3">
              {stats.recentPractices.map((practice) => (
                <Link
                  key={practice.id}
                  to="/practice"
                  className="block p-3 hover:bg-gray-50 rounded-lg transition-colors"
                >
                  <div className="flex justify-between items-center">
                    <div>
                      <p className="font-medium text-gray-900">
                        {practice.location || '練習'}
                      </p>
                      <p className="text-sm text-gray-600">
                        {new Date(practice.sessionDate).toLocaleDateString(
                          'ja-JP'
                        )}
                      </p>
                    </div>
                    <p className="text-sm text-gray-600">
                      {practice.participantCount || 0}名参加
                    </p>
                  </div>
                </Link>
              ))}
            </div>
          ) : (
            <p className="text-gray-500 text-center py-8">
              予定されている練習がありません
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default Home;
