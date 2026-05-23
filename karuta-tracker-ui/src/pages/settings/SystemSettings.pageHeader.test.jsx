import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  currentPlayer: { id: 1, role: 'SUPER_ADMIN', adminOrganizationId: null },
  settingsGetAll: vi.fn(),
  organizationsGetAll: vi.fn(),
}));

vi.mock('../../api', () => ({
  systemSettingsAPI: {
    getAll: mocks.settingsGetAll,
    update: vi.fn(),
  },
  organizationAPI: {
    getAll: mocks.organizationsGetAll,
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: mocks.currentPlayer }),
}));

vi.mock('../../components/PageHeader', () => ({
  default: ({ title, backTo, rightActions }) => (
    <div data-testid="page-header" data-title={title} data-back-to={backTo}>
      {rightActions}
    </div>
  ),
}));

vi.mock('../../components/LoadingScreen', () => ({
  default: () => <div>Loading...</div>,
}));

import SystemSettings from './SystemSettings';

const renderPage = (path = '/admin/settings') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <SystemSettings />
    </MemoryRouter>
  );

describe('SystemSettings PageHeader integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.currentPlayer = { id: 1, role: 'SUPER_ADMIN', adminOrganizationId: null };
    mocks.organizationsGetAll.mockResolvedValue({
      data: [{ id: 1, name: 'Org 1' }, { id: 2, name: 'Org 2' }],
    });
    mocks.settingsGetAll.mockResolvedValue({
      data: [
        { settingKey: 'lottery_deadline_days_before', settingValue: '-1' },
        { settingKey: 'lottery_normal_reserve_percent', settingValue: '30' },
      ],
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('passes "システム設定" title and /admin/lottery backTo to PageHeader (loading state)', async () => {
    // 初回描画時は loading=true の早期return が走るため、その時点の PageHeader を検証
    renderPage('/admin/settings?organizationId=2');
    const header = await screen.findByTestId('page-header');
    expect(header).toHaveAttribute('data-title', 'システム設定');
    expect(header).toHaveAttribute('data-back-to', '/admin/lottery');
  });
});
