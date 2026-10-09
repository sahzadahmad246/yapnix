# Yapnix Setup Guide

## 1. Create the Supabase project

1. Open Supabase Cloud and create a new project.
2. Record the project URL and project anonymous key.
3. Store them in the backend and Android local configuration as environment-only values.
4. Do not commit the project key to source control.

Example configuration:

```text
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_SERVICE_ROLE_KEY=<service-role-key>
```

The service-role key is required only by the NestJS backend and must never be sent to the Android application.

## 2. Configure Google OAuth

Follow [GOOGLE_OAUTH.md](GOOGLE_OAUTH.md). Both Studio and Android use browser-based Supabase Google OAuth. Android uses PKCE and returns to `yapnix://auth/callback`; the current implementation does not use a native Android OAuth client ID.

## 3. Configure the database

For copy-paste setup, use one of the [complete SQL Editor scripts](../supabase/README.md): **upgrade_existing.sql** if your initial Yapnix tables exist, or **setup_fresh.sql** for a fresh project. These include private Storage automatically. You can also apply individual migrations in filename order:

1. [`202610090001_initial_schema.sql`](../supabase/migrations/202610090001_initial_schema.sql) creates the schema and initial policies/functions. Run it once.
2. [`202610090002_existing_project_api.sql`](../supabase/migrations/202610090002_existing_project_api.sql) applies API grants and compatibility policies/functions.
3. [`202610090003_atomic_question_updates.sql`](../supabase/migrations/202610090003_atomic_question_updates.sql) adds atomic question editing. Apply this before deploying the backend's question-editing changes.
4. [`202610090004_content_readiness.sql`](../supabase/migrations/202610090004_content_readiness.sql) adds media-backed publication readiness checks and atomic level assignment.
5. [`202610090005_progress_sync.sql`](../supabase/migrations/202610090005_progress_sync.sql) adds signed-in player progress synchronization.
6. [`202610090006_private_content_storage.sql`](../supabase/migrations/202610090006_private_content_storage.sql) creates the private image bucket and restrictive Storage policy.

If you already applied migration `001`, apply outstanding migrations `002`–`006`, one at a time in **Supabase SQL Editor**, in that order, if you have not already applied them. Do not rerun migration `001`. The later migrations are written to upgrade the existing schema safely. Validate against a disposable database first; see [TESTING.md](TESTING.md).

The application must create:

- `games`
- `levels`
- `questions`
- `question_answers`
- `level_questions`
- `admin_users`
- `player_progress`
- `media_records`

Use the migration files for executable SQL; [`DATABASE.md`](DATABASE.md) describes the data model.

## 4. Configure storage

Migration `006` or either complete SQL Editor script configures Storage automatically. If you only need Storage, run [`supabase/storage_setup.sql`](../supabase/storage_setup.sql) in **Supabase SQL Editor**. This creates or updates the exact bucket `content-images` as **private**, limits uploads to 5 MiB, and blocks direct anonymous/authenticated access to that bucket, including access through older permissive policies.

Studio uploads through the approved-admin backend endpoint. The backend validates and converts JPEG/PNG/WebP to WebP, writes a media record, and issues temporary signed previews. Public gameplay receives signed image URLs only after publication checks. Do not make the bucket public or add client upload policies. Reload a level if its signed image URLs have expired.

Upload images through Studio so their required media records are created. Manually uploading a file in the Storage dashboard does not register it as question media.

The backend service-role key performs Storage operations and stays exclusively in the backend environment. Recordings never go into this bucket.

## 5. Deploy the NestJS backend

Deploy the backend to Render or another Node.js-compatible host.

Required environment variables:

```text
PORT=3333
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_SERVICE_ROLE_KEY=<service-role-key>
NODE_ENV=production
CORS_ORIGINS=https://<your-admin-domain>
```

The backend must validate Supabase access tokens and map their user IDs to approved administrator roles.

For local development, start both apps from the repository root with `npm run dev`. Yapnix Studio is at `http://localhost:3000`; the backend is at `http://localhost:3333`, with API routes under `/api` and Swagger at `http://localhost:3333/api/docs`.

The admin `.env` must use exact uppercase Next.js names: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, and `NEXT_PUBLIC_BACKEND_API_URL`. Restart the admin dev server after changing `.env`. Add `http://localhost:3000` to Supabase **Authentication → URL Configuration → Redirect URLs** for the local Google OAuth callback.

To grant your own Google account initial administrator access, first sign in once so Supabase creates the user. Then run this in Supabase SQL Editor, replacing the email:

```sql
insert into public.admin_users (supabase_user_id, role, approved)
select id, 'super_admin', true
from auth.users
where email = 'you@example.com'
on conflict (supabase_user_id)
do update set role = 'super_admin', approved = true;
```

## 6. Deploy the Next.js admin dashboard

Deploy the admin application to Vercel.

Required environment variables:

```text
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon-key>
NEXT_PUBLIC_BACKEND_API_URL=https://<backend-host>/api
```

The dashboard should call the NestJS API rather than directly accessing privileged database operations.

## 7. Configure Android build environment

For local development:

1. Copy the Android template into the ignored local configuration file:

   ```text
   cp android/local.properties.example android/local.properties
   ```

2. Add the Supabase project URL and anonymous key.
3. Add `yapnix://auth/callback` to Supabase Auth Redirect URLs.
4. Set `API_BASE_URL` based on where the app runs:
   - Android emulator: `http://10.0.2.2:3333/api`.
   - Physical phone on the same Wi-Fi as the laptop: `http://<laptop-LAN-IP>:3333/api`.
5. On Linux, set `sdk.dir` to your SDK path, commonly `/home/<user>/Android/Sdk`.
6. Start backend and admin locally on the laptop with `npm run dev`.
7. Build the debug APK with `cd android && ./gradlew assembleDebug`.
8. Install `android/app/build/outputs/apk/debug/app-debug.apk` on the phone. Keep the phone and laptop on the same Wi-Fi, allow incoming laptop port `3333` through the firewall, and update `API_BASE_URL` if the laptop's LAN IP changes.

The Android app should use a development Supabase project until the release configuration is ready. Do not place the Supabase service-role key in Android.

## 8. Local progress behavior

- The current app stores progress in app-private SharedPreferences with serialized writes.
- Save the current level, question index, score, and session state locally.
- Use local storage for the most recent result and question history.
- After optional Google login, choose **Sync progress** to upload/restore saves through the backend. The newer timestamp wins per level; these are player saves, not verified competitive scores.
- If a user ignores sign-in, periodically present a non-blocking prompt to sign in or create an account.
- Never delete local progress without the user’s explicit action.

## 9. Recording storage behavior

- Video stays on the device for MVP.
- Do not automatically upload recordings.
- Provide explicit save and share actions.
- Use a clear app-owned storage location.
- Delete or export a recording only after user confirmation.

## 10. Builds on an 8 GB machine

Run one check at a time. Avoid running Android Studio's emulator, a Gradle build, and a Next.js build together. Gradle uses one worker and a 1 GB heap; unit tests also use one worker. Use a physical phone when possible.

```sh
npm test
npm run lint
npm run build
cd android
./gradlew testDebugUnitTest assembleDebug --no-daemon --max-workers=1
```

Do not run these commands concurrently. Physical-device checks in [TESTING.md](TESTING.md) are still required before release.
