import { describe, it, expect } from 'vitest';
import {
  GRACE_MINUTES,
  toMinutes,
  isToday,
  timeBasedDefaultMatchNumber,
  getCompletedMatchNumbers,
  defaultForResultsView,
  defaultForBulkInput,
} from './defaultMatchNumber';

// 要件例: 1試合目 17:05–18:15 / 2試合目 18:15–19:30
const EXAMPLE_SCHEDULES = [
  { matchNumber: 1, startTime: '17:05:00', endTime: '18:15:00' },
  { matchNumber: 2, startTime: '18:15:00', endTime: '19:30:00' },
];

// 当日（2026-06-25）の指定時刻のローカル Date を作る
const at = (h, m) => new Date(2026, 5, 25, h, m, 0);
const TODAY = '2026-06-25';
const PAST_DAY = '2026-06-24';

describe('GRACE_MINUTES', () => {
  it('は 15 分固定', () => {
    expect(GRACE_MINUTES).toBe(15);
  });
});

describe('toMinutes', () => {
  it('"HH:mm:ss" を分に変換する', () => {
    expect(toMinutes('18:15:00')).toBe(18 * 60 + 15);
  });
  it('"HH:mm" を分に変換する', () => {
    expect(toMinutes('09:05')).toBe(9 * 60 + 5);
  });
  it('不正値・未定義は null', () => {
    expect(toMinutes(null)).toBeNull();
    expect(toMinutes(undefined)).toBeNull();
    expect(toMinutes('')).toBeNull();
    expect(toMinutes('abc')).toBeNull();
  });
  it('形式不正・範囲外は null（末尾不正・時分範囲外・桁不正）', () => {
    expect(toMinutes('18:15abc')).toBeNull();
    expect(toMinutes('99:99')).toBeNull();
    expect(toMinutes('18x:15')).toBeNull();
    expect(toMinutes('24:00')).toBeNull();
    expect(toMinutes('12:60')).toBeNull();
  });
});

describe('isToday', () => {
  it('端末ローカル日付と一致すれば true', () => {
    expect(isToday(TODAY, at(12, 0))).toBe(true);
  });
  it('過去日は false', () => {
    expect(isToday(PAST_DAY, at(12, 0))).toBe(false);
  });
  it('sessionDate 未指定は false', () => {
    expect(isToday(null, at(12, 0))).toBe(false);
    expect(isToday(undefined, at(12, 0))).toBe(false);
  });
});

describe('timeBasedDefaultMatchNumber（要件例の境界）', () => {
  it('開始前 16:00 → 1試合目', () => {
    expect(timeBasedDefaultMatchNumber(EXAMPLE_SCHEDULES, at(16, 0))).toBe(1);
  });
  it('18:00（1試合目終了+15分=18:30未満）→ 1試合目', () => {
    expect(timeBasedDefaultMatchNumber(EXAMPLE_SCHEDULES, at(18, 0))).toBe(1);
  });
  it('18:30（ちょうど境界）→ 2試合目', () => {
    expect(timeBasedDefaultMatchNumber(EXAMPLE_SCHEDULES, at(18, 30))).toBe(2);
  });
  it('19:00 → 2試合目', () => {
    expect(timeBasedDefaultMatchNumber(EXAMPLE_SCHEDULES, at(19, 0))).toBe(2);
  });
  it('19:45（最終試合終了+15分=19:45）→ 1試合目に戻す', () => {
    expect(timeBasedDefaultMatchNumber(EXAMPLE_SCHEDULES, at(19, 45))).toBe(1);
  });
  it('21:00（最終試合超過）→ 1試合目に戻す', () => {
    expect(timeBasedDefaultMatchNumber(EXAMPLE_SCHEDULES, at(21, 0))).toBe(1);
  });

  it('一部の試合番号のみ時刻定義あり: 定義のない番号はスキップ', () => {
    // 1試合目は endTime なし、2試合目のみ定義
    const partial = [
      { matchNumber: 1, startTime: null, endTime: null },
      { matchNumber: 2, startTime: '18:15:00', endTime: '19:30:00' },
    ];
    // 18:00 は 2試合目の境界(19:45)未満。1試合目は対象外なので 2試合目が返る
    expect(timeBasedDefaultMatchNumber(partial, at(18, 0))).toBe(2);
    // 20:00 は 2試合目も超過 → 1に戻す
    expect(timeBasedDefaultMatchNumber(partial, at(20, 0))).toBe(1);
  });

  it('matchNumber 昇順でない配列でも昇順走査する', () => {
    const reversed = [
      { matchNumber: 2, startTime: '18:15:00', endTime: '19:30:00' },
      { matchNumber: 1, startTime: '17:05:00', endTime: '18:15:00' },
    ];
    expect(timeBasedDefaultMatchNumber(reversed, at(18, 0))).toBe(1);
    expect(timeBasedDefaultMatchNumber(reversed, at(18, 30))).toBe(2);
  });
});

describe('getCompletedMatchNumbers', () => {
  const pairings = [
    { matchNumber: 1, player1Id: 1, player2Id: 2 },
    { matchNumber: 1, player1Id: 3, player2Id: 4 },
    { matchNumber: 2, player1Id: 1, player2Id: 3 },
  ];

  it('全入力済み: すべての試合番号を返す', () => {
    const matches = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
      { matchNumber: 1, player1Id: 3, player2Id: 4 },
      { matchNumber: 2, player1Id: 1, player2Id: 3 },
    ];
    expect(getCompletedMatchNumbers({ pairings, matches, totalMatches: 2 })).toEqual([1, 2]);
  });

  it('一部入力済み: 全ペアリングに結果がある番号のみ返す', () => {
    // 1試合目は2件中2件、2試合目は0件
    const matches = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
      { matchNumber: 1, player1Id: 3, player2Id: 4 },
    ];
    expect(getCompletedMatchNumbers({ pairings, matches, totalMatches: 2 })).toEqual([1]);
  });

  it('ペアリングの一部だけ結果がある番号は入力済みにしない', () => {
    // 1試合目は2件中1件のみ
    const matches = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
    ];
    expect(getCompletedMatchNumbers({ pairings, matches, totalMatches: 2 })).toEqual([]);
  });

  it('ペアリング0件の試合番号は含めない', () => {
    // totalMatches=3 だが3試合目はペアリングなし
    const matches = [
      { matchNumber: 1, player1Id: 1, player2Id: 2 },
      { matchNumber: 1, player1Id: 3, player2Id: 4 },
    ];
    expect(getCompletedMatchNumbers({ pairings, matches, totalMatches: 3 })).toEqual([1]);
  });

  it('player id が逆順でも min/max 正規化で突合する', () => {
    const p = [{ matchNumber: 1, player1Id: 2, player2Id: 1 }];
    const m = [{ matchNumber: 1, player1Id: 1, player2Id: 2 }];
    expect(getCompletedMatchNumbers({ pairings: p, matches: m, totalMatches: 1 })).toEqual([1]);
  });

  it('matches/pairings が空でも安全に動作する', () => {
    expect(getCompletedMatchNumbers({ pairings: [], matches: [], totalMatches: 3 })).toEqual([]);
    expect(getCompletedMatchNumbers({ totalMatches: 0 })).toEqual([]);
  });
});

describe('defaultForBulkInput', () => {
  it('1試合目未入力＆2試合目入力済み → 3試合目（入力済み最大+1）', () => {
    expect(defaultForBulkInput({
      completedMatchNumbers: [2],
      totalMatches: 5,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: TODAY,
      now: at(18, 0),
    })).toBe(3);
  });

  it('全入力済み → 最終試合（min でクランプ）', () => {
    expect(defaultForBulkInput({
      completedMatchNumbers: [1, 2, 3],
      totalMatches: 3,
      sessionDate: TODAY,
      now: at(12, 0),
    })).toBe(3);
  });

  it('入力済み皆無＆当日 → 時刻ベース', () => {
    expect(defaultForBulkInput({
      completedMatchNumbers: [],
      totalMatches: 2,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: TODAY,
      now: at(18, 30),
    })).toBe(2);
  });

  it('入力済み皆無＆過去日 → 1試合目', () => {
    expect(defaultForBulkInput({
      completedMatchNumbers: [],
      totalMatches: 2,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: PAST_DAY,
      now: at(18, 30),
    })).toBe(1);
  });

  it('入力済み皆無＆スケジュール無し → 1試合目', () => {
    expect(defaultForBulkInput({
      completedMatchNumbers: [],
      totalMatches: 2,
      venueSchedules: [],
      sessionDate: TODAY,
      now: at(18, 30),
    })).toBe(1);
  });

  it('入力済み制約は時刻ベースより優先される', () => {
    // 現在18:00 の時刻ベースは1試合目だが、1試合目入力済みなら2試合目
    expect(defaultForBulkInput({
      completedMatchNumbers: [1],
      totalMatches: 5,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: TODAY,
      now: at(18, 0),
    })).toBe(2);
  });
});

describe('defaultForResultsView', () => {
  it('urlMatchNumber 指定時は最優先（時刻ベースより優先）', () => {
    expect(defaultForResultsView({
      urlMatchNumber: 3,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: TODAY,
      now: at(18, 30), // 時刻ベースなら2
    })).toBe(3);
  });

  it('urlMatchNumber なし＆当日 → 時刻ベース', () => {
    expect(defaultForResultsView({
      urlMatchNumber: null,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: TODAY,
      now: at(18, 30),
    })).toBe(2);
  });

  it('urlMatchNumber なし＆過去日 → 1試合目', () => {
    expect(defaultForResultsView({
      urlMatchNumber: null,
      venueSchedules: EXAMPLE_SCHEDULES,
      sessionDate: PAST_DAY,
      now: at(18, 30),
    })).toBe(1);
  });

  it('urlMatchNumber なし＆スケジュール無し → 1試合目', () => {
    expect(defaultForResultsView({
      urlMatchNumber: null,
      venueSchedules: [],
      sessionDate: TODAY,
      now: at(18, 30),
    })).toBe(1);
  });
});
