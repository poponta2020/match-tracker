/**
 * 「当月扱い／来月扱い／過去月」を判定する共通ヘルパー。
 *
 * 判定ルール：
 * - 表示月 < 現在年月 → 過去月（isPastMonth: true）
 * - 表示月 == 現在年月 → 当月扱い
 * - 表示月 > 現在年月 で月内に抽選確定済み（SUCCESS）が1つでもあれば → 当月扱い（例外）
 * - 表示月 > 現在年月 で抽選確定済みなし → 来月扱い
 *
 * 第3引数は月単位フラグ `hasAnyExecutedLottery`（バックエンドの
 * `PlayerParticipationStatusDto.hasAnyExecutedLotteryInMonth` をそのまま渡す）。
 * セッション単位のロック判定（lotteryExecuted）とは分離されている。
 *
 * @param {number} year 判定対象の年
 * @param {number} month 判定対象の月（1-12）
 * @param {boolean} [hasAnyExecutedLottery] 月内に抽選確定済みが1つでもあれば true
 * @param {Date} [now] 現在時刻（テスト時に注入可能）
 * @returns {{ isCurrentMonth: boolean, isPastMonth: boolean }} 判定結果
 */
export function resolveAttendanceMode(year, month, hasAnyExecutedLottery, now = new Date()) {
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;

  const targetIndex = year * 12 + (month - 1);
  const nowIndex = currentYear * 12 + (currentMonth - 1);

  if (targetIndex < nowIndex) {
    return { isCurrentMonth: false, isPastMonth: true };
  }
  if (targetIndex === nowIndex) {
    return { isCurrentMonth: true, isPastMonth: false };
  }
  return { isCurrentMonth: Boolean(hasAnyExecutedLottery), isPastMonth: false };
}
