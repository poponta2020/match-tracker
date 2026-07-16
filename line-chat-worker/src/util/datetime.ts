/**
 * ISO8601（+09:00 等のオフセット付き）⇄ JST壁時計値の変換ロジック。
 *
 * 【注意】LINE Official Account Manager の日時入力欄に実際に投入する値の書式
 * （テキスト入力かプルダウン選択か、区切り文字等）はタスク7（Phase 2 ローカルPoC）で
 * 実DOMを調査してから確定する。ここでは DOM 非依存の「JST構成要素への分解」までを
 * 純関数として提供し、DOM操作側（ChatPage 実装）が必要な形式へ整形する。
 */

/** JSTの壁時計表現（年月日時分）。 */
export interface JstDateTimeParts {
  year: number;
  /** 1-12 */
  month: number;
  /** 1-31 */
  day: number;
  /** 0-23 */
  hour: number;
  /** 0-59 */
  minute: number;
}

const JST_OFFSET_MINUTES = 9 * 60;

/**
 * ISO8601 文字列（任意のオフセットを許容）を JST の壁時計値に変換する。
 * 例: "2026-07-18T08:00:00+09:00" → { year:2026, month:7, day:18, hour:8, minute:0 }
 * 例: "2026-07-17T23:00:00Z" → { year:2026, month:7, day:18, hour:8, minute:0 }（UTC→JST日付繰上り）
 */
export function parseIsoToJstParts(iso: string): JstDateTimeParts {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`invalid ISO8601 datetime: ${iso}`);
  }

  const jstMs = date.getTime() + JST_OFFSET_MINUTES * 60_000;
  const jst = new Date(jstMs);

  return {
    year: jst.getUTCFullYear(),
    month: jst.getUTCMonth() + 1,
    day: jst.getUTCDate(),
    hour: jst.getUTCHours(),
    minute: jst.getUTCMinutes(),
  };
}

/**
 * JST構成要素を "YYYY/MM/DD" "HH:mm" の暫定表現に整形する（表示・ログ・比較用の共通フォーマット）。
 * OAM の実際の入力欄書式が確定するまでの暫定値。ChatPage 実装（タスク7）で必要な形式へ
 * 変換すること（このまま入力欄に渡せると仮定しない）。
 */
export function formatJstParts(parts: JstDateTimeParts): { dateText: string; timeText: string } {
  const pad = (n: number): string => String(n).padStart(2, "0");
  return {
    dateText: `${parts.year}/${pad(parts.month)}/${pad(parts.day)}`,
    timeText: `${pad(parts.hour)}:${pad(parts.minute)}`,
  };
}
