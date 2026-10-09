import { Module } from '@nestjs/common';
import { SupabaseService } from '../supabase/supabase.service.js';
import { AdminGuard } from './guards/admin.guard.js';

@Module({
  providers: [AdminGuard, SupabaseService],
  exports: [AdminGuard],
})
export class AuthModule {}
