import apiClient from './client';

export const venueAPI = {
  getAll: () => apiClient.get('/venues'),
  getById: (id) => apiClient.get(`/venues/${id}`),
  create: (data) => apiClient.post('/venues', data),
  update: (id, data) => apiClient.put(`/venues/${id}`, data),
  delete: (id) => apiClient.delete(`/venues/${id}`),
};
