import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react';

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
vi.mock('../../components/AttendanceRegisterModal', () => ({
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

const LEGEND_SEEN_KEY = 'practiceCalendarLegendSeen';

// 凡例パネルが開いているかどうかは、パネル内固有のラベルの有無で判定する
const isLegendOpen = () => screen.queryByText('空きあり') !== null;

// ローディング完了（凡例ボタンが描画される）まで待つ
const waitForLoaded = () =>
  waitFor(() => {
    expect(
      screen.getByRole('button', { name: '記号の見方を開く' }),
    ).toBeTruthy();
  });

describe('PracticeList 凡例（記号の見方）', () => {
  beforeEach(() => {
    localStorage.clear();
    practiceAPI.getSessionSummaries.mockResolvedValue({ data: [] });
    practiceAPI.getPlayerParticipations.mockResolvedValue({ data: {} });
    practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
      data: { participations: {}, lotteryExecuted: {} },
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('凡例ボタン（記号の見方）が常時表示される', async () => {
    localStorage.setItem(LEGEND_SEEN_KEY, '1'); // 初回自動表示を抑止し、ボタン存在のみ検証
    render(<PracticeList />);
    await waitForLoaded();

    expect(
      screen.getByRole('button', { name: '記号の見方を開く' }),
    ).toBeTruthy();
  });

  it('ボタンをタップすると凡例パネルが開き、再タップで閉じる', async () => {
    localStorage.setItem(LEGEND_SEEN_KEY, '1'); // 自動表示を抑止し、手動開閉のみ検証
    render(<PracticeList />);
    await waitForLoaded();

    // 初期状態は閉じている
    expect(isLegendOpen()).toBe(false);

    const toggle = screen.getByRole('button', { name: '記号の見方を開く' });

    fireEvent.click(toggle);
    // 2グループ（試合の空き状況／あなたの参加状況）の主要ラベルが揃って表示される
    expect(screen.getByText('空きあり')).toBeTruthy();
    expect(screen.getByText('残りわずか')).toBeTruthy();
    expect(screen.getByText('満員')).toBeTruthy();
    expect(screen.getByText('参加確定')).toBeTruthy();
    expect(screen.getByText('キャンセル待ち')).toBeTruthy();

    fireEvent.click(toggle);
    expect(isLegendOpen()).toBe(false);
  });

  it('初回訪問時（localStorage 未設定）はパネルが自動で開き、既読フラグが保存される', async () => {
    // localStorage は beforeEach でクリア済み
    render(<PracticeList />);
    await waitForLoaded();

    expect(isLegendOpen()).toBe(true);
    await waitFor(() => {
      expect(localStorage.getItem(LEGEND_SEEN_KEY)).toBe('1');
    });
  });

  it('ローディング中は既読フラグを保存せず、表示完了後に保存する', async () => {
    // getSessionSummaries の解決を保留してローディング状態を維持する
    let resolveSessions;
    practiceAPI.getSessionSummaries.mockReturnValue(
      new Promise((resolve) => {
        resolveSessions = resolve;
      }),
    );

    render(<PracticeList />);

    // ローディング中（LoadingScreen 表示中）は凡例も既読フラグ保存も発生しない
    expect(screen.getByText('Loading...')).toBeTruthy();
    expect(localStorage.getItem(LEGEND_SEEN_KEY)).toBeNull();

    // データ解決 → ローディング完了
    resolveSessions({ data: [] });

    await waitForLoaded();
    // 表示完了後に自動表示され、このタイミングで初めて既読フラグが保存される
    expect(isLegendOpen()).toBe(true);
    await waitFor(() => {
      expect(localStorage.getItem(LEGEND_SEEN_KEY)).toBe('1');
    });
  });

  it('既読済み（localStorage 設定済み）のときはパネルが自動で開かない', async () => {
    localStorage.setItem(LEGEND_SEEN_KEY, '1');
    render(<PracticeList />);
    await waitForLoaded();

    expect(isLegendOpen()).toBe(false);
  });

  it('パネル外をタップすると閉じる', async () => {
    localStorage.setItem(LEGEND_SEEN_KEY, '1');
    render(<PracticeList />);
    await waitForLoaded();

    fireEvent.click(screen.getByRole('button', { name: '記号の見方を開く' }));
    expect(isLegendOpen()).toBe(true);

    // パネル外（document.body）の mousedown で閉じる
    fireEvent.mouseDown(document.body);
    expect(isLegendOpen()).toBe(false);
  });
});
