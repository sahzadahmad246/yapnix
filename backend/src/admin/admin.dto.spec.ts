import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { describe, expect, it } from 'vitest';
import { CreateGameDto, CreateQuestionDto } from './admin.dto.js';

describe('admin request validation', () => {
  it('rejects game difficulty outside the supported range', async () => {
    const input = plainToInstance(CreateGameDto, {
      name: 'Object Guessing',
      slug: 'object-guessing',
      difficulty: 6,
    });

    const errors = await validate(input);

    expect(errors.map((error) => error.property)).toContain('difficulty');
  });

  it('rejects non-string accepted answer aliases', async () => {
    const input = plainToInstance(CreateQuestionDto, {
      image_storage_path: 'objects/fan.webp',
      primary_answer: 'fan',
      accepted_answers: ['table fan', 12],
      difficulty: 2,
    });

    const errors = await validate(input);

    expect(errors.map((error) => error.property)).toContain('accepted_answers');
  });
});