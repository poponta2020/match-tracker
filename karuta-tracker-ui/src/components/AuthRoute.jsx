import { useAuth } from '../context/AuthContext';

const AuthRoute = ({ authenticated, unauthenticated }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#4a6b5a]"></div>
      </div>
    );
  }

  return isAuthenticated ? authenticated : unauthenticated;
};

export default AuthRoute;
