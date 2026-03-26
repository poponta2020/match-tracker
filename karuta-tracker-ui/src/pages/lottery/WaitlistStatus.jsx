import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lotteryAPI } from '../../api/lottery';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * キャンセル待ち状況確認画面
 */
export default function WaitlistStatus() {
  const { currentPlayer } = useAuth();
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (currentPlayer?.id) fetchStatus();
  }, [currentPlayer]);

  const fetchStatus = async () => {
    setLoading(true);
    try {
      const res = await lotteryAPI.getWaitlistStatus();
      setEntries(res.data.entries || []);
    } catch (err) {
      console.error('Failed to fetch waitlist status:', err);
    } finally {
      setLoading(false);
    }
  };

  const getStatusLabel = (status) => {
    switch (status) {
      case 'WAITLISTED': return { text: '待機中', color: 'bg-yellow-100 text-yellow-800' };
      case 'OFFERED': return { text: '応答待ち', color: 'bg-blue-100 text-blue-800' };
      default: return { text: status, color: 'bg-gray-100 text-gray-600' };
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-4">
      <h1 className="text-xl font-bold mb-4">キャンセル待ち状況</h1>

      {loading ? (
        <LoadingScreen />
      ) : entries.length === 0 ? (
        <div className="text-center py-8 text-gray-500">
          キャンセル待ちはありません
        </div>
      ) : (
        <div className="space-y-3">
          {entries.map((entry) => {
            const statusInfo = getStatusLabel(entry.status);
            return (
              <div key={entry.participantId} className="bg-white rounded-lg shadow p-4">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="font-bold">
                      {new Date(entry.sessionDate).toLocaleDateString('ja-JP', {
                        month: 'long', day: 'numeric', weekday: 'short'
                      })}
                    </div>
                    <div className="text-sm text-gray-600">
                      試合{entry.matchNumber}
                      {entry.startTime && ` · ${entry.startTime}〜${entry.endTime}`}
                    </div>
                  </div>
                  <div className="text-right">
                    <span className={`px-2 py-1 rounded text-xs font-bold ${statusInfo.color}`}>
                      {statusInfo.text}
                    </span>
                    <div className="text-sm text-gray-500 mt-1">
                      待ち番号: {entry.waitlistNumber}番
                    </div>
                  </div>
                </div>
                {entry.status === 'OFFERED' && entry.offerDeadline && (
                  <div className="mt-2 p-2 bg-blue-50 rounded text-sm text-blue-700">
                    応答期限: {new Date(entry.offerDeadline).toLocaleString('ja-JP')}
                    <a href={`/lottery/offer-response?id=${entry.participantId}`}
                      className="ml-2 font-bold underline">応答する</a>
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
