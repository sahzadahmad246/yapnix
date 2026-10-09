# Yapnix Deployment Plan

## Recommended production topology

```text
Android App
    │
    ├── Supabase Auth and Google OAuth
    ├── Signed content image URLs from the API
    │
    └── NestJS API on Render

Next.js Yapnix Studio
    │
    ├── Vercel hosting
    ├── Supabase Auth
    └── NestJS admin API on Render
```

## Supabase

Use Supabase Cloud for:

- PostgreSQL.
- Google OAuth.
- Authentication.
- Storage.
- Database migrations.

Supabase Cloud is the preferred location for shared identity, data, and media storage.

## Render

Use Render for the NestJS backend if the API needs:

- Admin authorization.
- Content publishing.
- Supabase control-plane access.
- Private environment variables.
- REST and OpenAPI endpoints.

The backend must be deployed separately from the Next.js dashboard.

## Vercel

Use Vercel for the Next.js admin dashboard if the dashboard:

- Uses browser Supabase authentication, with backend token and administrator checks.
- Calls the NestJS API.
- Serves the dashboard without direct database access.
- Uses static Next.js rendering where appropriate.

Do not deploy a full data-access layer to Vercel without a clear security review.

## Android distribution

The Android application can be distributed through:

- Google Play.
- Internal Test Track.
- Managed Android device distribution.

The initial release should not include production OAuth secrets or service-role credentials.

## Google OAuth deployment requirements

- Configure a Web OAuth client in Google Cloud Console for Supabase.
- Add the exact Supabase callback URL to the Google Web client.
- Allow `yapnix://auth/callback` and the Studio origin in Supabase Auth Redirect URLs.
- Keep the Google OAuth Client Secret in Supabase only.
- The current Android browser PKCE flow does not use a native Google OAuth client.

## Production checklist

- Replace all placeholders.
- Use HTTPS.
- Enable content security policy.
- Configure CORS.
- Restrict storage access.
- Configure proper email and domain settings.
- Add exact error and retry handling.
- Verify admin authorization on the backend.
- Test Google login in a signed release build.
- Test local video storage and export.

Apply migrations 001–006 in order (only migrations not already applied), or use the matching complete SQL Editor bundle in `supabase/sql-editor/`. Migration 006 creates the private `content-images` bucket. Set backend `CORS_ORIGINS` to the exact Studio origin in production. See [SETUP.md](SETUP.md).
