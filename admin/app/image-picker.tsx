'use client';

import React, { useState } from 'react';
import { apiRequest } from './api';

export interface MediaImage { storage_path: string; image_url: string | null; original_filename: string }

export function ImagePicker({ token, value, imageUrl, disabled, onChange, onBusyChange }: {
  token: string; value: string; imageUrl?: string | null; disabled: boolean;
  onChange: (image: MediaImage) => void; onBusyChange: (busy: boolean) => void;
}) {
  const [images, setImages] = useState<MediaImage[]>([]);
  const [showLibrary, setShowLibrary] = useState(false);
  const [error, setError] = useState('');
  const [working, setWorking] = useState(false);

  async function upload(file?: File) {
    if (!file) return;
    setError('');
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError('Choose a JPEG, PNG or WebP image, 5 MB or smaller.');
      return;
    }
    setWorking(true); onBusyChange(true);
    try {
      const body = new FormData(); body.set('file', file);
      const image = await apiRequest<MediaImage>('/admin/media', token, { method: 'POST', body });
      onChange(image);
      setImages(current => [image, ...current]);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Upload failed');
    } finally { setWorking(false); onBusyChange(false); }
  }

  async function browse() {
    setWorking(true); setError('');
    try {
      setImages(await apiRequest<MediaImage[]>('/admin/media', token));
      setShowLibrary(true);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Could not load images');
    } finally { setWorking(false); }
  }

  return <div className="image-picker">
    <label>Question image<input aria-label="Upload question image" type="file" accept="image/jpeg,image/png,image/webp"
      disabled={disabled || working} onChange={event => {
        const file = event.currentTarget.files?.[0]; event.currentTarget.value = ''; void upload(file);
      }} /></label>
    <p className="small-note">JPEG, PNG or WebP · up to 5 MB. Images are resized and stored privately.</p>
    <button type="button" className="quiet-button" disabled={disabled || working} onClick={() => void browse()}>Browse uploaded images</button>
    {working && <p role="status">Loading image…</p>}
    {error && <p className="notice" role="alert">{error}</p>}
    {imageUrl && <img className="image-preview" src={imageUrl} alt="Selected question" />}
    <input name="image_storage_path" type="hidden" value={value} />
    {showLibrary && <div className="image-library" aria-label="Uploaded images">
      {!images.length && <p>No images yet. Upload your first image above.</p>}
      {images.map(image => <button type="button" key={image.storage_path} disabled={disabled || !image.image_url}
        className={value === image.storage_path ? 'image-card selected' : 'image-card'}
        onClick={() => { onChange(image); setShowLibrary(false); }}>
        {image.image_url ? <img src={image.image_url} alt={image.original_filename} /> : <span>Preview unavailable</span>}
        <span>{image.original_filename}</span>
      </button>)}
    </div>}
  </div>;
}
