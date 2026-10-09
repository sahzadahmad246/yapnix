import { Module } from '@nestjs/common';
import { SupabaseService } from '../supabase/supabase.service.js';
import { PlayerAuthGuard } from '../auth/guards/player-auth.guard.js';
import { ProgressController } from './progress.controller.js';
import { ProgressService } from './progress.service.js';

@Module({
  controllers: [ProgressController],
  providers: [ProgressService, PlayerAuthGuard, SupabaseService],
})
export class ProgressModule {}