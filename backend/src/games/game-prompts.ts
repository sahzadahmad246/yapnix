export const GAME_PROMPTS = {
  what_is_this: 'What is this?',
  guess_animal: 'Guess the animal',
  name_food: 'Name this food',
  name_object: 'What is this object called?',
  name_vehicle: 'Name this vehicle',
} as const;
export const gamePrompt = (key?: string): string => GAME_PROMPTS[key as keyof typeof GAME_PROMPTS] ?? GAME_PROMPTS.what_is_this;
export const gamePromptOptions = Object.entries(GAME_PROMPTS).map(([key, label]) => ({ key, label }));
