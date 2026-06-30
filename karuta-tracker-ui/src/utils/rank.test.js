import { describe, expect, it } from 'vitest';

import { kyuRankShortLabel } from './rank';

describe('kyuRankShortLabel', () => {
  it('級位を頭文字の短縮ラベルに変換する', () => {
    expect(kyuRankShortLabel('A級')).toBe('(A)');
    expect(kyuRankShortLabel('B級')).toBe('(B)');
    expect(kyuRankShortLabel('C級')).toBe('(C)');
    expect(kyuRankShortLabel('D級')).toBe('(D)');
    expect(kyuRankShortLabel('E級')).toBe('(E)');
  });

  it('未設定（null/undefined/空文字）は空文字を返す', () => {
    expect(kyuRankShortLabel(null)).toBe('');
    expect(kyuRankShortLabel(undefined)).toBe('');
    expect(kyuRankShortLabel('')).toBe('');
  });

  it('文字列以外は空文字を返す', () => {
    expect(kyuRankShortLabel(123)).toBe('');
    expect(kyuRankShortLabel({})).toBe('');
  });

  it('前後の空白を無視して頭文字を取る', () => {
    expect(kyuRankShortLabel('  A級  ')).toBe('(A)');
  });
});
