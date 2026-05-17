import apiClient from './client';

export const icalCalendarAPI = {
  // 自分のフィードURL+所属団体・表示名一覧を取得
  getFeedInfo: () =>
    apiClient.get('/calendar/feed/info'),

  // フィードトークンを再発行
  regenerateFeed: () =>
    apiClient.post('/calendar/feed/regenerate'),

  // 団体ごとの表示名を一括更新
  // displayNames: { [organizationId]: 表示名 or null }
  updateDisplayNames: (displayNames) =>
    apiClient.patch('/calendar/feed/display-names', { displayNames }),
};
