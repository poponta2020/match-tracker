import apiClient from './client';

export const kaderuSyncAPI = {
  // 手動同期トリガー（ADMIN+ 限定）。ADMIN は organizationId 省略可、SUPER_ADMIN は必須
  trigger: (organizationId) =>
    apiClient.post('/kaderu-sync/trigger', { organizationId }),

  // 進行中（PENDING）のイベント取得。なければ pendingEvent: null が返る
  getStatus: (organizationId) =>
    apiClient.get('/kaderu-sync/status', { params: { organizationId } }),
};
