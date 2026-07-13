import { describe, it, expect } from 'vitest';
import { computeLockedPairsInput, buildAutoMatchBody } from './pairingLockLogic';

describe('computeLockedPairsInput（再シャッフルで送る lockedPairs 入力）', () => {
  it('手動ロック（locked）の両選手揃った組を {player1Id, player2Id} に射影する', () => {
    const pairings = [
      { player1Id: 1, player2Id: 2, locked: true },
      { player1Id: 3, player2Id: 4, locked: false },
    ];
    expect(computeLockedPairsInput(pairings)).toEqual([{ player1Id: 1, player2Id: 2 }]);
  });

  it('結果入力済み（hasResult）も含める', () => {
    const pairings = [
      { player1Id: 1, player2Id: 2, hasResult: true },
      { player1Id: 5, player2Id: 6, locked: true },
    ];
    expect(computeLockedPairsInput(pairings)).toEqual([
      { player1Id: 1, player2Id: 2 },
      { player1Id: 5, player2Id: 6 },
    ]);
  });

  it('未保存ロック組（id なし）も選手が揃っていれば含める（AC-4）', () => {
    const pairings = [
      { player1Id: 7, player2Id: 8, locked: true }, // id なし＝未保存
    ];
    expect(computeLockedPairsInput(pairings)).toEqual([{ player1Id: 7, player2Id: 8 }]);
  });

  it('片方だけ埋まったロック組は送らない（null player id ガード）', () => {
    const pairings = [
      { player1Id: 1, player2Id: null, locked: true },
      { player1Id: null, player2Id: 2, hasResult: true },
    ];
    expect(computeLockedPairsInput(pairings)).toEqual([]);
  });

  it('非ロック組は送らない', () => {
    const pairings = [
      { player1Id: 1, player2Id: 2 },
      { player1Id: 3, player2Id: 4, locked: false, hasResult: false },
    ];
    expect(computeLockedPairsInput(pairings)).toEqual([]);
  });

  it('空配列・null・undefined でも例外を投げず空配列を返す', () => {
    expect(computeLockedPairsInput([])).toEqual([]);
    expect(computeLockedPairsInput(null)).toEqual([]);
    expect(computeLockedPairsInput(undefined)).toEqual([]);
  });
});

describe('buildAutoMatchBody（null/undefined 境界＝クライアント権威か否か）', () => {
  it('lockedPairs===undefined（対戦編集）→ body に lockedPairs キーを含めない（従来挙動）', () => {
    const body = buildAutoMatchBody('2026-06-28', 1, undefined);
    expect(body).toEqual({ sessionDate: '2026-06-28', matchNumber: 1 });
    expect('lockedPairs' in body).toBe(false);
  });

  it('lockedPairs=[]（再シャッフル・ロック0件）→ 空配列でも lockedPairs を必ず送る', () => {
    const body = buildAutoMatchBody('2026-06-28', 1, []);
    expect(body).toEqual({ sessionDate: '2026-06-28', matchNumber: 1, lockedPairs: [] });
    expect('lockedPairs' in body).toBe(true);
  });

  it('lockedPairs=[{...}]（再シャッフル・ロックあり）→ そのまま body に入れる', () => {
    const pairs = [{ player1Id: 1, player2Id: 2 }];
    const body = buildAutoMatchBody('2026-06-28', 1, pairs);
    expect(body).toEqual({ sessionDate: '2026-06-28', matchNumber: 1, lockedPairs: pairs });
  });
});
