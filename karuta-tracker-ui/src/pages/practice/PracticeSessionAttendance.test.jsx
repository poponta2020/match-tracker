import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mockNavigate = vi.fn();
let mockSearchParams = new URLSearchParams('sessionId=945');

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useSearchParams: () => [mockSearchParams, vi.fn()],
}));

vi.mock('../../api', () => ({
  practiceAPI: {
    getById: vi.fn(),
    getPlayerParticipations: vi.fn(),
    getPlayerParticipationStatus: vi.fn(),
    registerParticipations: vi.fn(),
  },
  lotteryAPI: {
    cancelMultiple: vi.fn(),
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: { getAll: vi.fn() },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: { id: 10, name: 'テスト選手', role: 'PLAYER' } }),
}));

vi.mock('../../components/LoadingScreen', () => ({ default: () => <div>Loading...</div> }));

import { practiceAPI, lotteryAPI } from '../../api';
import { organizationAPI } from '../../api/organizations';
import PracticeSessionAttendance from './PracticeSessionAttendance';

const defaultSession = (o = {}) => ({
  id: 945,
  sessionDate: '2026-05-25',
  totalMatches: 3,
  capacity: 24,
  venueName: 'テスト会場',
  organizationId: 1,
  matchParticipantCounts: { 1: 5, 2: 6, 3: 7 },
  venueSchedules: [
    { matchNumber: 1, startTime: '10:00:00', endTime: '11:00:00' },
    { matchNumber: 2, startTime: '11:00:00', endTime: '12:00:00' },
    { matchNumber: 3, startTime: '12:00:00', endTime: '13:00:00' },
  ],
  densukeDeletionCandidateMatchNumbers: [],
  ...o,
});

const defaultStatus = () => ({
  participations: { 945: [{ matchNumber: 1, status: 'PENDING', participantId: 555 }] },
  version: 7,
  lotteryExecuted: {},
  hasAnyExecutedLotteryInMonth: false,
});

const configure = ({ session, monthParticipations, statusData, org } = {}) => {
  practiceAPI.getById.mockResolvedValue({ data: session ?? defaultSession() });
  practiceAPI.getPlayerParticipations.mockResolvedValue({ data: monthParticipations ?? { 945: [1], 900: [2] } });
  practiceAPI.getPlayerParticipationStatus.mockResolvedValue({ data: statusData ?? defaultStatus() });
  practiceAPI.registerParticipations.mockResolvedValue({ data: {} });
  lotteryAPI.cancelMultiple.mockResolvedValue({ data: {} });
  organizationAPI.getAll.mockResolvedValue({
    data: [org ?? { id: 1, name: 'わすら', color: '#123456', deadlineType: 'FIXED' }],
  });
};

const registerSection = () => screen.getByText('参加する試合').closest('section');
const cancelSection = () => screen.getByText('参加をキャンセル').closest('section');

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-05-21T09:00:00Z'));
  mockSearchParams = new URLSearchParams('sessionId=945');
  // happy-dom は window.confirm を実装しないため明示的に定義
  window.confirm = vi.fn(() => true);
  configure();
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.clearAllMocks();
});

describe('PracticeSessionAttendance 上部バー・団体（AC-17/AC-2）', () => {
  it('上部バーに M/D(曜) と会場名、団体カラーで団体名を表示する', async () => {
    render(<PracticeSessionAttendance />);
    const heading = await screen.findByRole('heading', { level: 1 });
    expect(heading.textContent).toContain('5/25');
    expect(heading.textContent).toContain('テスト会場');
    expect(screen.getByText('わすら')).toBeInTheDocument();
  });

  it('対象セッション1件のみ表示し参加とキャンセルを同一画面に提供する', async () => {
    render(<PracticeSessionAttendance />);
    expect(await screen.findByText('参加する試合')).toBeInTheDocument();
    expect(screen.getByText('参加をキャンセル')).toBeInTheDocument();
  });
});

describe('セクション排他振り分け（AC-16/AC-15/AC-7）', () => {
  it('当月・一部参加: 未参加は参加側 / 参加中(PENDING)はキャンセル側に排他表示', async () => {
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');

    // 未参加の第2・第3は参加セクション（チェックボックスあり）
    expect(within(registerSection()).getByLabelText('第2試合に参加')).toBeInTheDocument();
    expect(within(registerSection()).getByLabelText('第3試合に参加')).toBeInTheDocument();
    expect(within(registerSection()).queryByLabelText('第1試合に参加')).not.toBeInTheDocument();

    // 参加中の第1はキャンセルセクション
    expect(within(cancelSection()).getByLabelText('第1試合をキャンセル対象に選択')).toBeInTheDocument();
  });

  it('満員（capacity 到達）でもチェック可: 満員ラベル表示かつチェックボックスは有効', async () => {
    configure({ session: defaultSession({ matchParticipantCounts: { 2: 24 } }) });
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    expect(within(registerSection()).getByText('満員')).toBeInTheDocument();
    const cb = within(registerSection()).getByLabelText('第2試合に参加');
    expect(cb).not.toBeDisabled();
  });

  it('伝助削除承認済みの試合は参加トグルを出さず × 表示', async () => {
    configure({ session: defaultSession({ densukeDeletionCandidateMatchNumbers: [3] }) });
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    expect(within(registerSection()).queryByLabelText('第3試合に参加')).not.toBeInTheDocument();
    expect(within(registerSection()).getByText('×')).toBeInTheDocument();
  });

  it('全試合参加中は参加セクション非表示（キャンセルのみ）', async () => {
    configure({
      monthParticipations: { 945: [1, 2, 3] },
      statusData: {
        participations: {
          945: [
            { matchNumber: 1, status: 'WON', participantId: 501 },
            { matchNumber: 2, status: 'WON', participantId: 502 },
            { matchNumber: 3, status: 'PENDING', participantId: 503 },
          ],
        },
        version: 7,
        lotteryExecuted: {},
        hasAnyExecutedLotteryInMonth: false,
      },
    });
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加をキャンセル');
    expect(screen.queryByText('参加する試合')).not.toBeInTheDocument();
    expect(screen.queryByText('参加を保存')).not.toBeInTheDocument();
  });

  it('全試合未参加はキャンセルセクション非表示（参加のみ）', async () => {
    configure({
      monthParticipations: { 945: [] },
      statusData: { participations: { 945: [] }, version: 7, lotteryExecuted: {}, hasAnyExecutedLotteryInMonth: false },
    });
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    expect(screen.queryByText('参加をキャンセル')).not.toBeInTheDocument();
  });
});

describe('抽選確定済みセッション（AC-6）', () => {
  it('参加トグル不可（保存ボタンなし）・WON はキャンセル可・WAITLISTED は読み取り専用', async () => {
    configure({
      monthParticipations: { 945: [1, 2] },
      statusData: {
        participations: {
          945: [
            { matchNumber: 1, status: 'WON', participantId: 601 },
            { matchNumber: 2, status: 'WAITLISTED', waitlistNumber: 3 },
          ],
        },
        version: 9,
        lotteryExecuted: { 945: true },
        hasAnyExecutedLotteryInMonth: true,
      },
    });
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加をキャンセル');
    expect(screen.queryByText('参加を保存')).not.toBeInTheDocument();
    expect(within(cancelSection()).getByLabelText('第1試合をキャンセル対象に選択')).toBeInTheDocument();
    expect(screen.getByText('その他の申込')).toBeInTheDocument(); // readonly セクション
    expect(screen.getByText(/待ち/)).toBeInTheDocument(); // WAITLISTED は readonly バッジ
  });
});

describe('来月扱い（AC-5）', () => {
  it('未来月・抽選前は全試合トグル（登録済みは pre-check）・キャンセルセクション非表示', async () => {
    configure({
      session: defaultSession({ id: 945, sessionDate: '2026-06-25' }),
      monthParticipations: { 945: [1] },
      statusData: {
        participations: { 945: [{ matchNumber: 1, status: 'PENDING', participantId: 555 }] },
        version: 7,
        lotteryExecuted: {},
        hasAnyExecutedLotteryInMonth: false,
      },
    });
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    // 全試合がトグルに出る
    expect(within(registerSection()).getByLabelText('第1試合に参加')).toBeChecked();
    expect(within(registerSection()).getByLabelText('第2試合に参加')).not.toBeChecked();
    expect(within(registerSection()).getByLabelText('第3試合に参加')).toBeInTheDocument();
    // キャンセルセクションは非表示
    expect(screen.queryByText('参加をキャンセル')).not.toBeInTheDocument();
  });
});

describe('参加保存ペイロード（AC-3/AC-10）', () => {
  it('対象セッション以外の同月参加を保持しつつ expectedVersion を送る', async () => {
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');

    // 第2を追加チェック（seed=[1] に +2）
    await user.click(within(registerSection()).getByLabelText('第2試合に参加'));
    await user.click(screen.getByText('参加を保存'));

    await waitFor(() => expect(practiceAPI.registerParticipations).toHaveBeenCalledTimes(1));
    expect(practiceAPI.registerParticipations).toHaveBeenCalledWith({
      playerId: 10,
      year: 2026,
      month: 5,
      participations: [
        { sessionId: 900, matchNumber: 2 }, // 他日（900）は保持
        { sessionId: 945, matchNumber: 1 }, // 既存の第1が落ちない
        { sessionId: 945, matchNumber: 2 }, // 追加
      ],
      expectedVersion: 7,
    });
  });

  it('保存成功で SaveProgressOverlay 完了→「カレンダーに戻る」で /practice へ（AC-9）', async () => {
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    await user.click(within(registerSection()).getByLabelText('第2試合に参加'));
    await user.click(screen.getByText('参加を保存'));

    await screen.findByText('保存しました');
    await user.click(screen.getByText('カレンダーに戻る'));
    expect(mockNavigate).toHaveBeenCalledWith('/practice');
  });

  it('保存エラー時は overlay error を表示し状態を維持する（AC-9）', async () => {
    practiceAPI.registerParticipations.mockRejectedValue({ response: { status: 500, data: { message: 'サーバエラー' } } });
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    await user.click(within(registerSection()).getByLabelText('第2試合に参加'));
    await user.click(screen.getByText('参加を保存'));

    expect(await screen.findByText('処理に失敗しました')).toBeInTheDocument();
  });

  it('409 応答でエラー表示＋最新再読込する（AC-10）', async () => {
    practiceAPI.registerParticipations.mockRejectedValue({
      response: { status: 409, data: { message: '他の端末で更新されました' } },
    });
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');
    // 初期ロードで getPlayerParticipations は1回呼ばれている
    expect(practiceAPI.getPlayerParticipations).toHaveBeenCalledTimes(1);

    await user.click(within(registerSection()).getByLabelText('第2試合に参加'));
    await user.click(screen.getByText('参加を保存'));

    expect(await screen.findByText('処理に失敗しました')).toBeInTheDocument();
    // reloadKey 更新で最新を再取得（getPlayerParticipations が再度呼ばれる）
    await waitFor(() => expect(practiceAPI.getPlayerParticipations).toHaveBeenCalledTimes(2));
  });
});

describe('理由付きキャンセル（AC-4）', () => {
  it('理由未選択では実行不可、選択後に participantId を集約して cancelMultiple を呼ぶ', async () => {
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加をキャンセル');

    await user.click(within(cancelSection()).getByLabelText('第1試合をキャンセル対象に選択'));

    const cancelButton = screen.getByText('選択した試合をキャンセル');
    expect(cancelButton).toBeDisabled(); // 理由未選択

    await user.click(screen.getByLabelText('体調不良'));
    expect(cancelButton).not.toBeDisabled();

    await user.click(cancelButton);
    await waitFor(() => expect(lotteryAPI.cancelMultiple).toHaveBeenCalledTimes(1));
    expect(lotteryAPI.cancelMultiple).toHaveBeenCalledWith([555], 'HEALTH', null);
  });

  it('「その他」は詳細必須（詳細空では実行不可、入力で有効化＋detail 送信）', async () => {
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加をキャンセル');

    await user.click(within(cancelSection()).getByLabelText('第1試合をキャンセル対象に選択'));
    await user.click(screen.getByLabelText('その他'));

    const cancelButton = screen.getByText('選択した試合をキャンセル');
    expect(cancelButton).toBeDisabled(); // 詳細未入力

    await user.type(screen.getByPlaceholderText('具体的な理由を入力してください'), '私用のため');
    expect(cancelButton).not.toBeDisabled();

    await user.click(cancelButton);
    await waitFor(() => expect(lotteryAPI.cancelMultiple).toHaveBeenCalledTimes(1));
    expect(lotteryAPI.cancelMultiple).toHaveBeenCalledWith([555], 'OTHER', '私用のため');
  });
});

describe('seed 取得失敗は保存をブロック（データ消失防止・advisor 指摘）', () => {
  it('getPlayerParticipations が失敗したらエラー表示のみで参加を保存に到達しない', async () => {
    practiceAPI.getPlayerParticipations.mockRejectedValue(new Error('network'));
    render(<PracticeSessionAttendance />);
    expect(await screen.findByText('データの取得に失敗しました')).toBeInTheDocument();
    expect(screen.queryByText('参加する試合')).not.toBeInTheDocument();
    expect(screen.queryByText('参加を保存')).not.toBeInTheDocument();
  });
});

describe('SAME_DAY 当日12時以降の確認ダイアログ（AC-8）', () => {
  beforeEach(() => {
    vi.setSystemTime(new Date('2026-05-25T13:00:00Z')); // 当日・正午以降（UTC/JST とも hours>=12）
  });

  it('参加保存: SAME_DAY 団体の当日変更で確認ダイアログ→「はい」で保存', async () => {
    configure({
      session: defaultSession({ sessionDate: '2026-05-25' }),
      monthParticipations: { 945: [] },
      statusData: { participations: { 945: [] }, version: 7, lotteryExecuted: {}, hasAnyExecutedLotteryInMonth: false },
      org: { id: 1, name: 'わすら', color: '#123456', deadlineType: 'SAME_DAY' },
    });
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加する試合');

    await user.click(within(registerSection()).getByLabelText('第1試合に参加'));
    await user.click(screen.getByText('参加を保存'));

    // 即保存せず確認ダイアログ
    expect(await screen.findByText(/12時以降の参加登録・キャンセル/)).toBeInTheDocument();
    expect(practiceAPI.registerParticipations).not.toHaveBeenCalled();

    await user.click(screen.getByText('はい'));
    await waitFor(() => expect(practiceAPI.registerParticipations).toHaveBeenCalledTimes(1));
  });

  it('キャンセル: 当日12時以降で確認ダイアログ→「キャンセルする」で実行', async () => {
    configure({
      session: defaultSession({ sessionDate: '2026-05-25' }),
      monthParticipations: { 945: [1] },
      statusData: {
        participations: { 945: [{ matchNumber: 1, status: 'PENDING', participantId: 555 }] },
        version: 7,
        lotteryExecuted: {},
        hasAnyExecutedLotteryInMonth: false,
      },
      org: { id: 1, name: 'わすら', color: '#123456', deadlineType: 'SAME_DAY' },
    });
    const user = userEvent.setup();
    render(<PracticeSessionAttendance />);
    await screen.findByText('参加をキャンセル');

    await user.click(within(cancelSection()).getByLabelText('第1試合をキャンセル対象に選択'));
    await user.click(screen.getByLabelText('体調不良'));
    await user.click(screen.getByText('選択した試合をキャンセル'));

    expect(await screen.findByText(/直前のキャンセル/)).toBeInTheDocument();
    expect(lotteryAPI.cancelMultiple).not.toHaveBeenCalled();

    // ダイアログ内の「キャンセルする」を押下
    const dialogConfirm = screen.getAllByText('キャンセルする').slice(-1)[0];
    await user.click(dialogConfirm);
    await waitFor(() => expect(lotteryAPI.cancelMultiple).toHaveBeenCalledTimes(1));
  });
});
