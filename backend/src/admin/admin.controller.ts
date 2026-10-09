import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Put,
  UseGuards,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { MediaService, MAX_IMAGE_BYTES, type ImageUpload } from '../media/media.service.js';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { AdminRoles } from '../auth/roles.js';
import { AdminGuard } from '../auth/guards/admin.guard.js';
import {
  CreateGameDto,
  CreateLevelDto,
  CreateQuestionDto,
  SetLevelQuestionsDto,
  UpdateGameDto,
  UpdateLevelDto,
  UpdateQuestionDto,
} from './admin.dto.js';
import { AdminService } from './admin.service.js';

@ApiTags('admin')
@ApiBearerAuth()
@UseGuards(AdminGuard)
@AdminRoles('content_admin', 'super_admin')
@Controller('admin')
export class AdminController {
  constructor(private readonly adminService: AdminService, private readonly media: MediaService) {}

  @Get('media')
  listMedia() { return this.media.list(); }

  @Post('media')
  @UseInterceptors(FileInterceptor('file', { limits: { fileSize: MAX_IMAGE_BYTES, files: 1, fields: 0 } }))
  uploadImage(@UploadedFile() file?: ImageUpload) { return this.media.upload(file); }

  @Get('overview')
  getOverview() {
    return this.adminService.getOverview();
  }

  @Get('games')
  listGames() {
    return this.adminService.listGames();
  }

  @Post('games')
  createGame(@Body() input: CreateGameDto) {
    return this.adminService.createGame(input);
  }

  @Patch('games/:id')
  updateGame(@Param('id', new ParseUUIDPipe()) id: string, @Body() input: UpdateGameDto) {
    return this.adminService.updateGame(id, input);
  }

  @Get('levels')
  listLevels() {
    return this.adminService.listLevels();
  }

  @Post('levels')
  createLevel(@Body() input: CreateLevelDto) {
    return this.adminService.createLevel(input);
  }

  @Patch('levels/:id')
  updateLevel(@Param('id', new ParseUUIDPipe()) id: string, @Body() input: UpdateLevelDto) {
    return this.adminService.updateLevel(id, input);
  }

  @Put('levels/:id/questions')
  setLevelQuestions(
    @Param('id', new ParseUUIDPipe()) id: string,
    @Body() input: SetLevelQuestionsDto,
  ) {
    return this.adminService.setLevelQuestions(id, input);
  }

  @Get('questions')
  listQuestions() {
    return this.adminService.listQuestions();
  }

  @Post('questions')
  createQuestion(@Body() input: CreateQuestionDto) {
    return this.adminService.createQuestion(input);
  }

  @Patch('questions/:id')
  updateQuestion(@Param('id', new ParseUUIDPipe()) id: string, @Body() input: UpdateQuestionDto) {
    return this.adminService.updateQuestion(id, input);
  }
}
