import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../../api/matchComments', () => ({
  matchCommentsAPI: {
    getComments: vi.fn().mockResolvedValue({ data: [] }),
    createComment: vi.fn().mockResolvedValue({ data: {} }),
    updateComment: vi.fn().mockResolvedValue({ data: {} }),
    deleteComment: vi.fn().mockResolvedValue({}),
    sendNotification: vi.fn().mockResolvedValue({ data: { result: 'SUCCESS' } }),
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: { id: 1, name: 'テスト選手' },
  }),
}));

const mockSetVisible = vi.fn();
vi.mock('../../context/BottomNavContext', () => ({
  useBottomNav: () => ({
    isVisible: true,
    setVisible: mockSetVisible,
  }),
}));

import { matchCommentsAPI } from '../../api/matchComments';
import MatchCommentThread from './MatchCommentThread';

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe('MatchCommentThread', () => {
  const renderComponent = () =>
    render(<MatchCommentThread matchId={1} menteeId={1} />);

  it('Enterキーでコメントが送信される', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.type(textarea, 'テストコメント');
    await user.keyboard('{Enter}');

    expect(matchCommentsAPI.createComment).toHaveBeenCalledWith(
      1,
      1,
      'テストコメント'
    );
  });

  it('Shift+Enterで改行が入力される（送信されない）', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.type(textarea, '1行目');
    await user.keyboard('{Shift>}{Enter}{/Shift}');
    await user.type(textarea, '2行目');

    expect(matchCommentsAPI.createComment).not.toHaveBeenCalled();
    expect(textarea.value).toContain('1行目');
    expect(textarea.value).toContain('2行目');
  });

  it('送信後に入力欄がクリアされる', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.type(textarea, 'テストコメント');
    await user.keyboard('{Enter}');

    await vi.waitFor(() => {
      expect(textarea.value).toBe('');
    });
  });
});

describe('MatchCommentThread LINE通知', () => {
  const unnotifiedComments = [
    { id: 1, authorId: 1, authorName: 'テスト選手', content: 'コメント1', lineNotified: false, createdAt: '2026-04-10T10:00:00' },
    { id: 2, authorId: 1, authorName: 'テスト選手', content: 'コメント2', lineNotified: false, createdAt: '2026-04-10T10:05:00' },
  ];

  const renderWithComments = (comments = unnotifiedComments) => {
    matchCommentsAPI.getComments.mockResolvedValue({ data: comments });
    return render(<MatchCommentThread matchId={1} menteeId={1} />);
  };

  it('未通知コメントがあるとき通知ボタンが表示される', async () => {
    renderWithComments();

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信（2件）')).toBeInTheDocument();
    });
  });

  it('未通知コメントがないとき通知ボタンが表示されない', async () => {
    const notifiedComments = [
      { id: 1, authorId: 1, authorName: 'テスト選手', content: 'コメント1', lineNotified: true, createdAt: '2026-04-10T10:00:00' },
    ];
    renderWithComments(notifiedComments);

    await vi.waitFor(() => {
      expect(screen.getByText('コメント1')).toBeInTheDocument();
    });
    expect(screen.queryByText(/LINE通知を送信/)).not.toBeInTheDocument();
  });

  it('SUCCESS時に成功メッセージが表示される', async () => {
    const user = userEvent.setup();
    matchCommentsAPI.sendNotification.mockResolvedValue({ data: { result: 'SUCCESS' } });
    renderWithComments();

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信（2件）')).toBeInTheDocument();
    });

    await user.click(screen.getByText('LINE通知を送信（2件）'));

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信しました')).toBeInTheDocument();
    });
    expect(matchCommentsAPI.sendNotification).toHaveBeenCalledWith(1, 1);
  });

  it('SKIPPED時にエラーメッセージが表示される', async () => {
    const user = userEvent.setup();
    matchCommentsAPI.sendNotification.mockResolvedValue({ data: { result: 'SKIPPED' } });
    renderWithComments();

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信（2件）')).toBeInTheDocument();
    });

    await user.click(screen.getByText('LINE通知を送信（2件）'));

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信できませんでした（LINE未連携・通知設定OFF・月間上限超過の可能性があります）')).toBeInTheDocument();
    });
  });

  it('FAILED時にエラーメッセージが表示される', async () => {
    const user = userEvent.setup();
    matchCommentsAPI.sendNotification.mockResolvedValue({ data: { result: 'FAILED' } });
    renderWithComments();

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信（2件）')).toBeInTheDocument();
    });

    await user.click(screen.getByText('LINE通知を送信（2件）'));

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知の送信に失敗しました。再度お試しください')).toBeInTheDocument();
    });
  });

  it('API例外時にエラーメッセージが表示される', async () => {
    const user = userEvent.setup();
    matchCommentsAPI.sendNotification.mockRejectedValue({
      response: { data: { message: 'サーバーエラー' } },
    });
    renderWithComments();

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信（2件）')).toBeInTheDocument();
    });

    await user.click(screen.getByText('LINE通知を送信（2件）'));

    await vi.waitFor(() => {
      expect(screen.getByText('サーバーエラー')).toBeInTheDocument();
    });
  });

  it('送信中はボタンが無効化される', async () => {
    const user = userEvent.setup();
    let resolveNotification;
    matchCommentsAPI.sendNotification.mockImplementation(
      () => new Promise((resolve) => { resolveNotification = resolve; })
    );
    renderWithComments();

    await vi.waitFor(() => {
      expect(screen.getByText('LINE通知を送信（2件）')).toBeInTheDocument();
    });

    await user.click(screen.getByText('LINE通知を送信（2件）'));

    await vi.waitFor(() => {
      expect(screen.getByText('送信中...')).toBeInTheDocument();
    });
    expect(screen.getByText('送信中...').closest('button')).toBeDisabled();

    resolveNotification({ data: { result: 'SUCCESS' } });
  });
});

describe('MatchCommentThread ボトムナビ制御', () => {
  const renderComponent = () =>
    render(<MatchCommentThread matchId={1} menteeId={1} />);

  it('textarea フォーカス時にボトムナビが非表示になる', async () => {
    const user = userEvent.setup();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    await user.click(textarea);

    expect(mockSetVisible).toHaveBeenCalledWith(false);
  });

  it('textarea ブラー時に100ms後にボトムナビが再表示される', () => {
    vi.useFakeTimers();
    renderComponent();

    const textarea = screen.getByPlaceholderText('コメントを入力...');
    fireEvent.focus(textarea);
    mockSetVisible.mockClear();

    fireEvent.blur(textarea);
    expect(mockSetVisible).not.toHaveBeenCalledWith(true);

    vi.advanceTimersByTime(100);
    expect(mockSetVisible).toHaveBeenCalledWith(true);
  });

  it('unmount時にボトムナビが表示状態にリセットされる', () => {
    const { unmount } = renderComponent();
    mockSetVisible.mockClear();

    unmount();

    expect(mockSetVisible).toHaveBeenCalledWith(true);
  });
});
