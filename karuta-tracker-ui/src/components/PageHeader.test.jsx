import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import PageHeader from './PageHeader';

let locationRef = null;
const LocationProbe = () => {
  locationRef = useLocation();
  return null;
};

const renderWithRouter = (ui, { initialEntries = ['/start'] } = {}) =>
  render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="*" element={
          <>
            <LocationProbe />
            {ui}
          </>
        } />
      </Routes>
    </MemoryRouter>
  );

describe('PageHeader', () => {
  beforeEach(() => {
    locationRef = null;
  });

  afterEach(() => {
    cleanup();
  });

  it('renders the title as an h1 heading', () => {
    renderWithRouter(<PageHeader title="プロフィール" backTo="/settings" />);
    const heading = screen.getByRole('heading', { level: 1, name: 'プロフィール' });
    expect(heading).toBeInTheDocument();
  });

  it('renders an accessible back button with aria-label', () => {
    renderWithRouter(<PageHeader title="通知設定" backTo="/settings" />);
    expect(screen.getByRole('button', { name: '戻る' })).toBeInTheDocument();
  });

  it('navigates to backTo when the back button is clicked', async () => {
    const user = userEvent.setup();
    renderWithRouter(<PageHeader title="通知設定" backTo="/settings" />, {
      initialEntries: ['/settings/notifications'],
    });
    expect(locationRef.pathname).toBe('/settings/notifications');

    await user.click(screen.getByRole('button', { name: '戻る' }));

    expect(locationRef.pathname).toBe('/settings');
  });

  it('renders rightActions when provided', () => {
    const onClick = vi.fn();
    renderWithRouter(
      <PageHeader
        title="通知"
        backTo="/"
        rightActions={<button onClick={onClick}>すべて削除</button>}
      />
    );
    expect(screen.getByRole('button', { name: 'すべて削除' })).toBeInTheDocument();
  });

  it('invokes rightActions handlers when used', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    renderWithRouter(
      <PageHeader
        title="通知"
        backTo="/"
        rightActions={<button onClick={onClick}>すべて削除</button>}
      />
    );
    await user.click(screen.getByRole('button', { name: 'すべて削除' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('renders only the back button when rightActions are omitted', () => {
    renderWithRouter(<PageHeader title="プロフィール" backTo="/settings" />);
    expect(screen.getAllByRole('button')).toHaveLength(1);
  });
});
