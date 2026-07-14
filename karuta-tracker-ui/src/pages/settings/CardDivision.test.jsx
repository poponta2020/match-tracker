import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  getCardDivision: vi.fn(),
  updateSubscription: vi.fn(),
  lineGetStatus: vi.fn(),
  organizationGetAll: vi.fn(),
  organizationGetPlayerOrganizations: vi.fn(),
  currentPlayer: { id: 1, role: 'PLAYER' },
}));

vi.mock('../../api/cardDivision', () => ({
  cardDivisionAPI: {
    getCardDivision: mocks.getCardDivision,
    updateSubscription: mocks.updateSubscription,
  },
}));

vi.mock('../../api', () => ({
  lineAPI: {
    getStatus: mocks.lineGetStatus,
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: mocks.organizationGetAll,
    getPlayerOrganizations: mocks.organizationGetPlayerOrganizations,
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: mocks.currentPlayer }),
}));

vi.mock('../../components/PageHeader', () => ({
  default: ({ title }) => <div data-testid="page-header">{title}</div>,
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

import CardDivision from './CardDivision';

const renderPage = () =>
  render(
    <MemoryRouter>
      <CardDivision />
    </MemoryRouter>
  );

const singleOrg = [{ id: 1, name: 'わすらもち会', color: '#ff0000' }];

describe('CardDivision', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.currentPlayer = { id: 1, role: 'PLAYER' };
    mocks.organizationGetAll.mockResolvedValue({ data: singleOrg });
    mocks.organizationGetPlayerOrganizations.mockResolvedValue({ data: [{ id: 1 }] });
    mocks.lineGetStatus.mockResolvedValue({ data: { enabled: true, linked: true } });
  });

  afterEach(() => {
    cleanup();
  });

  it('当日セッションありのブロックで札組テキストが表示される', async () => {
    mocks.getCardDivision.mockResolvedValue({
      data: {
        hasSession: true,
        date: '2026-07-15',
        organizationId: 1,
        text: '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7',
        subscribed: false,
      },
    });

    renderPage();

    const textarea = await screen.findByDisplayValue(/1試合目：一の位1\.3\.5\.6\.7/);
    expect(textarea).toBeInTheDocument();
  });

  it('「コピー」押下で navigator.clipboard.writeText がテキスト内容で呼ばれる', async () => {
    const text = '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7';
    mocks.getCardDivision.mockResolvedValue({
      data: { hasSession: true, date: '2026-07-15', organizationId: 1, text, subscribed: false },
    });

    renderPage();
    await screen.findByDisplayValue(/1試合目/);

    const user = userEvent.setup();
    if (!navigator.clipboard) {
      Object.defineProperty(navigator, 'clipboard', { value: {}, configurable: true });
    }
    vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);

    const copyButton = screen.getByRole('button', { name: /コピー/ });
    await user.click(copyButton);

    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(text);
    });
  });

  it('購読トグル押下で updateSubscription が enabled 反転値で呼ばれる', async () => {
    mocks.getCardDivision.mockResolvedValue({
      data: {
        hasSession: true,
        date: '2026-07-15',
        organizationId: 1,
        text: '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7',
        subscribed: false,
      },
    });
    mocks.updateSubscription.mockResolvedValue({ data: {} });

    renderPage();
    await screen.findByDisplayValue(/1試合目/);

    const user = userEvent.setup();
    const toggle = screen.getByText('この練習会の札分けをLINEで受け取る').closest('div').querySelector('button');
    await user.click(toggle);

    await waitFor(() => {
      expect(mocks.updateSubscription).toHaveBeenCalledWith({
        playerId: 1,
        organizationId: 1,
        enabled: true,
      });
    });
  });

  it('hasSession=false の団体は空表示（テキストなし）', async () => {
    mocks.getCardDivision.mockResolvedValue({
      data: { hasSession: false, date: '2026-07-15', organizationId: 1, text: null, subscribed: false },
    });

    renderPage();

    await screen.findByText('本日は練習がありません');
    expect(screen.queryByRole('textbox', { name: '' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /コピー/ })).not.toBeInTheDocument();
  });

  it('参加練習会が0件のとき空表示＋参加練習会への導線を出す', async () => {
    mocks.organizationGetPlayerOrganizations.mockResolvedValue({ data: [] });

    renderPage();

    await screen.findByText('参加練習会が設定されていません。');
    expect(screen.getByRole('link', { name: /参加練習会/ })).toHaveAttribute(
      'href',
      '/settings/organizations'
    );
    expect(mocks.getCardDivision).not.toHaveBeenCalled();
  });

  it('LINE linked=false のとき未連携案内文言が表示される', async () => {
    mocks.lineGetStatus.mockResolvedValue({ data: { enabled: true, linked: false } });
    mocks.getCardDivision.mockResolvedValue({
      data: {
        hasSession: true,
        date: '2026-07-15',
        organizationId: 1,
        text: '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7',
        subscribed: false,
      },
    });

    renderPage();

    await screen.findByText(/LINE登録済みでない場合は/);
    expect(screen.getByRole('link', { name: /設定 → 通知設定/ })).toHaveAttribute(
      'href',
      '/settings/notifications'
    );
  });
});
