import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const mocks = vi.hoisted(() => ({
  bulkUpdate: vi.fn(),
  orgGetAll: vi.fn(),
  navigate: vi.fn(),
  location: vi.fn(),
}));

vi.mock('../../api/players', () => ({ playerAPI: { bulkUpdate: mocks.bulkUpdate } }));
vi.mock('../../api/organizations', () => ({ organizationAPI: { getAll: mocks.orgGetAll } }));
vi.mock('../../components/PageHeader', () => ({
  default: ({ title, backTo }) => <div data-testid="page-header" data-title={title} data-back-to={backTo} />,
}));
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useNavigate: () => mocks.navigate,
    useLocation: () => mocks.location(),
    Navigate: ({ to }) => <div data-testid="redirect" data-to={to} />,
  };
});

import PlayerBulkEdit from './PlayerBulkEdit';

const ORGS = [
  { id: 10, code: 'hokudai', name: '北海道大学かるた会', color: '#9b2335' },
  { id: 20, code: 'wasura', name: 'わすらもち会', color: '#16a34a' },
];

const makePlayers = () => [
  { id: 1, name: '新一', gender: '男性', kyuRank: '', danRank: null, karutaClub: '', organizationIds: [] },
  { id: 2, name: '新二', gender: '男性', kyuRank: '', danRank: null, karutaClub: '', organizationIds: [] },
];

const renderWith = (state) => {
  mocks.location.mockReturnValue({ state });
  return render(<PlayerBulkEdit />);
};

const save = async () => {
  await userEvent.click(screen.getByRole('button', { name: '保存' }));
  expect(await screen.findByText('一括更新の確認')).toBeTruthy();
  await userEvent.click(screen.getByRole('button', { name: '適用する' }));
};

describe('PlayerBulkEdit', () => {
  beforeEach(() => {
    Object.values(mocks).forEach((m) => m.mockReset());
    mocks.orgGetAll.mockResolvedValue({ data: ORGS });
    mocks.bulkUpdate.mockResolvedValue({ data: [] });
  });

  afterEach(() => cleanup());

  it('router state が無い場合は一覧へリダイレクトする', () => {
    renderWith(null);
    const redirect = screen.getByTestId('redirect');
    expect(redirect).toHaveAttribute('data-to', '/players');
  });

  it('「全員をE級に」で全員の級＝E級・段位＝無段になり、保存ペイロードに反映される', async () => {
    renderWith({ players: makePlayers() });

    await userEvent.click(screen.getByRole('button', { name: '全員をE級に' }));
    await save();

    await waitFor(() => expect(mocks.bulkUpdate).toHaveBeenCalledTimes(1));
    const updates = mocks.bulkUpdate.mock.calls[0][0];
    expect(updates).toHaveLength(2);
    updates.forEach((u) => {
      expect(u.kyuRank).toBe('E級');
      expect(u.danRank).toBe('無段');
    });
    // 成功後は一覧へ戻る
    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledWith('/players'));
  });

  it('「全員に北大を追加」で addOrganizationIds に北大(10)が追加される', async () => {
    renderWith({ players: makePlayers() });

    // 団体ロード後にボタンが有効化される
    const addBtn = await screen.findByRole('button', { name: '全員に北大を追加' });
    await waitFor(() => expect(addBtn).toBeEnabled());
    await userEvent.click(addBtn);
    await save();

    await waitFor(() => expect(mocks.bulkUpdate).toHaveBeenCalledTimes(1));
    const updates = mocks.bulkUpdate.mock.calls[0][0];
    updates.forEach((u) => {
      expect(u.addOrganizationIds).toContain(10);
      // 所属追加だけのつもりなら、未変更の属性は送らない（他項目を上書きしない）
      expect(u.gender).toBeNull();
      expect(u.kyuRank).toBeNull();
    });
  });

  it('行ごとの性別変更が該当選手のみ保存ペイロードに反映される', async () => {
    renderWith({ players: makePlayers() });

    await userEvent.selectOptions(screen.getByLabelText('新一の性別'), '女性');
    await save();

    await waitFor(() => expect(mocks.bulkUpdate).toHaveBeenCalledTimes(1));
    const updates = mocks.bulkUpdate.mock.calls[0][0];
    expect(updates.find((u) => u.playerId === 1).gender).toBe('女性'); // 変更した行のみ送信
    expect(updates.find((u) => u.playerId === 2).gender).toBeNull();   // 未変更の行は null（据え置き）
  });

  it('保存ボタンで確認ダイアログを表示し、適用するで bulkUpdate を呼ぶ', async () => {
    renderWith({ players: makePlayers() });

    await userEvent.click(screen.getByRole('button', { name: '保存' }));
    expect(await screen.findByText('一括更新の確認')).toBeTruthy();
    expect(screen.getByText(/2人に以下の変更を適用します/)).toBeTruthy();

    await userEvent.click(screen.getByRole('button', { name: '適用する' }));
    await waitFor(() => expect(mocks.bulkUpdate).toHaveBeenCalledTimes(1));
  });

  it('未変更の項目は（設定済みでも）null で送信され、据え置かれる（同時更新を巻き戻さない）', async () => {
    // 何も触らずに保存 → 全項目 null（性別が設定済みでも送らない＝他者の更新を上書きしない）
    renderWith({ players: makePlayers() });
    await save();

    await waitFor(() => expect(mocks.bulkUpdate).toHaveBeenCalledTimes(1));
    const u = mocks.bulkUpdate.mock.calls[0][0].find((x) => x.playerId === 1);
    expect(u.kyuRank).toBeNull();
    expect(u.danRank).toBeNull();
    expect(u.karutaClub).toBeNull();
    expect(u.gender).toBeNull(); // 設定済み(男性)でも未変更なら送らない
  });

  it('級の空（初心者）オプションは無効化され、設定済みの値を空へ戻せない', () => {
    renderWith({ players: makePlayers() });
    const kyuSelect = screen.getByLabelText('新一の級');
    const emptyOption = within(kyuSelect).getByRole('option', { name: '初心者' });
    expect(emptyOption).toBeDisabled();
  });

  it('A級では段位の選択肢が四段〜八段に限定される（不整合な低段位を選べない）', async () => {
    renderWith({ players: makePlayers() });
    await userEvent.selectOptions(screen.getByLabelText('新一の級'), 'A級');
    const danSelect = screen.getByLabelText('新一の段位');
    expect(within(danSelect).getByRole('option', { name: '四段' })).toBeTruthy();
    expect(within(danSelect).getByRole('option', { name: '八段' })).toBeTruthy();
    expect(within(danSelect).queryByRole('option', { name: '無段' })).toBeNull();
    expect(within(danSelect).queryByRole('option', { name: '参段' })).toBeNull();
  });
});
