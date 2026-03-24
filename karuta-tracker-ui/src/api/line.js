import apiClient from './client';

export const lineAPI = {
  // ========== ユーザー向け ==========

  // LINE通知を有効化
  enable: (playerId) =>
    apiClient.post('/line/enable', { playerId }),

  // LINE通知を無効化
  disable: (playerId) =>
    apiClient.delete('/line/disable', { params: { playerId } }),

  // LINE連携状態を取得
  getStatus: (playerId) =>
    apiClient.get('/line/status', { params: { playerId } }),

  // 通知設定を取得
  getPreferences: (playerId) =>
    apiClient.get('/line/preferences', { params: { playerId } }),

  // 通知設定を更新
  updatePreferences: (data) =>
    apiClient.put('/line/preferences', data),

  // ========== 管理者向け ==========

  // チャネル一覧取得
  getChannels: () =>
    apiClient.get('/admin/line/channels'),

  // チャネル個別登録
  createChannel: (data) =>
    apiClient.post('/admin/line/channels', data),

  // チャネル一括登録
  importChannels: (data) =>
    apiClient.post('/admin/line/channels/import', data),

  // チャネル無効化
  disableChannel: (id) =>
    apiClient.put(`/admin/line/channels/${id}/disable`),

  // チャネル有効化
  enableChannel: (id) =>
    apiClient.put(`/admin/line/channels/${id}/enable`),

  // チャネル強制割り当て解除
  releaseChannel: (id) =>
    apiClient.put(`/admin/line/channels/${id}/release`),

  // 抽選結果LINE送信
  sendLotteryResult: (year, month) =>
    apiClient.post('/admin/line/send/lottery-result', { year, month }),

  // 対戦組み合わせLINE送信
  sendMatchPairing: (sessionId) =>
    apiClient.post('/admin/line/send/match-pairing', { sessionId }),

  // スケジュール設定取得
  getScheduleSettings: () =>
    apiClient.get('/admin/line/schedule-settings'),

  // スケジュール設定更新
  updateScheduleSetting: (data) =>
    apiClient.put('/admin/line/schedule-settings', data),
};
