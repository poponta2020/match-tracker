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

  // 日付で練習記録取得
  getByDate: (date) =>
    apiClient.get('/practice-sessions/date', {
      params: { date },
    }),

  // 練習セッションの参加者取得
  getParticipants: (id) =>
    apiClient.get(`/practice-sessions/${id}/participants`),

  // 年月で練習セッションを取得
  getByYearMonth: (year, month) =>
    apiClient.get('/practice-sessions/year-month', {
      params: { year, month },
    }),

  // 選手の練習参加を一括登録
  registerParticipations: (data) =>
    apiClient.post('/practice-sessions/participations', data),

  // 指定日以降の練習セッションを取得（エンリッチメントなし・高速）
  getUpcoming: (fromDate) =>
    apiClient.get('/practice-sessions/upcoming', {
      params: { fromDate },
    }),

  // 次の参加予定練習を取得（ホーム画面用・軽量）
  getNextParticipation: (playerId) =>
    apiClient.get('/practice-sessions/next-participation', {
      params: { playerId },
    }),

  // 指定日以降の練習日の日付リストのみ取得（軽量）
  getDates: (fromDate) =>
    apiClient.get('/practice-sessions/dates', {
      params: { fromDate },
    }),

  // 選手の特定月の参加状況を取得
  getPlayerParticipations: (playerId, year, month) =>
    apiClient.get(`/practice-sessions/participations/player/${playerId}`, {
      params: { year, month },
    }),

  // 試合別参加者を設定（管理者のみ）
  setMatchParticipants: (sessionId, matchNumber, playerIds) =>
    apiClient.put(`/practice-sessions/${sessionId}/matches/${matchNumber}/participants`, {
      playerIds,
    }),

  // 特定の試合に参加者を1名追加
  addParticipantToMatch: (date, matchNumber, playerId) =>
    apiClient.post(`/practice-sessions/date/${date}/matches/${matchNumber}/participants/${playerId}`),
};
