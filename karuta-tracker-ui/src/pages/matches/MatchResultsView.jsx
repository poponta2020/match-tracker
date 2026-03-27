import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams, Link } from 'react-router-dom';
import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI } from '../../api';
import { isAdmin, isSuperAdmin } from '../../utils/auth';
import { AlertCircle, CheckCircle, Edit, ChevronLeft, ChevronRight, Calendar, Plus, BookOpen, User, Eye, UsersRound, MoreHorizontal } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

// カレンダーピッカーコンポーネント
const CalendarPicker = ({ selectedDate, availableDates, onSelectDate, onClose, onMonthChange, calendarLoading }) => {
  const calendarRef = useRef(null);
  const initDate = selectedDate ? new Date(selectedDate + 'T00:00:00') : new Date();
  const [viewYear, setViewYear] = useState(initDate.getFullYear());
  const [viewMonth, setViewMonth] = useState(initDate.getMonth()); // 0-indexed

  // 外側クリックで閉じる
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (calendarRef.current && !calendarRef.current.contains(e.target)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClose]);

  const practiceDateSet = new Set(availableDates);

  const changeMonth = (delta) => {
    let newMonth = viewMonth + delta;
    let newYear = viewYear;
    if (newMonth < 0) { newMonth = 11; newYear--; }
    if (newMonth > 11) { newMonth = 0; newYear++; }
    setViewYear(newYear);
    setViewMonth(newMonth);
    onMonthChange(newYear, newMonth + 1);
  };

  // カレンダーのグリッドを生成
  const firstDay = new Date(viewYear, viewMonth, 1).getDay();
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const today = new Date().toISOString().split('T')[0];

  const cells = [];
  for (let i = 0; i < firstDay; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const weekDays = ['日', '月', '火', '水', '木', '金', '土'];

  return (
    <div ref={calendarRef} className="absolute top-full mt-2 left-1/2 -translate-x-1/2 bg-white border border-gray-200 rounded-lg shadow-lg z-40 p-3 w-[280px]">
      {/* 月ナビゲーション */}
      <div className="flex items-center justify-between mb-2">
        <button onClick={() => changeMonth(-1)} className="p-1 hover:bg-gray-100 rounded">
          <ChevronLeft className="w-4 h-4" />
        </button>
        <span className="font-semibold text-sm text-[#374151]">
          {viewYear}年{viewMonth + 1}月
        </span>
        <button onClick={() => changeMonth(1)} className="p-1 hover:bg-gray-100 rounded">
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>

      {/* 曜日ヘッダー */}
      <div className="grid grid-cols-7 text-center text-xs text-gray-400 mb-1">
        {weekDays.map(w => <div key={w}>{w}</div>)}
      </div>

      {/* 日付グリッド */}
      {calendarLoading ? (
        <div className="flex items-center justify-center py-6">
          <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-[#4a6b5a]"></div>
        </div>
      ) : (
        <div className="grid grid-cols-7 text-center text-sm">
          {cells.map((day, i) => {
            if (day === null) return <div key={`empty-${i}`} />;
            const dateStr = `${viewYear}-${String(viewMonth + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            const isPractice = practiceDateSet.has(dateStr);
            const isSelected = dateStr === selectedDate;
            const isToday = dateStr === today;

            return (
              <button
                key={dateStr}
                disabled={!isPractice}
                onClick={() => { onSelectDate(dateStr); onClose(); }}
                className={`relative w-9 h-9 mx-auto rounded-full flex items-center justify-center transition-colors
                  ${isSelected ? 'bg-[#4a6b5a] text-white font-bold' : ''}
                  ${!isSelected && isPractice ? 'text-[#374151] font-semibold hover:bg-[#dce5de] cursor-pointer' : ''}
                  ${!isPractice ? 'text-gray-300 cursor-default' : ''}
                  ${isToday && !isSelected ? 'ring-1 ring-[#4a6b5a]' : ''}
                `}
              >
                {day}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};

const MatchResultsView = () => {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const dateParam = searchParams.get('date');

  const [session, setSession] = useState(null);
  const [pairings, setPairings] = useState([]);
  const [matches, setMatches] = useState([]);
  const [byeActivitiesData, setByeActivitiesData] = useState([]); // 抜け番活動データ
  const [currentMatchNumber, setCurrentMatchNumber] = useState(1);
  const [loading, setLoading] = useState(true);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState(null);

  const ACTIVITY_ICONS = {
    READING: BookOpen,
    SOLO_PICK: User,
    OBSERVING: Eye,
    ASSIST_OBSERVING: UsersRound,
    OTHER: MoreHorizontal,
  };

  // 日付選択関連の状態
  const [selectedDate, setSelectedDate] = useState(null);
  const [availableDates, setAvailableDates] = useState([]);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const initialFetchDone = useRef(false);
  const lastFetchedDate = useRef(null);
  const fetchedMonths = useRef(new Set()); // キャッシュ済み月を管理

  // 月の練習日を取得してavailableDatesにマージ
  const fetchDatesForMonth = useCallback(async (year, month) => {
    const key = `${year}-${month}`;
    if (fetchedMonths.current.has(key)) return;
    fetchedMonths.current.add(key);
    try {
      setCalendarLoading(true);
      const fromDate = `${year}-${String(month).padStart(2, '0')}-01`;
      const lastDay = new Date(year, month, 0).getDate();
      const toDate = `${year}-${String(month).padStart(2, '0')}-${lastDay}`;
      const response = await practiceAPI.getDates(fromDate);
      const dates = (response.data || []).filter(d => d <= toDate);
      setAvailableDates(prev => {
        const merged = new Set([...prev, ...dates]);
        return [...merged].sort((a, b) => b.localeCompare(a));
      });
    } catch (err) {
      console.error('月別日付取得エラー:', err);
    } finally {
      setCalendarLoading(false);
    }
  }, []);

  // 日付データの取得ヘルパー
  const fetchDataForDate = async (date) => {
    const [sessionResponse, pairingsResponse, matchesResponse, byeResponse] = await Promise.all([
      practiceAPI.getByDate(date).catch(() => null),
      pairingAPI.getByDate(date, { light: true }).catch(() => ({ data: [] })),
      matchAPI.getByDate(date).catch(() => ({ data: [] })),
      byeActivityAPI.getByDate(date).catch(() => ({ data: [] })),
    ]);
    return { sessionResponse, pairingsResponse, matchesResponse, byeResponse };
  };

  const applyData = (data) => {
    if (data.sessionResponse?.data) {
      setSession(data.sessionResponse.data);
      setPairings(data.pairingsResponse.data || []);
      setMatches(data.matchesResponse.data || []);
      setByeActivitiesData(data.byeResponse?.data || []);
    } else {
      setSession(null);
      setPairings([]);
      setMatches([]);
      setByeActivitiesData([]);
    }
  };

  // 初回：当月＋前月の練習日 + 今日のデータを並列取得
  useEffect(() => {
    const fetchInitial = async () => {
      try {
        const today = new Date().toISOString().split('T')[0];
        const targetDate = dateParam || today;
        const now = new Date();
        const thisYear = now.getFullYear();
        const thisMonth = now.getMonth() + 1;
        const prevMonth = thisMonth === 1 ? 12 : thisMonth - 1;
        const prevYear = thisMonth === 1 ? thisYear - 1 : thisYear;

        // 当月の1日からの練習日を取得（前月分も含まれる）
        const fromDate = `${prevYear}-${String(prevMonth).padStart(2, '0')}-01`;

        const [datesResponse, targetData] = await Promise.all([
          practiceAPI.getDates(fromDate),
          fetchDataForDate(targetDate),
        ]);

        const dates = datesResponse.data || [];
        setAvailableDates(dates);
        fetchedMonths.current.add(`${thisYear}-${thisMonth}`);
        fetchedMonths.current.add(`${prevYear}-${prevMonth}`);

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
  }, [sessionId, dateParam]);

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
    return <LoadingScreen />;
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

  // 対戦組み合わせに含まれていない参加者（抜けの選手）を算出
  const getByePlayersForMatch = (matchNumber) => {
    const matchParticipants = session?.matchParticipants?.[matchNumber] || [];
    if (matchParticipants.length === 0) return [];
    const matchPairings = getPairingsForMatch(matchNumber);
    const pairedNames = new Set();
    matchPairings.forEach(p => {
      pairedNames.add(p.player1Name);
      pairedNames.add(p.player2Name);
    });
    return matchParticipants
      .map(p => typeof p === 'string' ? p : p.name)
      .filter(name => !pairedNames.has(name));
  };

  const currentByePlayers = getByePlayersForMatch(currentMatchNumber);

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
        <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
          <div className="max-w-7xl mx-auto">
            <div className="flex items-center justify-between py-3">
              <button
                onClick={goToPreviousDate}
                disabled={!hasPreviousDate()}
                className={`p-2 rounded-full transition-colors ${
                  hasPreviousDate()
                    ? 'hover:bg-[#3d5a4c] text-white'
                    : 'text-white/30 cursor-not-allowed'
                }`}
              >
                <ChevronLeft className="w-6 h-6" />
              </button>

              <div className="relative">
                <button
                  onClick={() => setShowDatePicker(!showDatePicker)}
                  className="text-lg font-semibold text-white"
                >
                  {selectedDate && new Date(selectedDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                    weekday: 'short'
                  })}
                </button>

                {showDatePicker && (
                  <CalendarPicker
                    selectedDate={selectedDate}
                    availableDates={availableDates}
                    onSelectDate={setSelectedDate}
                    onClose={() => setShowDatePicker(false)}
                    onMonthChange={fetchDatesForMonth}
                    calendarLoading={calendarLoading}
                  />
                )}
              </div>

              <button
                onClick={goToNextDate}
                disabled={!hasNextDate()}
                className={`p-2 rounded-full transition-colors ${
                  hasNextDate()
                    ? 'hover:bg-[#3d5a4c] text-white'
                    : 'text-white/30 cursor-not-allowed'
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
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4">
        <div className="max-w-7xl mx-auto">
          {/* 日付選択 */}
          <div className="flex items-center justify-between py-3">
            <button
              onClick={goToPreviousDate}
              disabled={!hasPreviousDate()}
              className={`p-2 rounded-full transition-colors ${
                hasPreviousDate()
                  ? 'hover:bg-[#3d5a4c] text-white'
                  : 'text-white/30 cursor-not-allowed'
              }`}
            >
              <ChevronLeft className="w-6 h-6" />
            </button>

            <div className="relative">
              <button
                onClick={() => setShowDatePicker(!showDatePicker)}
                className="text-lg font-semibold text-white"
              >
                {selectedDate && new Date(selectedDate + 'T00:00:00').toLocaleDateString('ja-JP', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  weekday: 'short'
                })}
              </button>

              {showDatePicker && (
                <CalendarPicker
                  selectedDate={selectedDate}
                  availableDates={availableDates}
                  onSelectDate={setSelectedDate}
                  onClose={() => setShowDatePicker(false)}
                  onMonthChange={fetchDatesForMonth}
                  calendarLoading={calendarLoading}
                />
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
                      ? 'border-white text-white'
                      : 'border-transparent text-white/60 hover:text-white hover:border-white/50'
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

        {/* 抜け番の選手と活動 */}
        {(() => {
          const matchByeActivities = byeActivitiesData.filter(a => a.matchNumber === currentMatchNumber);
          // 活動記録のある抜け番 + 活動未記録の抜け番を統合
          const byeWithActivities = matchByeActivities.map(a => ({
            name: a.playerName,
            activityType: a.activityType,
            activityTypeDisplay: a.activityTypeDisplay,
            freeText: a.freeText,
          }));
          const activityPlayerNames = new Set(matchByeActivities.map(a => a.playerName));
          const byeWithoutActivities = currentByePlayers
            .filter(name => !activityPlayerNames.has(name))
            .map(name => ({ name, activityType: null, activityTypeDisplay: null, freeText: null }));
          const allBye = [...byeWithActivities, ...byeWithoutActivities];

          if (allBye.length === 0) return null;

          return (
            <div className="mt-4 bg-[#e5ebe7] rounded-lg p-3">
              <div className="space-y-1.5">
                {allBye.map((player, i) => (
                    <div key={i} className="flex items-center gap-2 text-sm">
                      <span className="font-medium text-[#374151]">{player.name}</span>
                      {player.activityTypeDisplay && (
                        <span className="text-xs px-2 py-0.5 bg-white rounded-full text-[#6b7280]">
                          {player.activityTypeDisplay}
                          {player.freeText && `（${player.freeText}）`}
                        </span>
                      )}
                    </div>
                ))}
              </div>
            </div>
          );
        })()}

        {/* 管理者用：編集ボタン */}
        {(isAdmin() || isSuperAdmin()) && session && (
          <button
            onClick={() => navigate(`/matches/bulk-input/${session.id}`)}
            className="w-full mt-6 py-3 px-4 bg-[#1A3654] text-white rounded-lg hover:bg-[#122740] flex items-center justify-center gap-2 font-semibold transition-colors"
          >
            <Edit className="w-5 h-5" />
            結果を編集・入力する
          </button>
        )}
      </div>

      {/* FAB: 試合結果を追加 */}
      <button
        onClick={() => navigate('/matches/new')}
        className="fixed right-5 bg-[#4a6b5a] text-white rounded-full shadow-lg hover:bg-[#3d5a4c] active:scale-95 transition-all flex items-center justify-center z-40 px-4 py-3 gap-2"
        style={{ bottom: 'calc(5rem + env(safe-area-inset-bottom, 0px))' }}
      >
        <Plus className="w-5 h-5" />
        <span className="text-sm font-medium">結果を入力</span>
      </button>
    </div>
  );
};

export default MatchResultsView;
