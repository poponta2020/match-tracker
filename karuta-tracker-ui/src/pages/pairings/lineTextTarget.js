/**
 * 「LINE送信用テキスト生成」導線（全試合／単一試合のセグメントトグル）のための純粋ロジック。
 * PairingGenerator.jsx から extract（テスト可能化のため）。
 */

/**
 * 各セグメントの有効条件を算出する。
 * - 全試合（allComplete）: 1..totalMatches すべての試合の組み合わせが揃っている
 * - 単一試合（singleComplete）: 選択中の試合の組み合わせが完成している
 * @param {number} totalMatches セッションの総試合数
 * @param {Object} matchExistsMap { [試合番号]: boolean } 各試合の組み合わせ完成フラグ
 * @param {number} matchNumber 選択中の試合番号（1始まり）
 * @returns {{ allComplete: boolean, singleComplete: boolean }}
 */
export function computeLineTextAvailability(totalMatches, matchExistsMap, matchNumber) {
  const total = totalMatches || 0;
  const allComplete =
    total > 0 &&
    Array.from({ length: total }, (_, i) => i + 1).every((num) => !!matchExistsMap?.[num]);
  const singleComplete = !!matchExistsMap?.[matchNumber];
  return { allComplete, singleComplete };
}

/**
 * 希望ターゲット（preferred）と各セグメントの有効性から、実際に有効なターゲットを解決する。
 * - 希望が有効ならそれを採用（ユーザーの選択を尊重）
 * - 希望が無効なら有効な方へ自動フォールバック（全試合を優先）
 * - 両方無効なら null（セクション非表示）
 * @param {'all'|'single'} preferred 現在のトグル選択
 * @returns {'all'|'single'|null}
 */
export function resolveLineTextTarget(preferred, allComplete, singleComplete) {
  if (preferred === 'all' && allComplete) return 'all';
  if (preferred === 'single' && singleComplete) return 'single';
  if (allComplete) return 'all';
  if (singleComplete) return 'single';
  return null;
}

/**
 * 札ルール一覧画面（PairingSummary）への遷移 URL を組み立てる。
 * - 全試合: /pairings/summary?date=...
 * - 単一試合: /pairings/summary?date=...&matchNumber=N
 * @param {string} sessionDate 対象日付（YYYY-MM-DD）
 * @param {number} matchNumber 選択中の試合番号（単一試合時に付与）
 * @param {'all'|'single'} target 解決済みターゲット
 * @returns {string}
 */
export function buildSummaryUrl(sessionDate, matchNumber, target) {
  const base = `/pairings/summary?date=${sessionDate}`;
  return target === 'single' ? `${base}&matchNumber=${matchNumber}` : base;
}
