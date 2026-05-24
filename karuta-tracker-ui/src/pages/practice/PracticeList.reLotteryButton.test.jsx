import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockNavigate = vi.fn();
const mockSetSearchParams = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useSearchParams: () => [new URLSearchParams(), mockSetSearchParams],
}));

vi.mock('../../api', () => ({
  practiceAPI: {
    getByYearMonth: vi.fn().mockResolvedValue({ data: [] }),
    getSessionSummaries: vi.fn(),
    getById: vi.fn(),
    expandVenue: vi.fn(),
    confirmReservation: vi.fn(),
    getPlayerParticipations: vi.fn().mockResolvedValue({ data: {} }),
    getPlayerParticipationStatus: vi.fn().mockResolvedValue({
      data: { participations: {}, lotteryExecuted: {} },
    }),
  },
  lotteryAPI: {},
  venueReservationProxyAPI: {
    createSession: vi.fn(),
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

vi.mock('../../utils/auth', () => ({
  isSuperAdmin: () => true,
  isAdmin: () => true,
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'Admin', role: 'SUPER_ADMIN' },
  }),
}));

vi.mock('../../components/MatchParticipantsEditModal', () => ({
  default: () => null,
}));
vi.mock('../../components/PlayerChip', () => ({
  default: ({ player }) => <span>{player?.name}</span>,
}));
vi.mock('../../components/YearMonthPicker', () => ({
  default: () => null,
}));
vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

import { practiceAPI } from '../../api';
import PracticeList from './PracticeList';

// 2026-05-22 を「現在」として固定
const FIXED_NOW = new Date('2026-05-22T12:00:00+09:00');

// 今日より未来の練習セッション（同月内 = 5/30）
const futureSession = {
  id: 1,
  sessionDate: '2026-05-30',
  totalMatches: 3,
  venueId: 1,
  venueName: 'テスト会場',
  capacity: 14,
  organizationId: 1,
  participantCount: 10,
  reservationConfirmedAt: null,
  matchParticipants: {},
};

const futureSessionSummary = {
  id: 1,
  sessionDate: '2026-05-30',
  totalMatches: 3,
  venueName: 'テスト会場',
  participantCount: 10,
  organizationId: 1,
};

describe('PracticeList カレンダー練習日ポップアップ:再抽選ボタン', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    vi.clearAllMocks();
    practiceAPI.getSessionSummaries.mockResolvedValue({ data: [futureSessionSummary] });
    practiceAPI.getById.mockResolvedValue({ data: futureSession });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
  });

  it('管理者で未来日セッションのポップアップを開いても「再抽選」ボタンは表示されない', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<PracticeList />);

    // カレンダーから 30 日のセルをクリック
    const dayCell = await screen.findByText('30');
    await user.click(dayCell);

    // ポップアップが開いたことを「削除」ボタン（SUPER_ADMIN のみ表示）の出現で確認
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /^削除$/ })).toBeInTheDocument();
    });

    // 「再抽選」ボタンが UI 上に存在しないこと（本PRの主目的）
    expect(screen.queryByRole('button', { name: /再抽選/ })).toBeNull();
  });
});
