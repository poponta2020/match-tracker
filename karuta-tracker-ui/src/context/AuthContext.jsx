import { createContext, useContext, useState, useEffect } from 'react';
import { playerAPI } from '../api';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [currentPlayer, setCurrentPlayer] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // ローカルストレージから認証情報を復元
    const savedPlayer = localStorage.getItem('currentPlayer');
    if (savedPlayer) {
      try {
        setCurrentPlayer(JSON.parse(savedPlayer));
      } catch (error) {
        console.error('Failed to parse saved player data:', error);
        // 破損したデータを削除
        localStorage.removeItem('currentPlayer');
        localStorage.removeItem('authToken');
      }
    }
    setLoading(false);
  }, []);

  const login = async (name, password) => {
    try {
      // ログインエンドポイントを使用
      const response = await playerAPI.login(name, password);
      const player = response.data;

      setCurrentPlayer(player);
      localStorage.setItem('currentPlayer', JSON.stringify(player));
      localStorage.setItem('authToken', 'dummy-token'); // TODO: 実際のトークンに置き換え
      return player;
    } catch (error) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('ログインに失敗しました');
    }
  };

  const logout = () => {
    setCurrentPlayer(null);
    localStorage.removeItem('currentPlayer');
    localStorage.removeItem('authToken');
  };

  const register = async (playerData) => {
    try {
      const response = await playerAPI.register(playerData);
      const newPlayer = response.data;
      setCurrentPlayer(newPlayer);
      localStorage.setItem('currentPlayer', JSON.stringify(newPlayer));
      localStorage.setItem('authToken', 'dummy-token'); // TODO: 実際のトークンに置き換え
      return newPlayer;
    } catch (error) {
      throw error;
    }
  };

  const value = {
    currentPlayer,
    loading,
    login,
    logout,
    register,
    isAuthenticated: !!currentPlayer,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
