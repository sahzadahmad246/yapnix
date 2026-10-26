// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CategoryForm, GameForm } from './game-form';
import { apiRequest } from './api';
vi.mock('./api', () => ({ apiRequest: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('game and sub-game library icons', () => {
  it('searches icons and creates a game family without uploading a file', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    render(<CategoryForm token="admin" busy={false} onBusyChange={() => {}} onSave={save} onCancel={() => {}}/>);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Category name'), 'Guess');
    await user.type(screen.getByLabelText('Search icons'), 'mystery');
    expect(screen.queryByRole('button', { name: 'Select Animals icon' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Select Guess icon' }));
    expect(screen.getByRole('button', { name: 'Select Guess icon' }).getAttribute('aria-pressed')).toBe('true');
    await user.click(screen.getByRole('button', { name: 'Create category' }));
    expect(save).toHaveBeenCalledWith({ name: 'Guess', slug: 'guess', description: '', icon_key: 'question', icon_storage_path: null });
    expect(apiRequest).not.toHaveBeenCalled();
  });
  it('replaces an existing uploaded sub-game image with a bundled icon', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    render(<GameForm row={{ id: 'animals', name: 'Animals', slug: 'animals', category_id: 'guess', icon_storage_path: 'old.webp', icon_url: 'https://storage.test/old' }} categories={[{ id: 'guess', name: 'Guess' }]} categoryId="guess" prompts={[{key:'what_is_this',label:'What is this?'}]} token="admin" busy={false} onBusyChange={() => {}} onSave={save} onCancel={() => {}}/>);
    const user = userEvent.setup();
    expect(screen.getByRole('img', { name: 'Selected artwork' })).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Icon library' }));
    await user.click(screen.getByRole('button', { name: 'Select Animals icon' }));
    await user.click(screen.getByRole('button', { name: 'Save game' }));
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ icon_key: 'paw-print', icon_storage_path: null }));
    expect(apiRequest).not.toHaveBeenCalled();
  });
  it('retains custom square image uploads as an alternative', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    vi.mocked(apiRequest).mockResolvedValue({ storage_path: 'artwork/custom.webp', image_url: 'https://storage.test/custom', original_filename: 'custom.png' });
    render(<CategoryForm token="admin" busy={false} onBusyChange={() => {}} onSave={save} onCancel={() => {}}/>);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Category name'), 'Guess');
    await user.click(screen.getByRole('button', { name: 'Custom image' }));
    expect((screen.getByRole('button', { name: 'Create category' }) as HTMLButtonElement).disabled).toBe(true);
    await user.upload(screen.getByLabelText('Upload square artwork'), new File(['png'], 'custom.png', { type: 'image/png' }));
    await waitFor(() => expect((screen.getByRole('button', { name: 'Create category' }) as HTMLButtonElement).disabled).toBe(false));
    await user.click(screen.getByRole('button', { name: 'Create category' }));
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ icon_storage_path: 'artwork/custom.webp' }));
    expect(apiRequest).toHaveBeenCalledWith('/admin/media/artwork', 'admin', expect.objectContaining({ method: 'POST' }));
  });
});
