import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const PrivateRoute = ({ children }) => {
  const { currentPlayer, isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // プロフィール未設定チェック（kyuRankがnullなら未設定 = 初ログイン後にプロフィール未入力）
  // /profile/edit 以外のページにアクセスしようとした場合はリダイレクト
  if (!currentPlayer?.kyuRank && !location.pathname.startsWith('/profile')) {
    return <Navigate to="/profile/edit?setup=true" replace />;
  }

  return children;
};

export default PrivateRoute;
