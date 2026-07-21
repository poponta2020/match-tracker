import { describe, it, expect } from 'vitest';
import { slotWidthOf, nearestIndex, capsuleCenterOf, clampCenter } from './bottomNav';

describe('bottomNav pure logic', () => {
  describe('slotWidthOf', () => {
    it('divides track width by the item count', () => {
      expect(slotWidthOf(375, 5)).toBe(75);
    });
    it('returns 0 for non-positive track width', () => {
      expect(slotWidthOf(0, 5)).toBe(0);
      expect(slotWidthOf(-10, 5)).toBe(0);
    });
    it('returns 0 for non-positive count', () => {
      expect(slotWidthOf(375, 0)).toBe(0);
    });
  });

  describe('nearestIndex', () => {
    const slot = 75; // 5スロット×75px（trackWidth 375）
    const count = 5;
    it('maps a slot-center point to that slot index', () => {
      // 各スロット中心 = slot*(i+0.5)
      expect(nearestIndex(37.5, slot, count)).toBe(0);
      expect(nearestIndex(112.5, slot, count)).toBe(1);
      expect(nearestIndex(187.5, slot, count)).toBe(2);
      expect(nearestIndex(262.5, slot, count)).toBe(3);
      expect(nearestIndex(337.5, slot, count)).toBe(4);
    });
    it('rounds to the nearest slot at boundaries', () => {
      // スロット0/1境界(75px)直前は0、直後は1
      expect(nearestIndex(74, slot, count)).toBe(0);
      expect(nearestIndex(76, slot, count)).toBe(1);
    });
    it('clamps below 0 and above the last index', () => {
      expect(nearestIndex(-100, slot, count)).toBe(0);
      expect(nearestIndex(100000, slot, count)).toBe(count - 1);
    });
    it('returns 0 when slot width is non-positive (guards divide-by-zero)', () => {
      expect(nearestIndex(200, 0, count)).toBe(0);
      expect(nearestIndex(200, -5, count)).toBe(0);
    });
  });

  describe('capsuleCenterOf', () => {
    const slot = 75;
    it('returns the slot center for a valid index', () => {
      expect(capsuleCenterOf(0, slot)).toBe(37.5);
      expect(capsuleCenterOf(4, slot)).toBe(337.5);
    });
    it('returns null when no item is active (index < 0)', () => {
      expect(capsuleCenterOf(-1, slot)).toBeNull();
    });
    it('returns null when slot width is not measured yet', () => {
      expect(capsuleCenterOf(2, 0)).toBeNull();
    });
  });

  describe('clampCenter', () => {
    const trackWidth = 375;
    const capsuleWidth = 62;
    it('passes through a point that fits within the track', () => {
      expect(clampCenter(200, trackWidth, capsuleWidth)).toBe(200);
    });
    it('clamps to half the capsule width at the left edge', () => {
      expect(clampCenter(0, trackWidth, capsuleWidth)).toBe(31);
      expect(clampCenter(-50, trackWidth, capsuleWidth)).toBe(31);
    });
    it('clamps to trackWidth minus half the capsule width at the right edge', () => {
      expect(clampCenter(375, trackWidth, capsuleWidth)).toBe(344);
      expect(clampCenter(9999, trackWidth, capsuleWidth)).toBe(344);
    });
  });
});
