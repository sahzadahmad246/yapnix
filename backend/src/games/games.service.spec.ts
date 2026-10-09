import { describe, expect, it, vi } from 'vitest';
import type { SupabaseService } from '../supabase/supabase.service.js';
import type { MediaService } from '../media/media.service.js';
import { GamesService } from './games.service.js';
import { NotFoundException } from '@nestjs/common';

const media = { withImageUrls: async (rows: unknown[]) => rows } as unknown as MediaService;

describe('GamesService', () => {
  it('does not query levels when the parent game is unpublished or missing', async () => {
    const builder = {
      select: vi.fn().mockReturnThis(),
      eq: vi.fn().mockReturnThis(),
      maybeSingle: vi.fn().mockResolvedValue({ data: null, error: null }),
    };
    const from = vi.fn(() => builder);
    const service = new GamesService({ client: { from } } as unknown as SupabaseService, media);

    await expect(service.listPublishedLevels('draft-game')).rejects.toBeInstanceOf(NotFoundException);
    expect(from).toHaveBeenCalledTimes(1);
    expect(from).toHaveBeenCalledWith('games');
    expect(builder.eq).toHaveBeenCalledWith('is_published', true);
  });

  it('does not return a published level under an unpublished parent game', async () => {
    const builder = {
      select: vi.fn().mockReturnThis(),
      eq: vi.fn().mockReturnThis(),
      single: vi.fn()
        .mockResolvedValueOnce({ data: { id: 'level', level_questions: [] }, error: null })
        .mockResolvedValueOnce({ data: null, error: null }),
    };
    const service = new GamesService({ client: { from: () => builder } } as unknown as SupabaseService, media);

    await expect(service.getPlayableLevel('draft-game', 'level')).rejects.toBeInstanceOf(NotFoundException);
    expect(builder.eq).toHaveBeenCalledWith('game_id', 'draft-game');
    expect(builder.eq.mock.calls.filter(([column]) => column === 'is_published')).toHaveLength(2);
  });

  it('rejects an unpublished or missing level before returning questions', async () => {
    const builder = {
      select: vi.fn().mockReturnThis(),
      eq: vi.fn().mockReturnThis(),
      single: vi.fn().mockResolvedValue({ data: null, error: null }),
    };
    const service = new GamesService({ client: { from: () => builder } } as unknown as SupabaseService, media);

    await expect(service.getPlayableLevel('game', 'draft-level')).rejects.toBeInstanceOf(NotFoundException);
    expect(builder.eq).toHaveBeenCalledWith('is_published', true);
  });

  it('returns published level questions in configured order with answer aliases', async () => {
    const calls: Array<[string, ...unknown[]]> = [];
    let singleCall = 0;
    const builder = {
      select: vi.fn((...args: unknown[]) => {
        calls.push(['select', ...args]);
        return builder;
      }),
      eq: vi.fn((...args: unknown[]) => {
        calls.push(['eq', ...args]);
        return builder;
      }),
      order: vi.fn((...args: unknown[]) => {
        calls.push(['order', ...args]);
        return builder;
      }),
      single: vi.fn(async () => {
        singleCall += 1;
        if (singleCall === 1) {
          return {
            data: {
              id: 'level-id',
              name: 'First level',
              question_count: 2,
              default_time_limit_seconds: 5,
              level_questions: [
                {
                  order_index: 1,
                  questions: [{
                    id: 'question-two',
                    image_storage_path: 'objects/bike.webp',
                    primary_answer: 'bike',
                    difficulty: 1,
                    language: 'en',
                    question_answers: [{ answer: 'bike' }, { answer: 'bicycle' }],
                  }],
                },
                {
                  order_index: 0,
                  questions: [{
                    id: 'question-one',
                    image_storage_path: 'objects/fan.webp',
                    primary_answer: 'fan',
                    difficulty: 1,
                    language: 'en',
                    question_answers: [{ answer: 'fan' }, { answer: 'table fan' }],
                  }],
                },
              ],
            },
            error: null,
          };
        }
        return { data: { id: 'game-id' }, error: null };
      }),
    };
    const supabase = {
      client: { from: vi.fn(() => builder) },
    } as unknown as SupabaseService;
    const service = new GamesService(supabase, media);

    const level = await service.getPlayableLevel('game-id', 'level-id');

    expect(level.questions.map((question) => question.id)).toEqual([
      'question-one',
      'question-two',
    ]);
    expect(level.questions[0].accepted_answers).toEqual(['fan', 'table fan']);
    expect(calls).toContainEqual(['eq', 'is_published', true]);
    expect(calls).toContainEqual(['eq', 'game_id', 'game-id']);
  });
});
