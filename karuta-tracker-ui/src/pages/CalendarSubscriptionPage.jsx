import { useState, useEffect, useCallback } from 'react';
import { icalCalendarAPI } from '../api/icalCalendar';
import { Rss, Copy, RefreshCw, AlertCircle, Check } from 'lucide-react';
import LoadingScreen from '../components/LoadingScreen';

const DISPLAY_NAME_MAX_LENGTH = 50;

const CalendarSubscriptionPage = () => {
  const [feedInfo, setFeedInfo] = useState(null);
  const [displayNamesDraft, setDisplayNamesDraft] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [copyFeedbackId, setCopyFeedbackId] = useState(null);

  const applyFeedInfo = useCallback((info) => {
    setFeedInfo(info);
    const draft = {};
    (info.organizationFeeds || []).forEach(feed => {
      draft[feed.organizationId] = feed.displayName ?? '';
    });
    setDisplayNamesDraft(draft);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        const res = await icalCalendarAPI.getFeedInfo();
        if (cancelled) return;
        applyFeedInfo(res.data);
      } catch (e) {
        if (cancelled) return;
        console.error('カレンダー購読情報の取得に失敗:', e);
        setError('カレンダー購読情報の取得に失敗しました');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applyFeedInfo]);

  const copyToClipboard = async (url, cardId) => {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setCopyFeedbackId(cardId);
      setTimeout(() => setCopyFeedbackId((prev) => (prev === cardId ? null : prev)), 2000);
    } catch (e) {
      console.error('クリップボードへのコピーに失敗:', e);
      setError('コピーに失敗しました');
    }
  };

  const handleRegenerate = async () => {
    if (!window.confirm('再発行すると、すべてのカレンダーURL（所属団体別＋ゲスト参加）が一斉に無効になります。 Google/Appleカレンダー側で全カレンダーを再登録する必要があります。続行しますか？')) {
      return;
    }
    try {
      setRegenerating(true);
      setError(null);
      setSuccess(null);
      const res = await icalCalendarAPI.regenerateFeed();
      applyFeedInfo(res.data);
      setSuccess('URLを再発行しました');
    } catch (e) {
      console.error('URL再発行に失敗:', e);
      setError('URL再発行に失敗しました');
    } finally {
      setRegenerating(false);
    }
  };

  const handleDisplayNameChange = (orgId, value) => {
    if (value.length > DISPLAY_NAME_MAX_LENGTH) return;
    setDisplayNamesDraft(prev => ({ ...prev, [orgId]: value }));
    setSuccess(null);
  };

  const handleSaveDisplayNames = async () => {
    if (!feedInfo) return;
    const payload = {};
    for (const feed of feedInfo.organizationFeeds || []) {
      const value = (displayNamesDraft[feed.organizationId] ?? '').trim();
      if (value.length > DISPLAY_NAME_MAX_LENGTH) {
        setError(`表示名は${DISPLAY_NAME_MAX_LENGTH}文字以下にしてください`);
        return;
      }
      payload[String(feed.organizationId)] = value === '' ? null : value;
    }
    try {
      setSaving(true);
      setError(null);
      setSuccess(null);
      const res = await icalCalendarAPI.updateDisplayNames(payload);
      applyFeedInfo(res.data);
      setSuccess('表示名を更新しました');
    } catch (e) {
      console.error('表示名の更新に失敗:', e);
      setError('表示名の更新に失敗しました');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <LoadingScreen />;

  const organizationFeeds = feedInfo?.organizationFeeds ?? [];
  const guestFeed = feedInfo?.guestFeed ?? null;
  const hasOrganizations = organizationFeeds.length > 0;

  const renderFeedCard = (cardId, title, subtitle, url) => {
    const copied = copyFeedbackId === cardId;
    return (
      <div className="bg-white rounded-lg border p-4 space-y-3">
        <div>
          <h2 className="font-semibold text-gray-800">{title}</h2>
          {subtitle && (
            <p className="text-xs text-gray-500 mt-1">{subtitle}</p>
          )}
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">フィードURL</label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              readOnly
              value={url ?? ''}
              onFocus={(e) => e.target.select()}
              className="flex-1 bg-gray-50 border border-gray-300 rounded-lg px-3 py-2 text-sm font-mono text-gray-700"
            />
            <button
              onClick={() => copyToClipboard(url, cardId)}
              disabled={!url}
              className="p-2 bg-gray-100 rounded-lg hover:bg-gray-200 disabled:opacity-50 transition-colors"
              title="コピー"
            >
              {copied ? (
                <Check className="h-5 w-5 text-green-600" />
              ) : (
                <Copy className="h-5 w-5 text-gray-600" />
              )}
            </button>
          </div>
          {copied && (
            <p className="text-xs text-green-600 mt-1">コピーしました</p>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="max-w-lg mx-auto p-4 space-y-6">
      <h1 className="text-xl font-bold text-gray-800 flex items-center gap-2">
        <Rss className="h-6 w-6" />
        カレンダー購読
      </h1>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
          <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
          <p className="text-red-800 text-sm">{error}</p>
        </div>
      )}
      {success && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-3 flex items-center gap-2">
          <Check className="h-5 w-5 text-green-600 flex-shrink-0" />
          <p className="text-green-800 text-sm">{success}</p>
        </div>
      )}

      <div className="bg-white rounded-lg border p-4">
        <p className="text-sm text-gray-600">
          <span className="font-semibold">所属団体ごと（＋ゲスト参加）にURLが分かれています。</span>
          Googleカレンダー等で各URLを別カレンダーとして登録すると、団体ごとに色を分けられます。GoogleカレンダーやAppleカレンダー、Outlookで使えます。
        </p>
      </div>

      {!hasOrganizations && (
        <div className="bg-white rounded-lg border p-4">
          <p className="text-sm text-gray-600">所属団体が登録されていません</p>
        </div>
      )}

      {organizationFeeds.map(feed => {
        const title = feed.displayName ?? feed.organizationName;
        const subtitle = feed.displayName ? `団体名: ${feed.organizationName}` : null;
        return (
          <div key={feed.organizationId}>
            {renderFeedCard(`org-${feed.organizationId}`, title, subtitle, feed.url)}
          </div>
        );
      })}

      {guestFeed && renderFeedCard('guest', 'ゲスト参加', '所属していない団体の練習を表示します', guestFeed.url)}

      {hasOrganizations && (
        <div className="bg-white rounded-lg border p-4 space-y-4">
          <div>
            <h2 className="font-semibold text-gray-700">所属団体ごとの表示名</h2>
            <p className="text-xs text-gray-500 mt-1">
              設定するとカレンダー上の表示が変わります（例: 早稲田カルタ会 → わすら）。空欄なら団体名がそのまま表示されます。
            </p>
          </div>

          <div className="space-y-3">
            {organizationFeeds.map(feed => (
              <div key={feed.organizationId}>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {feed.organizationName}
                </label>
                <input
                  type="text"
                  value={displayNamesDraft[feed.organizationId] ?? ''}
                  onChange={(e) => handleDisplayNameChange(feed.organizationId, e.target.value)}
                  placeholder={feed.organizationName}
                  maxLength={DISPLAY_NAME_MAX_LENGTH}
                  className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            ))}
          </div>

          <button
            onClick={handleSaveDisplayNames}
            disabled={saving}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {saving ? '保存中...' : '表示名を保存'}
          </button>
        </div>
      )}

      <div className="bg-white rounded-lg border p-4">
        <button
          onClick={handleRegenerate}
          disabled={regenerating}
          className="w-full flex items-center justify-center gap-2 bg-gray-100 text-gray-700 py-2 px-4 rounded-lg text-sm hover:bg-gray-200 disabled:opacity-50 transition-colors"
        >
          <RefreshCw className={`h-4 w-4 ${regenerating ? 'animate-spin' : ''}`} />
          {regenerating ? '再発行中...' : 'URLを再発行する'}
        </button>
      </div>
    </div>
  );
};

export default CalendarSubscriptionPage;
