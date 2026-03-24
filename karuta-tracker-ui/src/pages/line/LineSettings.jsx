import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lineAPI } from '../../api/line';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * LINE通知設定画面（ユーザー向け）
 * - LINE連携の有効化/無効化
 * - 友だち追加QRコード/URL表示
 * - 通知種別ごとのON/OFF設定
 */
export default function LineSettings() {
  const { currentPlayer } = useAuth();
  const [status, setStatus] = useState(null);
  const [preferences, setPreferences] = useState(null);
  const [loading, setLoading] = useState(true);
  const [enabling, setEnabling] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (currentPlayer?.id) fetchData();
  }, [currentPlayer]);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [statusRes, prefsRes] = await Promise.all([
        lineAPI.getStatus(currentPlayer.id),
        lineAPI.getPreferences(currentPlayer.id),
      ]);
      setStatus(statusRes.data);
      setPreferences(prefsRes.data);
    } catch (err) {
      console.error('Failed to fetch LINE status:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleEnable = async () => {
    setEnabling(true);
    setError('');
    try {
      const res = await lineAPI.enable(currentPlayer.id);
      setStatus(res.data);
    } catch (err) {
      if (err.response?.status === 503) {
        setError('利用可能なLINEチャネルがありません。管理者にお問い合わせください。');
      } else {
        setError('LINE通知の有効化に失敗しました。');
      }
    } finally {
      setEnabling(false);
    }
  };

  const handleDisable = async () => {
    if (!window.confirm('LINE通知を無効にしますか？')) return;
    try {
      await lineAPI.disable(currentPlayer.id);
      setStatus({ enabled: false, linked: false });
    } catch (err) {
      console.error('Failed to disable LINE:', err);
    }
  };

  const handlePrefChange = async (key, value) => {
    const updated = { ...preferences, [key]: value, playerId: currentPlayer.id };
    setPreferences(updated);
    try {
      await lineAPI.updatePreferences(updated);
    } catch (err) {
      console.error('Failed to update preferences:', err);
    }
  };

  if (loading) return <LoadingScreen />;

  const prefItems = [
    { key: 'lotteryResult', label: '抽選結果' },
    { key: 'waitlistOffer', label: 'キャンセル待ち連絡' },
    { key: 'offerExpired', label: 'オファー期限切れ' },
    { key: 'matchPairing', label: '対戦組み合わせ' },
    { key: 'practiceReminder', label: '参加予定リマインダー' },
    { key: 'deadlineReminder', label: '締め切りリマインダー' },
  ];

  return (
    <div className="max-w-lg mx-auto">
      <h1 className="text-2xl font-bold mb-6">LINE通知設定</h1>

      {/* 連携状態 */}
      <div className="bg-white rounded-lg shadow p-4 mb-6">
        <h2 className="text-lg font-semibold mb-3">連携状態</h2>

        {!status?.enabled ? (
          <div>
            <p className="text-gray-600 mb-4">
              LINE通知を有効にすると、抽選結果やキャンセル待ち連絡などをLINEで受け取れます。
            </p>
            {error && <p className="text-red-500 text-sm mb-3">{error}</p>}
            <button
              onClick={handleEnable}
              disabled={enabling}
              className="w-full bg-green-500 text-white py-2 px-4 rounded-lg hover:bg-green-600 disabled:opacity-50"
            >
              {enabling ? '処理中...' : 'LINE通知を有効にする'}
            </button>
          </div>
        ) : !status?.linked ? (
          <div>
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 mb-4">
              <p className="text-yellow-800 text-sm font-medium mb-2">
                以下のQRコードまたはURLからLINE友だち追加してください
              </p>
              {status.qrCodeUrl && (
                <div className="flex justify-center mb-3">
                  <img src={status.qrCodeUrl} alt="QRコード" className="w-48 h-48" />
                </div>
              )}
              {status.friendAddUrl && (
                <a
                  href={status.friendAddUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-600 hover:underline text-sm break-all"
                >
                  {status.friendAddUrl}
                </a>
              )}
            </div>
            <p className="text-gray-500 text-xs mb-3">
              友だち追加後、この画面をリロードするとステータスが更新されます。
            </p>
            <div className="flex gap-2">
              <button
                onClick={fetchData}
                className="flex-1 bg-blue-500 text-white py-2 px-4 rounded-lg hover:bg-blue-600"
              >
                ステータス更新
              </button>
              <button
                onClick={handleDisable}
                className="flex-1 bg-gray-200 text-gray-700 py-2 px-4 rounded-lg hover:bg-gray-300"
              >
                無効にする
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div className="flex items-center gap-2 mb-3">
              <span className="w-3 h-3 bg-green-500 rounded-full"></span>
              <span className="text-green-700 font-medium">LINE通知: 有効</span>
            </div>
            <button
              onClick={handleDisable}
              className="text-red-500 hover:text-red-700 text-sm"
            >
              LINE通知を無効にする
            </button>
          </div>
        )}
      </div>

      {/* 通知種別設定 */}
      {status?.enabled && preferences && (
        <div className="bg-white rounded-lg shadow p-4">
          <h2 className="text-lg font-semibold mb-3">通知種別</h2>
          <div className="space-y-3">
            {prefItems.map(({ key, label }) => (
              <div key={key} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
                <span className="text-gray-700">{label}</span>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={preferences[key] ?? true}
                    onChange={(e) => handlePrefChange(key, e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-green-500"></div>
                </label>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
