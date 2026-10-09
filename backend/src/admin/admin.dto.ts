import { PartialType } from '@nestjs/swagger';
import { Transform } from 'class-transformer';
import {
  IsArray,
  IsBoolean,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  Min,
  MinLength,
  MaxLength,
  Matches,
  ArrayUnique,
  ValidateIf,
} from 'class-validator';

export class CreateGameDto {
  @IsString()
  @MinLength(1)
  @MaxLength(120)
  @Transform(({ value }: { value: unknown }) => typeof value === 'string' ? value.trim() : value)
  name!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(120)
  @Matches(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, { message: 'slug must use lowercase words separated by hyphens' })
  slug!: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsInt()
  @Min(1)
  @Max(5)
  difficulty!: number;

  @ValidateIf((_object, value) => value !== undefined)
  @IsBoolean()
  is_published?: boolean;
}

export class UpdateGameDto extends PartialType(CreateGameDto, { skipNullProperties: false }) {}

export class CreateLevelDto {
  @IsUUID()
  game_id!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(120)
  @Transform(({ value }: { value: unknown }) => typeof value === 'string' ? value.trim() : value)
  name!: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsInt()
  @Min(1)
  @Max(100)
  question_count!: number;

  @IsInt()
  @Min(1)
  @Max(60)
  default_time_limit_seconds!: number;

  @ValidateIf((_object, value) => value !== undefined)
  @IsBoolean()
  is_published?: boolean;
}

export class UpdateLevelDto extends PartialType(CreateLevelDto, { skipNullProperties: false }) {}

export class SetLevelQuestionsDto {
  @IsArray()
  @ArrayUnique()
  @IsUUID(undefined, { each: true })
  question_ids!: string[];
}

export class CreateQuestionDto {
  @IsString()
  @MinLength(1)
  @MaxLength(512)
  @Matches(/^(?!.*\.\.)(?!\/)[A-Za-z0-9_./-]+\.(?:webp|png|jpg|jpeg)$/i, { message: 'image_storage_path must be a Storage object path' })
  image_storage_path!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(120)
  @Transform(({ value }: { value: unknown }) => typeof value === 'string' ? value.trim() : value)
  primary_answer!: string;

  @ValidateIf((_object, value) => value !== undefined)
  @IsArray()
  @IsString({ each: true })
  @MinLength(1, { each: true })
  @MaxLength(120, { each: true })
  @Transform(({ value }: { value: unknown }) => Array.isArray(value) ? value.map(item => typeof item === 'string' ? item.trim() : item) : value)
  accepted_answers?: string[];

  @IsInt()
  @Min(1)
  @Max(5)
  difficulty!: number;

  @ValidateIf((_object, value) => value !== undefined)
  @IsString()
  @Matches(/^[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$/, { message: 'language must be a language tag such as en or hi-IN' })
  language?: string;

  @ValidateIf((_object, value) => value !== undefined)
  @IsBoolean()
  is_active?: boolean;
}

export class UpdateQuestionDto extends PartialType(CreateQuestionDto, { skipNullProperties: false }) {}
