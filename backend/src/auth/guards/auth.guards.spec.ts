import { ForbiddenException, UnauthorizedException, type ExecutionContext } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { describe, expect, it, vi } from 'vitest';
import type { SupabaseService } from '../../supabase/supabase.service.js';
import { AdminGuard } from './admin.guard.js';
import { PlayerAuthGuard } from './player-auth.guard.js';

function setup(authorization?: string) {
  const request = { headers: { authorization } };
  const context = {
    getHandler: () => () => undefined,
    getClass: () => class {},
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
  const getUser = vi.fn().mockResolvedValue({ data: { user: { id: 'player-id' } }, error: null });
  const single = vi.fn().mockResolvedValue({ data: { role: 'content_admin', approved: true }, error: null });
  const query = { select: vi.fn(), eq: vi.fn(), single };
  query.select.mockReturnValue(query);
  query.eq.mockReturnValue(query);
  const from = vi.fn().mockReturnValue(query);
  const supabase = { client: { auth: { getUser } }, adminClient: { from } } as unknown as SupabaseService;
  const reflector = new Reflector();
  vi.spyOn(reflector, 'getAllAndOverride').mockReturnValue(['content_admin', 'super_admin']);
  return { request, context, getUser, single, query, from,
    admin: new AdminGuard(reflector, supabase), player: new PlayerAuthGuard(supabase) };
}

describe('server authentication and administrator approval', () => {
  it.each([undefined, '', 'token', 'Basic token', 'Bearer ', 'Bearer token extra'])(
    'rejects missing or malformed authorization: %s', async (header) => {
      const fixture = setup(header);
      await expect(fixture.admin.canActivate(fixture.context)).rejects.toBeInstanceOf(UnauthorizedException);
      await expect(fixture.player.canActivate(fixture.context)).rejects.toBeInstanceOf(UnauthorizedException);
      expect(fixture.getUser).not.toHaveBeenCalled();
    },
  );

  it('validates tokens on the server before looking up administrator permissions', async () => {
    const fixture = setup('Bearer invalid');
    fixture.getUser.mockResolvedValue({ data: { user: null }, error: new Error('expired') });
    await expect(fixture.admin.canActivate(fixture.context)).rejects.toBeInstanceOf(UnauthorizedException);
    await expect(fixture.player.canActivate(fixture.context)).rejects.toBeInstanceOf(UnauthorizedException);
    expect(fixture.from).not.toHaveBeenCalled();
  });

  it.each([
    null,
    { approved: false, role: 'super_admin' },
    { approved: true, role: 'player' },
  ])('denies a signed-in user without an approved administrator role: %j', async (data) => {
    const fixture = setup('Bearer valid');
    fixture.single.mockResolvedValue({ data, error: null });
    await expect(fixture.admin.canActivate(fixture.context)).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('authorizes approved administrators by the verified user ID', async () => {
    const fixture = setup('bEaReR valid');
    await expect(fixture.admin.canActivate(fixture.context)).resolves.toBe(true);
    expect(fixture.getUser).toHaveBeenCalledWith('valid');
    expect(fixture.query.eq).toHaveBeenCalledWith('supabase_user_id', 'player-id');
    expect(fixture.request).toHaveProperty('user.id', 'player-id');
  });

  it('allows player progress authentication without an administrator role', async () => {
    const fixture = setup('Bearer valid');
    await expect(fixture.player.canActivate(fixture.context)).resolves.toBe(true);
    expect(fixture.request).toHaveProperty('player.id', 'player-id');
    expect(fixture.request).toHaveProperty('accessToken', 'valid');
    expect(fixture.from).not.toHaveBeenCalled();
  });
});
