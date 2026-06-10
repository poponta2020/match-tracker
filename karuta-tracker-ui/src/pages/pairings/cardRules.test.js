import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  generateCardRules,
  loadCardRules,
  saveCardRules,
  getTodayLocalDateStr,
  cleanupOldCardRules,
  reconcileCardRules,
  isValidCardRule,
  STORAGE_PREFIX,
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

  describe('prefix で続行', () => {
    it('prefix が空配列の場合は totalMatches 件を新規生成（デフォルト挙動）', () => {
      const rules = generateCardRules(3, []);
      expect(rules).toHaveLength(3);
      expect(rules[0].type).toBe('ones');
    });

    it('prefix 3 件 + totalMatches 5 → 5 件返却、先頭3件は prefix のまま', () => {
      const prefix = generateCardRules(3);
      const extended = generateCardRules(5, prefix);
      expect(extended).toHaveLength(5);
      expect(extended[0]).toEqual(prefix[0]);
      expect(extended[1]).toEqual(prefix[1]);
      expect(extended[2]).toEqual(prefix[2]);
    });

    it('prefix 3 件（ones, nuki, tens）+ 続行で 4 件目は "ones"（サイクル折り返し）', () => {
      const prefix = generateCardRules(3);
      const extended = generateCardRules(4, prefix);
      expect(extended[3].type).toBe('ones');
    });

    it('prefix 1 件（ones）+ 続行で 2 件目は "nuki"（サイクル続行）', () => {
      const prefix = generateCardRules(1);
      const extended = generateCardRules(2, prefix);
      expect(extended[1].type).toBe('nuki');
    });

    it('prefix 2 件 + 続行で 3 件目は "tens"', () => {
      const prefix = generateCardRules(2);
      const extended = generateCardRules(3, prefix);
      expect(extended[2].type).toBe('tens');
    });

    it('prefix からの続行時、nuki digits が直前の ones digits を含まない', () => {
      const prefix = generateCardRules(1);
      const extended = generateCardRules(2, prefix);
      const onesDigits = new Set(prefix[0].digits);
      for (const d of extended[1].digits) {
        expect(onesDigits.has(d)).toBe(false);
      }
    });
  });
});

describe('isValidCardRule', () => {
  it('正しい ones ルールは valid', () => {
    expect(isValidCardRule({
      type: 'ones',
      digits: [0, 1, 2, 3, 4],
      removedCard: null,
      description: '一の位0.1.2.3.4',
    })).toBe(true);
  });

  it('正しい nuki ルールは valid', () => {
    expect(isValidCardRule({
      type: 'nuki',
      digits: [5, 6, 7],
      removedCard: '57',
      description: '5.6.7　57抜き',
    })).toBe(true);
  });

  it('正しい tens ルールは valid', () => {
    expect(isValidCardRule({
      type: 'tens',
      digits: [0, 1, 2, 8, 9],
      removedCard: null,
      description: '十の位0.1.2.8.9',
    })).toBe(true);
  });

  it('null / undefined / プリミティブは invalid', () => {
    expect(isValidCardRule(null)).toBe(false);
    expect(isValidCardRule(undefined)).toBe(false);
    expect(isValidCardRule('string')).toBe(false);
    expect(isValidCardRule(123)).toBe(false);
    expect(isValidCardRule(true)).toBe(false);
  });

  it('空オブジェクトは invalid', () => {
    expect(isValidCardRule({})).toBe(false);
  });

  it('不正な type は invalid', () => {
    expect(isValidCardRule({
      type: 'unknown',
      digits: [0, 1, 2, 3, 4],
      removedCard: null,
      description: 'desc',
    })).toBe(false);
  });

  it('digits が配列でない場合は invalid', () => {
    expect(isValidCardRule({
      type: 'ones',
      digits: 'not-array',
      removedCard: null,
      description: 'desc',
    })).toBe(false);
  });

  it('digits の要素が 0〜9 範囲外の場合は invalid', () => {
    expect(isValidCardRule({
      type: 'ones',
      digits: [0, 1, 2, 3, 10],
      removedCard: null,
      description: 'desc',
    })).toBe(false);
  });

  it('digits の要素が整数でない場合は invalid', () => {
    expect(isValidCardRule({
      type: 'ones',
      digits: [0, 1, 2, 3, 4.5],
      removedCard: null,
      description: 'desc',
    })).toBe(false);
  });

  it('ones で digits 長が5でない場合は invalid', () => {
    expect(isValidCardRule({
      type: 'ones',
      digits: [0, 1, 2],
      removedCard: null,
      description: 'desc',
    })).toBe(false);
  });

  it('nuki で digits 長が3でない場合は invalid', () => {
    expect(isValidCardRule({
      type: 'nuki',
      digits: [0, 1, 2, 3, 4],
      removedCard: '57',
      description: 'desc',
    })).toBe(false);
  });

  it('description が文字列でない場合は invalid', () => {
    expect(isValidCardRule({
      type: 'ones',
      digits: [0, 1, 2, 3, 4],
      removedCard: null,
      description: null,
    })).toBe(false);
  });

  it('removedCard が string でも null でもない場合は invalid', () => {
    expect(isValidCardRule({
      type: 'nuki',
      digits: [5, 6, 7],
      removedCard: 57,
      description: 'desc',
    })).toBe(false);
  });
});

describe('loadCardRules / saveCardRules ラウンドトリップ', () => {
  it('保存した値を取り出して同一の配列を得る', () => {
    const date = '2026-06-09';
    const rules = generateCardRules(3);
    saveCardRules(date, rules);
    const loaded = loadCardRules(date);
    expect(loaded).toEqual(rules);
  });

  it('保存していない日付は null', () => {
    expect(loadCardRules('2026-01-01')).toBeNull();
  });

  it('JSON として不正な値は null（パース失敗）', () => {
    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', '{not valid json');
    expect(loadCardRules('2026-06-09')).toBeNull();
  });

  it('配列でない JSON 値は null', () => {
    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', JSON.stringify({ foo: 'bar' }));
    expect(loadCardRules('2026-06-09')).toBeNull();
  });

  it('配列だが要素が不正（[{}]）の場合は null', () => {
    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', JSON.stringify([{}]));
    expect(loadCardRules('2026-06-09')).toBeNull();
  });

  it('配列だが要素が null（[null]）の場合は null', () => {
    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', JSON.stringify([null]));
    expect(loadCardRules('2026-06-09')).toBeNull();
  });

  it('正しい要素と不正な要素が混在する場合は null（部分採用しない）', () => {
    const validRule = {
      type: 'ones',
      digits: [0, 1, 2, 3, 4],
      removedCard: null,
      description: 'desc',
    };
    localStorage.setItem(STORAGE_PREFIX + '2026-06-09', JSON.stringify([validRule, {}]));
    expect(loadCardRules('2026-06-09')).toBeNull();
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
  it('今日のキーは保持される', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    const todayRules = generateCardRules(3);
    saveCardRules('2026-06-09', todayRules);
    cleanupOldCardRules();

    expect(loadCardRules('2026-06-09')).toEqual(todayRules);
    vi.useRealTimers();
  });

  it('今日以外のキーは削除される', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    const yesterdayRules = generateCardRules(3);
    saveCardRules('2026-06-08', yesterdayRules);
    saveCardRules('2025-12-31', yesterdayRules);
    cleanupOldCardRules();

    expect(loadCardRules('2026-06-08')).toBeNull();
    expect(loadCardRules('2025-12-31')).toBeNull();
    vi.useRealTimers();
  });

  it('STORAGE_PREFIX 以外のキーには影響しない', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    localStorage.setItem('auth-token', 'abc123');
    localStorage.setItem('other-app:foo', 'bar');
    cleanupOldCardRules();

    expect(localStorage.getItem('auth-token')).toBe('abc123');
    expect(localStorage.getItem('other-app:foo')).toBe('bar');
    vi.useRealTimers();
  });

  it('今日と過去日のキーが混在しても、今日のみ残る', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 5, 9, 12, 0, 0));

    const rules = generateCardRules(3);
    saveCardRules('2026-06-09', rules);
    saveCardRules('2026-06-08', rules);
    cleanupOldCardRules();

    expect(loadCardRules('2026-06-09')).toEqual(rules);
    expect(loadCardRules('2026-06-08')).toBeNull();
    vi.useRealTimers();
  });
});

describe('reconcileCardRules', () => {
  it('保存長と試合数が一致 → そのまま返却・changed=false', () => {
    const stored = generateCardRules(3);
    const result = reconcileCardRules(stored, 3);
    expect(result.rules).toEqual(stored);
    expect(result.changed).toBe(false);
  });

  it('保存長 < 試合数 → 末尾追加・changed=true', () => {
    const stored = generateCardRules(3);
    const result = reconcileCardRules(stored, 5);
    expect(result.rules).toHaveLength(5);
    expect(result.rules[0]).toEqual(stored[0]);
    expect(result.rules[1]).toEqual(stored[1]);
    expect(result.rules[2]).toEqual(stored[2]);
    expect(result.changed).toBe(true);
  });

  it('保存長 > 試合数 → 先頭totalMatches件のみ返却・changed=false（保存値は呼び出し側で保持）', () => {
    const stored = generateCardRules(5);
    const result = reconcileCardRules(stored, 3);
    expect(result.rules).toHaveLength(3);
    expect(result.rules).toEqual(stored.slice(0, 3));
    expect(result.changed).toBe(false);
  });

  it('保存長 0（空配列）→ totalMatches 件を全部追加・changed=true', () => {
    const result = reconcileCardRules([], 3);
    expect(result.rules).toHaveLength(3);
    expect(result.changed).toBe(true);
  });

  it('追加された末尾ルールは前ルールとサイクル整合（ones→nuki→tens→...）', () => {
    const stored = generateCardRules(2); // ones, nuki
    const result = reconcileCardRules(stored, 4);
    expect(result.rules[2].type).toBe('tens');
    expect(result.rules[3].type).toBe('ones');
  });
});
