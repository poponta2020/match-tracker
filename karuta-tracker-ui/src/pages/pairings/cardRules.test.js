import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  generateCardRules,
  pickRandom,
  hashSeed,
  mulberry32,
  loadNonce,
  saveNonce,
  getCardRules,
  getTodayLocalDateStr,
  cleanupOldCardRules,
  STORAGE_PREFIX,
  NONCE_PREFIX,
  cardToNumber,
  expandRule,
  getMatchCards,
} from './cardRules';

beforeEach(() => {
  localStorage.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('generateCardRules', () => {
  it('totalMatches 件の札ルールを生成する', () => {
    const rules = generateCardRules(3);
    expect(rules).toHaveLength(3);
  });

  it('1試合目は "ones" タイプ（一の位）で digits 長 5', () => {
    const rules = generateCardRules(1);
    expect(rules[0].type).toBe('ones');
    expect(rules[0].digits).toHaveLength(5);
    expect(rules[0].removedCard).toBeNull();
    expect(rules[0].description).toMatch(/^一の位/);
  });

  it('2試合目は "nuki" タイプ（抜き）で digits 長 3 / removedCard あり', () => {
    const rules = generateCardRules(2);
    expect(rules[1].type).toBe('nuki');
    expect(rules[1].digits).toHaveLength(3);
    expect(typeof rules[1].removedCard).toBe('string');
    expect(rules[1].removedCard).toMatch(/^[0-9]{2}$/);
  });

  it('3試合目は "tens" タイプ（十の位）で digits 長 5', () => {
    const rules = generateCardRules(3);
    expect(rules[2].type).toBe('tens');
    expect(rules[2].digits).toHaveLength(5);
    expect(rules[2].removedCard).toBeNull();
    expect(rules[2].description).toMatch(/^十の位/);
  });

  it('4試合目（cyclePos=0）はサイクルが折り返して "ones"', () => {
    const rules = generateCardRules(4);
    expect(rules[3].type).toBe('ones');
  });

  it('全 digits は 0〜9 の整数のみ', () => {
    const rules = generateCardRules(6);
    for (const r of rules) {
      for (const d of r.digits) {
        expect(Number.isInteger(d)).toBe(true);
        expect(d).toBeGreaterThanOrEqual(0);
        expect(d).toBeLessThanOrEqual(9);
      }
    }
  });

  it('nuki の3つの数字は前の試合（ones）で使わなかった5つから選ばれる', () => {
    const rules = generateCardRules(2);
    const onesDigits = new Set(rules[0].digits);
    for (const d of rules[1].digits) {
      expect(onesDigits.has(d)).toBe(false);
    }
  });

  it('tens の5つの数字は前の試合（nuki）で使わなかった7つから選ばれる', () => {
    const rules = generateCardRules(3);
    const nukiDigits = new Set(rules[1].digits);
    for (const d of rules[2].digits) {
      expect(nukiDigits.has(d)).toBe(false);
    }
  });

  describe('決定論性（seeded rng）', () => {
    it('同一シードの rng → 同一札ルール列', () => {
      const seed = hashSeed('2026-06-09', 0);
      const a = generateCardRules(6, mulberry32(seed));
      const b = generateCardRules(6, mulberry32(seed));
      expect(a).toEqual(b);
    });

    it('totalMatches を増やしても先頭の試合は安定（決定論列の先頭安定）', () => {
      const seed = hashSeed('2026-06-09', 0);
      const five = generateCardRules(5, mulberry32(seed));
      const three = generateCardRules(3, mulberry32(seed));
      // 各試合が消費する乱数は試合番号のみで決まるため、先頭3件が一致する
      expect(five.slice(0, 3)).toEqual(three);
    });

    it('シード継続: 先頭1件は totalMatches に依らず一致（旧 prefix 続行の置換）', () => {
      const seed = hashSeed('2026-06-09', 0);
      const one = generateCardRules(1, mulberry32(seed));
      const four = generateCardRules(4, mulberry32(seed));
      expect(four[0]).toEqual(one[0]);
      // サイクルは続行する（2件目以降の type は ones→nuki→tens→ones）
      expect(four[1].type).toBe('nuki');
      expect(four[2].type).toBe('tens');
      expect(four[3].type).toBe('ones');
    });

    it('異なるシード → 異なる札ルール列', () => {
      const a = generateCardRules(6, mulberry32(hashSeed('2026-06-09', 0)));
      const b = generateCardRules(6, mulberry32(hashSeed('2026-06-09', 1)));
      expect(a).not.toEqual(b);
    });
  });
});

describe('hashSeed', () => {
  it('同一入力 → 同一ハッシュ（決定論）', () => {
    expect(hashSeed('2026-06-09', 0)).toBe(hashSeed('2026-06-09', 0));
  });

  it('date が異なれば異なるハッシュ', () => {
    expect(hashSeed('2026-06-09', 0)).not.toBe(hashSeed('2026-06-10', 0));
  });

  it('nonce が異なれば異なるハッシュ', () => {
    expect(hashSeed('2026-06-09', 0)).not.toBe(hashSeed('2026-06-09', 1));
  });

  it('nonce 省略時は 0 と同等', () => {
    expect(hashSeed('2026-06-09')).toBe(hashSeed('2026-06-09', 0));
  });

  it('32bit 符号なし整数を返す', () => {
    const h = hashSeed('2026-06-09', 7);
    expect(Number.isInteger(h)).toBe(true);
    expect(h).toBeGreaterThanOrEqual(0);
    expect(h).toBeLessThanOrEqual(0xffffffff);
  });
});

describe('mulberry32', () => {
  it('同一シード → 同一数列', () => {
    const r1 = mulberry32(123456);
    const r2 = mulberry32(123456);
    expect([r1(), r1(), r1()]).toEqual([r2(), r2(), r2()]);
  });

  it('異なるシード → 異なる数列', () => {
    expect(mulberry32(1)()).not.toBe(mulberry32(2)());
  });

  it('生成値は 0 以上 1 未満', () => {
    const r = mulberry32(987654321);
    for (let i = 0; i < 50; i++) {
      const v = r();
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThan(1);
    }
  });
});

describe('pickRandom', () => {
  it('n 個を返し、すべて元配列の要素である', () => {
    const picked = pickRandom([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], 5, mulberry32(42));
    expect(picked).toHaveLength(5);
    for (const x of picked) expect([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]).toContain(x);
  });

  it('重複なく選ぶ', () => {
    const picked = pickRandom([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], 5, mulberry32(42));
    expect(new Set(picked).size).toBe(picked.length);
  });

  it('同一シードの rng なら同一結果（決定論・エンジン非依存）', () => {
    const a = pickRandom([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], 5, mulberry32(7));
    const b = pickRandom([0, 1, 2, 3, 4, 5, 6, 7, 8, 9], 5, mulberry32(7));
    expect(a).toEqual(b);
  });

  it('n が配列長を超える場合は全要素を返す', () => {
    const picked = pickRandom([1, 2, 3], 5, mulberry32(1));
    expect(picked).toHaveLength(3);
    expect([...picked].sort((a, b) => a - b)).toEqual([1, 2, 3]);
  });

  it('元配列を破壊しない', () => {
    const src = [1, 2, 3, 4, 5];
    pickRandom(src, 3, mulberry32(1));
    expect(src).toEqual([1, 2, 3, 4, 5]);
  });
});

describe('loadNonce / saveNonce', () => {
  it('未保存の日付は既定 0', () => {
    expect(loadNonce('2026-06-09')).toBe(0);
  });

  it('保存した値を読み出せる（round-trip）', () => {
    saveNonce('2026-06-09', 3);
    expect(loadNonce('2026-06-09')).toBe(3);
  });

  it('数値でない不正値は 0 にフォールバック', () => {
    localStorage.setItem(NONCE_PREFIX + '2026-06-09', 'abc');
    expect(loadNonce('2026-06-09')).toBe(0);
  });

  it('負値は 0 にフォールバック', () => {
    localStorage.setItem(NONCE_PREFIX + '2026-06-09', '-1');
    expect(loadNonce('2026-06-09')).toBe(0);
  });

  it('部分的に数値で始まる文字列（1abc / 1.5 / 1e2）も 0 にフォールバック', () => {
    for (const bad of ['1abc', '1.5', '1e2']) {
      localStorage.setItem(NONCE_PREFIX + '2026-06-09', bad);
      expect(loadNonce('2026-06-09')).toBe(0);
    }
  });
});

describe('getCardRules', () => {
  it('nonce 未保存（既定 0）でも決定論的に同じ列を返す', () => {
    expect(getCardRules('2026-06-09', 5)).toEqual(getCardRules('2026-06-09', 5));
  });

  it('保存が無くても日付シードから再現できる（端末・再訪非依存）', () => {
    const a = getCardRules('2026-06-09', 5);
    localStorage.clear(); // 保存をすべて消しても日付から再計算できる
    expect(getCardRules('2026-06-09', 5)).toEqual(a);
  });

  it('totalMatches を増やしても先頭の試合は変わらない', () => {
    const three = getCardRules('2026-06-09', 3);
    const five = getCardRules('2026-06-09', 5);
    expect(five.slice(0, 3)).toEqual(three);
  });

  it('nonce を上げると別の札ルール列になる（再生成）', () => {
    const before = getCardRules('2026-06-09', 5);
    saveNonce('2026-06-09', loadNonce('2026-06-09') + 1);
    const after = getCardRules('2026-06-09', 5);
    expect(after).not.toEqual(before);
  });

  it('日付が違えば別の札ルール列になる', () => {
    expect(getCardRules('2026-06-09', 5)).not.toEqual(getCardRules('2026-06-10', 5));
  });

  it('generateCardRules(同一シード) と一致する', () => {
    const viaHelper = getCardRules('2026-06-09', 4);
    const direct = generateCardRules(4, mulberry32(hashSeed('2026-06-09', 0)));
    expect(viaHelper).toEqual(direct);
  });
});

describe('cardToNumber', () => {
  it('"00" は 100 番', () => {
    expect(cardToNumber('00')).toBe(100);
  });
  it('"05" は 5 番、"99" は 99 番', () => {
    expect(cardToNumber('05')).toBe(5);
    expect(cardToNumber('99')).toBe(99);
  });
});

describe('expandRule（札ルール→出札50枚）', () => {
  it('ones: 一の位が該当する札ちょうど50枚（すべて条件を満たす）', () => {
    const rule = { type: 'ones', digits: [0, 1, 2, 3, 4], removedCard: null };
    const cards = expandRule(rule);
    expect(cards).toHaveLength(50);
    for (const n of cards) {
      // 100番→"00"→一の位0。それ以外は n%10。
      const ones = n === 100 ? 0 : n % 10;
      expect(rule.digits).toContain(ones);
    }
  });

  it('tens: 十の位が該当する札ちょうど50枚', () => {
    const rule = { type: 'tens', digits: [0, 1, 2, 3, 4], removedCard: null };
    const cards = expandRule(rule);
    expect(cards).toHaveLength(50);
    for (const n of cards) {
      const tens = n === 100 ? 0 : Math.floor(n / 10) % 10;
      expect(rule.digits).toContain(tens);
    }
  });

  it('nuki: 51枚から removedCard を除いてちょうど50枚', () => {
    // removedCard は必ず対象集合内の札（生成側は matchingCards から選ぶ）。35は ones5・tens3 とも該当。
    const rule = { type: 'nuki', digits: [3, 5, 7], removedCard: '35' };
    const cards = expandRule(rule);
    expect(cards).toHaveLength(50);
    expect(cards).not.toContain(35); // 除外札
  });

  it('全札番号は 1〜100 の一意な整数・昇順', () => {
    const cards = expandRule({ type: 'ones', digits: [0, 1, 2, 3, 4], removedCard: null });
    expect(new Set(cards).size).toBe(cards.length);
    const sorted = [...cards].sort((a, b) => a - b);
    expect(cards).toEqual(sorted);
    for (const n of cards) {
      expect(n).toBeGreaterThanOrEqual(1);
      expect(n).toBeLessThanOrEqual(100);
    }
  });

  it('未知のルール/nullは空配列', () => {
    expect(expandRule(null)).toEqual([]);
    expect(expandRule({ type: 'unknown', digits: [] })).toEqual([]);
  });
});

describe('getMatchCards', () => {
  it('各試合の出札は50枚（決定論）', () => {
    const c1 = getMatchCards('2026-06-09', 3, 1, 0);
    expect(c1).toHaveLength(50);
    // 同一 (date, total, matchNumber, nonce) なら同一
    expect(getMatchCards('2026-06-09', 3, 1, 0)).toEqual(c1);
  });

  it('試合番号ごとに異なる出札（サイクル）', () => {
    const c1 = getMatchCards('2026-06-09', 3, 1, 0);
    const c2 = getMatchCards('2026-06-09', 3, 2, 0);
    expect(c1).not.toEqual(c2);
  });

  it('nonce を上げると別の出札になる', () => {
    const before = getMatchCards('2026-06-09', 3, 1, 0);
    const after = getMatchCards('2026-06-09', 3, 1, 1);
    expect(after).not.toEqual(before);
  });
});

describe('getTodayLocalDateStr', () => {
  it('YYYY-MM-DD フォーマットで返す', () => {
    const today = getTodayLocalDateStr();
    expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('指定した Date を mock してその日の YYYY-MM-DD を返す', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0)); // 2026-06-09 12:00 ローカル
    expect(getTodayLocalDateStr()).toBe('2026-06-09');
    vi.useRealTimers();
  });

  it('月・日が1桁の場合もゼロパディングされる', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 5, 9, 0, 0)); // 2026-01-05
    expect(getTodayLocalDateStr()).toBe('2026-01-05');
    vi.useRealTimers();
  });
});

describe('cleanupOldCardRules', () => {
  it('旧形式の札ルール配列キーは（今日分も含め）全削除される', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', JSON.stringify([{ type: 'ones' }]));
    localStorage.setItem(STORAGE_PREFIX + '2026-06-08', JSON.stringify([{ type: 'ones' }]));
    cleanupOldCardRules();

    // 新方式は旧形式配列を一切読まないため、今日分も含めて掃除する
    expect(localStorage.getItem(STORAGE_PREFIX + '2026-06-09')).toBeNull();
    expect(localStorage.getItem(STORAGE_PREFIX + '2026-06-08')).toBeNull();
    vi.useRealTimers();
  });

  it('今日の nonce キーは保持される', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    saveNonce('2026-06-09', 2);
    cleanupOldCardRules();

    expect(loadNonce('2026-06-09')).toBe(2);
    vi.useRealTimers();
  });

  it('今日以外の nonce キーは削除される', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    saveNonce('2026-06-08', 1);
    saveNonce('2025-12-31', 5);
    cleanupOldCardRules();

    expect(localStorage.getItem(NONCE_PREFIX + '2026-06-08')).toBeNull();
    expect(localStorage.getItem(NONCE_PREFIX + '2025-12-31')).toBeNull();
    vi.useRealTimers();
  });

  it('NONCE_PREFIX / STORAGE_PREFIX 以外のキーには影響しない', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    localStorage.setItem('auth-token', 'abc123');
    localStorage.setItem('other-app:foo', 'bar');
    cleanupOldCardRules();

    expect(localStorage.getItem('auth-token')).toBe('abc123');
    expect(localStorage.getItem('other-app:foo')).toBe('bar');
    vi.useRealTimers();
  });

  it('今日の nonce と過去日の nonce・旧形式キーが混在しても、今日の nonce のみ残る', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    saveNonce('2026-06-09', 1);
    saveNonce('2026-06-08', 1);
    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', JSON.stringify([{ type: 'ones' }]));
    cleanupOldCardRules();

    expect(loadNonce('2026-06-09')).toBe(1);
    expect(localStorage.getItem(NONCE_PREFIX + '2026-06-08')).toBeNull();
    expect(localStorage.getItem(STORAGE_PREFIX + '2026-06-09')).toBeNull();
    vi.useRealTimers();
  });
});
