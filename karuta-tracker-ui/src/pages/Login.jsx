import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { User, Lock, AlertCircle } from 'lucide-react';

const Login = () => {
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const player = await login(name, password);
      if (player.firstLogin) {
        navigate('/profile/edit?setup=true');
      } else {
        navigate('/');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'ログインに失敗しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="max-w-md w-full">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-primary mb-2">
            わすらログ（仮）
          </h1>
        </div>

        <div className="bg-surface rounded-lg shadow-md p-8">
          <h2 className="text-2xl font-bold text-text mb-6">ログイン</h2>

          {error && (
            <div className="mb-4 p-3 bg-status-danger-surface border border-status-danger/20 rounded-lg flex items-center gap-2 text-status-danger">
              <AlertCircle className="w-5 h-5" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text mb-1">
                選手名
              </label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 transform -translate-y-1/2 text-text-muted w-5 h-5" />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 border border-border-strong rounded-lg focus:ring-2 focus:ring-focus focus:border-transparent"
                  placeholder="選手名を入力"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-text mb-1">
                パスワード
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-text-muted w-5 h-5" />
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 border border-border-strong rounded-lg focus:ring-2 focus:ring-focus focus:border-transparent"
                  placeholder="パスワードを入力"
                  required
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primary text-text-inverse py-2 px-4 rounded-lg hover:bg-primary-hover transition-colors disabled:bg-surface-disabled disabled:text-text-disabled disabled:cursor-not-allowed font-medium"
            >
              {loading ? 'ログイン中...' : 'ログイン'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Login;
