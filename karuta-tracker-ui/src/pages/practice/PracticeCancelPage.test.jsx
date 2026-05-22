import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockNavigate = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useSearchParams: () => [mockSearchParams, vi.fn()],
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

import { practiceAPI, lotteryAPI } from '../../api';
import PracticeCancelPage from './PracticeCancelPage';

const FIXED_NOW = new Date('2026-05-21T20:00:00+09:00');

describe('PracticeCancelPage UI 統一', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    mockSearchParams = new URLSearchParams();
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

describe('PracticeCancelPage 月ナビ廃止と年月固定表示', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    mockSearchParams = new URLSearchParams();
    practiceAPI.getSessionSummaries.mockResolvedValue({ data: [] });
    practiceAPI.getPlayerParticipationStatus.mockResolvedValue({
      data: { participations: {} },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('月送り（前月/翌月）のボタンを描画しない', async () => {
    render(<PracticeCancelPage />);
    // ローディング完了待ち
    await screen.findByText('どの日の試合をキャンセルしますか？');

    // ナビゲーション領域の戻るボタン以外、月送り目的のボタンは存在しない
    const buttons = screen.getAllByRole('button');
    // 戻るボタン（ナビバー左の ArrowLeft）はあるが、それ以外のIconOnlyな月送りボタンは廃止済み
    const hoverGrayButtons = buttons.filter((b) =>
      (b.className || '').includes('hover:bg-gray-100'),
    );
    expect(hoverGrayButtons.length).toBe(0);
  });

  it('クエリパラメータ未指定のとき現在年月（2026年5月）を表示する', async () => {
    mockSearchParams = new URLSearchParams();
    render(<PracticeCancelPage />);
    expect(await screen.findByText('2026年5月')).toBeInTheDocument();
  });

  it('クエリパラメータ year=2026&month=8 のとき「2026年8月」を表示する', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=8');
    render(<PracticeCancelPage />);
    expect(await screen.findByText('2026年8月')).toBeInTheDocument();
  });

  it('クエリパラメータ year=2027&month=1 のとき「2027年1月」を表示する（年またぎ）', async () => {
    mockSearchParams = new URLSearchParams('year=2027&month=1');
    render(<PracticeCancelPage />);
    expect(await screen.findByText('2027年1月')).toBeInTheDocument();
  });

  it('クエリパラメータ year=2026&month=8 の月でセッション一覧APIが呼ばれる（月変更が起きない）', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=8');
    render(<PracticeCancelPage />);
    await screen.findByText('2026年8月');

    expect(practiceAPI.getSessionSummaries).toHaveBeenCalledWith(2026, 8);
    expect(practiceAPI.getPlayerParticipationStatus).toHaveBeenCalledWith(10, 2026, 8);
  });
});

describe('PracticeCancelPage キャンセルフロー（SaveProgressOverlay）', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
    mockSearchParams = new URLSearchParams();
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

  const selectMatchAndReason = async (user) => {
    const matchCountEl = await screen.findByText('1試合');
    await user.click(matchCountEl.closest('td'));
    await waitFor(() => {
      expect(screen.getByText('第3試合')).toBeInTheDocument();
    });
    await user.click(screen.getByRole('checkbox', { name: /第3試合/ }));
    await user.click(screen.getByLabelText('体調不良'));
  };

  it('キャンセル成功: success オーバーレイ →「カレンダーに戻る」で navigate、alert は呼ばれない', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const confirmStub = vi.fn().mockReturnValue(true);
    const alertStub = vi.fn();
    vi.stubGlobal('confirm', confirmStub);
    vi.stubGlobal('alert', alertStub);
    lotteryAPI.cancelMultiple.mockResolvedValue({ data: {} });

    render(<PracticeCancelPage />);
    await selectMatchAndReason(user);

    await user.click(
      screen.getByRole('button', { name: /キャンセルする/ }),
    );

    expect(confirmStub).toHaveBeenCalled();

    await waitFor(() => {
      expect(screen.getByText('キャンセル処理が完了しました')).toBeInTheDocument();
    });
    expect(alertStub).not.toHaveBeenCalled();
    expect(mockNavigate).not.toHaveBeenCalled();

    const overlay = screen.getByRole('dialog');
    await user.click(within(overlay).getByRole('button', { name: 'カレンダーに戻る' }));
    expect(mockNavigate).toHaveBeenCalledWith('/practice');

    vi.unstubAllGlobals();
  });

  it('キャンセル失敗: error オーバーレイ →「閉じる」で idle、選択状態（試合・理由）を保持', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    lotteryAPI.cancelMultiple.mockRejectedValue({
      response: { data: { message: 'サーバーエラー: 500' } },
    });
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(<PracticeCancelPage />);
    await selectMatchAndReason(user);

    await user.click(
      screen.getByRole('button', { name: /キャンセルする/ }),
    );

    await waitFor(() => {
      expect(screen.getByText('キャンセルに失敗しました')).toBeInTheDocument();
    });
    expect(screen.getByText('サーバーエラー: 500')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '閉じる' }));
    expect(screen.queryByText('キャンセルに失敗しました')).toBeNull();

    // 選択状態は維持されている（試合・理由ともに）
    expect(screen.getByText('第3試合')).toBeInTheDocument();
    expect(screen.getByLabelText('体調不良').checked).toBe(true);
    expect(mockNavigate).not.toHaveBeenCalled();

    vi.unstubAllGlobals();
    consoleErrorSpy.mockRestore();
  });
});
