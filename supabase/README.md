# Supabase setup and complete SQL

Run SQL in your Supabase project's **SQL Editor**, using the `postgres` role. These files contain actual SQL, not psql include commands.

## Choose one complete script

- **Existing Yapnix tables / migration 001 already applied:** copy all of [upgrade_existing.sql](sql-editor/upgrade_existing.sql) into a new query and Run. Includes migrations 002–010 and Storage. It is safe to reapply these upgrades.
- **Fresh project with no Yapnix tables:** use [setup_fresh.sql](sql-editor/setup_fresh.sql). Includes migrations 001–010 and Storage. The guard refuses to run over existing tables.

Do not run both scripts as separate setup steps. They run in a transaction: on error, all changes in that run roll back. Neither script deletes your content. They do not change Google provider settings or grant anyone administrator access.

## What to do next

1. Open **Storage → Buckets**. Confirm `content-images` exists and **Public is off**. The SQL creates it automatically; do not create another bucket or add client upload/read policies. Maximum image size is 5 MiB; allowed types are JPEG, PNG and WebP.
2. Run [verify_setup.sql](verify_setup.sql). All `present` values must be true, the bucket must be private, and the restrictive policy must exist. A readiness error means existing published content needs correction through Studio.
3. Configure the Google provider and allow the Studio URL plus `yapnix://auth/callback`, following [GOOGLE_OAUTH.md](../docs/GOOGLE_OAUTH.md).
4. Configure the backend's ignored environment file with `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY`. Set production `CORS_ORIGINS` to the Studio origin. Keep the service-role key exclusively in the backend.
5. Configure Studio with `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, and `NEXT_PUBLIC_BACKEND_API_URL`. Android uses `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `API_BASE_URL` in ignored `local.properties`. All apps must refer to the same Supabase project.
6. Sign in to Studio once. Edit the email in [approve_admin.sql](approve_admin.sql), run it in SQL Editor, then refresh Studio. A Google account alone grants no admin access.
7. In Studio, open **Categories → Guess → Games → New game**. Create Animals with five questions and a timer, choose a library icon, then add images and reveal answers directly, then click **Save game questions** and **Publish game**. There is no level step. The category must also be published. See [the content and migration guide](../docs/admin-game-workflow.md).

Upload game images through **Studio**, rather than manually through the Storage dashboard: Studio also creates the media metadata required by publication checks. `questions/` is created by uploading object paths; no manual folder setup is needed. Android uses the backend's temporary `image_url` and does not construct a public bucket URL. Recordings stay on the phone.

## Individual migrations

| Order | SQL | Purpose |
| --- | --- | --- |
| 001 | [Initial schema](migrations/202610090001_initial_schema.sql) | Tables, initial RLS and functions; fresh project only |
| 002 | [Existing project API](migrations/202610090002_existing_project_api.sql) | Compatibility grants, publication RLS and assignments |
| 003 | [Atomic question updates](migrations/202610090003_atomic_question_updates.sql) | Transactional answer/metadata edits |
| 004 | [Content readiness](migrations/202610090004_content_readiness.sql) | Atomic creation and publication constraints |
| 005 | [Progress sync](migrations/202610090005_progress_sync.sql) | Completion, content fingerprint and owner-scoped sync |
| 006 | [Private content Storage](migrations/202610090006_private_content_storage.sql) | Private bucket, limits and restrictive access policy |

[storage_setup.sql](storage_setup.sql) is a compatibility shortcut containing migration 006. If you already ran one complete bundle, you do not need this shortcut separately.

Generated scripts stay aligned with migrations using `npm run sql:generate` and `npm run sql:check`. Run tests sequentially on this 8 GB machine. Provider reference: [Supabase private buckets and signed URLs](https://supabase.com/docs/guides/storage/buckets/fundamentals).

## Picture editor and safe removal upgrade

If game categories (migration 007) are already installed, run
[upgrade_game_editor.sql](sql-editor/upgrade_game_editor.sql) for migration 008 only.
It adds game-wide prompt selection and fixes editing/removing published game
questions. Saving changed assignments moves the game to draft and updates its
question count. Re-publish after reviewing the saved selection. No image files,
reusable questions or player progress are deleted by this upgrade.

## Catalogue artwork upgrade

If migrations 001–008 are already installed, run [upgrade_catalog_artwork.sql](sql-editor/upgrade_catalog_artwork.sql). Migration [009](migrations/202610260009_catalog_artwork.sql) adds optional artwork references to categories and games and extends their existing atomic RPCs. Existing published records and saved progress remain intact. Deploy the API and Studio after applying the SQL, then upload/select square icons on both records. Full setup/upgrade bundles also include 009.

## Bundled icon library upgrade

The default artwork picker now offers 45 bundled Phosphor duotone icons with search. Custom square image uploads remain available. Store a stable `icon_key` on each category/game; Android renders the same bundled vector offline. No Flaticon API, account, CDN request or upload is needed for library icons. The included MIT licence is shipped with Studio assets and Android.

Run [upgrade_icon_library.sql](sql-editor/upgrade_icon_library.sql) if migrations 001–008 are installed. It includes 009 and 010, so it also works if 009 was already applied. Earlier projects should use the complete upgrade bundle. Apply SQL before deploying the API/Studio update. Neither migration changes content IDs or progress. Existing custom images remain selected.
