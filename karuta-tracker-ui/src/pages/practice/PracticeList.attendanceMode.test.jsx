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
    getPlayerParticipations: vi.fn(),
    getPlayerParticipationStatus: vi.fn(),
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
  isSuperAdmin: () => false,
  isAdmin: () => false,
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'Player', role: 'PLAYER' },
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

// 2026-05-22 を「現在」として固定（タスク2の結果ファイル時点）
const FIXED_NOW = new Date('2026-05-22T12:00:00+09:00');

describe('PracticeList × resolveAttendanceMode の連携', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    practiceAPI.getSessionSummaries.mockResolvedValue({ data: [] });
    practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('当月(2026-05)で表示時にFABが描画される', async () => {
    practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
      data: { participations: {}, lotteryExecuted: {} },
    });

    render(<PracticeList />);

    expect(await screen.findByRole('button', { name: /出欠登録/ })).toBeTruthy();
  });

  it('FABを押すと AttendanceRegisterModal が両ボタン（参加登録/キャンセル登録）で開く（当月扱い）', async () => {
    practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
      data: { participations: {}, lotteryExecuted: {} },
    });

    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<PracticeList />);

    await user.click(await screen.findByRole('button', { name: /出欠登録/ }));

    expect(screen.getByRole('button', { name: /参加登録/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /キャンセル登録/ })).toBeTruthy();
  });

  it('getPlayerParticipationStatus が失敗するとエラー表示になり FAB を非表示にする', async () => {
    practiceAPI.getPlayerParticipationStatus.mockRejectedValue(new Error('boom'));

    render(<PracticeList />);

    // エラーメッセージ表示まで待つ
    await waitFor(() => {
      expect(screen.getByText('練習記録の取得に失敗しました')).toBeInTheDocument();
    });

    // FAB は描画されない（誤った来月扱い判定で操作させないため）
    expect(screen.queryByRole('button', { name: /出欠登録/ })).toBeNull();
  });
});
