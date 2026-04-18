import apiClient from './client';

export const densukeTemplateAPI = {
  // テンプレート取得（未登録団体にはデフォルト値が返る）
  get: (organizationId) =>
    apiClient.get(`/densuke-templates/${organizationId}`),

  // テンプレート更新（upsert）
  update: (organizationId, data) =>
    apiClient.put(`/densuke-templates/${organizationId}`, data),
};
