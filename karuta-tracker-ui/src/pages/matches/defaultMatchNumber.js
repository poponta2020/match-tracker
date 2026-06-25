/**
 * 試合番号デフォルト決定ロジック（純粋関数群）
 *
 * 試合結果一覧画面 (MatchResultsView) と一括入力画面 (BulkResultInput) の
 * 初期表示試合番号を、現在時刻と入力状況に応じて決定する。
 *
 * 要件: docs/features/match-number-default-by-time-and-progress/requirements.md
 *
 * - 時刻ベース: 会場の試合番号ごとの開始・終了時刻 (venueSchedules) を用い、
 *   「now < (N試合目の終了時刻 + GRACE_MINUTES)」を満たす最小の試合番号 N を選ぶ。
 *   どの境界にも該当しない（最終試合の終了+猶予を過ぎた）場合は 1 に戻す。
 * - 当日（端末ローカル日付 === sessionDate）かつ venueSchedules ありのときのみ発動。
 * - 一括入力画面はさらに上位制約として「入力済み最大番号 + 1」を優先する。
 */

// 試合切替後、前の試合番号をデフォルトに保つ猶予時間（分）。固定値。
export const GRACE_MINUTES = 15;

/**
 * "HH:mm:ss" / "HH:mm" を 0時からの分数に変換する。
 * 不正値・未定義は null を返す。
 *
 * @param {string} timeStr
 * @returns {number|null}
 */
export function toMinutes(timeStr) {
  if (typeof timeStr !== 'string') return null;
  // "HH:mm" / "HH:mm:ss" 形式かつ時刻範囲（00:00〜23:59）のみ受け付ける。
  // "18:15abc"（末尾不正）・"99:99"（範囲外）・"18x:15"（桁不正）などは null を返す。
  const match = /^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/.exec(timeStr);
  if (!match) return null;
  return parseInt(match[1], 10) * 60 + parseInt(match[2], 10);
}

/**
 * sessionDate ("YYYY-MM-DD") が now の端末ローカル日付と同一（当日）かを判定する。
 *
 * @param {string} sessionDate - "YYYY-MM-DD"
 * @param {Date}   now         - 端末ローカル時刻
 * @returns {boolean}
 */
export function isToday(sessionDate, now) {
  if (!sessionDate || !now) return false;
  const y = now.getFullYear();
  const mo = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return sessionDate === `${y}-${mo}-${d}`;
}

/**
 * 時刻ベースのデフォルト試合番号。
 * endTime を持つ試合番号を昇順に走査し、now の分換算が
 * 「endTime + GRACE_MINUTES」未満となる最小の matchNumber を返す。
 * 該当なし（最終試合の終了+猶予を過ぎた）なら 1 を返す。
 * endTime が未定義／不正な試合番号はスキップする。
 *
 * @param {Array}  venueSchedules - [{ matchNumber, startTime, endTime }]
 * @param {Date}   now            - 端末ローカル時刻
 * @returns {number} デフォルト試合番号（1始まり）
 */
export function timeBasedDefaultMatchNumber(venueSchedules, now) {
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  const sorted = (venueSchedules || [])
    .filter(s => s && toMinutes(s.endTime) !== null)
    .slice()
    .sort((a, b) => a.matchNumber - b.matchNumber);
  for (const s of sorted) {
    const boundary = toMinutes(s.endTime) + GRACE_MINUTES;
    if (nowMinutes < boundary) {
      return s.matchNumber;
    }
  }
  return 1;
}

/**
 * 全入力済みの試合番号の配列を返す。
 * 各試合番号について「全ペアリングに対応する Match が存在する」なら入力済みとみなす。
 * ペアリング0件の試合番号は含めない（既存 isMatchCompleted と同じ判定）。
 * player id は min/max 正規化で突合する（保存時に player1Id < player2Id へ正規化されるため）。
 *
 * @param {Object} params
 * @param {Array}  params.pairings     - [{ matchNumber, player1Id, player2Id }]
 * @param {Array}  params.matches      - [{ matchNumber, player1Id, player2Id }]
 * @param {number} params.totalMatches - 総試合数
 * @returns {number[]} 入力済み試合番号の配列（昇順）
 */
export function getCompletedMatchNumbers({ pairings, matches, totalMatches }) {
  const total = totalMatches || 0;
  const result = [];
  for (let num = 1; num <= total; num++) {
    const matchPairings = (pairings || []).filter(p => p.matchNumber === num);
    if (matchPairings.length === 0) continue; // ペアリング0件は入力済みとみなさない
    const allInput = matchPairings.every(pairing => {
      const lo = Math.min(pairing.player1Id, pairing.player2Id);
      const hi = Math.max(pairing.player1Id, pairing.player2Id);
      return (matches || []).some(m =>
        m.matchNumber === num &&
        Math.min(m.player1Id, m.player2Id) === lo &&
        Math.max(m.player1Id, m.player2Id) === hi
      );
    });
    if (allInput) result.push(num);
  }
  return result;
}

/**
 * 試合結果一覧画面 (MatchResultsView) の初期表示試合番号を決定する。
 * 1. urlMatchNumber 指定があれば最優先で返す（保存後遷移などの明示指定）。
 * 2. 当日 かつ venueSchedules ありなら時刻ベース。
 * 3. いずれでもなければ 1。
 *
 * @param {Object} params
 * @param {number} [params.urlMatchNumber] - URLクエリ指定の試合番号（無効なら null/undefined）
 * @param {Array}  [params.venueSchedules]
 * @param {string} [params.sessionDate]    - "YYYY-MM-DD"
 * @param {Date}   params.now
 * @returns {number}
 */
export function defaultForResultsView({ urlMatchNumber, venueSchedules, sessionDate, now }) {
  if (urlMatchNumber) return urlMatchNumber;
  if (!isToday(sessionDate, now) || !venueSchedules?.length) return 1;
  return timeBasedDefaultMatchNumber(venueSchedules, now);
}

/**
 * 試合結果一括入力画面 (BulkResultInput) の初期表示試合番号を決定する。
 * 1. 入力済み試合番号があれば min(max(completed) + 1, totalMatches) を最優先で返す。
 * 2. 入力済み皆無なら、当日 かつ venueSchedules ありで時刻ベース。
 * 3. いずれでもなければ 1。
 *
 * @param {Object} params
 * @param {number[]} [params.completedMatchNumbers] - 全入力済みの試合番号
 * @param {number}   params.totalMatches
 * @param {Array}    [params.venueSchedules]
 * @param {string}   [params.sessionDate]           - "YYYY-MM-DD"
 * @param {Date}     params.now
 * @returns {number}
 */
export function defaultForBulkInput({ completedMatchNumbers, totalMatches, venueSchedules, sessionDate, now }) {
  if (completedMatchNumbers && completedMatchNumbers.length > 0) {
    const maxCompleted = Math.max(...completedMatchNumbers);
    return Math.min(maxCompleted + 1, totalMatches);
  }
  if (!isToday(sessionDate, now) || !venueSchedules?.length) return 1;
  return timeBasedDefaultMatchNumber(venueSchedules, now);
}
