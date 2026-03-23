import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import {
  ArrowLeft, Save, AlertCircle, CheckCircle, Eye, EyeOff, Info, ChevronDown
} from 'lucide-react';
import LoadingScreen from '../components/LoadingScreen';

const ProfileEdit = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const isSetup = searchParams.get('setup') === 'true';
  const { currentPlayer, login, updateCurrentPlayer } = useAuth();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [showPasswordSection, setShowPasswordSection] = useState(false);
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
    setFormData(prev => ({ ...prev, [name]: value }));
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
    if (!formData.gender) errors.gender = '性別を選択してください';
    if (!formData.dominantHand) errors.dominantHand = '利き手を選択してください';
    if (!formData.kyuRank) errors.kyuRank = '級位を選択してください';
    if (!formData.danRank) errors.danRank = '段位を選択してください';

    if (formData.newPassword || formData.confirmPassword) {
      if (!formData.currentPassword) errors.currentPassword = '現在のパスワードを入力してください';
      if (!formData.newPassword) errors.newPassword = '新しいパスワードを入力してください';
      else if (formData.newPassword.length < 8) errors.newPassword = '8文字以上で入力してください';
      if (formData.newPassword !== formData.confirmPassword) errors.confirmPassword = 'パスワードが一致しません';
    }

    if (formData.karutaClub && formData.karutaClub.length > 200) {
      errors.karutaClub = '200文字以内で入力してください';
    }

    setValidationErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validate()) return;

    try {
      setSaving(true);
      setError(null);

      const updateData = {
        gender: formData.gender,
        dominantHand: formData.dominantHand,
        kyuRank: formData.kyuRank,
        danRank: formData.danRank,
        karutaClub: formData.karutaClub || null,
        remarks: formData.remarks || null,
      };

      if (formData.newPassword) {
        try {
          await playerAPI.login(currentPlayer.name, formData.currentPassword);
        } catch {
          setValidationErrors({ currentPassword: '現在のパスワードが正しくありません' });
          setSaving(false);
          return;
        }
        updateData.password = formData.newPassword;
      }

      const response = await playerAPI.update(currentPlayer.id, updateData);
      const updatedPlayer = response.data;

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

      if (formData.newPassword) {
        await login(currentPlayer.name, formData.newPassword);
      } else {
        updateCurrentPlayer(playerData);
      }

      setSuccess(true);
      setTimeout(() => {
        navigate(isSetup ? '/' : '/profile');
      }, 1000);
    } catch (err) {
      console.error('保存に失敗:', err);
      setError(err.response?.data?.message || '保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  const selectClass = (name) =>
    `w-full px-3 py-2.5 text-sm border rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] transition-shadow bg-white appearance-none ${
      validationErrors[name] ? 'border-red-400' : 'border-[#d4ddd7]'
    }`;

  const inputClass = (name) =>
    `w-full px-3 py-2.5 text-sm border rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] transition-shadow ${
      validationErrors[name] ? 'border-red-400' : 'border-[#d4ddd7]'
    }`;

  if (loading) {
    return <LoadingScreen />;
  }

  return (
    <div className="min-h-screen bg-[#f2ede6] pb-6">
      {/* 固定ヘッダー */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-3">
        <div className="max-w-lg mx-auto flex items-center justify-between">
          {!isSetup ? (
            <button
              onClick={() => navigate('/profile')}
              className="flex items-center gap-1 text-sm text-white hover:text-white/80"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
          ) : (
            <div className="w-5" />
          )}
          <h1 className="text-lg font-semibold text-white">
            {isSetup ? 'プロフィール設定' : 'プロフィール編集'}
          </h1>
          <div className="w-5" />
        </div>
      </div>

      {/* コンテンツ */}
      <div className="max-w-lg mx-auto px-4 pt-16">
        {/* 初期設定メッセージ */}
        {isSetup && (
          <div className="mt-4 bg-blue-50 border border-blue-200 rounded-lg p-3 flex items-start gap-2">
            <Info className="h-4 w-4 text-blue-600 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-blue-700">
              プロフィールを設定すると全機能が使えるようになります
            </p>
          </div>
        )}

        {/* 成功・エラーメッセージ */}
        {success && (
          <div className="mt-4 bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
            <CheckCircle className="h-4 w-4 text-green-600" />
            <p className="text-sm text-green-800">保存しました</p>
          </div>
        )}
        {error && (
          <div className="mt-4 bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-red-600" />
            <p className="text-sm text-red-800">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-5 space-y-5">
          {/* 選手名（変更不可） */}
          <div className="bg-[#f9f6f2] rounded-xl p-5 shadow-sm space-y-4">
            <div>
              <label className="block text-xs font-medium text-[#6b7280] mb-1.5">選手名</label>
              <div className="px-3 py-2.5 text-sm bg-[#e5ebe7] border border-[#d4ddd7] rounded-lg text-[#6b7280]">
                {currentPlayer?.name}
                <span className="ml-1.5 text-xs text-[#9ca3af]">(変更不可)</span>
              </div>
            </div>

            {/* 基本情報：2列グリッド */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-[#6b7280] mb-1.5">
                  性別 <span className="text-red-400">*</span>
                </label>
                <select name="gender" value={formData.gender} onChange={handleChange} className={selectClass('gender')}>
                  <option value="">選択</option>
                  <option value="男性">男性</option>
                  <option value="女性">女性</option>
                  <option value="その他">その他</option>
                </select>
                {validationErrors.gender && <p className="mt-1 text-xs text-red-500">{validationErrors.gender}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-[#6b7280] mb-1.5">
                  利き手 <span className="text-red-400">*</span>
                </label>
                <select name="dominantHand" value={formData.dominantHand} onChange={handleChange} className={selectClass('dominantHand')}>
                  <option value="">選択</option>
                  <option value="右">右</option>
                  <option value="左">左</option>
                  <option value="両">両</option>
                </select>
                {validationErrors.dominantHand && <p className="mt-1 text-xs text-red-500">{validationErrors.dominantHand}</p>}
              </div>
            </div>

            {/* 競技情報：2列グリッド */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-[#6b7280] mb-1.5">
                  級位 <span className="text-red-400">*</span>
                </label>
                <select name="kyuRank" value={formData.kyuRank} onChange={handleChange} className={selectClass('kyuRank')}>
                  <option value="">選択</option>
                  <option value="A級">A級</option>
                  <option value="B級">B級</option>
                  <option value="C級">C級</option>
                  <option value="D級">D級</option>
                  <option value="E級">E級</option>
                </select>
                {validationErrors.kyuRank && <p className="mt-1 text-xs text-red-500">{validationErrors.kyuRank}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-[#6b7280] mb-1.5">
                  段位 <span className="text-red-400">*</span>
                </label>
                <select name="danRank" value={formData.danRank} onChange={handleChange} className={selectClass('danRank')}>
                  <option value="">選択</option>
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
                {validationErrors.danRank && <p className="mt-1 text-xs text-red-500">{validationErrors.danRank}</p>}
              </div>
            </div>

            {/* 所属かるた会 */}
            <div>
              <label className="block text-xs font-medium text-[#6b7280] mb-1.5">所属かるた会</label>
              <input
                type="text"
                name="karutaClub"
                value={formData.karutaClub}
                onChange={handleChange}
                placeholder="例: ○○かるた会"
                className={inputClass('karutaClub')}
              />
              {validationErrors.karutaClub && <p className="mt-1 text-xs text-red-500">{validationErrors.karutaClub}</p>}
            </div>

            {/* 備考 */}
            <div>
              <label className="block text-xs font-medium text-[#6b7280] mb-1.5">備考</label>
              <textarea
                name="remarks"
                value={formData.remarks}
                onChange={handleChange}
                rows={2}
                placeholder="個人的なメモなど"
                className="w-full px-3 py-2.5 text-sm border border-[#d4ddd7] rounded-lg focus:ring-1 focus:ring-[#4a6b5a] focus:border-[#4a6b5a] resize-none"
              />
            </div>
          </div>

          {/* パスワード変更（折りたたみ） */}
          <div className="bg-[#f9f6f2] rounded-xl shadow-sm overflow-hidden">
            <button
              type="button"
              onClick={() => setShowPasswordSection(!showPasswordSection)}
              className="w-full flex items-center justify-between px-5 py-4 text-sm font-medium text-[#374151] hover:bg-[#f0ebe3] transition-colors"
            >
              <span>パスワード変更</span>
              <ChevronDown className={`w-4 h-4 text-[#6b7280] transition-transform ${showPasswordSection ? 'rotate-180' : ''}`} />
            </button>

            {showPasswordSection && (
              <div className="px-5 pb-5 space-y-3">
                <p className="text-xs text-[#9ca3af]">変更しない場合は空欄のままにしてください</p>
                <div>
                  <label className="block text-xs font-medium text-[#6b7280] mb-1.5">現在のパスワード</label>
                  <div className="relative">
                    <input
                      type={showCurrentPassword ? 'text' : 'password'}
                      name="currentPassword"
                      value={formData.currentPassword}
                      onChange={handleChange}
                      className={`${inputClass('currentPassword')} pr-9`}
                    />
                    <button type="button" onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                      {showCurrentPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {validationErrors.currentPassword && <p className="mt-1 text-xs text-red-500">{validationErrors.currentPassword}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#6b7280] mb-1.5">新しいパスワード</label>
                  <div className="relative">
                    <input
                      type={showNewPassword ? 'text' : 'password'}
                      name="newPassword"
                      value={formData.newPassword}
                      onChange={handleChange}
                      placeholder="8文字以上"
                      className={`${inputClass('newPassword')} pr-9`}
                    />
                    <button type="button" onClick={() => setShowNewPassword(!showNewPassword)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                      {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {validationErrors.newPassword && <p className="mt-1 text-xs text-red-500">{validationErrors.newPassword}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-[#6b7280] mb-1.5">新しいパスワード（確認）</label>
                  <div className="relative">
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      name="confirmPassword"
                      value={formData.confirmPassword}
                      onChange={handleChange}
                      placeholder="もう一度入力"
                      className={`${inputClass('confirmPassword')} pr-9`}
                    />
                    <button type="button" onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                      {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {validationErrors.confirmPassword && <p className="mt-1 text-xs text-red-500">{validationErrors.confirmPassword}</p>}
                </div>
              </div>
            )}
          </div>

          {/* ボタン */}
          <div className="flex gap-3 pt-1 pb-2">
            {!isSetup && (
              <button
                type="button"
                onClick={() => navigate('/profile')}
                disabled={saving}
                className="flex-1 py-3 text-sm border border-[#d4ddd7] text-[#6b7280] rounded-xl hover:bg-[#e5ebe7] transition-colors disabled:opacity-50 font-medium"
              >
                キャンセル
              </button>
            )}
            <button
              type="submit"
              disabled={saving}
              className="flex-1 flex items-center justify-center gap-1.5 py-3 text-sm bg-[#4a6b5a] text-white rounded-xl hover:bg-[#3d5a4c] transition-colors disabled:opacity-50 font-medium shadow-sm"
            >
              {saving ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                  保存中...
                </>
              ) : (
                <>
                  <Save className="h-4 w-4" />
                  保存
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProfileEdit;
