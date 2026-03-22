import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { practiceAPI } from '../../api';
import { isSuperAdmin } from '../../utils/auth';
import { X, ChevronLeft, ChevronRight, CalendarCheck } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import MatchParticipantsEditModal from '../../components/MatchParticipantsEditModal';
import PlayerChip from '../../components/PlayerChip';
import { sortPlayersByRank } from '../../utils/playerSort';

// 年月グリッドピッカー（ドロップダウン型）
const YearMonthPicker = ({ currentYear, currentMonth, onSelect, onClose }) => {
  const pickerRef = useRef(null);
  const [viewYear, setViewYear] = useState(currentYear);

  // 外側クリックで閉じる
  useEffect(() => {
    const handleClick = (e) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target)) {
        onClose();
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [onClose]);

  return (
    <div
      ref={pickerRef}
      className="absolute top-full mt-2 left-1/2 -translate-x-1/2 bg-white border border-gray-200 rounded-lg shadow-lg z-[60] w-[240px] p-3"
    >
      {/* 年ナビゲーション */}
      <div className="flex items-center justify-between mb-2">
        <button onClick={() => setViewYear(viewYear - 1)} className="p-1 hover:bg-gray-100 rounded">
          <ChevronLeft className="w-4 h-4" />
        </button>
        <span className="font-semibold text-sm text-[#374151]">{viewYear}年</span>
        <button onClick={() => setViewYear(viewYear + 1)} className="p-1 hover:bg-gray-100 rounded">
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>

      {/* 月グリッド 3x4 */}
      <div className="grid grid-cols-3 gap-1">
        {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => {
          const isSelected = viewYear === currentYear && month === currentMonth;
          return (
            <button
              key={month}
              onClick={() => { onSelect(viewYear, month); onClose(); }}
              className={`py-2 text-sm rounded-lg transition-colors
                ${isSelected
                  ? 'bg-[#4a6b5a] text-white font-bold'
                  : 'text-[#374151] hover:bg-[#eef2ef]'
                }`}
            >
              {month}月
            </button>
          );
        })}
      </div>
    </div>
  );
};

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
        const [sessionsRes, participationsRes] = await Promise.all([
          practiceAPI.getSessionSummaries(year, month),
          practiceAPI.getPlayerParticipations(currentPlayer.id, year, month).catch(() => ({ data: {} })),
        ]);

        if (!cancelled) {
          setSessions(sessionsRes.data);
          setMyParticipations(participationsRes.data || {});
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
      const response = await practiceAPI.getPlayerParticipations(currentPlayer.id, year, month);
      setMyParticipations(response.data || {});
    } catch (err) {
      console.error('Error fetching my participations:', err);
      setMyParticipations({});
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

  // 自分の参加状況を取得（全試合/部分参加/未参加）
  const getMyParticipationStatus = (session) => {
    if (!session || !myParticipations[session.id]) return 'none';
    const myMatches = myParticipations[session.id];
    const totalMatches = session.totalMatches || 7;
    if (myMatches.length === totalMatches) return 'full';
    if (myMatches.length > 0) return 'partial';
    return 'none';
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

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-600">読み込み中...</div>
      </div>
    );
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
      <div className="bg-[#d4ddd7] border-b border-[#c5cec8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button
            onClick={() => changeMonth(-1)}
            className="p-2 hover:bg-[#c5cec8] rounded-full transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-[#374151]" />
          </button>
          <div className="relative">
            <button
              onClick={() => setShowYearMonthPicker(!showYearMonthPicker)}
              className="text-lg font-semibold text-[#374151]"
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
            className="p-2 hover:bg-[#c5cec8] rounded-full transition-colors"
          >
            <ChevronRight className="w-6 h-6 text-[#374151]" />
          </button>
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-20">
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

                  let bgColor = 'bg-[#f9f6f2]';
                  let borderColor = 'border-[#c5cec8]';
                  let cursor = 'cursor-default';
                  const isMyParticipation = participationStatus !== 'none';

                  if (hasSession) {
                    cursor = 'cursor-pointer';
                    if (isMyParticipation) {
                      bgColor = 'bg-[#dce5de] hover:bg-[#cdd8cf]';
                      borderColor = 'border-[#8a9e90]';
                    } else {
                      bgColor = 'bg-[#f9f6f2] hover:bg-[#eef2ef]';
                    }
                  }

                  return (
                    <td
                      key={dayIdx}
                      className={`px-1 py-2 border ${bgColor} ${borderColor} ${cursor} align-top h-20 relative`}
                      onClick={() => handleCellClick(day)}
                    >
                      {day && (
                        <div className="text-center flex flex-col items-center">
                          <div className={`text-lg leading-tight ${today ? 'font-bold bg-[#4a6b5a] text-white w-8 h-8 rounded-full flex items-center justify-center mx-auto' : ''}`}>
                            {day}
                          </div>
                          {session && session.venueName && (
                            <div className="mt-0.5 text-[10px] text-[#6b7280] leading-tight">
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
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={closeModal}
        >
          <div
            className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full mx-4 overflow-hidden flex flex-col"
          style={{ maxHeight: 'calc(100vh - 8rem - env(safe-area-inset-bottom, 0px))' }}
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
                    const count = participants.length;
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
                            {participants.length > 0 ? (
                              <div className="flex flex-wrap gap-1.5">
                                {sortPlayersByRank(participants).map((p, idx) => {
                                  const pName = typeof p === 'string' ? p : p.name;
                                  const isMyself = pName === currentPlayer?.name;
                                  return (
                                    <PlayerChip
                                      key={idx}
                                      name={pName}
                                      kyuRank={typeof p === 'string' ? undefined : p.kyuRank}
                                      className={`text-xs ${
                                        isMyself
                                          ? 'bg-[#4a6b5a] text-white font-medium'
                                          : 'text-[#374151] bg-white'
                                      }`}
                                    />
                                  );
                                })}
                              </div>
                            ) : (
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
      <button
        onClick={goToParticipation}
        className="fixed right-4 z-20 bg-[#4a6b5a] text-white pl-4 pr-5 py-3 rounded-full shadow-lg hover:bg-[#3d5a4c] transition-all hover:shadow-xl flex items-center gap-2"
        style={{ bottom: 'calc(4.5rem + env(safe-area-inset-bottom, 0px))' }}
      >
        <CalendarCheck className="w-5 h-5" />
        <span className="text-sm font-medium">参加登録</span>
      </button>

    </div>
    </div>
  );
};

export default PracticeList;
