import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from './auth/auth.module.js';
import { GamesModule } from './games/games.module.js';
import { ProgressModule } from './progress/progress.module.js';
import { AdminModule } from './admin/admin.module.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    AuthModule,
    GamesModule,
    ProgressModule,
    AdminModule,
  ],
})
export class AppModule {}
