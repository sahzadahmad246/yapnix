import { SetMetadata } from '@nestjs/common';
import type { AdminRole } from '../roles.js';
import { ROLES_KEY } from '../roles.js';

export function AdminRole(...roles: AdminRole[]) {
  return SetMetadata(ROLES_KEY, roles);
}
