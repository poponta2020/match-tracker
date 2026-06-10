/**
 * 札ルール生成・localStorage 永続化のための純粋関数群。
 * `PairingSummary.jsx` から extract（テスト可能化のため）。
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

/** 配列からn個ランダムに選ぶ */
export function pickRandom(arr, n) {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

/**
 * 札ルール生成（3試合サイクル）
 * @param {number} totalMatches 試合数
 * @param {Array} [prefix=[]] 既存の札ルール配列。指定時はその末尾から続きを生成して `prefix.concat(extra)` を返す
 * @returns {Array<{type: string, digits: number[], removedCard: string|null, description: string}>}
 */
export function generateCardRules(totalMatches, prefix = []) {
  const rules = prefix.length > 0 ? [...prefix] : [];
  let prevUnusedDigits = null;
  let prevUsedDigits = null;

  if (prefix.length > 0) {
    const last = prefix[prefix.length - 1];
    prevUsedDigits = [...last.digits];
    prevUnusedDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9].filter(d => !last.digits.includes(d));
  }

  for (let i = rules.length; i < totalMatches; i++) {
    const cyclePos = i % 3;

    if (cyclePos === 0) {
      const allDigits = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];
      const chosen = pickRandom(allDigits, 5).sort((a, b) => a - b);
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
      const chosen = pickRandom(source, 3).sort((a, b) => a - b);

      const matchingCards = ALL_CARDS.filter(card =>
        chosen.includes(onesDigit(card)) || chosen.includes(tensDigit(card))
      );

      const removedCard = matchingCards[Math.floor(Math.random() * matchingCards.length)];

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
      const chosen = pickRandom(source, 5).sort((a, b) => a - b);
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

export const STORAGE_PREFIX = 'karuta-tracker:card-rules:';

const VALID_RULE_TYPES = new Set(['ones', 'nuki', 'tens']);

/**
 * 保存済み札ルール要素の構造検証
 * `digits` 配列長は 'ones'/'tens' で5、'nuki' で3 を要求する
 */
export function isValidCardRule(rule) {
  if (!rule || typeof rule !== 'object') return false;
  if (!VALID_RULE_TYPES.has(rule.type)) return false;
  if (!Array.isArray(rule.digits)) return false;
  if (!rule.digits.every(d => Number.isInteger(d) && d >= 0 && d <= 9)) return false;
  const expectedDigitsLen = rule.type === 'nuki' ? 3 : 5;
  if (rule.digits.length !== expectedDigitsLen) return false;
  if (typeof rule.description !== 'string') return false;
  if (rule.removedCard !== null && typeof rule.removedCard !== 'string') return false;
  return true;
}

/**
 * localStorage から日付指定の札ルールを復元。失敗時は null を返す
 * - 配列でない / 各要素が `isValidCardRule` を満たさない場合も null
 *   （破損データや旧バージョンを取り込んで `generateCardRules` 続行時に
 *   `last.digits` 参照で例外を投げるのを防ぐ）
 */
export function loadCardRules(date) {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + date);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return null;
    if (!parsed.every(isValidCardRule)) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** localStorage に日付指定の札ルールを保存。失敗時は黙ってスキップ */
export function saveCardRules(date, rules) {
  try {
    localStorage.setItem(STORAGE_PREFIX + date, JSON.stringify(rules));
  } catch {
    // localStorage 不可環境はスキップ
  }
}

/** クライアント端末ローカルタイムの今日（YYYY-MM-DD） */
export function getTodayLocalDateStr() {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** STORAGE_PREFIX で始まるキーのうち、今日以外の日付のものを localStorage から削除 */
export function cleanupOldCardRules() {
  try {
    const today = getTodayLocalDateStr();
    const toRemove = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(STORAGE_PREFIX)) {
        const keyDate = key.substring(STORAGE_PREFIX.length);
        if (keyDate !== today) toRemove.push(key);
      }
    }
    for (const k of toRemove) localStorage.removeItem(k);
  } catch {
    // 失敗時はスキップ
  }
}

/**
 * 保存済み札ルールと試合数を突き合わせ、表示用配列と保存上書きの要否を返す
 * - 一致: そのまま
 * - 保存が長い: 先頭totalMatches分のみ返す（localStorage 側は保持）
 * - 保存が短い: 末尾に不足分を追加生成（localStorage 側は上書き必要）
 */
export function reconcileCardRules(stored, totalMatches) {
  if (stored.length === totalMatches) return { rules: stored, changed: false };
  if (stored.length > totalMatches) {
    return { rules: stored.slice(0, totalMatches), changed: false };
  }
  const extended = generateCardRules(totalMatches, stored);
  return { rules: extended, changed: true };
}
