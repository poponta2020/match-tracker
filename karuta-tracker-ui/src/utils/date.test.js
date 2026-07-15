import { afterEach, describe, expect, it, vi } from 'vitest';

import { addOneIsoDay, toLocalISODate, todayLocalISODate } from './date';

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

describe('addOneIsoDay（TZ非依存で ISO 日付を1日進める。札分けの「明日」算出に使用）', () => {
  it('通常日を1日進める', () => {
    expect(addOneIsoDay('2026-07-15')).toBe('2026-07-16');
  });
  it('月末をまたいで翌月へ繰り上げる', () => {
    expect(addOneIsoDay('2026-07-31')).toBe('2026-08-01');
  });
  it('年末をまたいで翌年へ繰り上げる', () => {
    expect(addOneIsoDay('2026-12-31')).toBe('2027-01-01');
  });
  it('うるう年 2/28 → 2/29', () => {
    expect(addOneIsoDay('2028-02-28')).toBe('2028-02-29');
  });
  it('平年 2/28 → 3/1', () => {
    expect(addOneIsoDay('2026-02-28')).toBe('2026-03-01');
  });
  it('ゼロ埋め（月・日）を維持する', () => {
    expect(addOneIsoDay('2026-01-09')).toBe('2026-01-10');
  });
});
