import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI, pairingAPI } from '../api';
import apiClient from '../api/client';
import {
  Trophy,
  BookOpen,
  TrendingUp,
  Calendar,
  Plus,
  ArrowRight,
  Clock,
  MapPin,
  ClipboardList,
} from 'lucide-react';
import { isAdmin, isSuperAdmin } from '../utils/auth';

const Home = () => {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();
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
  const [todayMatch, setTodayMatch] = useState(null);
  const [todaySessionId, setTodaySessionId] = useState(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const today = new Date();
        const todayStr = today.toISOString().split('T')[0];
        today.setHours(0, 0, 0, 0);
        const year = today.getFullYear();
        const month = today.getMonth() + 1;

        // 全APIを並列実行（直列→並列化で大幅高速化）
        const [
          matchesRes,
          statisticsRes,
          practicesRes,
          todayPairingsRes,
          participationsRes,
          todayMatchesRes,
        ] = await Promise.all([
          matchAPI.getByPlayerId(currentPlayer.id).catch(() => ({ data: [] })),
          matchAPI.getStatistics(currentPlayer.id).catch(() => ({ data: { totalMatches: 0, wins: 0, winRate: 0 } })),
          apiClient.get('/practice-sessions').then(res => res.data).catch(() => []),
          pairingAPI.getByDate(todayStr).catch(() => ({ data: [] })),
          practiceAPI.getPlayerParticipations(currentPlayer.id, year, month).catch(() => ({ data: {} })),
          apiClient.get(`/matches?date=${todayStr}`).then(res => res.data).catch(() => []),
        ]);

        const allPractices = Array.isArray(practicesRes) ? practicesRes : [];

        // 今日のセッションを練習一覧から検索（追加APIコール不要）
        const todaySession = allPractices.find(p => p.sessionDate === todayStr) || null;
        if (todaySession) {
          setTodaySessionId(todaySession.id);
        }

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

        // 今日の対戦情報を設定
        if (!todaySession) {
          setTodayMatch(null);
          return;
        }

        const myParticipations = (participationsRes.data || {})[todaySession.id] || [];

        if (myParticipations.length === 0) {
          setTodayMatch(null);
          return;
        }

        const allPairings = todayPairingsRes.data || [];

        // 未入力の最小試合番号を見つける
        let defaultMatchNumber = null;
        for (const matchNum of [...myParticipations].sort((a, b) => a - b)) {
          const hasRecord = todayMatchesRes.some(m =>
            m.matchNumber === matchNum &&
            (m.player1Id === currentPlayer.id || m.player2Id === currentPlayer.id)
          );
          if (!hasRecord) {
            defaultMatchNumber = matchNum;
            break;
          }
        }

        // 全試合入力済みの場合は最後の試合を表示
        if (!defaultMatchNumber && myParticipations.length > 0) {
          defaultMatchNumber = Math.max(...myParticipations);
        }

        if (!defaultMatchNumber) {
          setTodayMatch(null);
          return;
        }

        const myPairing = allPairings.find(p =>
          p.matchNumber === defaultMatchNumber &&
          (p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id)
        );

        setTodayMatch({
          session: todaySession,
          defaultMatchNumber,
          myParticipations,
          allPairings: allPairings.filter(p => myParticipations.includes(p.matchNumber)),
          myPairing,
          matchRecords: todayMatchesRes,
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
        <p className="text-xl font-semibold mb-2">
          ようこそ、{currentPlayer?.name}さん
        </p>
        <p className="text-primary-100">
          今日も練習頑張りましょう！
        </p>
      </div>

      {/* 今日の対戦カード */}
      {todayMatch && (
        <div className="bg-white rounded-lg shadow-md p-6">
          <h2 className="text-xl font-bold text-gray-900 flex items-center gap-2 mb-4">
            <Trophy className="w-6 h-6 text-primary-600" />
            今日の対戦
          </h2>

          <div className="space-y-4">
            {/* 練習日情報 */}
            <div className="flex flex-wrap gap-3 text-sm text-gray-600">
              <div className="flex items-center gap-1">
                <Calendar className="w-4 h-4" />
                {new Date(todayMatch.session.sessionDate).toLocaleDateString('ja-JP', {
                  month: 'long',
                  day: 'numeric',
                  weekday: 'short'
                })}
              </div>
              {todayMatch.session.startTime && (
                <div className="flex items-center gap-1">
                  <Clock className="w-4 h-4" />
                  {todayMatch.session.startTime}～{todayMatch.session.endTime || ''}
                </div>
              )}
              {todayMatch.session.venueName && (
                <div className="flex items-center gap-1">
                  <MapPin className="w-4 h-4" />
                  {todayMatch.session.venueName}
                </div>
              )}
            </div>

            {/* 対戦カード */}
            <div className="border-2 border-primary-100 rounded-lg p-6 bg-primary-50">
              <div className="text-center mb-4">
                <span className="inline-block bg-primary-600 text-white px-4 py-1 rounded-full font-bold text-lg">
                  第{todayMatch.defaultMatchNumber}試合
                </span>
              </div>

              {todayMatch.myPairing ? (
                <>
                  {/* 対戦相手の名前のみ表示 */}
                  <div className="text-center my-6">
                    <div className="text-2xl font-bold text-gray-900">
                      {todayMatch.myPairing.player1Id === currentPlayer.id
                        ? todayMatch.myPairing.player2Name
                        : todayMatch.myPairing.player1Name}
                    </div>
                  </div>

                  {/* 試合結果の確認 */}
                  {(() => {
                    const myMatchRecord = todayMatch.matchRecords.find(m =>
                      m.matchNumber === todayMatch.defaultMatchNumber &&
                      (m.player1Id === currentPlayer.id || m.player2Id === currentPlayer.id)
                    );

                    if (myMatchRecord) {
                      const isWin = myMatchRecord.winnerId === currentPlayer.id;
                      return (
                        <div className="mt-4 p-4 bg-white rounded-lg border-2 border-gray-200">
                          <div className="text-center text-lg font-bold text-gray-900">
                            {isWin ? '〇' : '×'}{myMatchRecord.scoreDifference}
                          </div>
                        </div>
                      );
                    } else {
                      return (
                        <button
                          onClick={() => {
                            const opponentId = todayMatch.myPairing.player1Id === currentPlayer.id
                              ? todayMatch.myPairing.player2Id
                              : todayMatch.myPairing.player1Id;
                            const opponentName = todayMatch.myPairing.player1Id === currentPlayer.id
                              ? todayMatch.myPairing.player2Name
                              : todayMatch.myPairing.player1Name;
                            navigate('/matches/new', {
                              state: {
                                matchDate: todayMatch.session.sessionDate,
                                matchNumber: todayMatch.defaultMatchNumber,
                                opponentId: opponentId,
                                opponentName: opponentName
                              }
                            });
                          }}
                          className="w-full mt-4 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors font-medium flex items-center justify-center gap-2"
                        >
                          ✏️ タップして結果を入力
                        </button>
                      );
                    }
                  })()}
                </>
              ) : (
                <div className="text-center py-8">
                  <div className="text-lg text-gray-600 mb-2">対戦相手: 未定</div>
                  <div className="text-sm text-gray-500">組み合わせがまだ作成されていません</div>
                </div>
              )}
            </div>

            {/* 試合選択タブ */}
            {todayMatch.myParticipations.length > 1 && (
              <div className="grid gap-2" style={{
                gridTemplateColumns: `repeat(${Math.min(todayMatch.myParticipations.length, 7)}, minmax(0, 1fr))`
              }}>
                {[...todayMatch.myParticipations].sort((a, b) => a - b).map((matchNum) => {
                  const hasRecord = todayMatch.matchRecords.some(m =>
                    m.matchNumber === matchNum &&
                    (m.playerId === currentPlayer.id || m.opponentName === currentPlayer.name)
                  );
                  const isSelected = matchNum === todayMatch.defaultMatchNumber;

                  return (
                    <button
                      key={matchNum}
                      onClick={() => {
                        const myPairing = todayMatch.allPairings.find(p =>
                          p.matchNumber === matchNum &&
                          (p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id)
                        );
                        setTodayMatch({
                          ...todayMatch,
                          defaultMatchNumber: matchNum,
                          myPairing
                        });
                      }}
                      className={`py-3 rounded-lg border-2 font-medium transition-all ${
                        isSelected
                          ? 'border-primary-600 bg-primary-600 text-white'
                          : 'border-gray-300 bg-white text-gray-700 hover:border-primary-300'
                      }`}
                    >
                      <div className="text-lg">{matchNum}</div>
                      <div className="text-xs mt-1">
                        {hasRecord ? '✓' : '□'}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {/* スーパー管理者用：試合結果一括入力ボタン */}
          {isSuperAdmin() && todaySessionId && (
            <div className="mt-4 pt-4 border-t">
              <button
                onClick={() => navigate(`/matches/bulk-input/${todaySessionId}`)}
                className="w-full py-3 px-4 bg-primary-600 text-white rounded-lg hover:bg-primary-700 flex items-center justify-center gap-2 font-semibold"
              >
                <ClipboardList className="w-5 h-5" />
                📝 試合結果一括入力
              </button>
            </div>
          )}
        </div>
      )}

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
          to={todaySessionId ? `/matches/results/${todaySessionId}` : "/pairings"}
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
          value={stats.totalMatches}
          color="bg-primary-500"
          link="/matches"
        />
        <StatCard
          icon={BookOpen}
          title="今後の練習予定"
          value={stats.upcomingPracticesCount}
          color="bg-green-500"
          link="/practice"
        />
        <StatCard
          icon={TrendingUp}
          title="勝率"
          value={`${Math.round(stats.winRate)}%`}
          color="bg-blue-500"
          link="/statistics"
        />
        <StatCard
          icon={Calendar}
          title="今日の対戦"
          value={stats.todayPairings.length}
          color="bg-purple-500"
          link={todaySessionId ? `/matches/results/${todaySessionId}` : "/pairings"}
        />
      </div>

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
                        {practice.venueName || '練習'}
                      </p>
                      <p className="text-sm text-gray-600">
                        {new Date(practice.sessionDate).toLocaleDateString('ja-JP')}
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
