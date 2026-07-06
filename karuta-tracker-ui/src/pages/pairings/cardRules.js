/**
 * 札ルール生成のための純粋関数群。
 * `PairingSummary.jsx` から extract（テスト可能化のため）。
 *
 * 札ルールは「日付（＋再生成カウンタ nonce）をシードにした決定論的生成」で導出する。
 * 同じ `(date, nonce)` なら必ず同じ札ルール列になり、端末・再訪・過去日でブレない。
 * 札ルール配列そのものは localStorage に保存せず、再生成カウンタ nonce のみを保存する。
 */

/**
 * 札番号は01〜99, 00(=100番)の100枚
 * 2桁ゼロパディングで管理
 */
export const ALL_CARDS = Array.from({ length: 100 }, (_, i) => {
  const num = (i + 1) % 100;
  return String(num).padStart(2, '0');
});

/** 札番号の1の位を取得 */
export const onesDigit = (card) => parseInt(card[1], 10);

/** 札番号の10の位を取得 */
export const tensDigit = (card) => parseInt(card[0], 10);

/**
 * 文字列 `date#nonce` を 32bit 符号なし整数へハッシュする（FNV-1a 32bit）。
 * 決定論PRNG `mulberry32` のシードとして用いる。
 */
export function hashSeed(date, nonce = 0) {
  const str = `${date}#${nonce}`;
  let h = 0x811c9dc5; // FNV-1a 32bit offset basis (2166136261)
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193); // FNV prime (16777619)
  }
  return h >>> 0;
}

/**
 * 決定論的擬似乱数生成器（mulberry32）。
 * 32bit シードから `() => number(0..1)` を返す。同一シードなら同一の数列。
 */
export function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * 配列から n 個を選ぶ（部分 Fisher-Yates シャッフル）。
 * `rng` は `() => number(0..1)` の乱数源。省略時は `Math.random`。
 * `sort(() => rng()-0.5)` は分布が偏り、決定論的乱数では比較関数が非推移的になり
 * エンジン依存の挙動を生むため、均一かつエンジン非依存な Fisher-Yates を用いる。
 */
export function pickRandom(arr, n, rng = Math.random) {
  const a = [...arr];
  const count = Math.min(n, a.length);
  for (let i = 0; i < count; i++) {
    const j = i + Math.floor(rng() * (a.length - i));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a.slice(0, count);
}

/**
 * 札ルール生成（3試合サイクル）
 * @param {number} totalMatches 試合数
 * @param {() => number} [rng=Math.random] 乱数源。決定論化には seeded PRNG を渡す。
 * @returns {Array<{type: string, digits: number[], removedCard: string|null, description: string}>}
 *
 * 各試合が消費する乱数の本数・順序は試合番号のみで決まり `totalMatches` に依存しないため、
 * 同一シードであれば `totalMatches` を増やしても先頭の試合の札ルールは安定する。
 */
export function generateCardRules(totalMatches, rng = Math.random) {
  const rules = [];
  let prevUnusedDigits = null;
  let prevUsedDigits = null;

  for (let i = 0; i < totalMatches; i++) {
    const cyclePos = i % 3;

    if (cyclePos === 0) {
      const allDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
      const chosen = pickRandom(allDigits, 5, rng).sort((a, b) => a - b);
      const unused = allDigits.filter(d => !chosen.includes(d));
      prevUnusedDigits = unused;
      prevUsedDigits = chosen;

      rules.push({
        type: 'ones',
        digits: chosen,
        removedCard: null,
        description: `一の位${chosen.join('.')}`,
      });
    } else if (cyclePos === 1) {
      const source = prevUnusedDigits || [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
      const chosen = pickRandom(source, 3, rng).sort((a, b) => a - b);

      const matchingCards = ALL_CARDS.filter(card =>
        chosen.includes(onesDigit(card)) || chosen.includes(tensDigit(card))
      );

      const removedCard = matchingCards[Math.floor(rng() * matchingCards.length)];

      prevUsedDigits = chosen;
      prevUnusedDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(d => !chosen.includes(d));

      rules.push({
        type: 'nuki',
        digits: chosen,
        removedCard,
        description: `${chosen.join('.')}　${parseInt(removedCard, 10) || 100}抜き`,
      });
    } else {
      const source = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(d => !prevUsedDigits.includes(d));
      const chosen = pickRandom(source, 5, rng).sort((a, b) => a - b);
      const unused = source.filter(d => !chosen.includes(d));
      prevUnusedDigits = unused;
      prevUsedDigits = chosen;

      rules.push({
        type: 'tens',
        digits: chosen,
        removedCard: null,
        description: `十の位${chosen.join('.')}`,
      });
    }
  }

  return rules;
}

/** 旧形式（札ルール配列）の localStorage キー接頭辞。新方式では読まず、掃除対象のみ。 */
export const STORAGE_PREFIX = 'karuta-tracker:card-rules:';

/** 再生成カウンタ（nonce）の localStorage キー接頭辞。 */
export const NONCE_PREFIX = 'karuta-tracker:card-nonce:';

/**
 * 日付ごとの再生成カウンタ nonce を localStorage から読む。
 * 未保存・不正値・localStorage 不可環境では既定の 0 を返す（既定状態は全端末で一致）。
 */
export function loadNonce(date) {
  try {
    const raw = localStorage.getItem(NONCE_PREFIX + date);
    if (raw == null) return 0;
    // 文字列全体が非負整数のときのみ採用。parseInt は '1abc'/'1.5'/'1e2' の先頭を拾うため正規表現で全体検証する
    if (!/^\d+$/.test(raw)) return 0;
    const n = Number(raw);
    return Number.isInteger(n) && n >= 0 ? n : 0;
  } catch {
    return 0;
  }
}

/** 日付ごとの再生成カウンタ nonce を localStorage に保存。失敗時は黙ってスキップ。 */
export function saveNonce(date, n) {
  try {
    localStorage.setItem(NONCE_PREFIX + date, String(n));
  } catch {
    // localStorage 不可環境はスキップ
  }
}

/**
 * 日付（＋ nonce）から決定論的に札ルール列を生成する公開ヘルパ。
 * 保存に依存せず、同じ日・同じ nonce なら常に同じ札ルールになる。
 * `nonce` 省略時は保存済み nonce（既定 0）を読む。再生成時は確定済みの nonce を明示で渡すことで、
 * localStorage 保存の成否に依存せず再計算できる。
 */
export function getCardRules(date, totalMatches, nonce = loadNonce(date)) {
  const rng = mulberry32(hashSeed(date, nonce));
  return generateCardRules(totalMatches, rng);
}

/** 札番号2桁文字列（"00"=100）→ 1〜100 の整数 */
export function cardToNumber(card) {
  const n = parseInt(card, 10);
  return n === 0 ? 100 : n;
}

/**
 * 札ルール1つ → 出札の札番号配列（1〜100の整数・50枚・番号昇順）。
 * ones: 一の位が該当 / tens: 十の位が該当 / nuki: 一の位or十の位が該当から removedCard を除外。
 */
export function expandRule(rule) {
  if (!rule) return [];
  let cards;
  if (rule.type === 'ones') {
    cards = ALL_CARDS.filter((c) => rule.digits.includes(onesDigit(c)));
  } else if (rule.type === 'tens') {
    cards = ALL_CARDS.filter((c) => rule.digits.includes(tensDigit(c)));
  } else if (rule.type === 'nuki') {
    cards = ALL_CARDS
      .filter((c) => rule.digits.includes(onesDigit(c)) || rule.digits.includes(tensDigit(c)))
      .filter((c) => c !== rule.removedCard);
  } else {
    return [];
  }
  return cards.map(cardToNumber).sort((a, b) => a - b);
}

/**
 * (date, totalMatches, matchNumber, nonce) → その試合の出札札番号配列（50枚）。
 * matchNumber は 1 始まり。nonce は DB 共有値を明示で渡す（省略時は localStorage 既定）。
 */
export function getMatchCards(date, totalMatches, matchNumber, nonce = loadNonce(date)) {
  const rules = getCardRules(date, totalMatches, nonce);
  return expandRule(rules[matchNumber - 1]);
}

/** クライアント端末ローカルタイムの今日（YYYY-MM-DD） */
export function getTodayLocalDateStr() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * localStorage の掃除:
 * - 旧形式の札ルール配列キー（STORAGE_PREFIX）は新方式で一切読まないため全削除する。
 * - nonce キー（NONCE_PREFIX）は今日以外を削除する（今日分は再生成状態の保持のため残す）。
 */
export function cleanupOldCardRules() {
  try {
    const today = getTodayLocalDateStr();
    const toRemove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key) continue;
      if (key.startsWith(STORAGE_PREFIX)) {
        // 旧形式の札ルール配列キーは全削除
        toRemove.push(key);
        continue;
      }
      if (key.startsWith(NONCE_PREFIX)) {
        const keyDate = key.substring(NONCE_PREFIX.length);
        if (keyDate !== today) toRemove.push(key);
      }
    }
    for (const k of toRemove) localStorage.removeItem(k);
  } catch {
    // 失敗時はスキップ
  }
}
