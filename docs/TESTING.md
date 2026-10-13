# Development checks

## Backend and admin

From the repository root:

```sh
npm test
npm run build
```

Backend `test` runs Vitest unit tests, compiles TypeScript with decorator metadata, then runs HTTP integration tests with Node's test runner. The HTTP tests start a NestJS server on an ephemeral loopback port and use the production prefix and validation settings. They do not load local Supabase credentials: the Supabase provider is replaced in the testing module.

HTTP coverage includes guest content access, draft parents and levels, invalid IDs, invalid sessions, unapproved administrators, DTO validation, authenticated progress reads, and server-derived ownership of progress writes. Supabase queries are mocked here; this suite does not prove database RLS enforcement.

Admin tests cover initial rendering, approved/unapproved access, create/publish readiness, edit-form defaults, image upload/library reuse/failures, serialization of content edits, form cancellation/Escape and failed-save preservation, and category → game → question creation and direct assignment. They do not replace browser validation of Google OAuth and the full content-management workflow.

`npm run lint` uses the repository ESLint flat configuration. Run checks sequentially on an 8 GB machine; test workers are limited to one. Studio production builds use one worker and a 1 GB Node heap.

## Database migrations and RLS

Install the PostgreSQL client and create an **empty disposable PostgreSQL database**. The connection must be a superuser because the fixture creates Supabase-like database roles and a minimal `auth` schema. Then run:

```sh
YAPNIX_TEST_DATABASE_URL='postgresql://localhost/yapnix_test' npm run test:db
```

The suite refuses databases containing `auth` or `public.games`. It runs in a transaction and rolls back its schema, fixtures and roles on completion. A failure aborts the connection and rolls back the transaction as well.

Run `npm run sql:check` to verify SQL Editor bundles match their source migrations; `npm run test:db` includes this check.

Checks include applying all eight migrations, reapplying compatibility/update migrations, anonymous and authenticated publication isolation, inactive-question filtering, progress ownership enforcement, mutation-function permissions, alias preservation/removal, missing-question errors, and rollback after an injected answer-insertion failure or invalid question assignment.

The auth fixture implements only user IDs and `auth.uid()`. A minimal Storage schema checks private-bucket policies against a legacy permissive policy. These checks run real PostgreSQL policies and functions but do not validate real Supabase Auth/Storage services or the deployed project's existing policies.

## Android

```sh
cd android
./gradlew testDebugUnitTest assembleDebug --no-daemon --max-workers=1
```

Answer-matching unit tests cover punctuation, aliases, partial/empty answers, Unicode and device locale. PKCE tests cover the RFC challenge vector and random verifiers; sync policy tests cover timestamp and completion conflicts. Physical-device testing remains required for speech recognition, microphone permissions, lifecycle/timers, Google login, and camera recording/export.

## Physical-device and staging validation log

Status: **not run**. No connected-device or live-project validation is claimed.

| Check | Required observation | Result |
| --- | --- | --- |
| Guest play | Start, answer, timeout, replay without login | Pending |
| Resume | Background, resume, kill/relaunch; resolved questions never score twice | Pending |
| Microphone | Grant/deny/revoke; missing speech service gives a clear message | Pending |
| Google login | Success/cancel, callback, app restart, expired access-token refresh, sign-out | Pending |
| Progress sync | Two devices, newer-save merge, changed content, offline failure; local saves preserved | Pending |
| Studio access | Approved admin allowed; ordinary Google account denied | Pending |
| Images | Upload/reuse; draft image never exposed through guest APIs; private bucket direct access denied | Pending |
| Publication | Incomplete games blocked; published category/game available to guest | Pending |
| Recording | Camera grant/deny; start/stop/background; playable finalized local video | Pending |
| Audio coexistence | Voice answers and recorded audio both work during CameraX recording | Pending |
| Export/share | Preview, document export, share URI, cancel, confirmed delete | Pending |

Record phone model, Android version, date, and actual results here before release. Camera reaction video is implemented; game-screen video composition is not implemented.

## SQL Editor bundle validation — 9 October 2026

Both generated bundles were executed on disposable PostgreSQL 16 with minimal Supabase Auth/Storage fixtures. Fresh setup, upgrade, repeated upgrade, fresh-on-existing refusal, upgrade-on-empty refusal, verification SQL, and explicit admin approval/reapplication passed. The disposable database was removed and the server stopped. Hosted Supabase configuration still requires verification.

## Category migration validation

Migration 007 tests preserve legacy progress IDs, split multi-session games without
losing assignments, enforce one session per game, reject incomplete publication,
and hide games/questions/aliases beneath unpublished categories. The suite also
reapplies migration 007 to check idempotence. An isolated PGlite PostgreSQL run was
used during implementation (pgcrypto extension creation omitted in that runner;
UUID generation uses built-in gen_random_uuid). Live Supabase remains unmodified.

Migration 008 checks cover removal of the last image from a published game,
no-op saves retaining publication, failed replacements rolling back publication,
automatic question counts, and allowed/invalid game prompt keys. Composer UI tests
cover uploaded-file reuse without re-upload, clear/replace/reset/discard actions,
and removing/discarding saved game selections. Browser validation injects the
observed `cz-shortcut-listen` body attribute to check extension-triggered hydration
warnings without suppressing warnings inside the app tree.
