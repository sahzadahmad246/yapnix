import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module.js';
import { SupabaseService } from '../supabase/supabase.service.js';
import { AdminController } from './admin.controller.js';
import { AdminService } from './admin.service.js';
import { MediaModule } from '../media/media.module.js';

@Module({
  imports: [AuthModule, MediaModule],
  controllers: [AdminController],
  providers: [AdminService, SupabaseService],
})
export class AdminModule {}
