import apiClient from './client';

export const homeAPI = {
  getData: (playerId) => apiClient.get('/home', { params: { playerId } }),
};
