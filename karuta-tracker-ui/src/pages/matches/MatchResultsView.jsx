import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { matchAPI, pairingAPI, practiceAPI } from '../../api';
import { isAdmin, isSuperAdmin } from '../../utils/auth';
import { AlertCircle, CheckCircle, Edit, ChevronLeft, ChevronRight, Calendar } from 'lucide-react';

const MatchResultsView = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();

  const [session, setSession] = useState(null);
  const [pairings, setPairings] = useState([]);
  const [matches, setMatches] = useState([]);
  const [currentMatchNumber, setCurrentMatchNumber] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 日付選択関連の状態
  const [selectedDate, setSelectedDate] = useState(null);
  const [allSessions, setAllSessions] = useState([]);
  const [availableDates, setAvailableDates] = useState([]);
  const [showDatePicker, setShowDatePicker] = useState(false);

  // 全練習セッション取得（初回のみ）
  useEffect(() => {
    const fetchAllSessions = async () => {
      try {
        const response = await practiceAPI.getAll();
        const sessions = response.data || [];
        setAllSessions(sessions);

        // 日付リストを抽出（降順ソート）
        const dates = sessions
          .map(s => s.sessionDate)
          .sort((a, b) => new Date(b) - new Date(a));
        setAvailableDates(dates);

        // 初期日付の決定
        if (sessionId) {
          // sessionIdがある場合、そのセッションの日付を取得
          const targetSession = sessions.find(s => s.id === parseInt(sessionId));
          if (targetSession) {
            setSelectedDate(targetSession.sessionDate);
          } else {
            // sessionIdが見つからない場合は最新日付
            setSelectedDate(dates[0] || null);
          }
        } else {
          // sessionIdがない場合は最新の練習日
          setSelectedDate(dates[0] || null);
        }
      } catch (err) {
        console.error('練習セッション一覧の取得に失敗:', err);
        setError('練習セッション一覧の取得に失敗しました');
      }
    };

    fetchAllSessions();
  }, [sessionId]);

  // 選択された日付のデータ取得
  useEffect(() => {
    const fetchDataByDate = async () => {
      if (!selectedDate) {
        setLoading(false);
        return;
      }

      try {
        setLoading(true);
        setError(null);

        // 練習セッション情報取得（日付ベース）
        const sessionResponse = await practiceAPI.getByDate(selectedDate);
        const sessionData = sessionResponse.data;

        if (!sessionData) {
          // セッションが存在しない場合
          setSession(null);
          setPairings([]);
          setMatches([]);
          setLoading(false);
          return;
        }

        setSession(sessionData);

        // 対戦ペアリング取得（日付ベース）
        const pairingsResponse = await pairingAPI.getByDate(selectedDate);
        setPairings(pairingsResponse.data || []);

        // 試合結果取得（日付ベース）
        // キャッシュ無効化: 常に最新のデータを取得するため
        const matchesResponse = await fetch(`http://localhost:8080/api/matches?date=${selectedDate}`, {
          cache: 'no-store',
          headers: {
            'Cache-Control': 'no-cache',
            'Pragma': 'no-cache'
          }
        });
        if (!matchesResponse.ok) throw new Error('試合結果の取得に失敗しました');
        const sessionMatches = await matchesResponse.json();
        setMatches(sessionMatches);

      } catch (err) {
        console.error('データ取得エラー:', err);
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchDataByDate();
  }, [selectedDate]);

  // 前後の練習日に移動
  const goToPreviousDate = () => {
    const currentIndex = availableDates.indexOf(selectedDate);
    if (currentIndex < availableDates.length - 1) {
      setSelectedDate(availableDates[currentIndex + 1]);
    }
  };

  const goToNextDate = () => {
    const currentIndex = availableDates.indexOf(selectedDate);
    if (currentIndex > 0) {
      setSelectedDate(availableDates[currentIndex - 1]);
    }
  };

  const hasPreviousDate = () => {
    const currentIndex = availableDates.indexOf(selectedDate);
    return currentIndex < availableDates.length - 1;
  };

  const hasNextDate = () => {
    const currentIndex = availableDates.indexOf(selectedDate);
    return currentIndex > 0;
  };

  // 選択可能な日付かチェック
  const isDateAvailable = (dateStr) => {
    return availableDates.includes(dateStr);
  };

  // 試合番号ごとのペアリングを取得
  const getPairingsForMatch = (matchNumber) => {
    return pairings.filter(p => p.matchNumber === matchNumber);
  };

  // 試合結果を取得
  const getMatchResult = (matchNumber, player1Id, player2Id) => {
    return matches.find(
      m => m.matchNumber === matchNumber &&
           ((m.player1Id === player1Id && m.player2Id === player2Id) ||
            (m.player1Id === player2Id && m.player2Id === player1Id))
    );
  };

  // 試合が完了しているかチェック
  const isMatchCompleted = (matchNumber) => {
    const matchPairings = getPairingsForMatch(matchNumber);
    return matchPairings.every(pairing => {
      const match = getMatchResult(matchNumber, pairing.player1Id, pairing.player2Id);
      return match !== undefined;
    });
  };

  // 試合の統計を計算
  const getMatchStats = (matchNumber) => {
    const matchPairings = getPairingsForMatch(matchNumber);
    const completedCount = matchPairings.filter(pairing => {
      const match = getMatchResult(matchNumber, pairing.player1Id, pairing.player2Id);
      return match !== undefined;
    }).length;

    const totalPairs = matchPairings.length;
    const completionRate = totalPairs > 0 ? Math.round((completedCount / totalPairs) * 100) : 0;

    // 平均枚数差を計算
    let totalScoreDiff = 0;
    let countWithScore = 0;
    matchPairings.forEach(pairing => {
      const match = getMatchResult(matchNumber, pairing.player1Id, pairing.player2Id);
      if (match && match.scoreDifference !== null) {
        totalScoreDiff += match.scoreDifference;
        countWithScore++;
      }
    });
    const avgScoreDiff = countWithScore > 0 ? (totalScoreDiff / countWithScore).toFixed(1) : 0;

    return {
      totalPairs,
      completedCount,
      pendingCount: totalPairs - completedCount,
      completionRate,
      avgScoreDiff,
    };
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4">
        <div className="bg-red-50 border border-red-200 rounded-lg p-6 max-w-md">
          <div className="flex items-center gap-2 text-red-800 mb-2">
            <AlertCircle className="h-5 w-5" />
            <h2 className="font-semibold">エラー</h2>
          </div>
          <p className="text-red-700">{error}</p>
          <button
            onClick={() => navigate('/')}
            className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
          >
            ホームに戻る
          </button>
        </div>
      </div>
    );
  }

  const currentPairings = getPairingsForMatch(currentMatchNumber);
  const totalMatches = session?.totalMatches || 0;
  const stats = getMatchStats(currentMatchNumber);

  // 今日の日付を取得（YYYY-MM-DD形式）
  const getTodayDate = () => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  };

  // 選択中の日付が今日かチェック
  const isToday = () => {
    return selectedDate === getTodayDate();
  };

  // データなし画面
  if (!loading && !session && selectedDate) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="bg-white shadow-sm sticky top-0 z-30">
          <div className="max-w-4xl mx-auto px-4 py-4">
            {/* 日付選択UI */}
            <div className="flex items-center justify-center gap-2 mb-4">
              <button
                onClick={goToPreviousDate}
                disabled={!hasPreviousDate()}
                className={`p-2 rounded-full ${
                  hasPreviousDate()
                    ? 'hover:bg-gray-100 text-gray-700'
                    : 'text-gray-300 cursor-not-allowed'
                }`}
              >
                <ChevronLeft className="h-5 w-5" />
              </button>

              <div className="relative">
                <button
                  onClick={() => setShowDatePicker(!showDatePicker)}
                  className="flex items-center gap-2 px-4 py-2 bg-primary-50 text-primary-700 rounded-lg hover:bg-primary-100"
                >
                  <Calendar className="h-4 w-4" />
                  <span className="font-semibold">{selectedDate}</span>
                </button>

                {/* 日付選択ドロップダウン */}
                {showDatePicker && (
                  <div className="absolute top-full mt-2 bg-white border border-gray-200 rounded-lg shadow-lg z-40 max-h-60 overflow-y-auto">
                    {availableDates.map((date) => (
                      <button
                        key={date}
                        onClick={() => {
                          setSelectedDate(date);
                          setShowDatePicker(false);
                        }}
                        className={`block w-full text-left px-4 py-2 hover:bg-gray-100 ${
                          date === selectedDate ? 'bg-primary-50 text-primary-700 font-semibold' : 'text-gray-700'
                        }`}
                      >
                        {date}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <button
                onClick={goToNextDate}
                disabled={!hasNextDate()}
                className={`p-2 rounded-full ${
                  hasNextDate()
                    ? 'hover:bg-gray-100 text-gray-700'
                    : 'text-gray-300 cursor-not-allowed'
                }`}
              >
                <ChevronRight className="h-5 w-5" />
              </button>
            </div>
          </div>
        </div>

        {/* データなしメッセージ */}
        <div className="max-w-4xl mx-auto px-4 py-12 text-center">
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-8">
            <Calendar className="h-16 w-16 text-blue-400 mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-blue-900 mb-2">
              この日は練習がありません
            </h2>
            <p className="text-blue-700">
              {selectedDate} の練習セッションは登録されていません。
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-20">
      {/* ヘッダー */}
      <div className="bg-white shadow-sm sticky top-0 z-30">
        <div className="max-w-4xl mx-auto px-4 py-4">
          <div className="mb-4">
            <h1 className="text-xl font-bold text-gray-900">試合結果詳細</h1>
          </div>

          {/* 日付選択UI */}
          <div className="flex items-center justify-center gap-2 mb-4">
            <button
              onClick={goToPreviousDate}
              disabled={!hasPreviousDate()}
              className={`p-2 rounded-full ${
                hasPreviousDate()
                  ? 'hover:bg-gray-100 text-gray-700'
                  : 'text-gray-300 cursor-not-allowed'
              }`}
            >
              <ChevronLeft className="h-5 w-5" />
            </button>

            <div className="relative">
              <button
                onClick={() => setShowDatePicker(!showDatePicker)}
                className="flex items-center gap-2 px-4 py-2 bg-primary-50 text-primary-700 rounded-lg hover:bg-primary-100"
              >
                <Calendar className="h-4 w-4" />
                <span className="font-semibold">{selectedDate}</span>
              </button>

              {/* 日付選択ドロップダウン */}
              {showDatePicker && (
                <div className="absolute top-full mt-2 bg-white border border-gray-200 rounded-lg shadow-lg z-40 max-h-60 overflow-y-auto">
                  {availableDates.map((date) => (
                    <button
                      key={date}
                      onClick={() => {
                        setSelectedDate(date);
                        setShowDatePicker(false);
                      }}
                      className={`block w-full text-left px-4 py-2 hover:bg-gray-100 ${
                        date === selectedDate ? 'bg-primary-50 text-primary-700 font-semibold' : 'text-gray-700'
                      }`}
                    >
                      {date}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              onClick={goToNextDate}
              disabled={!hasNextDate()}
              className={`p-2 rounded-full ${
                hasNextDate()
                  ? 'hover:bg-gray-100 text-gray-700'
                  : 'text-gray-300 cursor-not-allowed'
              }`}
            >
              <ChevronRight className="h-5 w-5" />
            </button>
          </div>

          {session && (
            <div className="space-y-1 text-sm text-gray-600">
              <p>🏛️ {session.venueName}</p>
              <p>👥 参加者: {pairings.length * 2}名</p>
            </div>
          )}
        </div>
      </div>

      {/* タブバー */}
      <div className="bg-white border-b sticky top-[120px] z-20">
        <div className="max-w-4xl mx-auto px-4 overflow-x-auto">
          <div className="flex gap-2 py-2">
            {Array.from({ length: totalMatches }, (_, i) => i + 1).map(num => (
              <button
                key={num}
                onClick={() => setCurrentMatchNumber(num)}
                className={`flex-shrink-0 px-4 py-2 rounded-t-lg transition-colors ${
                  currentMatchNumber === num
                    ? 'bg-primary-600 text-white border-b-2 border-primary-600'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                <div className="text-center">
                  <div className="font-semibold">
                    {num}試合{isMatchCompleted(num) ? '✓' : ''}
                  </div>
                  <div className="text-xs opacity-80">
                    {session?.startTime && `${num === 1 ? session.startTime : ''}`}
                  </div>
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* メインコンテンツ */}
      <div className="max-w-4xl mx-auto px-4 py-6">
        <div className="bg-white rounded-lg shadow-sm p-4 mb-4">
          <h2 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
            第{currentMatchNumber}試合 ({currentPairings.length * 2}名参加)
            {isMatchCompleted(currentMatchNumber) && (
              <CheckCircle className="h-5 w-5 text-green-600" />
            )}
          </h2>

          <div className="space-y-3">
            {currentPairings.map((pairing, index) => {
              const match = getMatchResult(currentMatchNumber, pairing.player1Id, pairing.player2Id);
              const isPlayer1Winner = match && match.winnerId === pairing.player1Id;
              const isPlayer2Winner = match && match.winnerId === pairing.player2Id;

              return (
                <div
                  key={index}
                  className="border border-gray-200 rounded-lg p-4"
                >
                  <div className="flex items-center justify-between gap-4">
                    {/* 選手1 */}
                    <div
                      className={`flex-1 px-4 py-2 rounded-lg ${
                        isPlayer1Winner
                          ? 'bg-green-100 border-2 border-green-500 font-semibold'
                          : 'bg-gray-50'
                      }`}
                    >
                      {isPlayer1Winner && '🏆 '}
                      {pairing.player1Name}
                    </div>

                    {/* 枚数差 */}
                    <div className="px-3 py-2 text-center min-w-[100px]">
                      {match ? (
                        <span className="font-semibold text-gray-900">
                          {match.scoreDifference}枚差
                        </span>
                      ) : (
                        <span className="text-gray-400">未入力</span>
                      )}
                    </div>

                    {/* 選手2 */}
                    <div
                      className={`flex-1 text-right px-4 py-2 rounded-lg ${
                        isPlayer2Winner
                          ? 'bg-green-100 border-2 border-green-500 font-semibold'
                          : 'bg-gray-50'
                      }`}
                    >
                      {isPlayer2Winner && '🏆 '}
                      {pairing.player2Name}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* 統計情報 */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-4">
          <h3 className="font-semibold text-blue-900 mb-3 flex items-center gap-2">
            📊 第{currentMatchNumber}試合の統計
          </h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-blue-700">対戦数</p>
              <p className="text-lg font-bold text-blue-900">{stats.totalPairs}組</p>
            </div>
            <div>
              <p className="text-blue-700">入力済み</p>
              <p className="text-lg font-bold text-green-600">
                {stats.completedCount}組 ({stats.completionRate}%)
              </p>
            </div>
            <div>
              <p className="text-blue-700">未入力</p>
              <p className="text-lg font-bold text-gray-600">{stats.pendingCount}組</p>
            </div>
            <div>
              <p className="text-blue-700">平均枚数差</p>
              <p className="text-lg font-bold text-blue-900">{stats.avgScoreDiff}枚</p>
            </div>
          </div>
        </div>

        {/* 管理者用：編集ボタン */}
        {(isAdmin() || isSuperAdmin()) && (isSuperAdmin() || isToday()) && session && (
          <button
            onClick={() => navigate(`/matches/bulk-input/${session.id}`)}
            className="w-full py-3 px-4 bg-primary-600 text-white rounded-lg hover:bg-primary-700 flex items-center justify-center gap-2 font-semibold"
          >
            <Edit className="w-5 h-5" />
            結果を編集・入力する
          </button>
        )}
      </div>
    </div>
  );
};

export default MatchResultsView;
