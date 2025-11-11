import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { playerAPI } from '../../api/players';
import { User, Save, ArrowLeft } from 'lucide-react';

const PlayerEdit = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [formData, setFormData] = useState({
    name: '',
    gender: '',
    dominantHand: '',
    danRank: '',
    kyuRank: '',
    karutaClub: '',
    role: 'USER',
    remarks: '',
  });

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
        role: player.role || 'USER',
        remarks: player.remarks || '',
      });
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
        danRank = '四段'; // A級はデフォルトで四段
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

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!formData.name.trim()) {
      setError('名前は必須です');
      return;
    }

    try {
      setSubmitting(true);
      if (id) {
        await playerAPI.update(id, formData);
        alert('選手情報を更新しました');
        navigate(`/players/${id}`);
      } else {
        const response = await playerAPI.create(formData);
        alert('選手を登録しました');
        navigate(`/players/${response.data.id}`);
      }
    } catch (err) {
      console.error('Failed to save player:', err);
      setError(id ? '選手情報の更新に失敗しました' : '選手の登録に失敗しました');
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
                <option value="MALE">男性</option>
                <option value="FEMALE">女性</option>
                <option value="OTHER">その他</option>
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
                <option value="RIGHT">右利き</option>
                <option value="LEFT">左利き</option>
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

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              ロール
            </label>
            <select
              name="role"
              value={formData.role}
              onChange={handleChange}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            >
              <option value="USER">一般ユーザー</option>
              <option value="ADMIN">管理者</option>
            </select>
          </div>

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
