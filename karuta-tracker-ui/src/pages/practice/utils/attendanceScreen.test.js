import { describe, it, expect } from 'vitest';
import {
  buildMonthParticipationsPayload,
  resolveAttendanceSections,
  resolveAvailableMatchNumbers,
  MAX_MATCHES_PER_SESSION,
} from './attendanceScreen';

describe('buildMonthParticipationsPayload', () => {
  describe('他日保持（クロスデイ軸）— 全置換対策の肝', () => {
    it('他セッションの参加は payload にそのまま残る', () => {
      const monthMap = { 10: [1, 2], 20: [3], 30: [1] };
      const payload = buildMonthParticipationsPayload(monthMap, 20, [3]);
      expect(payload).toEqual([
        { sessionId: 10, matchNumber: 1 },
        { sessionId: 10, matchNumber: 2 },
        { sessionId: 20, matchNumber: 3 },
        { sessionId: 30, matchNumber: 1 },
      ]);
    });

    it('対象セッションの希望を変えても他日は不変', () => {
      const monthMap = { 10: [1, 2], 20: [3] };
      const payload = buildMonthParticipationsPayload(monthMap, 20, [1, 2, 3]);
      // 他日 (10) は [1,2] のまま、対象 (20) だけ [1,2,3] に差し替え
      expect(payload).toEqual([
        { sessionId: 10, matchNumber: 1 },
        { sessionId: 10, matchNumber: 2 },
        { sessionId: 20, matchNumber: 1 },
        { sessionId: 20, matchNumber: 2 },
        { sessionId: 20, matchNumber: 3 },
      ]);
    });

    it('文字列キーの月マップでも数値 sessionId として扱う', () => {
      const monthMap = { '10': [1], '20': [2] };
      const payload = buildMonthParticipationsPayload(monthMap, '20', [2, 3]);
      expect(payload).toEqual([
        { sessionId: 10, matchNumber: 1 },
        { sessionId: 20, matchNumber: 2 },
        { sessionId: 20, matchNumber: 3 },
      ]);
    });
  });

  describe('対象セッション内保持（イントラ軸・破綻ケース）', () => {
    it('当月扱い・第1登録済み・第2を追加 → 対象は [1,2]（第1が落ちない）', () => {
      // 呼び出し側で seed([1]) にトグル差分(+2) を適用した希望集合 [1,2] を渡す想定
      const monthMap = { 20: [1] };
      const payload = buildMonthParticipationsPayload(monthMap, 20, [1, 2]);
      expect(payload).toEqual([
        { sessionId: 20, matchNumber: 1 },
        { sessionId: 20, matchNumber: 2 },
      ]);
    });

    it('来月扱い・第1登録済み・第2追加 → [1,2]、第1をuncheck → [2]', () => {
      const monthMap = { 20: [1] };
      expect(buildMonthParticipationsPayload(monthMap, 20, [1, 2])).toEqual([
        { sessionId: 20, matchNumber: 1 },
        { sessionId: 20, matchNumber: 2 },
      ]);
      expect(buildMonthParticipationsPayload(monthMap, 20, [2])).toEqual([
        { sessionId: 20, matchNumber: 2 },
      ]);
    });
  });

  describe('境界ケース', () => {
    it('希望が空なら対象セッションは payload から消える（全解除）が他日は残る', () => {
      const monthMap = { 10: [1], 20: [2, 3] };
      const payload = buildMonthParticipationsPayload(monthMap, 20, []);
      expect(payload).toEqual([{ sessionId: 10, matchNumber: 1 }]);
    });

    it('対象が月マップに未登録でも希望を新規追加できる', () => {
      const monthMap = { 10: [1] };
      const payload = buildMonthParticipationsPayload(monthMap, 20, [1, 2]);
      expect(payload).toEqual([
        { sessionId: 10, matchNumber: 1 },
        { sessionId: 20, matchNumber: 1 },
        { sessionId: 20, matchNumber: 2 },
      ]);
    });

    it('空の月マップ・空の希望なら空配列', () => {
      expect(buildMonthParticipationsPayload({}, 20, [])).toEqual([]);
      expect(buildMonthParticipationsPayload(null, 20, null)).toEqual([]);
    });

    it('希望集合の重複は排除される', () => {
      const payload = buildMonthParticipationsPayload({}, 20, [1, 1, 2]);
      expect(payload).toEqual([
        { sessionId: 20, matchNumber: 1 },
        { sessionId: 20, matchNumber: 2 },
      ]);
    });
  });
});

describe('resolveAvailableMatchNumbers', () => {
  it('totalMatches に応じて 1..N を返す', () => {
    expect(resolveAvailableMatchNumbers({ totalMatches: 3 })).toEqual([1, 2, 3]);
  });
  it('totalMatches 欠落時は 1..7 にフォールバック', () => {
    expect(resolveAvailableMatchNumbers({})).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(resolveAvailableMatchNumbers(null)).toHaveLength(MAX_MATCHES_PER_SESSION);
  });
  it('totalMatches が 7 超でも 7 で頭打ち', () => {
    expect(resolveAvailableMatchNumbers({ totalMatches: 12 })).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });
});

describe('resolveAttendanceSections', () => {
  const session = (overrides = {}) => ({ totalMatches: 3, ...overrides });

  describe('当月扱い（抽選前）', () => {
    it('一部参加: 未参加=参加側 / PENDING=キャンセル側（理由付き）', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [1],
        statusesForSession: [{ matchNumber: 1, status: 'PENDING' }],
      });
      expect(result.showRegisterSection).toBe(true);
      expect(result.registerMatches).toEqual([2, 3]); // 未参加
      expect(result.showCancelSection).toBe(true);
      expect(result.cancelMatches).toEqual([1]); // PENDING
      expect(result.readonlyStatusMatches).toEqual([]);
    });

    it('全試合参加中: 参加セクション非表示・キャンセルのみ', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [1, 2, 3],
        statusesForSession: [
          { matchNumber: 1, status: 'WON' },
          { matchNumber: 2, status: 'WON' },
          { matchNumber: 3, status: 'PENDING' },
        ],
      });
      expect(result.showRegisterSection).toBe(false);
      expect(result.registerMatches).toEqual([]);
      expect(result.showCancelSection).toBe(true);
      expect(result.cancelMatches).toEqual([1, 2, 3]);
    });

    it('全試合未参加: キャンセルセクション非表示・参加のみ', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [],
        statusesForSession: [],
      });
      expect(result.showRegisterSection).toBe(true);
      expect(result.registerMatches).toEqual([1, 2, 3]);
      expect(result.showCancelSection).toBe(false);
      expect(result.cancelMatches).toEqual([]);
    });

    it('WAITLISTED/OFFERED は readonly（参加にもキャンセルにも出さない・3分割）', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [1, 2],
        statusesForSession: [
          { matchNumber: 1, status: 'WAITLISTED' },
          { matchNumber: 2, status: 'OFFERED' },
        ],
      });
      expect(result.registerMatches).toEqual([3]); // 未参加のみ
      expect(result.cancelMatches).toEqual([]); // WON/PENDING なし
      expect(result.readonlyStatusMatches).toEqual([1, 2]); // 操作対象外
    });

    it('満員（capacity 到達）でもチェック可: register に残す（無効化しない）', () => {
      // 満員判定は行の情報表示のみ。resolveAttendanceSections は満員でも register から除外しない
      const result = resolveAttendanceSections({
        session: session({ totalMatches: 3, matchCapacityStatuses: ['FULL', 'FULL', 'FULL'] }),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [],
        statusesForSession: [],
      });
      expect(result.registerMatches).toEqual([1, 2, 3]);
    });

    it('伝助削除承認済みの試合番号は参加にもキャンセルにも出さない', () => {
      const result = resolveAttendanceSections({
        session: session({ totalMatches: 3, densukeDeletionCandidateMatchNumbers: [2] }),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [],
        statusesForSession: [],
      });
      expect(result.registerMatches).toEqual([1, 3]); // 2 は除外
      expect(result.cancelMatches).toEqual([]);
      expect(result.readonlyStatusMatches).toEqual([]);
    });
  });

  describe('来月扱い（未来月・抽選前）', () => {
    it('全試合をトグル表示・キャンセルセクションは常に非表示', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: false,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [1], // 登録済み（呼び出し側で pre-check）
        statusesForSession: [{ matchNumber: 1, status: 'PENDING' }],
      });
      expect(result.showRegisterSection).toBe(true);
      expect(result.registerMatches).toEqual([1, 2, 3]); // 登録済みも含め全試合
      expect(result.showCancelSection).toBe(false);
      expect(result.cancelMatches).toEqual([]);
      expect(result.readonlyStatusMatches).toEqual([]);
    });

    it('伝助削除は来月扱いでも除外', () => {
      const result = resolveAttendanceSections({
        session: session({ totalMatches: 3, densukeDeletionCandidateMatchNumbers: [3] }),
        isCurrentMonthMode: false,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [],
        statusesForSession: [],
      });
      expect(result.registerMatches).toEqual([1, 2]);
    });

    it('beforeDeadline 省略時は締切前扱い（全トグル・既存挙動を維持）', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: false,
        lotteryExecutedForSession: false,
        monthParticipationsForSession: [1],
        statusesForSession: [{ matchNumber: 1, status: 'PENDING' }],
      });
      expect(result.registerMatches).toEqual([1, 2, 3]);
      expect(result.showCancelSection).toBe(false);
    });
  });

  describe('来月扱い×締切後（beforeDeadline=false）— 全置換サイレントno-op回帰の防止', () => {
    it('締切後は既存参加をトグルに出さず理由付きキャンセルへ回す（全トグルにしない）', () => {
      // registerAfterDeadline は payload 省略分を削除しない。既存を uncheck 全置換保存しても
      // 実データが残るため、締切後の来月扱いは当月扱いパーティションに倒す。
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: false,
        lotteryExecutedForSession: false,
        beforeDeadline: false,
        monthParticipationsForSession: [1],
        statusesForSession: [{ matchNumber: 1, status: 'PENDING', participantId: 555 }],
      });
      // 既存の第1はキャンセル側（理由付き）に出す。参加トグルには出さない
      expect(result.cancelMatches).toEqual([1]);
      expect(result.registerMatches).not.toContain(1);
      expect(result.showCancelSection).toBe(true);
    });

    it('締切後でも未参加の追加登録は可能（ロックは既存参加の解除のみ）', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: false,
        lotteryExecutedForSession: false,
        beforeDeadline: false,
        monthParticipationsForSession: [1],
        statusesForSession: [{ matchNumber: 1, status: 'PENDING', participantId: 555 }],
      });
      // 未参加の第2・第3は参加側に残る（registerAfterDeadline が追加を処理する）
      expect(result.registerMatches).toEqual([2, 3]);
      expect(result.showRegisterSection).toBe(true);
    });
  });

  describe('抽選確定済みセッション', () => {
    it('参加トグル不可（register 非表示）・WON/PENDING はキャンセル可・他は readonly', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: true,
        monthParticipationsForSession: [1, 2, 3],
        statusesForSession: [
          { matchNumber: 1, status: 'WON' },
          { matchNumber: 2, status: 'WAITLISTED' },
          { matchNumber: 3, status: 'PENDING' },
        ],
      });
      expect(result.showRegisterSection).toBe(false);
      expect(result.registerMatches).toEqual([]);
      expect(result.cancelMatches).toEqual([1, 3]); // WON/PENDING
      expect(result.readonlyStatusMatches).toEqual([2]); // WAITLISTED
    });

    it('WON が無ければキャンセルセクションも非表示', () => {
      const result = resolveAttendanceSections({
        session: session(),
        isCurrentMonthMode: true,
        lotteryExecutedForSession: true,
        monthParticipationsForSession: [1],
        statusesForSession: [{ matchNumber: 1, status: 'WAITLISTED' }],
      });
      expect(result.showCancelSection).toBe(false);
      expect(result.cancelMatches).toEqual([]);
      expect(result.readonlyStatusMatches).toEqual([1]);
    });
  });
});
