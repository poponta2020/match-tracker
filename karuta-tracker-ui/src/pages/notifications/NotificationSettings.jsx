import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lineAPI, notificationAPI } from '../../api';
import { Bell, MessageSquare, Copy, RefreshCw, ExternalLink, AlertCircle, Check, Info } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

/**
 * Base64 URL-safe文字列をUint8Arrayに変換（VAPID鍵用）
 */
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

const NotificationSettings = () => {
  const { currentPlayer } = useAuth();
  const playerId = currentPlayer?.id;
  const isAdmin = currentPlayer?.role === 'ADMIN' || currentPlayer?.role === 'SUPER_ADMIN';

  // 共通
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Web Push
  const [pushPreferences, setPushPreferences] = useState(null);
  const [pushActionLoading, setPushActionLoading] = useState(false);
  const [browserSupported, setBrowserSupported] = useState(true);
  const [browserBlocked, setBrowserBlocked] = useState(false);

  // LINE
  const [lineStatus, setLineStatus] = useState(null);
  const [linePreferences, setLinePreferences] = useState(null);
  const [linkingCode, setLinkingCode] = useState(null);
  const [codeExpiresAt, setCodeExpiresAt] = useState(null);
  const [friendAddUrl, setFriendAddUrl] = useState(null);
  const [lineActionLoading, setLineActionLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!playerId) return;

    // ブラウザの通知サポートチェック
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
      setBrowserSupported(false);
    } else if (Notification.permission === 'denied') {
      setBrowserBlocked(true);
    }

    fetchData();
  }, [playerId]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [pushRes, lineStatusRes, linePrefsRes] = await Promise.all([
        notificationAPI.getPushPreferences(playerId),
        lineAPI.getStatus(playerId),
        lineAPI.getPreferences(playerId),
      ]);
      setPushPreferences(pushRes.data);
      setLineStatus(lineStatusRes.data);
      setLinePreferences(linePrefsRes.data);
      if (lineStatusRes.data.friendAddUrl) {
        setFriendAddUrl(lineStatusRes.data.friendAddUrl);
      }
    } catch (err) {
      console.error('通知設定の取得に失敗:', err);
      setError('通知設定の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  };

  // === Web Push ハンドラー ===

  const handleEnablePush = async () => {
    try {
      setPushActionLoading(true);
      setError(null);

      // 1. ブラウザ通知許可
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        setBrowserBlocked(true);
        setError('ブラウザの設定から通知を許可してください');
        return;
      }

      // 2. VAPID公開鍵取得
      const keyRes = await notificationAPI.getVapidPublicKey();
      const vapidPublicKey = keyRes.data.publicKey;

      // 3. Service Worker登録
      const registration = await navigator.serviceWorker.register('/sw.js');
      await navigator.serviceWorker.ready;

      // 4. Push購読
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidPublicKey),
      });

      const subJson = subscription.toJSON();

      // 5. バックエンドに購読登録
      await notificationAPI.subscribePush({
        playerId,
        endpoint: subJson.endpoint,
        p256dhKey: subJson.keys.p256dh,
        authKey: subJson.keys.auth,
        userAgent: navigator.userAgent,
      });

      // 6. 設定をONに
      const updated = { ...pushPreferences, playerId, enabled: true };
      await notificationAPI.updatePushPreferences(updated);
      setPushPreferences(updated);
      setSuccessMessage('Web Push通知を有効にしました');
    } catch (err) {
      console.error('Web Push有効化に失敗:', err);
      setError('Web Push通知の有効化に失敗しました');
    } finally {
      setPushActionLoading(false);
    }
  };

  const handleDisablePush = async () => {
    try {
      setPushActionLoading(true);
      setError(null);
      const updated = { ...pushPreferences, playerId, enabled: false };
      await notificationAPI.updatePushPreferences(updated);
      setPushPreferences(updated);
      setSuccessMessage('Web Push通知を無効にしました');
    } catch (err) {
      setError('Web Push通知の無効化に失敗しました');
    } finally {
      setPushActionLoading(false);
    }
  };

  const handleTogglePushPreference = async (key) => {
    const updated = { ...pushPreferences, [key]: !pushPreferences[key] };
    setPushPreferences(updated);
    try {
      await notificationAPI.updatePushPreferences({ ...updated, playerId });
    } catch (err) {
      setPushPreferences(pushPreferences);
      setError('設定の更新に失敗しました');
    }
  };

  // === LINE ハンドラー ===

  const handleEnableLine = async () => {
    try {
      setLineActionLoading(true);
      setError(null);
      const res = await lineAPI.enable(playerId);
      setLinkingCode(res.data.linkingCode);
      setCodeExpiresAt(res.data.codeExpiresAt);
      setFriendAddUrl(res.data.friendAddUrl);
      await fetchData();
      setSuccessMessage('チャネルが割り当てられました。友だち追加してコードを入力してください。');
    } catch (err) {
      setError(err.response?.data?.message || 'LINE通知の有効化に失敗しました');
    } finally {
      setLineActionLoading(false);
    }
  };

  const handleDisableLine = async () => {
    if (!confirm('LINE通知を無効にしますか？')) return;
    try {
      setLineActionLoading(true);
      setError(null);
      await lineAPI.disable(playerId);
      setLinkingCode(null);
      setCodeExpiresAt(null);
      setFriendAddUrl(null);
      await fetchData();
      setSuccessMessage('LINE通知を無効にしました。');
    } catch (err) {
      setError('LINE通知の無効化に失敗しました');
    } finally {
      setLineActionLoading(false);
    }
  };

  const handleReissueCode = async () => {
    try {
      setLineActionLoading(true);
      setError(null);
      const res = await lineAPI.reissueCode(playerId);
      setLinkingCode(res.data.linkingCode);
      setCodeExpiresAt(res.data.codeExpiresAt);
      setSuccessMessage('新しいコードを発行しました。');
    } catch (err) {
      setError(err.response?.data?.message || 'コード再発行に失敗しました');
    } finally {
      setLineActionLoading(false);
    }
  };

  const handleCopyCode = () => {
    if (linkingCode) {
      navigator.clipboard.writeText(linkingCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleToggleLinePreference = async (key) => {
    const updated = { ...linePreferences, [key]: !linePreferences[key] };
    setLinePreferences(updated);
    try {
      await lineAPI.updatePreferences({ ...updated, playerId });
    } catch (err) {
      setLinePreferences(linePreferences);
      setError('設定の更新に失敗しました');
    }
  };

  if (loading) return <LoadingScreen />;

  // Web Push通知の種別定義
  const pushNotificationTypes = [
    { key: 'lotteryResult', label: '抽選結果' },
    { key: 'waitlistOffer', label: 'キャンセル待ち繰り上げ' },
    { key: 'offerExpiring', label: '繰り上げ期限切れ警告' },
    { key: 'offerExpired', label: '繰り上げ期限切れ' },
    ...(isAdmin ? [
      { key: 'channelReclaimWarning', label: 'LINEチャネル回収警告' },
      { key: 'densukeUnmatched', label: '伝助未登録者' },
    ] : []),
  ];

  // LINE通知の種別定義
  const lineNotificationTypes = [
    { key: 'lotteryResult', label: '抽選結果' },
    { key: 'waitlistOffer', label: 'キャンセル待ち連絡' },
    { key: 'offerExpired', label: 'オファー期限切れ' },
    { key: 'matchPairing', label: '対戦組み合わせ' },
    { key: 'practiceReminder', label: '参加予定リマインダー' },
    { key: 'deadlineReminder', label: '締め切りリマインダー' },
  ];

  /** トグルボタンの共通コンポーネント */
  const Toggle = ({ enabled, onClick, color = 'bg-blue-600' }) => (
    <button
      onClick={onClick}
      className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
        enabled ? color : 'bg-gray-300'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
          enabled ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  );

  return (
    <div className="max-w-lg mx-auto p-4 space-y-6">
      <h1 className="text-xl font-bold text-gray-800 flex items-center gap-2">
        <Bell className="h-6 w-6" />
        通知設定
      </h1>

      {/* エラー表示 */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}

      {/* 成功メッセージ */}
      {successMessage && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600 flex-shrink-0" />
          <p className="text-green-800 text-sm">{successMessage}</p>
        </div>
      )}

      {/* ========== Web Push通知セクション ========== */}
      <div className="bg-white rounded-lg border p-4 space-y-4">
        <h2 className="font-semibold text-gray-700 flex items-center gap-2">
          <Bell className="h-5 w-5" />
          Web Push通知
        </h2>

        {!browserSupported ? (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 flex items-center gap-2">
            <Info className="h-5 w-5 text-yellow-600 flex-shrink-0" />
            <p className="text-yellow-800 text-sm">お使いのブラウザはWeb Push通知に対応していません</p>
          </div>
        ) : browserBlocked && !pushPreferences?.enabled ? (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-3 flex items-center gap-2">
            <Info className="h-5 w-5 text-yellow-600 flex-shrink-0" />
            <p className="text-yellow-800 text-sm">ブラウザの設定から通知を許可してください</p>
          </div>
        ) : !pushPreferences?.enabled ? (
          <div className="space-y-3">
            <p className="text-sm text-gray-600">
              Web Push通知を有効にすると、アプリを開いていなくても通知を受け取れます。
            </p>
            <button
              onClick={handleEnablePush}
              disabled={pushActionLoading}
              className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              {pushActionLoading ? '処理中...' : 'Web Push通知を有効にする'}
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 bg-blue-500 rounded-full" />
                <span className="text-sm font-medium text-blue-700">有効</span>
              </div>
              <button
                onClick={handleDisablePush}
                disabled={pushActionLoading}
                className="bg-gray-100 text-gray-700 py-1.5 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                無効にする
              </button>
            </div>

            {/* 種別トグル */}
            <div className="space-y-1">
              <h3 className="text-sm font-medium text-gray-600 mb-2">通知種別</h3>
              {pushNotificationTypes.map(({ key, label }) => (
                <div key={key} className="flex items-center justify-between py-2 border-b last:border-b-0">
                  <span className="text-sm text-gray-700">{label}</span>
                  <Toggle
                    enabled={pushPreferences[key]}
                    onClick={() => handleTogglePushPreference(key)}
                    color="bg-blue-600"
                  />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* ========== LINE通知セクション ========== */}
      <div className="bg-white rounded-lg border p-4 space-y-4">
        <h2 className="font-semibold text-gray-700 flex items-center gap-2">
          <MessageSquare className="h-5 w-5" />
          LINE通知
        </h2>

        {!lineStatus?.enabled ? (
          <div className="space-y-3">
            <p className="text-sm text-gray-600">
              LINE通知を有効にすると、抽選結果やキャンセル待ちの連絡をLINEで受け取れます。
            </p>
            <button
              onClick={handleEnableLine}
              disabled={lineActionLoading}
              className="w-full bg-[#06C755] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#05b54d] disabled:opacity-50 transition-colors"
            >
              {lineActionLoading ? '処理中...' : 'LINE通知を有効にする'}
            </button>
          </div>
        ) : lineStatus?.linked ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 bg-green-500 rounded-full" />
              <span className="text-sm font-medium text-green-700">LINE連携済み</span>
            </div>
            <button
              onClick={handleDisableLine}
              disabled={lineActionLoading}
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
                disabled={lineActionLoading}
                className="flex-1 flex items-center justify-center gap-1 bg-gray-100 text-gray-700 py-2 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                <RefreshCw className="h-4 w-4" />
                コード再発行
              </button>
              <button
                onClick={handleDisableLine}
                disabled={lineActionLoading}
                className="flex-1 bg-gray-100 text-gray-700 py-2 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                無効にする
              </button>
            </div>
          </div>
        )}
      </div>

      {/* LINE通知種別ごとのON/OFF */}
      {lineStatus?.enabled && linePreferences && (
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <h2 className="font-semibold text-gray-700">LINE通知種別</h2>
          {lineNotificationTypes.map(({ key, label }) => (
            <div key={key} className="flex items-center justify-between py-2 border-b last:border-b-0">
              <span className="text-sm text-gray-700">{label}</span>
              <Toggle
                enabled={linePreferences[key]}
                onClick={() => handleToggleLinePreference(key)}
                color="bg-[#06C755]"
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default NotificationSettings;
