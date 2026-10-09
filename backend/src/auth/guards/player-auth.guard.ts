import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import type { Request } from 'express';
import { SupabaseService } from '../../supabase/supabase.service.js';

export interface AuthenticatedPlayerRequest extends Request {
  player: { id: string };
  accessToken: string;
}

@Injectable()
export class PlayerAuthGuard implements CanActivate {
  constructor(private readonly supabase: SupabaseService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedPlayerRequest>();
    const accessToken = request.headers.authorization?.match(/^Bearer\s+(\S+)$/i)?.[1];

    if (!accessToken) throw new UnauthorizedException('A sign-in session is required');

    const { data, error } = await this.supabase.client.auth.getUser(accessToken);
    if (error || !data.user) throw new UnauthorizedException('The sign-in session is invalid');

    request.player = { id: data.user.id };
    request.accessToken = accessToken;
    return true;
  }
}
