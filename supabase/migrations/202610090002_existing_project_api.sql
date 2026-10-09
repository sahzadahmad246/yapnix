create extension if not exists pgcrypto;

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

  delete from public.question_answers where question_id = target_question_id;
  insert into public.question_answers (question_id, answer, is_primary)
  values (target_question_id, new_primary_answer, true);

  insert into public.question_answers (question_id, answer, is_primary)
  select target_question_id, aliases.answer, false
  from (
    select distinct unnest(coalesce(answer_aliases, '{}'::text[])) as answer
  ) aliases
  where aliases.answer <> new_primary_answer;
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

create unique index if not exists question_answers_one_primary_idx
  on public.question_answers(question_id)
  where is_primary;
create unique index if not exists player_progress_user_level_idx
  on public.player_progress(supabase_user_id, level_id);

alter table public.games enable row level security;
alter table public.levels enable row level security;
alter table public.questions enable row level security;
alter table public.question_answers enable row level security;
alter table public.level_questions enable row level security;
alter table public.admin_users enable row level security;
alter table public.player_progress enable row level security;
alter table public.media_records enable row level security;

grant select on public.games, public.levels, public.questions,
  public.question_answers, public.level_questions to anon, authenticated;
grant select, insert, update on public.player_progress to authenticated;

drop policy if exists "published games are readable" on public.games;
create policy "published games are readable"
  on public.games for select to anon, authenticated
  using (is_published);

drop policy if exists "published levels are readable" on public.levels;
create policy "published levels are readable"
  on public.levels for select to anon, authenticated
  using (public.is_published_level(id));

drop policy if exists "questions in published levels are readable" on public.questions;
create policy "questions in published levels are readable"
  on public.questions for select to anon, authenticated
  using (public.is_published_question(id));

drop policy if exists "answers in published levels are readable" on public.question_answers;
create policy "answers in published levels are readable"
  on public.question_answers for select to anon, authenticated
  using (public.is_published_question(question_id));

drop policy if exists "published level questions are readable" on public.level_questions;
create policy "published level questions are readable"
  on public.level_questions for select to anon, authenticated
  using (
    public.is_published_level(level_id)
    and public.is_published_question(question_id)
  );

drop policy if exists "players read own progress" on public.player_progress;
create policy "players read own progress"
  on public.player_progress for select to authenticated
  using (auth.uid() = supabase_user_id);

drop policy if exists "players create own progress" on public.player_progress;
create policy "players create own progress"
  on public.player_progress for insert to authenticated
  with check (auth.uid() = supabase_user_id);

drop policy if exists "players update own progress" on public.player_progress;
create policy "players update own progress"
  on public.player_progress for update to authenticated
  using (auth.uid() = supabase_user_id)
  with check (auth.uid() = supabase_user_id);
