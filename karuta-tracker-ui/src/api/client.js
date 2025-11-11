import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// リクエストインターセプター（認証トークンの追加など）
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 現在のユーザーのロールをヘッダーに追加（簡易認証実装）
    const currentPlayer = localStorage.getItem('currentPlayer');
    if (currentPlayer) {
      try {
        const player = JSON.parse(currentPlayer);
        if (player.role) {
          config.headers['X-User-Role'] = player.role;
        }
      } catch (e) {
        console.error('Failed to parse currentPlayer from localStorage', e);
      }
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// レスポンスインターセプター（エラーハンドリング）
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 認証エラー時はログイン画面へ
      localStorage.removeItem('authToken');
      localStorage.removeItem('currentPlayer');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
