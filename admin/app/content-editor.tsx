'use client';

import React, { type FormEvent } from 'react';
import { QuestionForm } from './question-form';

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
    ...(data.has('aliases') ? { accepted_answers: [...new Set(text('aliases').split(',').map(answer => answer.trim()).filter(Boolean))] } : {}),
    difficulty: Number(data.get('difficulty')), language: text('language'), is_active: data.has('is_active'),
  };
}

export function ContentEditor({ kind, row, busy, token, onSave, onCancel, onBusyChange, prompt }: {
  kind: ContentKind; row: ContentRow; busy: boolean; token: string; prompt?: string;
  onSave: (changes: ContentRow) => Promise<void>; onCancel: () => void; onBusyChange?: (busy: boolean) => void;
}) {
  if (kind === 'questions') return <QuestionForm prompt={prompt} row={row} token={token} busy={busy} onSave={onSave} onCancel={onCancel} onBusyChange={onBusyChange ?? (() => {})} />;
  const field = (label: string, name: string, required = true, type = 'text', min?: number, max?: number) =>
    <label>{label}<input name={name} required={required} type={type} min={min} max={max} defaultValue={String(row[name] ?? '')} /></label>;
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!busy) void onSave(readContentChanges(kind, new FormData(event.currentTarget))); }
  return <section className="form-panel content-editor" aria-label="Edit content"><form onSubmit={submit}><fieldset disabled={busy}>
    {field('Name','name')}{kind === 'games' && field('Slug','slug')}{field('Description','description',false)}
    {kind === 'levels' ? <div className="field-pair">{field('Number of images','question_count',true,'number',1,100)}{field('Seconds / question','default_time_limit_seconds',true,'number',1,60)}</div>
      : <label>Difficulty<select name="difficulty" defaultValue={String(row.difficulty ?? 1)}>{[1,2,3,4,5].map(value=><option key={value}>{value}</option>)}</select></label>}
    <div className="form-actions"><button type="button" className="quiet-button" onClick={onCancel}>Cancel</button><button className="primary-button">{busy ? 'Saving…' : 'Save changes'}</button></div>
  </fieldset></form></section>;
}
