import apiClient from './client.js';

const kaderuAPI = {
  openReserve: (roomName, date, slotIndex = 2) =>
    apiClient.post('/kaderu/open-reserve', null, {
      params: { roomName, date, slotIndex },
    }),
};

export default kaderuAPI;
