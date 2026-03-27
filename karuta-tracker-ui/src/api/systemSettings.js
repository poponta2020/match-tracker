import apiClient from './client';

export const systemSettingsAPI = {
  getAll: () =>
    apiClient.get('/system-settings'),

  update: (key, value) =>
    apiClient.put(`/system-settings/${key}`, { value }),

  getDeadline: (year, month) =>
    apiClient.get('/lottery/deadline', { params: { year, month } }),
};
