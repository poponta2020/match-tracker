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

describe('PracticeList 試合別ステータスグリッド', () => {
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

  it('matchCapacityStatuses が [AVAILABLE, NEARLY_FULL, FULL] のとき ○ △ × が順に描画される', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [
        buildSession({
          matchCapacityStatuses: ['AVAILABLE', 'NEARLY_FULL', 'FULL'],
        }),
      ],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    const grids = screen.getAllByTestId('match-status-grid');
    expect(grids).toHaveLength(1);
    const grid = grids[0];
    const symbols = grid.querySelectorAll('span');
    expect(symbols).toHaveLength(3);
    expect(symbols[0].textContent).toBe('○');
    expect(symbols[0].className).toContain('text-green-600');
    expect(symbols[1].textContent).toBe('△');
    expect(symbols[1].className).toContain('text-orange-500');
    expect(symbols[2].textContent).toBe('×');
    expect(symbols[2].className).toContain('text-red-600');
  });

  it('7試合の場合は3列固定 grid で 3+3+1 のレイアウトとして描画される', async () => {
    const statuses = [
      'AVAILABLE',
      'AVAILABLE',
      'AVAILABLE',
      'NEARLY_FULL',
      'NEARLY_FULL',
      'FULL',
      'FULL',
    ];
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [
        buildSession({
          totalMatches: 7,
          matchCapacityStatuses: statuses,
        }),
      ],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    const grid = screen.getByTestId('match-status-grid');
    // 3列固定 grid（記号は順序通り並び、3列レイアウトにより自動で 3+3+1 行に折り返される）
    expect(grid.className).toContain('grid-cols-3');
    const symbols = grid.querySelectorAll('span');
    expect(symbols).toHaveLength(7);
    expect(Array.from(symbols).map((s) => s.textContent)).toEqual([
      '○', '○', '○', '△', '△', '×', '×',
    ]);
  });

  it('同日に2セッションあるときグリッドは描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [
        buildSession({
          id: 1,
          venueName: '会場A',
          matchCapacityStatuses: ['AVAILABLE', 'AVAILABLE', 'AVAILABLE'],
        }),
        buildSession({
          id: 2,
          venueName: '会場B',
          matchCapacityStatuses: ['FULL', 'FULL', 'FULL'],
        }),
      ],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
      expect(screen.getByText('会場B')).toBeTruthy();
    });

    expect(screen.queryAllByTestId('match-status-grid')).toHaveLength(0);
  });

  it('matchCapacityStatuses が null のときグリッドは描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ matchCapacityStatuses: null })],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    expect(screen.queryAllByTestId('match-status-grid')).toHaveLength(0);
  });

  it('matchCapacityStatuses が未定義（capacity 未設定セッション相当）のときグリッドは描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ capacity: null, matchCapacityStatuses: undefined })],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    expect(screen.queryAllByTestId('match-status-grid')).toHaveLength(0);
  });

  it('matchCapacityStatuses が空配列のときグリッドは描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [buildSession({ matchCapacityStatuses: [] })],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    expect(screen.queryAllByTestId('match-status-grid')).toHaveLength(0);
  });

  it('matchCapacityStatuses に不正値が含まれるときグリッドは描画されない', async () => {
    practiceAPI.getSessionSummaries.mockResolvedValue({
      data: [
        buildSession({ matchCapacityStatuses: ['AVAILABLE', 'UNKNOWN', 'FULL'] }),
      ],
    });

    render(<PracticeList />);

    await waitFor(() => {
      expect(screen.getByText('会場A')).toBeTruthy();
    });

    expect(screen.queryAllByTestId('match-status-grid')).toHaveLength(0);
  });
});
