// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
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
    expect(screen.queryByLabelText('Name')).toBeNull();
    await waitFor(() => expect((screen.getByRole('button', { name: 'New game' }) as HTMLButtonElement).disabled).toBe(false));
    await user.click(screen.getByRole('button', { name: 'New game' }));
    await user.type(screen.getByLabelText('Name'), 'Animals');
    await user.type(screen.getByLabelText('Slug'), 'animals');
    await user.click(screen.getByRole('button', { name: 'Create game' }));
    await waitFor(() => expect(apiRequest).toHaveBeenCalledWith('/admin/games', 'approved-token', expect.objectContaining({ method: 'POST' })));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
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
  it('closes create forms with Cancel and Escape, and keeps failed submissions open', async () => {
    const user = userEvent.setup(); render(<Home />);
    await user.click(await screen.findByRole('button', { name: 'games' }));
    await waitFor(() => expect((screen.getByRole('button', { name: 'New game' }) as HTMLButtonElement).disabled).toBe(false));
    await user.click(screen.getByRole('button', { name: 'New game' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'New game' }));
    await user.click(screen.getByLabelText('Name'));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'New game' }));
    await user.type(screen.getByLabelText('Name'), 'Animals');
    await user.type(screen.getByLabelText('Slug'), 'animals');
    vi.mocked(apiRequest).mockRejectedValueOnce(new Error('This slug already exists'));
    await user.click(screen.getByRole('button', { name: 'Create game' }));
    expect((await screen.findByRole('alert')).textContent).toContain('This slug already exists');
    expect(screen.getByRole('dialog')).toBeTruthy();
    expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Animals');
  });

  it('adds an image and answer inside a game level, then saves its selected assignment and closes', async () => {
    const original = vi.mocked(apiRequest).getMockImplementation()!;
    const questions: Array<Record<string, unknown>> = [];
    vi.mocked(apiRequest).mockImplementation(async (path, token, options) => {
      if (path === '/admin/levels' && !options) return [{ id: 'level', game_id: 'game', games: { name: 'Objects' }, name: 'Round 1', question_count: 2, is_published: false, level_questions: [], publication_issues: [] }] as never;
      if (path === '/admin/questions' && !options) return questions as never;
      if (path === '/admin/media' && options?.method === 'POST') return { storage_path: 'questions/bike.webp', image_url: 'https://storage.test/bike', original_filename: 'photo.png' } as never;
      if (path === '/admin/questions' && options?.method === 'POST') {
        const value = JSON.parse(String(options.body));
        const question = { id: 'question', ...value, image_url: 'https://storage.test/bike', question_answers: [{ answer: 'bicycle' }, { answer: 'bike' }, { answer: 'cycle' }] };
        questions.push(question); return question as never;
      }
      return original(path, token, options);
    });
    const user = userEvent.setup(); render(<Home />);
    await user.click(await screen.findByRole('button', { name: 'games' }));
    await user.click(await screen.findByRole('button', { name: 'Levels' }));
    await screen.findByText('Round 1');
    expect((screen.getByLabelText('Filter levels by game') as HTMLSelectElement).value).toBe('game');
    await user.click(within(screen.getByRole('table')).getByRole('button', { name: 'Images & answers' }));
    await user.click(screen.getByRole('button', { name: 'Add new image & answer' }));
    await user.upload(screen.getByLabelText('Upload question image'), new File(['png'], 'photo.png', { type: 'image/png' }));
    await waitFor(() => expect((screen.getByLabelText('Correct name / answer') as HTMLInputElement).disabled).toBe(false));
    await user.type(screen.getByLabelText('Correct name / answer'), 'bicycle');
    await user.type(screen.getByLabelText('Other accepted names'), 'bike, cycle');
    await user.click(screen.getByRole('button', { name: 'Save image & answer' }));
    await screen.findByRole('checkbox', { name: 'Use bicycle' });
    const create = vi.mocked(apiRequest).mock.calls.find(call => call[0] === '/admin/questions' && call[2]?.method === 'POST');
    expect(JSON.parse(String(create?.[2]?.body))).toMatchObject({ image_storage_path: 'questions/bike.webp', primary_answer: 'bicycle', accepted_answers: ['bike', 'cycle'], language: 'en-US' });
    expect((screen.getByRole('checkbox', { name: 'Use bicycle' }) as HTMLInputElement).checked).toBe(true);
    await waitFor(() => expect((screen.getByRole('button', { name: 'Save level images' }) as HTMLButtonElement).disabled).toBe(false));
    await user.click(screen.getByRole('button', { name: 'Save level images' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(apiRequest).toHaveBeenCalledWith('/admin/levels/level/questions', 'approved-token', { method: 'PUT', body: JSON.stringify({ question_ids: ['question'] }) });
  });

});
