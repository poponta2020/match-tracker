/**
 * 1日分出欠登録画面（PracticeSessionAttendance）の純ロジック。
 *
 * 回帰の要を描画から切り離してテスト可能にする 2 関数を提供する:
 *  - buildMonthParticipationsPayload: registerParticipations の全置換ペイロードを組む
 *  - resolveAttendanceSections: 参加/キャンセル/読み取り専用の 3 セクション振り分け
 *
 * 既存 2 画面（PracticeParticipation / PracticeCancelPage）の挙動 union を
 * 1 セッションにスコープして再構成したもの。新しいビジネスルールは足していない。
 */

// この app の練習セッションは 1 日最大 7 試合（既存 PracticeParticipation の
// 7 列テーブルと同じ不変条件）。totalMatches 欠落時のフォールバックも 7。
export const MAX_MATCHES_PER_SESSION = 7;

// 理由付きキャンセルの対象（PracticeCancelPage.getCancellableMatches と一致）
const CANCELLABLE_STATUSES = new Set(['WON', 'PENDING']);
// 「参加中」とみなすステータス（未参加判定の否定条件に使う）
const ACTIVE_STATUSES = new Set(['WON', 'PENDING', 'WAITLISTED', 'OFFERED']);

/**
 * 対象セッションで表示・操作しうる試合番号（1..min(totalMatches, 7)）。
 * @param {{ totalMatches?: number }} session
 * @returns {number[]}
 */
export function resolveAvailableMatchNumbers(session) {
  const total = Math.min(
    Number(session?.totalMatches) || MAX_MATCHES_PER_SESSION,
    MAX_MATCHES_PER_SESSION,
  );
  return Array.from({ length: total }, (_, i) => i + 1);
}

/**
 * registerParticipations（月・選手単位の全置換 API）へ送るペイロードを組む。
 *
 * ⚠ 全置換対策の肝: 月マップ（getPlayerParticipations の戻り = {sessionId: [matchNumber]}）を
 * seed にして、対象セッションだけを targetDesiredMatchNumbers で差し替える。他セッション（他日）の
 * 参加は必ずそのまま payload に残す。対象日だけを送ると他日の参加が消える（データ消失事故）。
 *
 * targetDesiredMatchNumbers は「対象セッションの完全な希望アクティブ集合」
 * （＝既存アクティブ登録を seed にトグル差分を適用した結果）であること。参加セクションの
 * トグルだけを渡してはならない（当月扱いでは既存登録が参加セクションに出ないため落ちる）。
 *
 * @param {Record<string|number, number[]>} monthParticipations 月の参加マップ（全セッション）
 * @param {number|string} targetSessionId 差し替える対象セッションID
 * @param {number[]} targetDesiredMatchNumbers 対象セッションの完全な希望集合
 * @returns {Array<{ sessionId: number, matchNumber: number }>} 全置換ペイロード
 */
export function buildMonthParticipationsPayload(
  monthParticipations,
  targetSessionId,
  targetDesiredMatchNumbers,
) {
  const target = Number(targetSessionId);
  const map = monthParticipations || {};
  const result = [];

  // 他セッション（他日）の参加はそのまま保持
  for (const [sid, matches] of Object.entries(map)) {
    if (Number(sid) === target) continue; // 対象は下で希望集合に差し替える
    (matches || []).forEach((m) => {
      result.push({ sessionId: Number(sid), matchNumber: Number(m) });
    });
  }

  // 対象セッションを希望集合で差し替え（重複排除）。空なら対象は payload から消える（全解除）
  const desired = [...new Set((targetDesiredMatchNumbers || []).map(Number))];
  desired.forEach((m) => {
    result.push({ sessionId: target, matchNumber: m });
  });

  // API は全置換で順序非依存。テスト・比較の決定性のためにソートして返す
  return result.sort((a, b) =>
    a.sessionId - b.sessionId || a.matchNumber - b.matchNumber,
  );
}

/**
 * 対象セッション 1 件を、参加/キャンセル/読み取り専用の 3 セクションに振り分ける。
 *
 * 排他振り分け（design-spec §5 の状態マトリクス）:
 *  - 参加する試合（register）: 未参加・抽選前・伝助削除でない試合
 *  - 参加をキャンセル（cancel）: WON / PENDING（理由付き）
 *  - 読み取り専用（readonly）: WAITLISTED / OFFERED 等、操作対象外の参加
 *  各試合は最大 1 セクションにのみ現れる（重複させない）。
 *
 * モード差:
 *  - 抽選確定済みセッション: 追加登録不可（register 非表示）、WON/PENDING のみキャンセル可
 *  - 来月扱い（未来月・抽選前）で**締切前**: 全試合をトグル（登録済みは呼び出し側で pre-check）、キャンセル非表示
 *  - 当月扱い、**または締切後**（未来月でも）: 既存参加はトグルに出さず理由付きキャンセルへ回す
 *    （未参加=参加側 / WON・PENDING=キャンセル側 / その他アクティブ=readonly）
 *
 * ⚠ 締切後（`beforeDeadline=false`）の来月扱いを全トグルにしてはならない。バックエンドの
 * `registerAfterDeadline` は payload から省略された既存参加を削除しない（追加のみ）ため、既存参加を
 * uncheck して全置換保存しても実データは残り、UI と食い違う（サイレント no-op）。既存
 * `PracticeParticipation.isLockedRegistration`（`!isCurrentMonthMode && beforeDeadline` のときのみ
 * 解除可、それ以外はロック）と同じ判定で、締切後は理由付きキャンセル（`cancelMultiple`）へ回す。
 *
 * @param {object} params
 * @param {{ totalMatches?: number, densukeDeletionCandidateMatchNumbers?: number[] }} params.session
 * @param {boolean} params.isCurrentMonthMode resolveAttendanceMode の isCurrentMonth
 * @param {boolean} params.lotteryExecutedForSession 当該セッションの抽選確定済みフラグ
 * @param {boolean} [params.beforeDeadline=true] 締切前か（`PlayerParticipationStatusDto.beforeDeadline`。既定 true）
 * @param {number[]} params.monthParticipationsForSession 月マップの対象セッション分（希望集合の seed）
 * @param {Array<{ matchNumber: number, status: string }>} params.statusesForSession 対象セッションのステータス配列
 * @returns {{
 *   showRegisterSection: boolean,
 *   registerMatches: number[],
 *   showCancelSection: boolean,
 *   cancelMatches: number[],
 *   readonlyStatusMatches: number[],
 * }}
 */
export function resolveAttendanceSections({
  session,
  isCurrentMonthMode,
  lotteryExecutedForSession,
  beforeDeadline = true,
  monthParticipationsForSession,
  statusesForSession,
}) {
  const available = resolveAvailableMatchNumbers(session);
  const deleted = new Set(session?.densukeDeletionCandidateMatchNumbers || []);
  const notDeleted = available.filter((m) => !deleted.has(m));

  const statusByMatch = new Map();
  (statusesForSession || []).forEach((s) => {
    if (s && typeof s.matchNumber === 'number') {
      statusByMatch.set(s.matchNumber, s.status);
    }
  });
  const participatedSet = new Set((monthParticipationsForSession || []).map(Number));

  const isActive = (m) => {
    const s = statusByMatch.get(m);
    return (s !== undefined && ACTIVE_STATUSES.has(s)) || participatedSet.has(m);
  };
  const isCancellable = (m) => CANCELLABLE_STATUSES.has(statusByMatch.get(m));

  // 抽選確定済みセッション: 追加登録不可、WON/PENDING のみキャンセル、他ステータスは読み取り専用
  if (lotteryExecutedForSession) {
    const cancelMatches = notDeleted.filter(isCancellable);
    const readonlyStatusMatches = notDeleted.filter((m) => {
      const s = statusByMatch.get(m);
      return s !== undefined && !CANCELLABLE_STATUSES.has(s);
    });
    return {
      showRegisterSection: false,
      registerMatches: [],
      showCancelSection: cancelMatches.length > 0,
      cancelMatches,
      readonlyStatusMatches,
    };
  }

  // 来月扱い（未来月・抽選前）かつ締切前: 全試合をトグル（登録済みは呼び出し側で pre-check）、キャンセルなし
  if (!isCurrentMonthMode && beforeDeadline) {
    return {
      showRegisterSection: notDeleted.length > 0,
      registerMatches: notDeleted,
      showCancelSection: false,
      cancelMatches: [],
      readonlyStatusMatches: [],
    };
  }

  // 当月扱い、または締切後の未来月: 未参加=参加側 / WON・PENDING=キャンセル側 / その他アクティブ=readonly
  // （締切後の既存参加は理由付きキャンセルへ回す。全置換 uncheck では消えないため）
  const registerMatches = notDeleted.filter((m) => !isActive(m));
  const cancelMatches = notDeleted.filter(isCancellable);
  const readonlyStatusMatches = notDeleted.filter(
    (m) => isActive(m) && !isCancellable(m),
  );
  return {
    showRegisterSection: registerMatches.length > 0,
    registerMatches,
    showCancelSection: cancelMatches.length > 0,
    cancelMatches,
    readonlyStatusMatches,
  };
}
