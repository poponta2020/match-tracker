import { describe, it, expect } from 'vitest';
import { resolveAttendanceMode } from './attendanceMode';

const at = (year, month) => new Date(year, month - 1, 15, 12, 0, 0);

describe('resolveAttendanceMode', () => {
  describe('過去月', () => {
    it('表示月が現在年月より前のとき isPastMonth=true, isCurrentMonth=false', () => {
      const result = resolveAttendanceMode(2026, 4, false, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: false, isPastMonth: true });
    });

    it('前年12月から見て当年1月は過去扱い', () => {
      const result = resolveAttendanceMode(2025, 12, false, at(2026, 1));
      expect(result).toEqual({ isCurrentMonth: false, isPastMonth: true });
    });

    it('過去月では hasAnyExecutedLottery=true でも isPastMonth=true', () => {
      const result = resolveAttendanceMode(2026, 4, true, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: false, isPastMonth: true });
    });
  });

  describe('当月（表示月 == 現在年月）', () => {
    it('表示月が現在年月と同じとき isCurrentMonth=true, isPastMonth=false', () => {
      const result = resolveAttendanceMode(2026, 5, false, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: true, isPastMonth: false });
    });

    it('当月では hasAnyExecutedLottery の値に関わらず isCurrentMonth=true', () => {
      const result = resolveAttendanceMode(2026, 5, true, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: true, isPastMonth: false });
    });
  });

  describe('未来月（表示月 > 現在年月）', () => {
    it('hasAnyExecutedLottery=false のとき isCurrentMonth=false（来月扱い）', () => {
      const result = resolveAttendanceMode(2026, 6, false, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: false, isPastMonth: false });
    });

    it('hasAnyExecutedLottery=true のとき isCurrentMonth=true（当月扱いに例外昇格）', () => {
      const result = resolveAttendanceMode(2026, 6, true, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: true, isPastMonth: false });
    });

    it('年をまたいだ未来月（当年12月→翌年1月）も同じルールで判定', () => {
      const noLottery = resolveAttendanceMode(2027, 1, false, at(2026, 12));
      expect(noLottery).toEqual({ isCurrentMonth: false, isPastMonth: false });

      const withLottery = resolveAttendanceMode(2027, 1, true, at(2026, 12));
      expect(withLottery).toEqual({ isCurrentMonth: true, isPastMonth: false });
    });
  });

  describe('hasAnyExecutedLottery の境界値', () => {
    it('undefined のとき未来月は来月扱いになる', () => {
      const result = resolveAttendanceMode(2026, 6, undefined, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: false, isPastMonth: false });
    });

    it('null のとき未来月は来月扱いになる', () => {
      const result = resolveAttendanceMode(2026, 6, null, at(2026, 5));
      expect(result).toEqual({ isCurrentMonth: false, isPastMonth: false });
    });
  });

  describe('now 省略時の挙動', () => {
    it('now を省略すると new Date() が使われる（実時刻に依存しない確認）', () => {
      // 実行時刻に対して必ず過去となる遠い過去年月を指定
      const result = resolveAttendanceMode(2000, 1, false);
      expect(result.isPastMonth).toBe(true);
      expect(result.isCurrentMonth).toBe(false);
    });
  });
});
