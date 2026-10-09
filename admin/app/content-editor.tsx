'use client';

import React, { useState, type FormEvent } from 'react';
import { ImagePicker } from './image-picker';

export type ContentKind = 'games' | 'levels' | 'questions';
export type ContentRow = Record<string, unknown>;

export function readContentChanges(kind: ContentKind, data: FormData): ContentRow {
  const text = (name: string) => String(data.get(name) ?? '').trim();
  if (kind === 'games') return {
    name: text('name'), slug: text('slug'), description: text('description'), difficulty: Number(data.get('difficulty')),
  };
  if (kind === 'levels') return {
    name: text('name'), description: text('description'), question_count: Number(data.get('question_count')),
    default_time_limit_seconds: Number(data.get('default_time_limit_seconds')),
  };
  return {
    image_storage_path: text('image_storage_path'), primary_answer: text('primary_answer'),
    accepted_answers: [...new Set(text('aliases').split(',').map(answer => answer.trim()).filter(Boolean))],
    difficulty: Number(data.get('difficulty')), language: text('language'), is_active: data.has('is_active'),
  };
}

export function ContentEditor({ kind, row, busy, token, onSave, onCancel }: {
  kind: ContentKind;
  row: ContentRow;
  busy: boolean;
  token: string;
  onSave: (changes: ContentRow) => Promise<void>;
  onCancel: () => void;
}) {
  const question = kind === 'questions';
  const [image, setImage] = useState({ storage_path: String(row.image_storage_path ?? ''), image_url: row.image_url as string | null });
  const [uploading, setUploading] = useState(false);
  const aliases = ((row.question_answers as ContentRow[] | undefined) ?? [])
    .filter(answer => !answer.is_primary && answer.answer !== row.primary_answer)
    .map(answer => String(answer.answer)).join(', ');
  const field = (label: string, name: string, required = true, type = 'text', min?: number, max?: number) => (
    <label>{label}<input name={name} required={required} type={type} min={min} max={max} defaultValue={String(row[name] ?? '')} /></label>
  );
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void onSave(readContentChanges(kind, new FormData(event.currentTarget)));
  }
  return <section className="form-panel content-editor" aria-label="Edit content">
    <div className="section-heading"><h2>Edit {question ? 'question' : kind === 'games' ? 'game' : 'level'}</h2>
      <button type="button" className="quiet-button" disabled={busy} onClick={onCancel}>Cancel</button></div>
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        {question ? <>
          <ImagePicker token={token} value={image.storage_path} imageUrl={image.image_url} disabled={busy}
            onChange={setImage} onBusyChange={setUploading} />
          {field('Primary answer', 'primary_answer')}
          <label>Accepted aliases, comma-separated<input name="aliases" defaultValue={aliases} /></label>
          {field('Language', 'language')}
          <label className="checkbox-field"><input type="checkbox" name="is_active" defaultChecked={Boolean(row.is_active)} /> Active in gameplay</label>
        </> : <>
          {field('Name', 'name')}
          {kind === 'games' && field('Slug', 'slug')}
          {field('Description', 'description', false)}
        </>}
        {kind === 'levels' ? <div className="field-pair">
          {field('Questions', 'question_count', true, 'number', 1)}
          {field('Seconds / question', 'default_time_limit_seconds', true, 'number', 1, 60)}
        </div> : <label>Difficulty<select name="difficulty" defaultValue={String(row.difficulty ?? 1)}>
          {[1, 2, 3, 4, 5].map(value => <option key={value} value={value}>{value}</option>)}
        </select></label>}
        <button className="primary-button" type="submit" disabled={uploading || (question && !image.storage_path)}>Save changes</button>
      </fieldset>
    </form>
  </section>;
}
