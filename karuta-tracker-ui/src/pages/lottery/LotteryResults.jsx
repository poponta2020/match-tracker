import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lotteryAPI } from '../../api/lottery';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * 抽選結果確認画面
 */
export default function LotteryResults() {
  const { currentPlayer } = useAuth();
  const [currentDate, setCurrentDate] = useState(() => {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() + 1 };
  });
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(null);

  useEffect(() => {
    fetchResults();
  }, [currentDate]);

  const fetchResults = async () => {
    setLoading(true);
    try {
      const res = await lotteryAPI.getResults(currentDate.year, currentDate.month);
      setResults(res.data);
    } catch (err) {
      console.error('Failed to fetch lottery results:', err);
    } finally {
      setLoading(false);
    }
  };

  const changeMonth = (delta) => {
    setCurrentDate((prev) => {
      let newMonth = prev.month + delta;
      let newYear = prev.year;
      if (newMonth > 12) { newMonth = 1; newYear++; }
      if (newMonth < 1) { newMonth = 12; newYear--; }
      return { year: newYear, month: newMonth };
    });
  };

  const handleDeclineWaitlist = async (e, sessionId) => {
    e.stopPropagation();
    if (!confirm('このセッションのキャンセル待ちを辞退しますか？')) return;

    setProcessing(`decline-${sessionId}`);
    try {
      await lotteryAPI.declineWaitlist(sessionId, currentPlayer.id);
      await fetchResults();
    } catch (err) {
      console.error('Failed to decline waitlist:', err);
      alert('辞退処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  const handleRejoinWaitlist = async (e, sessionId) => {
    e.stopPropagation();
    if (!confirm('キャンセル待ちに復帰しますか？（最後尾になります）')) return;

    setProcessing(`rejoin-${sessionId}`);
    try {
      await lotteryAPI.rejoinWaitlist(sessionId, currentPlayer.id);
      await fetchResults();
    } catch (err) {
      console.error('Failed to rejoin waitlist:', err);
      alert('復帰処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  const getStatusBadge = (status, waitlistNumber) => {
    switch (status) {
      case 'WON': return <span className="px-2 py-0.5 bg-green-100 text-green-800 rounded text-xs font-bold">当選</span>;
      case 'WAITLISTED': return <span className="px-2 py-0.5 bg-yellow-100 text-yellow-800 rounded text-xs font-bold">待ち{waitlistNumber}番</span>;
      case 'OFFERED': return <span className="px-2 py-0.5 bg-blue-100 text-blue-800 rounded text-xs font-bold">繰上連絡中</span>;
      case 'DECLINED': return <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs font-bold">辞退</span>;
      case 'CANCELLED': return <span className="px-2 py-0.5 bg-red-100 text-red-800 rounded text-xs font-bold">キャンセル</span>;
      case 'PENDING': return <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs font-bold">申込中</span>;
      case 'WAITLIST_DECLINED': return <span className="px-2 py-0.5 bg-gray-100 text-gray-500 rounded text-xs font-bold">待ち辞退</span>;
      default: return null;
    }
  };

  const isMyResult = (playerId) => currentPlayer && currentPlayer.id === playerId;

  // セッション内で自分がWAITLISTED/WAITLIST_DECLINEDかチェック
  const getMyWaitlistStatus = (session) => {
    if (!currentPlayer || !session.matchResults) return null;
    let hasWaitlisted = false;
    let hasWaitlistDeclined = false;
    for (const match of Object.values(session.matchResults)) {
      for (const p of (match.waitlisted || [])) {
        if (p.playerId === currentPlayer.id) {
          if (p.status === 'WAITLISTED') hasWaitlisted = true;
          if (p.status === 'WAITLIST_DECLINED') hasWaitlistDeclined = true;
        }
      }
    }
    if (hasWaitlisted) return 'WAITLISTED';
    if (hasWaitlistDeclined) return 'WAITLIST_DECLINED';
    return null;
  };

  return (
    <div className="max-w-2xl mx-auto p-4">
      <h1 className="text-xl font-bold mb-4">抽選結果</h1>

      {/* 月選択 */}
      <div className="flex items-center justify-center gap-4 mb-6">
        <button onClick={() => changeMonth(-1)} className="p-2 rounded hover:bg-gray-100">&lt;</button>
        <span className="text-lg font-semibold">{currentDate.year}年{currentDate.month}月</span>
        <button onClick={() => changeMonth(1)} className="p-2 rounded hover:bg-gray-100">&gt;</button>
      </div>

      {loading ? (
        <LoadingScreen />
      ) : results.length === 0 ? (
        <div className="text-center py-8 text-gray-500">この月の抽選結果はありません</div>
      ) : (
        <div className="space-y-6">
          {results.map((session) => {
            const myWaitlistStatus = getMyWaitlistStatus(session);
            return (
              <div key={session.sessionId} className="bg-white rounded-lg shadow p-4">
                <div className="flex justify-between items-center mb-3">
                  <h2 className="font-bold text-lg">
                    {new Date(session.sessionDate).toLocaleDateString('ja-JP', { month: 'long', day: 'numeric', weekday: 'short' })}
                  </h2>
                  {session.capacity && (
                    <span className="text-sm text-gray-500">定員: {session.capacity}名</span>
                  )}
                </div>

                {session.matchResults && Object.entries(session.matchResults).map(([matchNum, match]) => (
                  <div key={matchNum} className="mb-4 last:mb-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="font-semibold text-sm">試合{matchNum}</span>
                      {match.lotteryRequired && (
                        <span className="text-xs px-1.5 py-0.5 bg-orange-100 text-orange-700 rounded">抽選あり</span>
                      )}
                    </div>

                    {/* 当選者 */}
                    {match.winners && match.winners.length > 0 && (
                      <div className="mb-2">
                        <div className="text-xs text-gray-500 mb-1">当選者 ({match.winners.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.winners.map((p) => (
                            <span key={p.playerId}
                              className={`px-2 py-0.5 rounded text-xs ${isMyResult(p.playerId) ? 'bg-green-200 font-bold' : 'bg-gray-100'}`}>
                              {p.playerName}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* キャンセル待ち */}
                    {match.waitlisted && match.waitlisted.length > 0 && (
                      <div>
                        <div className="text-xs text-gray-500 mb-1">キャンセル待ち ({match.waitlisted.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.waitlisted.map((p) => (
                            <span key={p.playerId}
                              className={`px-2 py-0.5 rounded text-xs ${isMyResult(p.playerId) ? (p.status === 'WAITLIST_DECLINED' ? 'bg-gray-200 font-bold' : 'bg-yellow-200 font-bold') : 'bg-gray-100'}`}>
                              {p.waitlistNumber ? `${p.waitlistNumber}. ` : ''}{p.playerName} {getStatusBadge(p.status, p.waitlistNumber)}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}

                {/* セッション単位のキャンセル待ち辞退/復帰ボタン */}
                {myWaitlistStatus === 'WAITLISTED' && (
                  <div className="mt-3 pt-3 border-t">
                    <button
                      onClick={(e) => handleDeclineWaitlist(e, session.sessionId)}
                      disabled={processing === `decline-${session.sessionId}`}
                      className="px-3 py-1.5 text-xs bg-gray-200 hover:bg-gray-300 text-gray-700 rounded disabled:opacity-50">
                      {processing === `decline-${session.sessionId}` ? '処理中...' : 'この日のキャンセル待ちを辞退する'}
                    </button>
                  </div>
                )}
                {myWaitlistStatus === 'WAITLIST_DECLINED' && (
                  <div className="mt-3 pt-3 border-t">
                    <button
                      onClick={(e) => handleRejoinWaitlist(e, session.sessionId)}
                      disabled={processing === `rejoin-${session.sessionId}`}
                      className="px-3 py-1.5 text-xs bg-blue-100 hover:bg-blue-200 text-blue-700 rounded disabled:opacity-50">
                      {processing === `rejoin-${session.sessionId}` ? '処理中...' : 'キャンセル待ちに復帰する（最後尾）'}
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
