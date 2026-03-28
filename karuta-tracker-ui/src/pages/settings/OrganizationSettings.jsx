import { useState, useEffect } from 'react';
import { organizationAPI } from '../../api/organizations';
import { getCurrentPlayer } from '../../utils/auth';
import { Building2, AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const OrganizationSettings = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [organizations, setOrganizations] = useState([]);
  const [selectedOrgIds, setSelectedOrgIds] = useState([]);

  const currentPlayer = getCurrentPlayer();

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [orgsRes, playerOrgsRes] = await Promise.all([
        organizationAPI.getAll(),
        organizationAPI.getPlayerOrganizations(currentPlayer.id),
      ]);
      setOrganizations(orgsRes.data);
      setSelectedOrgIds(playerOrgsRes.data.map(o => o.id));
    } catch (e) {
      setError('データの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = (orgId) => {
    setSelectedOrgIds(prev => {
      if (prev.includes(orgId)) {
        // 最低1つは必須
        if (prev.length <= 1) return prev;
        return prev.filter(id => id !== orgId);
      }
      return [...prev, orgId];
    });
    setSuccess(null);
    setError(null);
  };

  const handleSave = async () => {
    if (selectedOrgIds.length === 0) {
      setError('参加する練習会を最低1つ選択してください');
      return;
    }

    try {
      setSaving(true);
      setError(null);
      await organizationAPI.updatePlayerOrganizations(currentPlayer.id, selectedOrgIds);
      setSuccess('参加練習会を更新しました');
    } catch (e) {
      setError('更新に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <LoadingScreen />;

  return (
    <div className="max-w-lg mx-auto p-4">
      <div className="flex items-center gap-2 mb-6">
        <Building2 className="w-6 h-6 text-gray-600" />
        <h1 className="text-xl font-bold">参加練習会の設定</h1>
      </div>

      <p className="text-sm text-gray-600 mb-4">
        参加する練習会を選択してください。選択していない練習会の練習日や通知は表示されません。
      </p>

      <div className="space-y-3 mb-6">
        {organizations.map(org => (
          <label
            key={org.id}
            className={`flex items-center gap-3 p-4 rounded-lg border-2 cursor-pointer transition-colors ${
              selectedOrgIds.includes(org.id)
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-200 bg-white hover:border-gray-300'
            }`}
          >
            <input
              type="checkbox"
              checked={selectedOrgIds.includes(org.id)}
              onChange={() => handleToggle(org.id)}
              className="w-5 h-5 rounded"
            />
            <div className="flex items-center gap-2">
              <span
                className="w-3 h-3 rounded-full"
                style={{ backgroundColor: org.color }}
              />
              <span className="font-medium">{org.name}</span>
            </div>
          </label>
        ))}
      </div>

      {error && (
        <div className="flex items-center gap-2 text-red-600 text-sm mb-4">
          <AlertCircle className="w-4 h-4" />
          {error}
        </div>
      )}

      {success && (
        <div className="flex items-center gap-2 text-green-600 text-sm mb-4">
          <Check className="w-4 h-4" />
          {success}
        </div>
      )}

      <button
        onClick={handleSave}
        disabled={saving || selectedOrgIds.length === 0}
        className="w-full py-3 px-4 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
      >
        {saving ? '保存中...' : '保存'}
      </button>
    </div>
  );
};

export default OrganizationSettings;
