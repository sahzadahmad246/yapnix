import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';
import { ROLES_KEY } from '../roles.js';
import type { AdminRole } from '../roles.js';
import { SupabaseService } from '../../supabase/supabase.service.js';

interface SupabaseRequest extends Request {
  user?: { id: string };
}

@Injectable()
export class AdminGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    private readonly supabase: SupabaseService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const requiredRoles = this.reflector.getAllAndOverride<AdminRole[]>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    if (!requiredRoles?.length) return true;

    const request = context.switchToHttp().getRequest<SupabaseRequest>();
    const accessToken = request.headers.authorization?.match(/^Bearer\s+(\S+)$/i)?.[1];

    if (!accessToken) {
      throw new UnauthorizedException('A valid administrator session is required');
    }

    const { data: userData, error: userError } =
      await this.supabase.client.auth.getUser(accessToken);

    if (userError || !userData.user?.id) {
      throw new UnauthorizedException('The administrator session is invalid');
    }

    request.user = { id: userData.user.id };

    const { data, error } = await this.supabase.adminClient
      .from('admin_users')
      .select('role, approved')
      .eq('supabase_user_id', request.user.id)
      .single();

    if (error || !data?.approved || !requiredRoles.includes(data.role as AdminRole)) {
      throw new ForbiddenException('Administrator access is not permitted');
    }

    return true;
  }
}
