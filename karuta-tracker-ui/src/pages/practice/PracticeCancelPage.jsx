import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { practiceAPI, lotteryAPI } from '../../api';
import { ChevronLeft, ChevronRight, ArrowLeft, XCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import YearMonthPicker from '../../components/YearMonthPicker';

const CANCEL_REASONS = [
  { value: 'HEALTH', label: '体調不良' },
  { value: 'WORK_SCHOOL', label: '仕事・学業の都合' },
  { value: 'FAMILY', label: '家庭の事情' },
  { value: 'TRANSPORT', label: '交通機関の問題' },
  { value: 'OTHER', label: 'その他' },
];

const PracticeCancelPage = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [currentDate, setCurrentDate] = useState(new Date());
  const [sessions, setSessions] = useState([]);
  const [myStatuses, setMyStatuses] = useState({});
  const [loading, setLoading] = useState(true);
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedSession, setSelectedSession] = useState(null);
  const [selectedMatches, setSelectedMatches] = useState([]);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelReasonDetail, setCancelReasonDetail] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState('');
  const [showYearMonthPicker, setShowYearMonthPicker] = useState(false);

  const fetchingRef = useRef(false);

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth() + 1;

  // データ取得
  useEffect(() => {
    let cancelled = false;

    const fetchData = async () => {
      if (!currentPlayer?.id) return;
      if (fetchingRef.current) return;
      fetchingRef.current = true;

      try {
        setLoading(true);
        setError('');

        const [sessionsRes, statusRes] = await Promise.all([
          practiceAPI.getSessionSummaries(year, month),
          practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month).catch(() => ({ data: { participations: {} } })),
        ]);

        if (!cancelled) {
          setSessions(sessionsRes.data || []);
          setMyStatuses(statusRes.data?.participations || {});
        }
      } catch (err) {
        if (!cancelled) {
          setError('データの取得に失敗しました');
          console.error('Error fetching data:', err);
        }
      } finally {
        if (!cancelled) setLoading(false);
        fetchingRef.current = false;
      }
    };

    fetchData();
    return () => {
      cancelled = true;
      fetchingRef.current = false;
    };
  }, [currentPlayer?.id, year, month]);

  // 月変更時にリセット
  useEffect(() => {
    setSelectedDate(null);
    setSelectedSession(null);
    setSelectedMatches([]);
    setCancelReason('');
    setCancelReasonDetail('');
  }, [year, month]);

  // カレンダー生成
  const generateCalendar = () => {
    const m = currentDate.getMonth();
    const y = currentDate.getFullYear();
    const firstDay = new Date(y, m, 1);
    const lastDay = new Date(y, m + 1, 0);
    const daysInMonth = lastDay.getDate();
    const startDayOfWeek = firstDay.getDay();

    const calendar = [];
    let week = new Array(7).fill(null);

    for (let i = 0; i < startDayOfWeek; i++) {
      week[i] = null;
    }

    for (let day = 1; day <= daysInMonth; day++) {
      const dayOfWeek = (startDayOfWeek + day - 1) % 7;
      week[dayOfWeek] = day;

      if (dayOfWeek === 6 || day === daysInMonth) {
        calendar.push([...week]);
        week = new Array(7).fill(null);
      }
    }

    return calendar;
  };

  // 日付からセッションを取得
  const getSessionForDate = (day) => {
    if (!day) return null;
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    return sessions.find((s) => s.sessionDate === dateStr);
  };

  // そのセッションでWONの試合があるかチェック
  const getWonMatches = (session) => {
    if (!session) return [];
    const statuses = myStatuses[session.id] || [];
    return statuses.filter((s) => s.status === 'WON');
  };

  // 今日かどうか
  const isToday = (day) => {
    if (!day) return false;
    const today = new Date();
    return (
      day === today.getDate() &&
      currentDate.getMonth() === today.getMonth() &&
      currentDate.getFullYear() === today.getFullYear()
    );
  };

  // 過去の日付かどうか
  const isPastDate = (day) => {
    if (!day) return false;
    const date = new Date(year, month - 1, day);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return date < today;
  };

  // 場所名を省略
  const abbreviateLocation = (location) => {
    if (!location) return '';
    if (location.length <= 4) return location;
    if (location.includes('市民館')) return '市民館';
    if (location.includes('公民館')) return '公民館';
    if (location.includes('体育館')) return '体育館';
    return location.substring(0, 4);
  };

  // 日付クリック
  const handleDateClick = (day) => {
    if (!day) return;
    if (isPastDate(day)) return;

    const session = getSessionForDate(day);
    if (!session) return;

    const wonMatches = getWonMatches(session);
    if (wonMatches.length === 0) return;

    setSelectedDate(day);
    setSelectedSession(session);
    setSelectedMatches([]);
    setCancelReason('');
    setCancelReasonDetail('');
  };

  // 試合選択トグル
  const toggleMatchSelection = (matchNumber) => {
    setSelectedMatches((prev) =>
      prev.includes(matchNumber)
        ? prev.filter((m) => m !== matchNumber)
        : [...prev, matchNumber]
    );
  };

  // 日付フォーマット
  const formatDate = (day) => {
    const date = new Date(year, month - 1, day);
    return date.toLocaleDateString('ja-JP', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'short',
    });
  };

  // キャンセル実行
  const handleCancel = async () => {
    if (selectedMatches.length === 0) return;
    if (!cancelReason) return;

    const wonMatches = getWonMatches(selectedSession);
    const participantIds = selectedMatches
      .map((matchNum) => wonMatches.find((m) => m.matchNumber === matchNum)?.participantId)
      .filter(Boolean);

    if (participantIds.length === 0) return;

    const matchLabels = selectedMatches.sort((a, b) => a - b).map((m) => `第${m}試合`).join('、');
    const dateLabel = formatDate(selectedDate);

    if (!window.confirm(`${dateLabel}の${matchLabels}の参加をキャンセルします。よろしいですか？`)) {
      return;
    }

    setCancelling(true);
    setError('');
    try {
      await lotteryAPI.cancelMultiple(
        participantIds,
        cancelReason,
        cancelReason === 'OTHER' ? cancelReasonDetail : null
      );
      alert('キャンセル処理が完了しました');
      navigate('/practice');
    } catch (err) {
      console.error('Cancel error:', err);
      setError(err.response?.data?.message || 'キャンセルに失敗しました');
    } finally {
      setCancelling(false);
    }
  };

  // 月変更
  const changeMonth = (offset) => {
    const newDate = new Date(currentDate);
    newDate.setMonth(newDate.getMonth() + offset);
    setCurrentDate(newDate);
  };

  if (loading) return <LoadingScreen />;

  const calendar = generateCalendar();
  const monthStr = `${year}年${month}月`;
  const wonMatchesForSelected = selectedSession ? getWonMatches(selectedSession) : [];

  return (
    <div className="max-w-7xl mx-auto">
      {/* ナビゲーションバー */}
      <div className="bg-[#8b4513] border-b border-[#723a10] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button
            onClick={() => navigate('/practice')}
            className="p-2 hover:bg-[#723a10] rounded-full transition-colors"
          >
            <ArrowLeft className="w-6 h-6 text-white" />
          </button>
          <h1 className="text-lg font-semibold text-white">参加キャンセル</h1>
          <div className="w-10" />
        </div>
      </div>

      <div className="pt-20 pb-24 px-4">
        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
            {error}
          </div>
        )}

        {/* 月ナビゲーション */}
        <div className="flex items-center justify-between mb-4">
          <button onClick={() => changeMonth(-1)} className="p-2 hover:bg-gray-100 rounded-full">
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <div className="relative">
            <button
              onClick={() => setShowYearMonthPicker(!showYearMonthPicker)}
              className="text-lg font-semibold text-gray-800"
            >
              {monthStr}
            </button>
            {showYearMonthPicker && (
              <YearMonthPicker
                currentYear={year}
                currentMonth={month}
                onSelect={(y, m) => {
                  setCurrentDate(new Date(y, m - 1, 1));
                }}
                onClose={() => setShowYearMonthPicker(false)}
              />
            )}
          </div>
          <button onClick={() => changeMonth(1)} className="p-2 hover:bg-gray-100 rounded-full">
            <ChevronRight className="w-5 h-5 text-gray-600" />
          </button>
        </div>

        {/* 説明文 */}
        {!selectedDate && (
          <p className="text-center text-sm text-gray-600 mb-4">
            どの日の試合をキャンセルしますか？
          </p>
        )}

        {/* カレンダー（日付選択モード） */}
        {!selectedDate && (
          <div className="bg-[#f9f6f2] shadow-md rounded-lg overflow-hidden">
            <table className="w-full border-collapse table-fixed">
              <thead className="bg-[#e8d5c4]">
                <tr>
                  {['日', '月', '火', '水', '木', '金', '土'].map((d) => (
                    <th key={d} className="py-3 text-center text-sm font-medium border border-[#d4c0ae]">
                      {d}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {calendar.map((week, weekIdx) => (
                  <tr key={weekIdx}>
                    {week.map((day, dayIdx) => {
                      const session = getSessionForDate(day);
                      const today = isToday(day);
                      const past = isPastDate(day);
                      const wonMatches = session ? getWonMatches(session) : [];
                      const hasWon = wonMatches.length > 0;
                      const hasSession = !!session;

                      let cellBg = 'bg-[#f9f6f2]';
                      let cellBorder = 'border border-[#d4c0ae]';
                      let cursor = 'cursor-default';
                      let venueColor = 'text-gray-400';

                      if (hasWon && !past) {
                        cellBg = 'bg-[#fce4e4] hover:bg-[#f8d0d0]';
                        cellBorder = 'border-2 border-[#e8a0a0]';
                        cursor = 'cursor-pointer';
                        venueColor = 'text-[#8b4513]';
                      } else if (hasSession && !past) {
                        cellBg = 'bg-[#f9f6f2]';
                      }

                      if (past && day) {
                        cellBg = 'bg-gray-50';
                      }

                      return (
                        <td
                          key={dayIdx}
                          className={`px-1 py-2 ${cellBorder} ${cellBg} ${cursor} align-top h-20 relative`}
                          onClick={() => hasWon && !past && handleDateClick(day)}
                        >
                          {day && (
                            <div className="text-center flex flex-col items-center">
                              <div
                                className={`text-lg leading-tight ${
                                  today
                                    ? 'font-bold bg-[#8b4513] text-white w-8 h-8 rounded-full flex items-center justify-center mx-auto'
                                    : past
                                    ? 'text-gray-300'
                                    : ''
                                }`}
                              >
                                {day}
                              </div>
                              {session?.venueName && (
                                <div className={`mt-0.5 text-[10px] ${venueColor} leading-tight`}>
                                  {abbreviateLocation(session.venueName)}
                                </div>
                              )}
                              {hasWon && !past && (
                                <div className="mt-0.5 text-[9px] text-red-500 font-bold">
                                  {wonMatches.length}試合
                                </div>
                              )}
                            </div>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* 試合選択 + キャンセル理由（日付選択後） */}
        {selectedDate && selectedSession && (
          <div className="space-y-4">
            {/* 戻るボタン */}
            <button
              onClick={() => {
                setSelectedDate(null);
                setSelectedSession(null);
                setSelectedMatches([]);
                setCancelReason('');
                setCancelReasonDetail('');
              }}
              className="flex items-center gap-1 text-sm text-gray-600 hover:text-gray-800"
            >
              <ChevronLeft className="w-4 h-4" />
              カレンダーに戻る
            </button>

            {/* 日付表示 */}
            <div className="bg-[#f9f6f2] rounded-lg p-4 border border-[#e8d5c4]">
              <h3 className="text-lg font-bold text-gray-800">
                {formatDate(selectedDate)}
              </h3>
              {selectedSession.venueName && (
                <p className="text-sm text-gray-600 mt-1">{selectedSession.venueName}</p>
              )}
            </div>

            {/* 試合選択 */}
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <p className="text-sm font-medium text-gray-700 mb-3">
                何試合目の参加をキャンセルしますか？
              </p>
              <div className="space-y-2">
                {wonMatchesForSelected
                  .sort((a, b) => a.matchNumber - b.matchNumber)
                  .map((match) => {
                    const isSelected = selectedMatches.includes(match.matchNumber);
                    return (
                      <label
                        key={match.matchNumber}
                        className={`flex items-center gap-3 p-3 rounded-lg border-2 cursor-pointer transition-colors ${
                          isSelected
                            ? 'border-red-400 bg-red-50'
                            : 'border-gray-200 hover:border-gray-300 bg-white'
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleMatchSelection(match.matchNumber)}
                          className="w-5 h-5 rounded border-gray-300"
                          style={{ accentColor: '#8b4513' }}
                        />
                        <span className="text-sm font-medium text-gray-800">
                          第{match.matchNumber}試合
                        </span>
                        <span className="text-xs text-green-600 bg-green-100 px-2 py-0.5 rounded font-bold">
                          当選
                        </span>
                      </label>
                    );
                  })}
              </div>
            </div>

            {/* キャンセル理由 */}
            {selectedMatches.length > 0 && (
              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <p className="text-sm font-medium text-gray-700 mb-3">
                  キャンセル理由を選択してください <span className="text-red-500">*</span>
                </p>
                <div className="space-y-2">
                  {CANCEL_REASONS.map((reason) => (
                    <label
                      key={reason.value}
                      className={`flex items-center gap-3 p-3 rounded-lg border-2 cursor-pointer transition-colors ${
                        cancelReason === reason.value
                          ? 'border-[#8b4513] bg-[#fdf6ee]'
                          : 'border-gray-200 hover:border-gray-300 bg-white'
                      }`}
                    >
                      <input
                        type="radio"
                        name="cancelReason"
                        value={reason.value}
                        checked={cancelReason === reason.value}
                        onChange={(e) => setCancelReason(e.target.value)}
                        className="w-4 h-4"
                        style={{ accentColor: '#8b4513' }}
                      />
                      <span className="text-sm text-gray-800">{reason.label}</span>
                    </label>
                  ))}
                </div>

                {/* その他の場合の自由記述 */}
                {cancelReason === 'OTHER' && (
                  <textarea
                    value={cancelReasonDetail}
                    onChange={(e) => setCancelReasonDetail(e.target.value)}
                    placeholder="具体的な理由を入力してください"
                    className="mt-3 w-full p-3 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-[#8b4513] focus:border-transparent resize-none"
                    rows={3}
                  />
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 固定キャンセルボタン */}
      {selectedDate && selectedMatches.length > 0 && cancelReason && (
        <div
          className="fixed left-0 right-0 z-40 px-4 py-3 bg-white border-t border-gray-200 shadow-lg"
          style={{ bottom: 'calc(3.5rem + env(safe-area-inset-bottom, 0px))' }}
        >
          <button
            onClick={handleCancel}
            disabled={cancelling || (cancelReason === 'OTHER' && !cancelReasonDetail.trim())}
            className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
          >
            <XCircle className="w-5 h-5" />
            {cancelling ? 'キャンセル中...' : 'キャンセルする'}
          </button>
        </div>
      )}
    </div>
  );
};

export default PracticeCancelPage;
