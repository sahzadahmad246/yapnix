import { BadRequestException, Injectable, InternalServerErrorException, NotFoundException } from '@nestjs/common';
import { SupabaseService } from '../supabase/supabase.service.js';
import type { SaveProgressDto } from './progress.dto.js';

@Injectable()
export class ProgressService {
  constructor(private readonly supabase: SupabaseService) {}

  async getForPlayer(accessToken: string) {
    const { data, error } = await this.supabase
      .createUserClient(accessToken)
      .from('player_progress')
      .select('level_id,question_index,score,correct_count,completed,content_fingerprint,last_played_at,updated_at')
      .order('updated_at', { ascending: false });

    if (error) throw new InternalServerErrorException('Unable to load saved progress');
    return data;
  }

  async saveForPlayer(accessToken: string, progress: SaveProgressDto) {
    const { data, error } = await this.supabase
      .createUserClient(accessToken)
      .rpc('save_player_progress', { entry: progress });

    if (error?.code === 'P0002') throw new NotFoundException('Published level not found');
    if (error?.code === 'YP001') throw new BadRequestException(error.message);
    if (error) throw new InternalServerErrorException('Unable to save progress');
    return data;
  }
}
