import {
  Body,
  Controller,
  Get,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import {
  PlayerAuthGuard,
  type AuthenticatedPlayerRequest,
} from '../auth/guards/player-auth.guard.js';
import { SaveProgressDto } from './progress.dto.js';
import { ProgressService } from './progress.service.js';

@ApiTags('progress')
@ApiBearerAuth()
@UseGuards(PlayerAuthGuard)
@Controller('progress')
export class ProgressController {
  constructor(private readonly progressService: ProgressService) {}

  @Get()
  async getMyProgress(@Req() request: AuthenticatedPlayerRequest) {
    return this.progressService.getForPlayer(request.accessToken);
  }

  @Post()
  async saveMyProgress(
    @Req() request: AuthenticatedPlayerRequest,
    @Body() progress: SaveProgressDto,
  ) {
    return this.progressService.saveForPlayer(request.accessToken, progress);
  }
}
