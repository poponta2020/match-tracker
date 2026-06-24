import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
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
 * MatchResultsView スワイプ挙動テスト
 *
 * - 左スワイプで次の試合、右スワイプで前の試合へタブが切り替わる
 * - 端（最初/最後）ではスワイプしても切り替わらない
 * - タブタップは従来どおり動作する
 * - 日付ナビ・FAB（固定要素）は従来どおり表示される
 */

const DATE = '2026-06-24';
const SESSION = {
  id: 42,
  sessionDate: DATE,
  totalMatches: 3,
  pairingIncludesPending: false,
  matchParticipants: {},
};
const PAIRINGS = [
  { id: 1, matchNumber: 1, player1Id: 2, player1Name: 'A1', player2Id: 3, player2Name: 'B1' },
  { id: 2, matchNumber: 2, player1Id: 2, player1Name: 'A2', player2Id: 3, player2Name: 'B2' },
  { id: 3, matchNumber: 3, player1Id: 2, player1Name: 'A3', player2Id: 3, player2Name: 'B3' },
];

const WIDTH = 300; // 確定閾値 = 75px
let offsetWidthSpy;

beforeEach(() => {
  offsetWidthSpy = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(WIDTH);
  practiceAPI.getDates.mockResolvedValue({ data: [DATE] });
  practiceAPI.getByDate.mockResolvedValue({ data: SESSION });
  pairingAPI.getByDate.mockResolvedValue({ data: PAIRINGS });
  matchAPI.getByDate.mockResolvedValue({ data: [] });
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
  matchVideoAPI.getByDate.mockResolvedValue({ data: [] });
});

afterEach(() => {
  offsetWidthSpy.mockRestore();
  cleanup();
  vi.clearAllMocks();
});

const renderView = () =>
  render(
    <MemoryRouter initialEntries={[`/matches/results/42?date=${DATE}`]}>
      <Routes>
        <Route path="/matches/results/:sessionId" element={<MatchResultsView />} />
        <Route path="/pairings" element={<div>pairings</div>} />
      </Routes>
    </MemoryRouter>
  );

const tab = (n) => screen.getByRole('button', { name: new RegExp(`${n}試合目`) });

const swipe = (dx) => {
  const el = screen.getByTestId('match-carousel');
  fireEvent.touchStart(el, { touches: [{ clientX: 200, clientY: 200 }] });
  fireEvent.touchMove(el, { touches: [{ clientX: 200 + dx, clientY: 200 }] });
  fireEvent.touchEnd(el, { changedTouches: [{ clientX: 200 + dx, clientY: 200 }] });
};

describe('MatchResultsView - スワイプによる試合切替', () => {
  it('左スワイプで次の試合番号タブがアクティブになる', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    swipe(-120); // 左スワイプ = 次へ
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(tab(1)).toHaveAttribute('data-active', 'false');
  });

  it('右スワイプで前の試合番号タブがアクティブになる', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    // まず2試合目へ
    swipe(-120);
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    // 右スワイプで1試合目へ戻る
    swipe(120);
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
  });

  it('最初の試合で右スワイプしても切り替わらない（端で止まる）', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    swipe(120); // prev方向だが端
    await new Promise((r) => setTimeout(r, 250));
    expect(tab(1)).toHaveAttribute('data-active', 'true');
  });

  it('タブタップでも従来どおり切り替わる', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    fireEvent.click(tab(3));
    await waitFor(() => expect(tab(3)).toHaveAttribute('data-active', 'true'));
  });

  it('日付ナビ・FAB（固定要素）が表示される', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
    expect(screen.getByText('自分の結果を入力')).toBeInTheDocument();
  });
});
