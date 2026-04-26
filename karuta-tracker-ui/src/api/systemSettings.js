import apiClient from './client';

export const systemSettingsAPI = {
  getAll: (organizationId) =>
    apiClient.get('/system-settings', {
      params: organizationId ? { organizationId } : {},
    }),

  update: (key, value, organizationId) =>
    apiClient.put(`/system-settings/${key}`, { value, ...(organizationId && { organizationId: String(organizationId) }) }),

  getDeadline: (year, month) =>
    apiClient.get('/lottery/deadline', { params: { year, month } }),
};
