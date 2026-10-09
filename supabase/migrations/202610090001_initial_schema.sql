create extension if not exists pgcrypto;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table public.games (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text,
  slug text not null unique,
  difficulty smallint not null default 1 check (difficulty between 1 and 5),
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.levels (
  id uuid primary key default gen_random_uuid(),
  game_id uuid not null references public.games(id) on delete cascade,
  name text not null,
  description text,
  question_count integer not null default 5 check (question_count > 0),
  default_time_limit_seconds integer not null default 5 check (default_time_limit_seconds > 0),
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (game_id, name)
);

create table public.questions (
  id uuid primary key default gen_random_uuid(),
  image_storage_path text not null,
  primary_answer text not null,
  difficulty smallint not null default 1 check (difficulty between 1 and 5),
  language text not null default 'en',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.question_answers (
  id uuid primary key default gen_random_uuid(),
  question_id uuid not null references public.questions(id) on delete cascade,
  answer text not null,
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  unique (question_id, answer)
);

create unique index question_answers_one_primary_idx
  on public.question_answers(question_id)
  where is_primary;

create table public.level_questions (
  level_id uuid not null references public.levels(id) on delete cascade,
  question_id uuid not null references public.questions(id) on delete restrict,
  order_index integer not null check (order_index >= 0),
  created_at timestamptz not null default now(),
  primary key (level_id, question_id),
  unique (level_id, order_index)
);

create table public.admin_users (
  id uuid primary key default gen_random_uuid(),
  supabase_user_id uuid not null unique references auth.users(id) on delete cascade,
  role text not null default 'content_admin'
    check (role in ('super_admin', 'content_admin')),
  approved boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.player_progress (
  id uuid primary key default gen_random_uuid(),
  supabase_user_id uuid not null references auth.users(id) on delete cascade,
  level_id uuid not null references public.levels(id) on delete cascade,
  question_index integer not null default 0 check (question_index >= 0),
  score integer not null default 0 check (score >= 0),
  correct_count integer not null default 0 check (correct_count >= 0),
  last_played_at timestamptz not null default now(),
  synced_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index player_progress_user_level_idx
  on public.player_progress(supabase_user_id, level_id);

create table public.media_records (
  id uuid primary key default gen_random_uuid(),
  storage_path text not null unique,
  original_filename text not null,
  mime_type text not null,
  size_bytes bigint not null check (size_bytes > 0),
  width integer check (width is null or width > 0),
  height integer check (height is null or height > 0),
  status text not null default 'ready' check (status in ('ready', 'archived')),
  created_at timestamptz not null default now()
);

create or replace function public.set_question_answers(
  target_question_id uuid,
  new_primary_answer text,
  answer_aliases text[]
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  update public.questions
  set primary_answer = new_primary_answer
  where id = target_question_id;

  if not found then
    raise exception 'Question not found';
  end if;

  delete from public.question_answers
  where question_id = target_question_id;

  insert into public.question_answers (question_id, answer, is_primary)
  values (target_question_id, new_primary_answer, true);

  insert into public.question_answers (question_id, answer, is_primary)
  select target_question_id, alias, false
  from (
    select distinct unnest(coalesce(answer_aliases, '{}'::text[])) as alias
  ) aliases
  where alias <> new_primary_answer;
end;
$$;

create or replace function public.set_level_questions(
  target_level_id uuid,
  question_ids uuid[]
)
returns void
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if cardinality(coalesce(question_ids, '{}'::uuid[])) <> (
    select count(distinct selected.id)
    from unnest(coalesce(question_ids, '{}'::uuid[])) as selected(id)
  ) then
    raise exception 'Question IDs must not repeat';
  end if;

  delete from public.level_questions where level_id = target_level_id;

  insert into public.level_questions (level_id, question_id, order_index)
  select target_level_id, selected.id, selected.ordinality - 1
  from unnest(coalesce(question_ids, '{}'::uuid[])) with ordinality as selected(id, ordinality);
end;
$$;

revoke all on function public.set_question_answers(uuid, text, text[])
  from public, anon, authenticated;
grant execute on function public.set_question_answers(uuid, text, text[])
  to service_role;
revoke all on function public.set_level_questions(uuid, uuid[])
  from public, anon, authenticated;
grant execute on function public.set_level_questions(uuid, uuid[])
  to service_role;

create index levels_game_published_idx on public.levels(game_id, is_published);
create index level_questions_order_idx on public.level_questions(level_id, order_index);
create index question_answers_question_idx on public.question_answers(question_id);
create index player_progress_user_updated_idx on public.player_progress(supabase_user_id, updated_at desc);

create trigger games_set_updated_at before update on public.games
  for each row execute function public.set_updated_at();
create trigger levels_set_updated_at before update on public.levels
  for each row execute function public.set_updated_at();
create trigger questions_set_updated_at before update on public.questions
  for each row execute function public.set_updated_at();
create trigger admin_users_set_updated_at before update on public.admin_users
  for each row execute function public.set_updated_at();
create trigger player_progress_set_updated_at before update on public.player_progress
  for each row execute function public.set_updated_at();

alter table public.games enable row level security;
alter table public.levels enable row level security;
alter table public.questions enable row level security;
alter table public.question_answers enable row level security;
alter table public.level_questions enable row level security;
alter table public.admin_users enable row level security;
alter table public.player_progress enable row level security;
alter table public.media_records enable row level security;

create or replace function public.is_published_level(target_level_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
set row_security = off
as $$
  select exists (
    select 1
    from public.levels l
    join public.games g on g.id = l.game_id
    where l.id = target_level_id
      and l.is_published
      and g.is_published
  );
$$;

create or replace function public.is_published_question(target_question_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
set row_security = off
as $$
  select exists (
    select 1
    from public.questions q
    join public.level_questions lq on lq.question_id = q.id
    join public.levels l on l.id = lq.level_id
    join public.games g on g.id = l.game_id
    where q.id = target_question_id
      and q.is_active
      and l.is_published
      and g.is_published
  );
$$;

create policy "published games are readable"
  on public.games for select to anon, authenticated
  using (is_published);

create policy "published levels are readable"
  on public.levels for select to anon, authenticated
  using (public.is_published_level(id));

create policy "questions in published levels are readable"
  on public.questions for select to anon, authenticated
  using (public.is_published_question(id));

create policy "answers in published levels are readable"
  on public.question_answers for select to anon, authenticated
  using (public.is_published_question(question_id));

create policy "published level questions are readable"
  on public.level_questions for select to anon, authenticated
  using (
    public.is_published_level(level_id)
    and public.is_published_question(question_id)
  );

create policy "players read own progress"
  on public.player_progress for select to authenticated
  using (auth.uid() = supabase_user_id);

create policy "players create own progress"
  on public.player_progress for insert to authenticated
  with check (auth.uid() = supabase_user_id);

create policy "players update own progress"
  on public.player_progress for update to authenticated
  using (auth.uid() = supabase_user_id)
  with check (auth.uid() = supabase_user_id);
