import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  playerGetById: vi.fn(),
  organizationsGetAll: vi.fn(),
}));

vi.mock('../../api/players', () => ({
  playerAPI: {
    getById: mocks.playerGetById,
    create: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('../../api/organizations', () => ({
  organizationAPI: {
    getAll: mocks.organizationsGetAll,
  },
}));

vi.mock('../../utils/auth', () => ({
  isSuperAdmin: () => false,
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

import PlayerEdit from './PlayerEdit';

const renderAt = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/players/new" element={<PlayerEdit />} />
        <Route path="/players/:id/edit" element={<PlayerEdit />} />
      </Routes>
    </MemoryRouter>
  );

describe('PlayerEdit PageHeader integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.organizationsGetAll.mockResolvedValue({ data: [] });
    mocks.playerGetById.mockResolvedValue({
      data: {
        id: 7,
        name: '山田 太郎',
        gender: 'MALE',
        dominantHand: 'RIGHT',
        danRank: '初段',
        kyuRank: '',
        karutaClub: '',
        remarks: '',
        role: 'PLAYER',
        adminOrganizationId: null,
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('passes "選手新規登録" title and /players backTo in create mode', async () => {
    renderAt('/players/new');
    const header = await screen.findByTestId('page-header');
    expect(header).toHaveAttribute('data-title', '選手新規登録');
    expect(header).toHaveAttribute('data-back-to', '/players');
  });

  it('passes "選手情報編集" title and /players/:id backTo in edit mode', async () => {
    renderAt('/players/7/edit');
    const header = await screen.findByTestId('page-header');
    await waitFor(() => {
      expect(header).toHaveAttribute('data-title', '選手情報編集');
    });
    expect(header).toHaveAttribute('data-back-to', '/players/7');
  });
});
