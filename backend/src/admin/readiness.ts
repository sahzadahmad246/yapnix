interface Question { id: string; is_active: boolean; image_storage_path: string; primary_answer: string }
export interface LevelContent {
  question_count: number;
  level_questions: Array<{ questions: Question | Question[] | null }>;
}

export function publicationIssues(level: LevelContent, readyImages: Set<string>): string[] {
  const issues: string[] = [];
  const assignments = level.level_questions ?? [];
  if (assignments.length !== level.question_count) issues.push(`Assign exactly ${level.question_count} questions (${assignments.length} selected).`);
  for (const assignment of assignments) {
    const question = Array.isArray(assignment.questions) ? assignment.questions[0] : assignment.questions;
    if (!question) { issues.push('An assigned question is unavailable.'); continue; }
    if (!question.is_active) issues.push(`Activate "${question.primary_answer}" or replace it.`);
    if (!readyImages.has(question.image_storage_path)) issues.push(`Upload an image for "${question.primary_answer}".`);
  }
  return [...new Set(issues)];
}
