import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useSearchParams: () => [new URLSearchParams(), vi.fn()],
}));

vi.mock('../../api', () => ({
  practiceAPI: {
    getSessionSummaries: vi.fn(),
    getPlayerParticipationStatus: vi.fn(),
  },
  lotteryAPI: {
    cancelMultiple: vi.fn(),
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 10, name: 'テスト選手', role: 'PLAYER' },
  }),
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

vi.mock('../../components/YearMonthPicker', () => ({
  default: () => null,
}));

import { practiceAPI } from '../../api';
import PracticeCancelPage from './PracticeCancelPage';

const FIXED_NOW = new Date('2026-05-21T20:00:00+09:00');

describe('PracticeCancelPage UI 統一', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [
        {
          id: 945,
          sessionDate: '2026-05-25',
          totalMatches: 3,
          venueName: 'テスト会場',
          capacity: 24,
        },
      ],
    });
    practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
      data: {
        participations: {
          945: [
            { participantId: 21415, matchNumber: 3, status: 'PENDING' },
          ],
        },
      },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('PENDING のみのキャンセル可能日が赤系で統一表示される', async () => {
    render(<PracticeCancelPage />);

    const matchCountEl = await screen.findByText('1試合');
    expect(matchCountEl.className).toContain('text-red-500');
    expect(matchCountEl.className).not.toContain('text-blue-600');

    const cell = matchCountEl.closest('td');
    expect(cell.className).toContain('bg-[#fce4e4]');
    expect(cell.className).not.toContain('bg-[#e4ecfc]');
  });

  it('試合選択リストに「当選」「申込」バッジを表示しない', async () => {
    const user = userEvent.setup();
    render(<PracticeCancelPage />);

    const matchCountEl = await screen.findByText('1試合');
    await user.click(matchCountEl.closest('td'));

    await waitFor(() => {
      expect(screen.getByText('第3試合')).toBeInTheDocument();
    });
    expect(screen.queryByText('当選')).not.toBeInTheDocument();
    expect(screen.queryByText('申込（抽選前）')).not.toBeInTheDocument();
  });
});
