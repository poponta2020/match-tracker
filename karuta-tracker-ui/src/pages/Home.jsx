import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { matchAPI, practiceAPI, pairingAPI } from '../api';
import {
  Trophy,
  BookOpen,
  TrendingUp,
  Calendar,
  Plus,
  ArrowRight,
  Clock,
  MapPin,
} from 'lucide-react';

const Home = () => {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [stats, setStats] = useState({
    recentMatches: [],
    recentPractices: [],
    todayPairings: [],
    loading: true,
  });
  const [todayMatch, setTodayMatch] = useState(null);

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

        // 今日の対戦情報を取得
        await fetchTodayMatch(todayStr);
      } catch (error) {
        console.error('データ取得エラー:', error);
        setStats((prev) => ({ ...prev, loading: false }));
      }
    };

    const fetchTodayMatch = async (todayStr) => {
      try {
        // 1. 今日の練習セッションを取得
        const sessionRes = await fetch(`http://localhost:8080/api/practice-sessions/date?date=${todayStr}`)
          .then(res => res.ok ? res.json() : null)
          .catch(() => null);

        if (!sessionRes) {
          setTodayMatch(null);
          return;
        }

        // 2. 自分が参加している試合番号を取得
        const participantsRes = await practiceAPI.getParticipants(sessionRes.id);
        const participants = participantsRes.data || [];

        // 試合別参加状況を取得
        const year = new Date(todayStr).getFullYear();
        const month = new Date(todayStr).getMonth() + 1;
        const participationsRes = await practiceAPI.getPlayerParticipations(currentPlayer.id, year, month);
        const myParticipations = participationsRes.data[sessionRes.id] || [];

        if (myParticipations.length === 0) {
          setTodayMatch(null);
          return;
        }

        // 3. 今日の対戦組み合わせを取得
        const pairingsRes = await pairingAPI.getByDate(todayStr);
        const allPairings = pairingsRes.data || [];

        // 4. 今日の試合結果を取得（キャッシュ無効化）
        const matchesRes = await fetch(`http://localhost:8080/api/matches?date=${todayStr}`, {
          cache: 'no-store',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        })
          .then(res => res.ok ? res.json() : [])
          .catch(() => []);

        // 5. 自分が参加する試合で、未入力の最小試合番号を見つける
        let defaultMatchNumber = null;
        for (const matchNum of myParticipations.sort((a, b) => a - b)) {
          // この試合番号の自分の試合記録を確認
          const hasRecord = matchesRes.some(m =>
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

        // 6. その試合の対戦カードを取得
        const myPairing = allPairings.find(p =>
          p.matchNumber === defaultMatchNumber &&
          (p.player1Id === currentPlayer.id || p.player2Id === currentPlayer.id)
        );

        // 7. 今日の対戦情報を設定
        setTodayMatch({
          session: sessionRes,
          defaultMatchNumber,
          myParticipations,
          allPairings: allPairings.filter(p => myParticipations.includes(p.matchNumber)),
          myPairing,
          matchRecords: matchesRes,
        });

      } catch (error) {
        console.error('今日の対戦情報取得エラー:', error);
        setTodayMatch(null);
      }
    };

    if (currentPlayer?.id) {
      fetchData();
    }

    // 画面フォーカス時にデータを再取得（試合結果入力後の更新対応）
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
        <h1 className="text-3xl font-bold mb-2">
          ようこそ、{currentPlayer?.name}さん
        </h1>
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
                      // 入力済み: 結果を表示（名前なし、記号と点差のみ）
                      return (
                        <div className="mt-4 p-4 bg-white rounded-lg border-2 border-gray-200">
                          <div className="text-center text-lg font-bold text-gray-900">
                            {myMatchRecord.result === '勝ち' ? '〇' : '×'}{myMatchRecord.scoreDifference}
                          </div>
                        </div>
                      );
                    } else {
                      // 未入力: 入力ボタンを表示
                      return (
                        <button
                          onClick={() => {
                            const opponentName = todayMatch.myPairing.player1Id === currentPlayer.id
                              ? todayMatch.myPairing.player2Name
                              : todayMatch.myPairing.player1Name;
                            navigate('/matches/new', {
                              state: {
                                matchDate: todayMatch.session.sessionDate,
                                matchNumber: todayMatch.defaultMatchNumber,
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
                {todayMatch.myParticipations.sort((a, b) => a - b).map((matchNum) => {
                  const hasRecord = todayMatch.matchRecords.some(m =>
                    m.matchNumber === matchNum &&
                    (m.playerId === currentPlayer.id || m.opponentName === currentPlayer.name)
                  );
                  const isSelected = matchNum === todayMatch.defaultMatchNumber;

                  return (
                    <button
                      key={matchNum}
                      onClick={() => {
                        // 試合番号を切り替える
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
                        {practice.venueName || '練習'}
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
