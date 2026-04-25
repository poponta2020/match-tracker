import apiClient from './client';

const venueReservationProxyAPI = {
  createSession: ({ venue, practiceSessionId, roomName, date, slotIndex }) =>
    apiClient.post('/venue-reservation-proxy/session', {
      venue,
      practiceSessionId,
      roomName,
      date,
      slotIndex,
    }),
};

export default venueReservationProxyAPI;
