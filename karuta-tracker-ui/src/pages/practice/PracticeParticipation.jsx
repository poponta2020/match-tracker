import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { practiceAPI, lotteryAPI } from '../../api';
import { ChevronLeft, ChevronRight, Check, Save, AlertCircle, XCircle } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const PracticeParticipation = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [currentDate, setCurrentDate] = useState(new Date());
  const [sessions, setSessions] = useState([]);
  const [participations, setParticipations] = useState({});
  const [initialParticipations, setInitialParticipations] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [cancelling, setCancelling] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [participationStatuses, setParticipationStatuses] = useState({}); // sessionId -> [{matchNumber, status, waitlistNumber}]
  const [lotteryExecuted, setLotteryExecuted] = useState({}); // sessionId -> boolean

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth() + 1;

  // 月のセッションと参加状況を取得
  useEffect(() => {
    const fetchData = async () => {
      if (!currentPlayer?.id) return;

      setLoading(true);
      setError('');

      try {
        const [sessionsRes, participationsRes, statusRes] = await Promise.all([
          practiceAPI.getByYearMonth(year, month),
          practiceAPI.getPlayerParticipations(currentPlayer.id, year, month),
          practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month),
        ]);

        // セッションを日付昇順にソート
        const sortedSessions = (sessionsRes.data || []).sort(
          (a, b) => new Date(a.sessionDate) - new Date(b.sessionDate)
        );

        setSessions(sortedSessions);
        const participationsData = participationsRes.data || {};
        setParticipations(participationsData);
        setInitialParticipations(JSON.parse(JSON.stringify(participationsData)));

        // 抽選ステータス情報
        const statusData = statusRes.data || {};
        setParticipationStatuses(statusData.participations || {});
        setLotteryExecuted(statusData.lotteryExecuted || {});
      } catch (err) {
        console.error('データ取得エラー:', err);
        setError('データの取得に失敗しました');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [currentPlayer?.id, year, month]);

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
    // 抽選済みセッションは変更不可
    if (lotteryExecuted[sessionId]) return;

    const sessionParticipations = participations[sessionId] || [];
    const isChecked = sessionParticipations.includes(matchNumber);

    if (isChecked) {
      setParticipations({
        ...participations,
        [sessionId]: sessionParticipations.filter((m) => m !== matchNumber),
      });
    } else {
      setParticipations({
        ...participations,
        [sessionId]: [...sessionParticipations, matchNumber],
      });
    }
  };

  // 変更があるかチェック（抽選済みセッションの変更は除外）
  const hasChanges = () => {
    // 抽選済みセッションを除いた参加情報を比較
    const filterLottery = (data) => {
      const filtered = {};
      for (const [sid, matches] of Object.entries(data)) {
        if (!lotteryExecuted[Number(sid)]) {
          filtered[sid] = matches;
        }
      }
      return filtered;
    };
    return JSON.stringify(filterLottery(participations)) !== JSON.stringify(filterLottery(initialParticipations));
  };

  // 保存処理
  const handleSave = async () => {
    if (!currentPlayer?.id) return;

    setSaving(true);
    setError('');
    setSuccess('');

    try {
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

  // 抽選後の当選をキャンセル
  const handleCancelParticipation = async (participantId, sessionId, matchNumber) => {
    if (!currentPlayer?.id || !participantId) return;
    if (!window.confirm('この試合の参加をキャンセルしますか？キャンセル待ちの次の方に通知されます。')) return;

    setCancelling(`${sessionId}-${matchNumber}`);
    setError('');
    try {
      await lotteryAPI.cancel(participantId);
      setSuccess('参加をキャンセルしました');
      // データをリロード
      const [participationsRes, statusRes] = await Promise.all([
        practiceAPI.getPlayerParticipations(currentPlayer.id, year, month),
        practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month),
      ]);
      const participationsData = participationsRes.data || {};
      setParticipations(participationsData);
      setInitialParticipations(JSON.parse(JSON.stringify(participationsData)));
      setParticipationStatuses(statusRes.data?.participations || {});
      setLotteryExecuted(statusRes.data?.lotteryExecuted || {});
    } catch (err) {
      console.error('キャンセルエラー:', err);
      setError(err.response?.data?.message || 'キャンセルに失敗しました');
    } finally {
      setCancelling(null);
    }
  };

  // 抽選ステータスのラベルとスタイルを取得
  const getStatusInfo = (status) => {
    switch (status) {
      case 'WON': return { label: '当選', color: 'bg-green-100 text-green-800', canCancel: true };
      case 'WAITLISTED': return { label: '待ち', color: 'bg-yellow-100 text-yellow-800', canCancel: false };
      case 'OFFERED': return { label: '応答待', color: 'bg-blue-100 text-blue-800', canCancel: false };
      case 'PENDING': return { label: '申込', color: 'bg-gray-100 text-gray-600', canCancel: false };
      case 'DECLINED': return { label: '辞退', color: 'bg-gray-100 text-gray-400', canCancel: false };
      case 'CANCELLED': return { label: '取消', color: 'bg-red-100 text-red-400', canCancel: false };
      default: return { label: '−', color: 'bg-gray-100 text-gray-400', canCancel: false };
    }
  };

  // 日付が過去かどうか判定
  const isPastDate = (dateStr) => {
    const sessionDate = new Date(dateStr);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return sessionDate < today;
  };

  // 場所名を省略
  const abbreviateVenue = (name) => {
    if (!name) return '-';
    if (name.length <= 4) return name;
    if (name.includes('センター')) return name.replace('センター', '');
    if (name.includes('会館')) return name.replace(/.*?(?=会館)/, '').length < name.length ? name.substring(0, 4) : name;
    return name.substring(0, 4);
  };

  // 参加人数に応じたバッジの色を取得
  const getParticipantBadgeColor = (count, capacity) => {
    if (!capacity) capacity = 20;
    const ratio = count / capacity;

    if (ratio >= 0.8) return 'bg-red-100 text-red-800';
    if (ratio >= 0.6) return 'bg-orange-100 text-orange-800';
    if (ratio >= 0.4) return 'bg-yellow-100 text-yellow-800';
    return 'bg-green-100 text-green-800';
  };

  // 未来のセッションのみフィルタ
  const futureSessions = sessions.filter((s) => !isPastDate(s.sessionDate));

  if (loading) {
    return <LoadingScreen />;
  }

  return (
    <div className="max-w-7xl mx-auto">
      {/* 固定ナビゲーションバー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <button
            onClick={handlePrevMonth}
            className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
          >
            <ChevronLeft className="w-6 h-6 text-white" />
          </button>
          <h1 className="text-lg font-semibold text-white">
            {year}年{month}月 参加登録
          </h1>
          <button
            onClick={handleNextMonth}
            className="p-2 hover:bg-[#3d5a4c] rounded-full transition-colors"
          >
            <ChevronRight className="w-6 h-6 text-white" />
          </button>
        </div>
      </div>

      {/* コンテンツ */}
      <div className="pt-20 pb-24">
        {/* エラー・成功メッセージ */}
        {error && (
          <div className="mx-4 mb-4 bg-red-50 border border-red-200 rounded-lg p-3 flex items-start gap-2">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {success && (
          <div className="mx-4 mb-4 bg-green-50 border border-green-200 rounded-lg p-3 flex items-start gap-2">
            <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-green-700">{success}</p>
          </div>
        )}

        {/* 練習セッション一覧 */}
        {futureSessions.length === 0 ? (
          <div className="bg-[#f9f6f2] rounded-lg shadow-sm p-12 text-center mx-4">
            <p className="text-gray-500">この月の今後の練習予定はありません</p>
          </div>
        ) : (
          <div className="bg-[#f9f6f2] rounded-lg shadow-sm overflow-hidden mx-4">
            <div className="overflow-x-auto">
              <table className="w-full table-fixed">
                <thead className="bg-[#e2d9d0] border-b border-[#d0c5b8]">
                  <tr>
                    <th className="w-[72px] px-2 py-2 text-left text-xs font-semibold text-[#5f3a2d]">
                      日付
                    </th>
                    <th className="w-[52px] px-1 py-2 text-left text-xs font-semibold text-[#5f3a2d]">
                      場所
                    </th>
                    {Array.from({ length: 7 }, (_, i) => i + 1).map((n) => (
                      <th key={n} className="px-0 py-2 text-center text-xs font-semibold text-[#5f3a2d]">
                        {n}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#d0c5b8]">
                  {futureSessions.map((session) => {
                    const sessionParticipations = participations[session.id] || [];
                    const matchCount = Math.min(session.totalMatches || 7, 7);

                    return (
                      <tr key={session.id} className="hover:bg-[#f0ebe3]">
                        {/* 日付 */}
                        <td className="px-2 py-3">
                          <span className="text-sm text-gray-900 whitespace-nowrap">
                            {new Date(session.sessionDate).toLocaleDateString(
                              'ja-JP',
                              { month: 'numeric', day: 'numeric', weekday: 'short' }
                            )}
                          </span>
                        </td>

                        {/* 場所（省略表示） */}
                        <td className="px-1 py-3">
                          <span className="text-xs text-gray-700 whitespace-nowrap">
                            {abbreviateVenue(session.venueName)}
                          </span>
                        </td>

                        {/* 試合チェックボックス or 抽選ステータス */}
                        {Array.from({ length: 7 }, (_, i) => i + 1).map(
                          (matchNumber) => {
                            const isAvailable = matchNumber <= matchCount;
                            const isChecked = sessionParticipations.includes(matchNumber);
                            const participantCount =
                              session.matchParticipantCounts?.[matchNumber] || 0;

                            // 抽選済みセッションの場合はステータス表示
                            const isLotteryDone = lotteryExecuted[session.id] === true;
                            const statusList = participationStatuses[session.id] || [];
                            const matchStatus = statusList.find(s => s.matchNumber === matchNumber);

                            return (
                              <td key={matchNumber} className="px-0 py-3 text-center">
                                {isAvailable ? (
                                  isLotteryDone && matchStatus ? (
                                    // 抽選済み: ステータス表示
                                    <div className="flex flex-col items-center gap-0.5">
                                      <span className={`text-[10px] px-1.5 py-0.5 rounded font-bold ${getStatusInfo(matchStatus.status).color}`}>
                                        {getStatusInfo(matchStatus.status).label}
                                      </span>
                                      {matchStatus.status === 'WAITLISTED' && matchStatus.waitlistNumber && (
                                        <span className="text-[9px] text-gray-500">#{matchStatus.waitlistNumber}</span>
                                      )}
                                      {getStatusInfo(matchStatus.status).canCancel && (
                                        <button
                                          onClick={() => handleCancelParticipation(matchStatus.participantId, session.id, matchNumber)}
                                          disabled={cancelling === `${session.id}-${matchNumber}`}
                                          className="text-[9px] text-red-500 hover:text-red-700 disabled:opacity-50"
                                        >
                                          {cancelling === `${session.id}-${matchNumber}` ? '...' : '取消'}
                                        </button>
                                      )}
                                    </div>
                                  ) : isLotteryDone && !matchStatus ? (
                                    // 抽選済みだが未登録の試合
                                    <span className="text-gray-300">−</span>
                                  ) : (
                                    // 抽選前: チェックボックス
                                    <div className="flex flex-col items-center gap-0.5">
                                      <input
                                        type="checkbox"
                                        checked={isChecked}
                                        onChange={() => toggleMatch(session.id, matchNumber)}
                                        className="w-5 h-5 border-gray-300 rounded focus:ring-[#4a6b5a]"
                                        style={{ accentColor: '#4a6b5a' }}
                                      />
                                      <span
                                        className={`text-[10px] px-1 rounded font-medium ${getParticipantBadgeColor(
                                          participantCount,
                                          session.capacity
                                        )}`}
                                      >
                                        {participantCount}
                                      </span>
                                    </div>
                                  )
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
      </div>

      {/* 固定保存ボタン（変更がある場合のみ表示） */}
      {futureSessions.length > 0 && hasChanges() && (
        <div className="fixed left-0 right-0 z-40 px-4 py-3 bg-white border-t border-gray-200 shadow-lg" style={{ bottom: 'calc(3.5rem + env(safe-area-inset-bottom, 0px))' }}>
          <button
            onClick={handleSave}
            disabled={saving}
            className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-[#82655a] text-white rounded-lg hover:bg-[#6b5048] transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-medium"
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
