import apiClient from './client';

export const practiceAPI = {
  // 練習記録一覧取得
  getAll: () => apiClient.get('/practice-sessions'),

  // 練習記録詳細取得
  getById: (id) => apiClient.get(`/practice-sessions/${id}`),

  // 練習記録登録
  create: (data) => apiClient.post('/practice-sessions', data),

  // 練習記録更新
  update: (id, data) => apiClient.put(`/practice-sessions/${id}`, data),

  // 練習記録削除
  delete: (id) => apiClient.delete(`/practice-sessions/${id}`),

  // 選手の練習記録取得
  getByPlayerId: (playerId) =>
    apiClient.get(`/practice-sessions/player/${playerId}`),

  // 期間指定で練習記録取得
  getByDateRange: (playerId, startDate, endDate) =>
    apiClient.get(`/practice-sessions/player/${playerId}`, {
      params: { startDate, endDate },
    }),

  // 練習統計取得
  getStatistics: (playerId) =>
    apiClient.get(`/practice-sessions/player/${playerId}/statistics`),
};
