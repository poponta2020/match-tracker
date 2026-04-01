import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lineAPI } from '../../api';
import { MessageSquare, Copy, RefreshCw, ExternalLink, AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import { isSuperAdmin } from '../../utils/auth';

const LineSettings = () => {
  const { currentPlayer } = useAuth();
  const [status, setStatus] = useState(null);
  const [preferences, setPreferences] = useState(null);
  const [linkingCode, setLinkingCode] = useState(null);
  const [codeExpiresAt, setCodeExpiresAt] = useState(null);
  const [friendAddUrl, setFriendAddUrl] = useState(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [error, setError] = useState(null);
  const [copied, setCopied] = useState(false);
  const [message, setMessage] = useState(null);

  const playerId = currentPlayer?.id;

  useEffect(() => {
    if (!playerId) return;
    fetchData();
  }, [playerId]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [statusRes, prefsRes] = await Promise.all([
        lineAPI.getStatus(playerId),
        lineAPI.getPreferences(playerId),
      ]);
      setStatus(statusRes.data);
      setPreferences(prefsRes.data);
      if (statusRes.data.friendAddUrl) {
        setFriendAddUrl(statusRes.data.friendAddUrl);
      }
    } catch (err) {
      console.error('LINE設定の取得に失敗:', err);
      setError('LINE設定の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  const handleEnable = async () => {
    try {
      setActionLoading(true);
      setError(null);
      const res = await lineAPI.enable(playerId);
      setLinkingCode(res.data.linkingCode);
      setCodeExpiresAt(res.data.codeExpiresAt);
      setFriendAddUrl(res.data.friendAddUrl);
      await fetchData();
      setMessage('チャネルが割り当てられました。友だち追加してコードを入力してください。');
    } catch (err) {
      setError(err.response?.data?.message || 'LINE通知の有効化に失敗しました');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDisable = async () => {
    if (!confirm('LINE通知を無効にしますか？')) return;
    try {
      setActionLoading(true);
      setError(null);
      await lineAPI.disable(playerId);
      setLinkingCode(null);
      setCodeExpiresAt(null);
      setFriendAddUrl(null);
      await fetchData();
      setMessage('LINE通知を無効にしました。');
    } catch (err) {
      setError('LINE通知の無効化に失敗しました');
    } finally {
      setActionLoading(false);
    }
  };

  const handleReissueCode = async () => {
    try {
      setActionLoading(true);
      setError(null);
      const res = await lineAPI.reissueCode(playerId);
      setLinkingCode(res.data.linkingCode);
      setCodeExpiresAt(res.data.codeExpiresAt);
      setMessage('新しいコードを発行しました。');
    } catch (err) {
      setError(err.response?.data?.message || 'コード再発行に失敗しました');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCopyCode = () => {
    if (linkingCode) {
      navigator.clipboard.writeText(linkingCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleTogglePreference = async (key) => {
    const updated = { ...preferences, [key]: !preferences[key] };
    setPreferences(updated);
    try {
      await lineAPI.updatePreferences({ ...updated, playerId });
    } catch (err) {
      setPreferences(preferences);
      setError('設定の更新に失敗しました');
    }
  };

  if (loading) return <LoadingScreen />;

  const notificationTypes = [
    { key: 'lotteryResult', label: '抽選結果' },
    { key: 'waitlistOffer', label: 'キャンセル待ち連絡' },
    { key: 'offerExpired', label: 'オファー期限切れ' },
    { key: 'matchPairing', label: '対戦組み合わせ' },
    { key: 'practiceReminder', label: '参加予定リマインダー' },
    { key: 'deadlineReminder', label: '締め切りリマインダー' },
    ...(isSuperAdmin() ? [{ key: 'adminWaitlistUpdate', label: 'キャンセル待ち状況通知（管理者）' }] : []),
  ];

  return (
    <div className="max-w-lg mx-auto p-4 space-y-6">
      <h1 className="text-xl font-bold text-gray-800 flex items-center gap-2">
        <MessageSquare className="h-6 w-6" />
        LINE通知設定
      </h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {message && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600 flex-shrink-0" />
          <p className="text-green-800 text-sm">{message}</p>
        </div>
      )}

      {/* 連携状態 */}
      <div className="bg-white rounded-lg border p-4 space-y-4">
        <h2 className="font-semibold text-gray-700">連携状態</h2>

        {!status?.enabled ? (
          <div className="space-y-3">
            <p className="text-sm text-gray-600">
              LINE通知を有効にすると、抽選結果やキャンセル待ちの連絡をLINEで受け取れます。
            </p>
            <button
              onClick={handleEnable}
              disabled={actionLoading}
              className="w-full bg-[#06C755] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#05b54d] disabled:opacity-50 transition-colors"
            >
              {actionLoading ? '処理中...' : 'LINE通知を有効にする'}
            </button>
          </div>
        ) : status?.linked ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 bg-green-500 rounded-full" />
              <span className="text-sm font-medium text-green-700">LINE連携済み</span>
            </div>
            <button
              onClick={handleDisable}
              disabled={actionLoading}
              className="w-full bg-gray-100 text-gray-700 py-2 px-4 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
            >
              LINE通知を無効にする
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 bg-yellow-500 rounded-full" />
              <span className="text-sm font-medium text-yellow-700">連携待ち</span>
            </div>

            {/* 友だち追加URL */}
            {friendAddUrl && (
              <div>
                <p className="text-sm text-gray-600 mb-2">1. 友だち追加</p>
                <a
                  href={friendAddUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center justify-center gap-2 w-full bg-[#06C755] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#05b54d] transition-colors"
                >
                  <ExternalLink className="h-4 w-4" />
                  友だち追加する
                </a>
              </div>
            )}

            {/* ワンタイムコード */}
            {linkingCode && (
              <div>
                <p className="text-sm text-gray-600 mb-2">2. 連携コードをLINEトークに貼り付け</p>
                <div className="flex items-center gap-2">
                  <div className="flex-1 bg-gray-100 border rounded-lg px-4 py-3 font-mono text-lg text-center tracking-widest">
                    {linkingCode}
                  </div>
                  <button
                    onClick={handleCopyCode}
                    className="p-3 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                    title="コピー"
                  >
                    {copied ? <Check className="h-5 w-5 text-green-600" /> : <Copy className="h-5 w-5 text-gray-600" />}
                  </button>
                </div>
                {codeExpiresAt && (
                  <p className="text-xs text-gray-500 mt-1">
                    有効期限: {new Date(codeExpiresAt).toLocaleString('ja-JP')}
                  </p>
                )}
              </div>
            )}

            <div className="flex gap-2">
              <button
                onClick={handleReissueCode}
                disabled={actionLoading}
                className="flex-1 flex items-center justify-center gap-1 bg-gray-100 text-gray-700 py-2 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                <RefreshCw className="h-4 w-4" />
                コード再発行
              </button>
              <button
                onClick={handleDisable}
                disabled={actionLoading}
                className="flex-1 bg-gray-100 text-gray-700 py-2 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                無効にする
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 通知種別ごとのON/OFF */}
      {status?.enabled && preferences && (
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <h2 className="font-semibold text-gray-700">通知種別</h2>
          {notificationTypes.map(({ key, label }) => (
            <div key={key} className="flex items-center justify-between py-2 border-b last:border-b-0">
              <span className="text-sm text-gray-700">{label}</span>
              <button
                onClick={() => handleTogglePreference(key)}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                  preferences[key] ? 'bg-[#06C755]' : 'bg-gray-300'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    preferences[key] ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default LineSettings;
