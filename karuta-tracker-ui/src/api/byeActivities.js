import apiClient from './client';

export const byeActivityAPI = {
  getByDate: (date, matchNumber) => {
    const params = { date };
    if (matchNumber != null) params.matchNumber = matchNumber;
    return apiClient.get('/bye-activities', { params });
  },

  getByPlayer: (playerId, type) => {
    const params = {};
    if (type) params.type = type;
    return apiClient.get(`/bye-activities/player/${playerId}`, { params });
  },

  create: (data) =>
    apiClient.post('/bye-activities', data),

  createBatch: (date, matchNumber, items) =>
    apiClient.post('/bye-activities/batch', items, { params: { date, matchNumber } }),

  update: (id, data) =>
    apiClient.put(`/bye-activities/${id}`, data),

  delete: (id) =>
    apiClient.delete(`/bye-activities/${id}`),
};
