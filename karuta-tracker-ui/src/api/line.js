import apiClient from './client';

export const lineAPI = {
  // LINE通知を有効化する（用途別）
  enable: (playerId, channelType = 'PLAYER') =>
    apiClient.post(`/line/${channelType}/enable`, { playerId }),

  // LINE通知を無効化する（用途別）
  disable: (playerId, channelType = 'PLAYER') =>
    apiClient.delete(`/line/${channelType}/disable`, { data: { playerId } }),

  // ワンタイムコードを再発行する（用途別）
  reissueCode: (playerId, channelType = 'PLAYER') =>
    apiClient.post(`/line/${channelType}/reissue-code`, { playerId }),

  // LINE連携状態を取得する（用途別）
  getStatus: (playerId, channelType = 'PLAYER') =>
    apiClient.get(`/line/${channelType}/status`, { params: { playerId } }),

  // 通知設定を取得する
  getPreferences: (playerId) =>
    apiClient.get('/line/preferences', { params: { playerId } }),

  // 通知設定を更新する
  updatePreferences: (data) =>
    apiClient.put('/line/preferences', data),

  // === 管理者向け ===

  // チャネル一覧を取得する（用途別フィルタ対応）
  getChannels: (channelType) =>
    apiClient.get('/admin/line/channels', { params: channelType ? { channelType } : {} }),

  // チャネルを登録する
  createChannel: (data) =>
    apiClient.post('/admin/line/channels', data),

  // チャネルを一括登録する
  importChannels: (channels) =>
    apiClient.post('/admin/line/channels/import', channels),

  // チャネルを無効化する
  disableChannel: (channelId) =>
    apiClient.put(`/admin/line/channels/${channelId}/disable`),

  // チャネルを有効化する
  enableChannel: (channelId) =>
    apiClient.put(`/admin/line/channels/${channelId}/enable`),

  // チャネルの強制割り当て解除
  forceReleaseChannel: (channelId) =>
    apiClient.delete(`/admin/line/channels/${channelId}/assignment`),

  // 対戦組み合わせをLINE送信する
  sendMatchPairing: (sessionId) =>
    apiClient.post('/admin/line/send/match-pairing', { sessionId }),

  // スケジュール設定を取得する
  getScheduleSettings: () =>
    apiClient.get('/admin/line/schedule-settings'),

  // スケジュール設定を更新する
  updateScheduleSettings: (data) =>
    apiClient.put('/admin/line/schedule-settings', data),
};
