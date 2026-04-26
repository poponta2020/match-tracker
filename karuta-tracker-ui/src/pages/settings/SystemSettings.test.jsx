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
        { settingKey: 'lottery_normal_reserve_percent', settingValue: '30' },
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

    await user.click(screen.getByRole('button'));
    const buttons = screen.getAllByRole('button');
    await user.click(buttons[buttons.length - 1]);

    await waitFor(() => {
      expect(mocks.settingsUpdate).toHaveBeenCalledWith(
        'lottery_deadline_days_before',
        '-1',
        2
      );
      expect(mocks.settingsUpdate).toHaveBeenCalledWith(
        'lottery_normal_reserve_percent',
        '30',
        2
      );
    });
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
            { settingKey: 'lottery_normal_reserve_percent', settingValue: '40' },
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
          { settingKey: 'lottery_normal_reserve_percent', settingValue: '99' },
        ],
      });
    });

    expect(screen.getByDisplayValue('40')).toBeInTheDocument();
    expect(screen.queryByDisplayValue('99')).not.toBeInTheDocument();
  });
});
