import apiClient from './client';

export const notificationAPI = {
  // 通知一覧取得
  getAll: (playerId) =>
    apiClient.get('/notifications', { params: { playerId } }),

  // 未読通知数取得
  getUnreadCount: (playerId) =>
    apiClient.get('/notifications/unread-count', { params: { playerId } }),

  // 通知を既読にする
  markAsRead: (id) =>
    apiClient.put(`/notifications/${id}/read`),

  // 通知を一括削除
  deleteAll: (playerId) =>
    apiClient.delete('/notifications', { params: { playerId } }),

  // Push購読登録
  subscribePush: (data) =>
    apiClient.post('/push-subscriptions', data),

  // Push購読解除
  unsubscribePush: (playerId, endpoint) =>
    apiClient.delete('/push-subscriptions', { params: { playerId, endpoint } }),

  // VAPID公開鍵取得
  getVapidPublicKey: () =>
    apiClient.get('/push-subscriptions/vapid-public-key'),
};
