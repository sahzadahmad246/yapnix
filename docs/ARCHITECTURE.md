# Yapnix Architecture

## Android

- Kotlin and Jetpack Compose.
- Coroutines and Flow.
- MVVM/ViewModel state ownership.
- CameraX for preview and capture.
- Android media APIs for video output.
- HTTP API calls through HttpURLConnection on IO dispatchers.
- App-private SharedPreferences for local progress, with serialized writes.
- Supabase browser OAuth with PKCE for optional Google sign-in; Android Keystore encrypts stored tokens.

The Android application should not use the Supabase service-role key.

## Backend

- NestJS.
- TypeScript strict mode.
- REST endpoints.
- Swagger/OpenAPI.
- Supabase Auth JWT validation.
- Supabase PostgreSQL queries.
- Supabase Storage for content.
- Server-side admin authorization.

## Admin

- Next.js and TypeScript.
- Shared CSS and React components.
- Browser-managed Supabase sessions; tokens validated by NestJS.
- Approved administrator roles enforced by NestJS on privileged routes.
- NestJS API for all content mutation.

## Local progress

The Android app stores progress locally as a non-authoritative preference. It periodically prompts users to sign in or create an account. Synchronization happens only after login and only when the user explicitly chooses Sync progress. The newer timestamp wins per level.

## Recording

Video remains local to the Android device in the MVP. CameraX records a camera reaction video in app-private storage. Finalized videos can be previewed, exported through the Android document picker, shared through FileProvider, or deleted after confirmation. No automatic upload occurs. This implementation does not composite the game screen into the video. Simultaneous microphone recording and speech recognition require physical-device validation.

## Public API trust boundary

Public APIs only return published content. Server-side answer validation is not part of the MVP, so client responses cannot be treated as authoritative competition data.

## Standalone speaking games

The current model is Category → Game → Questions (migration 007), for example
Guess → Animals → five image/answer questions. Admin creates game metadata and
its private session configuration atomically, assigns ordered questions directly
to the game, and publishes the complete game in one action. Android groups games
by category and calls `/api/games/:id/play`; there is no level picker.

The legacy session/progress UUID is retained for backward compatibility. New
speaking mechanics require a player implementation and a validated question schema;
creating a category alone does not enable unsupported gameplay. See
[the workflow and upgrade guide](admin-game-workflow.md).
