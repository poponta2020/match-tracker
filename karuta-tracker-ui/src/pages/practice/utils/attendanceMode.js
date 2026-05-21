/**
 * 「当月扱い／来月扱い／過去月」を判定する共通ヘルパー。
 *
 * 判定ルール：
 * - 表示月 < 現在年月 → 過去月（isPastMonth: true）
 * - 表示月 == 現在年月 → 当月扱い
 * - 表示月 > 現在年月 で抽選確定済みセッションが1つでもあれば → 当月扱い（例外）
 * - 表示月 > 現在年月 で抽選確定済みセッションが0個 → 来月扱い
 *
 * @param {number} year 判定対象の年
 * @param {number} month 判定対象の月（1-12）
 * @param {Object<number, boolean>} [lotteryExecutedMap] セッションIDごとの抽選確定状態
 * @param {Date} [now] 現在時刻（テスト時に注入可能）
 * @returns {{ isCurrentMonth: boolean, isPastMonth: boolean }} 判定結果
 */
export function resolveAttendanceMode(year, month, lotteryExecutedMap, now = new Date()) {
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
  const hasExecutedLottery = Object.values(lotteryExecutedMap || {}).some(Boolean);
  return { isCurrentMonth: hasExecutedLottery, isPastMonth: false };
}
