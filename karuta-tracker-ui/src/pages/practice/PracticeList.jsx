import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { practiceAPI, lotteryAPI } from '../../api';
import { isSuperAdmin, isAdmin } from '../../utils/auth';
import { X, ChevronLeft, ChevronRight, CalendarCheck, RotateCcw, XCircle, Bell } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import MatchParticipantsEditModal from '../../components/MatchParticipantsEditModal';
import PlayerChip from '../../components/PlayerChip';
import YearMonthPicker from '../../components/YearMonthPicker';
import { sortPlayersByRank } from '../../utils/playerSort';
import LoadingScreen from '../../components/LoadingScreen';

const PracticeList = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedSession, setSelectedSession] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [expandedMatches, setExpandedMatches] = useState({}); // アコーディオンの開閉状態
  const [myParticipations, setMyParticipations] = useState({}); // 自分の参加状況 {sessionId: [matchNumbers]}
  const [myParticipationStatuses, setMyParticipationStatuses] = useState({}); // ステータス付き {sessionId: [{matchNumber, status, ...}]}
  const [showEditModal, setShowEditModal] = useState(false); // 試合別参加者編集モーダル
  const [editingMatchNumber, setEditingMatchNumber] = useState(null); // 編集中の試合番号
  const [refreshing, setRefreshing] = useState(false); // データ更新中
  const [showYearMonthPicker, setShowYearMonthPicker] = useState(false); // 年月ピッカー表示

  // StrictMode重複呼び出し防止用
  const fetchingRef = useRef(false);

  // データ取得を並列化して高速化
  useEffect(() => {
    let cancelled = false;

    const fetchAllData = async () => {
      if (!currentPlayer?.id) {
        setLoading(false);
        return;
      }

      // StrictModeで2回目の呼び出しをスキップ
      if (fetchingRef.current) return;
      fetchingRef.current = true;

      try {
        setLoading(true);
        setError('');

        const year = currentDate.getFullYear();
        const month = currentDate.getMonth() + 1;

        // 並列でデータ取得（軽量エンドポイント使用）
        const [sessionsRes, participationsRes, statusRes] = await Promise.all([
          practiceAPI.getSessionSummaries(year, month),
          practiceAPI.getPlayerParticipations(currentPlayer.id, year, month).catch(() => ({ data: {} })),
          practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month).catch(() => ({ data: { participations: {} } })),
        ]);

        if (!cancelled) {
          setSessions(sessionsRes.data);
          setMyParticipations(participationsRes.data || {});
          setMyParticipationStatuses(statusRes.data?.participations || {});
        }
      } catch (err) {
        if (!cancelled) {
          setError('練習記録の取得に失敗しました');
          console.error('Error fetching practice data:', err);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
        fetchingRef.current = false;
      }
    };

    fetchAllData();

    return () => {
      cancelled = true;
      fetchingRef.current = false;
    };
  }, [currentDate, currentPlayer?.id]);

  // 同期後などの再取得用関数（軽量エンドポイント使用）
  const fetchSessions = async () => {
    try {
      setLoading(true);
      const year = currentDate.getFullYear();
      const month = currentDate.getMonth() + 1;
      const response = await practiceAPI.getSessionSummaries(year, month);
      setSessions(response.data);
    } catch (err) {
      setError('練習記録の取得に失敗しました');
      console.error('Error fetching practice sessions:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchMyParticipations = async () => {
    if (!currentPlayer?.id) return;

    try {
      const year = currentDate.getFullYear();
      const month = currentDate.getMonth() + 1;
      const [response, statusRes] = await Promise.all([
        practiceAPI.getPlayerParticipations(currentPlayer.id, year, month),
        practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month).catch(() => ({ data: { participations: {} } })),
      ]);
      setMyParticipations(response.data || {});
      setMyParticipationStatuses(statusRes.data?.participations || {});
    } catch (err) {
      console.error('Error fetching my participations:', err);
      setMyParticipations({});
      setMyParticipationStatuses({});
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('この練習記録を削除してもよろしいですか?')) {
      return;
    }

    try {
      await practiceAPI.delete(id);
      setShowModal(false);
      setSelectedSession(null);
      fetchSessions();
    } catch (err) {
      setError('削除に失敗しました');
      console.error('Error deleting practice session:', err);
    }
  };

  // カレンダー生成用の関数
  const generateCalendar = () => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const daysInMonth = lastDay.getDate();
    const startDayOfWeek = firstDay.getDay();

    const calendar = [];
    let week = new Array(7).fill(null);

    // 前月の空白を埋める
    for (let i = 0; i < startDayOfWeek; i++) {
      week[i] = null;
    }

    // 当月の日付を埋める
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

  // 指定日の練習セッションを取得
  const getSessionForDate = (day) => {
    if (!day) return null;
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    return sessions.find((s) => s.sessionDate === dateStr);
  };

  // 自分の参加状況を取得（当選あり/全キャン待ち/未参加）
  const getMyParticipationStatus = (session) => {
    if (!session || !myParticipations[session.id]) return 'none';
    const myMatches = myParticipations[session.id];
    if (myMatches.length === 0) return 'none';

    // ステータス付き情報がある場合、当選/キャン待ちを判定
    const statuses = myParticipationStatuses[session.id];
    if (statuses && statuses.length > 0) {
      const hasWon = statuses.some(s =>
        s.status === 'WON' || s.status === 'OFFERED' || s.status === 'PENDING'
      );
      if (hasWon) return 'confirmed';
      const hasWaitlisted = statuses.some(s => s.status === 'WAITLISTED');
      if (hasWaitlisted) return 'waitlisted';
    }

    // ステータス情報がなければ従来通り参加扱い
    return 'confirmed';
  };

  // 今日かどうか判定
  const isToday = (day) => {
    if (!day) return false;
    const today = new Date();
    return (
      day === today.getDate() &&
      currentDate.getMonth() === today.getMonth() &&
      currentDate.getFullYear() === today.getFullYear()
    );
  };

  // 場所名を省略（4文字まではそのまま表示）
  const abbreviateLocation = (location) => {
    if (!location) return '';
    if (location.length <= 4) return location;
    if (location.includes('市民館')) return '市民館';
    if (location.includes('公民館')) return '公民館';
    if (location.includes('体育館')) return '体育館';
    return location.substring(0, 4);
  };

  // 月を変更
  const changeMonth = (offset) => {
    const newDate = new Date(currentDate);
    newDate.setMonth(newDate.getMonth() + offset);
    setCurrentDate(newDate);
  };

  // セルクリック
  const handleCellClick = async (day) => {
    if (!day) return;
    const session = getSessionForDate(day);
    if (session) {
      try {
        // 個別に詳細取得（試合別参加者を含むエンリッチメント済みデータ）
        const response = await practiceAPI.getById(session.id);
        setSelectedSession(response.data);
        // 全試合をデフォルトで開いた状態にする
        if (response.data.matchParticipants) {
          const allExpanded = {};
          Object.keys(response.data.matchParticipants).forEach(matchNum => {
            allExpanded[matchNum] = true;
          });
          setExpandedMatches(allExpanded);
        }
        setShowModal(true);
      } catch (err) {
        console.error('Error fetching session details:', err);
        // エラー時は元のデータで表示
        setSelectedSession(session);
        setExpandedMatches({});
        setShowModal(true);
      }
    }
  };

  // モーダルを閉じる
  const closeModal = () => {
    setShowModal(false);
    setSelectedSession(null);
    setExpandedMatches({}); // アコーディオンの状態をリセット
  };

  // 試合別参加者編集モーダルを開く
  const handleEditMatchParticipants = (matchNumber) => {
    setEditingMatchNumber(matchNumber);
    setShowEditModal(true);
  };

  // 試合別参加者編集モーダルを閉じる
  const handleCloseEditModal = () => {
    setShowEditModal(false);
    setEditingMatchNumber(null);
  };

  // 試合別参加者保存後の処理
  const handleSaveMatchParticipants = async () => {
    // モーダルを閉じる
    handleCloseEditModal();
    // セッション詳細を再取得して更新
    if (selectedSession) {
      try {
        const response = await practiceAPI.getById(selectedSession.id);
        setSelectedSession(response.data);
      } catch (err) {
        console.error('Error refreshing session:', err);
      }
    }
  };

  // アコーディオンのトグル
  const toggleMatch = (matchNum) => {
    setExpandedMatches((prev) => ({
      ...prev,
      [matchNum]: prev[matchNum] === false,
    }));
  };

  const formatDateForModal = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ja-JP', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'short',
    });
  };

  // データ更新（DBの最新状態を再取得）
  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await Promise.all([fetchSessions(), fetchMyParticipations()]);
    } finally {
      setRefreshing(false);
    }
  };

  // 過去の日付かどうか判定
  const isPastDate = (dateString) => {
    const sessionDate = new Date(dateString);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    sessionDate.setHours(0, 0, 0, 0);
    return sessionDate < today;
  };

  // 再抽選実行
  const handleReLottery = async (sessionId) => {
    if (!window.confirm('このセッションの再抽選を実行しますか？\n※繰り上げ当選者は維持されます')) return;
    try {
      await lotteryAPI.reExecute(sessionId);
      // セッション詳細を再取得
      const response = await practiceAPI.getById(sessionId);
      setSelectedSession(response.data);
    } catch (err) {
      console.error('Re-lottery error:', err);
      alert(err.response?.data?.message || '再抽選に失敗しました');
    }
  };

  // 抽選結果通知を送信
  const handleNotifyResults = async () => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth() + 1;
    try {
      // 送信済みチェック
      const statusRes = await lotteryAPI.notifyStatus(year, month);
      if (statusRes.data.sent) {
        if (!window.confirm(`${year}年${month}月の抽選結果通知は既に送信済みです（${statusRes.data.sentCount}件）。再送信しますか？`)) {
          return;
        }
      }
      const result = await lotteryAPI.notifyResults(year, month);
      alert(`通知送信完了\nアプリ内通知: ${result.data.inAppCount}件\nLINE送信: ${result.data.lineSent}件`);
    } catch (err) {
      console.error('Notify error:', err);
      alert(err.response?.data?.message || '通知送信に失敗しました');
    }
  };

  // 参加者のステータスバッジ
  const getStatusBadge = (status) => {
    switch (status) {
      case 'WON': return null; // 当選者はバッジなし（通常表示）
      case 'WAITLISTED': return <span className="text-[9px] px-1 py-0.5 bg-yellow-100 text-yellow-800 rounded font-bold">待</span>;
      case 'OFFERED': return <span className="text-[9px] px-1 py-0.5 bg-blue-100 text-blue-800 rounded font-bold">応答待</span>;
      case 'CANCELLED': return <span className="text-[9px] px-1 py-0.5 bg-red-100 text-red-400 rounded font-bold">取消</span>;
      default: return null;
    }
  };

  if (loading) {
    return <LoadingScreen />;
  }

  const calendar = generateCalendar();
  const monthStr = `${currentDate.getFullYear()}年${currentDate.getMonth() + 1}月`;

  const goToParticipation = () => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth() + 1;
    navigate(`/practice/participation?year=${year}&month=${month}`);
  };

  return (
    <div className="max-w-7xl mx-auto">
      {/* ナビゲーションバー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button
            onClick={() => changeMonth(-1)}
            className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-white" />
          </button>
          <div className="relative">
            <button
              onClick={() => setShowYearMonthPicker(!showYearMonthPicker)}
              className="text-lg font-semibold text-white"
            >
              {monthStr}
            </button>
            {showYearMonthPicker && (
              <YearMonthPicker
                currentYear={currentDate.getFullYear()}
                currentMonth={currentDate.getMonth() + 1}
                onSelect={(year, month) => {
                  const newDate = new Date(year, month - 1, 1);
                  setCurrentDate(newDate);
                }}
                onClose={() => setShowYearMonthPicker(false)}
              />
            )}
          </div>
          <button
            onClick={() => changeMonth(1)}
            className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
          >
            <ChevronRight className="w-6 h-6 text-white" />
          </button>
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-20">
      {/* 管理者用: 抽選結果通知ボタン */}
      {isAdmin() && (
        <div className="mb-3 flex justify-end">
          <button
            onClick={handleNotifyResults}
            className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-[#4a6b5a] border border-[#4a6b5a] rounded-lg hover:bg-[#4a6b5a] hover:text-white transition-colors"
          >
            <Bell className="w-4 h-4" />
            抽選結果を通知
          </button>
        </div>
      )}
      {error && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {error}
        </div>
      )}


      {/* カレンダー */}
      <div className="bg-[#f9f6f2] shadow-md rounded-lg overflow-hidden">
        <table className="w-full border-collapse table-fixed">
          <thead className="bg-[#d4ddd7]">
            <tr>
              {['日', '月', '火', '水', '木', '金', '土'].map((day) => (
                <th key={day} className="py-3 text-center text-sm font-medium border">
                  {day}
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
                  const hasSession = !!session;
                  const participationStatus = session ? getMyParticipationStatus(session) : 'none';

                  const cursor = hasSession ? 'cursor-pointer' : 'cursor-default';
                  let venueTextColor = 'text-[#6b7280]';
                  let cellBorder = 'border border-[#c5cec8]';
                  let cellBg = 'bg-[#f9f6f2]';

                  if (participationStatus === 'confirmed') {
                    cellBorder = 'border-2 border-[#a3c4ad]';
                    cellBg = 'bg-[#dce5de] hover:bg-[#cdd8cf]';
                    venueTextColor = 'text-[#4a6b5a]';
                  } else if (participationStatus === 'waitlisted') {
                    cellBorder = 'border-2 border-[#e8d48b]';
                    cellBg = 'bg-[#fef9ed] hover:bg-[#fdf3d7]';
                    venueTextColor = 'text-[#b8860b]';
                  } else if (hasSession) {
                    cellBg = 'bg-[#f9f6f2] hover:bg-[#eef2ef]';
                  }

                  return (
                    <td
                      key={dayIdx}
                      className={`px-1 py-2 ${cellBorder} ${cellBg} ${cursor} align-top h-20 relative`}
                      onClick={() => handleCellClick(day)}
                    >
                      {day && (
                        <div className="text-center flex flex-col items-center">
                          <div className={`text-lg leading-tight ${today ? 'font-bold bg-[#4a6b5a] text-white w-8 h-8 rounded-full flex items-center justify-center mx-auto' : ''}`}>
                            {day}
                          </div>
                          {session && session.venueName && (
                            <div className={`mt-0.5 text-[10px] ${venueTextColor} leading-tight`}>
                              {abbreviateLocation(session.venueName)}
                            </div>
                          )}
                          {hasSession && !session?.venueName && (
                            <div className="mt-1 w-1.5 h-1.5 rounded-full bg-[#4a6b5a]" />
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

      {/* モーダル */}
      {showModal && selectedSession && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 pb-16"
          onClick={closeModal}
        >
          <div
            className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full mx-4 overflow-hidden flex flex-col"
          style={{ maxHeight: 'calc(100vh - 12rem - env(safe-area-inset-bottom, 0px))' }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* ヘッダー */}
            <div className="px-6 pt-5 pb-4 flex justify-between items-start">
              <div>
                <h3 className="text-lg font-bold text-[#374151]">
                  {formatDateForModal(selectedSession.sessionDate)}
                </h3>
                {selectedSession.venueName && (
                  <p className="text-sm text-[#6b7280] mt-0.5">{selectedSession.venueName}</p>
                )}
              </div>
              <button onClick={closeModal} className="text-[#6b7280] hover:text-[#374151] -mt-1">
                <X size={20} />
              </button>
            </div>

            {/* 試合リスト */}
            <div className="divide-y divide-[#d4ddd7] overflow-y-auto flex-1">
              {selectedSession.matchParticipants &&
              Object.keys(selectedSession.matchParticipants).length > 0 ? (
                Object.entries(selectedSession.matchParticipants)
                  .sort(([a], [b]) => parseInt(a) - parseInt(b))
                  .map(([matchNum, participants]) => {
                    const isExpanded = expandedMatches[matchNum] !== false;
                    const count = participants.filter(p => {
                      if (typeof p === 'string') return true;
                      return p.status !== 'WAITLISTED' && p.status !== 'CANCELLED' && p.status !== 'DECLINED' && p.status !== 'WAITLIST_DECLINED';
                    }).length;
                    const myMatchNumbers = myParticipations[selectedSession.id] || [];
                    const isMyMatch = myMatchNumbers.includes(parseInt(matchNum));

                    const schedule = selectedSession.venueSchedules?.find(
                      s => s.matchNumber === parseInt(matchNum)
                    );
                    const timeRange = schedule
                      ? `${schedule.startTime?.substring(0, 5)}-${schedule.endTime?.substring(0, 5)}`
                      : '';

                    return (
                      <div key={matchNum}>
                        <div className={`px-6 py-3 flex items-center justify-between ${isMyMatch ? 'bg-[#eef2ef]' : ''}`}>
                          <button
                            onClick={() => toggleMatch(matchNum)}
                            className="flex-1 text-left flex items-center gap-3"
                          >
                            <span className="text-sm font-semibold text-[#374151] w-16 flex-shrink-0">
                              第{matchNum}試合
                            </span>
                            {timeRange && (
                              <span className="text-xs text-[#6b7280]">{timeRange}</span>
                            )}
                            <span className="text-xs text-[#6b7280] ml-auto mr-2">{count}名</span>
                            <ChevronRight
                              size={14}
                              className={`text-[#6b7280] transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                            />
                          </button>
                          {isSuperAdmin(currentPlayer) && (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleEditMatchParticipants(parseInt(matchNum));
                              }}
                              className="ml-2 px-2 py-1 text-xs text-[#4a6b5a] border border-[#4a6b5a] rounded hover:bg-[#4a6b5a] hover:text-white transition-colors"
                            >
                              編集
                            </button>
                          )}
                        </div>
                        {isExpanded && (
                          <div className={`px-6 pb-3 ${isMyMatch ? 'bg-[#eef2ef]' : ''}`}>
                            {participants.length > 0 ? (() => {
                              const wonList = participants.filter(p => {
                                const s = typeof p === 'string' ? null : p.status;
                                return s !== 'WAITLISTED' && s !== 'CANCELLED' && s !== 'DECLINED' && s !== 'WAITLIST_DECLINED';
                              });
                              const waitList = participants.filter(p => {
                                const s = typeof p === 'string' ? null : p.status;
                                return s === 'WAITLISTED';
                              });
                              const cancelledList = participants.filter(p => {
                                const s = typeof p === 'string' ? null : p.status;
                                return s === 'CANCELLED';
                              });
                              return (
                                <div className="space-y-2">
                                  <div className="flex flex-wrap gap-1.5">
                                    {sortPlayersByRank(wonList).map((p, idx) => {
                                      const pName = typeof p === 'string' ? p : p.name;
                                      const isMyself = pName === currentPlayer?.name;
                                      const pStatus = typeof p === 'string' ? null : p.status;
                                      return (
                                        <span key={idx} className="inline-flex items-center gap-0.5">
                                          <PlayerChip
                                            name={pName}
                                            kyuRank={typeof p === 'string' ? undefined : p.kyuRank}
                                            className={`text-xs ${
                                              isMyself
                                                ? 'bg-[#4a6b5a] text-white font-medium'
                                                : 'text-[#374151] bg-white'
                                            }`}
                                          />
                                          {getStatusBadge(pStatus)}
                                        </span>
                                      );
                                    })}
                                  </div>
                                  {waitList.length > 0 && (
                                    <div>
                                      <div className="text-[10px] text-[#9ca3af] mb-1">キャンセル待ち</div>
                                      <div className="flex flex-wrap gap-1">
                                        {sortPlayersByRank(waitList).map((p, idx) => {
                                          const pName = typeof p === 'string' ? p : p.name;
                                          const isMyself = pName === currentPlayer?.name;
                                          return (
                                            <PlayerChip
                                              key={idx}
                                              name={pName}
                                              kyuRank={typeof p === 'string' ? undefined : p.kyuRank}
                                              className={`!px-1.5 !py-0.5 text-[10px] ${
                                                isMyself
                                                  ? 'bg-[#4a6b5a] text-white font-medium'
                                                  : 'text-[#6b7280] bg-yellow-50 border-yellow-200'
                                              }`}
                                            />
                                          );
                                        })}
                                      </div>
                                    </div>
                                  )}
                                  {cancelledList.length > 0 && (
                                    <div>
                                      <div className="text-[10px] text-[#9ca3af] mb-1">キャンセル済み</div>
                                      <div className="flex flex-wrap gap-1">
                                        {sortPlayersByRank(cancelledList).map((p, idx) => {
                                          const pName = typeof p === 'string' ? p : p.name;
                                          const isMyself = pName === currentPlayer?.name;
                                          return (
                                            <PlayerChip
                                              key={idx}
                                              name={pName}
                                              kyuRank={typeof p === 'string' ? undefined : p.kyuRank}
                                              className={`!px-1.5 !py-0.5 text-[10px] ${
                                                isMyself
                                                  ? 'bg-red-200 text-red-800 font-medium'
                                                  : 'text-[#6b7280] bg-red-50 border-red-200'
                                              }`}
                                            />
                                          );
                                        })}
                                      </div>
                                    </div>
                                  )}
                                </div>
                              );
                            })() : (
                              <div className="text-xs text-[#6b7280]">参加者なし</div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })
              ) : (
                <div className="px-6 py-4 text-sm text-[#6b7280]">試合データなし</div>
              )}
            </div>

            {selectedSession.remarks && (
              <div className="px-6 py-3 border-t border-[#d4ddd7]">
                <p className="text-sm text-[#6b7280]">{selectedSession.remarks}</p>
              </div>
            )}

            {/* ボタン */}
            <div className="px-6 py-4 border-t border-[#d4ddd7] flex items-center gap-2">
              {isPastDate(selectedSession.sessionDate) ? (
                <button
                  onClick={() => navigate(`/matches/results/${selectedSession.id}?date=${selectedSession.sessionDate}`)}
                  className="flex-1 py-2 text-sm font-medium text-[#4a6b5a] border border-[#4a6b5a] rounded-lg hover:bg-[#4a6b5a] hover:text-white transition-colors whitespace-nowrap"
                >
                  試合結果
                </button>
              ) : (
                <button
                  onClick={goToParticipation}
                  className="flex-1 py-2 text-sm font-medium text-[#4a6b5a] border border-[#4a6b5a] rounded-lg hover:bg-[#4a6b5a] hover:text-white transition-colors whitespace-nowrap"
                >
                  参加登録
                </button>
              )}
              {isAdmin() && !isPastDate(selectedSession.sessionDate) && (
                <button
                  onClick={() => handleReLottery(selectedSession.id)}
                  className="flex-1 py-2 text-sm font-medium text-orange-600 border border-orange-400 rounded-lg hover:bg-orange-600 hover:text-white transition-colors whitespace-nowrap flex items-center justify-center gap-1"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  再抽選
                </button>
              )}
              {isSuperAdmin() && (
                <button
                  onClick={() => handleDelete(selectedSession.id)}
                  className="flex-1 py-2 text-sm font-medium text-red-600 border border-red-400 rounded-lg hover:bg-red-600 hover:text-white transition-colors whitespace-nowrap"
                >
                  削除
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 試合別参加者編集モーダル */}
      {showEditModal && selectedSession && editingMatchNumber && (
        <MatchParticipantsEditModal
          session={selectedSession}
          matchNumber={editingMatchNumber}
          onClose={handleCloseEditModal}
          onSave={handleSaveMatchParticipants}
        />
      )}
      {/* フローティングアクションボタン (FAB) */}
      <div className="fixed left-4 z-20" style={{ bottom: 'calc(4.5rem + env(safe-area-inset-bottom, 0px))' }}>
        <button
          onClick={() => navigate('/practice/cancel')}
          className="bg-white text-red-600 border border-red-300 pl-4 pr-5 py-3 rounded-full shadow-lg hover:bg-red-50 transition-all hover:shadow-xl flex items-center gap-2"
        >
          <XCircle className="w-5 h-5" />
          <span className="text-sm font-medium">登録キャンセル</span>
        </button>
      </div>
      <div className="fixed right-4 z-20" style={{ bottom: 'calc(4.5rem + env(safe-area-inset-bottom, 0px))' }}>
        <button
          onClick={goToParticipation}
          className="bg-[#4a6b5a] text-white pl-4 pr-5 py-3 rounded-full shadow-lg hover:bg-[#3d5a4c] transition-all hover:shadow-xl flex items-center gap-2"
        >
          <CalendarCheck className="w-5 h-5" />
          <span className="text-sm font-medium">参加登録</span>
        </button>
      </div>

    </div>
    </div>
  );
};

export default PracticeList;
