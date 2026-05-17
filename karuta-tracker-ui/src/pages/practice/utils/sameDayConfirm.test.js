import { describe, it, expect } from 'vitest';
import { needsSameDayConfirm } from './sameDayConfirm';

const todayStr = '2026-05-17';
const orgMap = {
  1: { id: 1, deadlineType: 'SAME_DAY' },
  2: { id: 2, deadlineType: 'MONTHLY' },
};
const todaySession = { id: 100, organizationId: 1, sessionDate: todayStr };
const laterSession = { id: 200, organizationId: 1, sessionDate: '2026-05-19' };
const monthlyTodaySession = { id: 300, organizationId: 2, sessionDate: todayStr };

const at = (hours) => new Date(2026, 4, 17, hours, 0, 0); // 5/17 ローカル

describe('needsSameDayConfirm', () => {
  it('12時より前は当日セッションを変更しても false', () => {
    const result = needsSameDayConfirm({
      sessions: [todaySession],
      orgMap,
      participations: { 100: [1, 2] },
      initialParticipations: { 100: [1] },
      now: at(11),
    });
    expect(result).toBe(false);
  });

  it('12時以降で当日SAME_DAYセッションに変更がある場合 true', () => {
    const result = needsSameDayConfirm({
      sessions: [todaySession],
      orgMap,
      participations: { 100: [1, 2] },
      initialParticipations: { 100: [1] },
      now: at(13),
    });
    expect(result).toBe(true);
  });

  it('12時以降でも当日SAME_DAYセッションが未変更なら false（別日だけ変更したケース）', () => {
    const result = needsSameDayConfirm({
      sessions: [todaySession, laterSession],
      orgMap,
      participations: { 100: [1], 200: [1, 2] },
      initialParticipations: { 100: [1], 200: [1] },
      now: at(13),
    });
    expect(result).toBe(false);
  });

  it('当日でもMONTHLYタイプなら false', () => {
    const result = needsSameDayConfirm({
      sessions: [monthlyTodaySession],
      orgMap,
      participations: { 300: [1, 2] },
      initialParticipations: { 300: [1] },
      now: at(13),
    });
    expect(result).toBe(false);
  });

  it('当日SAME_DAYセッションが画面になければ false', () => {
    const result = needsSameDayConfirm({
      sessions: [laterSession],
      orgMap,
      participations: { 200: [1, 2] },
      initialParticipations: { 200: [1] },
      now: at(13),
    });
    expect(result).toBe(false);
  });

  it('参加試合の順序が違うだけなら未変更扱い（false）', () => {
    const result = needsSameDayConfirm({
      sessions: [todaySession],
      orgMap,
      participations: { 100: [2, 1, 3] },
      initialParticipations: { 100: [1, 2, 3] },
      now: at(13),
    });
    expect(result).toBe(false);
  });

  it('当日セッションでキャンセル（試合を外す）した場合も true', () => {
    const result = needsSameDayConfirm({
      sessions: [todaySession],
      orgMap,
      participations: { 100: [1] },
      initialParticipations: { 100: [1, 2] },
      now: at(13),
    });
    expect(result).toBe(true);
  });
});
