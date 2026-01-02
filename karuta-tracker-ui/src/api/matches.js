import apiClient from './client';

export const matchAPI = {
  // 試合記録一覧取得
  getAll: () => apiClient.get('/matches'),

  // 試合記録詳細取得
  getById: (id) => apiClient.get(`/matches/${id}`),

  // 試合記録登録（簡易版）
  create: (data) => apiClient.post('/matches', data),

  // 試合記録登録（詳細版）
  createDetailed: (data) => apiClient.post('/matches/detailed', data),

  // 試合記録更新（簡易版）
  update: (id, data) => apiClient.put(`/matches/${id}`, data),

  // 試合記録更新（詳細版）
  updateDetailed: (id, winnerId, scoreDifference, updatedBy) =>
    apiClient.put(`/matches/${id}/detailed`, null, {
      params: { winnerId, scoreDifference, updatedBy },
    }),

  // 試合記録削除
  delete: (id) => apiClient.delete(`/matches/${id}`),

  // 選手の試合記録取得
  getByPlayerId: (playerId) => apiClient.get(`/matches/player/${playerId}`),

  // 期間指定で試合記録取得
  getByDateRange: (startDate, endDate) =>
    apiClient.get('/matches/search', {
      params: { startDate, endDate },
    }),

  // 試合統計取得
  getStatistics: (playerId) =>
    apiClient.get(`/matches/player/${playerId}/statistics`),
};
