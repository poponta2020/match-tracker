import { useState, useEffect } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lineAPI } from '../../api';
import { organizationAPI } from '../../api/organizations';
import { Bell, MessageSquare, Copy, RefreshCw, ExternalLink, AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';

const NotificationSettings = () => {
  const { currentPlayer } = useAuth();
  const playerId = currentPlayer?.id;
  const isAdmin = currentPlayer?.role === 'ADMIN' || currentPlayer?.role === 'SUPER_ADMIN';

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // 団体
  const [organizations, setOrganizations] = useState([]);
  const [playerOrgIds, setPlayerOrgIds] = useState([]);

  // LINE（選手用）
  const [lineStatus, setLineStatus] = useState(null);
  const [linePrefsMap, setLinePrefsMap] = useState({});
  const [linkingCode, setLinkingCode] = useState(null);
  const [codeExpiresAt, setCodeExpiresAt] = useState(null);
  const [friendAddUrl, setFriendAddUrl] = useState(null);
  const [lineActionLoading, setLineActionLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  // LINE（管理者用）
  const [adminLineStatus, setAdminLineStatus] = useState(null);
  const [adminLinkingCode, setAdminLinkingCode] = useState(null);
  const [adminCodeExpiresAt, setAdminCodeExpiresAt] = useState(null);
  const [adminFriendAddUrl, setAdminFriendAddUrl] = useState(null);
  const [adminLineActionLoading, setAdminLineActionLoading] = useState(false);
  const [adminCopied, setAdminCopied] = useState(false);

  useEffect(() => {
    if (!playerId) return;
    fetchData();
  }, [playerId]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const promises = [
        lineAPI.getStatus(playerId),
        lineAPI.getPreferences(playerId),
        organizationAPI.getAll().catch(() => ({ data: [] })),
        organizationAPI.getPlayerOrganizations(playerId).catch(() => ({ data: [] })),
      ];
      if (isAdmin) {
        promises.push(lineAPI.getStatus(playerId, 'ADMIN').catch(() => ({ data: null })));
      }
      const results = await Promise.all(promises);
      const [lineStatusRes, linePrefsRes, orgsRes, playerOrgsRes] = results;

      // 団体情報
      setOrganizations(orgsRes.data || []);
      const pOrgIds = (playerOrgsRes.data || []).map(o => o.id);
      setPlayerOrgIds(pOrgIds);

      // LINE設定（選手用）
      setLineStatus(lineStatusRes.data);
      const lineMap = {};
      const linePrefs = Array.isArray(linePrefsRes.data) ? linePrefsRes.data : (linePrefsRes.data ? [linePrefsRes.data] : []);
      for (const p of linePrefs) {
        lineMap[p.organizationId] = p;
      }
      setLinePrefsMap(lineMap);

      if (lineStatusRes.data.friendAddUrl) {
        setFriendAddUrl(lineStatusRes.data.friendAddUrl);
      }

      // LINE設定（管理者用）
      if (isAdmin && results[4]?.data) {
        setAdminLineStatus(results[4].data);
        if (results[4].data.friendAddUrl) {
          setAdminFriendAddUrl(results[4].data.friendAddUrl);
        }
      }
    } catch (err) {
      console.error('通知設定の取得に失敗:', err);
      setError('通知設定の取得に失敗しました');
    } finally {
      setLoading(false);
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
    } catch {
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

  const handleToggleLinePreference = async (orgId, key) => {
    const pref = linePrefsMap[orgId];
    if (!pref) return;
    const updated = { ...pref, [key]: !pref[key] };
    setLinePrefsMap(prev => ({ ...prev, [orgId]: updated }));
    try {
      await lineAPI.updatePreferences({ ...updated, playerId, organizationId: orgId });
    } catch {
      setLinePrefsMap(prev => ({ ...prev, [orgId]: pref }));
      setError('設定の更新に失敗しました');
    }
  };

  // === 管理者用LINE ハンドラー ===

  const handleEnableAdminLine = async () => {
    try {
      setAdminLineActionLoading(true);
      setError(null);
      const res = await lineAPI.enable(playerId, 'ADMIN');
      setAdminLinkingCode(res.data.linkingCode);
      setAdminCodeExpiresAt(res.data.codeExpiresAt);
      setAdminFriendAddUrl(res.data.friendAddUrl);
      const statusRes = await lineAPI.getStatus(playerId, 'ADMIN');
      setAdminLineStatus(statusRes.data);
      setSuccessMessage('管理者用チャネルが割り当てられました。友だち追加してコードを入力してください。');
    } catch (err) {
      setError(err.response?.data?.message || '管理者用LINE通知の有効化に失敗しました');
    } finally {
      setAdminLineActionLoading(false);
    }
  };

  const handleDisableAdminLine = async () => {
    if (!confirm('管理者用LINE通知を無効にしますか？')) return;
    try {
      setAdminLineActionLoading(true);
      setError(null);
      await lineAPI.disable(playerId, 'ADMIN');
      setAdminLinkingCode(null);
      setAdminCodeExpiresAt(null);
      setAdminFriendAddUrl(null);
      const statusRes = await lineAPI.getStatus(playerId, 'ADMIN');
      setAdminLineStatus(statusRes.data);
      setSuccessMessage('管理者用LINE通知を無効にしました。');
    } catch {
      setError('管理者用LINE通知の無効化に失敗しました');
    } finally {
      setAdminLineActionLoading(false);
    }
  };

  const handleReissueAdminCode = async () => {
    try {
      setAdminLineActionLoading(true);
      setError(null);
      const res = await lineAPI.reissueCode(playerId, 'ADMIN');
      setAdminLinkingCode(res.data.linkingCode);
      setAdminCodeExpiresAt(res.data.codeExpiresAt);
      setSuccessMessage('管理者用の新しいコードを発行しました。');
    } catch (err) {
      setError(err.response?.data?.message || '管理者用コード再発行に失敗しました');
    } finally {
      setAdminLineActionLoading(false);
    }
  };

  const handleCopyAdminCode = () => {
    if (adminLinkingCode) {
      navigator.clipboard.writeText(adminLinkingCode);
      setAdminCopied(true);
      setTimeout(() => setAdminCopied(false), 2000);
    }
  };

  // SUPER_ADMIN専用の管理者通知設定（organizationId=0 を使用）
  const handleToggleAdminLinePref = async (key) => {
    const currentPref = linePrefsMap[0] || { playerId, organizationId: 0, adminWaitlistUpdate: true, adminSameDayConfirmation: true, adminSameDayCancel: true };
    const currentVal = currentPref[key] ?? true;
    const updated = { ...currentPref, [key]: !currentVal };
    setLinePrefsMap(prev => ({ ...prev, 0: updated }));
    try {
      await lineAPI.updatePreferences({ ...updated, playerId, organizationId: 0 });
    } catch {
      setLinePrefsMap(prev => ({ ...prev, 0: currentPref }));
      setError('設定の更新に失敗しました');
    }
  };

  if (loading) return <LoadingScreen />;

  const showOrgHeaders = playerOrgIds.length > 1;

  const getLineTypesForOrg = (org) => {
    const types = [];
    if (org?.deadlineType !== 'SAME_DAY') {
      types.push({ key: 'lotteryResult', label: '抽選結果' });
    }
    types.push(
      { key: 'waitlistOffer', label: 'キャンセル待ち連絡' },
      { key: 'offerExpired', label: 'オファー期限切れ' },
      { key: 'matchPairing', label: '対戦組み合わせ' },
      { key: 'practiceReminder', label: '参加予定リマインダー' },
      { key: 'deadlineReminder', label: '締め切りリマインダー' },
      { key: 'sameDayConfirmation', label: '参加者確定通知（当日12:00）' },
      { key: 'sameDayCancel', label: '当日キャンセル通知' },
      { key: 'sameDayVacancy', label: '空き募集通知' },
      { key: 'mentorComment', label: 'メンターコメント通知' },
    );
    return types;
  };

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

  const playerOrgs = organizations.filter(o => playerOrgIds.includes(o.id));

  return (
    <div className="max-w-lg mx-auto p-4 space-y-6">
      <h1 className="text-xl font-bold text-gray-800 flex items-center gap-2">
        <Bell className="h-6 w-6" />
        通知設定
      </h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}
      {successMessage && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600 flex-shrink-0" />
          <p className="text-green-800 text-sm">{successMessage}</p>
        </div>
      )}

      {/* ========== LINE通知セクション（選手用） ========== */}
      <div className="bg-white rounded-lg border p-4 space-y-4">
        <h2 className="font-semibold text-gray-700 flex items-center gap-2">
          <MessageSquare className="h-5 w-5" />
          {isAdmin ? '選手用LINE通知' : 'LINE通知'}
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

      {/* LINE通知種別ごとのON/OFF（団体別） */}
      {lineStatus?.enabled && Object.keys(linePrefsMap).length > 0 && (
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <h2 className="font-semibold text-gray-700">LINE通知種別</h2>
          {playerOrgs.map(org => (
            <div key={org.id} className="space-y-1">
              {showOrgHeaders && (
                <h3 className="text-sm font-medium text-gray-600 mb-2 flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: org.color }} />
                  {org.name}
                </h3>
              )}
              {getLineTypesForOrg(org).map(({ key, label }) => (
                <div key={`${org.id}-${key}`} className="flex items-center justify-between py-2 border-b last:border-b-0">
                  <span className="text-sm text-gray-700">{label}</span>
                  <Toggle
                    enabled={linePrefsMap[org.id]?.[key] ?? true}
                    onClick={() => handleToggleLinePreference(org.id, key)}
                    color="bg-[#06C755]"
                  />
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      {/* ========== 管理者用LINE通知セクション ========== */}
      {isAdmin && (
        <div className="bg-white rounded-lg border p-4 space-y-4">
          <h2 className="font-semibold text-gray-700 flex items-center gap-2">
            <MessageSquare className="h-5 w-5" />
            管理者用LINE通知
          </h2>

          {!adminLineStatus?.enabled ? (
            <div className="space-y-3">
              <p className="text-sm text-gray-600">
                管理者用LINE通知を有効にすると、キャンセル待ち状況や当日確認まとめをLINEで受け取れます。
              </p>
              <button
                onClick={handleEnableAdminLine}
                disabled={adminLineActionLoading}
                className="w-full bg-[#06C755] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#05b54d] disabled:opacity-50 transition-colors"
              >
                {adminLineActionLoading ? '処理中...' : '管理者用LINE通知を有効にする'}
              </button>
            </div>
          ) : adminLineStatus?.linked ? (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 bg-green-500 rounded-full" />
                <span className="text-sm font-medium text-green-700">LINE連携済み</span>
              </div>
              <button
                onClick={handleDisableAdminLine}
                disabled={adminLineActionLoading}
                className="w-full bg-gray-100 text-gray-700 py-2 px-4 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
              >
                管理者用LINE通知を無効にする
              </button>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 bg-yellow-500 rounded-full" />
                <span className="text-sm font-medium text-yellow-700">連携待ち</span>
              </div>

              {adminFriendAddUrl && (
                <div>
                  <p className="text-sm text-gray-600 mb-2">1. 友だち追加</p>
                  <a
                    href={adminFriendAddUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center justify-center gap-2 w-full bg-[#06C755] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#05b54d] transition-colors"
                  >
                    <ExternalLink className="h-4 w-4" />
                    友だち追加する
                  </a>
                </div>
              )}

              {adminLinkingCode && (
                <div>
                  <p className="text-sm text-gray-600 mb-2">2. 連携コードをLINEトークに貼り付け</p>
                  <div className="flex items-center gap-2">
                    <div className="flex-1 bg-gray-100 border rounded-lg px-4 py-3 font-mono text-lg text-center tracking-widest">
                      {adminLinkingCode}
                    </div>
                    <button
                      onClick={handleCopyAdminCode}
                      className="p-3 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
                      title="コピー"
                    >
                      {adminCopied ? <Check className="h-5 w-5 text-green-600" /> : <Copy className="h-5 w-5 text-gray-600" />}
                    </button>
                  </div>
                  {adminCodeExpiresAt && (
                    <p className="text-xs text-gray-500 mt-1">
                      有効期限: {new Date(adminCodeExpiresAt).toLocaleString('ja-JP')}
                    </p>
                  )}
                </div>
              )}

              <div className="flex gap-2">
                <button
                  onClick={handleReissueAdminCode}
                  disabled={adminLineActionLoading}
                  className="flex-1 flex items-center justify-center gap-1 bg-gray-100 text-gray-700 py-2 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
                >
                  <RefreshCw className="h-4 w-4" />
                  コード再発行
                </button>
                <button
                  onClick={handleDisableAdminLine}
                  disabled={adminLineActionLoading}
                  className="flex-1 bg-gray-100 text-gray-700 py-2 px-3 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
                >
                  無効にする
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 管理者用LINE連携済みの場合: 管理者通知種別設定 */}
      {isAdmin && adminLineStatus?.linked && (
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <h2 className="font-semibold text-gray-700">管理者通知種別</h2>
          <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <span className="text-sm text-gray-700">キャンセル待ち状況通知</span>
            <Toggle
              enabled={linePrefsMap[0]?.adminWaitlistUpdate ?? true}
              onClick={() => handleToggleAdminLinePref('adminWaitlistUpdate')}
              color="bg-[#06C755]"
            />
          </div>
          <div className="flex items-center justify-between py-2 border-b last:border-b-0">
            <span className="text-sm text-gray-700">参加者確定通知（当日12:00）</span>
            <Toggle
              enabled={linePrefsMap[0]?.adminSameDayConfirmation ?? true}
              onClick={() => handleToggleAdminLinePref('adminSameDayConfirmation')}
              color="bg-[#06C755]"
            />
          </div>
          <div className="flex items-center justify-between py-2">
            <div>
              <span className="text-sm text-gray-700">当日キャンセル・参加・空き枠通知</span>
              <p className="text-xs text-gray-500">当日のキャンセル・先着参加・空き枠情報を管理者用LINEに送信</p>
            </div>
            <Toggle
              enabled={linePrefsMap[0]?.adminSameDayCancel ?? true}
              onClick={() => handleToggleAdminLinePref('adminSameDayCancel')}
              color="bg-[#06C755]"
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default NotificationSettings;
