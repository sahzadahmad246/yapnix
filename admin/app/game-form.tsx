'use client';
import React, { useState, type FormEvent } from 'react';
import { ArtworkPicker, initialArtwork, artworkReady, artworkPayload } from './artwork-picker';
import type { ContentRow } from './content-editor';

export interface GamePromptOption { key: string; label: string }

export function GameForm({ row, categories, categoryId, prompts, busy, token, onBusyChange, onSave, onCancel }: {
  row?: ContentRow; categories: ContentRow[]; categoryId: string; prompts: GamePromptOption[]; busy: boolean; token: string; onBusyChange: (busy: boolean) => void;
  onSave: (value: ContentRow) => Promise<void>; onCancel: () => void;
}) {
  const [artwork, setArtwork] = useState(() => initialArtwork(row));
  const [uploading, setUploading] = useState(false);
  const disabled = busy || uploading;
  const [name, setName] = useState(String(row?.name ?? ''));
  const [slug, setSlug] = useState<string | null>(row ? String(row.slug) : null);
  const generated = name.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 120).replace(/-$/, '');
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (disabled || !artworkReady(artwork)) return;
    const form = new FormData(event.currentTarget);
    void onSave({ ...artworkPayload(artwork), name: name.trim(), slug: String(form.get('slug')).trim(), description: String(form.get('description')).trim(),
      category_id: String(form.get('category_id')), prompt_key: String(form.get('prompt_key')), difficulty: Number(form.get('difficulty')),
      question_count: Number(form.get('question_count')), default_time_limit_seconds: Number(form.get('time_limit')) });
  }
  return <section className="form-panel"><p className="muted">A game contains its questions directly. For example: Guess (game/category) → Animals (sub-game) → five image questions.</p>
    <form onSubmit={submit}><fieldset disabled={disabled}>
      <h3>Sub-game icon / image</h3><ArtworkPicker token={token} value={artwork} disabled={disabled} onChange={setArtwork} onBusyChange={value => { setUploading(value); onBusyChange(value); }} />
      <label>Category<select name="category_id" required defaultValue={String(row?.category_id ?? categoryId)}>
        <option value="" disabled>Choose a category</option>{categories.map(category => <option key={String(category.id)} value={String(category.id)}>{String(category.name)}</option>)}
      </select></label>
      <label>Game name<input required maxLength={120} value={name} onChange={event => setName(event.target.value)} placeholder="Animals, Foods, Everyday objects…" /></label>
      <label>Question shown to players<select name="prompt_key" required defaultValue={String(row?.prompt_key ?? 'what_is_this')}>
        {prompts.map(prompt => <option key={prompt.key} value={prompt.key}>{prompt.label}</option>)}
      </select></label><p className="small-note">Choose once for the game. This question appears above every picture.</p>
      <label>Description<input name="description" defaultValue={String(row?.description ?? '')} /></label>
      <div className="field-pair">{row ? <div><p className="small-note">Question count updates when you save your image selection.</p><input type="hidden" name="question_count" value={Number(row.question_count ?? 5)} /></div> : <label>Number of questions<input name="question_count" type="number" required min={1} max={100} defaultValue={5} /></label>}
        <label>Seconds per question<input name="time_limit" type="number" required min={1} max={60} defaultValue={Number(row?.default_time_limit_seconds ?? 5)} /></label></div>
      <details className="advanced-options" open={!!name.trim() && !generated ? true : undefined}><summary>Advanced settings</summary>
        <label>Slug<input name="slug" required maxLength={120} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={slug ?? generated} onChange={event => setSlug(event.target.value)} /></label>
        <label>Difficulty<select name="difficulty" defaultValue={String(row?.difficulty ?? 1)}>{[1,2,3,4,5].map(value => <option key={value}>{value}</option>)}</select></label>
      </details>
      <div className="form-actions"><button className="quiet-button" type="button" onClick={onCancel}>Cancel</button><button className="primary-button" disabled={disabled || !artworkReady(artwork)}>{busy ? 'Saving…' : row ? 'Save game' : 'Create game'}</button></div>
    </fieldset></form></section>;
}

export function CategoryForm({ row, busy, token, onBusyChange, onSave, onCancel }: { row?: ContentRow; busy: boolean; token: string; onBusyChange: (busy: boolean) => void; onSave: (value: ContentRow) => Promise<void>; onCancel: () => void }) {
  const [artwork, setArtwork] = useState(() => initialArtwork(row));
  const [uploading, setUploading] = useState(false);
  const disabled = busy || uploading;
  const [name, setName] = useState(String(row?.name ?? ''));
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0,120);
  return <section className="form-panel"><p className="muted">Categories group related speaking games. The current player supports image guessing. New speaking mechanics need a player update before they can be used.</p>
    <form onSubmit={event => { event.preventDefault(); if (disabled || !artworkReady(artwork)) return; const form = new FormData(event.currentTarget); void onSave({...artworkPayload(artwork), name: name.trim(), slug: String(form.get('slug')).trim(), description: String(form.get('description')).trim()}); }}>
      <fieldset disabled={disabled}><h3>Game / category icon</h3><ArtworkPicker token={token} value={artwork} disabled={disabled} onChange={setArtwork} onBusyChange={value => { setUploading(value); onBusyChange(value); }} /><label>Category name<input required maxLength={120} value={name} onChange={event => setName(event.target.value)} placeholder="Guess" /></label>
        <label>Slug<input name="slug" required maxLength={120} pattern="[a-z0-9]+(-[a-z0-9]+)*" key={row ? String(row.id) : slug} defaultValue={String(row?.slug ?? slug)} /></label>
        <label>Description<input name="description" defaultValue={String(row?.description ?? '')} /></label>
        <p className="small-note">Player mechanic: image guessing. New categories start as drafts.</p>
        <div className="form-actions"><button type="button" className="quiet-button" onClick={onCancel}>Cancel</button><button className="primary-button" disabled={disabled || !artworkReady(artwork)}>{busy ? 'Saving…' : row ? 'Save category' : 'Create category'}</button></div>
      </fieldset>
    </form></section>;
}
