import { describe, it, expect } from 'vitest';
import { isHorizontalSwipe, resolveSwipe, clampOffset } from './swipeGesture';

describe('isHorizontalSwipe', () => {
  it('横移動が閾値を超え縦より優勢なら true', () => {
    expect(isHorizontalSwipe(20, 5)).toBe(true);
    expect(isHorizontalSwipe(-20, 5)).toBe(true); // 左スワイプ
  });

  it('横移動が閾値以下なら false（タップ・微動）', () => {
    expect(isHorizontalSwipe(8, 0)).toBe(false);
    expect(isHorizontalSwipe(10, 0)).toBe(false); // ちょうど閾値は超えていない
  });

  it('縦移動が優勢なら false（縦スクロール）', () => {
    expect(isHorizontalSwipe(20, 30)).toBe(false);
    expect(isHorizontalSwipe(20, 20)).toBe(false); // 同値は横優勢でない
  });

  it('activationPx を指定できる', () => {
    expect(isHorizontalSwipe(15, 0, 20)).toBe(false);
    expect(isHorizontalSwipe(25, 0, 20)).toBe(true);
  });
});

describe('resolveSwipe', () => {
  const W = 300; // コンテナ幅。閾値 = 300 * 0.25 = 75px

  it('左スワイプ（dx<0）で閾値を超えたら next', () => {
    expect(resolveSwipe({ dx: -80, containerWidth: W })).toBe('next');
  });

  it('右スワイプ（dx>0）で閾値を超えたら prev', () => {
    expect(resolveSwipe({ dx: 80, containerWidth: W })).toBe('prev');
  });

  it('閾値未満なら null（元の試合へ戻す）', () => {
    expect(resolveSwipe({ dx: -50, containerWidth: W })).toBeNull();
    expect(resolveSwipe({ dx: 50, containerWidth: W })).toBeNull();
  });

  it('ちょうど閾値（25%）なら確定する', () => {
    expect(resolveSwipe({ dx: -75, containerWidth: W })).toBe('next');
  });

  it('素早いフリックなら移動量が小さくても確定する', () => {
    expect(resolveSwipe({ dx: -20, containerWidth: W, velocity: -0.8 })).toBe('next');
    expect(resolveSwipe({ dx: 20, containerWidth: W, velocity: 0.8 })).toBe('prev');
  });

  it('遅い小移動（フリックでない）なら null', () => {
    expect(resolveSwipe({ dx: -20, containerWidth: W, velocity: -0.1 })).toBeNull();
  });

  it('dx が 0 なら null', () => {
    expect(resolveSwipe({ dx: 0, containerWidth: W, velocity: 1 })).toBeNull();
  });

  it('commitRatio を指定できる', () => {
    expect(resolveSwipe({ dx: -40, containerWidth: W, commitRatio: 0.1 })).toBe('next'); // 閾値30px
    expect(resolveSwipe({ dx: -40, containerWidth: W, commitRatio: 0.5 })).toBeNull();   // 閾値150px
  });

  it('コンテナ幅が0でもフリックなら確定する（距離判定は無効）', () => {
    expect(resolveSwipe({ dx: -10, containerWidth: 0 })).toBeNull();
    expect(resolveSwipe({ dx: -10, containerWidth: 0, velocity: -1 })).toBe('next');
  });
});

describe('clampOffset', () => {
  it('中間の試合では dx をそのまま返す', () => {
    expect(clampOffset(40, { atFirst: false, atLast: false })).toBe(40);
    expect(clampOffset(-40, { atFirst: false, atLast: false })).toBe(-40);
  });

  it('最初の試合では「前」方向（dx>0）を抑制する', () => {
    expect(clampOffset(40, { atFirst: true, atLast: false })).toBe(0);
  });

  it('最初の試合でも「次」方向（dx<0）は許可する', () => {
    expect(clampOffset(-40, { atFirst: true, atLast: false })).toBe(-40);
  });

  it('最後の試合では「次」方向（dx<0）を抑制する', () => {
    expect(clampOffset(-40, { atFirst: false, atLast: true })).toBe(0);
  });

  it('最後の試合でも「前」方向（dx>0）は許可する', () => {
    expect(clampOffset(40, { atFirst: false, atLast: true })).toBe(40);
  });

  it('引数省略時は dx をそのまま返す', () => {
    expect(clampOffset(25)).toBe(25);
  });
});
