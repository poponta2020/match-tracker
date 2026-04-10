import apiClient from './client';

export const matchAPI = {
  // 試合記録一覧取得
  getAll: () => apiClient.get('/matches'),

  // 試合記録詳細取得
  getById: (id, params = {}) => apiClient.get(`/matches/${id}`, { params }),

  // 試合記録登録（簡易版）
  create: (data) => apiClient.post('/matches', data),

  // 試合記録登録（詳細版）
  createDetailed: (data) => apiClient.post('/matches/detailed', data),

  // 試合記録更新（簡易版）
  update: (id, data) => apiClient.put(`/matches/${id}`, data),

  // 試合記録更新（詳細版）
  updateDetailed: (id, winnerId, scoreDifference, updatedBy, personalNotes, otetsukiCount) =>
    apiClient.put(`/matches/${id}/detailed`, null, {
      params: { winnerId, scoreDifference, updatedBy, personalNotes, otetsukiCount },
    }),

  // 試合記録削除
  delete: (id) => apiClient.delete(`/matches/${id}`),

  // 選手の試合記録取得
  getByPlayerId: (playerId, params = {}) =>
    apiClient.get(`/matches/player/${playerId}`, { params }),

  // 期間指定で試合記録取得
  getByDateRange: (startDate, endDate) =>
    apiClient.get('/matches/search', {
      params: { startDate, endDate },
    }),

  // 選手の期間内試合記録取得
  getByPlayerIdAndPeriod: (playerId, startDate, endDate) =>
    apiClient.get(`/matches/player/${playerId}/period`, {
      params: { startDate, endDate },
    }),

  // 選手の期間内試合数取得（軽量）
  getMatchCount: (playerId, startDate, endDate) =>
    apiClient.get(`/matches/player/${playerId}/period/count`, {
      params: { startDate, endDate },
    }),

  // 試合統計取得
  getStatistics: (playerId) =>
    apiClient.get(`/matches/player/${playerId}/statistics`),

  // 級別統計取得
  getStatisticsByRank: (playerId, params = {}) =>
    apiClient.get(`/matches/player/${playerId}/statistics-by-rank`, { params }),

  // 選手ID・日付・試合番号で試合結果を取得
  getByPlayerDateAndMatchNumber: (playerId, matchDate, matchNumber) =>
    apiClient.get(`/matches/player/${playerId}/date/${matchDate}/match/${matchNumber}`),

  // 日付で試合結果を取得
  getByDate: (date) =>
    apiClient.get('/matches', { params: { date } }),
};
