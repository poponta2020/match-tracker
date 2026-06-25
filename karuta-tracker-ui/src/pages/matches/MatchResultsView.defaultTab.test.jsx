import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../api', () => ({
  matchAPI: { getByDate: vi.fn() },
  pairingAPI: { getByDate: vi.fn() },
  practiceAPI: { getByDate: vi.fn(), getDates: vi.fn() },
  byeActivityAPI: { getByDate: vi.fn() },
  matchVideoAPI: { getByDate: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 1, name: 'テスト選手' } }),
}));

import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI, matchVideoAPI } from '../../api';
import MatchResultsView from './MatchResultsView';

/**
 * MatchResultsView 初期表示試合番号テスト
 *
 * - URLクエリ matchNumber 指定時はその番号を最優先（時刻ベースより優先）
 * - 当日 かつ 会場スケジュールありなら時刻ベースで初期表示
 * - 過去日 / スケジュール無しは従来どおり1試合目
 */

const TODAY = '2026-06-25';
const PAST_DAY = '2026-06-24';

// 1試合目 17:05–18:15 / 2試合目 18:15–19:30 / 3試合目 19:30–20:45
const SCHEDULES = [
  { id: 1, matchNumber: 1, startTime: '17:05:00', endTime: '18:15:00' },
  { id: 2, matchNumber: 2, startTime: '18:15:00', endTime: '19:30:00' },
  { id: 3, matchNumber: 3, startTime: '19:30:00', endTime: '20:45:00' },
];

const sessionWith = (date, schedules) => ({
  id: 42,
  sessionDate: date,
  totalMatches: 3,
  pairingIncludesPending: false,
  matchParticipants: {},
  venueSchedules: schedules,
});

const PAIRINGS = [
  { id: 1, matchNumber: 1, player1Id: 2, player1Name: 'A1', player2Id: 3, player2Name: 'B1' },
  { id: 2, matchNumber: 2, player1Id: 2, player1Name: 'A2', player2Id: 3, player2Name: 'B2' },
  { id: 3, matchNumber: 3, player1Id: 2, player1Name: 'A3', player2Id: 3, player2Name: 'B3' },
];

let offsetWidthSpy;

beforeEach(() => {
  offsetWidthSpy = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(300);
  // Date のみ偽装（setTimeout は実時間のまま → waitFor が機能する）
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 5, 25, 18, 30, 0)); // 当日 18:30
  pairingAPI.getByDate.mockResolvedValue({ data: PAIRINGS });
  matchAPI.getByDate.mockResolvedValue({ data: [] });
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
  matchVideoAPI.getByDate.mockResolvedValue({ data: [] });
});

afterEach(() => {
  vi.useRealTimers();
  offsetWidthSpy.mockRestore();
  cleanup();
  vi.clearAllMocks();
});

const renderView = (query) =>
  render(
    <MemoryRouter initialEntries={[`/matches/results/42${query}`]}>
      <Routes>
        <Route path="/matches/results/:sessionId" element={<MatchResultsView />} />
        <Route path="/pairings" element={<div>pairings</div>} />
        <Route path="/matches/bulk-input/:sessionId" element={<div>bulk</div>} />
      </Routes>
    </MemoryRouter>
  );

const tab = (n) => screen.getByRole('button', { name: new RegExp(`${n}試合目`) });

describe('MatchResultsView - 初期表示試合番号のデフォルト', () => {
  it('当日かつ会場スケジュールあり: 18:30 は時刻ベースで2試合目を初期表示', async () => {
    practiceAPI.getDates.mockResolvedValue({ data: [TODAY] });
    practiceAPI.getByDate.mockResolvedValue({ data: sessionWith(TODAY, SCHEDULES) });

    renderView(`?date=${TODAY}`);
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(tab(1)).toHaveAttribute('data-active', 'false');
  });

  it('URLクエリ matchNumber 指定時は時刻ベースより優先される', async () => {
    practiceAPI.getDates.mockResolvedValue({ data: [TODAY] });
    practiceAPI.getByDate.mockResolvedValue({ data: sessionWith(TODAY, SCHEDULES) });

    // 時刻ベースなら2試合目だが、matchNumber=3 指定で3試合目
    renderView(`?date=${TODAY}&matchNumber=3`);
    await waitFor(() => expect(tab(3)).toHaveAttribute('data-active', 'true'));
  });

  it('matchNumber が非数値混じり（例: 3abc）の場合は無視して時刻ベースにフォールバックする', async () => {
    practiceAPI.getDates.mockResolvedValue({ data: [TODAY] });
    practiceAPI.getByDate.mockResolvedValue({ data: sessionWith(TODAY, SCHEDULES) });

    // "3abc" は parseInt では 3 になり得るが、純粋な整数文字列でないため無視され、
    // 18:30 の時刻ベース（2試合目）にフォールバックする（バグがあれば3試合目になる）
    renderView(`?date=${TODAY}&matchNumber=3abc`);
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(tab(3)).toHaveAttribute('data-active', 'false');
  });

  it('過去日は時刻ベースを発動せず1試合目を初期表示', async () => {
    practiceAPI.getDates.mockResolvedValue({ data: [PAST_DAY] });
    practiceAPI.getByDate.mockResolvedValue({ data: sessionWith(PAST_DAY, SCHEDULES) });

    renderView(`?date=${PAST_DAY}`);
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
  });

  it('会場スケジュール未定義なら当日でも1試合目を初期表示', async () => {
    practiceAPI.getDates.mockResolvedValue({ data: [TODAY] });
    practiceAPI.getByDate.mockResolvedValue({ data: sessionWith(TODAY, []) });

    renderView(`?date=${TODAY}`);
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
  });
});
