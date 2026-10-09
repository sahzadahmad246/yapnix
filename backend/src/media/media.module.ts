import { Module } from '@nestjs/common';
import { SupabaseService } from '../supabase/supabase.service.js';
import { MediaService } from './media.service.js';

@Module({ providers: [MediaService, SupabaseService], exports: [MediaService] })
export class MediaModule {}
