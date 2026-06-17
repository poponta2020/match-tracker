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

export default todayLocalISODate;
