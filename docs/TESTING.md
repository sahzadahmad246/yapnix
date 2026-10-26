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

Checks include applying all ten migrations, reapplying compatibility/update migrations, anonymous and authenticated publication isolation, inactive-question filtering, progress ownership enforcement, mutation-function permissions, alias preservation/removal, missing-question errors, and rollback after an injected answer-insertion failure or invalid question assignment.

The auth fixture implements only user IDs and `auth.uid()`. A minimal Storage schema checks private-bucket policies against a legacy permissive policy. These checks run real PostgreSQL policies and functions but do not validate real Supabase Auth/Storage services or the deployed project's existing policies.

## Android

```sh
cd android
./gradlew testDebugUnitTest assembleDebug --no-daemon --max-workers=1
```

Answer-matching unit tests cover punctuation, aliases, partial/empty answers, Unicode and device locale. PKCE tests cover the RFC challenge vector and random verifiers; sync policy tests cover timestamp and completion conflicts. Reveal-timer/readiness and image-memory budget tests cover five-second deadlines, stale callbacks and large rounds. Physical-device testing remains required for microphone permissions, lifecycle/timers, Google login, and camera recording/export; speech recognition is not used.

## Physical-device and staging validation log

Status: **not run**. No connected-device or live-project validation is claimed.

| Check | Required observation | Result |
| --- | --- | --- |
| Guest play | Start, five-second reveal, replay without login | Pending |
| Resume | Background, resume, kill/relaunch; resume at the next unresolved picture | Pending |
| Microphone | Grant/deny/revoke recording access; gameplay without recording needs no microphone | Pending |
| Google login | Success/cancel, callback, app restart, expired access-token refresh, sign-out | Pending |
| Progress sync | Two devices, newer-save merge, changed content, offline failure; local saves preserved | Pending |
| Studio access | Approved admin allowed; ordinary Google account denied | Pending |
| Images | Upload/reuse; draft image never exposed through guest APIs; private bucket direct access denied | Pending |
| Publication | Incomplete games blocked; published category/game available to guest | Pending |
| Recording | Camera grant/deny; start/stop/background; playable finalized local video | Pending |
| Audio coexistence | Microphone reaction audio is present and synchronized; TTS and sound cues do not interrupt capture | Pending |
| Export/share | Portrait reel preview/seek, document export, share URI, cancel, confirmed delete; original quality preserved | Pending |
| Reel layout / HD | 9:16 output, picture left / portrait camera right, aligned tops and timer/reveal bottoms, 1080p or720p fallback, mic/sync | Pending |
| Profile photo | Google photo/name from stored session, missing/invalid image fallback, sign-out clears identity | Pending |
| Direct update | Download/progress/cancel/retry, signer rejection, unknown-source permission, explicit install, interrupted download | Pending |

Record phone model, Android version, date, and actual results here before release. Dedicated game-canvas capture includes front-camera frames and microphone audio. Text-to-speech, five-second reveals, recorded sound/sync, recording pause/resume and update installation still need physical-device validation. See [v0.5.0 phone checklist](releases/v0.5.0.md).

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

Admin test-only dependencies belong to `admin/package.json` so filtered workspace installs include them. Production TypeScript excludes `*.spec.ts` and `*.spec.tsx`; run `npm run typecheck -w admin` to type-check both production and test code explicitly. `npm run test -w admin` type-checks the test files before executing every test.

## Catalogue/home update — 26 October 2026

Coverage added for square artwork re-encoding, rejected non-square/missing library icons, admin-only artwork uploads, signed artwork publication isolation, migration 009 reapplication/create/update/clear/reference checks, reveal-only question edits preserving stored aliases, and the shared camera/image geometry. Physical-device results remain pending for the home animation, installed offline narration, mirrored preview/export, pause/resume within the same MP4, leave confirmation and finalized playback.

The migration suite passed in isolated PGlite during this update, including all ten migrations and 009 reapplication. The temporary runner expands psql includes and omits only `create extension pgcrypto` (built-in `gen_random_uuid` is used). This verifies the SQL logic and RLS fixtures; it does not validate the deployed Supabase project.

The earlier production Studio build emitted a security warning for the then-installed Next.js 16.4.0 dependency. The [14 October vendor advisory](https://nextjs.org/blog/nextjs-security-update-october-14-2026) recommends a patched release. The build was retried after the tool’s notice; this update subsequently patches Studio to Next.js 16.4.1.

## Bundled icon picker validation

Icon tests cover searching/selecting category and sub-game icons with no upload, replacing an existing image, retaining optional square uploads, allowed/invalid API keys, and migration 010 defaults, repeatability, persisted choices and publication isolation. `npm run icons:check` checks shared SVG/metadata, generated Studio assets, Android vectors, licence distribution and the API/SQL key allowlists. The Next.js dependency was patched from 16.4.0 to 16.4.1 in this update before pushing.

The production dependency audit also identified request/upload parser denial-of-service advisories. Root overrides now require `body-parser` 2.4.0+ and `multer` 2.5.0+ (the latter within Nest’s Express adapter). The HTTP tests are rerun with these patched versions. Admin builds resolve the workspace’s Next CLI through `admin/build.mjs`, retaining the 1 GB heap limit even when npm installs Next locally instead of hoisting it.

Final pre-push checks passed: 42 backend unit tests, 33 HTTP integration tests, 25 Studio tests, 28 Android unit tests, Studio production and Android debug builds, repository lint, shared icon consistency, SQL bundle consistency and the isolated PGlite migration suite. `npm audit --omit=dev` reports zero vulnerabilities with the patched lockfile. No hosted database migration or physical-device validation is claimed.
