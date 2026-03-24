import { useEffect, useState } from 'react';
import { lineAPI } from '../../api/line';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * LINE通知スケジュール設定画面（管理者向け）
 */
export default function LineScheduleSettings() {
  const [settings, setSettings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const typeLabels = {
    PRACTICE_REMINDER: '参加予定リマインダー',
    DEADLINE_REMINDER: '締め切りリマインダー',
  };

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    setLoading(true);
    try {
      const res = await lineAPI.getScheduleSettings();
      // デフォルト値を設定
      const defaults = [
        { notificationType: 'PRACTICE_REMINDER', enabled: true, daysBefore: [1] },
        { notificationType: 'DEADLINE_REMINDER', enabled: true, daysBefore: [3, 1] },
      ];
      const merged = defaults.map((d) => {
        const existing = res.data.find((s) => s.notificationType === d.notificationType);
        return existing || d;
      });
      setSettings(merged);
    } catch (err) {
      console.error('Failed to fetch settings:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async (index) => {
    const updated = [...settings];
    updated[index] = { ...updated[index], enabled: !updated[index].enabled };
    setSettings(updated);
    await saveSetting(updated[index]);
  };

  const handleDaysChange = (index, value) => {
    const days = value.split(',')
      .map((s) => parseInt(s.trim(), 10))
      .filter((n) => !isNaN(n) && n > 0)
      .sort((a, b) => b - a);
    const updated = [...settings];
    updated[index] = { ...updated[index], daysBefore: days };
    setSettings(updated);
  };

  const handleSave = async (index) => {
    await saveSetting(settings[index]);
  };

  const saveSetting = async (setting) => {
    setSaving(true);
    try {
      await lineAPI.updateScheduleSetting(setting);
    } catch (err) {
      console.error('Failed to save setting:', err);
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <LoadingScreen />;

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold mb-6">LINE通知スケジュール設定</h1>

      <div className="space-y-4">
        {settings.map((setting, index) => (
          <div key={setting.notificationType} className="bg-white rounded-lg shadow p-4">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-lg font-semibold">
                {typeLabels[setting.notificationType] || setting.notificationType}
              </h2>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={setting.enabled}
                  onChange={() => handleToggle(index)}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-green-500"></div>
              </label>
            </div>

            {setting.enabled && (
              <div>
                <label className="block text-sm text-gray-600 mb-1">
                  送信タイミング（対象日の何日前に送信するか、カンマ区切り）
                </label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={setting.daysBefore.join(', ')}
                    onChange={(e) => handleDaysChange(index, e.target.value)}
                    className="flex-1 border rounded-lg px-3 py-2 text-sm"
                    placeholder="3, 1"
                  />
                  <button
                    onClick={() => handleSave(index)}
                    disabled={saving}
                    className="bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-600 text-sm disabled:opacity-50"
                  >
                    保存
                  </button>
                </div>
                <p className="text-xs text-gray-500 mt-1">
                  例: 「3, 1」→ 3日前と1日前の2回送信
                </p>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
