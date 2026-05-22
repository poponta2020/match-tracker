import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';

const mockNavigate = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useSearchParams: () => [mockSearchParams, vi.fn()],
}));

vi.mock('../../api', () => ({
  practiceAPI: {
    getByYearMonth: vi.fn(),
    getPlayerParticipations: vi.fn(),
    getPlayerParticipationStatus: vi.fn(),
    registerParticipations: vi.fn(),
  },
  systemSettingsAPI: {
    getDeadline: vi.fn().mockResolvedValue({ data: null }),
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: vi.fn().mockResolvedValue({ data: [] }),
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

import { practiceAPI } from '../../api';
import PracticeParticipation from './PracticeParticipation';

// 2026-05-21 を「現在」として固定
const FIXED_NOW = new Date('2026-05-21T12:00:00+09:00');

// テスト用のセッション・参加データを返すヘルパー
const setupAPI = ({
  sessions,
  participations = {},
  lotteryExecuted = {},
  participationStatuses = {},
  beforeDeadline = true,
  hasAnyExecutedLotteryInMonth,
}) => {
  // hasAnyExecutedLotteryInMonth 未指定時は lotteryExecuted の値から導出
  // （サーバー側の挙動と整合：セッション単位 true があれば月単位も true）
  const resolvedHasAny = hasAnyExecutedLotteryInMonth !== undefined
    ? hasAnyExecutedLotteryInMonth
    : Object.values(lotteryExecuted).some(Boolean);
  practiceAPI.getByYearMonth.mockResolvedValue({ data: sessions });
  practiceAPI.getPlayerParticipations.mockResolvedValue({ data: participations });
  practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
    data: {
      participations: participationStatuses,
      lotteryExecuted,
      beforeDeadline,
      hasAnyExecutedLotteryInMonth: resolvedHasAny,
    },
  });
};

describe('PracticeParticipation 当月扱い／来月扱いのチェック外し挙動', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('当月扱い: 既存登録（initial に含まれる試合）のチェックボックスは disabled', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=5'); // 現在年月＝当月扱い
    setupAPI({
      sessions: [
        {
          id: 100,
          sessionDate: '2026-05-25',
          totalMatches: 3,
          venueName: '東区民センター',
          organizationId: 1,
        },
      ],
      participations: { 100: [1, 2] }, // 第1・第2試合に登録済み
      lotteryExecuted: { 100: false },
      beforeDeadline: true,
    });

    render(<PracticeParticipation />);

    // ローディング完了待ち
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');
    // 第1・第2試合は既存登録 → disabled
    expect(checkboxes[0]).toBeDisabled();
    expect(checkboxes[1]).toBeDisabled();
    // 第3試合は未登録 → disabled でない（追加チェック可）
    expect(checkboxes[2]).not.toBeDisabled();
  });

  it('当月扱い: 未登録試合（initial に含まれない試合）の追加チェックは可能', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=5');
    setupAPI({
      sessions: [
        {
          id: 100,
          sessionDate: '2026-05-25',
          totalMatches: 3,
          venueName: '東区民センター',
          organizationId: 1,
        },
      ],
      participations: { 100: [] },
      lotteryExecuted: { 100: false },
      beforeDeadline: true,
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes).toHaveLength(3);
    checkboxes.forEach((cb) => expect(cb).not.toBeDisabled());
  });

  it('来月扱い（抽選確定済みなし）: 既存登録のチェックボックスも disabled でない（チェック外し可能）', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=6'); // 翌月＝来月扱い
    setupAPI({
      sessions: [
        {
          id: 200,
          sessionDate: '2026-06-15',
          totalMatches: 3,
          venueName: '東区民センター',
          organizationId: 1,
        },
      ],
      participations: { 200: [1, 2] },
      lotteryExecuted: { 200: false },
      beforeDeadline: true,
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');
    checkboxes.forEach((cb) => expect(cb).not.toBeDisabled());
  });

  it('来月扱いだが抽選確定済みセッションが混在: 当月扱いに昇格して initial 含む試合がロックされる', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=6');
    setupAPI({
      sessions: [
        {
          id: 300,
          sessionDate: '2026-06-10',
          totalMatches: 2,
          venueName: '北大体育館',
          organizationId: 2,
        },
        {
          id: 400,
          sessionDate: '2026-06-15',
          totalMatches: 2,
          venueName: 'わすら会場',
          organizationId: 3,
        },
      ],
      participations: { 400: [1] }, // 400 セッションに登録済み
      // 300は抽選確定済み → 月全体が当月扱いに昇格
      lotteryExecuted: { 300: true, 400: false },
      participationStatuses: {
        300: [
          { matchNumber: 1, status: 'WON' },
          { matchNumber: 2, status: 'WAITLISTED', waitlistNumber: 3 },
        ],
      },
      beforeDeadline: true,
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    // 抽選確定済みセッション (300) はステータス表示（チェックボックスなし）
    expect(screen.getByText('当選')).toBeInTheDocument();
    expect(screen.getByText('待ち')).toBeInTheDocument();

    // 抽選なしセッション (400) のチェックボックスは描画される
    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes).toHaveLength(2); // セッション400の2試合分

    // 当月扱いに昇格しているので、initial に含まれる第1試合は disabled
    expect(checkboxes[0]).toBeDisabled();
    // 第2試合は未登録 → 追加チェック可
    expect(checkboxes[1]).not.toBeDisabled();
  });

  it('抽選確定済みセッション: ステータスバッジを表示しチェックボックスを描画しない', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=6');
    setupAPI({
      sessions: [
        {
          id: 500,
          sessionDate: '2026-06-10',
          totalMatches: 2,
          venueName: '北大体育館',
          organizationId: 2,
        },
      ],
      participations: { 500: [1] },
      lotteryExecuted: { 500: true },
      participationStatuses: {
        500: [
          { matchNumber: 1, status: 'WON' },
        ],
      },
      beforeDeadline: true,
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    // 当選バッジ表示
    expect(screen.getByText('当選')).toBeInTheDocument();
    // 抽選確定済みセッション内のセルはチェックボックスを持たない
    const checkboxes = screen.queryAllByRole('checkbox');
    expect(checkboxes).toHaveLength(0);
  });

  it('来月扱い: 締切後（beforeDeadline=false）なら initial 含む試合はロックされる（既存仕様維持）', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=6');
    setupAPI({
      sessions: [
        {
          id: 600,
          sessionDate: '2026-06-15',
          totalMatches: 2,
          venueName: 'わすら会場',
          organizationId: 3,
        },
      ],
      participations: { 600: [1] },
      lotteryExecuted: { 600: false },
      beforeDeadline: false, // 締切後
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');
    expect(checkboxes[0]).toBeDisabled(); // 第1試合は initial 含む → disabled
    expect(checkboxes[1]).not.toBeDisabled(); // 第2試合は未登録 → 追加可能
  });
});
