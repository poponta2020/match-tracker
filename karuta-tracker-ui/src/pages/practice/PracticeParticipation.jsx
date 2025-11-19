import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { practiceAPI } from '../../api';
import { ChevronLeft, ChevronRight, Check, Save, AlertCircle } from 'lucide-react';

const PracticeParticipation = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [currentDate, setCurrentDate] = useState(new Date());
  const [sessions, setSessions] = useState([]);
  const [participations, setParticipations] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth() + 1;

  // 月のセッションと参加状況を取得
  useEffect(() => {
    const fetchData = async () => {
      if (!currentPlayer?.id) return;

      setLoading(true);
      setError('');

      try {
        const [sessionsRes, participationsRes] = await Promise.all([
          practiceAPI.getByYearMonth(year, month),
          practiceAPI.getPlayerParticipations(currentPlayer.id, year, month),
        ]);

        // セッションを日付昇順にソート
        const sortedSessions = (sessionsRes.data || []).sort(
          (a, b) => new Date(a.sessionDate) - new Date(b.sessionDate)
        );

        setSessions(sortedSessions);
        setParticipations(participationsRes.data || {});
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [currentPlayer, year, month]);

  // 前月へ
  const handlePrevMonth = () => {
    setCurrentDate(
      new Date(currentDate.getFullYear(), currentDate.getMonth() - 1, 1)
    );
  };

  // 次月へ
  const handleNextMonth = () => {
    setCurrentDate(
      new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 1)
    );
  };

  // チェックボックスの状態を切り替え
  const toggleMatch = (sessionId, matchNumber) => {
    const sessionParticipations = participations[sessionId] || [];
    const isChecked = sessionParticipations.includes(matchNumber);

    if (isChecked) {
      // チェックを外す
      setParticipations({
        ...participations,
        [sessionId]: sessionParticipations.filter((m) => m !== matchNumber),
      });
    } else {
      // チェックを入れる
      setParticipations({
        ...participations,
        [sessionId]: [...sessionParticipations, matchNumber],
      });
    }
  };

  // 保存処理
  const handleSave = async () => {
    if (!currentPlayer?.id) return;

    setSaving(true);
    setError('');
    setSuccess('');

    try {
      // 参加情報をフラットな配列に変換
      const participationsList = [];
      Object.entries(participations).forEach(([sessionId, matchNumbers]) => {
        matchNumbers.forEach((matchNumber) => {
          participationsList.push({
            sessionId: Number(sessionId),
            matchNumber: matchNumber,
          });
        });
      });

      await practiceAPI.registerParticipations({
        playerId: currentPlayer.id,
        year: year,
        month: month,
        participations: participationsList,
      });

      setSuccess('参加登録を保存しました');
      // 保存成功後、1秒待ってから練習記録画面に遷移
      setTimeout(() => {
        navigate('/practice');
      }, 1000);
    } catch (err) {
      console.error('保存エラー:', err);
      setError('保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  // 日付が過去かどうか判定
  const isPastDate = (dateStr) => {
    const sessionDate = new Date(dateStr);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return sessionDate < today;
  };

  // 時刻表示のフォーマット
  const formatTime = (timeStr) => {
    if (!timeStr) return '';
    const [hours, minutes] = timeStr.split(':');
    return `${hours}:${minutes}`;
  };

  // 参加人数に応じたバッジの色を取得
  // 定員に近いほど危険な色（赤系）にする
  const getParticipantBadgeColor = (count, capacity) => {
    if (!capacity) {
      // 定員が設定されていない場合はデフォルトで20とする
      capacity = 20;
    }

    const ratio = count / capacity;

    if (ratio >= 0.8) {
      // 80%以上: 赤系（危険）
      return 'bg-red-100 text-red-800';
    } else if (ratio >= 0.6) {
      // 60-79%: オレンジ系（注意）
      return 'bg-orange-100 text-orange-800';
    } else if (ratio >= 0.4) {
      // 40-59%: 黄色系（やや余裕あり）
      return 'bg-yellow-100 text-yellow-800';
    } else {
      // 40%未満: 緑系（余裕あり）
      return 'bg-green-100 text-green-800';
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-96">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* ヘッダー */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">
          練習参加登録
        </h1>
        <p className="text-gray-600">
          参加する練習日と試合にチェックを入れて保存してください
        </p>
      </div>

      {/* エラー・成功メッセージ */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-medium text-red-900">エラー</p>
            <p className="text-sm text-red-700">{error}</p>
          </div>
        </div>
      )}

      {success && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 flex items-start gap-3">
          <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
          <div>
            <p className="font-medium text-green-900">成功</p>
            <p className="text-sm text-green-700">{success}</p>
          </div>
        </div>
      )}

      {/* 月選択 */}
      <div className="bg-white rounded-lg shadow-sm p-4">
        <div className="flex items-center justify-between">
          <button
            onClick={handlePrevMonth}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ChevronLeft className="w-5 h-5 text-gray-600" />
          </button>
          <h2 className="text-xl font-bold text-gray-900">
            {year}年{month}月
          </h2>
          <button
            onClick={handleNextMonth}
            className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ChevronRight className="w-5 h-5 text-gray-600" />
          </button>
        </div>
      </div>

      {/* 練習セッション一覧 */}
      {sessions.length === 0 ? (
        <div className="bg-white rounded-lg shadow-sm p-12 text-center">
          <p className="text-gray-500">この月の練習予定はありません</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                    日付
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                    時間
                  </th>
                  <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">
                    場所
                  </th>
                  <th className="px-4 py-3 text-center text-sm font-semibold text-gray-900" colSpan="7">
                    試合
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {sessions.map((session) => {
                  const isPast = isPastDate(session.sessionDate);
                  const sessionParticipations = participations[session.id] || [];
                  const matchCount = Math.min(session.totalMatches || 7, 7);

                  return (
                    <tr
                      key={session.id}
                      className={isPast ? 'bg-gray-50' : 'hover:bg-gray-50'}
                    >
                      {/* 日付 */}
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <span
                            className={`text-sm ${
                              isPast ? 'text-gray-400' : 'text-gray-900'
                            }`}
                          >
                            {new Date(session.sessionDate).toLocaleDateString(
                              'ja-JP',
                              {
                                month: 'numeric',
                                day: 'numeric',
                                weekday: 'short',
                              }
                            )}
                          </span>
                          {isPast && (
                            <span className="px-2 py-0.5 bg-gray-200 text-gray-600 text-xs rounded">
                              終了
                            </span>
                          )}
                        </div>
                      </td>

                      {/* 時間 */}
                      <td className="px-4 py-3">
                        <span
                          className={`text-sm ${
                            isPast ? 'text-gray-400' : 'text-gray-700'
                          }`}
                        >
                          {session.startTime && session.endTime
                            ? `${formatTime(session.startTime)}~${formatTime(
                                session.endTime
                              )}`
                            : '-'}
                        </span>
                      </td>

                      {/* 場所 */}
                      <td className="px-4 py-3">
                        <span
                          className={`text-sm ${
                            isPast ? 'text-gray-400' : 'text-gray-700'
                          }`}
                        >
                          {session.location || '-'}
                        </span>
                      </td>

                      {/* 試合チェックボックス */}
                      {Array.from({ length: 7 }, (_, i) => i + 1).map(
                        (matchNumber) => {
                          const isAvailable = matchNumber <= matchCount;
                          const isChecked =
                            sessionParticipations.includes(matchNumber);
                          const participantCount =
                            session.matchParticipantCounts?.[matchNumber] || 0;

                          return (
                            <td
                              key={matchNumber}
                              className="px-2 py-3 text-center"
                            >
                              {isAvailable ? (
                                <div className="flex flex-col items-center gap-1">
                                  <input
                                    type="checkbox"
                                    checked={isChecked}
                                    onChange={() =>
                                      !isPast &&
                                      toggleMatch(session.id, matchNumber)
                                    }
                                    disabled={isPast}
                                    className="w-5 h-5 text-primary-600 border-gray-300 rounded focus:ring-primary-500 disabled:opacity-50 disabled:cursor-not-allowed"
                                  />
                                  {!isPast && (
                                    <span
                                      className={`text-xs px-2 py-0.5 rounded font-medium ${getParticipantBadgeColor(
                                        participantCount,
                                        session.capacity
                                      )}`}
                                      title={`試合${matchNumber}に${participantCount}名参加中`}
                                    >
                                      {participantCount}
                                    </span>
                                  )}
                                </div>
                              ) : (
                                <span className="text-gray-300">-</span>
                              )}
                            </td>
                          );
                        }
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 保存ボタン */}
      {sessions.length > 0 && (
        <div className="flex justify-end">
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 px-6 py-3 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <Save className="w-5 h-5" />
            {saving ? '保存中...' : '保存する'}
          </button>
        </div>
      )}
    </div>
  );
};

export default PracticeParticipation;
