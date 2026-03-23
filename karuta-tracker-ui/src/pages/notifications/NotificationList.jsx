import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { notificationAPI } from '../../api/notifications';

/**
 * 通知一覧画面
 */
export default function NotificationList() {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

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
    // 既読にする
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

    // 繰り上げ通知の場合は承認画面へ遷移
    if (notification.type === 'WAITLIST_OFFER' && notification.referenceId) {
      navigate(`/lottery/offer-response?id=${notification.referenceId}`);
    }
  };

  const getTypeIcon = (type) => {
    switch (type) {
      case 'LOTTERY_WON': return '✓';
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
      case 'LOTTERY_WAITLISTED': return 'border-l-yellow-500';
      case 'WAITLIST_OFFER': return 'border-l-blue-500';
      case 'OFFER_EXPIRED': return 'border-l-red-500';
      default: return 'border-l-gray-300';
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-4">
      <h1 className="text-xl font-bold mb-4">通知</h1>

      {loading ? (
        <div className="text-center py-8 text-gray-500">読み込み中...</div>
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
                  <div className="text-xs text-gray-600 mt-0.5">{n.message}</div>
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
