/**
 * 日付ユーティリティ。
 *
 * `new Date().toISOString().split('T')[0]` は UTC 基準のため、日本時間（UTC+9）の
 * 00:00〜08:59 では前日の日付になってしまう。日付選択 UI の初期値などローカル日付を
 * 期待する箇所で前日表示にならないよう、ローカルタイムゾーン基準で `YYYY-MM-DD` を組み立てる。
 */

// 2桁ゼロ埋め
const pad2 = (n) => String(n).padStart(2, '0');

/**
 * 任意の Date をローカルタイムゾーン基準の `YYYY-MM-DD` 文字列に変換する。
 * @param {Date} date 変換対象（既定: 現在時刻）
 * @returns {string} `YYYY-MM-DD`
 */
export const toLocalISODate = (date = new Date()) =>
  `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

/**
 * 今日のローカル日付を `YYYY-MM-DD` で返す。
 * @returns {string} `YYYY-MM-DD`
 */
export const todayLocalISODate = () => toLocalISODate(new Date());

/**
 * ISO 日付文字列（`YYYY-MM-DD`）を1日進める。
 *
 * 上の `toLocalISODate` 系がローカルTZ基準なのに対し、こちらは **TZ 非依存**（`Date.UTC` ベース）。
 * 「明日」の日付を、サーバーがJSTで解決した当日（API レスポンスの `date`）を基準に算出する用途で、
 * デバイスの現在時刻・タイムゾーンに一切依存しない純関数として使う。月末・年末・うるう年の繰り上げも正しく処理する。
 * @param {string} iso `YYYY-MM-DD`
 * @returns {string} 翌日の `YYYY-MM-DD`（例: '2026-07-31' → '2026-08-01'）
 */
export const addOneIsoDay = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  // Date.UTC は日・月・年のオーバーフローを正規化する（d+1 が月末を超えても翌月へ繰り上がる）
  const next = new Date(Date.UTC(y, m - 1, d + 1));
  return `${next.getUTCFullYear()}-${pad2(next.getUTCMonth() + 1)}-${pad2(next.getUTCDate())}`;
};

export default todayLocalISODate;
