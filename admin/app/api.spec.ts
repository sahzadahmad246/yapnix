import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, ApiError } from './api';

afterEach(() => vi.unstubAllGlobals());
describe('Studio API transport', () => {
  it('lets the browser set multipart boundaries and forwards bearer authorization', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
    vi.stubGlobal('fetch', fetch);
    await apiRequest('/admin/media', 'token', { method: 'POST', body: new FormData() });
    expect(fetch.mock.calls[0][1].headers).toEqual({ Authorization: 'Bearer token' });
  });
  it('retains HTTP status and validation messages for access/error handling', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403, json: async () => ({ message: ['Not approved'] }) }));
    await expect(apiRequest('/admin/games', 'token')).rejects.toEqual(new ApiError('Not approved', 403));
  });
});
