import apiClient from './client';

export const cardDivisionAPI = {
  // 札分けテキスト・購読状態を取得する
  getCardDivision: (playerId, organizationId, date) =>
    apiClient.get('/card-division', {
      params: { playerId, organizationId, ...(date ? { date } : {}) },
    }),

  // 札分けリマインダーのLINE購読設定を更新する
  updateSubscription: ({ playerId, organizationId, enabled }) =>
    apiClient.put('/card-division/subscription', { playerId, organizationId, enabled }),
};
