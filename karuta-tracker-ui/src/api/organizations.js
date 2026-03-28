import apiClient from './client';

export const organizationAPI = {
  // 団体一覧取得
  getAll: () =>
    apiClient.get('/organizations'),

  // ユーザーの参加団体一覧取得
  getPlayerOrganizations: (playerId) =>
    apiClient.get(`/organizations/players/${playerId}`),

  // ユーザーの参加団体更新
  updatePlayerOrganizations: (playerId, organizationIds) =>
    apiClient.put(`/organizations/players/${playerId}`, { organizationIds }),

  // ADMINの団体紐づけ変更（SUPER_ADMINのみ）
  updateAdminOrganization: (playerId, organizationId) =>
    apiClient.put(`/organizations/admin/${playerId}`, { organizationId }),
};
