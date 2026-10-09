# Yapnix Development Rules

## Repository purpose

Yapnix is a monorepo containing the Android game client, NestJS API, Next.js administration dashboard, and shared documentation.

## Project structure

- `android/`: Kotlin Android application with Jetpack Compose.
- `backend/`: NestJS REST API and administrative service.
- `admin/`: Next.js Yapnix Studio dashboard.
- `docs/`: architecture, database, API, recording, setup, and roadmap documentation.

## Coding conventions

- Use strict TypeScript settings in backend and admin code.
- Use Kotlin modules with explicit dependency boundaries.
- Keep the Android gameplay layer independent from camera, microphone, and recording implementations.
- Keep public content APIs explicitly publication-aware.
- Never commit secrets, OAuth credentials, service-role keys, or local environment files.
- Do not force authentication before guest gameplay.
- Keep recordings local to the device unless the user explicitly chooses to export or share them.
- Treat client-side answer validation as non-authoritative.

## Testing expectations

- This development machine has 8 GB RAM. Run builds and test suites sequentially; never overlap Gradle and Next.js builds or start an emulator for automated checks. Keep Gradle and test workers limited to one and preserve the configured memory limits.

- Add unit tests for answer normalization and matching.
- Add API integration tests for authentication and publication isolation.
- Add database migration checks before merging.
- Run Android unit tests and the backend/admin test suites before release.
- Record physical-device validation for camera, microphone, speech recognition, and video export.

## Security rules

- Admin authorization must be enforced in the NestJS backend.
- A Google account alone must never grant administrator access.
- Supabase service-role credentials must only exist in the backend environment.
- Public APIs must not expose unpublished content.
- Auth tokens must be validated on the server.
