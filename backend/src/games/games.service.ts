import { Injectable, NotFoundException } from '@nestjs/common';
import { SupabaseService } from '../supabase/supabase.service.js';
import { MediaService } from '../media/media.service.js';

@Injectable()
export class GamesService {
  constructor(private readonly supabase: SupabaseService, private readonly media: MediaService) {}

  async listPublishedGames() {
    const { data, error } = await this.supabase.client
      .from('games')
      .select('id,name,description,difficulty')
      .eq('is_published', true)
      .order('created_at', { ascending: false });

    if (error) throw new NotFoundException('Published games are unavailable');
    return data;
  }

  async listPublishedLevels(gameId: string) {
    const { data: game, error: gameError } = await this.supabase.client
      .from('games')
      .select('id')
      .eq('id', gameId)
      .eq('is_published', true)
      .maybeSingle();

    if (gameError || !game) throw new NotFoundException('Published game not found');

    const { data, error } = await this.supabase.client
      .from('levels')
      .select('id,game_id,name,description,question_count,default_time_limit_seconds')
      .eq('game_id', gameId)
      .eq('is_published', true)
      .order('created_at', { ascending: true });

    if (error) throw new NotFoundException('Published levels are unavailable');
    return data;
  }

  async getPlayableLevel(gameId: string, levelId: string) {
    const { data, error } = await this.supabase.client
      .from('levels')
      .select(
        'id,game_id,name,question_count,default_time_limit_seconds,level_questions(order_index,questions(id,image_storage_path,primary_answer,difficulty,language,question_answers(answer)))',
      )
      .eq('id', levelId)
      .eq('game_id', gameId)
      .eq('is_published', true)
      .single();

    if (error || !data) throw new NotFoundException('Playable level not found');

    const { level_questions: levelQuestions, ...level } = data;
    const { data: game, error: gameError } = await this.supabase.client
      .from('games')
      .select('id')
      .eq('id', gameId)
      .eq('is_published', true)
      .single();

    if (gameError || !game) throw new NotFoundException('Playable level not found');

    const orderedQuestions = (levelQuestions ?? []) as unknown as Array<{
      order_index: number;
      questions:
        | {
            id: string;
            image_storage_path: string;
            primary_answer: string;
            difficulty: number;
            language: string;
            question_answers: Array<{ answer: string }>;
          }
        | Array<{
            id: string;
            image_storage_path: string;
            primary_answer: string;
            difficulty: number;
            language: string;
            question_answers: Array<{ answer: string }>;
          }>
        | null;
    }>;

    const questions = orderedQuestions
        .flatMap(({ order_index: orderIndex, questions }) => {
          const question = Array.isArray(questions) ? questions[0] : questions;
          if (!question) return [];

          return [
            {
              ...question,
              order_index: orderIndex,
              accepted_answers: question.question_answers.map(({ answer }) => answer),
            },
          ];
        })
        .sort((first, second) => first.order_index - second.order_index);
    if (!questions.length) throw new NotFoundException('This level has no playable questions');
    return { ...level, questions: await this.media.withImageUrls(questions) };
  }
}
