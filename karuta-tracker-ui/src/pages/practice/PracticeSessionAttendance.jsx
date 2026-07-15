import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { practiceAPI, lotteryAPI } from '../../api';
import { organizationAPI } from '../../api/organizations';
import { ArrowLeft, AlertCircle } from 'lucide-react';
import LoadingScreen from '../../components/LoadingScreen';
import SaveProgressOverlay from '../../components/SaveProgressOverlay';
import { resolveAttendanceMode } from './utils/attendanceMode';
import { needsSameDayConfirm as needsSameDayConfirmFn } from './utils/sameDayConfirm';
import {
  buildMonthParticipationsPayload,
  resolveAttendanceSections,
  resolveAvailableMatchNumbers,
} from './utils/attendanceScreen';

// PracticeCancelPage と同一のキャンセル理由（既存画面は据え置きのためローカル複製）
const CANCEL_REASONS = [
  { value: 'HEALTH', label: '体調不良' },
  { value: 'WORK_SCHOOL', label: '仕事・学業の都合' },
  { value: 'FAMILY', label: '家庭の事情' },
  { value: 'TRANSPORT', label: '交通機関の問題' },
  { value: 'OTHER', label: 'その他' },
];

// 読み取り専用行のステータス表示（PracticeParticipation.getStatusInfo と同じマッピング）
const STATUS_LABEL = {
  WON: { label: '当選', color: 'bg-green-100 text-green-800' },
  WAITLISTED: { label: '待ち', color: 'bg-yellow-100 text-yellow-800' },
  OFFERED: { label: '応答待', color: 'bg-blue-100 text-blue-800' },
  PENDING: { label: '申込', color: 'bg-gray-100 text-gray-600' },
  DECLINED: { label: '辞退', color: 'bg-gray-100 text-gray-400' },
  CANCELLED: { label: '取消', color: 'bg-red-100 text-red-400' },
  WAITLIST_DECLINED: { label: '待辞退', color: 'bg-gray-100 text-gray-400' },
};

const HAIRLINE = '#e5ddd0';

const formatBarDate = (dateStr) => {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('ja-JP', { month: 'numeric', day: 'numeric', weekday: 'short' });
};

const formatConfirmDate = (dateStr) => {
  const d = new Date(dateStr);
  return d.toLocaleDateString('ja-JP', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  });
};

const timeRangeFor = (session, matchNumber) => {
  const schedule = (session?.venueSchedules || []).find((s) => s.matchNumber === matchNumber);
  if (!schedule) return '';
  return `${schedule.startTime?.substring(0, 5)}–${schedule.endTime?.substring(0, 5)}`;
};

const PracticeSessionAttendance = () => {
  const navigate = useNavigate();
  const { currentPlayer } = useAuth();
  const [searchParams] = useSearchParams();
  const sessionId = searchParams.get('sessionId');

  const [session, setSession] = useState(null);
  const [orgMap, setOrgMap] = useState({});
  const [monthParticipations, setMonthParticipations] = useState({});
  const [statusMap, setStatusMap] = useState({});
  const [participationVersion, setParticipationVersion] = useState(null);
  const [lotteryExecutedMap, setLotteryExecutedMap] = useState({});
  const [hasMonthlyLottery, setHasMonthlyLottery] = useState(false);

  const [desiredMatchNumbers, setDesiredMatchNumbers] = useState([]);
  const [selectedCancelMatches, setSelectedCancelMatches] = useState([]);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelReasonDetail, setCancelReasonDetail] = useState('');

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [overlayState, setOverlayState] = useState('idle'); // 'idle' | 'saving' | 'success' | 'error'
  const [overlayErrorDetail, setOverlayErrorDetail] = useState('');
  const [pendingAction, setPendingAction] = useState(null); // 'save' | 'cancel'（当日確認ダイアログ用）
  const [reloadKey, setReloadKey] = useState(0);

  // データ取得: (1) セッション詳細＋団体 → 日付から年月導出 → (2) 月の参加・ステータス。
  // ⚠ getPlayerParticipations（全置換ペイロードの seed）は catch しない。失敗を握りつぶして
  // 空 seed で保存すると、対象セッション以外の同月参加が全消えする（他日データ消失事故）。
  // 失敗時は全体エラーに流し、保存ボタン（コンテンツ描画の内側）に到達させない。
  useEffect(() => {
    let cancelled = false;

    const fetchData = async () => {
      if (!currentPlayer?.id) return;
      if (!sessionId) {
        setError('セッションが指定されていません');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');
      try {
        const [sessionRes, orgsRes] = await Promise.all([
          practiceAPI.getById(sessionId),
          organizationAPI.getAll().catch(() => ({ data: [] })),
        ]);
        const sessionData = sessionRes.data;
        const d = new Date(sessionData.sessionDate);
        const year = d.getFullYear();
        const month = d.getMonth() + 1;

        const [participationsRes, statusRes] = await Promise.all([
          practiceAPI.getPlayerParticipations(currentPlayer.id, year, month),
          practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month),
        ]);

        if (cancelled) return;

        const map = {};
        (orgsRes.data || []).forEach((o) => { map[o.id] = o; });
        setOrgMap(map);
        setSession(sessionData);

        const monthMap = participationsRes.data || {};
        setMonthParticipations(monthMap);
        setDesiredMatchNumbers([...(monthMap[sessionData.id] || [])]);

        const statusData = statusRes.data || {};
        setStatusMap(statusData.participations || {});
        setParticipationVersion(statusData.version ?? null);
        setLotteryExecutedMap(statusData.lotteryExecuted || {});
        setHasMonthlyLottery(Boolean(statusData.hasAnyExecutedLotteryInMonth));
      } catch (err) {
        if (!cancelled) {
          console.error('出欠データ取得エラー:', err);
          setError('データの取得に失敗しました');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    fetchData();
    return () => { cancelled = true; };
  }, [currentPlayer?.id, sessionId, reloadKey]);

  const year = session ? new Date(session.sessionDate).getFullYear() : null;
  const month = session ? new Date(session.sessionDate).getMonth() + 1 : null;

  const { isCurrentMonth: isCurrentMonthMode } = useMemo(
    () => (session ? resolveAttendanceMode(year, month, hasMonthlyLottery) : { isCurrentMonth: false }),
    [session, year, month, hasMonthlyLottery],
  );

  const lotteryExecutedForSession = session ? Boolean(lotteryExecutedMap[session.id]) : false;
  const statusesForSession = useMemo(
    () => (session ? (statusMap[session.id] || []) : []),
    [session, statusMap],
  );
  const monthParticipationsForSession = useMemo(
    () => (session ? (monthParticipations[session.id] || []) : []),
    [session, monthParticipations],
  );

  const sections = useMemo(() => {
    if (!session) {
      return { showRegisterSection: false, registerMatches: [], showCancelSection: false, cancelMatches: [], readonlyStatusMatches: [] };
    }
    return resolveAttendanceSections({
      session,
      isCurrentMonthMode,
      lotteryExecutedForSession,
      monthParticipationsForSession,
      statusesForSession,
    });
  }, [session, isCurrentMonthMode, lotteryExecutedForSession, monthParticipationsForSession, statusesForSession]);

  const capacity = session?.capacity || null;
  const isFull = (matchNumber) => {
    if (!capacity) return false;
    return (session?.matchParticipantCounts?.[matchNumber] || 0) >= capacity;
  };

  // 伝助側で削除承認済みの試合（参加トグルを出さず × 表示）。セクションからは除外済みなので
  // ここで別途 available ∩ 削除候補 を算出して参加不可行として描画する。
  const deletedAvailableMatches = useMemo(() => {
    if (!session) return [];
    const deleted = new Set(session.densukeDeletionCandidateMatchNumbers || []);
    return resolveAvailableMatchNumbers(session).filter((m) => deleted.has(m));
  }, [session]);

  const toggleDesired = (matchNumber) => {
    setDesiredMatchNumbers((prev) =>
      prev.includes(matchNumber)
        ? prev.filter((m) => m !== matchNumber)
        : [...prev, matchNumber],
    );
  };

  const toggleCancelSelection = (matchNumber) => {
    setSelectedCancelMatches((prev) =>
      prev.includes(matchNumber)
        ? prev.filter((m) => m !== matchNumber)
        : [...prev, matchNumber],
    );
  };

  // 参加保存: SAME_DAY 団体の当日12時以降かつ変更ありなら追加確認（PracticeParticipation と同じ判定）
  const needsSaveSameDayConfirm = () =>
    session
      ? needsSameDayConfirmFn({
          sessions: [session],
          orgMap,
          participations: { [session.id]: desiredMatchNumbers },
          initialParticipations: { [session.id]: monthParticipationsForSession },
          now: new Date(),
        })
      : false;

  // キャンセル: 当日12時以降なら追加確認（PracticeCancelPage と同じ判定）
  const isSameDayAfterNoon = () => {
    if (!session) return false;
    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    return session.sessionDate === todayStr && now.getHours() >= 12;
  };

  const handleSave = async () => {
    if (!currentPlayer?.id || !session) return;

    if (needsSaveSameDayConfirm() && pendingAction !== 'save') {
      setPendingAction('save');
      return;
    }
    setPendingAction(null);

    setSaving(true);
    setOverlayErrorDetail('');
    setOverlayState('saving');
    try {
      const participations = buildMonthParticipationsPayload(
        monthParticipations,
        session.id,
        desiredMatchNumbers,
      );
      await practiceAPI.registerParticipations({
        playerId: currentPlayer.id,
        year,
        month,
        participations,
        expectedVersion: participationVersion,
      });
      setOverlayState('success');
    } catch (err) {
      console.error('参加保存エラー:', err);
      if (err.response?.status === 409) {
        setOverlayErrorDetail(
          err.response?.data?.message
          || '他の端末または伝助で参加状況が更新されました。最新を読み込みます。',
        );
        setOverlayState('error');
        setReloadKey((k) => k + 1); // 最新の version / 参加状況を再取得
      } else {
        setOverlayErrorDetail(err.response?.data?.message || '');
        setOverlayState('error');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = async () => {
    if (!session) return;
    if (selectedCancelMatches.length === 0 || !cancelReason) return;
    if (cancelReason === 'OTHER' && !cancelReasonDetail.trim()) return;

    if (isSameDayAfterNoon() && pendingAction !== 'cancel') {
      setPendingAction('cancel');
      return;
    }
    setPendingAction(null);

    const participantIds = selectedCancelMatches
      .map((m) => statusesForSession.find(
        (s) => s.matchNumber === m && (s.status === 'WON' || s.status === 'PENDING'),
      )?.participantId)
      .filter(Boolean);
    if (participantIds.length === 0) return;

    const matchLabels = [...selectedCancelMatches].sort((a, b) => a - b).map((m) => `第${m}試合`).join('、');
    if (!window.confirm(`${formatConfirmDate(session.sessionDate)}の${matchLabels}の参加をキャンセルします。よろしいですか？`)) {
      return;
    }

    setSaving(true);
    setOverlayErrorDetail('');
    setOverlayState('saving');
    try {
      await lotteryAPI.cancelMultiple(
        participantIds,
        cancelReason,
        cancelReason === 'OTHER' ? cancelReasonDetail : null,
      );
      setOverlayState('success');
    } catch (err) {
      console.error('キャンセルエラー:', err);
      setOverlayErrorDetail(err.response?.data?.message || '');
      setOverlayState('error');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <LoadingScreen />;
  }

  const org = session ? orgMap[session.organizationId] : null;
  const showRegisterArea = sections.showRegisterSection || deletedAvailableMatches.length > 0;
  const hasNoOperableMatches = session
    && !showRegisterArea
    && !sections.showCancelSection
    && sections.readonlyStatusMatches.length === 0;

  return (
    <div className="max-w-2xl mx-auto min-h-screen bg-[#f9f6f2]">
      {/* 上部バー（緑）: 戻る + M/D(曜) 会場名 */}
      <div className="bg-[#4a6b5a] border-b border-[#3d5a4c] shadow-sm fixed top-0 left-0 right-0 z-50 px-4 py-4">
        <div className="max-w-2xl mx-auto flex items-center gap-3">
          <button
            onClick={() => navigate('/practice')}
            aria-label="カレンダーに戻る"
            className="p-2 -ml-2 hover:bg-[#3d5a4c] rounded-full transition-colors flex-shrink-0"
          >
            <ArrowLeft className="w-6 h-6 text-white" />
          </button>
          {session && (
            <h1 className="text-lg font-semibold text-white truncate">
              <span>{formatBarDate(session.sessionDate)}</span>
              {session.venueName && <span className="ml-3">{session.venueName}</span>}
            </h1>
          )}
        </div>
      </div>

      <div className="pt-20 pb-16 px-4">
        {error && (
          <div className="mb-4 bg-red-50 border border-red-200 rounded-lg p-3 flex items-start gap-2">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
            <p className="text-sm text-red-700">{error}</p>
          </div>
        )}

        {session && (
          <>
            {/* 練習会（団体）: 団体カラーのドット＋団体名 */}
            {org && (
              <div className="flex items-center gap-2 mb-4">
                <span
                  className="inline-block w-2.5 h-2.5 rounded-full flex-shrink-0"
                  style={{ backgroundColor: org.color || '#666666' }}
                />
                <span className="text-sm font-medium" style={{ color: org.color || '#4a5568' }}>
                  {org.name}
                </span>
              </div>
            )}

            {hasNoOperableMatches && (
              <p className="text-sm text-gray-500 py-8 text-center">操作できる試合がありません</p>
            )}

            {/* 参加する試合セクション（登録可能な試合 or 伝助削除の × 表示があれば出す） */}
            {showRegisterArea && (
              <section className="mb-8">
                <h2 className="text-sm font-semibold text-[#4a6b5a] pb-2 mb-1" style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                  参加する試合
                </h2>
                <ul>
                  {sections.registerMatches.map((matchNumber) => {
                    const checked = desiredMatchNumbers.includes(matchNumber);
                    const full = isFull(matchNumber);
                    const count = session.matchParticipantCounts?.[matchNumber] || 0;
                    const time = timeRangeFor(session, matchNumber);
                    return (
                      <li key={matchNumber} style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                        <label className="flex items-center gap-3 py-3 cursor-pointer select-none touch-manipulation">
                          <span className="text-sm font-medium text-gray-800 w-16 flex-shrink-0">第{matchNumber}試合</span>
                          {time && <span className="text-xs text-gray-500">{time}</span>}
                          <span className="ml-auto text-xs">
                            {full ? (
                              <span className="px-1.5 py-0.5 rounded bg-red-100 text-red-700 font-medium">満員</span>
                            ) : (
                              <span className="text-gray-500">{count}名</span>
                            )}
                          </span>
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleDesired(matchNumber)}
                            aria-label={`第${matchNumber}試合に参加`}
                            className="w-5 h-5 rounded border-gray-300 touch-manipulation flex-shrink-0"
                            style={{ accentColor: '#4a6b5a' }}
                          />
                        </label>
                      </li>
                    );
                  })}
                  {/* 伝助削除承認済み: 参加トグルを出さず × 表示（参加不可） */}
                  {deletedAvailableMatches.map((matchNumber) => (
                    <li key={`deleted-${matchNumber}`} style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                      <div className="flex items-center gap-3 py-3 opacity-60">
                        <span className="text-sm font-medium text-gray-500 w-16 flex-shrink-0">第{matchNumber}試合</span>
                        <span
                          className="ml-auto text-gray-400 font-bold"
                          title={`第${matchNumber}試合: 伝助側で削除されました`}
                        >
                          ×
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
                {sections.showRegisterSection && (
                  <>
                    <p className="text-xs text-gray-400 mt-2">満員でも申込できます（キャンセル待ちになる場合あり）</p>
                    <div className="flex justify-end mt-3">
                      <button
                        onClick={handleSave}
                        disabled={saving}
                        className="px-5 py-2 text-sm font-medium rounded-lg border transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        style={{ borderColor: '#a98e80', color: '#82655a' }}
                      >
                        参加を保存
                      </button>
                    </div>
                  </>
                )}
              </section>
            )}

            {/* 参加をキャンセルセクション（当月扱いのみ） */}
            {sections.showCancelSection && (
              <section className="mb-8">
                <h2 className="text-sm font-semibold text-[#b3524f] pb-2 mb-1" style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                  参加をキャンセル
                </h2>
                <ul>
                  {sections.cancelMatches.map((matchNumber) => {
                    const checked = selectedCancelMatches.includes(matchNumber);
                    const time = timeRangeFor(session, matchNumber);
                    return (
                      <li key={matchNumber} style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                        <label className="flex items-center gap-3 py-3 cursor-pointer select-none touch-manipulation">
                          <span className="text-sm font-medium text-gray-800 w-16 flex-shrink-0">第{matchNumber}試合</span>
                          {time && <span className="text-xs text-gray-500">{time}</span>}
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleCancelSelection(matchNumber)}
                            aria-label={`第${matchNumber}試合をキャンセル対象に選択`}
                            className="ml-auto w-5 h-5 rounded border-gray-300 touch-manipulation flex-shrink-0"
                            style={{ accentColor: '#d9534f' }}
                          />
                        </label>
                      </li>
                    );
                  })}
                </ul>

                {selectedCancelMatches.length > 0 && (
                  <div className="mt-4">
                    <p className="text-sm font-medium text-gray-700 mb-2">
                      キャンセル理由 <span className="text-red-500">*</span>
                    </p>
                    <div className="space-y-1.5">
                      {CANCEL_REASONS.map((reason) => (
                        <label key={reason.value} className="flex items-center gap-2.5 py-1.5 cursor-pointer select-none">
                          <input
                            type="radio"
                            name="cancelReason"
                            value={reason.value}
                            checked={cancelReason === reason.value}
                            onChange={(e) => setCancelReason(e.target.value)}
                            className="w-4 h-4"
                            style={{ accentColor: '#b3524f' }}
                          />
                          <span className="text-sm text-gray-800">{reason.label}</span>
                        </label>
                      ))}
                    </div>
                    {cancelReason === 'OTHER' && (
                      <textarea
                        value={cancelReasonDetail}
                        onChange={(e) => setCancelReasonDetail(e.target.value)}
                        placeholder="具体的な理由を入力してください"
                        rows={3}
                        className="mt-2 w-full p-3 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-[#b3524f] focus:border-transparent resize-none"
                      />
                    )}
                  </div>
                )}

                <div className="flex justify-end mt-3">
                  <button
                    onClick={handleCancel}
                    disabled={
                      saving
                      || selectedCancelMatches.length === 0
                      || !cancelReason
                      || (cancelReason === 'OTHER' && !cancelReasonDetail.trim())
                    }
                    className="px-5 py-2 text-sm font-medium rounded-lg bg-[#d9534f] text-white transition-colors hover:bg-[#c9302c] disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    選択した試合をキャンセル
                  </button>
                </div>
              </section>
            )}

            {/* 読み取り専用（操作対象外の参加: キャンセル待ち・応答待ち等） */}
            {sections.readonlyStatusMatches.length > 0 && (
              <section className="mb-8">
                <h2 className="text-sm font-semibold text-gray-500 pb-2 mb-1" style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                  その他の申込
                </h2>
                <ul>
                  {sections.readonlyStatusMatches.map((matchNumber) => {
                    const st = statusesForSession.find((s) => s.matchNumber === matchNumber);
                    const info = st ? STATUS_LABEL[st.status] : null;
                    const time = timeRangeFor(session, matchNumber);
                    return (
                      <li key={matchNumber} style={{ borderBottom: `0.5px solid ${HAIRLINE}` }}>
                        <div className="flex items-center gap-3 py-3">
                          <span className="text-sm font-medium text-gray-700 w-16 flex-shrink-0">第{matchNumber}試合</span>
                          {time && <span className="text-xs text-gray-500">{time}</span>}
                          {info && (
                            <span className={`ml-auto text-[11px] px-1.5 py-0.5 rounded font-bold ${info.color}`}>
                              {info.label}
                              {st?.status === 'WAITLISTED' && st?.waitlistNumber ? ` #${st.waitlistNumber}` : ''}
                            </span>
                          )}
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </section>
            )}
          </>
        )}
      </div>

      {/* SAME_DAY 当日12時以降の追加確認ダイアログ（参加保存・キャンセル共通） */}
      {pendingAction && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-sm w-full p-6">
            <div className="flex items-start gap-3 mb-4">
              <AlertCircle className="w-6 h-6 text-amber-500 flex-shrink-0 mt-0.5" />
              <div>
                <h3 className="font-bold text-gray-800 mb-2">確認</h3>
                <p className="text-sm text-gray-600">
                  {pendingAction === 'cancel'
                    ? '直前のキャンセルとなりますがよろしいですか？'
                    : '12時以降の参加登録・キャンセルは管理者への連絡が必須です。連絡しましたか？'}
                </p>
              </div>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => setPendingAction(null)}
                className="flex-1 py-2.5 px-4 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                {pendingAction === 'cancel' ? '戻る' : 'いいえ'}
              </button>
              <button
                onClick={pendingAction === 'cancel' ? handleCancel : handleSave}
                className={`flex-1 py-2.5 px-4 text-white rounded-lg ${
                  pendingAction === 'cancel' ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'
                }`}
              >
                {pendingAction === 'cancel' ? 'キャンセルする' : 'はい'}
              </button>
            </div>
          </div>
        </div>
      )}

      <SaveProgressOverlay
        state={overlayState}
        savingMessage="処理中..."
        successMessage="保存しました"
        errorMessage="処理に失敗しました"
        errorDetail={overlayErrorDetail}
        onSuccessConfirm={() => navigate('/practice')}
        onErrorClose={() => setOverlayState('idle')}
      />
    </div>
  );
};

export default PracticeSessionAttendance;
