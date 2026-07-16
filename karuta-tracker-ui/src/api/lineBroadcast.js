import apiClient from './client';

export const lineBroadcastAPI = {
  // 配信グループ一覧を取得する
  getGroups: () => apiClient.get('/admin/line/broadcast/groups'),

  // 配信グループを作成する
  createGroup: (data) => apiClient.post('/admin/line/broadcast/groups', data),

  // 配信グループを更新する（部分更新）
  updateGroup: (groupId, data) =>
    apiClient.put(`/admin/line/broadcast/groups/${groupId}`, data),

  // bot を配信グループに割り当てる
  assignBot: (groupId, channelId) =>
    apiClient.post(`/admin/line/broadcast/groups/${groupId}/bots`, { channelId }),

  // bot の割り当てを解除する
  unassignBot: (groupId, channelId) =>
    apiClient.delete(`/admin/line/broadcast/groups/${groupId}/bots/${channelId}`),

  // 配信グループの稼働状況を取得する
  getStatus: (groupId) =>
    apiClient.get(`/admin/line/broadcast/groups/${groupId}/status`),

  // 配信ログを取得する
  getLogs: (groupId) =>
    apiClient.get(`/admin/line/broadcast/groups/${groupId}/logs`),
};
