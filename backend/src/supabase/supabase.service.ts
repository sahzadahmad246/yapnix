import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { WebSocketLikeConstructor } from '@supabase/realtime-js';
import { createClient, type SupabaseClient } from '@supabase/supabase-js';
import WebSocket from 'ws';

const nodeWebSocket = WebSocket as unknown as WebSocketLikeConstructor;

@Injectable()
export class SupabaseService {
  readonly client: SupabaseClient;
  readonly adminClient: SupabaseClient;

  constructor(private readonly config: ConfigService) {
    const url = this.config.getOrThrow<string>('SUPABASE_URL');
    const anonKey = this.config.getOrThrow<string>('SUPABASE_ANON_KEY');
    const serviceRoleKey = this.config.getOrThrow<string>('SUPABASE_SERVICE_ROLE_KEY');

    this.client = createClient(url, anonKey, { auth: { persistSession: false, autoRefreshToken: false }, realtime: { transport: nodeWebSocket } });
    this.adminClient = createClient(url, serviceRoleKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      realtime: { transport: nodeWebSocket },
    });
  }

  createUserClient(accessToken: string): SupabaseClient {
    const url = this.config.getOrThrow<string>('SUPABASE_URL');
    const anonKey = this.config.getOrThrow<string>('SUPABASE_ANON_KEY');

    return createClient(url, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { headers: { Authorization: `Bearer ${accessToken}` } },
      realtime: { transport: nodeWebSocket },
    });
  }
}
