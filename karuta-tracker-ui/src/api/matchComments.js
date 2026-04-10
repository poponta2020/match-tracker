import apiClient from './client';

export const matchCommentsAPI = {
  getComments: (matchId, menteeId) =>
    apiClient.get(`/matches/${matchId}/comments`, { params: { menteeId } }),

  createComment: (matchId, menteeId, content) =>
    apiClient.post(`/matches/${matchId}/comments`, { menteeId, content }),

  updateComment: (matchId, commentId, content) =>
    apiClient.put(`/matches/${matchId}/comments/${commentId}`, { content }),

  deleteComment: (matchId, commentId) =>
    apiClient.delete(`/matches/${matchId}/comments/${commentId}`),

  sendNotification: (matchId, menteeId) =>
    apiClient.post(`/matches/${matchId}/comments/notify`, null, { params: { menteeId } }),
};
