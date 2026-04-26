import { beforeEach, describe, expect, it, vi } from 'vitest';

import apiClient from './client';
import venueReservationProxyAPI from './venueReservationProxy';

vi.mock('./client', () => ({
  default: {
    post: vi.fn(),
  },
}));

describe('venueReservationProxyAPI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('creates a proxy session with the backend request body', async () => {
    const payload = {
      venue: 'KADERU',
      practiceSessionId: 123,
      roomName: 'hamanasu',
      date: '2026-04-12',
      slotIndex: 2,
      returnUrl: 'https://app.example.com/practice',
    };
    const response = {
      data: {
        proxyToken: 'token-123',
        viewUrl: '/api/venue-reservation-proxy/view?token=token-123',
        venue: 'KADERU',
      },
    };
    apiClient.post.mockResolvedValue(response);

    await expect(venueReservationProxyAPI.createSession(payload)).resolves.toBe(response);
    expect(apiClient.post).toHaveBeenCalledWith('/venue-reservation-proxy/session', payload);
  });
});
