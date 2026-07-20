import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import apiClient from './client';

describe('apiClient request interceptor', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('attaches Authorization: Bearer from authToken', async () => {
    localStorage.setItem('authToken', 'real-token-abc123');

    const requestInterceptor = apiClient.interceptors.request.handlers[0].fulfilled;
    const config = await requestInterceptor({ headers: {} });

    expect(config.headers.Authorization).toBe('Bearer real-token-abc123');
  });

  it('does not attach X-User-Role / X-User-Id headers even when currentPlayer is present', async () => {
    localStorage.setItem('authToken', 'real-token-abc123');
    localStorage.setItem(
      'currentPlayer',
      JSON.stringify({ id: 1, role: 'SUPER_ADMIN', name: 'テスト太郎' })
    );

    const requestInterceptor = apiClient.interceptors.request.handlers[0].fulfilled;
    const config = await requestInterceptor({ headers: {} });

    expect(config.headers['X-User-Role']).toBeUndefined();
    expect(config.headers['X-User-Id']).toBeUndefined();
  });

  it('does not attach Authorization header when there is no authToken', async () => {
    const requestInterceptor = apiClient.interceptors.request.handlers[0].fulfilled;
    const config = await requestInterceptor({ headers: {} });

    expect(config.headers.Authorization).toBeUndefined();
  });
});
