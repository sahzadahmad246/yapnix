# Yapnix Database Design

## Identity and authorization

The user’s Supabase identity is stored by Supabase Auth. Yapnix stores only the approved application role in `admin_users`.

## Tables

### games

```sql
create table public.games (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text,
  slug text not null unique,
  difficulty smallint not null default 1,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

### levels

```sql
create table public.levels (
  id uuid primary key default gen_random_uuid(),
  game_id uuid not null references public.games(id) on delete cascade,
  name text not null,
  description text,
  question_count integer not null default 5,
  default_time_limit_seconds integer not null default 5,
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

### questions

```sql
create table public.questions (
  id uuid primary key default gen_random_uuid(),
  image_storage_path text not null,
  primary_answer text not null,
  difficulty smallint not null default 1,
  language text not null default 'en',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

### question_answers

```sql
create table public.question_answers (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null references public.questions(id) on delete cascade,
  answer text not null,
  is_primary boolean not null default false,
  created_at timestamptz not null default now()
);
```

### level_questions

```sql
create table public.level_questions (
  level_id uuid not null references public.levels(id) on delete cascade,
  question_id uuid not null references public.questions(id) on delete restrict,
  order_index integer not null,
  created_at timestamptz not null default now(),
  primary key (level_id, question_id),
  unique (level_id, order_index)
);
```

### admin_users

```sql
create table public.admin_users (
  id uuid primary key default gen_random_uuid(),
  supabase_user_id uuid not null unique,
  role text not null default 'content_admin',
  approved boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

### player_progress

```sql
create table public.player_progress (
  id uuid primary key default gen_random_uuid(),
  supabase_user_id uuid references auth.users(id) on delete cascade,
  level_id uuid references public.levels(id),
  question_index integer not null default 0,
  score integer not null default 0,
  correct_count integer not null default 0,
  completed boolean not null default false,
  content_fingerprint text not null default '',
  last_played_at timestamptz not null default now(),
  synced_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

### media_records

```sql
create table public.media_records (
  id uuid primary key default gen_random_uuid(),
  storage_path text not null unique,
  original_filename text not null,
  mime_type text not null,
  size_bytes bigint not null,
  width integer,
  height integer,
  status text not null default 'ready',
  created_at timestamptz not null default now()
);
```

## Content availability

Public gameplay queries must use `is_published = true` and must not return draft content.

## Local progress and synchronization

Guest progress remains in Android local storage. When the user signs in, the app can synchronize records through `player_progress`.

The local record should contain:

- Level ID.
- Question index.
- Score.
- Correct count.
- Last played timestamp.
- Completion flag and ordered question IDs as a content fingerprint.
- A synchronization timestamp on the backend.

The backend must not consider local-only progress authoritative for competitive scoring.

## Mutation and media boundaries

Apply the executable migrations in `supabase/migrations` rather than copying this reference schema. Migration 003 provides atomic question editing; 004 adds atomic creation and deferred publication readiness checks; 005 provides authenticated, owner-scoped progress synchronization that rejects stale overwrites; 006 configures private Supabase Storage.

Published levels require exactly their configured number of active questions, ready media records, and primary answers. Published games require a published level. Unpublish a game before unpublishing its last level, and unpublish a level before making incomplete content edits.

Content images live in the private `content-images` bucket. `supabase/storage_setup.sql` blocks direct client access. Approved Studio operations go through the backend; published gameplay receives temporary signed URLs. Local Android recordings are unrelated to Storage and are never synchronized automatically.
