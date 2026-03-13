import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { matchAPI, pairingAPI, practiceAPI } from '../../api';
import apiClient from '../../api/client';
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
  const [availableDates, setAvailableDates] = useState([]);
  const [showDatePicker, setShowDatePicker] = useState(false);

  // 利用可能な日付を取得（初回のみ）
  useEffect(() => {
    const fetchAvailableDates = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];

        // 今日から過去30日分の練習セッションを取得（軽量）
        const fromDate = new Date();
        fromDate.setDate(fromDate.getDate() - 30);
        const fromDateStr = fromDate.toISOString().split('T')[0];

        const response = await practiceAPI.getUpcoming(fromDateStr);
        const sessions = response.data || [];

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
            // sessionIdが見つからない場合は今日の日付
            const todaySession = dates.find(d => d === today);
            setSelectedDate(todaySession || dates[0] || null);
          }
        } else {
          // sessionIdがない場合は今日の日付（存在しない場合は最新の練習日）
          const todaySession = dates.find(d => d === today);
          setSelectedDate(todaySession || dates[0] || null);
        }
      } catch (err) {
        console.error('練習セッション一覧の取得に失敗:', err);
        setError('練習セッション一覧の取得に失敗しました');
      }
    };

    fetchAvailableDates();
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

        // 選択された日付のセッションデータを取得
        const sessionResponse = await practiceAPI.getByDate(selectedDate).catch(() => null);

        if (!sessionResponse || !sessionResponse.data) {
          setSession(null);
          setPairings([]);
          setMatches([]);
          setLoading(false);
          return;
        }

        setSession(sessionResponse.data);

        // 対戦ペアリングと試合結果を並列取得
        const [pairingsResponse, matchesResponse] = await Promise.all([
          pairingAPI.getByDate(selectedDate),
          apiClient.get(`/matches?date=${selectedDate}`),
        ]);
        setPairings(pairingsResponse.data || []);
        setMatches(matchesResponse.data);

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
    if (matchPairings.length === 0) return false;
    return matchPairings.every(pairing => {
      const match = getMatchResult(matchNumber, pairing.player1Id, pairing.player2Id);
      return match !== undefined;
    });
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
      <div className="min-h-screen bg-[#f2ede6]">
        <div className="bg-[#e2d9d0] shadow-sm sticky top-0 z-30">
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
    <div className="min-h-screen bg-[#f2ede6] pb-20">
      {/* ヘッダー */}
      <div className="bg-[#e2d9d0] shadow-sm sticky top-0 z-30">
        <div className="max-w-4xl mx-auto px-4 py-4">
          <div className="mb-4">
            <h1 className="text-xl font-bold">試合結果詳細</h1>
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
      <div className="bg-[#e2d9d0] sticky top-[120px] z-20">
        <div className="max-w-4xl mx-auto px-4 overflow-x-auto">
          <div className="flex gap-1 pt-2">
            {Array.from({ length: totalMatches }, (_, i) => i + 1).map(num => (
              <button
                key={num}
                onClick={() => setCurrentMatchNumber(num)}
                className={`flex-shrink-0 px-4 py-2 rounded-t-lg transition-colors ${
                  currentMatchNumber === num
                    ? 'bg-[#f9f6f2] text-[#5f3a2d] font-semibold border-t-2 border-x-2 border-[#d0c5b8]'
                    : 'bg-[#d0c5b8] text-[#7a5f54] hover:bg-[#c5bab0]'
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
        <div className="bg-[#f9f6f2] rounded-b-lg rounded-tr-lg shadow-sm p-4 mb-4">
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
                  className="bg-[#f9f6f2] border border-[#d0c5b8] rounded-lg p-4"
                >
                  {match ? (
                    // 結果入力済み: A 〇 11 × B 形式
                    <div className="flex items-center justify-center text-lg">
                      <div className={`font-semibold text-right w-32 pr-2 ${isPlayer1Winner ? 'text-green-600' : 'text-gray-700'}`}>
                        {pairing.player1Name}
                      </div>
                      <div className={`font-bold text-2xl w-8 text-center ${isPlayer1Winner ? 'text-green-600' : 'text-red-600'}`}>
                        {isPlayer1Winner ? '〇' : '×'}
                      </div>
                      <div className="font-bold text-gray-900 w-12 text-center">
                        {match.scoreDifference}
                      </div>
                      <div className={`font-bold text-2xl w-8 text-center ${isPlayer2Winner ? 'text-green-600' : 'text-red-600'}`}>
                        {isPlayer2Winner ? '〇' : '×'}
                      </div>
                      <div className={`font-semibold text-left w-32 pl-2 ${isPlayer2Winner ? 'text-green-600' : 'text-gray-700'}`}>
                        {pairing.player2Name}
                      </div>
                    </div>
                  ) : (
                    // 未入力: 従来の表示
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex-1 px-4 py-2 rounded-lg bg-[#f0ebe3]">
                        {pairing.player1Name}
                      </div>
                      <div className="px-3 py-2 text-center min-w-[80px] flex-shrink-0">
                        <span className="text-gray-400">未入力</span>
                      </div>
                      <div className="flex-1 text-right px-4 py-2 rounded-lg bg-[#f0ebe3]">
                        {pairing.player2Name}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
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
