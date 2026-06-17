import { afterEach, describe, expect, it, vi } from 'vitest';

import { toLocalISODate, todayLocalISODate } from './date';

describe('toLocalISODate', () => {
  it('ローカルの年月日を YYYY-MM-DD に組み立てる（2桁ゼロ埋め）', () => {
    // 2026-01-05（ローカル時刻のコンストラクタ）
    expect(toLocalISODate(new Date(2026, 0, 5))).toBe('2026-01-05');
    // 2桁月・日
    expect(toLocalISODate(new Date(2026, 11, 31))).toBe('2026-12-31');
  });

  it('UTC 早朝でもローカル日付を保つ（toISOString の前日ズレを起こさない）', () => {
    // JST(UTC+9) 2026-06-17 02:30 は UTC では 2026-06-16 17:30。
    // toISOString().split("T")[0] だと "2026-06-16" になってしまうケース。
    // ローカルが JST のとき toLocalISODate は "2026-06-17" を返すべき。
    // タイムゾーン非依存に検証するため、ローカル時刻指定の Date を使う。
    const jstEarlyMorning = new Date(2026, 5, 17, 2, 30, 0); // ローカル 2026-06-17 02:30
    expect(toLocalISODate(jstEarlyMorning)).toBe('2026-06-17');
    // 同じ瞬間を toISOString ベースにするとローカルが UTC+ のとき前日になりうることを対比確認。
    // （ここではロジックの正しさの確認が目的なので toLocalISODate のみアサート）
  });
});

describe('todayLocalISODate', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('現在時刻のローカル日付を返す', () => {
    vi.useFakeTimers();
    // 固定時刻: ローカル 2026-06-17 03:00
    vi.setSystemTime(new Date(2026, 5, 17, 3, 0, 0));
    expect(todayLocalISODate()).toBe('2026-06-17');
  });
});
