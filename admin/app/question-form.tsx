'use client';
import React, { useState, type FormEvent } from 'react';
import { ImagePicker, type MediaImage } from './image-picker';
import type { ContentRow } from './content-editor';

export function QuestionForm({ row, token, busy, prompt = 'What is this?', onSave, onCancel, onBusyChange }: {
  row?: ContentRow; token: string; busy: boolean; prompt?: string;
  onSave: (value: ContentRow) => Promise<void>; onCancel: () => void; onBusyChange: (busy: boolean) => void;
}) {
  const originalImage = (): MediaImage | null => row?.image_storage_path ? {
    storage_path: String(row.image_storage_path), image_url: row.image_url ? String(row.image_url) : null,
    original_filename: String(row.original_filename ?? 'Selected image'),
  } : null;
  const originalAliases = () => ((row?.question_answers as ContentRow[] | undefined) ?? [])
    .filter(answer => !answer.is_primary && answer.answer !== row?.primary_answer).map(answer => String(answer.answer)).join(', ');
  const [image, setImage] = useState<MediaImage | null>(originalImage);
  const [answer, setAnswer] = useState(String(row?.primary_answer ?? ''));
  const [aliases, setAliases] = useState(originalAliases);
  const [language, setLanguage] = useState(String(row?.language ?? 'en-US'));
  const [difficulty, setDifficulty] = useState(Number(row?.difficulty ?? 1));
  const [active, setActive] = useState(row ? Boolean(row.is_active) : true);
  const [working, setWorking] = useState(false);
  const disabled = busy || working;
  function reset() { setImage(originalImage()); setAnswer(String(row?.primary_answer ?? '')); setAliases(originalAliases()); setLanguage(String(row?.language ?? 'en-US')); setDifficulty(Number(row?.difficulty ?? 1)); setActive(row ? Boolean(row.is_active) : true); }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (disabled || !image || !answer.trim()) return;
    void onSave({ image_storage_path: image.storage_path, primary_answer: answer.trim(),
      accepted_answers: [...new Set(aliases.split(',').map(value => value.trim()).filter(Boolean))],
      language: language.trim(), difficulty, is_active: active });
  }
  return <section className="form-panel question-composer">
    <p className="composer-intro">Choose a picture you already uploaded, then tell us which spoken answers to accept.</p>
    <form onSubmit={submit}>
      <div className="composer-grid">
        <section className="composer-image"><div className="step-heading"><span>1</span><div><h3>Choose a picture</h3><p>Reuse a file from your library or upload a new one.</p></div></div>
          <ImagePicker token={token} value={image?.storage_path ?? ''} imageUrl={image?.image_url} filename={image?.original_filename}
            disabled={disabled} onChange={setImage} onClear={() => setImage(null)} onBusyChange={value => {setWorking(value);onBusyChange(value);}} />
        </section>
        <section className="composer-answers"><div className="step-heading"><span>2</span><div><h3>Set the answer</h3><p>Write what the player should say aloud.</p></div></div>
          <div className="prompt-preview"><span>PLAYER SEES</span><strong>{prompt}</strong><small>The game prompt applies to every picture.</small></div>
          <fieldset disabled={disabled}>
            <label>Correct name / answer<input name="primary_answer" required maxLength={120} value={answer} onChange={event => setAnswer(event.target.value)} placeholder="e.g. cat" autoComplete="off" /></label>
            <label>Other accepted names <small>Optional</small><textarea name="aliases" rows={3} value={aliases} onChange={event => setAliases(event.target.value)} placeholder="kitty, domestic cat" /></label>
            <p className="small-note">Separate alternatives with commas. The filename is never used as an answer.</p>
            <details className="advanced-options"><summary>Language & advanced settings</summary>
              <label>Answer language<input name="language" required pattern="[a-z]{2,3}(-[A-Za-z0-9]{2,8})*" value={language} onChange={event => setLanguage(event.target.value)} /></label>
              <label>Difficulty<select name="difficulty" value={difficulty} onChange={event => setDifficulty(Number(event.target.value))}>{[1,2,3,4,5].map(value => <option key={value}>{value}</option>)}</select></label>
              {row && <label className="checkbox-field"><input type="checkbox" name="is_active" checked={active} onChange={event => setActive(event.target.checked)} />Active in gameplay</label>}
            </details>
          </fieldset>
        </section>
      </div>
      <footer className="composer-footer"><p>{image ? 'Picture selected. Save to create a reusable image question.' : 'Choose an image to continue.'}</p><div className="form-actions">
        <button type="button" className="quiet-button" disabled={disabled} onClick={reset}>{row ? 'Reset changes' : 'Reset draft'}</button>
        <button type="button" className="quiet-button" disabled={disabled} onClick={onCancel}>{row ? 'Cancel' : 'Discard draft'}</button>
        <button className="primary-button" disabled={disabled || !image || !answer.trim()}>{busy ? 'Saving…' : row ? 'Save changes' : 'Save image & answer'}</button>
      </div></footer>
    </form>
  </section>;
}
