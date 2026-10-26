import { BadRequestException, ConflictException, Injectable, InternalServerErrorException, NotFoundException, ServiceUnavailableException } from '@nestjs/common';
import { MediaService } from '../media/media.service.js';
import { publicationIssues, type LevelContent } from './readiness.js';
import { SupabaseService } from '../supabase/supabase.service.js';
import type {
  CreateGameDto,
  CreateCategoryDto,
  UpdateCategoryDto,
  CreateLevelDto,
  CreateQuestionDto,
  SetLevelQuestionsDto,
  UpdateGameDto,
  UpdateLevelDto,
  UpdateQuestionDto,
} from './admin.dto.js';

@Injectable()
export class AdminService {
  constructor(private readonly supabase: SupabaseService, private readonly media: MediaService) {}

  private async assertIconSchema(input: { icon_key?: string }, table: 'games' | 'game_categories') {
    if (input.icon_key === undefined) return;
    const { error } = await this.supabase.adminClient.from(table).select('icon_key,icon_storage_path').limit(0);
    if (!error) return;
    if (['42703', 'PGRST204'].includes(error.code)) {
      throw new ServiceUnavailableException('Apply Supabase upgrade_icon_library.sql (migrations 009–010) before saving library icons. Your changes have not been saved.');
    }
    throw new InternalServerErrorException('Unable to check catalogue icon support');
  }

  private fail(error: { code?: string; message?: string }, fallback: string): never {
    if (error.code === 'P0002') throw new NotFoundException('Content not found');
    if (error.code === 'YP001') throw new BadRequestException(error.message);
    if (error.code === '23505') throw new ConflictException('This slug, name or assignment already exists');
    if (error.code === '23503') throw new BadRequestException('The selected game or question no longer exists');
    throw new InternalServerErrorException(fallback);
  }

  async getOverview() {
    const client = this.supabase.adminClient;
    const [games, levels, questions, recentLevels] = await Promise.all([
      client.from('games').select('id', { count: 'exact', head: true }),
      client.from('levels').select('id', { count: 'exact', head: true }),
      client.from('questions').select('id', { count: 'exact', head: true }),
      client
        .from('levels')
        .select('id,name,is_published,updated_at')
        .order('updated_at', { ascending: false })
        .limit(5),
    ]);

    if (games.error || levels.error || questions.error || recentLevels.error) {
      throw new InternalServerErrorException('Unable to load dashboard overview');
    }

    return {
      games: games.count ?? 0,
      levels: levels.count ?? 0,
      questions: questions.count ?? 0,
      recentlyEditedLevels: recentLevels.data,
    };
  }

  async listCategories() {
    const { data, error } = await this.supabase.adminClient.from('game_categories').select('*').order('created_at');
    if (error) throw new InternalServerErrorException('Unable to load game categories. Apply migration 007 first.');
    return this.media.withArtworkUrls(data ?? []);
  }

  async createCategory(input: CreateCategoryDto) {
    await this.assertIconSchema(input, 'game_categories');
    if (input.icon_storage_path) await this.media.assertReadyImage(input.icon_storage_path, true);
    const { data, error } = await this.supabase.adminClient.from('game_categories').insert(input).select().single();
    if (error) this.fail(error, 'Unable to create category');
    return data;
  }

  async updateCategory(id: string, input: UpdateCategoryDto) {
    await this.assertIconSchema(input, 'game_categories');
    if (input.icon_storage_path) await this.media.assertReadyImage(input.icon_storage_path, true);
    const { data, error } = await this.supabase.adminClient.from('game_categories').update(input).eq('id', id).select().maybeSingle();
    if (error) this.fail(error, 'Unable to update category');
    if (!data) throw new NotFoundException('Category not found');
    return data;
  }

  async listGames() {
    const { data, error } = await this.supabase.adminClient.from('games')
      .select('*,game_categories(id,name,slug,gameplay_kind,is_published),levels(id,question_count,default_time_limit_seconds,level_questions(question_id,order_index,questions(id,is_active,image_storage_path,primary_answer)))')
      .order('created_at', { ascending: false });
    if (error) throw new InternalServerErrorException('Unable to load games');
    const { data: images, error: imageError } = await this.supabase.adminClient.from('media_records').select('storage_path').eq('status', 'ready');
    if (imageError) throw new InternalServerErrorException('Unable to check game images');
    const ready = new Set((images ?? []).map(image => String(image.storage_path)));
    return this.media.withArtworkUrls((data ?? []).map(game => {
      const session = game.levels?.[0];
      const metadata = { ...game, levels: undefined };
      return { ...metadata, question_count: session?.question_count ?? 5,
        default_time_limit_seconds: session?.default_time_limit_seconds ?? 5,
        game_questions: session?.level_questions ?? [],
        publication_issues: session ? publicationIssues(session as unknown as LevelContent, ready) : ['Game configuration is missing.'] };
    }));
  }

  async createGame(input: CreateGameDto) {
    await this.assertIconSchema(input, 'games');
    if (input.icon_storage_path) await this.media.assertReadyImage(input.icon_storage_path, true);
    const { data, error } = await this.supabase.adminClient.rpc('create_standalone_game', { content: input });
    if (error) this.fail(error, 'Unable to create game');
    return data;
  }

  async setGameQuestions(id: string, input: SetLevelQuestionsDto) {
    const { error } = await this.supabase.adminClient.rpc('set_game_questions', { target_game_id: id, question_ids: input.question_ids });
    if (error) this.fail(error, 'Unable to save game questions');
    return { game_id: id, question_ids: input.question_ids };
  }

  async deleteGame(id: string) {
    const { data, error } = await this.supabase.adminClient
      .from('games').delete().eq('id', id).select('id').maybeSingle();
    if (error) this.fail(error, 'Unable to delete game');
    if (!data) throw new NotFoundException('Game not found');
    return { id: data.id };
  }

  async updateGame(id: string, input: UpdateGameDto) {
    await this.assertIconSchema(input, 'games');
    if (input.icon_storage_path) await this.media.assertReadyImage(input.icon_storage_path, true);
    const { data, error } = await this.supabase.adminClient.rpc('update_standalone_game', { target_game_id: id, changes: input });
    if (error) this.fail(error, 'Unable to update game');
    return data;
  }

  async listLevels() {
    const { data, error } = await this.supabase.adminClient
      .from('levels')
      .select('*,games(name,slug),level_questions(question_id,order_index,questions(id,is_active,image_storage_path,primary_answer))')
      .order('updated_at', { ascending: false });

    if (error) throw new InternalServerErrorException('Unable to load levels');
    const { data: images, error: imageError } = await this.supabase.adminClient.from('media_records')
      .select('storage_path').eq('status', 'ready');
    if (imageError) throw new InternalServerErrorException('Unable to check level images');
    const readyImages = new Set((images ?? []).map(image => String(image.storage_path)));
    return (data ?? []).map(level => ({ ...level, publication_issues: publicationIssues(level as unknown as LevelContent, readyImages) }));
  }

  async setLevelQuestions(id: string, input: SetLevelQuestionsDto) {
    const { error } = await this.supabase.adminClient.rpc('set_level_questions', {
      target_level_id: id,
      question_ids: input.question_ids,
    });

    if (error) this.fail(error, 'Unable to assign questions to level');
    return { level_id: id, question_ids: input.question_ids };
  }

  async createLevel(input: CreateLevelDto) {
    const { data, error } = await this.supabase.adminClient
      .from('levels')
      .insert(input)
      .select()
      .single();

    if (error) this.fail(error, 'Unable to create level');
    return data;
  }

  async updateLevel(id: string, input: UpdateLevelDto) {
    const { data, error } = await this.supabase.adminClient
      .from('levels')
      .update(input)
      .eq('id', id)
      .select()
      .maybeSingle();

    if (error) this.fail(error, 'Unable to update level');
    if (!data) throw new NotFoundException('Level not found');
    return data;
  }

  async listQuestions() {
    const { data, error } = await this.supabase.adminClient
      .from('questions')
      .select('*,question_answers(id,answer,is_primary),level_questions(level_id,order_index)')
      .order('updated_at', { ascending: false });

    if (error) throw new InternalServerErrorException('Unable to load questions');
    return this.media.withImageUrls((data ?? []) as Array<{ image_storage_path: string }>);
  }

  async createQuestion(input: CreateQuestionDto) {
    await this.media.assertReadyImage(input.image_storage_path);
    const { data, error } = await this.supabase.adminClient.rpc('create_question_content', {
      content: input,
    });
    if (error) this.fail(error, 'Unable to create question');
    return data;
  }

  async updateQuestion(id: string, input: UpdateQuestionDto) {
    if (input.image_storage_path !== undefined) await this.media.assertReadyImage(input.image_storage_path);
    const { data, error } = await this.supabase.adminClient.rpc('update_question_content', {
      target_question_id: id,
      changes: input,
    });
    if (error) this.fail(error, 'Unable to update question');
    return data;
  }
}
