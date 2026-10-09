import { describe, expect, it } from 'vitest';
import { publicationIssues } from './readiness.js';

describe('publication readiness', () => {
  const question = { id: 'question', primary_answer: 'bike', is_active: true, image_storage_path: 'bike.webp' };
  it('accepts exactly the required active questions with uploaded images', () => {
    expect(publicationIssues({ question_count: 1, level_questions: [{ questions: question }] }, new Set(['bike.webp']))).toEqual([]);
  });
  it('identifies empty or incomplete levels, inactive questions and missing media', () => {
    expect(publicationIssues({ question_count: 2, level_questions: [{ questions: { ...question, is_active: false } }] }, new Set())).toHaveLength(3);
    expect(publicationIssues({ question_count: 1, level_questions: [] }, new Set())[0]).toContain('Assign exactly');
  });
});
