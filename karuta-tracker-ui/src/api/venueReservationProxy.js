import apiClient from './client';

const venueReservationProxyAPI = {
  createSession: ({ venue, practiceSessionId, roomName, date, slotIndex, returnUrl }) =>
    apiClient.post('/venue-reservation-proxy/session', {
      venue,
      practiceSessionId,
      roomName,
      date,
      slotIndex,
      returnUrl,
    }),
};

export default venueReservationProxyAPI;
