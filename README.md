# Yapnix

Yapnix is a collection of speaking games organized as Category → Game → Questions. The first category, Guess, contains standalone games such as Animals, Foods and Everyday Objects: players identify each picture aloud before the countdown expires. There are no levels.

## Goal

The initial release should be a polished, reliable, expandable Android game. It should support guest gameplay, optional Google sign-in, local progress, and local video recording.

## Monorepo

- `android/` — Android app built with Kotlin and Jetpack Compose.
- `backend/` — NestJS API for public gameplay and protected administration.
- `admin/` — Next.js dashboard for Yapnix Studio.
- `docs/` — product, architecture, database, API, recording, setup, and roadmap documents.

## Deployment plan

- **Supabase Cloud**: PostgreSQL, Supabase Auth, Google OAuth, and Storage.
- **Render**: NestJS backend.
- **Vercel**: Next.js administration dashboard.
- **Android devices**: APK/AAB from Google Play or internal release pipeline.

## Local-first behavior

- Guest players can play without signing in.
- Local progress is stored on the device and synchronized only after optional Google sign-in.
- The app periodically asks users to sign in or create an account when local progress has not been synchronized.
- Video recordings are stored locally and are not uploaded automatically.

## Setup

Apply the [current content upgrade and workflow](docs/admin-game-workflow.md) for categories and standalone games. See [`docs/SETUP.md`](docs/SETUP.md) for the initial configuration and deployment checklist.

## Status

The repository implements protected backend content/image APIs, a Studio image library and publication workflow, and Android guest voice gameplay with optional Google login, explicit progress sync, and local camera recording/export/share. Live Supabase and physical-device validation remain required. See [the implementation status and remaining work](docs/STATUS.md) for the code audit and validation results.
