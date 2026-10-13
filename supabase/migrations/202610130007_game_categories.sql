-- Category -> standalone game -> ordered questions. Legacy levels are retained
-- as a private one-to-one session record so progress IDs remain stable.
set constraints all immediate;
set constraints all deferred;
create table if not exists public.game_categories (
  id uuid primary key default gen_random_uuid(),
  name text not null check (btrim(name) <> ''),
  slug text not null unique,
  description text not null default '',
  gameplay_kind text not null default 'image_guess' check (gameplay_kind in ('image_guess')),
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
insert into public.game_categories (name, slug, description, is_published)
values ('Guess', 'guess', 'Look at a picture and say what it is. Animals, food, objects and more.', true)
on conflict (slug) do nothing;
alter table public.games add column if not exists category_id uuid references public.game_categories(id) on delete restrict;
update public.games set category_id = (select id from public.game_categories where slug = 'guess') where category_id is null;
set constraints all immediate;
set constraints all deferred;
alter table public.games alter column category_id set not null;

-- Preserve every assignment and progress record. Extra old levels become games.
do $$ declare original public.games%rowtype; session public.levels%rowtype; first_session boolean; new_game uuid; begin
  for original in select * from public.games order by created_at, id loop
    first_session := true;
    for session in select * from public.levels where game_id = original.id order by is_published desc, created_at, id loop
      if first_session then first_session := false;
      else
        insert into public.games (name, slug, description, difficulty, is_published, category_id)
        values (original.name || ' — ' || session.name, 'migrated-' || session.id::text, coalesce(session.description, original.description), original.difficulty,
          original.is_published and session.is_published, original.category_id) returning id into new_game;
        update public.levels set game_id = new_game where id = session.id;
      end if;
    end loop;
    if first_session then
      insert into public.levels (game_id, name) values (original.id, original.name);
    end if;
  end loop;
end $$;
set constraints all immediate;
set constraints all deferred;
create unique index if not exists levels_one_session_per_game on public.levels(game_id);
create index if not exists games_category_idx on public.games(category_id);

alter table public.game_categories enable row level security;
grant select on public.game_categories to anon, authenticated;
grant all on public.game_categories to service_role;
drop policy if exists public_published_categories on public.game_categories;
create policy public_published_categories on public.game_categories for select to anon, authenticated using (is_published);
drop trigger if exists categories_updated_at on public.game_categories;
create trigger categories_updated_at before update on public.game_categories for each row execute function public.set_updated_at();

create or replace function public.is_published_game(target_game_id uuid)
returns boolean language sql stable security definer set search_path = '' set row_security = off as $$
  select exists(select 1 from public.games g join public.game_categories c on c.id = g.category_id
    where g.id = target_game_id and g.is_published and c.is_published and c.gameplay_kind = 'image_guess');
$$;
revoke all on function public.is_published_game(uuid) from public;
grant execute on function public.is_published_game(uuid) to anon, authenticated, service_role;
-- A restrictive policy also applies to any older permissive read policies.
drop policy if exists published_category_boundary on public.games;
create policy published_category_boundary on public.games as restrictive for select to anon, authenticated
using (public.is_published_game(id));
create or replace function public.is_published_level(target_level_id uuid)
returns boolean language sql stable security definer set search_path = '' set row_security = off as $$
  select exists(select 1 from public.levels l where l.id = target_level_id and l.is_published and public.is_published_game(l.game_id));
$$;
create or replace function public.is_published_question(target_question_id uuid)
returns boolean language sql stable security definer set search_path = '' set row_security = off as $$
  select exists(select 1 from public.questions q join public.level_questions lq on lq.question_id = q.id
    where q.id = target_question_id and q.is_active and public.is_published_level(lq.level_id));
$$;

create or replace function public.create_standalone_game(content jsonb)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare created public.games%rowtype; begin
  insert into public.games (name, slug, description, difficulty, category_id, is_published)
  values (content->>'name', content->>'slug', content->>'description', (content->>'difficulty')::smallint,
    coalesce((content->>'category_id')::uuid, (select id from public.game_categories where slug = 'guess')), false)
  returning * into created;
  insert into public.levels (game_id, name, question_count, default_time_limit_seconds)
  values (created.id, created.name, coalesce((content->>'question_count')::integer, 5), coalesce((content->>'default_time_limit_seconds')::integer, 5));
  return to_jsonb(created);
end $$;
create or replace function public.update_standalone_game(target_game_id uuid, changes jsonb)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare saved public.games%rowtype; begin
  perform pg_catalog.pg_advisory_xact_lock(74821, 1);
  update public.games set
    name = case when changes ? 'name' then changes->>'name' else name end,
    slug = case when changes ? 'slug' then changes->>'slug' else slug end,
    description = case when changes ? 'description' then changes->>'description' else description end,
    difficulty = case when changes ? 'difficulty' then (changes->>'difficulty')::smallint else difficulty end,
    category_id = case when changes ? 'category_id' then (changes->>'category_id')::uuid else category_id end,
    is_published = case when changes ? 'is_published' then (changes->>'is_published')::boolean else is_published end
  where id = target_game_id returning * into saved;
  if not found then raise exception 'Game not found' using errcode = 'P0002'; end if;
  update public.levels set name = saved.name, is_published = saved.is_published,
    question_count = case when changes ? 'question_count' then (changes->>'question_count')::integer else question_count end,
    default_time_limit_seconds = case when changes ? 'default_time_limit_seconds' then (changes->>'default_time_limit_seconds')::integer else default_time_limit_seconds end
  where game_id = target_game_id;
  return to_jsonb(saved);
end $$;
create or replace function public.set_game_questions(target_game_id uuid, question_ids uuid[])
returns void language plpgsql security invoker set search_path = '' as $$
declare session_id uuid; begin
  perform pg_catalog.pg_advisory_xact_lock(74821, 1);
  select id into session_id from public.levels where game_id = target_game_id for update;
  if not found then raise exception 'Game not found' using errcode = 'P0002'; end if;
  perform public.set_level_questions(session_id, question_ids);
end $$;
revoke all on function public.create_standalone_game(jsonb), public.update_standalone_game(uuid,jsonb), public.set_game_questions(uuid,uuid[]) from public, anon, authenticated;
grant execute on function public.create_standalone_game(jsonb), public.update_standalone_game(uuid,jsonb), public.set_game_questions(uuid,uuid[]) to service_role;
