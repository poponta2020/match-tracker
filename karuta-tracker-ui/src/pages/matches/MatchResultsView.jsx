import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { matchAPI, pairingAPI, practiceAPI } from '../../api';
import apiClient from '../../api/client';
import { isAdmin, isSuperAdmin } from '../../utils/auth';
import { AlertCircle, CheckCircle, Edit, ChevronLeft, ChevronRight, Calendar } from 'lucide-react';

const MatchResultsView = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const dateParam = searchParams.get('date');

  const [session, setSession] = useState(null);
  const [pairings, setPairings] = useState([]);
  const [matches, setMatches] = useState([]);
  const [currentMatchNumber, setCurrentMatchNumber] = useState(1);
  const [loading, setLoading] = useState(true);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState(null);

  // 日付選択関連の状態
  const [selectedDate, setSelectedDate] = useState(null);
  const [availableDates, setAvailableDates] = useState([]);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const initialFetchDone = useRef(false);
  const lastFetchedDate = useRef(null);

  // 日付データの取得ヘルパー
  const fetchDataForDate = async (date) => {
    const [sessionResponse, pairingsResponse, matchesResponse] = await Promise.all([
      practiceAPI.getByDate(date).catch(() => null),
      apiClient.get('/match-pairings/date', { params: { date, light: true } }).catch(() => ({ data: [] })),
      apiClient.get(`/matches?date=${date}`).catch(() => ({ data: [] })),
    ]);
    return { sessionResponse, pairingsResponse, matchesResponse };
  };

  const applyData = (data) => {
    if (data.sessionResponse?.data) {
      setSession(data.sessionResponse.data);
      setPairings(data.pairingsResponse.data || []);
      setMatches(data.matchesResponse.data || []);
    } else {
      setSession(null);
      setPairings([]);
      setMatches([]);
    }
  };

  // 初回：日付リスト + 今日のデータを並列取得
  useEffect(() => {
    const fetchInitial = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];
        const targetDate = dateParam || today;
        const fromDate = new Date();
        fromDate.setDate(fromDate.getDate() - 30);
        const fromDateStr = fromDate.toISOString().split('T')[0];

        // 日付リストとターゲット日付のデータを同時取得
        const [datesResponse, targetData] = await Promise.all([
          practiceAPI.getDates(fromDateStr),
          fetchDataForDate(targetDate),
        ]);

        const dates = datesResponse.data || [];
        setAvailableDates(dates);

        // 初期日付の決定: URLパラメータ > 今日 > 今日以前で最も近い練習日 > 最新日付
        const initialDate = dateParam || dates.find(d => d === today) || dates.find(d => d <= today) || dates[0] || null;
        setSelectedDate(initialDate);

        if (initialDate === targetDate) {
          applyData(targetData);
        } else if (initialDate) {
          const data = await fetchDataForDate(initialDate);
          applyData(data);
        }

        lastFetchedDate.current = initialDate;
        initialFetchDone.current = true;
        setInitialLoading(false);
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
        initialFetchDone.current = true;
        setInitialLoading(false);
      } finally {
        setLoading(false);
      }
    };

    fetchInitial();
  }, [sessionId]);

  // 日付変更時のデータ取得（ユーザー操作による変更のみ）
  useEffect(() => {
    if (!initialFetchDone.current || !selectedDate || selectedDate === lastFetchedDate.current) return;
    lastFetchedDate.current = selectedDate;

    const fetchDataByDate = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await fetchDataForDate(selectedDate);
        applyData(data);
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

  if (initialLoading || loading) {
    return (
      <div className="min-h-screen bg-[#f2ede6] flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#4a6b5a] mx-auto"></div>
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
      <div className="min-h-screen bg-[#f2ede6] pb-20">
        {/* ナビゲーションバー */}
        <div className="bg-[#d4ddd7] border-b border-[#c5cec8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
          <div className="max-w-7xl mx-auto">
            <div className="flex items-center justify-between py-3">
              <button
                onClick={goToPreviousDate}
                disabled={!hasPreviousDate()}
                className={`p-2 rounded-full transition-colors ${
                  hasPreviousDate()
                    ? 'hover:bg-[#c5cec8] text-[#374151]'
                    : 'text-gray-300 cursor-not-allowed'
                }`}
              >
                <ChevronLeft className="w-6 h-6" />
              </button>

              <div className="relative">
                <button
                  onClick={() => setShowDatePicker(!showDatePicker)}
                  className="text-lg font-semibold text-[#374151]"
                >
                  {selectedDate && new Date(selectedDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    weekday: 'short'
                  })}
                </button>

                {showDatePicker && (
                  <div className="absolute top-full mt-2 left-1/2 -translate-x-1/2 bg-white border border-gray-200 rounded-lg shadow-lg z-40 max-h-60 overflow-y-auto min-w-[200px]">
                    {availableDates.map((date) => (
                      <button
                        key={date}
                        onClick={() => {
                          setSelectedDate(date);
                          setShowDatePicker(false);
                        }}
                        className={`block w-full text-left px-4 py-2 hover:bg-[#eef2ef] ${
                          date === selectedDate ? 'bg-[#dce5de] text-[#374151] font-semibold' : 'text-gray-700'
                        }`}
                      >
                        {new Date(date + 'T00:00:00').toLocaleDateString('ja-JP', {
                          month: 'short',
                          day: 'numeric',
                          weekday: 'short'
                        })}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <button
                onClick={goToNextDate}
                disabled={!hasNextDate()}
                className={`p-2 rounded-full transition-colors ${
                  hasNextDate()
                    ? 'hover:bg-[#c5cec8] text-[#374151]'
                    : 'text-gray-300 cursor-not-allowed'
                }`}
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            </div>
          </div>
        </div>

        {/* データなしメッセージ */}
        <div className="max-w-4xl mx-auto px-4 pt-20 py-12 text-center">
          <div className="bg-blue-50 border border-blue-200 rounded-lg p-8">
            <Calendar className="h-16 w-16 text-blue-400 mx-auto mb-4" />
            <h2 className="text-xl font-semibold text-blue-900 mb-2">
              この日は練習がありません
            </h2>
            <p className="text-blue-700">
              {new Date(selectedDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                year: 'numeric',
                month: 'long',
                day: 'numeric'
              })} の練習セッションは登録されていません。
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f2ede6] pb-20">
      {/* ナビゲーションバー */}
      <div className="bg-[#d4ddd7] border-b border-[#c5cec8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
        <div className="max-w-7xl mx-auto">
          {/* 日付選択 */}
          <div className="flex items-center justify-between py-3">
            <button
              onClick={goToPreviousDate}
              disabled={!hasPreviousDate()}
              className={`p-2 rounded-full transition-colors ${
                hasPreviousDate()
                  ? 'hover:bg-[#c5cec8] text-[#374151]'
                  : 'text-gray-300 cursor-not-allowed'
              }`}
            >
              <ChevronLeft className="w-6 h-6" />
            </button>

            <div className="relative">
              <button
                onClick={() => setShowDatePicker(!showDatePicker)}
                className="text-lg font-semibold text-[#374151]"
              >
                {selectedDate && new Date(selectedDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  weekday: 'short'
                })}
              </button>

              {showDatePicker && (
                <div className="absolute top-full mt-2 left-1/2 -translate-x-1/2 bg-white border border-gray-200 rounded-lg shadow-lg z-40 max-h-60 overflow-y-auto min-w-[200px]">
                  {availableDates.map((date) => (
                    <button
                      key={date}
                      onClick={() => {
                        setSelectedDate(date);
                        setShowDatePicker(false);
                      }}
                      className={`block w-full text-left px-4 py-2 hover:bg-[#eef2ef] ${
                        date === selectedDate ? 'bg-[#dce5de] text-[#374151] font-semibold' : 'text-gray-700'
                      }`}
                    >
                      {new Date(date + 'T00:00:00').toLocaleDateString('ja-JP', {
                        month: 'short',
                        day: 'numeric',
                        weekday: 'short'
                      })}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              onClick={goToNextDate}
              disabled={!hasNextDate()}
              className={`p-2 rounded-full transition-colors ${
                hasNextDate()
                  ? 'hover:bg-[#c5cec8] text-[#374151]'
                  : 'text-gray-300 cursor-not-allowed'
              }`}
            >
              <ChevronRight className="w-6 h-6" />
            </button>
          </div>

          {/* タブバー */}
          {totalMatches > 0 && (
            <div className="flex overflow-x-auto -mb-px">
              {Array.from({ length: totalMatches }, (_, i) => i + 1).map(num => (
                <button
                  key={num}
                  onClick={() => setCurrentMatchNumber(num)}
                  className={`flex-shrink-0 px-4 py-2 text-sm font-medium transition-colors border-b-2 ${
                    currentMatchNumber === num
                      ? 'border-[#374151] text-[#374151]'
                      : 'border-transparent text-[#6b7280] hover:text-[#374151] hover:border-[#8a9e90]'
                  }`}
                >
                  {num}試合目{isMatchCompleted(num) ? ' ✓' : ''}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* メインコンテンツ */}
      <div className="max-w-4xl mx-auto px-6 pt-24 pb-6">
        <div className="divide-y divide-[#d4ddd7]">
          {currentPairings.map((pairing, index) => {
            const match = getMatchResult(currentMatchNumber, pairing.player1Id, pairing.player2Id);
            const isPlayer1Winner = match && match.winnerId === pairing.player1Id;
            const isPlayer2Winner = match && match.winnerId === pairing.player2Id;

            return (
              <div key={index} className="py-4">
                {match ? (
                  // 結果入力済み: A 〇 枚数差 × B
                  <div className="flex items-center text-lg">
                    <Link
                      to={`/matches?playerId=${pairing.player1Id}`}
                      className={`flex-1 text-right pr-2 font-semibold truncate hover:underline ${isPlayer1Winner ? 'text-green-600' : 'text-gray-700'}`}
                    >
                      {pairing.player1Name}
                    </Link>
                    <div className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${isPlayer1Winner ? 'text-green-600' : 'text-red-600'}`}>
                      {isPlayer1Winner ? '〇' : '×'}
                    </div>
                    <div className="font-bold text-gray-900 w-10 text-center flex-shrink-0">
                      {match.scoreDifference}
                    </div>
                    <div className={`text-2xl font-bold w-8 text-center flex-shrink-0 ${isPlayer2Winner ? 'text-green-600' : 'text-red-600'}`}>
                      {isPlayer2Winner ? '〇' : '×'}
                    </div>
                    <Link
                      to={`/matches?playerId=${pairing.player2Id}`}
                      className={`flex-1 text-left pl-2 font-semibold truncate hover:underline ${isPlayer2Winner ? 'text-green-600' : 'text-gray-700'}`}
                    >
                      {pairing.player2Name}
                    </Link>
                  </div>
                ) : (
                  // 未入力: A vs B
                  <div className="flex items-center text-lg">
                    <Link
                      to={`/matches?playerId=${pairing.player1Id}`}
                      className="flex-1 text-right pr-3 font-semibold text-gray-700 truncate hover:underline"
                    >
                      {pairing.player1Name}
                    </Link>
                    <div className="text-sm font-medium text-[#9ca3af] w-8 text-center flex-shrink-0">
                      vs
                    </div>
                    <Link
                      to={`/matches?playerId=${pairing.player2Id}`}
                      className="flex-1 text-left pl-3 font-semibold text-gray-700 truncate hover:underline"
                    >
                      {pairing.player2Name}
                    </Link>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* 管理者用：編集ボタン */}
        {(isAdmin() || isSuperAdmin()) && session && (
          <button
            onClick={() => navigate(`/matches/bulk-input/${session.id}`)}
            className="w-full mt-6 py-3 px-4 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] flex items-center justify-center gap-2 font-semibold transition-colors"
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
