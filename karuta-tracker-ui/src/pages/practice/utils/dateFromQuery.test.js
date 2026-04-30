import { describe, it, expect } from 'vitest';
import { getInitialDateFromQuery } from './dateFromQuery';

const makeParams = (init) => new URLSearchParams(init);

describe('getInitialDateFromQuery', () => {
  it('クエリなしのとき現在月にフォールバックする', () => {
    const before = new Date();
    const result = getInitialDateFromQuery(makeParams(''));
    const after = new Date();
    expect(result.getFullYear()).toBe(before.getFullYear());
    expect(result.getMonth()).toBe(before.getMonth());
    expect(result.getFullYear()).toBe(after.getFullYear());
  });

  it('year のみ指定されたとき現在月にフォールバックする', () => {
    const result = getInitialDateFromQuery(makeParams('year=2026'));
    const now = new Date();
    expect(result.getFullYear()).toBe(now.getFullYear());
    expect(result.getMonth()).toBe(now.getMonth());
  });

  it('month のみ指定されたとき現在月にフォールバックする（旧実装の 1900 年バグ回帰防止）', () => {
    const result = getInitialDateFromQuery(makeParams('month=5'));
    const now = new Date();
    expect(result.getFullYear()).toBe(now.getFullYear());
    expect(result.getMonth()).toBe(now.getMonth());
  });

  it('year=0&month=5 のとき現在月にフォールバックする', () => {
    const result = getInitialDateFromQuery(makeParams('year=0&month=5'));
    const now = new Date();
    expect(result.getFullYear()).toBe(now.getFullYear());
    expect(result.getMonth()).toBe(now.getMonth());
  });

  it('month が 0 または 13 のとき現在月にフォールバックする', () => {
    const now = new Date();
    [
      'year=2026&month=0',
      'year=2026&month=13',
      'year=2026&month=-1',
    ].forEach((qs) => {
      const result = getInitialDateFromQuery(makeParams(qs));
      expect(result.getFullYear()).toBe(now.getFullYear());
      expect(result.getMonth()).toBe(now.getMonth());
    });
  });

  it('year や month が数値でないとき現在月にフォールバックする', () => {
    const now = new Date();
    [
      'year=abc&month=5',
      'year=2026&month=abc',
      'year=2026.5&month=5',
    ].forEach((qs) => {
      const result = getInitialDateFromQuery(makeParams(qs));
      expect(result.getFullYear()).toBe(now.getFullYear());
      expect(result.getMonth()).toBe(now.getMonth());
    });
  });

  it('year=2026&month=5 のとき 2026 年 5 月 1 日を返す', () => {
    const result = getInitialDateFromQuery(makeParams('year=2026&month=5'));
    expect(result.getFullYear()).toBe(2026);
    expect(result.getMonth()).toBe(4); // 0-indexed
    expect(result.getDate()).toBe(1);
  });

  it('year=2026&month=12 のとき 2026 年 12 月 1 日を返す（境界値）', () => {
    const result = getInitialDateFromQuery(makeParams('year=2026&month=12'));
    expect(result.getFullYear()).toBe(2026);
    expect(result.getMonth()).toBe(11);
    expect(result.getDate()).toBe(1);
  });

  it('year=2026&month=1 のとき 2026 年 1 月 1 日を返す（境界値）', () => {
    const result = getInitialDateFromQuery(makeParams('year=2026&month=1'));
    expect(result.getFullYear()).toBe(2026);
    expect(result.getMonth()).toBe(0);
    expect(result.getDate()).toBe(1);
  });

  it('year=1999 のとき現在月にフォールバックする（下限境界）', () => {
    const now = new Date();
    const result = getInitialDateFromQuery(makeParams('year=1999&month=5'));
    expect(result.getFullYear()).toBe(now.getFullYear());
    expect(result.getMonth()).toBe(now.getMonth());
  });

  it('year=2000 のとき 2000 年 5 月 1 日を返す（下限境界 OK）', () => {
    const result = getInitialDateFromQuery(makeParams('year=2000&month=5'));
    expect(result.getFullYear()).toBe(2000);
    expect(result.getMonth()).toBe(4);
  });
});
