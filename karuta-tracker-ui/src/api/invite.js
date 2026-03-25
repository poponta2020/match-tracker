import apiClient from './client';

export const inviteAPI = {
  // 招待トークン生成（ADMIN以上）
  createToken: (type, createdBy) =>
    apiClient.post(`/invite-tokens?type=${type}&createdBy=${createdBy}`),

  // トークン検証（認証不要）
  validateToken: (token) =>
    apiClient.get(`/invite-tokens/validate/${token}`),

  // トークンを使った公開登録（認証不要）
  register: (data) =>
    apiClient.post('/invite-tokens/register', data),
};
