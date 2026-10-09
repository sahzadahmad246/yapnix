create or replace function public.create_question_content(content jsonb)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare new_id uuid;
begin
  insert into public.questions (image_storage_path, primary_answer, difficulty, language, is_active)
  values (content->>'image_storage_path', content->>'primary_answer', (content->>'difficulty')::smallint,
    coalesce(content->>'language', 'en'), coalesce((content->>'is_active')::boolean, true))
  returning id into new_id;
  -- The update RPC creates primary and alias answers inside this transaction.
  return public.update_question_content(new_id, jsonb_build_object(
    'primary_answer', content->>'primary_answer',
    'accepted_answers', coalesce(content->'accepted_answers', '[]'::jsonb)
  ));
end $$;
revoke all on function public.create_question_content(jsonb) from public, anon, authenticated;
grant execute on function public.create_question_content(jsonb) to service_role;

-- Deferred checks validate the final transaction state, allowing atomic answer
-- replacement while preventing any API path from breaking published content.
create or replace function public.assert_published_content()
returns void language plpgsql security definer set search_path = '' as $$
declare invalid_level public.levels%rowtype;
begin
  select l.* into invalid_level from public.levels l where l.is_published and (
    (select count(*) from public.level_questions lq where lq.level_id = l.id) <> l.question_count
    or exists (
      select 1 from public.level_questions lq
      join public.questions q on q.id = lq.question_id
      where lq.level_id = l.id and (
        not q.is_active or btrim(q.primary_answer) = '' or btrim(q.language) = ''
        or not exists (select 1 from public.media_records m where m.storage_path = q.image_storage_path and m.status = 'ready')
        or not exists (select 1 from public.question_answers a where a.question_id = q.id and a.is_primary and a.answer = q.primary_answer)
      )
    )
  ) limit 1;
  if found then
    raise exception 'Level "%" needs exactly % active questions, uploaded images and primary answers. Unpublish the level before changing its content.',
      invalid_level.name, invalid_level.question_count using errcode = 'YP001';
  end if;
  if exists (select 1 from public.games g where g.is_published and not exists (
    select 1 from public.levels l where l.game_id = g.id and l.is_published
  )) then
    raise exception 'A published game needs at least one published level. Publish a ready level first, or unpublish its game before removing the last published level.' using errcode = 'YP001';
  end if;
end $$;
revoke all on function public.assert_published_content() from public, anon, authenticated;
grant execute on function public.assert_published_content() to service_role;

-- Content edits are infrequent. A shared transaction lock prevents concurrent
-- publication and question edits from validating against inconsistent states.
create or replace function public.lock_content_edits()
returns trigger language plpgsql set search_path = '' as $$ begin
  perform pg_catalog.pg_advisory_xact_lock(74821, 1);
  return null;
end $$;
revoke all on function public.lock_content_edits() from public, anon, authenticated;

create or replace function public.check_published_content_trigger()
returns trigger language plpgsql security definer set search_path = '' as $$ begin
  perform public.assert_published_content();
  return null;
end $$;
revoke all on function public.check_published_content_trigger() from public, anon, authenticated;

do $$ declare table_name text; begin
  foreach table_name in array array['games', 'levels', 'questions', 'question_answers', 'level_questions', 'media_records'] loop
    execute format('drop trigger if exists serialize_content_edits on public.%I', table_name);
    execute format('create trigger serialize_content_edits before insert or update or delete on public.%I for each statement execute function public.lock_content_edits()', table_name);
    execute format('drop trigger if exists content_readiness on public.%I', table_name);
    execute format('create constraint trigger content_readiness after insert or update or delete on public.%I deferrable initially deferred for each row execute function public.check_published_content_trigger()', table_name);
  end loop;
end $$;

-- Serialize assignment writers and reject unknown levels, including [] updates.
create or replace function public.set_level_questions(target_level_id uuid, question_ids uuid[])
returns void language plpgsql security invoker set search_path = '' as $$ begin
  perform 1 from public.levels where id = target_level_id for update;
  if not found then raise exception 'Level not found' using errcode = 'P0002'; end if;
  if cardinality(coalesce(question_ids, '{}'::uuid[])) <> (
    select count(distinct selected.id) from unnest(coalesce(question_ids, '{}'::uuid[])) as selected(id)
  ) then raise exception 'Question IDs must not repeat' using errcode = 'YP001'; end if;
  delete from public.level_questions where level_id = target_level_id;
  insert into public.level_questions (level_id, question_id, order_index)
  select target_level_id, selected.id, selected.ordinality - 1
  from unnest(coalesce(question_ids, '{}'::uuid[])) with ordinality as selected(id, ordinality);
end $$;
revoke all on function public.set_level_questions(uuid, uuid[]) from public, anon, authenticated;
grant execute on function public.set_level_questions(uuid, uuid[]) to service_role;
