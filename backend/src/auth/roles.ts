import { SetMetadata } from '@nestjs/common';

export const ROLES_KEY = 'roles';
export type AdminRole = 'super_admin' | 'content_admin';

export const AdminRoles = (...roles: AdminRole[]) => SetMetadata(ROLES_KEY, roles);
