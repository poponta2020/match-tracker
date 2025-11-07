import apiClient from './client';

export const pairingAPI = {
  // 対戦組み合わせ生成
  generate: (data) => apiClient.post('/match-pairings/generate', data),

  // 対戦組み合わせ一覧取得
  getAll: () => apiClient.get('/match-pairings'),

  // 特定日の対戦組み合わせ取得
  getByDate: (date) =>
    apiClient.get('/match-pairings/date', { params: { date } }),

  // 対戦組み合わせ詳細取得
  getById: (id) => apiClient.get(`/match-pairings/${id}`),

  // 対戦組み合わせ削除
  delete: (id) => apiClient.delete(`/match-pairings/${id}`),

  // 選手の過去対戦履歴取得
  getPlayerHistory: (playerId) =>
    apiClient.get(`/match-pairings/player/${playerId}/history`),
};
