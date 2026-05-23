import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

const mocks = vi.hoisted(() => ({
  venueGetById: vi.fn(),
}));

vi.mock('../../api', () => ({
  venueAPI: {
    getById: mocks.venueGetById,
    create: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('../../components/PageHeader', () => ({
  default: ({ title, backTo, rightActions }) => (
    <div data-testid="page-header" data-title={title} data-back-to={backTo}>
      {rightActions}
    </div>
  ),
}));

import VenueForm from './VenueForm';

const renderAt = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/venues/new" element={<VenueForm />} />
        <Route path="/venues/edit/:id" element={<VenueForm />} />
      </Routes>
    </MemoryRouter>
  );

describe('VenueForm PageHeader integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.venueGetById.mockResolvedValue({
      data: {
        id: 5,
        name: '〇〇公民館',
        defaultMatchCount: 3,
        capacity: 24,
        schedules: [
          { matchNumber: 1, startTime: '13:00:00', endTime: '13:30:00' },
        ],
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it('passes "新規会場登録" title and /venues backTo in create mode', async () => {
    renderAt('/venues/new');
    const header = await screen.findByTestId('page-header');
    expect(header).toHaveAttribute('data-title', '新規会場登録');
    expect(header).toHaveAttribute('data-back-to', '/venues');
  });

  it('passes "会場編集" title and /venues backTo in edit mode', async () => {
    renderAt('/venues/edit/5');
    const header = await screen.findByTestId('page-header');
    await waitFor(() => {
      expect(header).toHaveAttribute('data-title', '会場編集');
    });
    expect(header).toHaveAttribute('data-back-to', '/venues');
  });
});
