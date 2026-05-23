import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { lotteryAPI } from '../../api/lottery';
import { organizationAPI } from '../../api/organizations';
import { isSuperAdmin } from '../../utils/auth';
import { Settings, Play, Check, Bell, BellRing } from 'lucide-react';
import { buildCopyText, hasAnyWaitlisted } from './lotteryResultText';
import PageHeader from '../../components/PageHeader';

/**
 * 抽選管理画面（ADMIN/SUPER_ADMIN用）
 *
 * 状態遷移:
 * - idle: 初期状態（抽選実行ボタンのみ表示）
 * - preview: プレビュー表示中（確定ボタン表示）
 * - confirmed: 確定済み（通知送信ボタン表示）
 */
const formatRank = (p) => {
  if (p.danRank) return p.danRank;
  if (p.kyuRank) return p.kyuRank;
  return '';
};

// 表示中の (year, month) が現在の年月以降か（=その月がまだ終わっていないか）を判定
const isMonthOngoing = (year, month) => {
  const now = new Date();
  const nowYear = now.getFullYear();
  const nowMonth = now.getMonth() + 1;
  return year > nowYear || (year === nowYear && month >= nowMonth);
};

export default function LotteryManagement() {
  const { currentPlayer } = useAuth();
  const navigate = useNavigate();
  const adminOrgId = currentPlayer?.adminOrganizationId || currentPlayer?.organizationId || null;

  // デフォルト: 翌月
  const [currentDate, setCurrentDate] = useState(() => {
    const now = new Date();
    let year = now.getFullYear();
    let month = now.getMonth() + 2; // 翌月
    if (month > 12) { month = 1; year++; }
    return { year, month };
  });

  const [phase, setPhase] = useState('idle'); // idle | preview | confirmed
  const [previewResults, setPreviewResults] = useState([]);
  const [processing, setProcessing] = useState(null);
  const [error, setError] = useState(null);
  const [notifyResult, setNotifyResult] = useState(null);
  const [lotterySeed, setLotterySeed] = useState(null);
  const [organizations, setOrganizations] = useState([]);
  const [selectedOrgId, setSelectedOrgId] = useState(adminOrgId);
  const [applicants, setApplicants] = useState([]);
  const [priorityPlayerIds, setPriorityPlayerIds] = useState([]);
  const [confirmedLotteryExists, setConfirmedLotteryExists] = useState(false);
  const [copyText, setCopyText] = useState('');
  const [copyFeedback, setCopyFeedback] = useState('');

  useEffect(() => {
    if (isSuperAdmin()) {
      organizationAPI.getAll().then(res => {
        setOrganizations(res.data);
        setSelectedOrgId(prev => prev || (res.data[0]?.id ?? null));
      });
    }
  }, []);

  // ADMIN は LoginResponse の adminOrganizationId を使う（organizationId は LoginResponse に存在しない）
  const organizationId = isSuperAdmin() ? selectedOrgId : adminOrgId;
  const sessionStorageKey = organizationId
    ? `lottery-priority-${currentDate.year}-${currentDate.month}-${organizationId}`
    : null;

  useEffect(() => {
    setApplicants([]);
    setPriorityPlayerIds([]);
    if (!organizationId) return;

    const key = `lottery-priority-${currentDate.year}-${currentDate.month}-${organizationId}`;
    const stored = sessionStorage.getItem(key);
    let restoredIds = [];
    if (stored) {
      try { restoredIds = JSON.parse(stored); } catch { /* ignore */ }
    }

    lotteryAPI.getMonthlyApplicants(currentDate.year, currentDate.month, organizationId)
      .then(res => {
        const fetchedApplicants = res.data.applicants ?? [];
        setApplicants(fetchedApplicants);
        const applicantPlayerIds = fetchedApplicants.map(a => a.playerId);
        const filteredIds = restoredIds.filter(id => applicantPlayerIds.includes(id));
        setPriorityPlayerIds(filteredIds);
        sessionStorage.setItem(key, JSON.stringify(filteredIds));
      })
      .catch(() => setApplicants([]));
  }, [currentDate.year, currentDate.month, organizationId]);

  useEffect(() => {
    if (!sessionStorageKey) return;
    sessionStorage.setItem(sessionStorageKey, JSON.stringify(priorityPlayerIds));
  }, [priorityPlayerIds, sessionStorageKey]);

  // 月・団体切り替え時に「その月の抽選が確定済みか」を取得
  // 取得中は古いフラグで別月の通知ボタンが残らないよう、まず false にリセットしてから問い合わせる。
  // 非同期レスポンスの順序は保証されないため、cancelled フラグで現在のリクエストだけ反映する。
  useEffect(() => {
    setConfirmedLotteryExists(false);
    if (!organizationId) return;
    let cancelled = false;
    lotteryAPI.isConfirmed(currentDate.year, currentDate.month, organizationId)
      .then(res => {
        if (cancelled) return;
        setConfirmedLotteryExists(res.data?.confirmed === true);
      })
      .catch(() => {
        if (cancelled) return;
        setConfirmedLotteryExists(false);
      });
    return () => { cancelled = true; };
  }, [currentDate.year, currentDate.month, organizationId]);

  // プレビュー結果が更新されたら LINE 告知用テキストを再生成する
  useEffect(() => {
    if (previewResults.length > 0) {
      setCopyText(buildCopyText(currentDate.year, currentDate.month, previewResults));
    } else {
      setCopyText('');
    }
  }, [previewResults, currentDate.year, currentDate.month]);

  const togglePriorityPlayer = (playerId) => {
    setPriorityPlayerIds(prev =>
      prev.includes(playerId) ? prev.filter(id => id !== playerId) : [...prev, playerId]
    );
    // プレビュー中に優先選手が変わったら、プレビュー結果が実行条件と食い違うのを防ぐため idle に戻して再プレビューを必須にする
    if (phase === 'preview') {
      setPhase('idle');
      setPreviewResults([]);
      setLotterySeed(null);
    }
  };

  const changeMonth = (delta) => {
    setCurrentDate((prev) => {
      let newMonth = prev.month + delta;
      let newYear = prev.year;
      if (newMonth > 12) { newMonth = 1; newYear++; }
      if (newMonth < 1) { newMonth = 12; newYear--; }
      return { year: newYear, month: newMonth };
    });
    // 月変更時にリセット（confirmedLotteryExists も同期的にクリアし、新しい is-confirmed 取得までの間に古い通知ボタンが残らないようにする）
    setPhase('idle');
    setPreviewResults([]);
    setError(null);
    setNotifyResult(null);
    setConfirmedLotteryExists(false);
  };

  // 抽選プレビュー実行
  const handlePreview = async () => {
    setProcessing('preview');
    setError(null);
    setNotifyResult(null);
    try {
      const res = await lotteryAPI.preview(currentDate.year, currentDate.month, organizationId, priorityPlayerIds);
      const { results, seed } = res.data;
      setPreviewResults(results);
      setLotterySeed(seed);
      if (results.length === 0) {
        setError('対象のセッションがありません');
        setPhase('idle');
      } else {
        setPhase('preview');
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || '抽選プレビューに失敗しました';
      setError(typeof msg === 'string' ? msg : '抽選プレビューに失敗しました');
      setPhase('idle');
    } finally {
      setProcessing(null);
    }
  };

  // 抽選確定
  const handleConfirm = async () => {
    if (!confirm('抽選結果を確定しますか？\n確定するとDBに保存され、伝助への書き戻しが実行されます。')) return;

    setProcessing('confirm');
    setError(null);
    try {
      const res = await lotteryAPI.confirm(currentDate.year, currentDate.month, organizationId, lotterySeed, priorityPlayerIds);
      if (sessionStorageKey) sessionStorage.removeItem(sessionStorageKey);
      setPhase('confirmed');
      setConfirmedLotteryExists(true);

      // 伝助書き戻しの失敗をユーザーに知らせる（確定 DB は維持される）
      if (res?.data && res.data.densukeWriteSucceeded === false) {
        const detail = res.data.densukeWriteError ? `\n詳細: ${res.data.densukeWriteError}` : '';
        alert('抽選結果は確定されましたが、伝助への書き戻しに失敗しました。手動で伝助の状態を確認してください。' + detail);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || '確定処理に失敗しました';
      setError(typeof msg === 'string' ? msg : '確定処理に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  // 既送信チェック（重複送信防止のため、再送信時に件数付きで確認）
  const confirmIfAlreadySent = async (firstPrompt) => {
    try {
      const statusRes = await lotteryAPI.notifyStatus(currentDate.year, currentDate.month, organizationId);
      if (statusRes.data?.sent) {
        const count = statusRes.data.sentCount ?? 0;
        return confirm(`既に${count}件の通知を送信済みです。再送信しますか？`);
      }
    } catch {
      // 送信済みチェックが失敗した場合はそのまま通常確認に進む
    }
    return confirm(firstPrompt);
  };

  // 全員に通知送信
  const handleNotifyAll = async () => {
    if (!(await confirmIfAlreadySent('全員（当選者＋キャンセル待ち）に通知を送信しますか？'))) return;

    setProcessing('notifyAll');
    setError(null);
    try {
      const res = await lotteryAPI.notifyResults(currentDate.year, currentDate.month, organizationId);
      setNotifyResult({ type: 'all', ...res.data });
    } catch {
      setError('通知送信に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  // LINE 告知用テキストをクリップボードにコピー
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

  // キャンセル待ちのみに通知送信
  const handleNotifyWaitlisted = async () => {
    if (!(await confirmIfAlreadySent('キャンセル待ちの人にだけ通知を送信しますか？'))) return;

    setProcessing('notifyWaitlisted');
    setError(null);
    try {
      const res = await lotteryAPI.notifyWaitlisted(currentDate.year, currentDate.month, organizationId);
      setNotifyResult({ type: 'waitlisted', ...res.data });
    } catch {
      setError('通知送信に失敗しました');
    } finally {
      setProcessing(null);
    }
  };

  return (
    <>
      <PageHeader
        title="抽選管理"
        backTo="/settings"
        rightActions={
          <button
            onClick={() => navigate(organizationId ? `/admin/settings?organizationId=${organizationId}` : '/admin/settings')}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-white border border-white rounded-lg hover:bg-white hover:text-[#4a6b5a] transition-colors"
          >
            <Settings size={14} />
            システム設定
          </button>
        }
      />
      <div className="max-w-2xl mx-auto p-4">
      {/* 年月セレクター */}
      <div className="flex items-center justify-center gap-4 mb-6">
        <button onClick={() => changeMonth(-1)} className="p-2 rounded hover:bg-gray-100 text-[#374151]">&lt;</button>
        <span className="text-lg font-semibold text-[#374151]">{currentDate.year}年{currentDate.month}月</span>
        <button onClick={() => changeMonth(1)} className="p-2 rounded hover:bg-gray-100 text-[#374151]">&gt;</button>
      </div>

      {/* 団体セレクタ（SUPER_ADMIN用） */}
      {isSuperAdmin() && organizations.length > 1 && (
        <div className="flex justify-center mb-6">
          <select
            value={selectedOrgId || ''}
            onChange={(e) => {
              setSelectedOrgId(Number(e.target.value));
              setPhase('idle');
              setPreviewResults([]);
              setError(null);
              setNotifyResult(null);
              setConfirmedLotteryExists(false);
            }}
            className="px-4 py-2 border border-gray-300 rounded-lg text-sm text-[#374151]"
          >
            {organizations.map(org => (
              <option key={org.id} value={org.id}>{org.name}</option>
            ))}
          </select>
        </div>
      )}

      {/* エラー表示 */}
      {error && (
        <div className="mb-4 p-3 bg-red-50 text-red-700 border border-red-200 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* 参加希望者一覧（優先選手指定） */}
      {applicants.length > 0 && (
        <div className="mb-6 bg-white rounded-lg shadow p-4">
          <div className="flex items-center justify-between mb-3">
            <h2 className="font-semibold text-sm text-[#374151]">優先選手指定</h2>
            {priorityPlayerIds.length > 0 && (
              <span className="text-xs text-blue-600 font-semibold">{priorityPlayerIds.length}名選択中</span>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            {applicants.map((applicant) => {
              const isSelected = priorityPlayerIds.includes(applicant.playerId);
              const isDisabled = phase === 'confirmed';
              return (
                <button
                  key={applicant.playerId}
                  onClick={() => !isDisabled && togglePriorityPlayer(applicant.playerId)}
                  disabled={isDisabled}
                  className={`px-3 py-1 rounded-full text-xs border transition-colors ${
                    isSelected
                      ? 'bg-blue-100 border-blue-400 text-blue-800'
                      : 'bg-gray-100 border-gray-300 text-[#374151]'
                  } ${isDisabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:opacity-80'}`}
                >
                  {applicant.name}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* 抽選実行ボタン（idle時） */}
      {phase === 'idle' && (
        <div className="flex justify-center mb-6">
          <button
            onClick={handlePreview}
            disabled={processing === 'preview'}
            className="flex items-center gap-2 px-6 py-3 bg-[#4a6b5a] hover:bg-[#3d5a4c] text-white rounded-lg font-semibold transition-colors disabled:opacity-50"
          >
            <Play size={18} />
            {processing === 'preview' ? '実行中...' : '抽選実行（プレビュー）'}
          </button>
        </div>
      )}

      {/* プレビュー結果 */}
      {(phase === 'preview' || phase === 'confirmed') && previewResults.length > 0 && (
        <div className="space-y-4 mb-6">
          {/* ステータスバー */}
          <div className="flex items-center gap-2 p-3 rounded-lg bg-white shadow">
            <span className="text-sm font-semibold text-[#374151]">ステータス:</span>
            {phase === 'preview' ? (
              <span className="px-2 py-0.5 bg-orange-100 text-orange-700 rounded text-xs font-bold">プレビュー中（未保存）</span>
            ) : (
              <span className="px-2 py-0.5 bg-green-100 text-green-800 rounded text-xs font-bold">確定済み</span>
            )}
          </div>

          {/* セッション別結果 */}
          {previewResults.map((session) => (
            <div key={session.sessionId} className="bg-white rounded-lg shadow p-4">
              <div className="flex justify-between items-center mb-3">
                <h2 className="font-bold text-lg text-[#374151]">
                  {new Date(session.sessionDate).toLocaleDateString('ja-JP', { month: 'long', day: 'numeric', weekday: 'short' })}
                </h2>
                <div className="flex items-center gap-2">
                  {session.venueName && (
                    <span className="text-xs text-[#6b7280]">{session.venueName}</span>
                  )}
                  {session.capacity && (
                    <span className="text-sm text-[#6b7280]">定員: {session.capacity}名</span>
                  )}
                </div>
              </div>

              {session.matchResults && Object.entries(session.matchResults)
                .sort(([a], [b]) => parseInt(a) - parseInt(b))
                .map(([matchNum, match]) => (
                  <div key={matchNum} className="mb-4 last:mb-0">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="font-semibold text-sm text-[#374151]">試合{matchNum}</span>
                      {match.lotteryRequired && (
                        <span className="text-xs px-1.5 py-0.5 bg-orange-100 text-orange-700 rounded">抽選あり</span>
                      )}
                    </div>

                    {/* 当選者 */}
                    {match.winners && match.winners.length > 0 && (
                      <div className="mb-2">
                        <div className="text-xs text-[#6b7280] mb-1">当選者 ({match.winners.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.winners.map((p) => (
                            <span key={p.playerId} className="px-2 py-0.5 rounded text-xs bg-green-50 text-green-800 border border-green-200">
                              {p.playerName}{formatRank(p) && <span className="ml-0.5 text-[10px] text-green-600">({formatRank(p)})</span>}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* キャンセル待ち */}
                    {match.waitlisted && match.waitlisted.length > 0 && (
                      <div>
                        <div className="text-xs text-[#6b7280] mb-1">キャンセル待ち ({match.waitlisted.length}名)</div>
                        <div className="flex flex-wrap gap-1">
                          {match.waitlisted.map((p) => (
                            <span key={p.playerId} className="px-2 py-0.5 rounded text-xs bg-yellow-50 text-yellow-800 border border-yellow-200">
                              {p.waitlistNumber}. {p.playerName}{formatRank(p) && <span className="ml-0.5 text-[10px] text-yellow-600">({formatRank(p)})</span>}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
            </div>
          ))}

          {/* 確定ボタン（プレビュー時） */}
          {phase === 'preview' && (
            <div className="flex justify-center gap-3">
              <button
                onClick={() => { setPhase('idle'); setPreviewResults([]); }}
                className="px-4 py-2.5 text-sm border border-[#6b7280] text-[#6b7280] rounded-lg hover:bg-gray-50 transition-colors"
              >
                やり直す
              </button>
              <button
                onClick={handleConfirm}
                disabled={processing === 'confirm'}
                className="flex items-center gap-2 px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-semibold transition-colors disabled:opacity-50"
              >
                <Check size={18} />
                {processing === 'confirm' ? '確定中...' : '結果を確定する'}
              </button>
            </div>
          )}

        </div>
      )}

      {/* 通知送信ボタン（抽選確定後 - その月が終わるまで表示し続ける） */}
      {(phase === 'confirmed' || (confirmedLotteryExists && isMonthOngoing(currentDate.year, currentDate.month))) && (
        <div className="space-y-3 mb-6">
          {phase === 'confirmed' && (
            <div className="p-3 bg-green-50 text-green-800 border border-green-200 rounded-lg text-sm text-center">
              抽選結果を確定しました。伝助への書き戻しが実行されました。
            </div>
          )}
          <div className="flex justify-center gap-3">
            <button
              onClick={handleNotifyAll}
              disabled={!!processing}
              className="flex items-center gap-2 px-4 py-2.5 bg-[#4a6b5a] hover:bg-[#3d5a4c] text-white rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
            >
              <Bell size={16} />
              {processing === 'notifyAll' ? '送信中...' : '全員に通知送信'}
            </button>
            <button
              onClick={handleNotifyWaitlisted}
              disabled={!!processing}
              className="flex items-center gap-2 px-4 py-2.5 bg-yellow-600 hover:bg-yellow-700 text-white rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
            >
              <BellRing size={16} />
              {processing === 'notifyWaitlisted' ? '送信中...' : 'キャンセル待ちのみ通知'}
            </button>
          </div>

          {/* 通知送信結果 */}
          {notifyResult && (
            <div className="p-3 bg-white rounded-lg shadow text-sm">
              <div className="font-semibold text-[#374151] mb-1">
                通知送信結果（{notifyResult.type === 'all' ? '全員' : 'キャンセル待ちのみ'}）
              </div>
              <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-[#6b7280]">
                <span>アプリ内通知:</span>
                <span>{notifyResult.inAppCount}件</span>
                <span>LINE送信成功:</span>
                <span>{notifyResult.lineSent}名</span>
                <span>LINE送信失敗:</span>
                <span>{notifyResult.lineFailed}名</span>
                <span>LINEスキップ:</span>
                <span>{notifyResult.lineSkipped}名</span>
              </div>
            </div>
          )}
        </div>
      )}

      {/* 管理者向け: LINE告知用コピー領域。確定後の主導線（通知送信）を妨げないよう最後に配置 */}
      {(phase === 'preview' || phase === 'confirmed') && previewResults.length > 0 && (
        <div className="mb-6 bg-white rounded-lg shadow p-4">
          <div className="text-sm font-semibold text-gray-700 mb-2">
            管理者向け: LINE告知用テキスト（抽選落ちのみ）
            {phase === 'preview' && (
              <span className="ml-2 text-xs text-orange-700 font-bold">
                ※ プレビュー（未確定）
              </span>
            )}
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
              disabled={!hasAnyWaitlisted(previewResults)}
              className={`px-4 py-1.5 text-white text-sm rounded disabled:opacity-50 disabled:cursor-not-allowed ${
                phase === 'preview'
                  ? 'bg-orange-500 hover:bg-orange-600'
                  : 'bg-blue-600 hover:bg-blue-700'
              }`}
            >
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
