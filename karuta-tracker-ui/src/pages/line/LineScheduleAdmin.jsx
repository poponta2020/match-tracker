import { useState, useEffect } from 'react';
import { lineAPI } from '../../api';
import { Clock, AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const LineScheduleAdmin = () => {
  const [settings, setSettings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    try {
      setLoading(true);
      const res = await lineAPI.getScheduleSettings();
      setSettings(res.data);
    } catch (err) {
      setError('設定の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleToggle = async (setting) => {
    try {
      await lineAPI.updateScheduleSettings({
        ...setting,
        enabled: !setting.enabled,
      });
      await fetchSettings();
    } catch (err) {
      setError('設定の更新に失敗しました');
    }
  };

  const handleUpdateDays = async (setting, newDays) => {
    try {
      setError(null);
      await lineAPI.updateScheduleSettings({
        ...setting,
        daysBefore: newDays,
      });
      setMessage('設定を更新しました');
      setTimeout(() => setMessage(null), 2000);
      await fetchSettings();
    } catch (err) {
      setError('設定の更新に失敗しました');
    }
  };

  const typeLabels = {
    PRACTICE_REMINDER: '参加予定リマインダー',
    DEADLINE_REMINDER: '締め切りリマインダー',
  };

  if (loading) return <LoadingScreen />;

  return (
    <div className="max-w-lg mx-auto p-4 space-y-4">
      <h1 className="text-xl font-bold text-gray-800 flex items-center gap-2">
        <Clock className="h-6 w-6" />
        LINE通知スケジュール設定
      </h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {message && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600" />
          <p className="text-green-800 text-sm">{message}</p>
        </div>
      )}

      {settings.map((setting) => (
        <ScheduleSettingCard
          key={setting.notificationType}
          setting={setting}
          label={typeLabels[setting.notificationType] || setting.notificationType}
          onToggle={() => handleToggle(setting)}
          onUpdateDays={(days) => handleUpdateDays(setting, days)}
        />
      ))}

      {settings.length === 0 && (
        <div className="bg-white rounded-lg border p-8 text-center text-gray-500">
          スケジュール設定がまだ登録されていません
        </div>
      )}
    </div>
  );
};

const ScheduleSettingCard = ({ setting, label, onToggle, onUpdateDays }) => {
  const [daysInput, setDaysInput] = useState(setting.daysBefore?.join(', ') || '');

  const handleSaveDays = () => {
    const days = daysInput
      .split(',')
      .map((s) => parseInt(s.trim(), 10))
      .filter((n) => !isNaN(n) && n > 0);
    onUpdateDays(days);
  };

  return (
    <div className="bg-white rounded-lg border p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-gray-700">{label}</h2>
        <button
          onClick={onToggle}
          className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
            setting.enabled ? 'bg-[#06C755]' : 'bg-gray-300'
          }`}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
              setting.enabled ? 'translate-x-6' : 'translate-x-1'
            }`}
          />
        </button>
      </div>

      {setting.enabled && (
        <div className="space-y-2">
          <label className="block text-sm text-gray-600">
            送信日数（カンマ区切り、例: 3, 1）
          </label>
          <div className="flex gap-2">
            <input
              type="text"
              value={daysInput}
              onChange={(e) => setDaysInput(e.target.value)}
              className="flex-1 border rounded-lg px-3 py-2 text-sm"
              placeholder="3, 1"
            />
            <button
              onClick={handleSaveDays}
              className="bg-[#4a6b5a] text-white py-2 px-4 rounded-lg text-sm hover:bg-[#3d5a4c]"
            >
              保存
            </button>
          </div>
          <p className="text-xs text-gray-500">
            対象日の何日前に送信するかを指定します
          </p>
        </div>
      )}
    </div>
  );
};

export default LineScheduleAdmin;
