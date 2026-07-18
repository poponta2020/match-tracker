import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../api', () => ({
  playerAPI: {
    login: mocks.login,
    logout: mocks.logout,
  },
}));

import { AuthProvider, useAuth } from './AuthContext';

const wrapper = ({ children }) => <AuthProvider>{children}</AuthProvider>;

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('stores the token from the login response into authToken on successful login', async () => {
    mocks.login.mockResolvedValue({
      data: {
        id: 1,
        name: 'テスト太郎',
        role: 'PLAYER',
        firstLogin: false,
        token: 'server-issued-token-xyz',
      },
    });

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.login('テスト太郎', 'password123');
    });

    expect(localStorage.getItem('authToken')).toBe('server-issued-token-xyz');

    // currentPlayer の役割表示用データにはtokenを含めない
    const savedPlayer = JSON.parse(localStorage.getItem('currentPlayer'));
    expect(savedPlayer.token).toBeUndefined();
    expect(savedPlayer.role).toBe('PLAYER');
  });

  it('calls the logout API and clears the token on logout', async () => {
    mocks.login.mockResolvedValue({
      data: {
        id: 1,
        name: 'テスト太郎',
        role: 'PLAYER',
        firstLogin: false,
        token: 'server-issued-token-xyz',
      },
    });
    mocks.logout.mockResolvedValue({});

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.login('テスト太郎', 'password123');
    });

    await act(async () => {
      await result.current.logout();
    });

    expect(mocks.logout).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('authToken')).toBeNull();
    expect(localStorage.getItem('currentPlayer')).toBeNull();
  });

  it('still clears local auth state when the logout API call fails', async () => {
    mocks.login.mockResolvedValue({
      data: {
        id: 1,
        name: 'テスト太郎',
        role: 'PLAYER',
        firstLogin: false,
        token: 'server-issued-token-xyz',
      },
    });
    mocks.logout.mockRejectedValue(new Error('network error'));

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.login('テスト太郎', 'password123');
    });

    await act(async () => {
      await result.current.logout();
    });

    expect(mocks.logout).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('authToken')).toBeNull();
    expect(localStorage.getItem('currentPlayer')).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
  });
});
