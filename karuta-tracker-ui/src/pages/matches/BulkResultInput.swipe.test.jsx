import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../api', () => ({
  matchAPI: { getByDate: vi.fn() },
  pairingAPI: { getByDate: vi.fn() },
  practiceAPI: { getById: vi.fn(), getParticipants: vi.fn() },
  byeActivityAPI: { getByDate: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 1, name: 'テスト選手', organizationId: 10 } }),
}));

import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI } from '../../api';
import BulkResultInput from './BulkResultInput';

/**
 * BulkResultInput スワイプ挙動テスト
 *
 * - 左/右スワイプで試合番号タブが切り替わる、端で止まる
 * - タブタップは従来どおり動作する
 * - スワイプで試合を移動しても入力内容（変更件数）は保持される
 * - 下部固定の保存バーはカルーセル外で維持される
 */

const SESSION = {
  id: 42,
  sessionDate: '2026-06-24',
  totalMatches: 3,
  organizationId: 10,
  pairingIncludesPending: false,
};
const PAIRINGS = [
  { id: 1, matchNumber: 1, player1Id: 2, player1Name: 'A1', player2Id: 3, player2Name: 'B1' },
  { id: 2, matchNumber: 2, player1Id: 2, player1Name: 'A2', player2Id: 3, player2Name: 'B2' },
  { id: 3, matchNumber: 3, player1Id: 2, player1Name: 'A3', player2Id: 3, player2Name: 'B3' },
];

const WIDTH = 300;
let offsetWidthSpy;

beforeEach(() => {
  offsetWidthSpy = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(WIDTH);
  practiceAPI.getById.mockResolvedValue({ data: SESSION });
  pairingAPI.getByDate.mockResolvedValue({ data: PAIRINGS });
  matchAPI.getByDate.mockResolvedValue({ data: [] });
  practiceAPI.getParticipants.mockResolvedValue({ data: [] });
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
});

afterEach(() => {
  offsetWidthSpy.mockRestore();
  cleanup();
  vi.clearAllMocks();
});

const renderView = () =>
  render(
    <MemoryRouter initialEntries={['/matches/bulk-input/42']}>
      <Routes>
        <Route path="/matches/bulk-input/:sessionId" element={<BulkResultInput />} />
        <Route path="/pairings" element={<div>pairings</div>} />
        <Route path="/matches/results/:sessionId" element={<div>results</div>} />
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

describe('BulkResultInput - スワイプによる試合切替', () => {
  it('左スワイプで次の試合、右スワイプで前の試合へ切り替わる', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    swipe(-120);
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));

    swipe(120);
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
  });

  it('最後の試合で左スワイプしても切り替わらない（端で止まる）', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    fireEvent.click(tab(3));
    await waitFor(() => expect(tab(3)).toHaveAttribute('data-active', 'true'));

    swipe(-120); // next方向だが端
    await new Promise((r) => setTimeout(r, 250));
    expect(tab(3)).toHaveAttribute('data-active', 'true');
  });

  it('スワイプで試合を移動しても入力内容（変更件数）が保持され、保存バーが固定表示される', async () => {
    renderView();
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));

    // 1試合目で勝者を選択 → 保存バーに1件
    fireEvent.click(screen.getByRole('button', { name: 'A1' }));
    await waitFor(() => expect(screen.getByText(/保存する（1件）/)).toBeInTheDocument());

    // 2試合目へスワイプ → 保存バーは保持（カルーセル外で固定）
    swipe(-120);
    await waitFor(() => expect(tab(2)).toHaveAttribute('data-active', 'true'));
    expect(screen.getByText(/保存する（1件）/)).toBeInTheDocument();

    // 1試合目へ戻っても入力件数は維持
    swipe(120);
    await waitFor(() => expect(tab(1)).toHaveAttribute('data-active', 'true'));
    expect(screen.getByText(/保存する（1件）/)).toBeInTheDocument();
  });
});
