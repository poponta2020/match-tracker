import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';

vi.mock('../../api', () => ({
  matchAPI: { getByDate: vi.fn() },
  pairingAPI: { getByDate: vi.fn() },
  practiceAPI: {
    getById: vi.fn(),
    getParticipants: vi.fn(),
  },
  byeActivityAPI: { getByDate: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'テスト選手', organizationId: 10 },
  }),
}));

import { matchAPI, pairingAPI, practiceAPI, byeActivityAPI } from '../../api';
import BulkResultInput from './BulkResultInput';

/**
 * BulkResultInput「対戦変更」ボタンのナビゲーション回帰テスト
 *
 * 検証ポイント:
 * 1. 未保存入力なし → confirm なしで /pairings へ遷移
 * 2. 未保存入力あり + confirm OK → /pairings へ遷移
 * 3. 未保存入力あり + confirm キャンセル → 遷移しない
 * 4. 遷移先 URL に date / matchNumber / from パラメータが含まれる
 */

let locationRef = null;
const LocationProbe = () => {
  locationRef = useLocation();
  return null;
};

const SESSION_ID = '42';
const SESSION = {
  id: 42,
  sessionDate: '2026-06-24',
  totalMatches: 2,
  organizationId: 10,
  pairingIncludesPending: false,
};

const PAIRINGS = [
  {
    id: 1,
    matchNumber: 1,
    player1Id: 2,
    player1Name: '選手A',
    player2Id: 3,
    player2Name: '選手B',
  },
];

const setupMocks = () => {
  practiceAPI.getById.mockResolvedValue({ data: SESSION });
  pairingAPI.getByDate.mockResolvedValue({ data: PAIRINGS });
  matchAPI.getByDate.mockResolvedValue({ data: [] });
  practiceAPI.getParticipants.mockResolvedValue({ data: [] });
  byeActivityAPI.getByDate.mockResolvedValue({ data: [] });
};

const renderComponent = () => {
  locationRef = null;
  return render(
    <MemoryRouter initialEntries={[`/matches/bulk-input/${SESSION_ID}`]}>
      <LocationProbe />
      <Routes>
        <Route path="/matches/bulk-input/:sessionId" element={<BulkResultInput />} />
        <Route path="/pairings" element={<div data-testid="pairings-page">pairings</div>} />
      </Routes>
    </MemoryRouter>
  );
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  locationRef = null;
  // jsdom では window.confirm が undefined のため vi.fn() を割り当てる
  window.confirm = vi.fn().mockReturnValue(false);
});

describe('BulkResultInput - 「対戦変更」ボタンのナビゲーション', () => {
  it('未保存入力がない場合、confirm を表示せず /pairings へ遷移する', async () => {
    setupMocks();
    renderComponent();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /対戦変更/ })).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /対戦変更/ }));

    expect(window.confirm).not.toHaveBeenCalled();
    expect(screen.getByTestId('pairings-page')).toBeInTheDocument();
  });

  it('未保存入力がある場合、confirm で OK を選ぶと /pairings へ遷移する', async () => {
    setupMocks();
    window.confirm = vi.fn().mockReturnValue(true);
    renderComponent();

    const user = userEvent.setup();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '選手A' })).toBeInTheDocument();
    });

    // 選手Aをクリックして未保存状態にする（setWinnerが呼ばれchangedMatchesに追加）
    await user.click(screen.getByRole('button', { name: '選手A' }));
    await user.click(screen.getByRole('button', { name: /対戦変更/ }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('未保存'));
    expect(screen.getByTestId('pairings-page')).toBeInTheDocument();
  });

  it('未保存入力がある場合、confirm でキャンセルすると遷移しない', async () => {
    setupMocks();
    // window.confirm は beforeEach で false を返す fn に設定済み
    renderComponent();

    const user = userEvent.setup();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '選手A' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '選手A' }));
    await user.click(screen.getByRole('button', { name: /対戦変更/ }));

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('未保存'));
    expect(screen.queryByTestId('pairings-page')).not.toBeInTheDocument();
    expect(locationRef.pathname).toBe('/matches/bulk-input/42');
  });

  it('遷移先 URL に sessionDate / matchNumber / from パラメータが含まれる', async () => {
    setupMocks();
    renderComponent();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /対戦変更/ })).toBeInTheDocument();
    });

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /対戦変更/ }));

    await waitFor(() => {
      expect(locationRef.pathname).toBe('/pairings');
    });
    expect(locationRef.search).toContain('date=2026-06-24');
    expect(locationRef.search).toContain('matchNumber=1');
    expect(locationRef.search).toContain('from=');
    expect(decodeURIComponent(locationRef.search)).toContain('/matches/bulk-input/42');
  });
});
