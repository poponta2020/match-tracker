import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { practiceAPI, playerAPI, venueAPI } from '../../api';
import { isSuperAdmin } from '../../utils/auth';

const PracticeForm = () => {
  const navigate = useNavigate();
  const { id } = useParams();
  const isEdit = Boolean(id);

  // 権限チェック: スーパー管理者のみアクセス可能
  useEffect(() => {
    if (!isSuperAdmin()) {
      alert('この機能はスーパー管理者のみ利用できます');
      navigate('/practice');
    }
  }, [navigate]);

  const [players, setPlayers] = useState([]);
  const [venues, setVenues] = useState([]);
  const [formData, setFormData] = useState({
    sessionDate: new Date().toISOString().split('T')[0],
    venueId: null,
    totalMatches: 10,
    notes: '',
    participantIds: []
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    fetchPlayers();
    fetchVenues();
    if (isEdit) {
      fetchSession();
    }
  }, [id, isEdit]);

  const fetchPlayers = async () => {
    try {
      const response = await playerAPI.getAll();
      setPlayers(response.data);
    } catch (err) {
      console.error('Error fetching players:', err);
      setError('選手情報の取得に失敗しました');
    }
  };

  const fetchVenues = async () => {
    try {
      const response = await venueAPI.getAll();
      setVenues(response.data);
    } catch (err) {
      console.error('Error fetching venues:', err);
    }
  };

  const fetchSession = async () => {
    try {
      const response = await practiceAPI.getById(id);
      const session = response.data;
      setFormData({
        sessionDate: session.sessionDate,
        venueId: session.venueId || null,
        totalMatches: session.totalMatches,
        notes: session.notes || '',
        participantIds: session.participants?.map(p => p.id) || []
      });
    } catch (err) {
      console.error('Error fetching session:', err);
      setError('練習記録の取得に失敗しました');
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleVenueChange = (e) => {
    const venueId = e.target.value ? parseInt(e.target.value) : null;
    const selectedVenue = venues.find(v => v.id === venueId);

    if (selectedVenue) {
      // 会場選択時に試合数を自動設定
      setFormData(prev => ({
        ...prev,
        venueId,
        totalMatches: selectedVenue.defaultMatchCount
      }));
    } else {
      setFormData(prev => ({
        ...prev,
        venueId: null
      }));
    }
  };

  const handleParticipantToggle = (playerId) => {
    setFormData(prev => ({
      ...prev,
      participantIds: prev.participantIds.includes(playerId)
        ? prev.participantIds.filter(id => id !== playerId)
        : [...prev.participantIds, playerId]
    }));
  };

  const handleSelectAll = () => {
    setFormData(prev => ({
      ...prev,
      participantIds: players.map(p => p.id)
    }));
  };

  const handleDeselectAll = () => {
    setFormData(prev => ({
      ...prev,
      participantIds: []
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    setLoading(true);

    try {
      const submitData = {
        ...formData,
        totalMatches: parseInt(formData.totalMatches)
      };

      if (isEdit) {
        await practiceAPI.update(id, submitData);
      } else {
        await practiceAPI.create(submitData);
      }

      navigate('/practice');
    } catch (err) {
      setError(err.response?.data?.message || '練習記録の保存に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <h1 className="text-3xl font-bold text-gray-900 mb-6">
        {isEdit ? '練習記録編集' : '練習記録登録'}
      </h1>

      {error && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="bg-white shadow-md rounded-lg p-6 space-y-6">
        {/* 練習日 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            練習日 <span className="text-red-500">*</span>
          </label>
          <input
            type="date"
            name="sessionDate"
            value={formData.sessionDate}
            onChange={handleChange}
            required
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* 会場選択 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            会場
          </label>
          <select
            name="venueId"
            value={formData.venueId || ''}
            onChange={handleVenueChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">会場を選択してください</option>
            {venues.map((venue) => (
              <option key={venue.id} value={venue.id}>
                {venue.name} ({venue.defaultMatchCount}試合)
              </option>
            ))}
          </select>
          {formData.venueId && (
            <p className="mt-1 text-sm text-gray-500">
              会場を選択すると、試合数が自動設定されます
            </p>
          )}
        </div>

        {/* 予定試合数 */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            予定試合数 <span className="text-red-500">*</span>
          </label>
          <input
            type="number"
            name="totalMatches"
            value={formData.totalMatches}
            onChange={handleChange}
            min="1"
            max="100"
            required
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* 参加者選択 */}
        <div>
          <div className="flex justify-between items-center mb-2">
            <label className="block text-sm font-medium text-gray-700">
              参加者 <span className="text-red-500">*</span>
              <span className="text-sm text-gray-500 ml-2">
                ({formData.participantIds.length}名選択)
              </span>
            </label>
            <div className="space-x-2">
              <button
                type="button"
                onClick={handleSelectAll}
                className="text-sm text-blue-600 hover:text-blue-800"
              >
                全選択
              </button>
              <button
                type="button"
                onClick={handleDeselectAll}
                className="text-sm text-gray-600 hover:text-gray-800"
              >
                全解除
              </button>
            </div>
          </div>
          <div className="border border-gray-300 rounded-md p-4 max-h-64 overflow-y-auto">
            {players.length === 0 ? (
              <p className="text-gray-500 text-center">選手が登録されていません</p>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                {players.map((player) => (
                  <label
                    key={player.id}
                    className="flex items-center space-x-2 cursor-pointer hover:bg-gray-50 p-2 rounded"
                  >
                    <input
                      type="checkbox"
                      checked={formData.participantIds.includes(player.id)}
                      onChange={() => handleParticipantToggle(player.id)}
                      className="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                    />
                    <span className="text-sm text-gray-900">{player.name}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* メモ */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            メモ
          </label>
          <textarea
            name="notes"
            value={formData.notes}
            onChange={handleChange}
            rows="4"
            maxLength={1000}
            placeholder="練習の内容や特記事項など"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* ボタン */}
        <div className="flex justify-end space-x-4">
          <button
            type="button"
            onClick={() => navigate('/practice')}
            className="px-6 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
          >
            キャンセル
          </button>
          <button
            type="submit"
            disabled={loading}
            className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors disabled:bg-gray-400"
          >
            {loading ? '保存中...' : (isEdit ? '更新' : '登録')}
          </button>
        </div>
      </form>
    </div>
  );
};

export default PracticeForm;
