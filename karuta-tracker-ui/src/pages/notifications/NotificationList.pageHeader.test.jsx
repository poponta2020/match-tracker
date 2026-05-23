import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  notificationsGetAll: vi.fn(),
  notificationsDeleteAll: vi.fn(),
  notificationsMarkAsRead: vi.fn(),
  currentPlayer: { id: 1, role: 'PLAYER' },
}));

vi.mock('../../api/notifications', () => ({
  notificationAPI: {
    getAll: mocks.notificationsGetAll,
    deleteAll: mocks.notificationsDeleteAll,
    markAsRead: mocks.notificationsMarkAsRead,
  },
}));

vi.mock('../../api/lottery', () => ({
  lotteryAPI: {
    getOfferDetail: vi.fn(),
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: mocks.currentPlayer }),
}));

vi.mock('../../components/PageHeader', () => ({
  default: ({ title, backTo, rightActions }) => (
    <div data-testid="page-header" data-title={title} data-back-to={backTo}>
      <div data-testid="right-actions">{rightActions}</div>
    </div>
  ),
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

import NotificationList from './NotificationList';

const renderPage = () =>
  render(
    <MemoryRouter>
      <NotificationList />
    </MemoryRouter>
  );

describe('NotificationList PageHeader integration', () => {
  let originalConfirm;

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.currentPlayer = { id: 1, role: 'PLAYER' };
    mocks.notificationsDeleteAll.mockResolvedValue({ data: {} });
    originalConfirm = window.confirm;
    window.confirm = vi.fn(() => true);
  });

  afterEach(() => {
    window.confirm = originalConfirm;
    cleanup();
  });

  it('passes "通知" title and / backTo to PageHeader', async () => {
    mocks.notificationsGetAll.mockResolvedValue({ data: [] });
    renderPage();
    const header = await screen.findByTestId('page-header');
    expect(header).toHaveAttribute('data-title', '通知');
    expect(header).toHaveAttribute('data-back-to', '/');
  });

  it('does not render the "すべて削除" rightAction when notifications are empty', async () => {
    mocks.notificationsGetAll.mockResolvedValue({ data: [] });
    renderPage();
    await screen.findByTestId('page-header');
    expect(screen.queryByRole('button', { name: 'すべて削除' })).not.toBeInTheDocument();
  });

  it('renders the "すべて削除" rightAction once notifications load and invokes the handler', async () => {
    mocks.notificationsGetAll.mockResolvedValue({
      data: [
        { id: 10, type: 'LOTTERY_WON', title: 'test', message: '', isRead: false, createdAt: '2026-01-01T00:00:00' },
      ],
    });
    renderPage();
    const button = await screen.findByRole('button', { name: 'すべて削除' });
    expect(button).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(button);
    await waitFor(() => {
      expect(mocks.notificationsDeleteAll).toHaveBeenCalledWith(1);
    });
  });
});
