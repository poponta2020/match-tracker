import apiClient from './client';

export const practiceAPI = {
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

  // 年月で練習セッションサマリーを取得（カレンダー用・軽量）
  getSessionSummaries: (year, month) =>
    apiClient.get('/practice-sessions/year-month/summary', {
      params: { year, month },
    }),

  // 選手の練習参加を一括登録
  registerParticipations: (data) =>
    apiClient.post('/practice-sessions/participations', data),

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

  // 選手の参加状況を取得（抽選ステータス付き）
  getPlayerParticipationStatus: (playerId, year, month) =>
    apiClient.get(`/practice-sessions/participations/player/${playerId}/status`, {
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

  // 伝助URL取得（月別・団体別）
  getDensukeUrl: (year, month, organizationId) =>
    apiClient.get('/practice-sessions/densuke-url', { params: { year, month, organizationId } }),

  // 伝助URL登録/更新
  saveDensukeUrl: (year, month, url, organizationId) =>
    apiClient.put('/practice-sessions/densuke-url', { year, month, url, organizationId }),

  // 伝助URL削除（作り直し用途: densuke.biz 側の既存ページは残存する）
  deleteDensukeUrl: (year, month, organizationId) =>
    apiClient.delete('/practice-sessions/densuke-url', { params: { year, month, organizationId } }),

  // 伝助同期（月・団体指定）
  syncDensuke: (year, month, organizationId) =>
    apiClient.post('/practice-sessions/sync-densuke', { year, month, organizationId }),

  // 未登録者を一括登録して再同期
  registerAndSyncDensuke: (names, year, month, organizationId) =>
    apiClient.post('/practice-sessions/register-and-sync-densuke', { names, year, month, organizationId }),

  // 特定の試合から参加者を削除
  removeParticipantFromMatch: (sessionId, matchNumber, playerId) =>
    apiClient.delete(`/practice-sessions/${sessionId}/matches/${matchNumber}/participants/${playerId}`),

  // 月別参加率TOP3を取得
  getParticipationRateTop3: (year, month) =>
    apiClient.get('/practice-sessions/participation-rate-top3', {
      params: { year, month },
    }),

  // 伝助への書き込み状況を取得（団体別）
  getDensukeWriteStatus: (organizationId) =>
    apiClient.get('/practice-sessions/densuke-write-status', { params: { organizationId } }),

  // 伝助ページを自動作成
  createDensukePage: (year, month, organizationId, overrides = {}) =>
    apiClient.post('/practice-sessions/densuke/create-page', {
      year,
      month,
      organizationId,
      overrides,
    }),

  // 隣室予約完了を記録
  confirmReservation: (sessionId) =>
    apiClient.post(`/practice-sessions/${sessionId}/confirm-reservation`),

  // 会場を拡張（隣室と合わせた大部屋に変更）
  expandVenue: (sessionId) =>
    apiClient.post(`/practice-sessions/${sessionId}/expand-venue`),
};
