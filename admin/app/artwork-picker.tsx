'use client';
import React, { useState } from 'react';
import { catalogIcons, catalogIconSrc, defaultCatalogIcon } from './catalog-icons';
import { ImagePicker, type MediaImage } from './image-picker';
import type { ContentRow } from './content-editor';

export interface CatalogArtwork { source: 'library' | 'upload'; iconKey: string; image: MediaImage | null }
export function initialArtwork(row?: ContentRow): CatalogArtwork {
  return {
    source: row?.icon_storage_path ? 'upload' : 'library',
    iconKey: catalogIcons.find(icon => icon.key === row?.icon_key)?.key ?? defaultCatalogIcon,
    image: row?.icon_storage_path ? { storage_path: String(row.icon_storage_path), image_url: row.icon_url ? String(row.icon_url) : null, original_filename: 'Custom artwork' } : null,
  };
}
export function artworkReady(value: CatalogArtwork): boolean { return value.source === 'library' || !!value.image; }
export function artworkPayload(value: CatalogArtwork): ContentRow {
  return { icon_key: value.iconKey, icon_storage_path: value.source === 'upload' ? value.image?.storage_path ?? null : null };
}

export function ArtworkPicker({ value, token, disabled, onChange, onBusyChange }: {
  value: CatalogArtwork; token: string; disabled: boolean; onChange: (value: CatalogArtwork) => void; onBusyChange: (busy: boolean) => void;
}) {
  const [search, setSearch] = useState('');
  const filtered = catalogIcons.filter(icon => `${icon.label} ${icon.key} ${icon.keywords}`.toLowerCase().includes(search.trim().toLowerCase()));
  const selected = catalogIcons.find(icon => icon.key === value.iconKey) ?? catalogIcons[0];
  return <section className="artwork-picker" aria-label="Game artwork">
    <div className="filter-tabs" aria-label="Artwork source">
      <button type="button" disabled={disabled} aria-pressed={value.source === 'library'} className={value.source === 'library' ? 'active' : ''} onClick={() => onChange({ ...value, source: 'library' })}>Icon library</button>
      <button type="button" disabled={disabled} aria-pressed={value.source === 'upload'} className={value.source === 'upload' ? 'active' : ''} onClick={() => onChange({ ...value, source: 'upload' })}>Custom image</button>
    </div>
    {value.source === 'library' ? <>
      <div className="selected-catalog-icon"><img src={catalogIconSrc(selected.key)} alt="Selected icon"/><div><strong>{selected.label}</strong><p className="small-note">Phosphor duotone · available offline in Android</p></div></div>
      <label>Search icons<input type="search" disabled={disabled} value={search} onChange={event => setSearch(event.target.value)} placeholder="Animals, food, transport, music…"/></label>
      <div className="catalog-icon-grid" aria-label="Available icons">
        {filtered.map(icon => <button type="button" disabled={disabled} key={icon.key} aria-label={`Select ${icon.label} icon`} aria-pressed={value.iconKey === icon.key} className={value.iconKey === icon.key ? 'catalog-icon-option selected' : 'catalog-icon-option'} onClick={() => onChange({ ...value, iconKey: icon.key })}><img src={catalogIconSrc(icon.key)} alt=""/><span>{icon.label}</span></button>)}
        {!filtered.length && <p className="muted">No matching icons. Try a broader search or choose a custom image.</p>}
      </div>
    </> : <ImagePicker square token={token} value={value.image?.storage_path ?? ''} imageUrl={value.image?.image_url} filename={value.image?.original_filename} disabled={disabled}
      onChange={image => onChange({ ...value, image })} onClear={() => onChange({ ...value, image: null })} onBusyChange={onBusyChange}/>}
  </section>;
}
