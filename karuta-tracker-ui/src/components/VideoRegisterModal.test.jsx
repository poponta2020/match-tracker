import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// API 層（barrel）をモック。コンポーネントは '../api' から名前付きで取り込む。
const mocks = vi.hoisted(() => ({
  matchGetByPlayerId: vi.fn(),
  pairingGetByPlayerId: vi.fn(),
  pairingGetByDate: vi.fn(),
  matchGetByDate: vi.fn(),
  videoGetByDate: vi.fn(),
  videoSearch: vi.fn(),
  videoRegister: vi.fn(),
  videoUpdate: vi.fn(),
  playerGetAll: vi.fn(),
}));

vi.mock('../api', () => ({
  matchAPI: {
    getByPlayerId: mocks.matchGetByPlayerId,
    getByDate: mocks.matchGetByDate,
  },
  pairingAPI: {
    getByPlayerId: mocks.pairingGetByPlayerId,
    getByDate: mocks.pairingGetByDate,
  },
  matchVideoAPI: {
    getByDate: mocks.videoGetByDate,
    search: mocks.videoSearch,
    register: mocks.videoRegister,
    update: mocks.videoUpdate,
  },
  playerAPI: {
    getAll: mocks.playerGetAll,
  },
}));

import VideoRegisterModal from './VideoRegisterModal';

const PLAYER = { id: 1, name: '山田太郎', kyuRank: 'A級' };

// 「選手から」タブに切り替え、選手を選択するまでの共通操作
const openPlayerTabAndSelect = async (user) => {
  await user.click(screen.getByRole('button', { name: /選手から/ }));
  const searchInput = screen.getByPlaceholderText('選手名で絞り込み...');
  await user.click(searchInput); // onFocus で選手一覧ロード
  await user.type(searchInput, '山田');
  const option = await screen.findByRole('button', { name: /山田太郎/ });
  await user.click(option);
};

describe('VideoRegisterModal - 選手起点（PlayerSourceSelect）', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((m) => m.mockReset());
    // 既定の空レスポンス（日付起点の初期 fetch 等で呼ばれても落ちないように）
    mocks.matchGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByDate.mockResolvedValue({ data: [] });
    mocks.matchGetByDate.mockResolvedValue({ data: [] });
    mocks.videoGetByDate.mockResolvedValue({ data: [] });
    mocks.videoSearch.mockResolvedValue({ data: { content: [] } });
    mocks.playerGetAll.mockResolvedValue({ data: [PLAYER] });
  });

  afterEach(() => {
    cleanup();
  });

  it('選手起点でも組み合わせ（結果未入力の pairings）が候補に含まれる', async () => {
    const user = userEvent.setup();
    // 完了済み試合はなし、組み合わせ（結果未入力）だけ存在
    mocks.matchGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByPlayerId.mockResolvedValue({
      data: [
        {
          id: 10,
          sessionDate: '2026-06-12',
          matchNumber: 1,
          player1Id: 1,
          player1Name: '山田太郎',
          player2Id: 2,
          player2Name: '佐藤花子',
        },
      ],
    });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={vi.fn()} />);
    await openPlayerTabAndSelect(user);

    // 各APIが選手IDで呼ばれること
    await waitFor(() => {
      expect(mocks.matchGetByPlayerId).toHaveBeenCalledWith(1);
      expect(mocks.pairingGetByPlayerId).toHaveBeenCalledWith(1);
      expect(mocks.videoSearch).toHaveBeenCalledWith({ playerId: 1, size: 100 });
    });

    // 組み合わせ由来の候補（対戦カード）が表示される
    const card = await screen.findByText(/佐藤花子/);
    expect(card).toBeTruthy();
    // 完了済み matches が無くてもペアリングが候補に出る（= 選択可能な button）
    const selectButton = card.closest('button');
    expect(selectButton).toBeTruthy();
  });

  it('登録済み動画に一致する組み合わせは「登録済み」表示＋選択不可になる', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();
    mocks.matchGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByPlayerId.mockResolvedValue({
      data: [
        {
          id: 10,
          sessionDate: '2026-06-12',
          matchNumber: 1,
          player1Id: 1,
          player1Name: '山田太郎',
          player2Id: 2,
          player2Name: '佐藤花子',
        },
      ],
    });
    // 同一自然キーの動画が登録済み（p1<p2 正規化済み）
    mocks.videoSearch.mockResolvedValue({
      data: {
        content: [
          { matchDate: '2026-06-12', matchNumber: 1, player1Id: 1, player2Id: 2 },
        ],
      },
    });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={onSelect} />);
    await openPlayerTabAndSelect(user);

    const registered = await screen.findByText('登録済み');
    expect(registered).toBeTruthy();

    // 登録済み行は button ではなく div（選択不可）。クリックしても次ステップに進まない。
    const card = screen.getByText(/佐藤花子/);
    expect(card.closest('button')).toBeNull();
  });

  it('完了済み試合（matches）と組み合わせ（pairings）が自然キーで重複排除され matches が優先される', async () => {
    const user = userEvent.setup();
    // 同一自然キー (2026-06-12, #1, ペア{1,2}) が matches と pairings の両方にある
    mocks.matchGetByPlayerId.mockResolvedValue({
      data: [
        {
          id: 100,
          matchDate: '2026-06-12',
          matchNumber: 1,
          player1Id: 1,
          player2Id: 2,
          opponentName: '佐藤花子',
          video: null,
        },
      ],
    });
    mocks.pairingGetByPlayerId.mockResolvedValue({
      data: [
        {
          id: 10,
          sessionDate: '2026-06-12',
          matchNumber: 1,
          player1Id: 2,
          player1Name: '佐藤花子',
          player2Id: 1,
          player2Name: '山田太郎',
        },
      ],
    });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={vi.fn()} />);
    await openPlayerTabAndSelect(user);

    // 対戦カードは1件だけ（重複排除されている）
    const cards = await screen.findAllByText(/佐藤花子/);
    expect(cards).toHaveLength(1);
  });

  it('選択可能な組み合わせをクリックすると onSelect が sessionDate→matchDate にマップした自然キーで呼ばれる', async () => {
    const user = userEvent.setup();
    const onSuccess = vi.fn();
    mocks.matchGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByPlayerId.mockResolvedValue({
      data: [
        {
          id: 10,
          sessionDate: '2026-06-12',
          matchNumber: 3,
          player1Id: 1,
          player1Name: '山田太郎',
          player2Id: 2,
          player2Name: '佐藤花子',
        },
      ],
    });
    mocks.videoRegister.mockResolvedValue({ data: { id: 99 } });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={onSuccess} />);
    await openPlayerTabAndSelect(user);

    const card = await screen.findByText(/佐藤花子/);
    await user.click(card.closest('button'));

    // URL 入力ステップに遷移し、対象試合カードが表示される
    expect(await screen.findByText('対象の試合')).toBeTruthy();

    // URL を入力して登録 → register に組み立てたペイロードが渡る
    const urlInput = screen.getByPlaceholderText('https://youtu.be/...');
    await user.type(urlInput, 'https://youtu.be/abcdefghijk');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(mocks.videoRegister).toHaveBeenCalledWith({
        matchDate: '2026-06-12',
        matchNumber: 3,
        player1Id: 1,
        player2Id: 2,
        videoUrl: 'https://youtu.be/abcdefghijk',
      });
    });
  });

  it('相手がシステム未登録（相手 id が 0/null）の試合は選択不可表示になる', async () => {
    const user = userEvent.setup();
    mocks.matchGetByPlayerId.mockResolvedValue({
      data: [
        {
          id: 100,
          matchDate: '2026-06-10',
          matchNumber: 1,
          player1Id: 1,
          player2Id: 0, // 相手未登録
          opponentName: 'ゲスト',
          video: null,
        },
      ],
    });
    mocks.pairingGetByPlayerId.mockResolvedValue({ data: [] });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={vi.fn()} />);
    await openPlayerTabAndSelect(user);

    expect(await screen.findByText('相手未登録')).toBeTruthy();
    const card = screen.getByText(/ゲスト/);
    expect(card.closest('button')).toBeNull();
  });
});

describe('VideoRegisterModal - 日付起点（DateSourceSelect 既存挙動の回帰）', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((m) => m.mockReset());
    mocks.matchGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByPlayerId.mockResolvedValue({ data: [] });
    mocks.pairingGetByDate.mockResolvedValue({ data: [] });
    mocks.matchGetByDate.mockResolvedValue({ data: [] });
    mocks.videoGetByDate.mockResolvedValue({ data: [] });
    mocks.videoSearch.mockResolvedValue({ data: { content: [] } });
    mocks.playerGetAll.mockResolvedValue({ data: [PLAYER] });
  });

  afterEach(() => {
    cleanup();
  });

  it('日付起点はペアリング+試合を統合し、登録済み動画はグレーアウトする（従来どおり）', async () => {
    mocks.pairingGetByDate.mockResolvedValue({
      data: [
        { matchNumber: 1, player1Id: 1, player1Name: '山田太郎', player2Id: 2, player2Name: '佐藤花子' },
        { matchNumber: 2, player1Id: 3, player1Name: '鈴木一郎', player2Id: 4, player2Name: '田中次郎' },
      ],
    });
    mocks.matchGetByDate.mockResolvedValue({ data: [] });
    // 第1試合の動画が登録済み
    mocks.videoGetByDate.mockResolvedValue({
      data: [{ matchNumber: 1, player1Id: 1, player2Id: 2 }],
    });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={vi.fn()} />);

    // 日付起点はデフォルトタブ。両カードが表示される
    expect(await screen.findByText(/佐藤花子/)).toBeTruthy();
    expect(screen.getByText(/田中次郎/)).toBeTruthy();

    // 登録済み（第1試合）はグレーアウト＝button ではない
    expect(screen.getByText('登録済み')).toBeTruthy();
    const registeredCard = screen.getByText(/佐藤花子/);
    expect(registeredCard.closest('button')).toBeNull();
    // 未登録（第2試合）は選択可能
    const selectableCard = screen.getByText(/田中次郎/);
    expect(selectableCard.closest('button')).toBeTruthy();
  });

  it('日付起点で未登録カードをクリックすると register に自然キーが渡る（従来どおり）', async () => {
    const user = userEvent.setup();
    mocks.pairingGetByDate.mockResolvedValue({
      data: [
        { matchNumber: 2, player1Id: 3, player1Name: '鈴木一郎', player2Id: 4, player2Name: '田中次郎' },
      ],
    });
    mocks.videoRegister.mockResolvedValue({ data: { id: 1 } });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={vi.fn()} />);

    const card = await screen.findByText(/田中次郎/);
    await user.click(card.closest('button'));

    const urlInput = await screen.findByPlaceholderText('https://youtu.be/...');
    await user.type(urlInput, 'https://youtu.be/abcdefghijk');
    await user.click(screen.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(mocks.videoRegister).toHaveBeenCalledWith({
        matchDate: expect.any(String),
        matchNumber: 2,
        player1Id: 3,
        player2Id: 4,
        videoUrl: 'https://youtu.be/abcdefghijk',
      });
    });
  });

  it('日付起点で相手がシステム未登録（id=0）の試合は選択不可表示になる', async () => {
    // 試合詳細・選手起点と統一: 未登録相手を含む試合は登録不可（選択不可）
    mocks.matchGetByDate.mockResolvedValue({
      data: [
        { matchNumber: 1, player1Id: 1, player1Name: '山田太郎', player2Id: 0, player2Name: 'ゲスト' },
      ],
    });

    render(<VideoRegisterModal selectMode onClose={vi.fn()} onSuccess={vi.fn()} />);

    expect(await screen.findByText('相手未登録')).toBeTruthy();
    // 選択不可（button ではなく div）
    const card = screen.getByText(/ゲスト/);
    expect(card.closest('button')).toBeNull();
  });
});
