import { describe, expect, it } from 'vitest';

import {
  buildWeeks,
  countMatchesInMonth,
  formatMonthDay,
  groupMatchesByDate,
  isoDate,
  opponentKyuChar,
  venueOfDay,
} from './matchCalendar';

describe('isoDate', () => {
  it('年・月(0-based)・日を YYYY-MM-DD にゼロ埋めで組み立てる', () => {
    expect(isoDate(2026, 6, 1)).toBe('2026-07-01');
    expect(isoDate(2026, 11, 31)).toBe('2026-12-31');
  });
});

describe('buildWeeks（月カレンダーの週グリッド）', () => {
  // 2026年7月: 7/1 は水曜（getDay()=3）。前月6月は30日。
  const weeks = buildWeeks(2026, 6);

  it('各週7セル・総セル数は7の倍数', () => {
    weeks.forEach((w) => expect(w).toHaveLength(7));
    expect(weeks.flat().length % 7).toBe(0);
  });

  it('月初の曜日ぶん前月末のはみ出しセルで埋める（inMonth:false・date無し）', () => {
    expect(weeks[0][0]).toEqual({ day: 28, inMonth: false });
    expect(weeks[0][1]).toEqual({ day: 29, inMonth: false });
    expect(weeks[0][2]).toEqual({ day: 30, inMonth: false });
  });

  it('当月セルは inMonth:true かつ date を持つ', () => {
    expect(weeks[0][3]).toEqual({ day: 1, inMonth: true, date: '2026-07-01' });
    const cell21 = weeks.flat().find((c) => c.date === '2026-07-21');
    expect(cell21).toEqual({ day: 21, inMonth: true, date: '2026-07-21' });
  });

  it('末尾は翌月頭のはみ出しセルで7の倍数に整える', () => {
    const last = weeks.flat().at(-1);
    expect(last).toEqual({ day: 1, inMonth: false });
    // 当月末日(31日)まで全て含む
    expect(weeks.flat().some((c) => c.inMonth && c.day === 31)).toBe(true);
  });
});

describe('groupMatchesByDate（日付ごとに試合番号昇順でグループ化）', () => {
  it('同日の試合を試合番号昇順に並べる', () => {
    const map = groupMatchesByDate([
      { id: 2, matchDate: '2026-07-03', matchNumber: 2 },
      { id: 1, matchDate: '2026-07-03', matchNumber: 1 },
      { id: 3, matchDate: '2026-07-05', matchNumber: 1 },
    ]);
    expect(map['2026-07-03'].map((m) => m.id)).toEqual([1, 2]);
    expect(map['2026-07-05'].map((m) => m.id)).toEqual([3]);
  });

  it('matchDate を持たない試合は除外する', () => {
    const map = groupMatchesByDate([
      { id: 1, matchDate: null, matchNumber: 1 },
      { id: 2, matchNumber: 1 },
    ]);
    expect(Object.keys(map)).toHaveLength(0);
  });
});

describe('countMatchesInMonth（指定年月の試合数）', () => {
  const matches = [
    { matchDate: '2026-07-01' },
    { matchDate: '2026-07-31' },
    { matchDate: '2026-08-01' },
    { matchDate: null },
  ];
  it('当該年月のみを数える（他月・matchDate無しは除外）', () => {
    expect(countMatchesInMonth(matches, 2026, 6)).toBe(2); // 7月
    expect(countMatchesInMonth(matches, 2026, 7)).toBe(1); // 8月
    expect(countMatchesInMonth(matches, 2025, 6)).toBe(0);
  });
});

describe('formatMonthDay（M/D・十の位0省略）', () => {
  it('先頭ゼロを落とす', () => {
    expect(formatMonthDay('2026-07-03')).toBe('7/3');
    expect(formatMonthDay('2026-01-09')).toBe('1/9');
  });
  it('2桁はそのまま', () => {
    expect(formatMonthDay('2026-12-25')).toBe('12/25');
  });
  it('空入力は空文字', () => {
    expect(formatMonthDay('')).toBe('');
  });
});

describe('opponentKyuChar（相手級の単字化）', () => {
  it('自分が player1 のとき相手(player2)の級を単字化する', () => {
    expect(
      opponentKyuChar({ player1Id: 1, player2KyuRank: 'A級', player1KyuRank: 'C級' }, 1)
    ).toBe('A');
  });
  it('自分が player2 のとき相手(player1)の級を単字化する', () => {
    expect(
      opponentKyuChar({ player1Id: 2, player2Id: 1, player1KyuRank: 'B級', player2KyuRank: 'D級' }, 1)
    ).toBe('B');
  });
  it('級が未設定(null)なら空文字', () => {
    expect(opponentKyuChar({ player1Id: 1, player2KyuRank: null }, 1)).toBe('');
  });
  it('match が無ければ空文字', () => {
    expect(opponentKyuChar(null, 1)).toBe('');
  });
});

describe('venueOfDay（その日の会場名。日付に一意の前提）', () => {
  it('最初に会場名を持つ試合の venueName を返す', () => {
    expect(venueOfDay([{ venueName: null }, { venueName: 'クラ館' }])).toBe('クラ館');
  });
  it('会場が無ければ空文字', () => {
    expect(venueOfDay([{ venueName: null }])).toBe('');
    expect(venueOfDay([])).toBe('');
  });
});
