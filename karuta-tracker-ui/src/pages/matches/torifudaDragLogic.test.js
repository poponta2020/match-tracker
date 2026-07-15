import { describe, it, expect } from 'vitest';
import {
  computeDrop,
  encodeCellId,
  parseDroppableId,
  createTrailingClickGuard,
  POOL_ID,
} from './torifudaDragLogic';

const cell = { takenBy: 'SELF', field: 'ENEMY', side: 'RIGHT', tier: 'BOTTOM' };
const cellId = encodeCellId('ENEMY', 'RIGHT', 'BOTTOM', 'SELF');

describe('torifudaDragLogic - droppable id', () => {
  it('encode/parse ラウンドトリップ', () => {
    expect(cellId).toBe('cell:ENEMY:RIGHT:BOTTOM:SELF');
    expect(parseDroppableId(cellId)).toEqual({
      type: 'cell', field: 'ENEMY', side: 'RIGHT', tier: 'BOTTOM', takenBy: 'SELF',
    });
    expect(parseDroppableId(POOL_ID)).toEqual({ type: 'pool' });
  });

  it('不正な id は null', () => {
    expect(parseDroppableId(null)).toBeNull();
    expect(parseDroppableId('garbage')).toBeNull();
    expect(parseDroppableId('cell:ENEMY:RIGHT')).toBeNull(); // 欠損
  });
});

describe('torifudaDragLogic - computeDrop', () => {
  it('C-1: 不明→マス（配置）', () => {
    const next = computeDrop({ activeCardNo: 7, overId: cellId, placements: {} });
    expect(next).toEqual({ 7: cell });
  });

  it('C-2: マス→別マス（移動）で takenBy/field/side/tier が更新される', () => {
    const from = { 7: { takenBy: 'OPPONENT', field: 'OWN', side: 'LEFT', tier: 'TOP' } };
    const next = computeDrop({ activeCardNo: 7, overId: cellId, placements: from });
    expect(next).toEqual({ 7: cell });
  });

  it('C-3: マス→不明（配置解除）。他の札は残る', () => {
    const other = { takenBy: 'OPPONENT', field: 'OWN', side: 'LEFT', tier: 'TOP' };
    const from = { 7: cell, 9: other };
    const next = computeDrop({ activeCardNo: 7, overId: POOL_ID, placements: from });
    expect(next).toEqual({ 9: other });
    expect(next[7]).toBeUndefined();
  });

  it('不正/枠外ドロップは null（配置に影響しない）', () => {
    expect(computeDrop({ activeCardNo: 7, overId: null, placements: {} })).toBeNull();
    expect(computeDrop({ activeCardNo: 7, overId: 'nope', placements: {} })).toBeNull();
    expect(computeDrop({ activeCardNo: null, overId: cellId, placements: {} })).toBeNull();
  });

  it('同一マスへのドロップ・未配置札を不明へ、は変化なし（null）', () => {
    expect(computeDrop({ activeCardNo: 7, overId: cellId, placements: { 7: cell } })).toBeNull();
    expect(computeDrop({ activeCardNo: 7, overId: POOL_ID, placements: {} })).toBeNull();
  });
});

describe('torifudaDragLogic - trailing-click ガード（drag-settle）', () => {
  it('ドラッグ直後の1クリックだけを食い、その後のクリックは通す', () => {
    const g = createTrailingClickGuard();
    expect(g.consumeClick()).toBe(false); // ドラッグ前の通常タップは通す
    g.onDragStart();
    g.onDragEnd();
    expect(g.consumeClick()).toBe(true);  // 実ドラッグ直後の合成 click は食う（unplace 打ち消し防止）
    expect(g.consumeClick()).toBe(false); // 2回目以降は通常タップとして通す
  });

  it('backstop: 合成 click が来なくても時間経過でガードが解除される', () => {
    let fired = null;
    const setTimer = (fn) => { fired = fn; return 1; };
    const clearTimer = () => {};
    const g = createTrailingClickGuard(setTimer, clearTimer, 300);
    g.onDragStart();
    g.onDragEnd();
    fired(); // backstop タイマー発火をシミュレート
    expect(g.consumeClick()).toBe(false); // 既に解除済み → 次の正当なタップを食わない
  });
});
