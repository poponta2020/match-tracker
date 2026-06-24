import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mocks = vi.hoisted(() => ({
  getAll: vi.fn(),
  orgGetAll: vi.fn(),
  createToken: vi.fn(),
  navigate: vi.fn(),
  isSuperAdmin: vi.fn(),
  getCurrentPlayer: vi.fn(),
}));

vi.mock('../../api/players', () => ({ playerAPI: { getAll: mocks.getAll } }));
vi.mock('../../api/organizations', () => ({ organizationAPI: { getAll: mocks.orgGetAll } }));
vi.mock('../../api/invite', () => ({ inviteAPI: { createToken: mocks.createToken } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ currentPlayer: { id: 1 } }) }));
vi.mock('../../utils/auth', () => ({
  isSuperAdmin: () => mocks.isSuperAdmin(),
  getCurrentPlayer: () => mocks.getCurrentPlayer(),
}));
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, useNavigate: () => mocks.navigate };
});
vi.mock('../../components/LoadingScreen', () => ({ default: () => <div>Loading...</div> }));

import PlayerList from './PlayerList';

const PLAYERS = [
  { id: 1, name: '新一', kyuRank: '', danRank: null, role: 'PLAYER', organizationIds: [], lastLoginAt: null },
  { id: 2, name: '北大太郎', kyuRank: 'A級', danRank: '四段', role: 'PLAYER', organizationIds: [10], lastLoginAt: null },
  { id: 3, name: 'わすら花子', kyuRank: 'C級', danRank: '弐段', role: 'PLAYER', organizationIds: [20], lastLoginAt: null },
];
const ORGS = [
  { id: 10, code: 'hokudai', name: '北海道大学かるた会', color: '#9b2335' },
  { id: 20, code: 'wasura', name: 'わすらもち会', color: '#16a34a' },
];

describe('PlayerList - 選択UI・団体フィルタ・一括編集導線', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((m) => m.mockReset());
    mocks.getAll.mockResolvedValue({ data: PLAYERS });
    mocks.orgGetAll.mockResolvedValue({ data: ORGS });
    // 既定は SUPER_ADMIN（フィルタ初期値=すべて）
    mocks.isSuperAdmin.mockReturnValue(true);
    mocks.getCurrentPlayer.mockReturnValue({ id: 1, role: 'SUPER_ADMIN', adminOrganizationId: null });
  });

  afterEach(() => cleanup());

  it('無所属フィルタで organizationIds が空の選手だけ表示する', async () => {
    render(<PlayerList />);
    expect(await screen.findByText('新一')).toBeTruthy();
    expect(screen.getByText('北大太郎')).toBeTruthy();

    const select = screen.getByLabelText('団体で絞り込み');
    await userEvent.selectOptions(select, 'NONE');

    expect(screen.getByText('新一')).toBeTruthy();
    expect(screen.queryByText('北大太郎')).toBeNull();
    expect(screen.queryByText('わすら花子')).toBeNull();
  });

  it('ADMIN は初期フィルタが自管轄団体になり、その団体の選手だけ表示する', async () => {
    mocks.isSuperAdmin.mockReturnValue(false);
    mocks.getCurrentPlayer.mockReturnValue({ id: 99, role: 'ADMIN', adminOrganizationId: 10 });

    render(<PlayerList />);

    expect(await screen.findByText('北大太郎')).toBeTruthy();
    expect(screen.queryByText('新一')).toBeNull();
    expect(screen.queryByText('わすら花子')).toBeNull();
  });

  it('招待ボタンは「すべて」では無効、具体的な団体選択で有効になる', async () => {
    render(<PlayerList />);
    await screen.findByText('新一');

    // 初期=すべて → 招待ボタン無効
    expect(screen.getByRole('button', { name: 'グループ用' })).toBeDisabled();

    // 具体的団体を選択 → 有効
    await userEvent.selectOptions(screen.getByLabelText('団体で絞り込み'), '10');
    expect(screen.getByRole('button', { name: 'グループ用' })).toBeEnabled();
  });

  it('選手を選択して一括編集すると bulk-edit へ選手配列を渡して遷移する', async () => {
    render(<PlayerList />);
    await screen.findByText('新一');

    await userEvent.click(screen.getByRole('button', { name: '新一を選択' }));
    await userEvent.click(screen.getByRole('button', { name: '北大太郎を選択' }));

    // 「一括編集（2人）」ボタン
    const bulkBtn = screen.getByRole('button', { name: /一括編集/ });
    expect(bulkBtn).toBeEnabled();
    await userEvent.click(bulkBtn);

    await waitFor(() => {
      expect(mocks.navigate).toHaveBeenCalledWith('/players/bulk-edit', expect.objectContaining({
        state: expect.objectContaining({
          players: expect.arrayContaining([
            expect.objectContaining({ id: 1 }),
            expect.objectContaining({ id: 2 }),
          ]),
        }),
      }));
    });
    // 選択した2人だけが渡る
    const passed = mocks.navigate.mock.calls[0][1].state.players;
    expect(passed).toHaveLength(2);
  });

  it('「新規登録」ボタンは SUPER_ADMIN のみ表示される', async () => {
    // ADMIN: 非表示（/players/new は SUPER_ADMIN 専用のため）
    mocks.isSuperAdmin.mockReturnValue(false);
    mocks.getCurrentPlayer.mockReturnValue({ id: 99, role: 'ADMIN', adminOrganizationId: 10 });
    const { unmount } = render(<PlayerList />);
    await screen.findByText('北大太郎');
    expect(screen.queryByRole('button', { name: /新規登録/ })).toBeNull();
    unmount();

    // SUPER_ADMIN: 表示
    mocks.isSuperAdmin.mockReturnValue(true);
    mocks.getCurrentPlayer.mockReturnValue({ id: 1, role: 'SUPER_ADMIN', adminOrganizationId: null });
    render(<PlayerList />);
    await screen.findByText('新一');
    expect(screen.getByRole('button', { name: /新規登録/ })).toBeTruthy();
  });
});
