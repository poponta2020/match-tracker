import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { lotteryAPI } from '../../api/lottery';
import { organizationAPI } from '../../api/organizations';
import { isSuperAdmin } from '../../utils/auth';
import LoadingScreen from '../../components/LoadingScreen';
import PageHeader from '../../components/PageHeader';
import { buildCopyText, hasAnyWaitlisted } from './lotteryResultText';

/**
 * 抽選結果確認画面
 */
export default function LotteryResults() {
  const { currentPlayer } = useAuth();
  const role = currentPlayer?.role;
  const isAdminOrSuper = role === 'ADMIN' || role === 'SUPER_ADMIN';
  // ADMIN は LoginResponse の adminOrganizationId を使う（organizationId は LoginResponse に存在しない）
  const adminOrgId = currentPlayer?.adminOrganizationId || null;

  const [currentDate, setCurrentDate] = useState(() => {
    const now = new Date();
    return { year: now.getFullYear(), month: now.getMonth() + 1 };
  });
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(true);
  const [processing, setProcessing] = useState(null);
  const [isConfirmed, setIsConfirmed] = useState(false);
  const [copyText, setCopyText] = useState('');
  const [copyFeedback, setCopyFeedback] = useState('');
  const [organizations, setOrganizations] = useState([]);
  const [selectedOrgId, setSelectedOrgId] = useState(null);
  // SUPER_ADMIN の団体一覧取得が完了したかどうか。失敗・0件のときも true になり、
  // fetchResults を組織ID未指定（全団体スコープ）で進めるフォールバックの起点になる。
  const [orgsFetched, setOrgsFetched] = useState(false);
  // 古いリクエストの結果が後から到着しても捨てるためのリクエストID（SUPER_ADMIN の
  // 初回ロードで全団体スコープと選択団体スコープのレスポンスが競合するのを防ぐ）
  const requestIdRef = useRef(0);

  // SUPER_ADMIN は団体一覧を取得し、デフォルトとして先頭団体を選択する。
  // LotteryManagement と同じ方針で、選択された団体IDを is-confirmed と getResults の
  // 両方に渡し、コピー領域の表示スコープを揃える。
  useEffect(() => {
    if (!isSuperAdmin()) return;
    organizationAPI.getAll().then(res => {
      setOrganizations(res.data);
      setSelectedOrgId(prev => prev || (res.data[0]?.id ?? null));
    }).catch(() => {
      setOrganizations([]);
    }).finally(() => {
      setOrgsFetched(true);
    });
  }, []);

  // 管理者向けの団体スコープ。ADMIN は adminOrgId 固定、SUPER_ADMIN は選択中の団体。
  const adminScopeOrgId = isAdminOrSuper
    ? (isSuperAdmin() ? selectedOrgId : adminOrgId)
    : null;

  useEffect(() => {
    fetchResults();
    // adminScopeOrgId 変更時にも再取得する（SUPER_ADMIN の団体切替対応）。
    // orgsFetched は団体一覧取得失敗 / 0件時の全団体スコープフォールバックを発火させる
    // ためにも依存に含める（このとき adminScopeOrgId は null のまま変化しない）。
  }, [currentDate, adminScopeOrgId, orgsFetched]);

  // ADMIN/SUPER_ADMIN かつ adminScopeOrgId が判明しているときだけ確定状態を問い合わせる。
  // adminScopeOrgId が無い間は団体スコープが定まらず is-confirmed と getResults の
  // 取得範囲が食い違うため、コピー領域は非表示のままにする。
  useEffect(() => {
    setIsConfirmed(false);
    if (!isAdminOrSuper || !adminScopeOrgId) return;
    let cancelled = false;
    lotteryAPI.isConfirmed(currentDate.year, currentDate.month, adminScopeOrgId)
      .then((res) => {
        if (cancelled) return;
        setIsConfirmed(res.data?.confirmed === true);
      })
      .catch(() => {
        if (cancelled) return;
        setIsConfirmed(false);
      });
    return () => { cancelled = true; };
  }, [currentDate.year, currentDate.month, isAdminOrSuper, adminScopeOrgId]);

  const fetchResults = async () => {
    // SUPER_ADMIN は団体一覧取得が完了するまでは fetch を発行しない。
    // 取得前に null で発行すると全団体スコープのレスポンスが返り、後続の単一団体
    // スコープのレスポンスより遅れて到着した場合に results / copyText を上書きする
    // 恐れがある（requestIdRef でも防げるが、不要な HTTP を避ける最適化）。
    // 取得後に selectedOrgId が無いケース（団体一覧取得失敗 / 0件）は従来どおり
    // 組織ID未指定で全団体スコープを取得し、ローディングを必ず解除する。
    if (isSuperAdmin() && !selectedOrgId && !orgsFetched) {
      return;
    }
    const myRequestId = ++requestIdRef.current;
    setLoading(true);
    try {
      // is-confirmed と取得対象が食い違わないよう、adminScopeOrgId が判明している
      // ADMIN/SUPER_ADMIN は同じ団体でセッション一覧を絞り込む。
      // ADMIN はバックエンド側で adminOrganizationId に強制されるため副作用はない。
      const orgIdParam = isAdminOrSuper && adminScopeOrgId ? adminScopeOrgId : undefined;
      const res = await lotteryAPI.getResults(currentDate.year, currentDate.month, orgIdParam);
      // 自分の発行後に新しい fetch が走っていたら stale なので捨てる
      if (requestIdRef.current !== myRequestId) return;
      setResults(res.data);
      setCopyText(buildCopyText(currentDate.year, currentDate.month, res.data));
    } catch (err) {
      if (requestIdRef.current !== myRequestId) return;
      console.error('Failed to fetch lottery results:', err);
    } finally {
      if (requestIdRef.current === myRequestId) setLoading(false);
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(copyText);
      setCopyFeedback('コピーしました');
    } catch (err) {
      console.error('Failed to copy text:', err);
      setCopyFeedback('コピーに失敗しました');
    }
    setTimeout(() => setCopyFeedback(''), 2000);
  };

  const changeMonth = (delta) => {
    setCurrentDate((prev) => {
      let newMonth = prev.month + delta;
      let newYear = prev.year;
      if (newMonth > 12) { newMonth = 1; newYear++; }
      if (newMonth < 1) { newMonth = 12; newYear--; }
      return { year: newYear, month: newMonth };
    });
  };

  const handleDeclineWaitlist = async (e, sessionId) => {
    e.stopPropagation();
    if (!confirm('このセッションのキャンセル待ちを辞退しますか？')) return;

    setProcessing(`decline-${sessionId}`);
    try {
      await lotteryAPI.declineWaitlist(sessionId, currentPlayer.id);
      await fetchResults();
    } catch (err) {
      console.error('Failed to decline waitlist:', err);
      alert('辞退処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  const handleRejoinWaitlist = async (e, sessionId) => {
    e.stopPropagation();
    if (!confirm('キャンセル待ちに復帰しますか？（最後尾になります）')) return;

    setProcessing(`rejoin-${sessionId}`);
    try {
      await lotteryAPI.rejoinWaitlist(sessionId, currentPlayer.id);
      await fetchResults();
    } catch (err) {
      console.error('Failed to rejoin waitlist:', err);
      alert('復帰処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  const getStatusBadge = (status, waitlistNumber) => {
    switch (status) {
      case 'WON': return <span className="px-2 py-0.5 bg-green-100 text-green-800 rounded text-xs font-bold">当選</span>;
      case 'WAITLISTED': return <span className="px-2 py-0.5 bg-yellow-100 text-yellow-800 rounded text-xs font-bold">待ち{waitlistNumber}番</span>;
      case 'OFFERED': return <span className="px-2 py-0.5 bg-blue-100 text-blue-800 rounded text-xs font-bold">繰上連絡中</span>;
      case 'DECLINED': return <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs font-bold">辞退</span>;
      case 'CANCELLED': return <span className="px-2 py-0.5 bg-red-100 text-red-800 rounded text-xs font-bold">キャンセル</span>;
      case 'PENDING': return <span className="px-2 py-0.5 bg-gray-100 text-gray-600 rounded text-xs font-bold">申込中</span>;
      case 'WAITLIST_DECLINED': return <span className="px-2 py-0.5 bg-gray-100 text-gray-500 rounded text-xs font-bold">待ち辞退</span>;
      default: return null;
    }
  };

  const isMyResult = (playerId) => currentPlayer && currentPlayer.id === playerId;

  // セッション内で自分がWAITLISTED/WAITLIST_DECLINEDかチェック
  const getMyWaitlistStatus = (session) => {
    if (!currentPlayer || !session.matchResults) return null;
    let hasWaitlisted = false;
    let hasWaitlistDeclined = false;
    for (const match of Object.values(session.matchResults)) {
      for (const p of (match.waitlisted || [])) {
        if (p.playerId === currentPlayer.id) {
          if (p.status === 'WAITLISTED') hasWaitlisted = true;
          if (p.status === 'WAITLIST_DECLINED') hasWaitlistDeclined = true;
        }
      }
    }
    if (hasWaitlisted) return 'WAITLISTED';
    if (hasWaitlistDeclined) return 'WAITLIST_DECLINED';
    return null;
  };

  return (
    <>
      <PageHeader title="抽選結果" backTo="/" />
      <div className="max-w-2xl mx-auto p-4">
      {/* 月選択 */}
      <div className="flex items-center justify-center gap-4 mb-6">
        <button onClick={() => changeMonth(-1)} className="p-2 rounded hover:bg-gray-100">&lt;</button>
        <span className="text-lg font-semibold">{currentDate.year}年{currentDate.month}月</span>
        <button onClick={() => changeMonth(1)} className="p-2 rounded hover:bg-gray-100">&gt;</button>
      </div>

      {/* 団体セレクタ（SUPER_ADMIN用）。LotteryManagement と同じ条件で複数団体時のみ表示。 */}
      {isSuperAdmin() && organizations.length > 1 && (
        <div className="flex justify-center mb-6">
          <select
            value={selectedOrgId || ''}
            onChange={(e) => setSelectedOrgId(Number(e.target.value))}
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm text-[#374151]"
          >
            {organizations.map(org => (
              <option key={org.id} value={org.id}>{org.name}</option>
            ))}
          </select>
        </div>
      )}

      {loading ? (
        <LoadingScreen />
      ) : results.length === 0 ? (
        <div className="text-center py-8 text-gray-500">この月の抽選結果はありません</div>
      ) : (
        <div className="space-y-6">
          {results.map((session) => {
            const myWaitlistStatus = getMyWaitlistStatus(session);
            return (
              <div key={session.sessionId} className="bg-white rounded-lg shadow p-4">
                <div className="flex justify-between items-center mb-3">
                  <h2 className="font-bold text-lg">
                    {new Date(session.sessionDate).toLocaleDateString('ja-JP', { month: 'long', day: 'numeric', weekday: 'short' })}
                  </h2>
                  {session.capacity && (
                    <span className="text-sm text-gray-500">定員: {session.capacity}名</span>
                  )}
                </div>

                {session.matchResults && Object.entries(session.matchResults).map(([matchNum, match]) => (
                  <div key={matchNum} className="mb-4 last:mb-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="font-semibold text-sm">試合{matchNum}</span>
                      {match.lotteryRequired && (
                        <span className="text-xs px-1.5 py-0.5 bg-orange-100 text-orange-700 rounded">抽選あり</span>
                      )}
                    </div>

                    {/* 当選者 */}
                    {match.winners && match.winners.length > 0 && (
                      <div className="mb-2">
                        <div className="text-xs text-gray-500 mb-1">当選者 ({match.winners.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.winners.map((p) => (
                            <span key={p.playerId}
                              className={`px-2 py-0.5 rounded text-xs ${isMyResult(p.playerId) ? 'bg-green-200 font-bold' : 'bg-gray-100'}`}>
                              {p.playerName}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* キャンセル待ち */}
                    {match.waitlisted && match.waitlisted.length > 0 && (
                      <div>
                        <div className="text-xs text-gray-500 mb-1">キャンセル待ち ({match.waitlisted.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.waitlisted.map((p) => (
                            <span key={p.playerId}
                              className={`px-2 py-0.5 rounded text-xs ${isMyResult(p.playerId) ? (p.status === 'WAITLIST_DECLINED' ? 'bg-gray-200 font-bold' : 'bg-yellow-200 font-bold') : 'bg-gray-100'}`}>
                              {p.waitlistNumber ? `${p.waitlistNumber}. ` : ''}{p.playerName} {getStatusBadge(p.status, p.waitlistNumber)}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}

                {/* セッション単位のキャンセル待ち辞退/復帰ボタン */}
                {myWaitlistStatus === 'WAITLISTED' && (
                  <div className="mt-3 pt-3 border-t">
                    <button
                      onClick={(e) => handleDeclineWaitlist(e, session.sessionId)}
                      disabled={processing === `decline-${session.sessionId}`}
                      className="px-3 py-1.5 text-xs bg-gray-200 hover:bg-gray-300 text-gray-700 rounded disabled:opacity-50">
                      {processing === `decline-${session.sessionId}` ? '処理中...' : 'この日のキャンセル待ちを辞退する'}
                    </button>
                  </div>
                )}
                {myWaitlistStatus === 'WAITLIST_DECLINED' && (
                  <div className="mt-3 pt-3 border-t">
                    <button
                      onClick={(e) => handleRejoinWaitlist(e, session.sessionId)}
                      disabled={processing === `rejoin-${session.sessionId}`}
                      className="px-3 py-1.5 text-xs bg-blue-100 hover:bg-blue-200 text-blue-700 rounded disabled:opacity-50">
                      {processing === `rejoin-${session.sessionId}` ? '処理中...' : 'キャンセル待ちに復帰する（最後尾）'}
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 管理者向け: LINE告知用コピー領域。抽選確定済の月にのみ表示する */}
      {isAdminOrSuper && isConfirmed && (
        <div className="mt-8 pt-4 border-t">
          <div className="text-sm font-semibold text-gray-700 mb-2">
            管理者向け: LINE告知用テキスト（抽選落ちのみ）
          </div>
          <textarea
            value={copyText}
            onChange={(e) => setCopyText(e.target.value)}
            rows={12}
            className="w-full font-mono text-xs border border-gray-300 rounded p-2 whitespace-pre"
          />
          <div className="mt-2 flex items-center gap-3">
            <button
              type="button"
              onClick={handleCopy}
              disabled={!hasAnyWaitlisted(results)}
              className="px-4 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">
              コピー
            </button>
            {copyFeedback && (
              <span className="text-sm text-gray-600">{copyFeedback}</span>
            )}
          </div>
        </div>
      )}
      </div>
    </>
  );
}
