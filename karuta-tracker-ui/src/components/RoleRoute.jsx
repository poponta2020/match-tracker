import { Navigate } from 'react-router-dom';
import { isAdmin, isSuperAdmin } from '../utils/auth';

/**
 * ロールベースのルート保護コンポーネント。
 * PrivateRoute（ログインチェック）の内側で使用する。
 *
 * @param {string} requiredRole - "ADMIN" or "SUPER_ADMIN"
 * @param {React.ReactNode} children - 表示するコンポーネント
 */
const RoleRoute = ({ requiredRole, children }) => {
  const hasAccess =
    requiredRole === 'SUPER_ADMIN' ? isSuperAdmin() :
    requiredRole === 'ADMIN' ? isAdmin() :
    true;

  if (!hasAccess) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default RoleRoute;
