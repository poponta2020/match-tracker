/**
 * 百人一首 決まり字マスター（100枚・確定版）
 * 出典/確定: docs/features/取り札記録/kimariji-master.md
 *
 * - 札番号(1〜100) → 決まり字。決まり字は最大4文字（縦書きチップに収める）。
 * - 共札は「共通字・区別字」記法（例 わた・や）。あさぼ系のみ「・」なしで4文字（あさぼあ/あさぼう）。
 * - 参照ファイルの誤りは補正済み: 041=こひ、068=こころに、082=おも。
 */
export const KIMARIJI = {
  1: 'あきの', 2: 'はるす', 3: 'あし', 4: 'たご', 5: 'おく',
  6: 'かさ', 7: 'あまの', 8: 'わがい', 9: 'はなの', 10: 'これ',
  11: 'わた・や', 12: 'あまつ', 13: 'つく', 14: 'みち', 15: 'きみ・は',
  16: 'たち', 17: 'ちは', 18: 'す', 19: 'なにわが', 20: 'わび',
  21: 'いまこ', 22: 'ふ', 23: 'つき', 24: 'この', 25: 'なにし',
  26: 'おぐ', 27: 'みかの', 28: 'やまざ', 29: 'こころあ', 30: 'ありあ',
  31: 'あさぼあ', 32: 'やまが', 33: 'ひさ', 34: 'たれ', 35: 'ひとは',
  36: 'なつ', 37: 'しら', 38: 'わすら', 39: 'あさじ', 40: 'しの',
  41: 'こひ', 42: 'ちぎりき', 43: 'あい', 44: 'おおこ', 45: 'あわれ',
  46: 'ゆら', 47: 'やえ', 48: 'かぜを', 49: 'みかき', 50: 'きみ・お',
  51: 'かく', 52: 'あけ', 53: 'なげき', 54: 'わすれ', 55: 'たき',
  56: 'あらざ', 57: 'め', 58: 'ありま', 59: 'やす', 60: 'おおえ',
  61: 'いに', 62: 'よを', 63: 'いまは', 64: 'あさぼう', 65: 'うら',
  66: 'もろ', 67: 'はるの', 68: 'こころに', 69: 'あらし', 70: 'さ',
  71: 'ゆう', 72: 'おと', 73: 'たか', 74: 'うか', 75: 'ちぎりお',
  76: 'わた・こ', 77: 'せ', 78: 'あわじ', 79: 'あきか', 80: 'ながか',
  81: 'ほ', 82: 'おも', 83: 'よの・よ', 84: 'ながら', 85: 'よも',
  86: 'なげけ', 87: 'む', 88: 'なにわえ', 89: 'たま', 90: 'みせ',
  91: 'きり', 92: 'わがそ', 93: 'よの・は', 94: 'みよ', 95: 'おおけ',
  96: 'はなさ', 97: 'こぬ', 98: 'かぜそ', 99: 'ひとも', 100: 'もも',
};

/** 札番号(1〜100) → 決まり字。未定義は番号文字列でフォールバック。 */
export function kimariji(cardNo) {
  return KIMARIJI[cardNo] ?? String(cardNo);
}

/** 全100枚の {no, kimariji} 配列（聞き間違いピッカー等・番号順） */
export const ALL_KIMARIJI = Array.from({ length: 100 }, (_, i) => ({
  no: i + 1,
  kimariji: KIMARIJI[i + 1],
}));

// 決まり字1文字目の並び順（むすめふさほせ…）
export const FIRST_CHAR_ORDER = 'むすめふさほせうつしもゆいちひきはやよかみたこおわなあ';

// 五十音辞書順（濁点・半濁点・小書きを基本音の直後に並べた照合順）
export const KANA_COLLATION = 'あぁいぃうぅえぇおぉかがきぎくぐけげこごさざしじすずせぜそぞただちぢつっづてでとどなにぬねのはばぱひびぴふぶぷへべぺほぼぽまみむめもやゃゆゅよょらりるれろわゐゑをんー';

/** 五十音辞書順における文字の順位。未知の文字は末尾扱い。 */
export function kanaRank(ch) {
  const idx = KANA_COLLATION.indexOf(ch);
  return idx === -1 ? KANA_COLLATION.length : idx;
}

/**
 * 決まり字文字列同士の比較（「・」を除去した上で五十音辞書順、
 * 完全一致する接頭辞なら短い方を先に）。
 */
export function compareKimariji(a, b) {
  const sa = a.replace(/・/g, '');
  const sb = b.replace(/・/g, '');
  const len = Math.min(sa.length, sb.length);
  for (let i = 0; i < len; i++) {
    const diff = kanaRank(sa[i]) - kanaRank(sb[i]);
    if (diff !== 0) return diff;
  }
  return sa.length - sb.length;
}

/**
 * 札番号同士を「決まり字1文字目＝むすめふさほせ…順、
 * 同一1文字目内は決まり字の五十音順」で比較する。
 */
export function compareCardsByDecisionOrder(noA, noB) {
  const a = kimariji(noA);
  const b = kimariji(noB);
  const rankA = FIRST_CHAR_ORDER.indexOf(a[0]);
  const rankB = FIRST_CHAR_ORDER.indexOf(b[0]);
  const firstRankA = rankA === -1 ? FIRST_CHAR_ORDER.length : rankA;
  const firstRankB = rankB === -1 ? FIRST_CHAR_ORDER.length : rankB;
  if (firstRankA !== firstRankB) return firstRankA - firstRankB;
  return compareKimariji(a, b);
}
