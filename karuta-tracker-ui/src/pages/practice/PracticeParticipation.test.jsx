import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

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
    getPlayerOrganizations: vi.fn().mockResolvedValue({ data: [] }),
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

import { practiceAPI, systemSettingsAPI } from '../../api';
import { organizationAPI } from '../../api/organizations';
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

  it('締切設定を持つ非hokudai団体について、その団体略称付きで締切バナーを表示する（団体決め打ち排除）', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=5');
    setupAPI({
      sessions: [
        { id: 100, sessionDate: '2026-05-25', totalMatches: 3, venueName: '東区民センター', organizationId: 55 },
      ],
      participations: { 100: [] },
      lotteryExecuted: { 100: false },
      beforeDeadline: true,
    });
    organizationAPI.getPlayerOrganizations.mockResolvedValueOnce({
      data: [{ id: 55, code: 'myclub', name: '○○会' }],
    });
    systemSettingsAPI.getDeadline.mockResolvedValueOnce({
      data: { deadline: '2026-05-25T23:59:00+09:00', noDeadline: false },
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    // 非hokudai団体でも締切バナーが表示され、ラベルはその団体の略称（name先頭2文字）
    expect(await screen.findByText(/締め切り:.*（○○）/)).toBeInTheDocument();
    // 北大決め打ちの残骸が出ないこと
    expect(screen.queryByText(/（北大）/)).toBeNull();
  });

  it('締切設定を持つ hokudai は従来どおり「北大」ラベルで締切バナーを表示する（非退行）', async () => {
    mockSearchParams = new URLSearchParams('year=2026&month=5');
    setupAPI({
      sessions: [
        { id: 100, sessionDate: '2026-05-25', totalMatches: 3, venueName: '北大体育館', organizationId: 66 },
      ],
      participations: { 100: [] },
      lotteryExecuted: { 100: false },
      beforeDeadline: true,
    });
    organizationAPI.getPlayerOrganizations.mockResolvedValueOnce({
      data: [{ id: 66, code: 'hokudai', name: '北海道大学かるた会' }],
    });
    systemSettingsAPI.getDeadline.mockResolvedValueOnce({
      data: { deadline: '2026-05-25T23:59:00+09:00', noDeadline: false },
    });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    expect(await screen.findByText(/締め切り:.*（北大）/)).toBeInTheDocument();
  });
});

describe('PracticeParticipation 保存フロー（SaveProgressOverlay）', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(FIXED_NOW);
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
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
    vi.clearAllMocks();
  });

  it('保存成功: saving → success オーバーレイ表示。「カレンダーに戻る」押下まで navigate しない（自動遷移なし）', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    practiceAPI.registerParticipations.mockResolvedValue({ data: {} });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[0]);

    const saveButton = await screen.findByRole('button', { name: /保存する/ });
    await user.click(saveButton);

    await waitFor(() => {
      expect(screen.getByText('参加登録を保存しました')).toBeInTheDocument();
    });
    expect(mockNavigate).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'カレンダーに戻る' }));
    expect(mockNavigate).toHaveBeenCalledWith('/practice');
  });

  it('再レンダー前に同一セッションの複数チェックを連続クリックしても両方とも保持される (stale closure 回帰)', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    practiceAPI.registerParticipations.mockResolvedValue({ data: {} });

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');

    // setParticipations をオブジェクト直接渡しで書くと、同一 act 内の
    // 2 つ目のクリックが 1 つ目の state 更新を spread で上書きしてしまう。
    // 関数形式 (prev => ...) で書かれていればこのバッチでも両方残る。
    act(() => {
      fireEvent.click(checkboxes[0]);
      fireEvent.click(checkboxes[1]);
    });

    expect(checkboxes[0]).toBeChecked();
    expect(checkboxes[1]).toBeChecked();

    const saveButton = await screen.findByRole('button', { name: /保存する/ });
    await user.click(saveButton);

    await waitFor(() => {
      expect(practiceAPI.registerParticipations).toHaveBeenCalled();
    });

    const callArgs = practiceAPI.registerParticipations.mock.calls[0][0];
    expect(callArgs.participations).toEqual(
      expect.arrayContaining([
        { sessionId: 100, matchNumber: 1 },
        { sessionId: 100, matchNumber: 2 },
      ]),
    );
    expect(callArgs.participations).toHaveLength(2);
  });

  it('保存失敗: error オーバーレイにサーバーメッセージ表示 →「閉じる」で idle、チェック状態を保持', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    practiceAPI.registerParticipations.mockRejectedValue({
      response: { data: { message: 'サーバーエラー: 500' } },
    });
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(<PracticeParticipation />);
    await waitFor(() => expect(screen.queryByText('Loading...')).toBeNull());

    const checkboxes = screen.getAllByRole('checkbox');
    await user.click(checkboxes[0]);
    expect(checkboxes[0]).toBeChecked();

    const saveButton = await screen.findByRole('button', { name: /保存する/ });
    await user.click(saveButton);

    await waitFor(() => {
      expect(screen.getByText('保存に失敗しました')).toBeInTheDocument();
    });
    expect(screen.getByText('サーバーエラー: 500')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '閉じる' }));
    expect(screen.queryByText('保存に失敗しました')).toBeNull();
    expect(checkboxes[0]).toBeChecked();
    expect(mockNavigate).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});
