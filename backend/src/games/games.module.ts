import { Module } from '@nestjs/common';
import { GamesController } from './games.controller.js';
import { GamesService } from './games.service.js';
import { SupabaseService } from '../supabase/supabase.service.js';
import { MediaModule } from '../media/media.module.js';

@Module({
  imports: [MediaModule],
  controllers: [GamesController],
  providers: [GamesService, SupabaseService],
})
export class GamesModule {}
