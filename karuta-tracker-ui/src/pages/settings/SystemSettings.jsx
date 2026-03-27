import { useState, useEffect, useMemo } from 'react';
import { systemSettingsAPI } from '../../api';
import { Settings, AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const SystemSettings = () => {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [showConfirm, setShowConfirm] = useState(false);

  // 設定値
  const [deadlineDays, setDeadlineDays] = useState(0);
  const [noDeadline, setNoDeadline] = useState(false);
  const [reservePercent, setReservePercent] = useState(30);

  useEffect(() => {
    fetchSettings();
  }, []);

  const fetchSettings = async () => {
    try {
      setLoading(true);
      const res = await systemSettingsAPI.getAll();
      const settings = res.data || [];
      for (const s of settings) {
        if (s.settingKey === 'lottery_deadline_days_before') {
          const val = parseInt(s.settingValue, 10);
          if (val === -1) {
            setNoDeadline(true);
            setDeadlineDays(0);
          } else {
            setNoDeadline(false);
            setDeadlineDays(val);
          }
        }
        if (s.settingKey === 'lottery_normal_reserve_percent') {
          setReservePercent(parseInt(s.settingValue, 10));
        }
      }
    } catch {
      setError('設定の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // 締め切りプレビュー
  const deadlinePreview = useMemo(() => {
    if (noDeadline) return '締め切りなし（いつでも登録変更可能）';
    const now = new Date();
    const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    const year = nextMonth.getFullYear();
    const month = nextMonth.getMonth() + 1;
    const firstDay = new Date(year, month - 1, 1);
    let deadlineDate;
    if (deadlineDays === 0) {
      deadlineDate = new Date(firstDay);
      deadlineDate.setDate(deadlineDate.getDate() - 1);
    } else {
      deadlineDate = new Date(firstDay);
      deadlineDate.setDate(deadlineDate.getDate() - deadlineDays);
    }
    return `${deadlineDate.getMonth() + 1}月${deadlineDate.getDate()}日`;
  }, [noDeadline, deadlineDays]);

  const handleSave = async () => {
    setShowConfirm(false);
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const deadlineValue = noDeadline ? '-1' : String(deadlineDays);
      await Promise.all([
        systemSettingsAPI.update('lottery_deadline_days_before', deadlineValue),
        systemSettingsAPI.update('lottery_normal_reserve_percent', String(reservePercent)),
      ]);
      setSuccess('保存しました');
      setTimeout(() => setSuccess(null), 3000);
    } catch {
      setError('保存に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  const handleDeadlineDaysChange = (e) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 0 && val <= 20) {
      setDeadlineDays(val);
    }
  };

  const handleReservePercentChange = (e) => {
    const val = parseInt(e.target.value, 10);
    if (!isNaN(val) && val >= 0 && val <= 100) {
      setReservePercent(val);
    }
  };

  if (loading) return <LoadingScreen />;

  return (
    <div className="max-w-lg mx-auto p-4 space-y-4">
      <h1 className="text-xl font-bold text-gray-800 flex items-center gap-2">
        <Settings className="h-6 w-6" />
        システム設定
      </h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {success && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600" />
          <p className="text-green-800 text-sm">{success}</p>
        </div>
      )}

      {/* 抽選締め切り日数 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <h2 className="font-semibold text-gray-800">抽選締め切り</h2>
        <p className="text-sm text-gray-500">対象月の初日から何日前を締め切りにするか設定します</p>

        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={noDeadline}
            onChange={(e) => setNoDeadline(e.target.checked)}
            className="w-4 h-4 rounded border-gray-300 text-[#4a6b5a] focus:ring-[#4a6b5a]"
          />
          <span className="text-sm text-gray-700">締め切りなし</span>
        </label>

        <div className="flex items-center gap-2">
          <input
            type="number"
            min="0"
            max="20"
            value={deadlineDays}
            onChange={handleDeadlineDaysChange}
            disabled={noDeadline}
            className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center disabled:bg-gray-100 disabled:text-gray-400"
          />
          <span className="text-sm text-gray-700">日前</span>
        </div>

        <div className="bg-gray-50 rounded-lg p-3">
          <p className="text-sm text-gray-600">
            来月の締め切り: <span className="font-medium text-gray-800">{deadlinePreview}</span>
          </p>
        </div>
      </div>

      {/* 一般枠の最低保証割合 */}
      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <h2 className="font-semibold text-gray-800">一般枠の最低保証割合</h2>
        <p className="text-sm text-gray-500">抽選時に一般枠として最低限確保する割合です</p>

        <div className="flex items-center gap-2">
          <input
            type="number"
            min="0"
            max="100"
            value={reservePercent}
            onChange={handleReservePercentChange}
            className="w-20 px-3 py-2 border border-gray-300 rounded-lg text-center"
          />
          <span className="text-sm text-gray-700">%</span>
        </div>
      </div>

      {/* 保存ボタン */}
      <button
        onClick={() => setShowConfirm(true)}
        disabled={saving}
        className="w-full py-3 bg-[#4a6b5a] text-white rounded-lg font-medium hover:bg-[#3d5a4c] transition-colors disabled:opacity-50"
      >
        {saving ? '保存中...' : '保存'}
      </button>

      {/* 確認ダイアログ */}
      {showConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 mx-4 max-w-sm w-full space-y-4">
            <h3 className="font-semibold text-gray-800">設定を変更しますか？</h3>
            <p className="text-sm text-gray-600">
              変更した設定は即座に反映されます。
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setShowConfirm(false)}
                className="flex-1 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50 transition-colors"
              >
                キャンセル
              </button>
              <button
                onClick={handleSave}
                className="flex-1 py-2 bg-[#4a6b5a] text-white rounded-lg hover:bg-[#3d5a4c] transition-colors"
              >
                変更する
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SystemSettings;
