import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ContentEditor, readContentChanges } from './content-editor';

describe('content editing', () => {
  it('prefills question metadata and editable aliases', () => {
    const html = renderToStaticMarkup(<ContentEditor kind="questions" token="test" busy={false} onSave={async () => {}} onCancel={() => {}}
      row={{ primary_answer: 'bike', image_storage_path: 'bike.webp', language: 'en', is_active: true,
        question_answers: [{ answer: 'bike', is_primary: true }, { answer: 'bicycle', is_primary: false }] }} />);
    expect(html).toContain('value="bike.webp"');
    expect(html).toContain('>bicycle</textarea>');
    expect(html).toContain('checked=""');
  });

  it('saves question edits including explicit alias removal and deactivation', () => {
    const data = new FormData();
    data.set('image_storage_path', ' bike.webp ');
    data.set('primary_answer', ' bicycle ');
    data.set('difficulty', '2');
    data.set('language', 'en');
    expect(readContentChanges('questions', data)).toEqual({
      image_storage_path: 'bike.webp', primary_answer: 'bicycle', accepted_answers: [],
      difficulty: 2, language: 'en', is_active: false,
    });
  });

  it('trims and deduplicates aliases while retaining non-English answers', () => {
    const data = new FormData();
    data.set('aliases', ' bicycle, पंखा, bicycle, , ');
    expect(readContentChanges('questions', data).accepted_answers).toEqual(['bicycle', 'पंखा']);
  });

  it('metadata editing does not change publication or reassign a level to another game', () => {
    const data = new FormData();
    data.set('name', ' Round 1 ');
    data.set('question_count', '4');
    data.set('default_time_limit_seconds', '10');
    expect(readContentChanges('levels', data)).toEqual({
      name: 'Round 1', description: '', question_count: 4, default_time_limit_seconds: 10,
    });
  });
});
