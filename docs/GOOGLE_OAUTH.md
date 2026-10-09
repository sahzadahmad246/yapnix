# Google login setup

Studio and Android currently use **browser-based Supabase OAuth**. Android protects the authorization-code exchange with PKCE. Guest play is available without signing in.

## Google Cloud and Supabase

1. In Google Auth Platform, configure Branding, Audience (including test users while in testing), and Data Access for `openid`, email and profile.
2. Create a **Web application** OAuth client. Add the Studio origin, such as `http://localhost:3000`, to authorized JavaScript origins.
3. Copy the exact callback shown in **Supabase Authentication → Providers → Google** into Google's authorized redirect URIs. Hosted Supabase normally uses `https://<project-ref>.supabase.co/auth/v1/callback` — there is no `/google` suffix.
4. Enable the Google provider in Supabase and enter the Web client ID and secret there. Keep the Google secret out of the repository and both clients.
5. In **Supabase Authentication → URL Configuration**, set the Studio Site URL and allow these Redirect URLs:
   - `http://localhost:3000` for local Studio.
   - Your exact production Studio URL.
   - `yapnix://auth/callback` for Android.

The current Android implementation does not use `GOOGLE_ANDROID_CLIENT_ID`, a native Google SDK or signing-certificate OAuth configuration. A future native Credential Manager integration would need its own setup.

Provider references: [Supabase Google login](https://supabase.com/docs/guides/auth/social-login/auth-google), [mobile deep linking](https://supabase.com/docs/guides/auth/native-mobile-deep-linking), and [PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow).

## Troubleshooting: provider is not enabled

`Unsupported provider: provider is not enabled` means Google is disabled in the Supabase project receiving the login request. In that project's **Authentication → Sign In / Providers → Google**, enable Google, enter the Google Web OAuth client ID and secret, and save. The OAuth client needs the exact Supabase callback URL shown on that page. Then allow `http://localhost:3000` in Supabase URL Configuration and retry login. If Google's consent screen is in testing, add your Google account as a test user.

Make sure the project shown in the failed login URL is the same project configured for Studio. SQL migrations and administrator approval do not enable OAuth providers. Provider changes take effect without rebuilding Studio; changing `NEXT_PUBLIC_SUPABASE_URL` requires a new Studio build and restart because public environment variables are embedded at build time.

## Client configuration

Studio's ignored local environment file uses:

```text
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon-key>
NEXT_PUBLIC_BACKEND_API_URL=http://localhost:3333/api
```

Android's ignored `local.properties` uses:

```text
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
API_BASE_URL=http://10.0.2.2:3333/api
```

Use your computer's LAN address instead of `10.0.2.2` on a physical phone. Rebuild Android after changes. Never place a service-role key in either client.

## Administrator approval

Google sign-in alone does not grant Studio access. An existing trusted operator must grant an approved role in `public.admin_users`. See [SETUP.md](SETUP.md) for the initial SQL. Backend guards validate both the token and approval on every privileged request.

## Manual verification

Check Studio login with an approved and an unapproved account. On Android, check successful login, cancellation, expired login, app restart, token refresh, explicit progress sync and sign-out. Verify guest play and local saves still work offline and after sign-out. These provider/device checks have not yet been completed locally.
