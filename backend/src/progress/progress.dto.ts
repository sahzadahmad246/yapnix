import { IsBoolean, IsISO8601, IsInt, IsString, IsUUID, Max, MaxLength, Min, ValidateIf } from 'class-validator';

export class SaveProgressDto {
  @IsUUID()
  levelId!: string;

  @IsInt()
  @Min(0)
  @Max(2147483647)
  questionIndex!: number;

  @IsInt()
  @Min(0)
  @Max(2147483647)
  score!: number;

  @IsInt()
  @Min(0)
  @Max(2147483647)
  correctCount!: number;

  @ValidateIf((_object, value) => value !== undefined)
  @IsBoolean()
  completed?: boolean;

  @ValidateIf((_object, value) => value !== undefined)
  @IsISO8601({ strict: true })
  lastPlayedAt?: string;

  @ValidateIf((_object, value) => value !== undefined)
  @IsString()
  @MaxLength(5000)
  contentFingerprint?: string;
}
