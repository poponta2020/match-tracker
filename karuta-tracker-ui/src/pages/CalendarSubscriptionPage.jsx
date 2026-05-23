import { useState, useEffect, useCallback } from 'react';
import { icalCalendarAPI } from '../api/icalCalendar';
import { Rss, Copy, RefreshCw, AlertCircle, Check, ChevronDown, ChevronUp } from 'lucide-react';
import LoadingScreen from '../components/LoadingScreen';

const DISPLAY_NAME_MAX_LENGTH = 50;

const AccordionItem = ({ title, isOpen, onToggle, children }) => (
  <div className="border border-gray-200 rounded-lg">
    <button
      type="button"
      onClick={onToggle}
      className="w-full flex items-center justify-between px-3 py-2 text-left text-sm font-medium text-gray-800 hover:bg-gray-50 transition-colors"
      aria-expanded={isOpen}
    >
      <span>{title}</span>
      {isOpen ? (
        <ChevronUp className="h-4 w-4 text-gray-500 flex-shrink-0" />
      ) : (
        <ChevronDown className="h-4 w-4 text-gray-500 flex-shrink-0" />
      )}
    </button>
    {isOpen && (
      <div className="px-3 pb-3 pt-2 text-sm text-gray-700 border-t border-gray-100">
        {children}
      </div>
    )}
  </div>
);

const CalendarSubscriptionPage = () => {
  const [feedInfo, setFeedInfo] = useState(null);
  const [displayNamesDraft, setDisplayNamesDraft] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [copyFeedbackId, setCopyFeedbackId] = useState(null);
  const [openAccordionKey, setOpenAccordionKey] = useState(null);

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

  const toggleAccordion = (key) => {
    setOpenAccordionKey(prev => (prev === key ? null : key));
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
          {copied ? (
            <p className="text-xs text-green-600 mt-1">コピーしました</p>
          ) : (
            <p className="text-xs text-gray-500 mt-1">
              このURLをコピーして、下の「登録手順を見る」のとおりカレンダーアプリに貼り付けてください
            </p>
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

      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
        <h2 className="font-semibold text-gray-800">このページについて</h2>
        <p className="text-sm text-gray-700">
          あなたの参加予定の練習を、Google カレンダーや iPhone のカレンダーに自動表示するためのURLを発行する画面です。
        </p>
        <div>
          <p className="text-sm font-semibold text-gray-700 mb-1">使い方（3ステップ）</p>
          <ol className="list-decimal list-outside ml-5 text-sm text-gray-700 space-y-1">
            <li>下のフィードURLをコピー</li>
            <li>お使いのカレンダーアプリで「URLで購読」を選んで貼り付け（具体的な手順は下の「登録手順を見る」を参照）</li>
            <li>以降はカレンダーアプリが数時間ごとに自動更新します（毎回の操作は不要）</li>
          </ol>
        </div>
        <div>
          <p className="text-sm font-semibold text-gray-700 mb-1">カレンダーに表示されるもの</p>
          <p className="text-sm text-gray-700">
            今日以降のあなたの参加予定の練習のみ（キャンセル済みは表示されません）。所属団体ごと＋ゲスト参加で別URLになっており、別カレンダーとして登録すると団体ごとに色分けできます。
          </p>
        </div>
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

      <div className="bg-white rounded-lg border p-4 space-y-3">
        <div>
          <h2 className="font-semibold text-gray-800">登録手順を見る</h2>
          <p className="text-xs text-gray-500 mt-1">
            ご利用のカレンダーアプリを選んで、手順を確認してください。
          </p>
        </div>
        <AccordionItem
          title="Google カレンダー（PCブラウザ）"
          isOpen={openAccordionKey === 'google'}
          onToggle={() => toggleAccordion('google')}
        >
          <ol className="list-decimal list-outside ml-5 space-y-1.5">
            <li>ブラウザで <a href="https://calendar.google.com" target="_blank" rel="noopener noreferrer" className="font-mono text-blue-600 hover:text-blue-800 underline">calendar.google.com</a> を開きます</li>
            <li>画面下部で「表示：デスクトップ」を選択</li>
            <li>左サイドメニュー「他のカレンダー」の右にある「+」をクリック</li>
            <li>「URLで追加」を選択</li>
            <li>このページでコピーしたURLを貼り付け、「カレンダーを追加」をクリック</li>
          </ol>
          <p className="text-xs text-gray-500 mt-2">
            ※ スマホアプリのGoogleカレンダーからは追加できません。ブラウザのデスクトップ用画面から一度追加すれば、スマホのGoogleカレンダーアプリにも自動的に表示されます。
          </p>
        </AccordionItem>
        <AccordionItem
          title="Apple カレンダー（iPhone）"
          isOpen={openAccordionKey === 'apple'}
          onToggle={() => toggleAccordion('apple')}
        >
          <ol className="list-decimal list-outside ml-5 space-y-1.5">
            <li>iPhoneの「設定」アプリを開きます</li>
            <li>「カレンダー」→「アカウント」→「アカウントを追加」をタップ</li>
            <li>「その他」→「照会するカレンダーを追加」をタップ</li>
            <li>このページでコピーしたURLを貼り付け、「次へ」→「保存」</li>
            <li>ホーム画面の「カレンダー」アプリで購読カレンダーが表示されます</li>
          </ol>
        </AccordionItem>
      </div>

      {hasOrganizations && (
        <div className="bg-white rounded-lg border p-4 space-y-4">
          <div>
            <h2 className="font-semibold text-gray-700">所属団体ごとの表示名</h2>
            <p className="text-xs text-gray-500 mt-1">
              カレンダー上のイベントタイトルは「<span className="font-mono">{'{表示名}＠{会場名}'}</span>」の形式で表示されます（例: 「わすら＠すずらん」）。表示名を設定すると、団体名の代わりにこの名前が使われます。空欄なら団体名がそのまま表示されます。
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
