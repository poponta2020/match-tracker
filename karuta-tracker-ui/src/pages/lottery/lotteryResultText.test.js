import { describe, it, expect } from 'vitest';
import {
  buildCopyText,
  hasAnyWaitlisted,
  formatSessionHeader,
  getJapaneseWeekday,
} from './lotteryResultText';

const mkWaitlisted = (playerName, waitlistNumber, status = 'WAITLISTED') => ({
  playerId: Math.floor(Math.random() * 100000),
  playerName,
  status,
  waitlistNumber,
});

const mkSession = (sessionDate, venueName, matchResults) => ({
  sessionId: Math.floor(Math.random() * 100000),
  sessionDate,
  venueName,
  matchResults,
});

describe('getJapaneseWeekday', () => {
  it('returns 1-character Japanese weekday', () => {
    expect(getJapaneseWeekday(new Date('2026-04-05'))).toBe('日');
    expect(getJapaneseWeekday(new Date('2026-04-06'))).toBe('月');
    expect(getJapaneseWeekday(new Date('2026-04-11'))).toBe('土');
  });
});

describe('formatSessionHeader', () => {
  it('formats date and venue without zero-padding', () => {
    expect(formatSessionHeader('2026-04-05', '麹町区民館')).toBe('4/5（日）麹町区民館');
  });
  it('substitutes 会場未設定 when venueName is null', () => {
    expect(formatSessionHeader('2026-04-05', null)).toBe('4/5（日）会場未設定');
  });
  it('substitutes 会場未設定 when venueName is empty string', () => {
    expect(formatSessionHeader('2026-04-05', '')).toBe('4/5（日）会場未設定');
  });
  // `new Date('YYYY-MM-DD')` は UTC 解釈になり、UTC- の TZ (例: America/Los_Angeles)
  // では前日に shift する。実装はローカル日付として扱う必要がある。
  // 以下のテストは新実装（split + new Date(y, m-1, d)）と、その正解を返す
  // ローカル Date コンストラクタを直接比較することで、UTC- TZ で実行された場合に
  // 旧実装の回帰（4/5（日） が 4/4（土） になる等）を検出する。
  it('parses YYYY-MM-DD as local date and matches new Date(year, month-1, day)', () => {
    const cases = ['2026-01-01', '2026-04-05', '2026-07-15', '2026-12-31'];
    for (const dateStr of cases) {
      const [y, m, d] = dateStr.split('-').map(Number);
      const expectedWeekday = ['日', '月', '火', '水', '木', '金', '土'][new Date(y, m - 1, d).getDay()];
      expect(formatSessionHeader(dateStr, 'V')).toBe(`${m}/${d}（${expectedWeekday}）V`);
    }
  });
});

describe('hasAnyWaitlisted', () => {
  it('returns true when any session has a WAITLISTED player', () => {
    const sessions = [
      mkSession('2026-04-05', 'V', { 1: { matchNumber: 1, waitlisted: [mkWaitlisted('A', 1)] } }),
    ];
    expect(hasAnyWaitlisted(sessions)).toBe(true);
  });
  it('returns false when only non-WAITLISTED statuses exist', () => {
    const sessions = [
      mkSession('2026-04-05', 'V', {
        1: {
          matchNumber: 1,
          waitlisted: [
            mkWaitlisted('A', 1, 'OFFERED'),
            mkWaitlisted('B', 2, 'WAITLIST_DECLINED'),
          ],
        },
      }),
    ];
    expect(hasAnyWaitlisted(sessions)).toBe(false);
  });
  it('returns false for empty sessions list', () => {
    expect(hasAnyWaitlisted([])).toBe(false);
    expect(hasAnyWaitlisted(null)).toBe(false);
  });
});

describe('buildCopyText', () => {
  it('produces header + footer only when no session has WAITLISTED', () => {
    const text = buildCopyText(2026, 4, []);
    expect(text).toBe('4月の抽選結果（抽選落ちのみ）です\n\nご確認のほどおねがいします。');
  });

  it('omits sessions where every match has zero WAITLISTED', () => {
    const sessions = [
      // 全試合 WAITLISTED 0 → 省略対象
      mkSession('2026-04-05', '会場A', {
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('A', 1, 'OFFERED')] },
        2: { matchNumber: 2, waitlisted: [] },
      }),
      // WAITLISTED あり → 出力対象
      mkSession('2026-04-12', '会場B', {
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('B', 1)] },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    expect(text).not.toContain('会場A');
    expect(text).toContain('会場B');
  });

  it('renders 「（なし）」 for empty matches inside a session that has WAITLISTED elsewhere', () => {
    const sessions = [
      mkSession('2026-04-12', '会場B', {
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('B', 1)] },
        2: { matchNumber: 2, waitlisted: [] },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    expect(text).toContain('★1試合目★');
    expect(text).toContain('★2試合目★');
    expect(text).toContain('（なし）');
  });

  it('filters out non-WAITLISTED statuses (OFFERED / WAITLIST_DECLINED)', () => {
    const sessions = [
      mkSession('2026-04-05', '会場A', {
        1: {
          matchNumber: 1,
          waitlisted: [
            mkWaitlisted('待機A', 1),
            mkWaitlisted('繰上中', 2, 'OFFERED'),
            mkWaitlisted('辞退者', 3, 'WAITLIST_DECLINED'),
          ],
        },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    expect(text).toContain('1. 待機A');
    expect(text).not.toContain('繰上中');
    expect(text).not.toContain('辞退者');
  });

  it('orders WAITLISTED players by waitlistNumber ascending', () => {
    const sessions = [
      mkSession('2026-04-05', '会場A', {
        1: {
          matchNumber: 1,
          waitlisted: [
            mkWaitlisted('三人目', 3),
            mkWaitlisted('一人目', 1),
            mkWaitlisted('二人目', 2),
          ],
        },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    const idx1 = text.indexOf('一人目');
    const idx2 = text.indexOf('二人目');
    const idx3 = text.indexOf('三人目');
    expect(idx1).toBeLessThan(idx2);
    expect(idx2).toBeLessThan(idx3);
    expect(text).toContain('1. 一人目');
    expect(text).toContain('2. 二人目');
    expect(text).toContain('3. 三人目');
  });

  it('orders matches inside a session by match number ascending', () => {
    const sessions = [
      mkSession('2026-04-05', '会場A', {
        2: { matchNumber: 2, waitlisted: [mkWaitlisted('B', 1)] },
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('A', 1)] },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    expect(text.indexOf('★1試合目★')).toBeLessThan(text.indexOf('★2試合目★'));
  });

  it('orders sessions by sessionDate ascending', () => {
    const sessions = [
      mkSession('2026-04-12', '会場B', {
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('B', 1)] },
      }),
      mkSession('2026-04-05', '会場A', {
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('A', 1)] },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    expect(text.indexOf('会場A')).toBeLessThan(text.indexOf('会場B'));
  });

  it('produces the full expected format for the requirements example', () => {
    const sessions = [
      mkSession('2026-04-05', '麹町区民館', {
        1: {
          matchNumber: 1,
          waitlisted: [
            mkWaitlisted('田中太郎', 1),
            mkWaitlisted('山田次郎', 2),
            mkWaitlisted('佐藤三郎', 3),
          ],
        },
        2: {
          matchNumber: 2,
          waitlisted: [
            mkWaitlisted('鈴木史郎', 1),
            mkWaitlisted('高橋五郎', 2),
          ],
        },
      }),
      mkSession('2026-04-12', '麹町区民館', {
        1: { matchNumber: 1, waitlisted: [mkWaitlisted('田中太郎', 1)] },
        2: { matchNumber: 2, waitlisted: [] },
      }),
    ];
    const text = buildCopyText(2026, 4, sessions);
    const expected = [
      '4月の抽選結果（抽選落ちのみ）です',
      '',
      '4/5（日）麹町区民館',
      '★1試合目★',
      '1. 田中太郎',
      '2. 山田次郎',
      '3. 佐藤三郎',
      '★2試合目★',
      '1. 鈴木史郎',
      '2. 高橋五郎',
      '',
      '4/12（日）麹町区民館',
      '★1試合目★',
      '1. 田中太郎',
      '★2試合目★',
      '（なし）',
      '',
      'ご確認のほどおねがいします。',
    ].join('\n');
    expect(text).toBe(expected);
  });
});
