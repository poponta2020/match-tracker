import { describe, it, expect } from 'vitest';
import { formatHeaderDate, resolveHeaderVenue } from './pairingHeader';

// 対戦組み合わせ画面ヘッダの「日付 会場名」表示ロジック（AC-2: 会場名フォールバック分岐）。
// 本番 PairingGenerator が同じ関数を import して headerTitle を組む。

describe('formatHeaderDate（YYYY-MM-DD → M/D・ゼロ埋めなし）', () => {
  it('通常日付をゼロ埋めなしの M/D に整形する', () => {
    expect(formatHeaderDate('2026-07-12')).toBe('7/12');
  });

  it('月・日ともゼロ埋めを外す', () => {
    expect(formatHeaderDate('2026-01-05')).toBe('1/5');
  });

  it('想定外フォーマットは入力をそのまま返す（表示を崩さない）', () => {
    expect(formatHeaderDate('2026/07/12')).toBe('2026/07/12');
  });

  it('空文字・未指定は空文字を返す', () => {
    expect(formatHeaderDate('')).toBe('');
    expect(formatHeaderDate(undefined)).toBe('');
    expect(formatHeaderDate(null)).toBe('');
  });
});

describe('resolveHeaderVenue（会場名フォールバック分岐・AC-2）', () => {
  it('会場名があればそのまま表示対象として返す', () => {
    expect(resolveHeaderVenue('中央体育館')).toBe('中央体育館');
  });

  it('前後の空白はトリムして返す', () => {
    expect(resolveHeaderVenue('  中央体育館  ')).toBe('中央体育館');
  });

  it('null は null（＝日付のみ表示にフォールバック）', () => {
    expect(resolveHeaderVenue(null)).toBeNull();
  });

  it('undefined は null（会場情報が付与されないセッション）', () => {
    expect(resolveHeaderVenue(undefined)).toBeNull();
  });

  it('空文字は null', () => {
    expect(resolveHeaderVenue('')).toBeNull();
  });

  it('空白のみは null（見た目上は会場名なし）', () => {
    expect(resolveHeaderVenue('   ')).toBeNull();
  });

  it('文字列以外（数値等）は null', () => {
    expect(resolveHeaderVenue(123)).toBeNull();
  });
});
