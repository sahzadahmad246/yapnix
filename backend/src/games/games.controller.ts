import { Controller, Get, Param, ParseUUIDPipe } from '@nestjs/common';
import { ApiTags } from '@nestjs/swagger';
import { GamesService } from './games.service.js';

@ApiTags('games')
@Controller('games')
export class GamesController {
  constructor(private readonly gamesService: GamesService) {}

  @Get()
  async listPublishedGames() {
    return this.gamesService.listPublishedGames();
  }

  @Get(':id/play')
  getPlayableGame(@Param('id', new ParseUUIDPipe()) gameId: string) {
    return this.gamesService.getPlayableGame(gameId);
  }

  @Get(':id/levels')
  async listPublishedLevels(@Param('id', new ParseUUIDPipe()) gameId: string) {
    return this.gamesService.listPublishedLevels(gameId);
  }

  @Get(':id/levels/:levelId')
  async getPlayableLevel(
    @Param('id', new ParseUUIDPipe()) gameId: string,
    @Param('levelId', new ParseUUIDPipe()) levelId: string,
  ) {
    return this.gamesService.getPlayableLevel(gameId, levelId);
  }
}
