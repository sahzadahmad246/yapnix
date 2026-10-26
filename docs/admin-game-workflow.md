# Categories, games and questions

Yapnix uses **Category → Game → Questions**. There is no level step.

Example:

- **Guess** (category; image guessing mechanic)
  - **Animals** (game): cat, dog, elephant, lion, cow
  - **Foods** (game): apple, pizza, banana, bread, carrot
  - **Everyday Objects** (game): chair, phone, clock, cup, bicycle

Different themes belong to separate games in Guess. Categories describe the speaking experience. A new category does not install a new gameplay engine: the current Android client supports image guessing only. Future spelling, repeat-after-me or sentence games require an engine implementation, corresponding question format, validation and player release before publishing.

## Apply the database upgrade first

For an existing installation, paste **supabase/sql-editor/upgrade_existing.sql** into Supabase SQL Editor and run it as postgres. This generated script applies migration 007 as well as safe, repeatable earlier upgrades in one transaction. Back up the database before upgrading. Do not use setup_fresh.sql on an existing installation.

Migration 007:

- Creates a published Guess category and assigns existing games to it.
- Keeps the original game for its first published session (or first draft if none are published).
- Converts every additional legacy level into a standalone game named `Old game — Old level`, with a unique slug derived from its existing UUID.
- Creates an empty draft session for an old game that had no levels.
- Preserves question, answer, image, assignment and progress IDs. Never merges unrelated question lists or deletes progress.
- Enforces one session configuration per game with a unique index. The old levels table and levelId progress fields remain compatibility details for stored progress, not an authoring or player hierarchy.
- Makes category publication part of game, question and answer access policies. Unpublishing a category hides all its games from public playback without changing individual game publication flags.

Rebuild/update Android alongside the backend. Old public level routes remain for compatibility, but new clients use `GET /api/games/:id/play` and do not ask players to choose levels.

## Add Animals with five questions

1. Open Categories. Guess is created by the migration; click its Games button.
2. Click New game. Choose Guess, select the Animals icon from the icon library, name it Animals, set Number of questions to 5 and Seconds per question to 5. Save.
3. The game opens directly to Questions & answers. Add pictures of a cat, dog, elephant, lion and cow, with those names as primary answers. Only the primary name is needed; it is shown and spoken when the timer ends. Use en-US for English answers.
4. New questions are selected automatically. You can also select existing questions from the library. Use arrows to choose play order.
5. Click Save game questions. Creating a reusable question and assigning it to the game are separate saves, so the UI marks unsaved selections.
6. Publish game once all five active questions have ready images and correct answers. Guess must also be published for players to see Animals.
7. In Android, games appear grouped under Guess. Tap Animals, choose optional recording, and play directly.

Games begin as drafts. Saving a changed question selection automatically moves the game to draft and updates the question count to match the saved images. Re-publish when ready. Question edits affect every game using that reusable question; the backend's publication checks reject changes that would break published content.

## Edit and delete

Search and category/status filters help find games. Each card has Questions, Edit, publication and Delete actions. Deletion requires typing the exact game name, then removes its session, assignments and saved player progress. Reusable image questions, accepted answers and uploaded files remain available to other games.

Failed loads show retry controls; failed saves keep the form and entered values. Refresh preserves unsaved question selection, and leaving the game asks before discarding it.


## Image editor and game prompts (migration 008)

If categories are already installed, run `supabase/sql-editor/upgrade_game_editor.sql`
in Supabase SQL Editor. Otherwise use the complete `upgrade_existing.sql`. Both
scripts are transactional; migration 008 is safe to reapply.

In Game settings, choose **Question shown to players**: What is this?, Guess the
animal, Name this food, What is this object called?, or Name this vehicle. The
selected prompt is previewed in the picture/answer form and shown above each image
in Android. Update the Android app to see it.

For the five already-uploaded animal pictures:

1. Open Animals → Game settings and select Guess the animal. Save game.
2. If Cat, Dog, Elephant, Lion and Cow already appear as image questions, select
   them directly; do not create duplicates.
3. If they are only uploaded files, choose Add new image & answer → Browse uploaded
   images. Pick the cat picture and enter `cat` as the reveal answer.
   Save, then repeat with `dog`, `elephant`, `lion`, and `cow`.
   Use en-US and difficulty 1 for all five. No re-upload is needed.
4. Arrange Cat → Dog → Elephant → Lion → Cow. Save game questions, then Publish game.

Remove selected image clears an unsaved image choice from the form. Replace image
chooses another upload; Browse uploaded images reuses a stored file. Reset draft
clears the form; Discard draft closes it without creating a question. Uploaded
files stay in the media library even when an unsaved form is discarded.

Remove in the game's selected list stages an assignment removal. Discard selection
changes restores the saved list. Remove all from game stages an empty selection;
saving it leaves the game as a non-playable draft. Saving changed selections never
deletes reusable questions or files. You can add up to 100 questions, independently
of the starting target entered when creating the game.

## Game and sub-game artwork (migration 009)

Android labels Guess a game and Animals a sub-game. Studio keeps the existing category → game database terminology to preserve content and progress IDs. Both category and game forms ask for an icon/image. New records require a selection in Studio; existing records can keep their fallback icon or clear their artwork. Uploads through these forms are centre-cropped to 512 × 512 (4:4 = 1:1). The media library filters to square images; non-square artwork is also rejected by the backend.

For installations already on migration 008, run [upgrade_catalog_artwork.sql](../supabase/sql-editor/upgrade_catalog_artwork.sql). Earlier installations should use the complete [upgrade_existing.sql](../supabase/sql-editor/upgrade_existing.sql). Apply SQL before deploying the API/admin update. The migration adds nullable `icon_storage_path` foreign keys to `game_categories` and `games` and extends the atomic game RPCs. No content or progress IDs change. Icons use the private content bucket; guest APIs sign only artwork belonging to published catalogue content. Existing signed URLs expire after one hour, as with question pictures.

The question form no longer asks for accepted-answer aliases because the player reveals answers without evaluating speech. New questions need only a primary reveal answer. Existing aliases and the `question_answers` table remain for compatibility with older clients, existing RPCs and publication checks; omitted aliases are preserved when editing. Dropping that table would require a separate coordinated schema/API migration.

## Choose a bundled icon (migration 010)

In a category/game form, **Icon library** is selected by default. Search Animals, food, transport, music or another theme and click an icon. The preview shows the selected icon; saving requires no media upload. Category icons appear on Android’s home/game-family cards; game icons appear on its sub-game cards. Existing custom artwork opens in **Custom image** mode. Choose **Icon library** to replace it, or keep uploading a square image as before. Question pictures still use uploaded images.

Use [upgrade_icon_library.sql](../supabase/sql-editor/upgrade_icon_library.sql) for a database on migrations 001–008 or later; it contains both 009 and 010. `icon_key` is a non-null, allowlisted text field, defaulting to `game-controller`. Existing Guess categories receive `question` only when the new field is initially populated; reapplying preserves selected keys and images. The API rejects unknown keys. Studio and Android use assets generated from `shared/catalog-icons`; future unknown keys fall back safely in Android. Run `npm run icons:check` before publishing a new player.
