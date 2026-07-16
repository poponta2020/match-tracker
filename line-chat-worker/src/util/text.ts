/** 重複予約判定に使う本文冒頭の切り出し長（文字数）。 */
export const TEXT_PREFIX_LENGTH = 20;

/** 「同一本文冒頭」の判定に使う文字列を切り出す純関数。 */
export function textPrefix(text: string, length: number = TEXT_PREFIX_LENGTH): string {
  return text.slice(0, length);
}
