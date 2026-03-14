import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { practiceAPI } from '../../api';
import { isSuperAdmin } from '../../utils/auth';
import { X, RefreshCw, ChevronLeft, ChevronRight, CalendarCheck } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import MatchParticipantsEditModal from '../../components/MatchParticipantsEditModal';

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

  // データ取得を行う関数をメモ化
  useEffect(() => {
    fetchSessions();
    fetchMyParticipations();
  }, [currentDate, currentPlayer?.id]); // currentPlayer.idまたは月が変わったときに再取得

  const fetchSessions = async () => {
    try {
      setLoading(true);
      const response = await practiceAPI.getAll();
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

  // 場所名を省略
  const abbreviateLocation = (location) => {
    if (!location) return '';
    if (location.includes('市民館')) return '市民';
    if (location.includes('公民館')) return '公民';
    if (location.includes('体育館')) return '体育';
    return location.substring(0, 3);
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
        setShowModal(true);
      } catch (err) {
        console.error('Error fetching session details:', err);
        // エラー時は元のデータで表示
        setSelectedSession(session);
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
      [matchNum]: !prev[matchNum],
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
      <div className="bg-[#e2d9d0] border-b border-[#d0c5b8] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button
            onClick={() => changeMonth(-1)}
            className="p-2 hover:bg-[#d0c5b8] rounded-full transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-[#5f3a2d]" />
          </button>
          <h1 className="text-lg font-semibold text-[#5f3a2d]">
            {monthStr}
          </h1>
          <button
            onClick={() => changeMonth(1)}
            className="p-2 hover:bg-[#d0c5b8] rounded-full transition-colors"
          >
            <ChevronRight className="w-6 h-6 text-[#5f3a2d]" />
          </button>
        </div>
      </div>

      {/* コンテンツ（上部パディング追加） */}
      <div className="pt-20">
      <div className="flex justify-end items-center mb-6">
        <div className="flex gap-3">
          <button
            onClick={() => { fetchSessions(); fetchMyParticipations(); }}
            className="flex items-center gap-1 px-4 py-2 bg-[#e2d9d0] rounded-lg hover:bg-[#d0c5b8] transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            更新
          </button>
          {isSuperAdmin() && (
            <button
              onClick={() => navigate('/practice/new')}
              className="px-4 py-2 bg-[#82655a] text-white rounded-lg hover:bg-[#6b5048] transition-colors"
            >
              + 新規登録
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {error}
        </div>
      )}


      {/* カレンダー */}
      <div className="bg-[#f9f6f2] shadow-md rounded-lg overflow-hidden">
        <table className="w-full border-collapse table-fixed">
          <thead className="bg-[#e2d9d0]">
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
                  let borderColor = 'border-[#d0c5b8]';
                  let cursor = 'cursor-default';
                  const isMyParticipation = participationStatus !== 'none';

                  if (hasSession) {
                    cursor = 'cursor-pointer';
                    if (isMyParticipation) {
                      bgColor = 'bg-[#e8e1d8] hover:bg-[#ddd4cb]';
                      borderColor = 'border-[#a5927f]';
                    } else {
                      bgColor = 'bg-[#f9f6f2] hover:bg-[#f0ebe3]';
                    }
                  }

                  return (
                    <td
                      key={dayIdx}
                      className={`px-1 py-3 border ${bgColor} ${borderColor} ${cursor} align-top h-20 relative`}
                      onClick={() => handleCellClick(day)}
                    >
                      {day && (
                        <div className="text-center">
                          <div className={`text-lg ${today ? 'font-bold' : ''}`}>{day}</div>
                          {session && session.venueName && (
                            <div className="mt-1 text-xs text-gray-700">
                              {abbreviateLocation(session.venueName)}
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

      {/* モーダル */}
      {showModal && selectedSession && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={closeModal}
        >
          <div
            className="bg-[#f9f6f2] rounded-2xl shadow-xl max-w-md w-full mx-4 overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            {/* ヘッダー */}
            <div className="px-6 pt-5 pb-4 flex justify-between items-start">
              <div>
                <h3 className="text-lg font-bold text-[#5f3a2d]">
                  {formatDateForModal(selectedSession.sessionDate)}
                </h3>
                {selectedSession.venueName && (
                  <p className="text-sm text-[#8a7568] mt-0.5">{selectedSession.venueName}</p>
                )}
              </div>
              <button onClick={closeModal} className="text-[#8a7568] hover:text-[#5f3a2d] -mt-1">
                <X size={20} />
              </button>
            </div>

            {/* 試合リスト */}
            <div className="divide-y divide-[#e2d9d0]">
              {selectedSession.matchParticipants &&
              Object.keys(selectedSession.matchParticipants).length > 0 ? (
                Object.entries(selectedSession.matchParticipants)
                  .sort(([a], [b]) => parseInt(a) - parseInt(b))
                  .map(([matchNum, participants]) => {
                    const isExpanded = expandedMatches[matchNum];
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
                        <div className={`px-6 py-3 flex items-center justify-between ${isMyMatch ? 'bg-[#f0ebe3]' : ''}`}>
                          <button
                            onClick={() => toggleMatch(matchNum)}
                            className="flex-1 text-left flex items-center gap-3"
                          >
                            <span className="text-sm font-semibold text-[#5f3a2d] w-16 flex-shrink-0">
                              第{matchNum}試合
                            </span>
                            {timeRange && (
                              <span className="text-xs text-[#8a7568]">{timeRange}</span>
                            )}
                            <span className="text-xs text-[#8a7568] ml-auto mr-2">{count}名</span>
                            <ChevronRight
                              size={14}
                              className={`text-[#8a7568] transition-transform ${isExpanded ? 'rotate-90' : ''}`}
                            />
                          </button>
                          {isSuperAdmin(currentPlayer) && (
                            <button
                              onClick={(e) => {
                                e.stopPropagation();
                                handleEditMatchParticipants(parseInt(matchNum));
                              }}
                              className="ml-2 px-2 py-1 text-xs text-[#82655a] border border-[#82655a] rounded hover:bg-[#82655a] hover:text-white transition-colors"
                            >
                              編集
                            </button>
                          )}
                        </div>
                        {isExpanded && (
                          <div className={`px-6 pb-3 ${isMyMatch ? 'bg-[#f0ebe3]' : ''}`}>
                            {participants.length > 0 ? (
                              <div className="flex flex-wrap gap-1.5 pl-16">
                                {participants.map((name, idx) => (
                                  <span key={idx} className="text-xs text-[#5f3a2d] bg-[#e2d9d0] px-2 py-0.5 rounded-full">
                                    {name}
                                  </span>
                                ))}
                              </div>
                            ) : (
                              <div className="text-xs text-[#8a7568] pl-16">参加者なし</div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })
              ) : (
                <div className="px-6 py-4 text-sm text-[#8a7568]">試合データなし</div>
              )}
            </div>

            {selectedSession.remarks && (
              <div className="px-6 py-3 border-t border-[#e2d9d0]">
                <p className="text-sm text-[#8a7568]">{selectedSession.remarks}</p>
              </div>
            )}

            {/* ボタン */}
            <div className="px-6 py-4 border-t border-[#e2d9d0] flex items-center gap-2">
              {isPastDate(selectedSession.sessionDate) ? (
                <button
                  onClick={() => navigate(`/matches/results/${selectedSession.id}`)}
                  className="flex-1 py-2 text-sm font-medium text-[#82655a] border border-[#82655a] rounded-lg hover:bg-[#82655a] hover:text-white transition-colors whitespace-nowrap"
                >
                  試合結果
                </button>
              ) : (
                <button
                  onClick={goToParticipation}
                  className="flex-1 py-2 text-sm font-medium text-[#82655a] border border-[#82655a] rounded-lg hover:bg-[#82655a] hover:text-white transition-colors whitespace-nowrap"
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
        className="fixed bottom-20 right-4 z-20 bg-[#82655a] text-white p-4 rounded-full shadow-lg hover:bg-[#6e5549] transition-all hover:shadow-xl"
      >
        <CalendarCheck className="w-6 h-6" />
      </button>
    </div>
    </div>
  );
};

export default PracticeList;
