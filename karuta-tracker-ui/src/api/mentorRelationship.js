import apiClient from './client';

export const mentorRelationshipAPI = {
  create: (mentorId, organizationId) =>
    apiClient.post('/mentor-relationships', { mentorId, organizationId }),

  approve: (id) =>
    apiClient.put(`/mentor-relationships/${id}/approve`),

  reject: (id) =>
    apiClient.put(`/mentor-relationships/${id}/reject`),

  delete: (id) =>
    apiClient.delete(`/mentor-relationships/${id}`),

  getMyMentors: () =>
    apiClient.get('/mentor-relationships/my-mentors'),

  getMyMentees: () =>
    apiClient.get('/mentor-relationships/my-mentees'),

  getPending: () =>
    apiClient.get('/mentor-relationships/pending'),
};
