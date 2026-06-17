import apiClient from './client';

/**
 * 試合動画 API クライアント
 *
 * 既存 API クライアント（matches.js / players.js 等）と同じく
 * apiClient（axios 共通設定）を介してリクエストする。
 *
 * 倉庫・試合詳細など複数画面から使い回すため、
 * オブジェクト形式（matchVideoAPI）と関数形式の両方をエクスポートする。
 */
export const matchVideoAPI = {
  // 動画登録: POST /api/match-videos
  // data: { matchDate, matchNumber, player1Id, player2Id, videoUrl }
  register: (data) => apiClient.post('/match-videos', data),

  // 動画URL差し替え: PUT /api/match-videos/{id}
  // data: { videoUrl }
  update: (id, data) => apiClient.put(`/match-videos/${id}`, data),

  // 動画紐付け削除: DELETE /api/match-videos/{id}
  remove: (id) => apiClient.delete(`/match-videos/${id}`),

  // 指定日の動画一覧取得: GET /api/match-videos?date=YYYY-MM-DD
  getByDate: (date) => apiClient.get('/match-videos', { params: { date } }),

  // 指定日の動画登録候補取得: GET /api/match-videos/date-candidates?date=YYYY-MM-DD
  // 参加日スコープなし（全ロール）・組織スコープあり。
  // レスポンス（matchNumber 昇順）: 各要素は
  //   { matchDate, matchNumber, player1Id, player1Name, player2Id, player2Name,
  //     hasResult, matchId, registered }
  getDateCandidates: (date) =>
    apiClient.get('/match-videos/date-candidates', { params: { date } }),

  // 動画倉庫検索（ページング）: GET /api/match-videos/search
  // params: { playerId, year, month, mine, page, size }
  search: (params) => apiClient.get('/match-videos/search', { params }),
};

export const registerMatchVideo = (data) => matchVideoAPI.register(data);
export const updateMatchVideo = (id, data) => matchVideoAPI.update(id, data);
export const removeMatchVideo = (id) => matchVideoAPI.remove(id);
export const getMatchVideosByDate = (date) => matchVideoAPI.getByDate(date);
export const getMatchVideoDateCandidates = (date) => matchVideoAPI.getDateCandidates(date);
export const searchMatchVideos = (params) => matchVideoAPI.search(params);
