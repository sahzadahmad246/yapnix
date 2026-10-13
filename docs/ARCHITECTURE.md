# Yapnix Architecture

## Android

- Kotlin and Jetpack Compose.
- Coroutines and Flow.
- MVVM/ViewModel state ownership.
- CameraX for front-camera preview; MediaProjection for consented game-screen capture.
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

Video remains local to the Android device. A media-projection foreground service records the screen with a CameraX TextureView front-camera preview visible in the game. Recordings intentionally contain video only: SpeechRecognizer owns the microphone for answers. Android capture consent is requested for each new recording. Pausing, backgrounding or leaving a round stops and finalizes it. Finalized videos can be previewed, exported through the Android document picker, shared through FileProvider, or deleted after confirmation. No automatic upload occurs. Physical-device validation is pending.

Game metadata and all question pictures are fetched before Play becomes available; Coil caches image bytes using the question ID and storage path rather than the temporary signed URL. Each question also waits for image-display success and text-to-speech completion before starting its countdown. Spoken feedback completes before advancing. Android text-to-speech failure falls back to visual play.

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
