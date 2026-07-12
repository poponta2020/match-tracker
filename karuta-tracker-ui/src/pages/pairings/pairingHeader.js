// 対戦組み合わせ画面（PairingGenerator）ヘッダの「日付 会場名」表示ロジック（純粋関数）。
// 本番 JSX とテストで同じ関数を import することで、会場名フォールバック等の分岐退行を確実に検知する
// （pairingDisplayLogic / lineTextTarget 等と同じ「純粋関数を切り出して本番・テスト双方で import する」方針）。

/**
 * `YYYY-MM-DD` の練習日をヘッダ表示用の `M/D`（ゼロ埋めなし）に整形する。
 * 想定外フォーマット（分割できない等）は入力文字列をそのまま返す（表示は崩さない）。
 *
 * @param {string} sessionDate 例: '2026-07-12'
 * @returns {string} 例: '7/12'
 */
export const formatHeaderDate = (sessionDate) => {
  const parts = (sessionDate || '').split('-');
  return parts.length === 3 ? `${Number(parts[1])}/${Number(parts[2])}` : (sessionDate || '');
};

/**
 * ヘッダに表示する会場名を解決する。会場名が無い（null / 非文字列 / 空 / 空白のみ）場合は
 * `null` を返し、呼び出し側は日付のみを表示する（フォールバック）。
 *
 * @param {string|null|undefined} venueName セッションの会場名（`PracticeSessionDto.venueName`）
 * @returns {string|null} 表示すべき会場名、無ければ null
 */
export const resolveHeaderVenue = (venueName) => {
  if (typeof venueName !== 'string') return null;
  const trimmed = venueName.trim();
  return trimmed.length > 0 ? trimmed : null;
};
