import apiClient from './client';

export const systemSettingsAPI = {
  getAll: () =>
    apiClient.get('/system-settings'),

  update: (key, value, organizationId) =>
    apiClient.put(`/system-settings/${key}`, { value, ...(organizationId && { organizationId: String(organizationId) }) }),

  getDeadline: (year, month) =>
    apiClient.get('/lottery/deadline', { params: { year, month } }),
};
