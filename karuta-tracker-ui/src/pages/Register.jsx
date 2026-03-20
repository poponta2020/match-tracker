import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { User, Lock, AlertCircle } from 'lucide-react';

const Register = () => {
  const [formData, setFormData] = useState({
    name: '',
    password: '',
    confirmPassword: '',
    gender: '男性',
    dominantHand: '右',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { register } = useAuth();

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (formData.password !== formData.confirmPassword) {
      setError('パスワードが一致しません');
      return;
    }

    if (formData.password.length < 6) {
      setError('パスワードは6文字以上で入力してください');
      return;
    }

    setLoading(true);

    try {
      const { confirmPassword, ...registerData } = formData;
      await register(registerData);
      navigate('/');
    } catch (err) {
      setError(
        err.response?.data?.message || '登録に失敗しました。選手名が既に使用されている可能性があります。'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="max-w-md w-full">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-primary mb-2">
            わすらログ
          </h1>
          <p className="text-text-muted">試合・練習記録管理システム</p>
        </div>

        <div className="bg-surface rounded-lg shadow-xl p-8">
          <h2 className="text-2xl font-bold text-text mb-6">選手登録</h2>

          {error && (
            <div className="mb-4 p-3 bg-status-danger-surface border border-status-danger/20 rounded-lg flex items-center gap-2 text-status-danger">
              <AlertCircle className="w-5 h-5" />
              <span className="text-sm">{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text mb-1">
                選手名
              </label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 transform -translate-y-1/2 text-text-placeholder w-5 h-5" />
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
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
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-text-placeholder w-5 h-5" />
                <input
                  type="password"
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  className="w-full pl-10 pr-4 py-2 border border-border-strong rounded-lg focus:ring-2 focus:ring-focus focus:border-transparent"
                  placeholder="6文字以上"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-text mb-1">
                パスワード（確認）
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-text-placeholder w-5 h-5" />
                <input
                  type="password"
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  className="w-full pl-10 pr-4 py-2 border border-border-strong rounded-lg focus:ring-2 focus:ring-focus focus:border-transparent"
                  placeholder="パスワードを再入力"
                  required
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-text mb-1">
                  性別
                </label>
                <select
                  name="gender"
                  value={formData.gender}
                  onChange={handleChange}
                  className="w-full px-4 py-2 border border-border-strong rounded-lg focus:ring-2 focus:ring-focus focus:border-transparent"
                  required
                >
                  <option value="男性">男性</option>
                  <option value="女性">女性</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-text mb-1">
                  利き手
                </label>
                <select
                  name="dominantHand"
                  value={formData.dominantHand}
                  onChange={handleChange}
                  className="w-full px-4 py-2 border border-border-strong rounded-lg focus:ring-2 focus:ring-focus focus:border-transparent"
                  required
                >
                  <option value="右">右</option>
                  <option value="左">左</option>
                </select>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-primary text-text-inverse py-2 px-4 rounded-lg hover:bg-primary-hover transition-colors disabled:bg-surface-disabled disabled:text-text-disabled disabled:cursor-not-allowed font-medium"
            >
              {loading ? '登録中...' : '登録'}
            </button>
          </form>

          <div className="mt-6 text-center">
            <p className="text-sm text-text-muted">
              すでにアカウントをお持ちの方は
              <Link
                to="/login"
                className="text-secondary hover:text-secondary-hover font-medium ml-1 underline"
              >
                ログイン
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;
