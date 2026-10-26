# Yapnix deployment: GitHub → Supabase → Render → Vercel → Android release

Use the single repository https://github.com/sahzadahmad246/yapnix. Deploy the
backend and admin as separate services; keep Android and migrations in the same
repository. This guide uses example domains. Replace them with the actual domains
assigned by Render and Vercel, including any generated suffix.

## 1. Keep the existing Supabase project

Your games, five animal questions and image files are stored in Supabase, not in
GitHub or the hosting services. Use the SAME Supabase project in all environments
to keep that content. Do not create another database for this deployment.

If needed, run `supabase/sql-editor/upgrade_existing.sql` in Supabase SQL Editor
as postgres. If migrations 001–008 are already installed, the smaller
`supabase/sql-editor/upgrade_icon_library.sql` applies migrations 009–010, including both uploaded artwork and bundled icons. Do not run
`setup_fresh.sql` against an existing installation. Then run `verify_setup.sql`.

Find your project URL and API keys in the project's Connect dialog / Settings →
API Keys. The current local `.env` files already contain the working values;
copy those values privately into hosting settings, not into this document.
Use the same anon/publishable key for backend, Studio and Android. Keep the
service-role/secret key exclusively on the backend. Admin approval remains in
`admin_users`; sign-in by itself never grants administrator rights.

## 2. Deploy NestJS on Render

1. Sign in at https://dashboard.render.com using GitHub.
2. Choose New → Web Service. Authorize repository access if requested.
3. Connect `sahzadahmad246/yapnix` and select branch `main`.
4. Configure:

| Setting | Value |
| --- | --- |
| Name | `yapnix-api` (or another available name) |
| Language/runtime | Node |
| Root Directory | Leave blank: repository root |
| Build Command | `npm ci --include=dev && npm run build -w backend` |
| Start Command | `npm run start -w backend` |
| Health Check Path | `/api/docs` |

The repository root is necessary because npm workspaces share `package-lock.json`.
Do not run the monorepo's top-level build here; that also builds the admin.

5. In the creation form's Environment Variables section, add:

| Key | Value |
| --- | --- |
| `NODE_ENV` | `production` |
| `NODE_VERSION` | `22` |
| `SUPABASE_URL` | Your existing Supabase project URL |
| `SUPABASE_ANON_KEY` | The working public key from your local backend environment |
| `SUPABASE_SERVICE_ROLE_KEY` | The working backend-only key from your local backend environment |
| `CORS_ORIGINS` | `http://localhost:3000` temporarily; replace with the real Vercel origin in step 4 |

You do not need to upload `.env`, and do not override `PORT`: Render supplies it.
The backend reads `process.env.PORT` and binds to the hosting port.

6. Create the Web Service and wait for deployment to finish.
7. Copy its exact public HTTPS URL, e.g. `https://yapnix-api.onrender.com`.
8. Open `<render-origin>/api/docs` and `<render-origin>/api/games`. The latter
   should return your published games as JSON. Opening the bare origin `/` may
   return 404, because the application exposes routes under `/api`.

For later environment changes, use the service's left menu → Environment → Add
Environment Variable, then Save, rebuild, and deploy (or Save and deploy for
runtime-only values).

A Free Render service sleeps after 15 minutes without traffic. Its cold start can
exceed Android's request timeout. Use an always-on instance for regular players;
if using Free for early testing, open the docs URL to wake it before testing.

## 3. Deploy Next.js Studio on Vercel

1. Sign in at https://vercel.com using GitHub.
2. Choose Add New → Project. Import `sahzadahmad246/yapnix`.
3. Set project name to `yapnix-studio` (or another available name).
4. Framework Preset: Next.js. Root Directory: `admin`.
5. Enable Include source files outside of the Root Directory in the Build Step
   if this setting is shown. The admin package uses dependencies installed in
   the parent workspace.
6. Build Command: `npm run build`. Install Command: `npm ci`. Leave Output
   Directory at the Next.js default. Select Node.js 22.x in project build settings
   if a version selector is available.
7. Expand Environment Variables before clicking Deploy. Add:

| Key | Value |
| --- | --- |
| `NEXT_PUBLIC_SUPABASE_URL` | The same Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | The same public key |
| `NEXT_PUBLIC_BACKEND_API_URL` | `<render-origin>/api`, e.g. `https://yapnix-api.onrender.com/api` |

The `/api` suffix is required. Never put the service-role key in Vercel or a
`NEXT_PUBLIC_` variable. Enter literal values, not references to your local shell
variables. Apply these variables to Production. Preview is optional and requires
separate allowed origins/redirects if you intend to use preview deployments.

8. Click Deploy. Copy the exact production origin from the project's Domains
   panel, e.g. `https://yapnix-studio.vercel.app`.
9. For later changes, go to Project → Settings → Environment Variables. Save the
   new values and redeploy from Deployments: public variables are embedded at build
   time, so saving them alone does not update an existing deployment.

## 4. Connect deployed Studio, backend and Google login

1. Render → your API service → Environment: set `CORS_ORIGINS` to the exact Vercel
   origin, without a trailing slash. To keep local development working, use
   `<vercel-origin>,http://localhost:3000` (comma-separated). Save and deploy.
2. Supabase → Authentication → URL Configuration: set Site URL to the Vercel
   production origin. Add both the origin and `<vercel-origin>/` to Redirect URLs,
   plus `yapnix://auth/callback`. Keep `http://localhost:3000` for local testing.
3. Supabase → Authentication → Sign In / Providers → Google: leave the working
   Google client configuration enabled.
4. In Google Cloud Console, the Web OAuth client's authorized redirect URI stays
   `https://<project-ref>.supabase.co/auth/v1/callback`. It is the Supabase callback,
   not the Vercel domain. The client secret belongs in Supabase only.
5. Open the production Studio domain, sign in with your approved account, and
   verify Guess → Animals, the five answers, timer and prompt. Ensure Guess and
   Animals are published. Test `/api/games` while signed out.

## 5. Build a signed Android APK

1. Open the `android` folder in Android Studio.
2. Edit the ignored `android/local.properties`. Keep your existing `sdk.dir` and
   working Supabase settings. Replace only `API_BASE_URL` with the Render HTTPS
   origin plus `/api`:

```properties
API_BASE_URL=https://yapnix-api.onrender.com/api
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-public-key>
```

Use your actual Render origin. Do not ship localhost, emulator IPs, service-role
keys or a Google client secret in the app.

3. Build → Generate Signed Bundle / APK → APK → Next.
4. Create or choose a keystore. Store it outside the repository and back it up
   securely with its alias/password. Keep using the same signing key for APK
   updates. Do not commit keystores or passwords.
5. Choose the `release` variant, finish the wizard and use its Locate link to find
   the signed `.apk`. Android Studio may output to the destination chosen in the
   wizard; use Locate instead of assuming a fixed path.
6. Install it on a physical device and verify guest gameplay, five images, prompt,
   speech recognition, timer, optional Google login, and recording/export. A debug
   APK already installed with another key may need uninstalling; uninstalling
   removes local app data, so export any recordings first.

For later releases, increase `versionCode` and update `versionName` in
`android/app/build.gradle.kts` before building. The current values are 1 / 0.1.0.
An APK is directly installable; an AAB is intended for Google Play.

## 6. Publish the APK on GitHub Releases

1. Open https://github.com/sahzadahmad246/yapnix/releases.
2. Choose Draft a new release / Create a new release.
3. Create a tag `v0.1.0`, targeting the tested `main` commit. If that tag already
   exists, choose the next version instead.
4. Title: `Yapnix v0.1.0`. Describe the tested features and any known limitations.
5. Attach the signed APK in the binary upload area, e.g. `yapnix-v0.1.0.apk`.
   Do not commit the APK, keystore or `.env` files to Git.
6. Mark this first test release as a pre-release and publish it after device checks.
7. Share its release URL. A private repository's releases require repository
   access; use Play internal testing for testers who should not access the source.

## 7. Subsequent updates

Push source changes to `main`; connected Render/Vercel services can deploy those
changes automatically. Apply new SQL migrations explicitly before deploying code
that requires them. Changes to games/answers in Studio take effect from Supabase
without pushing Git. Android code changes require a newly signed APK or Play release.

## Official references

- Render Node deployment: https://render.com/docs/deploy-node-express-app
- Render variables: https://render.com/docs/configure-environment-variables
- Render free-instance behavior: https://render.com/docs/free
- Vercel monorepos: https://vercel.com/docs/monorepos
- Vercel variables: https://vercel.com/docs/environment-variables
- Supabase redirects: https://supabase.com/docs/guides/auth/redirect-urls
- Android signing: https://developer.android.com/studio/publish/app-signing
- GitHub Releases: https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository

## Local signed release configuration

The release variant pins `API_BASE_URL` to `https://yapnix-api.onrender.com/api`.
Local signing configuration is read from the ignored file
`android/.release-signing/signing.properties`; its `storeFile` is resolved relative
to the Android root. The private key and credentials are never committed.
Back up the entire `.release-signing` folder securely: future APK updates need the
same key. A fresh clone requires restoring that private signing material before
rebuilding a matching signed APK. Without it, Gradle's release output is unsigned.

With the private signing files and existing public Supabase settings restored:

```sh
cd android
./gradlew :app:testReleaseUnitTest :app:assembleRelease --max-workers=1
```

The signed output is `android/app/build/outputs/apk/release/app-release.apk`.
Release notes for the first test build are in `docs/releases/v0.1.0.md`.

## Catalogue deployment order

Apply `upgrade_icon_library.sql` (requires 001–008) or the complete `upgrade_existing.sql` before publishing the updated API/Studio. If a Git-triggered API deployment arrives first, guest catalogue reads fall back to the previous publication-filtered query with default bundled icons while the new columns are unavailable. Selecting/persisting icons still requires the SQL upgrade. New icon writes check schema support first and return an actionable migration error before mutating content if the upgrade is missing. Then deploy/update Studio and install the new Android APK to see selected icons offline.
