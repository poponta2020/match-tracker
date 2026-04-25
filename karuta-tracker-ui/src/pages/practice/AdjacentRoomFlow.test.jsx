import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
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
    getSessionSummaries: vi.fn().mockResolvedValue({ data: [] }),
    getById: vi.fn(),
    expandVenue: vi.fn(),
    confirmReservation: vi.fn(),
    getPlayerParticipations: vi.fn().mockResolvedValue({ data: {} }),
    getPlayerParticipationStatus: vi.fn().mockResolvedValue({ data: { participations: {} } }),
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

import { practiceAPI, venueReservationProxyAPI } from '../../api';
import PracticeList from './PracticeList';

const sessionWithAdjacentRoom = {
  id: 1,
  sessionDate: '2026-04-12',
  totalMatches: 3,
  venueId: 3,
  venueName: 'suzuran',
  capacity: 14,
  organizationId: 1,
  participantCount: 10,
  adjacentRoomStatus: {
    adjacentRoomName: 'hamanasu',
    status: 'open',
    available: true,
    expandedVenueId: 7,
    expandedVenueName: 'suzuran-hamanasu',
    expandedCapacity: 24,
  },
  reservationConfirmedAt: null,
  matchParticipants: {},
};

const sessionSummary = {
  id: 1,
  sessionDate: '2026-04-12',
  totalMatches: 3,
  venueName: 'suzuran',
  participantCount: 10,
  organizationId: 1,
};

let openedTab;
let broadcastChannels;
let originalBroadcastChannel;

class MockBroadcastChannel {
  constructor(name) {
    this.name = name;
    this.onmessage = null;
    this.close = vi.fn();
    broadcastChannels.push(this);
  }

  emit(data) {
    return this.onmessage?.({ data });
  }
}

const renderListAndOpenSession = async (user) => {
  render(<PracticeList />);
  const dayCell = await screen.findByText('12');
  await user.click(dayCell);

  await waitFor(() => {
    expect(screen.getByText('隣室を予約')).toBeInTheDocument();
  });
};

beforeEach(() => {
  vi.clearAllMocks();

  practiceAPI.getSessionSummaries.mockResolvedValue({ data: [sessionSummary] });
  practiceAPI.getByYearMonth.mockResolvedValue({ data: [sessionSummary] });
  practiceAPI.getById.mockResolvedValue({ data: sessionWithAdjacentRoom });
  practiceAPI.confirmReservation.mockResolvedValue({
    data: { ...sessionWithAdjacentRoom, reservationConfirmedAt: '2026-04-12T10:00:00' },
  });
  practiceAPI.expandVenue.mockResolvedValue({
    data: {
      ...sessionWithAdjacentRoom,
      venueId: 7,
      venueName: 'suzuran-hamanasu',
      reservationConfirmedAt: '2026-04-12T10:00:00',
    },
  });
  venueReservationProxyAPI.createSession.mockResolvedValue({
    data: {
      proxyToken: 'token-123',
      viewUrl: '/api/venue-reservation-proxy/view?token=token-123',
      venue: 'KADERU',
    },
  });

  openedTab = {
    location: { href: 'about:blank' },
    close: vi.fn(),
  };
  window.open = vi.fn(() => openedTab);
  window.alert = vi.fn();
  window.confirm = vi.fn().mockReturnValue(true);

  broadcastChannels = [];
  originalBroadcastChannel = window.BroadcastChannel;
  window.BroadcastChannel = MockBroadcastChannel;
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  if (originalBroadcastChannel === undefined) {
    delete window.BroadcastChannel;
  } else {
    window.BroadcastChannel = originalBroadcastChannel;
  }
});

describe('adjacent room reservation proxy flow', () => {
  it('creates a proxy session, opens the proxy view, then keeps manual confirmation available', async () => {
    const user = userEvent.setup();
    await renderListAndOpenSession(user);

    await user.click(screen.getByText('隣室を予約'));

    await waitFor(() => {
      expect(window.open).toHaveBeenCalledWith('about:blank', '_blank');
      expect(venueReservationProxyAPI.createSession).toHaveBeenCalledWith({
        venue: 'KADERU',
        practiceSessionId: 1,
        roomName: 'hamanasu',
        date: '2026-04-12',
        slotIndex: 2,
      });
      expect(openedTab.location.href).toBe('http://localhost:8080/api/venue-reservation-proxy/view?token=token-123');
      expect(screen.getByText('予約完了を報告')).toBeInTheDocument();
    });
    expect(practiceAPI.confirmReservation).not.toHaveBeenCalled();

    await user.click(screen.getByText('予約完了を報告'));

    await waitFor(() => {
      expect(practiceAPI.confirmReservation).toHaveBeenCalledWith(1);
      expect(screen.getByText('会場を拡張')).toBeInTheDocument();
    });
    expect(screen.queryByText('隣室を予約')).not.toBeInTheDocument();
  });

  it('keeps the reservation button when the backend rejects the venue as unsupported', async () => {
    venueReservationProxyAPI.createSession.mockRejectedValue({
      response: { data: { errorCode: 'VENUE_NOT_SUPPORTED', message: 'unsupported venue' } },
    });

    const user = userEvent.setup();
    await renderListAndOpenSession(user);

    await user.click(screen.getByText('隣室を予約'));

    await waitFor(() => {
      expect(openedTab.close).toHaveBeenCalled();
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('プロキシ予約に対応していません'));
      expect(screen.getByText('隣室を予約')).toBeInTheDocument();
    });
    expect(screen.queryByText('予約完了を報告')).not.toBeInTheDocument();
  });

  it('closes the proxy tab and shows the backend message on login failure', async () => {
    venueReservationProxyAPI.createSession.mockRejectedValue({
      response: { data: { errorCode: 'LOGIN_FAILED', message: 'ログインに失敗しました' } },
    });

    const user = userEvent.setup();
    await renderListAndOpenSession(user);

    await user.click(screen.getByText('隣室を予約'));

    await waitFor(() => {
      expect(openedTab.close).toHaveBeenCalled();
      expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('ログインに失敗しました'));
      expect(screen.getByText('隣室を予約')).toBeInTheDocument();
    });
    expect(screen.queryByText('予約完了を報告')).not.toBeInTheDocument();
  });

  it('refreshes the selected session when BroadcastChannel receives reservation-completed', async () => {
    const completedSession = {
      ...sessionWithAdjacentRoom,
      reservationConfirmedAt: '2026-04-12T10:00:00',
    };
    practiceAPI.getById
      .mockResolvedValueOnce({ data: sessionWithAdjacentRoom })
      .mockResolvedValueOnce({ data: completedSession });

    const user = userEvent.setup();
    await renderListAndOpenSession(user);

    await act(async () => {
      await broadcastChannels[0].emit({
        type: 'reservation-completed',
        practiceSessionId: 1,
        venue: 'KADERU',
        token: 'token-123',
      });
    });

    await waitFor(() => {
      expect(practiceAPI.getById).toHaveBeenLastCalledWith(1);
      expect(screen.getByText('会場を拡張')).toBeInTheDocument();
    });
  });

  it('shows the expand button immediately when reservationConfirmedAt is already set', async () => {
    practiceAPI.getById.mockResolvedValue({
      data: { ...sessionWithAdjacentRoom, reservationConfirmedAt: '2026-04-12T10:00:00' },
    });

    const user = userEvent.setup();
    render(<PracticeList />);

    const dayCell = await screen.findByText('12');
    await user.click(dayCell);

    await waitFor(() => {
      expect(screen.getByText('会場を拡張')).toBeInTheDocument();
    });
    expect(screen.queryByText('隣室を予約')).not.toBeInTheDocument();
  });
});
