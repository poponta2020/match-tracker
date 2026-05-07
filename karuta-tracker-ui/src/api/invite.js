import apiClient from './client';

export const inviteAPI = {
  // 招待トークン生成（ADMIN以上）
  // organizationId: SUPER_ADMIN は必須、ADMIN は無視される（自団体固定）
  createToken: (type, createdBy, organizationId) => {
    const params = new URLSearchParams({ type, createdBy });
    if (organizationId != null) params.set('organizationId', organizationId);
    return apiClient.post(`/invite-tokens?${params.toString()}`);
  },

  // トークン検証（認証不要）
  validateToken: (token) =>
    apiClient.get(`/invite-tokens/validate/${token}`),

  // トークンを使った公開登録（認証不要）
  register: (data) =>
    apiClient.post('/invite-tokens/register', data),
};
