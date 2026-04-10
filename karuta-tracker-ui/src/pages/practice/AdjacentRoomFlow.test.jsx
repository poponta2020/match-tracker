import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// --- Mocks ---

const mockNavigate = vi.fn();
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
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
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

vi.mock('../../api/kaderu', () => ({
  default: {
    openReserve: vi.fn(),
  },
}));

vi.mock('../../utils/auth', () => ({
  isSuperAdmin: () => true,
  isAdmin: () => true,
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'テスト管理者', role: 'SUPER_ADMIN' },
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
import kaderuAPI from '../../api/kaderu';
import PracticeList from './PracticeList';

// セッション詳細データ（隣室空きあり）
const sessionWithAdjacentRoom = {
  id: 1,
  sessionDate: '2026-04-12',
  totalMatches: 3,
  venueId: 3,
  venueName: 'すずらん',
  capacity: 14,
  organizationId: 1,
  participantCount: 10,
  adjacentRoomStatus: {
    adjacentRoomName: 'はまなす',
    status: '○',
    available: true,
    expandedVenueId: 7,
    expandedVenueName: 'すずらん・はまなす',
    expandedCapacity: 24,
  },
  reservationConfirmedAt: null,
  matchParticipants: {},
};

// サマリーデータ
const sessionSummary = {
  id: 1,
  sessionDate: '2026-04-12',
  totalMatches: 3,
  venueName: 'すずらん',
  participantCount: 10,
  organizationId: 1,
};

beforeEach(() => {
  vi.clearAllMocks();
  // カレンダーに表示されるセッション
  practiceAPI.getSessionSummaries.mockResolvedValue({ data: [sessionSummary] });
  practiceAPI.getByYearMonth.mockResolvedValue({ data: [sessionSummary] });
  // モーダル用の詳細データ
  practiceAPI.getById.mockResolvedValue({ data: sessionWithAdjacentRoom });
  // happy-domでは window.alert/confirm が未定義のため直接設定
  window.alert = vi.fn();
  window.confirm = vi.fn().mockReturnValue(true);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

/**
 * カレンダーの日付をクリックしてモーダルを開くヘルパー
 */
const openSessionModal = async (user) => {
  // カレンダーの12日をクリック
  const dayCell = await screen.findByText('12');
  await user.click(dayCell);
  // モーダルが開くのを待つ
  await waitFor(() => {
    expect(screen.getByText('隣室を予約')).toBeInTheDocument();
  });
};

describe('隣室予約→会場拡張フロー', () => {
  it('openReserve成功時: 隣室を予約→confirmReservation→会場を拡張ボタン表示', async () => {
    kaderuAPI.openReserve.mockResolvedValue({ data: { success: true } });
    practiceAPI.confirmReservation.mockResolvedValue({
      data: { ...sessionWithAdjacentRoom, reservationConfirmedAt: '2026-04-12T10:00:00' },
    });

    const user = userEvent.setup();
    render(<PracticeList />);
    await openSessionModal(user);

    // 「隣室を予約」ボタンをクリック
    await user.click(screen.getByText('隣室を予約'));

    // openReserve → confirmReservation が呼ばれる
    await waitFor(() => {
      expect(kaderuAPI.openReserve).toHaveBeenCalledWith('はまなす', '2026-04-12');
      expect(practiceAPI.confirmReservation).toHaveBeenCalledWith(1);
    });

    // 「会場を拡張」ボタンが表示される
    await waitFor(() => {
      expect(screen.getByText('会場を拡張')).toBeInTheDocument();
    });
    expect(screen.queryByText('隣室を予約')).not.toBeInTheDocument();
  });

  it('DISABLED時: 隣室を予約→予約完了を報告ボタン表示→クリック→会場を拡張ボタン表示', async () => {
    kaderuAPI.openReserve.mockRejectedValue({
      response: { data: { errorCode: 'DISABLED' } },
    });
    practiceAPI.confirmReservation.mockResolvedValue({
      data: { ...sessionWithAdjacentRoom, reservationConfirmedAt: '2026-04-12T10:00:00' },
    });

    const user = userEvent.setup();
    render(<PracticeList />);
    await openSessionModal(user);

    // 「隣室を予約」ボタンをクリック
    await user.click(screen.getByText('隣室を予約'));

    // alertが表示され、「予約完了を報告」ボタンが表示される
    await waitFor(() => {
      expect(window.alert).toHaveBeenCalledWith(
        expect.stringContaining('自動予約機能は現在利用できません')
      );
      expect(screen.getByText('予約完了を報告')).toBeInTheDocument();
    });
    // この時点ではconfirmReservationはまだ呼ばれない
    expect(practiceAPI.confirmReservation).not.toHaveBeenCalled();

    // 「予約完了を報告」ボタンをクリック
    await user.click(screen.getByText('予約完了を報告'));

    // confirmReservation が呼ばれ、「会場を拡張」ボタンに変わる
    await waitFor(() => {
      expect(practiceAPI.confirmReservation).toHaveBeenCalledWith(1);
      expect(screen.getByText('会場を拡張')).toBeInTheDocument();
    });
  });

  it('その他エラー時: エラーメッセージが表示され、隣室を予約ボタンのまま', async () => {
    kaderuAPI.openReserve.mockRejectedValue({
      response: { data: { message: 'サーバーエラー' } },
    });

    const user = userEvent.setup();
    render(<PracticeList />);
    await openSessionModal(user);

    // 「隣室を予約」ボタンをクリック
    await user.click(screen.getByText('隣室を予約'));

    // エラーalertが表示される
    await waitFor(() => {
      expect(window.alert).toHaveBeenCalledWith(
        expect.stringContaining('サーバーエラー')
      );
    });

    // ボタンは「隣室を予約」のまま
    expect(screen.getByText('隣室を予約')).toBeInTheDocument();
    expect(screen.queryByText('会場を拡張')).not.toBeInTheDocument();
    expect(screen.queryByText('予約完了を報告')).not.toBeInTheDocument();
  });

  it('ページリロード時: reservationConfirmedAtがある場合は会場を拡張ボタンが表示される', async () => {
    // getByIdが予約確認済みデータを返す
    practiceAPI.getById.mockResolvedValue({
      data: { ...sessionWithAdjacentRoom, reservationConfirmedAt: '2026-04-12T10:00:00' },
    });

    const user = userEvent.setup();
    render(<PracticeList />);

    // カレンダーの12日をクリック
    const dayCell = await screen.findByText('12');
    await user.click(dayCell);

    // 初期表示から「会場を拡張」ボタンが表示されている（予約確認済みのため）
    await waitFor(() => {
      expect(screen.getByText('会場を拡張')).toBeInTheDocument();
    });
    expect(screen.queryByText('隣室を予約')).not.toBeInTheDocument();
  });
});
