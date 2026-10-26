import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { describe, expect, it } from 'vitest';
import { CreateGameDto, CreateQuestionDto, UpdateGameDto } from './admin.dto.js';

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
  it('accepts bundled icon keys and rejects unknown keys, paths and null', async () => {
    expect(await validate(plainToInstance(UpdateGameDto, { icon_key: 'paw-print' }))).toHaveLength(0);
    for (const icon_key of ['unknown-icon', '../cat', 'https://icons.test/cat.svg', null]) {
      expect((await validate(plainToInstance(UpdateGameDto, { icon_key }))).map(error => error.property)).toContain('icon_key');
    }
  });
  it('allows clearing artwork but rejects arbitrary URLs and traversal', async () => {
    for (const icon_storage_path of [null, 'artwork/icon.webp']) {
      expect(await validate(plainToInstance(UpdateGameDto, { icon_storage_path }))).toHaveLength(0);
    }
    for (const icon_storage_path of ['https://example.com/icon.webp', '../secret.webp', '/icon.webp']) {
      expect((await validate(plainToInstance(UpdateGameDto, { icon_storage_path }))).map(error => error.property)).toContain('icon_storage_path');
    }
  });
  it('accepts predefined game prompts and rejects unknown ones', async () => {
    expect(await validate(plainToInstance(UpdateGameDto, {prompt_key:'guess_animal'}))).toHaveLength(0);
    expect((await validate(plainToInstance(UpdateGameDto, {prompt_key:'unsupported'}))).map(error=>error.property)).toContain('prompt_key');
  });

});