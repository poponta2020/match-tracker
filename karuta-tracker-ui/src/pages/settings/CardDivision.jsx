import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { lineAPI } from '../../api';
import { organizationAPI } from '../../api/organizations';
import { cardDivisionAPI } from '../../api/cardDivision';
import { Copy, Check, AlertCircle } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import PageHeader from '../../components/PageHeader';
import { addOneIsoDay } from '../../utils/date';

// ISO 日付（'2026-07-05'）を M/D 表示（'7/5'。10の位が0の月日は省略）にする
const formatMonthDay = (iso) => {
  const [, m, d] = iso.split('-').map(Number);
  return `${m}/${d}`;
};

const Toggle = ({ enabled, onClick, color = 'bg-[#06C755]' }) => (
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

/**
 * 1日分（今日 or 明日）の札分けブロック。
 * ラベル（「今日 M/D」「明日 M/D」）＋テキストエリア＋その日専用のコピーボタンを表示する。
 * セッションが無ければ emptyMessage の空表示。copied 状態は日ごとに独立。
 * 明日ブロックは provisional=true で「暫定」注記を添える。
 */
const DayCardDivision = ({ label, dateIso, hasSession, text, emptyMessage, provisional, onError }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      onError('コピーに失敗しました');
    }
  };

  const labelText = dateIso ? `${label} ${formatMonthDay(dateIso)}` : label;
  const showText = hasSession && !!text;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">{labelText}</span>
        {showText && (
          <button
            onClick={handleCopy}
            className={`flex items-center gap-1.5 text-sm px-3 py-1.5 rounded-lg transition-colors ${
              copied ? 'bg-[#4a6b5a] text-white' : 'bg-[#4a6b5a] text-white hover:bg-[#3d5a4c]'
            }`}
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            {copied ? 'コピーしました' : 'コピー'}
          </button>
        )}
      </div>
      {showText ? (
        <textarea
          readOnly
          value={text}
          className="w-full h-40 px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm text-gray-700 leading-relaxed whitespace-pre resize-y focus:ring-2 focus:ring-[#4a6b5a] focus:border-transparent"
        />
      ) : (
        <p className="text-sm text-gray-500">{emptyMessage}</p>
      )}
      {/* 明日ブロックは常に暫定注記を出す（セッションの有無自体も確定前に変わりうるため。AC-18） */}
      {provisional && <p className="text-xs text-gray-500">暫定（確定前に変わる場合あり）</p>}
    </div>
  );
};

const OrgCardDivisionBlock = ({ org, playerId, showHeader, linked, onError }) => {
  const [loading, setLoading] = useState(true);
  const [today, setToday] = useState({ date: null, hasSession: false, text: null });
  const [tomorrow, setTomorrow] = useState({ date: null, hasSession: false, text: null });
  const [subscribed, setSubscribed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const fetchData = async () => {
      try {
        setLoading(true);
        // 今日: date 未指定で取得（BE が JST 今日を解決してレスポンスの date に載せる）
        const todayRes = await cardDivisionAPI.getCardDivision(playerId, org.id);
        if (cancelled) return;
        const todayDate = todayRes?.data?.date ?? null;
        setToday({
          date: todayDate,
          hasSession: !!todayRes?.data?.hasSession,
          text: todayRes?.data?.text ?? null,
        });
        setSubscribed(!!todayRes?.data?.subscribed);
        // 明日: 今日レスポンスの date（BE基準）を +1 した日付で再取得（直列）。
        // FE の new Date() で明日を作らない（デバイスTZ差で「今日が2回」や欠落を招くため）。
        if (todayDate) {
          const tomorrowDate = addOneIsoDay(todayDate);
          const tomorrowRes = await cardDivisionAPI.getCardDivision(playerId, org.id, tomorrowDate);
          if (cancelled) return;
          setTomorrow({
            date: tomorrowDate,
            hasSession: !!tomorrowRes?.data?.hasSession,
            text: tomorrowRes?.data?.text ?? null,
          });
        }
      } catch {
        if (!cancelled) {
          onError('札分け情報の取得に失敗しました');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    fetchData();
    return () => {
      cancelled = true;
    };
  }, [org.id, playerId]);

  const handleToggle = async () => {
    const next = !subscribed;
    setSubscribed(next);
    try {
      await cardDivisionAPI.updateSubscription({ playerId, organizationId: org.id, enabled: next });
    } catch {
      setSubscribed(!next);
      onError('設定の更新に失敗しました');
    }
  };

  return (
    <div className="bg-white rounded-lg border p-4 space-y-3">
      {showHeader && (
        <h3 className="text-sm font-medium text-gray-600 mb-1 flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: org.color }} />
          {org.name}
        </h3>
      )}

      {loading ? (
        <p className="text-sm text-gray-500">読み込み中...</p>
      ) : (
        <div className="space-y-4">
          <DayCardDivision
            label="今日"
            dateIso={today.date}
            hasSession={today.hasSession}
            text={today.text}
            emptyMessage="今日は練習がありません"
            onError={onError}
          />
          <DayCardDivision
            label="明日"
            dateIso={tomorrow.date}
            hasSession={tomorrow.hasSession}
            text={tomorrow.text}
            emptyMessage="明日は練習がありません"
            provisional
            onError={onError}
          />
        </div>
      )}

      <div className="flex items-center justify-between pt-2 border-t">
        <span className="text-sm text-gray-700">この練習会の札分けをLINEで受け取る</span>
        <Toggle enabled={subscribed} onClick={handleToggle} />
      </div>

      {linked !== true && (
        <p className="text-xs text-gray-500">
          LINE登録済みでない場合は{' '}
          <Link to="/settings/notifications" className="text-[#4a6b5a] underline">
            設定 → 通知設定
          </Link>{' '}
          からLINEの友だち登録を行ってください。
        </p>
      )}
    </div>
  );
};

const CardDivision = () => {
  const { currentPlayer } = useAuth();
  const playerId = currentPlayer?.id;

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [playerOrgs, setPlayerOrgs] = useState([]);
  const [linked, setLinked] = useState(false);

  useEffect(() => {
    if (!playerId) return;
    const fetchData = async () => {
      try {
        setLoading(true);
        const [orgsRes, playerOrgsRes, lineStatusRes] = await Promise.all([
          organizationAPI.getAll().catch(() => ({ data: [] })),
          organizationAPI.getPlayerOrganizations(playerId).catch(() => ({ data: [] })),
          lineAPI.getStatus(playerId).catch(() => ({ data: { linked: false } })),
        ]);
        const orgIds = (playerOrgsRes.data || []).map((o) => o.id);
        const orgs = (orgsRes.data || []).filter((o) => orgIds.includes(o.id));
        setPlayerOrgs(orgs);
        setLinked(!!lineStatusRes.data?.linked);
      } catch {
        setError('団体情報の取得に失敗しました');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [playerId]);

  if (loading) {
    return (
      <>
        <PageHeader title="札分け確認" backTo="/settings" />
        <LoadingScreen />
      </>
    );
  }

  const showOrgHeaders = playerOrgs.length > 1;

  return (
    <>
      <PageHeader title="札分け確認" backTo="/settings" />
      <div className="max-w-lg mx-auto p-4 space-y-6">
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-center gap-2">
            <AlertCircle className="h-5 w-5 text-red-600 flex-shrink-0" />
            <p className="text-red-800 text-sm">{error}</p>
          </div>
        )}

        {playerOrgs.length === 0 ? (
          <div className="bg-white rounded-lg border p-4 space-y-2">
            <p className="text-sm text-gray-600">参加練習会が設定されていません。</p>
            <Link to="/settings/organizations" className="text-sm text-[#4a6b5a] underline">
              設定 → 参加練習会 から練習会を選択してください
            </Link>
          </div>
        ) : (
          playerOrgs.map((org) => (
            <OrgCardDivisionBlock
              key={org.id}
              org={org}
              playerId={playerId}
              showHeader={showOrgHeaders}
              linked={linked}
              onError={setError}
            />
          ))
        )}
      </div>
    </>
  );
};

export default CardDivision;
