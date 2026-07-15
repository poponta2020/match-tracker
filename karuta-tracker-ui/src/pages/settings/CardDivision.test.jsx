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

// 1日分のレスポンス生成ヘルパー（今日/明日で別レスポンスを mockResolvedValueOnce で積む）
const sessionRes = (date, text, subscribed = false) => ({
  data: { hasSession: true, date, organizationId: 1, text, subscribed },
});
const noSessionRes = (date, subscribed = false) => ({
  data: { hasSession: false, date, organizationId: 1, text: null, subscribed },
});

// 今日→明日の2回取得を順に積む（1回目=今日、2回目=明日）
const mockTodayTomorrow = (todayRes, tomorrowRes) => {
  mocks.getCardDivision.mockResolvedValueOnce(todayRes).mockResolvedValueOnce(tomorrowRes);
};

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

  // AC-14: 今日・明日の2枠が常に表示される
  it('各練習会ブロックに「今日」「明日」の2枠が表示される', async () => {
    mockTodayTomorrow(
      sessionRes('2026-07-15', '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7'),
      sessionRes('2026-07-16', '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9')
    );

    renderPage();

    expect(await screen.findByText(/今日 7\/15/)).toBeInTheDocument();
    expect(screen.getByText(/明日 7\/16/)).toBeInTheDocument();
  });

  // AC-15: 明日にセッションが無ければ「明日は練習がありません」
  it('明日にセッションが無ければ「明日は練習がありません」を表示する', async () => {
    mockTodayTomorrow(
      sessionRes('2026-07-15', '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7'),
      noSessionRes('2026-07-16')
    );

    renderPage();

    await screen.findByText(/今日 7\/15/);
    expect(await screen.findByText('明日は練習がありません')).toBeInTheDocument();
  });

  // AC-15: 今日にセッションが無ければ「今日は練習がありません」
  it('今日にセッションが無ければ「今日は練習がありません」を表示する', async () => {
    mockTodayTomorrow(
      noSessionRes('2026-07-15'),
      sessionRes('2026-07-16', '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9')
    );

    renderPage();

    expect(await screen.findByText('今日は練習がありません')).toBeInTheDocument();
    expect(await screen.findByText(/明日 7\/16/)).toBeInTheDocument();
  });

  // AC-16: 今日・明日それぞれのテキストを独立したコピーボタンで個別にコピーできる
  it('今日・明日それぞれのコピーボタンが各テキストで writeText を呼ぶ', async () => {
    const todayText = '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7';
    const tomorrowText = '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9';
    mockTodayTomorrow(sessionRes('2026-07-15', todayText), sessionRes('2026-07-16', tomorrowText));

    renderPage();
    await screen.findByText(/今日 7\/15/);
    await screen.findByText(/明日 7\/16/);

    const user = userEvent.setup();
    // clipboard 未実装環境でも writeText を持たせてから spy する（空オブジェクトだと spy が例外になる）
    if (!navigator.clipboard) {
      Object.defineProperty(navigator, 'clipboard', {
        value: { writeText: vi.fn().mockResolvedValue(undefined) },
        configurable: true,
      });
    }
    vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue(undefined);

    const copyButtons = screen.getAllByRole('button', { name: /コピー/ });
    expect(copyButtons).toHaveLength(2); // 今日・明日で1つずつ

    await user.click(copyButtons[0]);
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(todayText);
    });

    await user.click(copyButtons[1]);
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(tomorrowText);
    });
  });

  // AC-17: 明日分は今日レスポンスの date を +1 した日付で取得される（FE の new Date に依存しない）
  it('明日分は今日レスポンスの date を +1 した日付で取得される', async () => {
    mockTodayTomorrow(
      sessionRes('2026-07-15', '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7'),
      sessionRes('2026-07-16', '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9')
    );

    renderPage();
    await screen.findByText(/今日 7\/15/);

    await waitFor(() => {
      expect(mocks.getCardDivision).toHaveBeenCalledTimes(2);
    });
    // 1回目: date 未指定（今日）。2回目: 今日レスポンスの date '2026-07-15' を +1 した '2026-07-16'
    expect(mocks.getCardDivision).toHaveBeenNthCalledWith(1, 1, 1);
    expect(mocks.getCardDivision).toHaveBeenNthCalledWith(2, 1, 1, '2026-07-16');
  });

  // AC-18: 明日ブロックに「暫定」注記が表示される（今日ブロックには付かない）
  it('明日ブロックに「暫定（確定前に変わる場合あり）」の注記が表示される', async () => {
    mockTodayTomorrow(
      sessionRes('2026-07-15', '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7'),
      sessionRes('2026-07-16', '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9')
    );

    renderPage();

    const notes = await screen.findAllByText('暫定（確定前に変わる場合あり）');
    expect(notes).toHaveLength(1); // 明日ブロックのみ
  });

  // AC-21回帰: 購読トグル押下で updateSubscription が enabled 反転値で呼ばれる
  it('購読トグル押下で updateSubscription が enabled 反転値で呼ばれる', async () => {
    mockTodayTomorrow(
      sessionRes('2026-07-15', '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7', false),
      sessionRes('2026-07-16', '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9', false)
    );
    mocks.updateSubscription.mockResolvedValue({ data: {} });

    renderPage();
    await screen.findByText(/今日 7\/15/);

    const user = userEvent.setup();
    const toggle = screen
      .getByText('この練習会の札分けをLINEで受け取る')
      .closest('div')
      .querySelector('button');
    await user.click(toggle);

    await waitFor(() => {
      expect(mocks.updateSubscription).toHaveBeenCalledWith({
        playerId: 1,
        organizationId: 1,
        enabled: true,
      });
    });
  });

  // AC-21回帰: 参加練習会が0件のとき空表示＋導線（getCardDivision は呼ばれない）
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

  // AC-21回帰: LINE linked=false のとき未連携案内文言が表示される
  it('LINE linked=false のとき未連携案内文言が表示される', async () => {
    mocks.lineGetStatus.mockResolvedValue({ data: { enabled: true, linked: false } });
    mockTodayTomorrow(
      sessionRes('2026-07-15', '【7/15 かでる2・7】\n1試合目：一の位1.3.5.6.7'),
      sessionRes('2026-07-16', '【7/16 かでる10】\n1試合目：十の位2.4.6.8.9')
    );

    renderPage();

    await screen.findByText(/LINE登録済みでない場合は/);
    expect(screen.getByRole('link', { name: /設定 → 通知設定/ })).toHaveAttribute(
      'href',
      '/settings/notifications'
    );
  });
});
