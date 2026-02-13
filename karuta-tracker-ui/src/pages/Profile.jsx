import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import {
  User, Lock, Trophy, ArrowLeft, Save, AlertCircle, CheckCircle, Eye, EyeOff
} from 'lucide-react';

const Profile = () => {
  const navigate = useNavigate();
  const { currentPlayer, login } = useAuth();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [formData, setFormData] = useState({
    gender: '',
    dominantHand: '',
    kyuRank: '',
    danRank: '',
    karutaClub: '',
    remarks: '',
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [validationErrors, setValidationErrors] = useState({});

  // ユーザー情報を取得
  useEffect(() => {
    const fetchUserData = async () => {
      if (!currentPlayer?.id) {
        navigate('/login');
        return;
      }

      try {
        setLoading(true);
        const response = await playerAPI.getById(currentPlayer.id);
        const player = response.data;

        setFormData({
          gender: player.gender || '',
          dominantHand: player.dominantHand || '',
          kyuRank: player.kyuRank || '',
          danRank: player.danRank || '',
          karutaClub: player.karutaClub || '',
          remarks: player.remarks || '',
          currentPassword: '',
          newPassword: '',
          confirmPassword: '',
        });
      } catch (err) {
        console.error('ユーザー情報の取得に失敗:', err);
        setError('ユーザー情報の取得に失敗しました');
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, [currentPlayer, navigate]);

  // 級位変更時に段位を自動調整
  useEffect(() => {
    const kyuToDanMap = {
      'E級': '無段',
      'D級': '初段',
      'C級': '弐段',
      'B級': '参段',
      'A級': '四段',
    };

    if (formData.kyuRank && kyuToDanMap[formData.kyuRank]) {
      setFormData(prev => ({
        ...prev,
        danRank: kyuToDanMap[formData.kyuRank],
      }));
    }
  }, [formData.kyuRank]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }));

    // エラーをクリア
    if (validationErrors[name]) {
      setValidationErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[name];
        return newErrors;
      });
    }
  };

  const validate = () => {
    const errors = {};

    // 必須項目チェック
    if (!formData.gender) errors.gender = '性別を選択してください';
    if (!formData.dominantHand) errors.dominantHand = '利き手を選択してください';
    if (!formData.kyuRank) errors.kyuRank = '級位を選択してください';
    if (!formData.danRank) errors.danRank = '段位を選択してください';

    // パスワード変更時のバリデーション
    if (formData.newPassword || formData.confirmPassword) {
      if (!formData.currentPassword) {
        errors.currentPassword = '現在のパスワードを入力してください';
      }

      if (!formData.newPassword) {
        errors.newPassword = '新しいパスワードを入力してください';
      } else if (formData.newPassword.length < 8) {
        errors.newPassword = 'パスワードは8文字以上で入力してください';
      }

      if (formData.newPassword !== formData.confirmPassword) {
        errors.confirmPassword = 'パスワードが一致しません';
      }
    }

    // 所属かるた会の文字数チェック
    if (formData.karutaClub && formData.karutaClub.length > 200) {
      errors.karutaClub = '所属かるた会は200文字以内で入力してください';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validate()) {
      return;
    }

    try {
      setSaving(true);
      setError(null);

      // 更新データを準備
      const updateData = {
        gender: formData.gender,
        dominantHand: formData.dominantHand,
        kyuRank: formData.kyuRank,
        danRank: formData.danRank,
        karutaClub: formData.karutaClub || null,
        remarks: formData.remarks || null,
      };

      // パスワード変更がある場合
      if (formData.newPassword) {
        // まず現在のパスワードで再ログインして確認
        try {
          await playerAPI.login(currentPlayer.name, formData.currentPassword);
        } catch (err) {
          setValidationErrors({
            currentPassword: '現在のパスワードが正しくありません',
          });
          setSaving(false);
          return;
        }

        updateData.password = formData.newPassword;
      }

      // 更新実行
      const response = await playerAPI.update(currentPlayer.id, updateData);
      const updatedPlayer = response.data;

      // AuthContext の currentPlayer を更新
      const playerData = {
        id: updatedPlayer.id,
        name: updatedPlayer.name,
        gender: updatedPlayer.gender,
        dominantHand: updatedPlayer.dominantHand,
        danRank: updatedPlayer.danRank,
        kyuRank: updatedPlayer.kyuRank,
        karutaClub: updatedPlayer.karutaClub,
        role: updatedPlayer.role,
      };

      // パスワード変更した場合は再ログイン
      if (formData.newPassword) {
        await login(currentPlayer.name, formData.newPassword);
      } else {
        // パスワード変更なしの場合は currentPlayer を更新
        localStorage.setItem('currentPlayer', JSON.stringify(playerData));
      }

      setSuccess(true);

      // 1秒後にホーム画面へ
      setTimeout(() => {
        navigate('/');
      }, 1000);

    } catch (err) {
      console.error('保存に失敗:', err);
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-blue-50">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto"></div>
          <p className="mt-4 text-gray-600">読み込み中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 via-white to-blue-50 pb-20">
      {/* ヘッダー */}
      <div className="bg-white shadow-sm border-b sticky top-0 z-10">
        <div className="max-w-3xl mx-auto px-4 py-4">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate('/')}
              className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
            >
              <ArrowLeft className="h-5 w-5 text-gray-600" />
            </button>
            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
              <User className="h-6 w-6 text-primary-600" />
              マイページ
            </h1>
          </div>
        </div>
      </div>

      {/* メインコンテンツ */}
      <div className="max-w-3xl mx-auto px-4 py-8">
        {/* 成功メッセージ */}
        {success && (
          <div className="mb-6 bg-green-50 border border-green-200 rounded-lg p-4 flex items-center gap-3 animate-fadeIn">
            <CheckCircle className="h-5 w-5 text-green-600 flex-shrink-0" />
            <div>
              <p className="text-green-800 font-semibold">保存しました</p>
              <p className="text-green-700 text-sm">ホーム画面に戻ります...</p>
            </div>
          </div>
        )}

        {/* エラーメッセージ */}
        {error && (
          <div className="mb-6 bg-red-50 border border-red-200 rounded-lg p-4 flex items-center gap-3">
            <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
            <p className="text-red-800">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          {/* 選手名表示（変更不可） */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
              <User className="h-5 w-5 text-gray-600" />
              <h2 className="text-lg font-semibold text-gray-900">アカウント情報</h2>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                選手名
              </label>
              <div className="px-4 py-3 bg-gray-50 border border-gray-200 rounded-lg text-gray-600">
                {currentPlayer?.name}
                <span className="ml-2 text-xs text-gray-500">(変更不可)</span>
              </div>
            </div>
          </div>

          {/* 基本情報 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
              <User className="h-5 w-5 text-blue-600" />
              <h2 className="text-lg font-semibold text-gray-900">基本情報</h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* 性別 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  性別 <span className="text-red-500">*</span>
                </label>
                <select
                  name="gender"
                  value={formData.gender}
                  onChange={handleChange}
                  className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow ${
                    validationErrors.gender ? 'border-red-500' : 'border-gray-300'
                  }`}
                >
                  <option value="">選択してください</option>
                  <option value="男性">男性</option>
                  <option value="女性">女性</option>
                  <option value="その他">その他</option>
                </select>
                {validationErrors.gender && (
                  <p className="mt-1 text-sm text-red-600">{validationErrors.gender}</p>
                )}
              </div>

              {/* 利き手 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  利き手 <span className="text-red-500">*</span>
                </label>
                <select
                  name="dominantHand"
                  value={formData.dominantHand}
                  onChange={handleChange}
                  className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow ${
                    validationErrors.dominantHand ? 'border-red-500' : 'border-gray-300'
                  }`}
                >
                  <option value="">選択してください</option>
                  <option value="右">右</option>
                  <option value="左">左</option>
                  <option value="両">両</option>
                </select>
                {validationErrors.dominantHand && (
                  <p className="mt-1 text-sm text-red-600">{validationErrors.dominantHand}</p>
                )}
              </div>
            </div>
          </div>

          {/* 競技情報 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
              <Trophy className="h-5 w-5 text-yellow-600" />
              <h2 className="text-lg font-semibold text-gray-900">競技情報</h2>
            </div>
            <div className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* 級位 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    級位 <span className="text-red-500">*</span>
                  </label>
                  <select
                    name="kyuRank"
                    value={formData.kyuRank}
                    onChange={handleChange}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow ${
                      validationErrors.kyuRank ? 'border-red-500' : 'border-gray-300'
                    }`}
                  >
                    <option value="">選択してください</option>
                    <option value="A級">A級</option>
                    <option value="B級">B級</option>
                    <option value="C級">C級</option>
                    <option value="D級">D級</option>
                    <option value="E級">E級</option>
                  </select>
                  {validationErrors.kyuRank && (
                    <p className="mt-1 text-sm text-red-600">{validationErrors.kyuRank}</p>
                  )}
                </div>

                {/* 段位 */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    段位 <span className="text-red-500">*</span>
                  </label>
                  <select
                    name="danRank"
                    value={formData.danRank}
                    onChange={handleChange}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow ${
                      validationErrors.danRank ? 'border-red-500' : 'border-gray-300'
                    }`}
                  >
                    <option value="">選択してください</option>
                    <option value="無段">無段</option>
                    <option value="初段">初段</option>
                    <option value="弐段">弐段</option>
                    <option value="参段">参段</option>
                    <option value="四段">四段</option>
                    <option value="五段">五段</option>
                    <option value="六段">六段</option>
                    <option value="七段">七段</option>
                    <option value="八段">八段</option>
                  </select>
                  {validationErrors.danRank && (
                    <p className="mt-1 text-sm text-red-600">{validationErrors.danRank}</p>
                  )}
                </div>
              </div>

              {/* 所属かるた会 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  所属かるた会
                </label>
                <input
                  type="text"
                  name="karutaClub"
                  value={formData.karutaClub}
                  onChange={handleChange}
                  placeholder="例: ○○かるた会"
                  className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow ${
                    validationErrors.karutaClub ? 'border-red-500' : 'border-gray-300'
                  }`}
                />
                {validationErrors.karutaClub && (
                  <p className="mt-1 text-sm text-red-600">{validationErrors.karutaClub}</p>
                )}
              </div>
            </div>
          </div>

          {/* パスワード変更 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
              <Lock className="h-5 w-5 text-red-600" />
              <h2 className="text-lg font-semibold text-gray-900">パスワード変更</h2>
            </div>
            <p className="text-sm text-gray-600 mb-4">
              パスワードを変更しない場合は空欄のままにしてください
            </p>
            <div className="space-y-4">
              {/* 現在のパスワード */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  現在のパスワード
                </label>
                <div className="relative">
                  <input
                    type={showCurrentPassword ? 'text' : 'password'}
                    name="currentPassword"
                    value={formData.currentPassword}
                    onChange={handleChange}
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow pr-10 ${
                      validationErrors.currentPassword ? 'border-red-500' : 'border-gray-300'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showCurrentPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                {validationErrors.currentPassword && (
                  <p className="mt-1 text-sm text-red-600">{validationErrors.currentPassword}</p>
                )}
              </div>

              {/* 新しいパスワード */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  新しいパスワード
                </label>
                <div className="relative">
                  <input
                    type={showNewPassword ? 'text' : 'password'}
                    name="newPassword"
                    value={formData.newPassword}
                    onChange={handleChange}
                    placeholder="8文字以上"
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow pr-10 ${
                      validationErrors.newPassword ? 'border-red-500' : 'border-gray-300'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPassword(!showNewPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showNewPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                {validationErrors.newPassword && (
                  <p className="mt-1 text-sm text-red-600">{validationErrors.newPassword}</p>
                )}
              </div>

              {/* パスワード確認 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  新しいパスワード（確認）
                </label>
                <div className="relative">
                  <input
                    type={showConfirmPassword ? 'text' : 'password'}
                    name="confirmPassword"
                    value={formData.confirmPassword}
                    onChange={handleChange}
                    placeholder="もう一度入力してください"
                    className={`w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow pr-10 ${
                      validationErrors.confirmPassword ? 'border-red-500' : 'border-gray-300'
                    }`}
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                  >
                    {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                {validationErrors.confirmPassword && (
                  <p className="mt-1 text-sm text-red-600">{validationErrors.confirmPassword}</p>
                )}
              </div>
            </div>
          </div>

          {/* 備考 */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">備考</h2>
            <textarea
              name="remarks"
              value={formData.remarks}
              onChange={handleChange}
              rows={4}
              placeholder="個人的なメモなど（任意）"
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow resize-none"
            />
          </div>

          {/* ボタン */}
          <div className="flex gap-4">
            <button
              type="button"
              onClick={() => navigate('/')}
              disabled={saving}
              className="flex-1 px-6 py-3 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed font-semibold"
            >
              キャンセル
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex-1 px-6 py-3 bg-gradient-to-r from-primary-600 to-primary-700 text-white rounded-lg hover:from-primary-700 hover:to-primary-800 transition-all shadow-lg hover:shadow-xl disabled:opacity-50 disabled:cursor-not-allowed font-semibold flex items-center justify-center gap-2"
            >
              {saving ? (
                <>
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                  保存中...
                </>
              ) : (
                <>
                  <Save className="h-5 w-5" />
                  保存する
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Profile;
