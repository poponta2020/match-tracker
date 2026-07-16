import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  getGroups: vi.fn(),
  createGroup: vi.fn(),
  updateGroup: vi.fn(),
  assignBot: vi.fn(),
  unassignBot: vi.fn(),
  getStatus: vi.fn(),
  getLogs: vi.fn(),
}));

vi.mock('../../api', () => ({
  lineBroadcastAPI: {
    getGroups: mocks.getGroups,
    createGroup: mocks.createGroup,
    updateGroup: mocks.updateGroup,
    assignBot: mocks.assignBot,
    unassignBot: mocks.unassignBot,
    getStatus: mocks.getStatus,
    getLogs: mocks.getLogs,
  },
}));

import CardDivisionBroadcastAdmin from './CardDivisionBroadcastAdmin';

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/admin/line/broadcast']}>
      <CardDivisionBroadcastAdmin />
    </MemoryRouter>
  );

const baseGroup = {
  id: 1,
  organizationId: 10,
  organizationName: '北海道支部',
  name: '全体グループA',
  enabled: true,
  expectedRecipientCount: 50,
  botCount: 2,
  readyBotCount: 1,
  createdAt: '2026-07-01T00:00:00',
  updatedAt: '2026-07-01T00:00:00',
};

const baseStatus = {
  nextBotChannelId: 101,
  expectedRecipientCount: 50,
  remainingBroadcasts: 4,
  exhausted: false,
  bots: [
    {
      channelId: 101,
      lineChannelId: 'C101',
      channelName: 'botA',
      monthlyMessageCount: 10,
      remainingQuota: 190,
      groupIdCaptured: true,
      enabled: true,
    },
    {
      channelId: 102,
      lineChannelId: 'C102',
      channelName: 'botB',
      monthlyMessageCount: 0,
      remainingQuota: 200,
      groupIdCaptured: false,
      enabled: true,
    },
  ],
};

const baseLogs = {
  logs: [
    {
      id: 1,
      sessionId: 5,
      lineChannelId: 101,
      recipientCount: 50,
      status: 'SUCCESS',
      errorMessage: null,
      sentAt: '2026-07-01T09:00:00',
    },
    {
      id: 2,
      sessionId: 6,
      lineChannelId: 102,
      recipientCount: 0,
      status: 'SKIPPED',
      errorMessage: '全bot枯渇',
      sentAt: '2026-07-02T09:00:00',
    },
  ],
  hasRecentSkip: true,
};

describe('CardDivisionBroadcastAdmin', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getGroups.mockResolvedValue({ data: [baseGroup] });
    mocks.getStatus.mockResolvedValue({ data: baseStatus });
    mocks.getLogs.mockResolvedValue({ data: baseLogs });
    mocks.createGroup.mockResolvedValue({ data: baseGroup });
    mocks.updateGroup.mockResolvedValue({ data: baseGroup });
    mocks.assignBot.mockResolvedValue({ data: {} });
    mocks.unassignBot.mockResolvedValue({ data: {} });
    vi.stubGlobal('confirm', vi.fn(() => true));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('renders the group list with organization, expected recipient count, and bot counts', async () => {
    renderPage();

    const nameCell = await screen.findByText('全体グループA');
    const row = nameCell.closest('tr');
    const rowScope = within(row);

    expect(rowScope.getByText('北海道支部')).toBeInTheDocument();
    expect(rowScope.getByText('50')).toBeInTheDocument();
    // 割当bot数=2, 配信可能bot数=1
    expect(rowScope.getByText('2')).toBeInTheDocument();
    expect(rowScope.getByText('1')).toBeInTheDocument();
  });

  it('shows bot quota status, next bot highlight, and unparticipated badge when detail is opened', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: '詳細' }));

    await waitFor(() => {
      expect(mocks.getStatus).toHaveBeenCalledWith(1);
      expect(mocks.getLogs).toHaveBeenCalledWith(1);
    });

    expect(await screen.findByText('botA')).toBeInTheDocument();
    expect(screen.getByText('botB')).toBeInTheDocument();
    expect(screen.getByText('未参加')).toBeInTheDocument();
    const remainingSpan = screen.getByText(/今月あと配信可能回数/);
    expect(remainingSpan).toHaveTextContent('今月あと配信可能回数: 4');
  });

  it('shows an exhausted alert when the group has run out of quota', async () => {
    mocks.getStatus.mockResolvedValue({
      data: { ...baseStatus, nextBotChannelId: null, remainingBroadcasts: 0, exhausted: true },
    });
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: '詳細' }));

    expect(await screen.findByText(/全botが枯渇しています/)).toBeInTheDocument();
  });

  it('shows a recent-skip alert and lists broadcast logs', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: '詳細' }));

    expect(await screen.findByText(/直近にスキップされた配信があります/)).toBeInTheDocument();
    expect(screen.getByText('成功')).toBeInTheDocument();
    expect(screen.getByText('スキップ')).toBeInTheDocument();
    expect(screen.getByText('全bot枯渇')).toBeInTheDocument();
  });

  it('assigns a bot by channel id', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: '詳細' }));
    await screen.findByText('botA');

    await user.type(screen.getByPlaceholderText('チャネルID'), '999');
    await user.click(screen.getByRole('button', { name: '割当' }));

    await waitFor(() => {
      expect(mocks.assignBot).toHaveBeenCalledWith(1, 999);
    });
  });

  it('unassigns a bot after confirmation', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: '詳細' }));
    await screen.findByText('botA');

    await user.click(screen.getAllByTitle('割当解除')[0]);

    await waitFor(() => {
      expect(mocks.unassignBot).toHaveBeenCalledWith(1, 101);
    });
  });

  it('toggles enabled state', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: '無効化' }));

    await waitFor(() => {
      expect(mocks.updateGroup).toHaveBeenCalledWith(1, { enabled: false });
    });
  });

  it('creates a new broadcast group from the form', async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('全体グループA');
    await user.click(screen.getByRole('button', { name: /配信グループ登録/ }));

    const heading = await screen.findByRole('heading', { name: '配信グループ登録' });
    const form = heading.closest('form');
    const formScope = within(form);

    await user.type(formScope.getAllByRole('spinbutton')[0], '20');
    await user.type(formScope.getByRole('textbox'), '全体グループB');
    await user.click(formScope.getByRole('button', { name: '登録' }));

    await waitFor(() => {
      expect(mocks.createGroup).toHaveBeenCalledWith({ organizationId: 20, name: '全体グループB' });
    });
  });

  it('shows the bot group setup guidance text', async () => {
    renderPage();

    await screen.findByText('全体グループA');
    expect(screen.getByText(/グループトーク参加を許可/)).toBeInTheDocument();
    expect(screen.getByText(/join Webhookでグループ ID が自動捕捉される/)).toBeInTheDocument();
  });
});
