import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import apiClient from '../../api/client';
import { User, Save, ArrowLeft, Lock, Eye, EyeOff, AlertTriangle } from 'lucide-react';
import { isSuperAdmin } from '../../utils/auth';

const PlayerEdit = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // 基本情報フォームデータ（PlayerUpdateRequestに対応）
  const [formData, setFormData] = useState({
    name: '',
    gender: '',
    dominantHand: '',
    danRank: '',
    kyuRank: '',
    karutaClub: '',
    remarks: '',
  });

  // ロールは別管理（別APIで更新）
  const [role, setRole] = useState('PLAYER');
  const [originalRole, setOriginalRole] = useState('PLAYER');

  // パスワード変更用の状態（スーパー管理者のみ）
  const [passwordData, setPasswordData] = useState({
    newPassword: '',
    confirmPassword: '',
  });
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [passwordError, setPasswordError] = useState('');

  useEffect(() => {
    if (id) {
      fetchPlayer();
    } else {
      setLoading(false);
    }
  }, [id]);

  const fetchPlayer = async () => {
    try {
      setLoading(true);
      const response = await playerAPI.getById(id);
      const player = response.data;

      setFormData({
        name: player.name || '',
        gender: player.gender || '',
        dominantHand: player.dominantHand || '',
        danRank: player.danRank || '',
        kyuRank: player.kyuRank || '',
        karutaClub: player.karutaClub || '',
        remarks: player.remarks || '',
      });

      // ロールは別管理
      setRole(player.role || 'PLAYER');
      setOriginalRole(player.role || 'PLAYER');
    } catch (err) {
      console.error('Failed to fetch player:', err);
      setError('選手情報の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleKyuRankChange = (e) => {
    const kyuRank = e.target.value;
    let danRank = '';

    // 級に応じて段位を自動設定
    switch (kyuRank) {
      case 'E級':
        danRank = '無段';
        break;
      case 'D級':
        danRank = '初段';
        break;
      case 'C級':
        danRank = '弐段';
        break;
      case 'B級':
        danRank = '参段';
        break;
      case 'A級':
        danRank = '四段';
        break;
      default:
        danRank = '';
    }

    setFormData((prev) => ({
      ...prev,
      kyuRank,
      danRank,
    }));
  };

  const handlePasswordChange = (e) => {
    const { name, value } = e.target;
    setPasswordData((prev) => ({
      ...prev,
      [name]: value,
    }));
    setPasswordError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setPasswordError('');

    if (!formData.name.trim()) {
      setError('名前は必須です');
      return;
    }

    // パスワード変更のバリデーション（スーパー管理者のみ）
    if (isSuperAdmin() && id && (passwordData.newPassword || passwordData.confirmPassword)) {
      if (passwordData.newPassword.length < 8) {
        setPasswordError('パスワードは8文字以上で入力してください');
        return;
      }
      if (passwordData.newPassword !== passwordData.confirmPassword) {
        setPasswordError('パスワードが一致しません');
        return;
      }
    }

    try {
      setSubmitting(true);

      if (id) {
        // === 既存選手の更新 ===

        // 1. 基本情報の更新データを準備（PlayerUpdateRequestに対応するフィールドのみ）
        const updateData = {
          name: formData.name,
          gender: formData.gender || null,
          dominantHand: formData.dominantHand || null,
          danRank: formData.danRank || null,
          kyuRank: formData.kyuRank || null,
          karutaClub: formData.karutaClub || null,
          remarks: formData.remarks || null,
        };

        // パスワード変更がある場合は追加
        if (isSuperAdmin() && passwordData.newPassword) {
          updateData.password = passwordData.newPassword;
        }

        // 2. 基本情報を更新
        await playerAPI.update(id, updateData);

        // 3. ロールが変更されている場合は別APIで更新（スーパー管理者のみ）
        if (isSuperAdmin() && role !== originalRole) {
          await apiClient.put(`/players/${id}/role?role=${role}`);
        }

        alert('選手情報を更新しました');
        navigate(`/players/${id}`);
      } else {
        // === 新規選手の登録 ===
        const response = await playerAPI.register(formData);
        alert('選手を登録しました');
        navigate(`/players/${response.data.id}`);
      }
    } catch (err) {
      console.error('Failed to save player:', err);
      const errorMessage = err.response?.data?.message || err.response?.data?.error || '';
      setError(id ? `選手情報の更新に失敗しました: ${errorMessage}` : `選手の登録に失敗しました: ${errorMessage}`);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-600">読み込み中...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <button
          onClick={() => navigate(id ? `/players/${id}` : '/players')}
          className="flex items-center gap-2 text-primary-600 hover:text-primary-800"
        >
          <ArrowLeft className="w-5 h-5" />
          {id ? '選手詳細に戻る' : '選手一覧に戻る'}
        </button>
      </div>

      <div className="bg-white shadow-sm rounded-lg overflow-hidden">
        <div className="bg-primary-600 text-white px-6 py-4">
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <User className="w-8 h-8" />
            {id ? '選手情報編集' : '選手新規登録'}
          </h1>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-6">
          {error && (
            <div className="bg-red-50 border border-red-200 p-4 rounded-lg text-red-700">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              名前 <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              name="name"
              value={formData.name}
              onChange={handleChange}
              required
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="山田 太郎"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                性別
              </label>
              <select
                name="gender"
                value={formData.gender}
                onChange={handleChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="">未設定</option>
                <option value="男性">男性</option>
                <option value="女性">女性</option>
                <option value="その他">その他</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                利き手
              </label>
              <select
                name="dominantHand"
                value={formData.dominantHand}
                onChange={handleChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="">未設定</option>
                <option value="右">右</option>
                <option value="左">左</option>
                <option value="両">両</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                級位
              </label>
              <select
                name="kyuRank"
                value={formData.kyuRank}
                onChange={handleKyuRankChange}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="">未設定</option>
                <option value="E級">E級</option>
                <option value="D級">D級</option>
                <option value="C級">C級</option>
                <option value="B級">B級</option>
                <option value="A級">A級</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                段位 {formData.kyuRank === 'A級' && '(A級は四段〜八段)'}
              </label>
              <select
                name="danRank"
                value={formData.danRank}
                onChange={handleChange}
                disabled={formData.kyuRank !== 'A級'}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent disabled:bg-gray-100 disabled:cursor-not-allowed"
              >
                <option value="">未設定</option>
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
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              所属かるた会
            </label>
            <input
              type="text"
              name="karutaClub"
              value={formData.karutaClub}
              onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="〇〇かるた会"
              maxLength={200}
            />
          </div>

          {/* ロール選択（スーパー管理者のみ、編集時のみ） */}
          {isSuperAdmin() && id && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                ロール
                <span className="ml-2 px-2 py-1 bg-purple-100 text-purple-800 text-xs font-medium rounded">
                  スーパー管理者専用
                </span>
              </label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="PLAYER">一般ユーザー</option>
                <option value="ADMIN">管理者</option>
                <option value="SUPER_ADMIN">スーパー管理者</option>
              </select>
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              備考
            </label>
            <textarea
              name="remarks"
              value={formData.remarks}
              onChange={handleChange}
              rows={4}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="特記事項など..."
            />
          </div>

          {/* パスワード変更セクション（スーパー管理者のみ、編集時のみ） */}
          {isSuperAdmin() && id && (
            <div className="border-t border-gray-200 pt-6 mt-6">
              <div className="flex items-center gap-2 mb-4">
                <Lock className="w-5 h-5 text-red-600" />
                <h2 className="text-lg font-semibold text-gray-900">パスワード変更</h2>
                <span className="px-2 py-1 bg-purple-100 text-purple-800 text-xs font-medium rounded">
                  スーパー管理者専用
                </span>
              </div>

              <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-4">
                <div className="flex items-start gap-2">
                  <AlertTriangle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                  <div>
                    <p className="text-sm text-yellow-800 font-medium">注意</p>
                    <p className="text-sm text-yellow-700">
                      この機能は選手のパスワードを強制的にリセットします。
                      パスワードを変更しない場合は空欄のままにしてください。
                    </p>
                  </div>
                </div>
              </div>

              {passwordError && (
                <div className="bg-red-50 border border-red-200 p-3 rounded-lg text-red-700 text-sm mb-4">
                  {passwordError}
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    新しいパスワード
                  </label>
                  <div className="relative">
                    <input
                      type={showNewPassword ? 'text' : 'password'}
                      name="newPassword"
                      value={passwordData.newPassword}
                      onChange={handlePasswordChange}
                      placeholder="8文字以上"
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent pr-10"
                    />
                    <button
                      type="button"
                      onClick={() => setShowNewPassword(!showNewPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showNewPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    新しいパスワード（確認）
                  </label>
                  <div className="relative">
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      name="confirmPassword"
                      value={passwordData.confirmPassword}
                      onChange={handlePasswordChange}
                      placeholder="もう一度入力"
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent pr-10"
                    />
                    <button
                      type="button"
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                    >
                      {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          )}

          <div className="flex gap-4 pt-4">
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition-colors disabled:bg-gray-400 disabled:cursor-not-allowed"
            >
              <Save className="w-5 h-5" />
              {submitting ? '保存中...' : '保存'}
            </button>
            <button
              type="button"
              onClick={() => navigate(id ? `/players/${id}` : '/players')}
              className="px-6 py-3 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
            >
              キャンセル
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default PlayerEdit;
