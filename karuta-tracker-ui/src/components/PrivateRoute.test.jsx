import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import PrivateRoute from './PrivateRoute';

// useAuth をテストごとに差し替える（認証状態・loading・プロフィール有無を制御）。
let mockAuth = { isAuthenticated: false, loading: false, currentPlayer: null };
vi.mock('../context/AuthContext', () => ({
  useAuth: () => mockAuth,
}));

const PROTECTED = <div>protected content</div>;

const renderPR = ({ children = PROTECTED, publicFallback, initialPath = '/' } = {}) =>
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/profile/edit" element={<div>profile setup</div>} />
        <Route
          path="*"
          element={<PrivateRoute publicFallback={publicFallback}>{children}</PrivateRoute>}
        />
      </Routes>
    </MemoryRouter>
  );

beforeEach(() => {
  mockAuth = { isAuthenticated: false, loading: false, currentPlayer: null };
});
afterEach(() => cleanup());

describe('PrivateRoute', () => {
  it('loading 中はスピナーを出し children を描画しない', () => {
    mockAuth = { isAuthenticated: false, loading: true, currentPlayer: null };
    const { container } = renderPR();
    expect(container.querySelector('.animate-spin')).not.toBeNull();
    expect(screen.queryByText('protected content')).toBeNull();
  });

  it('未認証 + publicFallback あり → fallback を描画（/login にリダイレクトしない）(AC-3a)', () => {
    mockAuth = { isAuthenticated: false, loading: false, currentPlayer: null };
    renderPR({ publicFallback: <div>landing page</div> });
    expect(screen.getByText('landing page')).toBeInTheDocument();
    expect(screen.queryByText('login page')).toBeNull();
    expect(screen.queryByText('protected content')).toBeNull();
  });

  it('未認証 + publicFallback なし → /login にリダイレクトする（既存挙動）(AC-3b)', () => {
    mockAuth = { isAuthenticated: false, loading: false, currentPlayer: null };
    renderPR();
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('protected content')).toBeNull();
  });

  it('認証済み（kyuRank あり）→ children を描画する (AC-3c)', () => {
    mockAuth = { isAuthenticated: true, loading: false, currentPlayer: { kyuRank: 'A級' } };
    renderPR();
    expect(screen.getByText('protected content')).toBeInTheDocument();
    expect(screen.queryByText('login page')).toBeNull();
  });

  it('publicFallback は認証済みユーザーには影響しない（children を描画）', () => {
    mockAuth = { isAuthenticated: true, loading: false, currentPlayer: { kyuRank: 'A級' } };
    renderPR({ publicFallback: <div>landing page</div> });
    expect(screen.getByText('protected content')).toBeInTheDocument();
    expect(screen.queryByText('landing page')).toBeNull();
  });

  it('認証済みだがプロフィール未設定（kyuRank なし）→ /profile/edit にリダイレクト（既存挙動・回帰）', () => {
    mockAuth = { isAuthenticated: true, loading: false, currentPlayer: { kyuRank: null } };
    renderPR({ initialPath: '/' });
    expect(screen.getByText('profile setup')).toBeInTheDocument();
    expect(screen.queryByText('protected content')).toBeNull();
  });
});
