import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  currentPlayer: { id: 1, role: 'ADMIN', adminOrganizationId: 1 },
  getMonthlyApplicants: vi.fn(),
  isConfirmed: vi.fn(),
  organizationsGetAll: vi.fn(),
}));

vi.mock('../../api/lottery', () => ({
  lotteryAPI: {
    getMonthlyApplicants: mocks.getMonthlyApplicants,
    isConfirmed: mocks.isConfirmed,
    executeLottery: vi.fn(),
    confirmLottery: vi.fn(),
    notifyWon: vi.fn(),
    notifyWaitlisted: vi.fn(),
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: mocks.organizationsGetAll,
  },
}));

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ currentPlayer: mocks.currentPlayer }),
}));

vi.mock('../../utils/auth', () => ({
  isSuperAdmin: () => false,
}));

vi.mock('../../components/PageHeader', () => ({
  default: ({ title, backTo, rightActions }) => (
    <div data-testid="page-header" data-title={title} data-back-to={backTo}>
      <div data-testid="right-actions">{rightActions}</div>
    </div>
  ),
}));

import LotteryManagement from './LotteryManagement';

let locationRef = null;
const LocationProbe = () => {
  locationRef = useLocation();
  return null;
};

const renderPage = (initialEntry = '/admin/lottery') =>
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <LocationProbe />
      <Routes>
        <Route path="/admin/lottery" element={<LotteryManagement />} />
        <Route path="/admin/settings" element={<div>System Settings Page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('LotteryManagement PageHeader integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    locationRef = null;
    mocks.currentPlayer = { id: 1, role: 'ADMIN', adminOrganizationId: 1 };
    mocks.getMonthlyApplicants.mockResolvedValue({ data: { applicants: [] } });
    mocks.isConfirmed.mockResolvedValue({ data: { confirmed: false } });
    mocks.organizationsGetAll.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    cleanup();
  });

  it('passes "抽選管理" title and /settings backTo to PageHeader', async () => {
    renderPage();
    const header = await screen.findByTestId('page-header');
    expect(header).toHaveAttribute('data-title', '抽選管理');
    expect(header).toHaveAttribute('data-back-to', '/settings');
  });

  it('renders the "システム設定" rightAction and navigates to /admin/settings when clicked', async () => {
    renderPage();
    const button = await screen.findByRole('button', { name: /システム設定/ });
    expect(button).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(button);
    await waitFor(() => {
      expect(locationRef.pathname).toBe('/admin/settings');
    });
  });
});
