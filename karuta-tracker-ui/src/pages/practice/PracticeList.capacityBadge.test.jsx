import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';

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

// 2026-05-22 を「現在」として固定
const FIXED_NOW = new Date('2026-05-22T12:00:00+09:00');

const buildSession = (overrides) => ({
  id: 1,
  sessionDate: '2026-05-10',
  venueName: '会場A',
  organizationId: 1,
  totalMatches: 3,
  capacity: 4,
  ...overrides,
});

describe('PracticeList カレンダー定員バッジ', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
    practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
      data: { participations: {}, lotteryExecuted: {} },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('capacityStatus が AVAILABLE のセッションではバッジが描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ capacityStatus: 'AVAILABLE' })],
    });

    render(<PracticeList />);

    // 会場名が描画されるまで待機（フェッチ完了の確認）
    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    expect(screen.queryByText('残わずか')).toBeNull();
    expect(screen.queryByText('満員')).toBeNull();
  });

  it('capacityStatus が NEARLY_FULL のセッションでは「残わずか」バッジが描画される', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ capacityStatus: 'NEARLY_FULL' })],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('残わずか')).toBeTruthy();
    });
    expect(screen.queryByText('満員')).toBeNull();
  });

  it('capacityStatus が FULL のセッションでは「満員」バッジが描画される', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ capacityStatus: 'FULL' })],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('満員')).toBeTruthy();
    });
    expect(screen.queryByText('残わずか')).toBeNull();
  });

  it('同日に NEARLY_FULL と FULL の2セッションがあると「満員」バッジのみ描画される', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [
        buildSession({ id: 1, capacityStatus: 'NEARLY_FULL', venueName: '会場A' }),
        buildSession({ id: 2, capacityStatus: 'FULL', venueName: '会場B' }),
      ],
    });

    render(<PracticeList />);

    // どちらの会場名も描画される（同日複数セッション）
    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
      expect(screen.getByText('会場B')).toBeTruthy();
    });

    // 最も重い状態 FULL のみ表示
    expect(screen.getByText('満員')).toBeTruthy();
    expect(screen.queryByText('残わずか')).toBeNull();
  });

  it('capacityStatus が未定義（undefined）の場合はバッジが描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ capacityStatus: undefined })],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    expect(screen.queryByText('残わずか')).toBeNull();
    expect(screen.queryByText('満員')).toBeNull();
  });
});
