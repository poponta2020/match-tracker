import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { playerAPI } from '../api';
import {
  ArrowLeft, Save, AlertCircle, CheckCircle, Eye, EyeOff, Info, ChevronDown
} from 'lucide-react';

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
    `w-full px-3 py-2.5 text-sm border rounded-lg focus:ring-1 focus:ring-focus focus:border-focus transition-shadow bg-bg appearance-none ${
      validationErrors[name] ? 'border-status-danger' : 'border-border-strong'
    }`;

  const inputClass = (name) =>
    `w-full px-3 py-2.5 text-sm border rounded-lg focus:ring-1 focus:ring-focus focus:border-focus transition-shadow ${
      validationErrors[name] ? 'border-status-danger' : 'border-border-strong'
    }`;

  if (loading) {
    return (
      <div className="min-h-screen bg-bg flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-secondary"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg pb-6">
      {/* 固定ヘッダー */}
      <div className="bg-surface border-b border-border-subtle shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-3">
        <div className="max-w-lg mx-auto flex items-center justify-between">
          {!isSetup ? (
            <button
              onClick={() => navigate('/profile')}
              className="flex items-center gap-1 text-sm text-text hover:text-text-muted"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
          ) : (
            <div className="w-5" />
          )}
          <h1 className="text-lg font-semibold text-text">
            {isSetup ? 'プロフィール設定' : 'プロフィール編集'}
          </h1>
          <div className="w-5" />
        </div>
      </div>

      {/* コンテンツ */}
      <div className="max-w-lg mx-auto px-4 pt-16">
        {/* 初期設定メッセージ */}
        {isSetup && (
          <div className="mt-4 bg-status-info-surface border border-status-info/20 rounded-lg p-3 flex items-start gap-2">
            <Info className="h-4 w-4 text-status-info flex-shrink-0 mt-0.5" />
            <p className="text-sm text-status-info">
              プロフィールを設定すると全機能が使えるようになります
            </p>
          </div>
        )}

        {/* 成功・エラーメッセージ */}
        {success && (
          <div className="mt-4 bg-status-success-surface border border-status-success/20 rounded-lg p-3 flex items-center gap-2">
            <CheckCircle className="h-4 w-4 text-status-success" />
            <p className="text-sm text-status-success">保存しました</p>
          </div>
        )}
        {error && (
          <div className="mt-4 bg-status-danger-surface border border-status-danger/20 rounded-lg p-3 flex items-center gap-2">
            <AlertCircle className="h-4 w-4 text-status-danger" />
            <p className="text-sm text-status-danger">{error}</p>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-5 space-y-5">
          {/* 選手名（変更不可） */}
          <div className="bg-surface rounded-xl p-5 shadow-sm space-y-4">
            <div>
              <label className="block text-xs font-medium text-text-muted mb-1.5">選手名</label>
              <div className="px-3 py-2.5 text-sm bg-surface-disabled border border-border-subtle rounded-lg text-text-disabled">
                {currentPlayer?.name}
                <span className="ml-1.5 text-xs text-text-placeholder">(変更不可)</span>
              </div>
            </div>

            {/* 基本情報：2列グリッド */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1.5">
                  性別 <span className="text-status-danger">*</span>
                </label>
                <select name="gender" value={formData.gender} onChange={handleChange} className={selectClass('gender')}>
                  <option value="">選択</option>
                  <option value="男性">男性</option>
                  <option value="女性">女性</option>
                  <option value="その他">その他</option>
                </select>
                {validationErrors.gender && <p className="mt-1 text-xs text-status-danger">{validationErrors.gender}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1.5">
                  利き手 <span className="text-status-danger">*</span>
                </label>
                <select name="dominantHand" value={formData.dominantHand} onChange={handleChange} className={selectClass('dominantHand')}>
                  <option value="">選択</option>
                  <option value="右">右</option>
                  <option value="左">左</option>
                  <option value="両">両</option>
                </select>
                {validationErrors.dominantHand && <p className="mt-1 text-xs text-status-danger">{validationErrors.dominantHand}</p>}
              </div>
            </div>

            {/* 競技情報：2列グリッド */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1.5">
                  級位 <span className="text-status-danger">*</span>
                </label>
                <select name="kyuRank" value={formData.kyuRank} onChange={handleChange} className={selectClass('kyuRank')}>
                  <option value="">選択</option>
                  <option value="A級">A級</option>
                  <option value="B級">B級</option>
                  <option value="C級">C級</option>
                  <option value="D級">D級</option>
                  <option value="E級">E級</option>
                </select>
                {validationErrors.kyuRank && <p className="mt-1 text-xs text-status-danger">{validationErrors.kyuRank}</p>}
              </div>
              <div>
                <label className="block text-xs font-medium text-text-muted mb-1.5">
                  段位 <span className="text-status-danger">*</span>
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
                {validationErrors.danRank && <p className="mt-1 text-xs text-status-danger">{validationErrors.danRank}</p>}
              </div>
            </div>

            {/* 所属かるた会 */}
            <div>
              <label className="block text-xs font-medium text-text-muted mb-1.5">所属かるた会</label>
              <input
                type="text"
                name="karutaClub"
                value={formData.karutaClub}
                onChange={handleChange}
                placeholder="例: ○○かるた会"
                className={inputClass('karutaClub')}
              />
              {validationErrors.karutaClub && <p className="mt-1 text-xs text-status-danger">{validationErrors.karutaClub}</p>}
            </div>

            {/* 備考 */}
            <div>
              <label className="block text-xs font-medium text-text-muted mb-1.5">備考</label>
              <textarea
                name="remarks"
                value={formData.remarks}
                onChange={handleChange}
                rows={2}
                placeholder="個人的なメモなど"
                className="w-full px-3 py-2.5 text-sm border border-border-strong rounded-lg focus:ring-1 focus:ring-focus focus:border-focus resize-none"
              />
            </div>
          </div>

          {/* パスワード変更（折りたたみ） */}
          <div className="bg-surface rounded-xl shadow-sm overflow-hidden">
            <button
              type="button"
              onClick={() => setShowPasswordSection(!showPasswordSection)}
              className="w-full flex items-center justify-between px-5 py-4 text-sm font-medium text-text hover:bg-bg transition-colors"
            >
              <span>パスワード変更</span>
              <ChevronDown className={`w-4 h-4 text-text-muted transition-transform ${showPasswordSection ? 'rotate-180' : ''}`} />
            </button>

            {showPasswordSection && (
              <div className="px-5 pb-5 space-y-3">
                <p className="text-xs text-text-placeholder">変更しない場合は空欄のままにしてください</p>
                <div>
                  <label className="block text-xs font-medium text-text-muted mb-1.5">現在のパスワード</label>
                  <div className="relative">
                    <input
                      type={showCurrentPassword ? 'text' : 'password'}
                      name="currentPassword"
                      value={formData.currentPassword}
                      onChange={handleChange}
                      className={`${inputClass('currentPassword')} pr-9`}
                    />
                    <button type="button" onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-placeholder hover:text-text-muted">
                      {showCurrentPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {validationErrors.currentPassword && <p className="mt-1 text-xs text-status-danger">{validationErrors.currentPassword}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-text-muted mb-1.5">新しいパスワード</label>
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
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-placeholder hover:text-text-muted">
                      {showNewPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {validationErrors.newPassword && <p className="mt-1 text-xs text-status-danger">{validationErrors.newPassword}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-text-muted mb-1.5">新しいパスワード（確認）</label>
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
                      className="absolute right-2.5 top-1/2 -translate-y-1/2 text-text-placeholder hover:text-text-muted">
                      {showConfirmPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {validationErrors.confirmPassword && <p className="mt-1 text-xs text-status-danger">{validationErrors.confirmPassword}</p>}
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
                className="flex-1 py-3 text-sm border border-border-subtle text-text-muted rounded-xl hover:bg-surface transition-colors disabled:opacity-50 font-medium"
              >
                キャンセル
              </button>
            )}
            <button
              type="submit"
              disabled={saving}
              className="flex-1 flex items-center justify-center gap-1.5 py-3 text-sm bg-primary text-text-inverse rounded-xl hover:bg-primary-hover transition-colors disabled:opacity-50 font-medium shadow-sm"
            >
              {saving ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-text-inverse"></div>
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
