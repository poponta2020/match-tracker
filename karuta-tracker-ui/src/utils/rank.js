/**
 * 級位・段位の選択肢と連動ロジック
 *
 * 単体編集（PlayerEdit.jsx の handleKyuRankChange）と同一のルール:
 *   E級→無段 / D級→初段 / C級→弐段 / B級→参段 / A級→四段（A級のみ手動で四段〜八段を指定可）
 */

export const KYU_RANKS = ['E級', 'D級', 'C級', 'B級', 'A級'];

export const DAN_RANKS = ['無段', '初段', '弐段', '参段', '四段', '五段', '六段', '七段', '八段'];

// A級で手動指定できる段位（四段〜八段）。A級以外は級に連動するため使わない
export const A_CLASS_DAN_RANKS = ['四段', '五段', '六段', '七段', '八段'];

/**
 * 級から自動連動する段位を返す。A級は手動指定の起点として四段を返す。
 * 未設定（空）の場合は空文字を返す。
 * @param {string} kyuRank 級位
 * @returns {string} 連動する段位
 */
export const defaultDanForKyu = (kyuRank) => {
  switch (kyuRank) {
    case 'E級':
      return '無段';
    case 'D級':
      return '初段';
    case 'C級':
      return '弐段';
    case 'B級':
      return '参段';
    case 'A級':
      return '四段';
    default:
      return '';
  }
};

/**
 * 級位を頭文字1字の短縮ラベルに整形する（例: "A級" → "(A)"）。表示専用。
 *
 * 対戦相手名の脇に控えめに級を出すため、"A級〜E級" の頭文字だけを括弧付きで返す。
 * 未設定（null/undefined/空文字/文字列以外）の場合は空文字を返し、呼び出し側で非表示にできる。
 *
 * @param {string} kyuRank 級位（"A級"〜"E級" 等）
 * @returns {string} "(A)" 形式の短縮ラベル。未設定時は ""
 */
export const kyuRankShortLabel = (kyuRank) => {
  if (typeof kyuRank !== 'string') return '';
  const initial = kyuRank.trim().charAt(0);
  return initial ? `(${initial})` : '';
};
