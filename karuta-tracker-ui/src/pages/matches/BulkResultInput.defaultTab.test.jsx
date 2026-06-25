import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';

vi.mock('../../api', () => ({
  matchAPI: { getByDate: vi.fn(), createDetailed: vi.fn(), updateDetailed: vi.fn() },
  pairingAPI: { getByDate: vi.fn() },
  practiceAPI: { getById: vi.fn(), getParticipants: vi.fn() },
  byeActivityAPI: { getByDate: vi.fn(), createBatch: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 1, name: 'テスト選手', organizationId: 10 } }),
}));

import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI } from '../../api';
import BulkResultInput from './BulkResultInput';

/**
 * BulkResultInput 初期表示試合番号 + 保存後遷移テスト
 *
 * - 入力済み試合番号の最大+1 を初期表示（時刻ベースより優先）
 * - 入力済み皆無なら当日の時刻ベースで初期表示
 * - 保存後の一覧画面遷移に ?matchNumber= が付与される
 */

const TODAY = '2026-06-25';
const PAST_DAY = '2026-06-24';

const SCHEDULES = [
  { id: 1, matchNumber: 1, startTime: '17:05:00', endTime: '18:15:00' },
  { id: 2, matchNumber: 2, startTime: '18:15:00', endTime: '19:30:00' },
  { id: 3, matchNumber: 3, startTime: '19:30:00', endTime: '20:45:00' },
];

const sessionWith = (date, totalMatches, schedules) => ({
  id: 42,
  sessionDate: date,
  totalMatches,
  organizationId: 10,
  pairingIncludesPending: false,
  venueSchedules: schedules,
});

const pairingsFor = (count) =>
  Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    matchNumber: i + 1,
    player1Id: 2,
    player1Name: `A${i + 1}`,
    player2Id: 3,
    player2Name: `B${i + 1}`,
  }));

let locationRef = null;
const LocationProbe = () => {
  locationRef = useLocation();
  return null;
};

const setupMocks = ({ session, pairings = [], matches = [] }) => {
  practiceAPI.getById.mockResolvedValue({ data: session });
  pairingAPI.getByDate.mockResolvedValue({ data: pairings });
  matchAPI.getByDate.mockResolvedValue({ data: matches });
  practiceAPI.getParticipants.mockResolvedValue({ data: [] });
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
};

const renderComponent = () => {
  locationRef = null;
  return render(
    <MemoryRouter initialEntries={['/matches/bulk-input/42']}>
      <LocationProbe />
      <Routes>
        <Route path="/matches/bulk-input/:sessionId" element={<BulkResultInput />} />
        <Route path="/matches/results/:sessionId" element={<div data-testid="results-page">results</div>} />
      </Routes>
    </MemoryRouter>
  );
};

const tab = (n) => screen.getByRole('button', { name: new RegExp(`${n}試合目`) });

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  locationRef = null;
});

describe('BulkResultInput - 初期表示試合番号のデフォルト', () => {
  it('1試合目入力済みなら2試合目を初期表示（入力済み最大+1）', async () => {
    setupMocks({
      session: sessionWith(PAST_DAY, 3, undefined),
      pairings: pairingsFor(3),
      matches: [{ matchNumber: 1, player1Id: 2, player2Id: 3, winnerId: 2, scoreDifference: 5, isLesson: false, id: 50 }],
    });
    renderComponent();
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(tab(1)).toHaveAttribute('data-active', 'false');
  });

  it('入力済み皆無かつ当日・会場スケジュールあり: 時刻ベースで2試合目を初期表示', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 5, 25, 18, 30, 0)); // 18:30
    setupMocks({
      session: sessionWith(TODAY, 3, SCHEDULES),
      pairings: pairingsFor(3),
      matches: [],
    });
    renderComponent();
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
  });

  it('入力済み皆無かつ過去日: 1試合目を初期表示', async () => {
    setupMocks({
      session: sessionWith(PAST_DAY, 3, SCHEDULES),
      pairings: pairingsFor(3),
      matches: [],
    });
    renderComponent();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
  });
});

describe('BulkResultInput - 保存後遷移の試合番号引き継ぎ', () => {
  it('保存後に /matches/results へ ?date=&matchNumber= 付きで遷移する（保存元セッションの日付も引き継ぐ）', async () => {
    matchAPI.createDetailed.mockResolvedValue({ data: { id: 100 } });
    setupMocks({
      session: sessionWith(PAST_DAY, 1, undefined),
      pairings: pairingsFor(1),
      matches: [],
    });
    renderComponent();

    const user = userEvent.setup();
    // 初期表示は1試合目
    await waitFor(() => expect(screen.getByRole('button', { name: 'A1' })).toBeInTheDocument());

    // 勝者を選択 → 枚数差を選択 → 保存
    await user.click(screen.getByRole('button', { name: 'A1' }));
    await user.selectOptions(screen.getByRole('combobox'), '5');
    await user.click(screen.getByRole('button', { name: /保存する/ }));

    await waitFor(() => expect(screen.getByTestId('results-page')).toBeInTheDocument());
    expect(matchAPI.createDetailed).toHaveBeenCalled();
    expect(locationRef.pathname).toBe('/matches/results/42');
    expect(locationRef.search).toContain('matchNumber=1');
    // 保存元セッションの日付（過去日）も引き継ぐ → 一覧側が当日に解決して別日を開く事故を防ぐ
    expect(locationRef.search).toContain(`date=${PAST_DAY}`);
  });
});
