// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Home from './page';
import { apiRequest, ApiError } from './api';

const auth = vi.hoisted(() => {
  process.env.NEXT_PUBLIC_SUPABASE_URL = 'https://test.supabase.co';
  process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY = 'test-anon';
  return { getSession: vi.fn(), onAuthStateChange: vi.fn(), signOut: vi.fn(), signInWithOAuth: vi.fn() };
});
vi.mock('@supabase/supabase-js', () => ({ createClient: () => ({ auth }) }));
vi.mock('./api', async importOriginal => ({ ...await importOriginal<typeof import('./api')>(), apiRequest: vi.fn() }));

beforeEach(() => {
  auth.getSession.mockResolvedValue({ data: { session: { access_token: 'approved-token', user: { id: 'admin', email: 'admin@test.local' } } } });
  auth.onAuthStateChange.mockReturnValue({ data: { subscription: { unsubscribe: vi.fn() } } });
  auth.signOut.mockResolvedValue({ error: null });
  vi.mocked(apiRequest).mockImplementation(async (path, _token, options) => {
    if (options) return {} as never;
    if (path === '/admin/overview') return { games: 1, levels: 0, questions: 0 } as never;
    if (path === '/admin/games') return [{ id: 'game', name: 'Objects', slug: 'objects', is_published: false }] as never;
    return [] as never;
  });
});
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('Studio management workflows', () => {
  it('blocks the management UI for a signed-in but unapproved Google account', async () => {
    vi.mocked(apiRequest).mockRejectedValue(new ApiError('Administrator access is not permitted', 403));
    render(<Home />);
    expect(await screen.findByRole('heading', { name: 'Administrator access required' })).toBeTruthy();
    expect(screen.queryByRole('navigation')).toBeNull();
  });

  it('creates draft games and sends publication changes with the administrator session', async () => {
    const user = userEvent.setup();
    render(<Home />);
    await user.click(await screen.findByRole('button', { name: 'games' }));
    await waitFor(() => expect((screen.getByRole('button', { name: 'Create game' }) as HTMLButtonElement).disabled).toBe(false));
    await user.type(screen.getByLabelText('Name'), 'Animals');
    await user.type(screen.getByLabelText('Slug'), 'animals');
    await user.click(screen.getByRole('button', { name: 'Create game' }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith('/admin/games', 'approved-token', expect.objectContaining({ method: 'POST' })));
    const create = vi.mocked(apiRequest).mock.calls.find(call => call[2]?.method === 'POST');
    expect(JSON.parse(String(create?.[2]?.body))).toMatchObject({ name: 'Animals', slug: 'animals', is_published: false });
    await waitFor(() => expect((screen.getByRole('button', { name: 'Publish' }) as HTMLButtonElement).disabled).toBe(false));
    await user.click(screen.getByRole('button', { name: 'Publish' }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith('/admin/games/game', 'approved-token', {
      method: 'PATCH', body: JSON.stringify({ is_published: true }),
    }));
  });

  it('displays backend readiness issues and disables publishing incomplete levels', async () => {
    const original = vi.mocked(apiRequest).getMockImplementation()!;
    vi.mocked(apiRequest).mockImplementation(async (path, ...args) => path === '/admin/levels'
      ? [{ id: 'level', name: 'Round 1', question_count: 5, is_published: false,
          publication_issues: ['Assign exactly 5 questions (0 selected).'] }] as never
      : original(path, ...args));
    const user = userEvent.setup(); render(<Home />);
    await user.click(await screen.findByRole('button', { name: 'levels' }));
    expect(await screen.findByText('Assign exactly 5 questions (0 selected).')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Publish' }) as HTMLButtonElement).disabled).toBe(true);
  });
});
