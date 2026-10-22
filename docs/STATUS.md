# Implementation status

Updated 9 October 2026 after crash recovery. This records saved code and local checks, not a deployed release.

## Implemented

| Area | Current implementation |
| --- | --- |
| Backend | Validated Supabase sessions; approved administrator roles; publication-aware guest APIs; content create/edit and ordered assignments; atomic question creation/editing; publication readiness; authenticated image upload, private previews and reuse; owner-scoped progress sync. |
| Studio | Google sign-in with approved-admin access; dashboard counts; game/level/question create/edit; question ordering and activation; publication readiness messages; private image upload/library/previews; game-scoped levels and image/answer selection; dismissible create/edit dialogs that close on successful save. |
| Android | Guest five-second answer-reveal gameplay, category browsing, local game/image downloads; serialized local progress and content-aware resume; background pause; optional Google PKCE login with encrypted tokens; explicit progress sync; optional dedicated game-canvas recording with movable/resizable front-camera overlay and microphone audio, preview, export/share and confirmed deletion. |
| Database/Storage | Migrations 001–006; RLS and restricted RPCs; atomic content changes; deferred publication constraints; stale progress protection with content fingerprints; private `content-images` bucket setup and restrictive client policies. |

Google login never grants administrator access by itself. Client progress and answer matching are non-authoritative. Recordings remain local until the player exports or shares them.

## Verified locally

- Backend: 32 unit tests and 20 HTTP integration tests passed, including authentication, publication isolation, image-upload permissions and progress sync validation. Backend compilation passed again after the dependency update.
- Studio: 15 component/workflow tests passed; TypeScript and production build passed using one worker and a 1 GB Node heap.
- ESLint passed for backend and Studio.
- All six migrations, migration reapplication, publication RLS/readiness, atomic rollback, progress ownership/stale-write/fingerprint behavior, and private Storage policy checks passed on disposable PostgreSQL 16. The fixture transaction rolled back and the server was stopped. Real Supabase services were not contacted.
- Dependency refresh reports zero npm audit vulnerabilities.
- Android: final debug APK build and all 9 unit tests passed after the resume/sync race fixes. APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Still requires project/device access

Next session: simplify the Studio content flow further. The user finds the current game → level → image/answer flow too complicated and requested a checkpoint commit before stopping for the day. Do not treat the current flow as the final UX.

1. Run the matching [complete SQL Editor bundle](../supabase/README.md), or apply only outstanding migrations in filename order. Migration 006 includes private Storage. Keep `content-images` private. No live project changes were made here.
2. Configure the Google Web OAuth client and Supabase redirect URLs per [GOOGLE_OAUTH.md](GOOGLE_OAUTH.md), approve the Studio operator, and verify real login/upload/publication/sync against staging.
3. Complete the physical-device log in [TESTING.md](TESTING.md). No authorized Android device was connected during recovery. CameraX frames, hardware canvas encoding, microphone audio, export and update installation need real-device validation.
4. Configure production HTTPS, origins and signing/distribution before release. v0.3.0 records a dedicated game composition, camera preview and microphone audio, with GitHub update prompts. Hardware validation is pending.

## Supabase Storage handoff

- [Existing project upgrade](../supabase/sql-editor/upgrade_existing.sql) includes migrations 002–006; [fresh setup](../supabase/sql-editor/setup_fresh.sql) includes 001–006. Both were executed successfully on disposable PostgreSQL 16, including repeated upgrades and wrong-project guards.
- [Verification SQL](../supabase/verify_setup.sql) and [explicit admin approval SQL](../supabase/approve_admin.sql) were also checked locally.
- Uploads and image serving now both verify the bucket is private. Android consumes signed image URLs from the backend.
- `npm run sql:check` detects generated SQL drift. No live Supabase configuration was changed.

## Resource limits and recovery

The machine has 8 GB RAM. Builds and test suites run sequentially. Gradle uses one worker, a 1 GB heap, no parallel execution, no persistent daemon, and in-process Kotlin compilation. Backend/Studio tests use one worker; Studio production builds use one rendering worker, Webpack memory optimizations and a 1 GB Node heap. These are per-process limits, not a total memory guarantee. Do not overlap builds or start an emulator during checks.

Setup, OAuth, architecture, deployment, database and testing docs now reflect the implemented code. Git was initialized on `main` after recovery. Local credentials, build outputs and signing material are ignored.
