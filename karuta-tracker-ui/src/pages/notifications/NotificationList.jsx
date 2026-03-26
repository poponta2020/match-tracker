import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { notificationAPI } from '../../api/notifications';
import { lotteryAPI } from '../../api/lottery';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * 通知一覧画面
 */
export default function NotificationList() {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (currentPlayer?.id) fetchNotifications();
  }, [currentPlayer]);

  const fetchNotifications = async () => {
    setLoading(true);
    try {
      const res = await notificationAPI.getAll(currentPlayer.id);
      setNotifications(res.data);
    } catch (err) {
      console.error('Failed to fetch notifications:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleClick = async (notification) => {
    if (!notification.isRead) {
      try {
        await notificationAPI.markAsRead(notification.id);
        setNotifications((prev) =>
          prev.map((n) => n.id === notification.id ? { ...n, isRead: true } : n)
        );
      } catch (err) {
        console.error('Failed to mark as read:', err);
      }
    }

    if (notification.type === 'WAITLIST_OFFER' && notification.referenceId) {
      navigate(`/lottery/offer-response?id=${notification.referenceId}`);
    }
  };

  const handleDeleteAll = async () => {
    if (!confirm('通知をすべて削除しますか？')) return;
    setDeleting(true);
    try {
      await notificationAPI.deleteAll(currentPlayer.id);
      setNotifications([]);
    } catch (err) {
      console.error('Failed to delete notifications:', err);
      alert('削除に失敗しました');
    } finally {
      setDeleting(false);
    }
  };

  const handleDeclineWaitlist = async (e, notification) => {
    e.stopPropagation();
    if (!confirm('キャンセル待ちを辞退しますか？')) return;

    setProcessing(notification.id);
    try {
      await lotteryAPI.declineWaitlist(notification.referenceId, currentPlayer.id);
      await fetchNotifications();
    } catch (err) {
      console.error('Failed to decline waitlist:', err);
      alert('辞退処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  const getTypeIcon = (type) => {
    switch (type) {
      case 'LOTTERY_WON': return '✓';
      case 'LOTTERY_ALL_WON': return '✓';
      case 'LOTTERY_REMAINING_WON': return '✓';
      case 'LOTTERY_WAITLISTED': return '⏳';
      case 'WAITLIST_OFFER': return '📩';
      case 'OFFER_EXPIRING': return '⚠';
      case 'OFFER_EXPIRED': return '✕';
      default: return '📌';
    }
  };

  const getTypeBg = (type) => {
    switch (type) {
      case 'LOTTERY_WON': return 'border-l-green-500';
      case 'LOTTERY_ALL_WON': return 'border-l-green-500';
      case 'LOTTERY_REMAINING_WON': return 'border-l-green-500';
      case 'LOTTERY_WAITLISTED': return 'border-l-yellow-500';
      case 'WAITLIST_OFFER': return 'border-l-blue-500';
      case 'OFFER_EXPIRED': return 'border-l-red-500';
      default: return 'border-l-gray-300';
    }
  };

  const isWaitlistedNotification = (n) =>
    n.type === 'LOTTERY_WAITLISTED' && n.referenceType === 'PRACTICE_SESSION' && n.referenceId;

  return (
    <div className="max-w-2xl mx-auto p-4">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-xl font-bold">通知</h1>
        {notifications.length > 0 && !loading && (
          <button
            onClick={handleDeleteAll}
            disabled={deleting}
            className="px-3 py-1.5 text-sm bg-red-500 hover:bg-red-600 text-white rounded disabled:opacity-50">
            {deleting ? '削除中...' : 'すべて削除'}
          </button>
        )}
      </div>

      {loading ? (
        <LoadingScreen />
      ) : notifications.length === 0 ? (
        <div className="text-center py-8 text-gray-500">通知はありません</div>
      ) : (
        <div className="space-y-2">
          {notifications.map((n) => (
            <div
              key={n.id}
              onClick={() => handleClick(n)}
              className={`bg-white rounded-lg shadow p-3 border-l-4 cursor-pointer hover:bg-gray-50
                ${getTypeBg(n.type)} ${!n.isRead ? 'font-semibold' : 'opacity-75'}`}>
              <div className="flex items-start gap-2">
                <span className="text-lg">{getTypeIcon(n.type)}</span>
                <div className="flex-1 min-w-0">
                  <div className="text-sm">{n.title}</div>
                  <div className="text-xs text-gray-600 mt-0.5 whitespace-pre-line">{n.message}</div>
                  {isWaitlistedNotification(n) && (
                    <button
                      onClick={(e) => handleDeclineWaitlist(e, n)}
                      disabled={processing === n.id}
                      className="mt-2 px-3 py-1 text-xs bg-gray-200 hover:bg-gray-300 text-gray-700 rounded disabled:opacity-50">
                      {processing === n.id ? '処理中...' : 'キャンセル待ちを辞退する'}
                    </button>
                  )}
                  <div className="text-xs text-gray-400 mt-1">
                    {new Date(n.createdAt).toLocaleString('ja-JP')}
                  </div>
                </div>
                {!n.isRead && (
                  <span className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0 mt-1.5"></span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
