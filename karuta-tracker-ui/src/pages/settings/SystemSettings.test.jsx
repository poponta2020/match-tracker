import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useNavigate } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  currentPlayer: { id: 1, role: 'SUPER_ADMIN', adminOrganizationId: null },
  settingsGetAll: vi.fn(),
  settingsUpdate: vi.fn(),
  organizationsGetAll: vi.fn(),
}));

vi.mock('../../api', () => ({
  systemSettingsAPI: {
    getAll: mocks.settingsGetAll,
    update: mocks.settingsUpdate,
  },
  organizationAPI: {
    getAll: mocks.organizationsGetAll,
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    currentPlayer: mocks.currentPlayer,
  }),
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

import SystemSettings from './SystemSettings';

let navigateRef = null;
const NavigationHelper = () => {
  navigateRef = useNavigate();
  return null;
};

const renderPage = (path = '/admin/settings') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <NavigationHelper />
      <SystemSettings />
    </MemoryRouter>
  );

describe('SystemSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.currentPlayer = { id: 1, role: 'SUPER_ADMIN', adminOrganizationId: null };
    mocks.organizationsGetAll.mockResolvedValue({
      data: [
        { id: 1, name: 'Org 1' },
        { id: 2, name: 'Org 2' },
      ],
    });
    mocks.settingsGetAll.mockResolvedValue({
      data: [
        { settingKey: 'lottery_deadline_days_before', settingValue: '-1' },
        { settingKey: 'lottery_weight_cap_percentile', settingValue: '30' },
      ],
    });
    mocks.settingsUpdate.mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    cleanup();
  });

  it('uses the selected organization when SUPER_ADMIN saves settings', async () => {
    const user = userEvent.setup();
    renderPage('/admin/settings?organizationId=2');

    const organizationSelect = await screen.findByRole('combobox');
    expect(organizationSelect).toHaveValue('2');

    await waitFor(() => {
      expect(mocks.settingsGetAll).toHaveBeenCalledWith(2);
    });

    await user.click(screen.getByRole('button', { name: '保存' }));
    await user.click(screen.getByRole('button', { name: '変更する' }));

    await waitFor(() => {
      expect(mocks.settingsUpdate).toHaveBeenCalledWith(
        'lottery_deadline_days_before',
        '-1',
        2
      );
      expect(mocks.settingsUpdate).toHaveBeenCalledWith(
        'lottery_weight_cap_percentile',
        '30',
        2
      );
    });

    expect(mocks.settingsUpdate).not.toHaveBeenCalledWith(
      'lottery_normal_reserve_percent',
      expect.anything(),
      expect.anything()
    );
  });

  it('ignores a stale settings response that arrives after the organization changed', async () => {
    let resolveOrg1Settings;
    const org1Pending = new Promise((resolve) => {
      resolveOrg1Settings = resolve;
    });

    mocks.settingsGetAll.mockImplementation((orgId) => {
      if (orgId === 1) return org1Pending;
      if (orgId === 2) {
        return Promise.resolve({
          data: [
            { settingKey: 'lottery_deadline_days_before', settingValue: '3' },
            { settingKey: 'lottery_weight_cap_percentile', settingValue: '40' },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });

    renderPage('/admin/settings?organizationId=1');

    await waitFor(() => {
      expect(mocks.settingsGetAll).toHaveBeenCalledWith(1);
    });

    await act(async () => {
      navigateRef('/admin/settings?organizationId=2');
    });

    await waitFor(() => {
      expect(mocks.settingsGetAll).toHaveBeenCalledWith(2);
    });
    await waitFor(() => {
      expect(screen.getByDisplayValue('40')).toBeInTheDocument();
    });

    await act(async () => {
      resolveOrg1Settings({
        data: [
          { settingKey: 'lottery_deadline_days_before', settingValue: '-1' },
          { settingKey: 'lottery_weight_cap_percentile', settingValue: '99' },
        ],
      });
    });

    expect(screen.getByDisplayValue('40')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('99')).not.toBeInTheDocument();
  });

  it('reflects lottery_weight_cap_percentile from getAll into the input', async () => {
    mocks.settingsGetAll.mockResolvedValue({
      data: [
        { settingKey: 'lottery_deadline_days_before', settingValue: '-1' },
        { settingKey: 'lottery_weight_cap_percentile', settingValue: '45' },
      ],
    });

    renderPage('/admin/settings?organizationId=2');

    await waitFor(() => {
      expect(screen.getByDisplayValue('45')).toBeInTheDocument();
    });
  });

  it('clamps an out-of-range stored percentile into the input on load', async () => {
    mocks.settingsGetAll.mockResolvedValue({
      data: [
        { settingKey: 'lottery_weight_cap_percentile', settingValue: '150' },
      ],
    });

    renderPage('/admin/settings?organizationId=2');

    // 保存値150はバックエンド getter と同じく100にクランプして表示し、実効値と一致させる
    await waitFor(() => {
      expect(screen.getByDisplayValue('100')).toBeInTheDocument();
    });
    expect(screen.queryByDisplayValue('150')).not.toBeInTheDocument();
  });

  it('falls back to the default percentile for a non-numeric stored value', async () => {
    mocks.settingsGetAll.mockResolvedValue({
      data: [
        { settingKey: 'lottery_weight_cap_percentile', settingValue: 'abc' },
      ],
    });

    renderPage('/admin/settings?organizationId=2');

    await waitFor(() => {
      expect(screen.getByDisplayValue('30')).toBeInTheDocument();
    });
  });

  it('rejects out-of-range percentile input and keeps the previous value', async () => {
    const user = userEvent.setup();
    renderPage('/admin/settings?organizationId=2');

    await screen.findByDisplayValue('30');
    const percentileInput = screen.getByDisplayValue('30');

    await user.clear(percentileInput);
    await user.type(percentileInput, '150');

    expect(percentileInput).toHaveValue(30);

    await user.clear(percentileInput);
    await user.type(percentileInput, '-5');

    expect(percentileInput).toHaveValue(30);
  });

  it('does not render the removed "一般枠の最低保証割合" card', async () => {
    renderPage('/admin/settings?organizationId=2');

    await screen.findByRole('combobox');

    expect(screen.queryByText('一般枠の最低保証割合')).not.toBeInTheDocument();
  });

  it('shows the "抽選の仕組み" explanation section', async () => {
    renderPage('/admin/settings?organizationId=2');

    await screen.findByRole('combobox');

    expect(screen.getByText('抽選の仕組み')).toBeInTheDocument();
    expect(screen.getByText(/その日の一巡/)).toBeInTheDocument();
    expect(screen.getAllByText(/直近30日/).length).toBeGreaterThan(0);
  });
});
