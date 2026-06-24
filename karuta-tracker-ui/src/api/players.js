import apiClient from './client';

export const playerAPI = {
  // 選手登録
  register: (data) => apiClient.post('/players', data),

  // 選手一覧取得
  getAll: () => apiClient.get('/players'),

  // 選手詳細取得
  getById: (id) => apiClient.get(`/players/${id}`),

  // 選手更新
  update: (id, data) => apiClient.put(`/players/${id}`, data),

  // 選手一括更新（性別・級・段位・かるた会の上書き＋所属練習会の追加）
  bulkUpdate: (updates) => apiClient.put('/players/bulk', { updates }),

  // 選手削除
  delete: (id) => apiClient.delete(`/players/${id}`),

  // 選手検索
  search: (params) => apiClient.get('/players/search', { params }),

  // ログイン（仮実装 - 後でSpring Securityと連携）
  login: (name, password) =>
    apiClient.post('/players/login', { name, password }),

  // プロフィール取得
  getProfile: (id) => apiClient.get(`/players/${id}/profile`),

  // プロフィール作成
  createProfile: (id, data) => apiClient.post(`/players/${id}/profile`, data),

  // プロフィール更新
  updateProfile: (id, data) => apiClient.put(`/players/${id}/profile`, data),

  // ロール更新
  updateRole: (id, role) => apiClient.put(`/players/${id}/role?role=${role}`),
};
