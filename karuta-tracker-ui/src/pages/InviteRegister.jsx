import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { inviteAPI } from '../api/invite';
import { organizationAPI } from '../api/organizations';
import { User, Lock, AlertCircle, Eye, EyeOff } from 'lucide-react';
import LoadingScreen from '../components/LoadingScreen';

const InviteRegister = () => {
  const { token } = useParams();
  const navigate = useNavigate();
  const { login } = useAuth();

  const [validating, setValidating] = useState(true);
  const [tokenValid, setTokenValid] = useState(false);
  const [tokenInfo, setTokenInfo] = useState(null);
  const [orgName, setOrgName] = useState(null);

  const [formData, setFormData] = useState({
    name: '',
    password: '',
    confirmPassword: '',
    gender: '男性',
    dominantHand: '右',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // トークン検証
  useEffect(() => {
    const validate = async () => {
      try {
        const response = await inviteAPI.validateToken(token);
        setTokenInfo(response.data);
        setTokenValid(true);

        // 団体名を取得
        if (response.data?.organizationId) {
          try {
            const orgsRes = await organizationAPI.getAll();
            const org = (orgsRes.data || []).find(o => o.id === response.data.organizationId);
            if (org) setOrgName(org.name);
          } catch { /* ignore */ }
        }
      } catch {
        setTokenValid(false);
      } finally {
        setValidating(false);
      }
    };
    validate();
  }, [token]);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (formData.password.length < 8) {
      setError('パスワードは8文字以上で入力してください');
      return;
    }

    if (formData.password !== formData.confirmPassword) {
      setError('パスワードが一致しません');
      return;
    }

    setLoading(true);

    try {
      // 招待トークンで登録
      await inviteAPI.register({
        token,
        name: formData.name,
        password: formData.password,
        gender: formData.gender,
        dominantHand: formData.dominantHand,
      });

      // 登録後に自動ログイン
      await login(formData.name, formData.password);

      // プロフィール設定画面へ
      navigate('/profile/edit?setup=true');
    } catch (err) {
      const message = err.response?.data?.message || err.response?.data?.error || '';
      if (message.includes('already exists') || message.includes('重複')) {
        setError('この選手名は既に使用されています');
      } else if (message.includes('無効') || message.includes('期限切れ')) {
        setError('この招待リンクは無効または期限切れです');
      } else {
        setError('登録に失敗しました。もう一度お試しください。');
      }
    } finally {
      setLoading(false);
    }
  };

  if (validating) {
    return <LoadingScreen />;
  }

  // トークンが無効な場合
  if (!tokenValid) {
    return (
      <div className="min-h-screen bg-[#f2ede6] flex items-center justify-center p-4">
        <div className="max-w-md w-full">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold text-[#4a6b5a] mb-2">
              わすらログ（仮）
            </h1>
          </div>
          <div className="bg-[#f9f6f2] rounded-lg shadow-md p-8 text-center">
            <AlertCircle className="w-12 h-12 text-red-400 mx-auto mb-4" />
            <h2 className="text-xl font-bold text-[#374151] mb-2">
              招待リンクが無効です
            </h2>
            <p className="text-[#6b7280] text-sm mb-6">
              このリンクは期限切れまたは既に使用済みです。<br />
              管理者に新しいリンクを発行してもらってください。
            </p>
            <Link
              to="/login"
              className="text-[#4a6b5a] hover:text-[#3d5a4c] font-medium text-sm"
            >
              ログインページへ
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f2ede6] flex items-center justify-center p-4">
      <div className="max-w-md w-full">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-[#4a6b5a] mb-2">
            わすらログ（仮）
          </h1>
        </div>

        <div className="bg-[#f9f6f2] rounded-lg shadow-md p-8">
          <h2 className="text-2xl font-bold text-[#374151] mb-2">選手登録</h2>
          <p className="text-[#6b7280] text-sm mb-6">
            {orgName
              ? `${orgName}の練習会への登録`
              : tokenInfo?.type === 'SINGLE_USE'
                ? 'あなた専用の招待リンクです'
                : '招待リンクから登録'}
          </p>

          {error && (
            <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg flex items-center gap-2 text-red-700">
              <AlertCircle className="w-5 h-5 flex-shrink-0" />
              <span className="text-sm">{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-[#374151] mb-1">
                選手名
              </label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 transform -translate-y-1/2 text-[#6b7280] w-5 h-5" />
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  className="w-full pl-10 pr-4 py-2 border border-[#d4ddd7] rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                  placeholder="選手名を入力"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#374151] mb-1">
                パスワード
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-[#6b7280] w-5 h-5" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  name="password"
                  value={formData.password}
                  onChange={handleChange}
                  className="w-full pl-10 pr-10 py-2 border border-[#d4ddd7] rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                  placeholder="8文字以上"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[#6b7280] hover:text-[#374151]"
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[#374151] mb-1">
                パスワード（確認）
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 transform -translate-y-1/2 text-[#6b7280] w-5 h-5" />
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  name="confirmPassword"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  className="w-full pl-10 pr-10 py-2 border border-[#d4ddd7] rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                  placeholder="パスワードを再入力"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[#6b7280] hover:text-[#374151]"
                >
                  {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-[#374151] mb-1">
                  性別
                </label>
                <select
                  name="gender"
                  value={formData.gender}
                  onChange={handleChange}
                  className="w-full px-4 py-2 border border-[#d4ddd7] rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
                  required
                >
                  <option value="男性">男性</option>
                  <option value="女性">女性</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-[#374151] mb-1">
                  利き手
                </label>
                <select
                  name="dominantHand"
                  value={formData.dominantHand}
                  onChange={handleChange}
                  className="w-full px-4 py-2 border border-[#d4ddd7] rounded-lg focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
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
              className="w-full bg-[#4a6b5a] text-white py-2 px-4 rounded-lg hover:bg-[#3d5a4c] transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed font-medium"
            >
              {loading ? '登録中...' : '登録'}
            </button>
          </form>

          <div className="mt-6 text-center">
            <p className="text-sm text-[#6b7280]">
              すでにアカウントをお持ちの方は
              <Link
                to="/login"
                className="text-[#4a6b5a] hover:text-[#3d5a4c] font-medium ml-1"
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

export default InviteRegister;
