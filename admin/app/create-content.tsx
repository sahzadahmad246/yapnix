'use client';

import React, { useState, type FormEvent } from 'react';
import { ImagePicker, type MediaImage } from './image-picker';
import type { ContentKind, ContentRow } from './content-editor';

export function CreateContent({ kind, token, busy, games, gameId, levelName, onBusyChange, onSave, onCancel }: {
  kind: ContentKind; token: string; busy: boolean; games: ContentRow[]; gameId: string;
  levelName?: string; onBusyChange: (busy: boolean) => void;
  onSave: (path: string, value: ContentRow) => Promise<void>; onCancel: () => void;
}) {
  const [image, setImage] = useState<MediaImage | null>(null);
  const [uploading, setUploading] = useState(false);
  const field = (label: string, name: string, required = true, type = 'text', value?: string, min?: number, max?: number) =>
    <label>{label}<input name={name} required={required} type={type} defaultValue={value} min={min} max={max} /></label>;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || uploading) return;
    const data = new FormData(event.currentTarget);
    const text = (name: string) => String(data.get(name) ?? '').trim();
    const value = kind === 'games'
      ? { name: text('name'), slug: text('slug'), description: text('description'), difficulty: Number(data.get('difficulty')), is_published: false }
      : kind === 'levels'
        ? { game_id: text('game_id'), name: text('name'), description: text('description'), question_count: Number(data.get('question_count')), default_time_limit_seconds: Number(data.get('time_limit')), is_published: false }
        : { image_storage_path: image?.storage_path, primary_answer: text('primary_answer'), accepted_answers: [...new Set(text('aliases').split(',').map(answer => answer.trim()).filter(Boolean))], difficulty: Number(data.get('difficulty')), language: text('language'), is_active: true };
    void onSave(`/admin/${kind}`, value);
  }
  return <section className="form-panel">
    {kind === 'questions' && <p className="muted">{levelName ? `Add an image and its correct answer to ${levelName}. Save the level's image selection afterwards to link it.` : 'Each image question contains a picture, its correct name, and other accepted names. Add it to a level from Games → Levels → Images & answers.'}</p>}
    <form onSubmit={submit}><fieldset disabled={busy || uploading}>
      {kind === 'games' && <>{field('Name', 'name')}{field('Slug', 'slug')}{field('Description', 'description', false)}</>}
      {kind === 'levels' && <>
        <label>Game<select name="game_id" required defaultValue={gameId}><option value="" disabled>Select a game</option>
          {games.map(game => <option key={String(game.id)} value={String(game.id)}>{String(game.name)}</option>)}</select></label>
        {field('Name', 'name')}{field('Description', 'description', false)}
        <div className="field-pair">{field('Number of images', 'question_count', true, 'number', '5', 1, 100)}
          {field('Seconds per image', 'time_limit', true, 'number', '5', 1, 60)}</div>
      </>}
      {kind === 'questions' && <>
        <ImagePicker token={token} value={image?.storage_path ?? ''} imageUrl={image?.image_url} disabled={busy}
          onChange={setImage} onBusyChange={working => { setUploading(working); onBusyChange(working); }} />
        {field('Correct name / answer', 'primary_answer')}
        <label>Other accepted names<input name="aliases" placeholder="For bicycle: bike, cycle" /></label>
        <p className="small-note">The player must say the correct name or one of these alternatives. The uploaded filename is not an answer.</p>
        {field('Answer language', 'language', true, 'text', 'en-US')}
      </>}
      {kind !== 'levels' && <label>Difficulty<select name="difficulty" defaultValue="1">
        {[1, 2, 3, 4, 5].map(value => <option key={value} value={value}>{value}</option>)}
      </select></label>}
      <div className="form-actions"><button type="button" className="quiet-button" onClick={onCancel}>Cancel</button>
        <button className="primary-button" disabled={kind === 'questions' && !image}>
          {kind === 'questions' ? 'Save image & answer' : kind === 'games' ? 'Create game' : 'Create level'}
        </button></div>
    </fieldset></form>
  </section>;
}
