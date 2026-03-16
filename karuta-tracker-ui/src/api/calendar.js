import apiClient from './client';

export const calendarAPI = {
  /**
   * Google Calendarと同期
   * @param {string} accessToken - Google OAuth2アクセストークン
   * @param {number} playerId - プレイヤーID
   */
  sync: (accessToken, playerId) =>
    apiClient.post('/google-calendar/sync', { accessToken, playerId }),
};
