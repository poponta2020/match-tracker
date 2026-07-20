import { createContext, useContext, useState, useEffect } from 'react';
import { playerAPI } from '../api';

const AuthContext = createContext(null);

// eslint-disable-next-line react-refresh/only-export-components -- 28箇所から参照されるフックの分離は影響範囲が大きいため見送り
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

      // firstLoginフラグはログイン判定用のみ、tokenはauthToken専用のため、いずれもcurrentPlayerには保存しない
      const { firstLogin: _firstLogin, token, ...playerData } = player;
      setCurrentPlayer(playerData);
      localStorage.setItem('currentPlayer', JSON.stringify(playerData));
      localStorage.setItem('authToken', token);
      return player;
    } catch (error) {
      if (error.response?.data?.message) {
        throw new Error(error.response.data.message);
      }
      throw new Error('ログインに失敗しました');
    }
  };

  const logout = async () => {
    try {
      // サーバ側のトークンを失効させる。失敗してもクライアント側のログアウトは必ず完了させる
      await playerAPI.logout();
    } catch (error) {
      console.error('Failed to call logout API', error);
    }
    setCurrentPlayer(null);
    localStorage.removeItem('currentPlayer');
    localStorage.removeItem('authToken');
  };

  const updateCurrentPlayer = (playerData) => {
    setCurrentPlayer(playerData);
    localStorage.setItem('currentPlayer', JSON.stringify(playerData));
  };

  // register はここには置かない。
  // 認証トークンを発行できるのは POST /api/players/login だけで、
  // 選手作成 (POST /api/players) は PlayerDto を返しトークンを持たない。
  // 以前ここにあった register は authToken に 'dummy-token' を書いてログイン状態を
  // 偽装していたため、トークン認証化に伴い削除した（呼び出し元は無かった）。
  // 招待リンクからの登録は inviteAPI.register → login() の経路で正しくトークンを得る。
  const value = {
    currentPlayer,
    loading,
    login,
    logout,
    updateCurrentPlayer,
    isAuthenticated: !!currentPlayer,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
