// @vitest-environment jsdom
import React from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ImagePicker } from './image-picker';
import { apiRequest } from './api';

vi.mock('./api', () => ({ apiRequest: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

function mount() {
  const onChange = vi.fn();
  const onBusyChange = vi.fn();
  render(<ImagePicker token="approved-session" value="" disabled={false} onChange={onChange} onBusyChange={onBusyChange} />);
  return { onChange, onBusyChange, user: userEvent.setup() };
}

describe('Studio image uploads', () => {
  it('uploads with the administrator token and selects the returned image', async () => {
    const image = { storage_path: 'questions/image.webp', image_url: 'https://storage.test/signed', original_filename: 'cat.png' };
    vi.mocked(apiRequest).mockResolvedValue(image);
    const { user, onChange, onBusyChange } = mount();
    await user.upload(screen.getByLabelText('Upload question image'), new File(['png'], 'cat.png', { type: 'image/png' }));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(image));
    const [path, token, options] = vi.mocked(apiRequest).mock.calls[0];
    expect(path).toBe('/admin/media'); expect(token).toBe('approved-session');
    expect(options?.body).toBeInstanceOf(FormData);
    expect(onBusyChange.mock.calls).toEqual([[true], [false]]);
  });
  it('displays failed uploads and restores controls', async () => {
    vi.mocked(apiRequest).mockRejectedValue(new Error('Storage unavailable'));
    const { user, onChange } = mount();
    await user.upload(screen.getByLabelText('Upload question image'), new File(['png'], 'cat.png', { type: 'image/png' }));
    expect((await screen.findByRole('alert')).textContent).toContain('Storage unavailable');
    expect(onChange).not.toHaveBeenCalled();
    expect((screen.getByLabelText('Upload question image') as HTMLInputElement).disabled).toBe(false);
  });
  it('allows reuse of an image from the private media library', async () => {
    const image = { storage_path: 'cat.webp', image_url: 'https://storage.test/signed', original_filename: 'cat.png' };
    vi.mocked(apiRequest).mockResolvedValue([image]);
    const { user, onChange } = mount();
    await user.click(screen.getByRole('button', { name: 'Browse uploaded images' }));
    await user.click(await screen.findByRole('button', { name: /cat\.png/ }));
    expect(onChange).toHaveBeenCalledWith(image);
  });
});
