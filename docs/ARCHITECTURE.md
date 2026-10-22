# Yapnix Architecture

## Android

- Kotlin and Jetpack Compose.
- Coroutines and Flow.
- MVVM/ViewModel state ownership.
- CameraX for front-camera frames; a dedicated game composition encoded with microphone audio.
- EGL/OpenGL encoder input, MediaRecorder H.264/AAC output, worker-thread finalization and track validation.
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

Video remains local to the Android device. A microphone foreground service composes a dedicated game bitmap on main and uploads it through a worker-owned EGL/OpenGL encoder surface into H.264 with AAC microphone audio. Direct Canvas locks on encoder surfaces are not used. No MediaProjection or device-screen recording is used. The camera overlay can be dragged and resized independently of game content. Games and images are stored locally; every round image is decoded before Play. Each question reveals its answer after five seconds without speech recognition or scoring. Pausing, backgrounding or leaving stops recording. Finalized videos support preview, explicit export/share and confirmed deletion. See [reaction flow and physical-device checks](ANDROID-REACTION-FLOW.md).

Game metadata and all question pictures are fetched before Play becomes available; Coil caches image bytes using the question ID and storage path rather than the temporary signed URL. The square picture stays visible during the prompt; the timer starts immediately after image readiness and text-to-speech completion, with no dark cover or artificial preparation countdown. Spoken feedback completes before advancing. Android text-to-speech failure falls back to visual play.

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

## Android updates

The Android app checks public GitHub releases at launch/resume (six-hour in-process throttle) and manually from Profile. It compares numeric three-part versions, ignores drafts, and accepts only exact HTTPS APK URLs under the Yapnix release repository. Test releases are included. Prompts appear outside gameplay. Installation uses the browser and Android installer with explicit user action; Later dismisses a version for the current process. No authentication, backend environment change or automatic installation is required.
