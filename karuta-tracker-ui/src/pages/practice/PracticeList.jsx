import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { practiceAPI } from '../../api';
import { isSuperAdmin } from '../../utils/auth';
import { X, RefreshCw } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

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
  const handleCellClick = (day) => {
    if (!day) return;
    const session = getSessionForDate(day);
    if (session) {
      setSelectedSession(session);
      setShowModal(true);
    }
  };

  // モーダルを閉じる
  const closeModal = () => {
    setShowModal(false);
    setSelectedSession(null);
    setExpandedMatches({}); // アコーディオンの状態をリセット
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
      <div className="flex justify-end items-center mb-6">
        <div className="flex gap-3">
          <button
            onClick={() => { fetchSessions(); fetchMyParticipations(); }}
            className="flex items-center gap-1 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            更新
          </button>
          {isSuperAdmin() && (
            <button
              onClick={() => navigate('/practice/new')}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
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

      {/* 月切り替え */}
      <div className="flex justify-center items-center mb-6 gap-4">
        <button
          onClick={() => changeMonth(-1)}
          className="px-4 py-2 bg-gray-200 hover:bg-gray-300 rounded-lg transition-colors"
        >
          ← 前月
        </button>
        <h2 className="text-2xl font-bold text-gray-900">{monthStr}</h2>
        <button
          onClick={() => changeMonth(1)}
          className="px-4 py-2 bg-gray-200 hover:bg-gray-300 rounded-lg transition-colors"
        >
          次月 →
        </button>
      </div>

      {/* 参加登録ボタン */}
      <div className="flex justify-center mb-6">
        <button
          onClick={goToParticipation}
          className="px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors font-medium"
        >
          📝 今月の参加登録を変更
        </button>
      </div>

      {/* カレンダー */}
      <div className="bg-white shadow-md rounded-lg overflow-hidden">
        <table className="min-w-full border-collapse">
          <thead className="bg-gray-100">
            <tr>
              {['日', '月', '火', '水', '木', '金', '土'].map((day) => (
                <th key={day} className="px-2 py-3 text-center text-sm font-medium text-gray-700 border">
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

                  let bgColor = 'bg-white';
                  let borderColor = 'border-gray-200';
                  let cursor = 'cursor-default';

                  if (today && hasSession) {
                    bgColor = 'bg-green-50';
                    borderColor = 'border-green-400';
                    cursor = 'cursor-pointer';
                  } else if (today) {
                    bgColor = 'bg-yellow-50';
                    borderColor = 'border-orange-300';
                  } else if (hasSession) {
                    bgColor = 'bg-blue-50 hover:bg-blue-100';
                    borderColor = 'border-blue-200';
                    cursor = 'cursor-pointer';
                  }

                  return (
                    <td
                      key={dayIdx}
                      className={`px-2 py-4 border ${bgColor} ${borderColor} ${cursor} align-top h-24 relative`}
                      onClick={() => handleCellClick(day)}
                    >
                      {day && (
                        <div className="text-center">
                          <div className="flex items-start justify-center gap-1">
                            <div className={`text-lg ${today ? 'font-bold' : ''}`}>{day}</div>
                            {participationStatus === 'full' && (
                              <span className="text-green-500 text-sm">●</span>
                            )}
                            {participationStatus === 'partial' && (
                              <span className="text-yellow-500 text-sm">◐</span>
                            )}
                          </div>
                          {session && session.venueName && (
                            <div className="mt-1 text-xs text-gray-700">
                              🏛{abbreviateLocation(session.venueName)}
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
            className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex justify-between items-start mb-4">
              <h3 className="text-xl font-bold text-gray-900">
                📅 {formatDateForModal(selectedSession.sessionDate)}
              </h3>
              <button onClick={closeModal} className="text-gray-400 hover:text-gray-600">
                <X size={24} />
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <div className="text-sm font-medium text-gray-700">📍 場所:</div>
                <div className="text-base text-gray-900">{selectedSession.venueName || '-'}</div>
              </div>

              <div>
                <div className="text-sm font-medium text-gray-700 mb-2">🎯 試合別参加者:</div>
                <div className="space-y-2">
                  {selectedSession.matchParticipants &&
                  Object.keys(selectedSession.matchParticipants).length > 0 ? (
                    Object.entries(selectedSession.matchParticipants)
                      .sort(([a], [b]) => parseInt(a) - parseInt(b))
                      .map(([matchNum, participants]) => {
                        const isExpanded = expandedMatches[matchNum];
                        const count = participants.length;
                        const myMatchNumbers = myParticipations[selectedSession.id] || [];
                        const isMyMatch = myMatchNumbers.includes(parseInt(matchNum));

                        // 試合時間を取得
                        const schedule = selectedSession.venueSchedules?.find(
                          s => s.matchNumber === parseInt(matchNum)
                        );
                        const timeRange = schedule
                          ? `${schedule.startTime?.substring(0, 5)}-${schedule.endTime?.substring(0, 5)}`
                          : '';

                        return (
                          <div key={matchNum} className="border border-gray-200 rounded overflow-hidden">
                            <button
                              onClick={() => toggleMatch(matchNum)}
                              className={`w-full px-3 py-2 ${isMyMatch ? 'bg-green-50 hover:bg-green-100' : 'bg-gray-50 hover:bg-gray-100'} transition-colors text-left flex items-center justify-between`}
                            >
                              <span className="text-sm font-medium text-gray-900">
                                {isExpanded ? '▼' : '▶'} {matchNum}試合目{timeRange ? `: ${timeRange}` : ''} ({count}名)
                              </span>
                            </button>
                            {isExpanded && (
                              <div className={`px-3 py-2 ${isMyMatch ? 'bg-green-50' : 'bg-white'}`}>
                                {participants.length > 0 ? (
                                  <ul className="list-disc list-inside space-y-1">
                                    {participants.map((name, idx) => (
                                      <li key={idx} className="text-sm text-gray-700">
                                        {name}
                                      </li>
                                    ))}
                                  </ul>
                                ) : (
                                  <div className="text-sm text-gray-500">参加者なし</div>
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })
                  ) : (
                    <div className="text-sm text-gray-500">試合別参加者データなし</div>
                  )}
                </div>
              </div>

              {selectedSession.remarks && (
                <div>
                  <div className="text-sm font-medium text-gray-700">📝 備考:</div>
                  <div className="text-base text-gray-900">{selectedSession.remarks}</div>
                </div>
              )}
            </div>

            <div className="flex justify-between items-center gap-3 mt-6">
              {isPastDate(selectedSession.sessionDate) ? (
                <button
                  onClick={() => navigate(`/matches/results/${selectedSession.id}`)}
                  className="px-4 py-2 bg-primary-600 text-white rounded hover:bg-primary-700 transition-colors"
                >
                  📊 試合結果を見る
                </button>
              ) : (
                <button
                  onClick={goToParticipation}
                  className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 transition-colors"
                >
                  📝 参加登録
                </button>
              )}
              <div className="flex gap-3">
                {isSuperAdmin() && (
                  <>
                    <button
                      onClick={() => navigate(`/practice/${selectedSession.id}/edit`)}
                      className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                    >
                      編集
                    </button>
                    <button
                      onClick={() => handleDelete(selectedSession.id)}
                      className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
                    >
                      削除
                    </button>
                  </>
                )}
                <button
                  onClick={closeModal}
                  className="px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300 transition-colors"
                >
                  閉じる
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PracticeList;
