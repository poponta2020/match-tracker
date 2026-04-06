import apiClient from './client';

export const pairingAPI = {
  // 指定日の対戦組み合わせを取得
  getByDate: (date, params = {}) =>
    apiClient.get('/match-pairings/date', {
      params: { date, ...params },
    }),

  // 指定日・試合番号の対戦組み合わせを取得
  getByDateAndMatchNumber: (date, matchNumber) =>
    apiClient.get('/match-pairings/date-and-match', {
      params: { date, matchNumber },
    }),

  // 対戦組み合わせが存在するか確認
  exists: (date, matchNumber) =>
    apiClient.get('/match-pairings/exists', {
      params: { date, matchNumber },
    }),

  // 対戦組み合わせを作成
  create: (data) => apiClient.post('/match-pairings', data),

  // 対戦組み合わせを一括作成
  createBatch: (date, matchNumber, pairings, waitingPlayerIds = []) =>
    apiClient.post('/match-pairings/batch', { pairings, waitingPlayerIds }, {
      params: { date, matchNumber },
    }),

  // 対戦組み合わせの選手を変更
  updatePlayer: (id, newPlayerId, side) =>
    apiClient.put(`/match-pairings/${id}/player`, null, {
      params: { newPlayerId, side },
    }),

  // 対戦組み合わせを削除
  delete: (id) => apiClient.delete(`/match-pairings/${id}`),

  // 指定日・試合番号の対戦組み合わせを削除
  deleteByDateAndMatchNumber: (date, matchNumber) =>
    apiClient.delete('/match-pairings/date-and-match', {
      params: { date, matchNumber },
    }),

  // 自動マッチングを実行
  autoMatch: (data) => apiClient.post('/match-pairings/auto-match', data),

  // ペアの直近対戦履歴を取得
  getPairHistory: (player1Id, player2Id, sessionDate, matchNumber) =>
    apiClient.get('/match-pairings/pair-history', {
      params: { player1Id, player2Id, sessionDate, matchNumber },
    }),

  // ペアリングと対応する試合結果を同時に削除（リセット）
  resetWithResult: (id) =>
    apiClient.delete(`/match-pairings/${id}/with-result`),
};
