-- Run only against an empty disposable PostgreSQL database, as superuser.
-- Minimal Supabase auth fixtures; every change is rolled back on completion.
begin;
set local statement_timeout = '30s';
do $$ begin
  if to_regclass('public.games') is not null or exists (
    select 1 from pg_namespace where nspname = 'auth'
  ) then
    raise exception 'Refusing database checks: use an empty disposable database';
  end if;
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then
    create role service_role nologin bypassrls;
  end if;
end $$;

create schema auth;
create table auth.users (id uuid primary key);
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
grant usage on schema auth, public to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to service_role;

\ir ../migrations/202610090001_initial_schema.sql
\ir ../migrations/202610090002_existing_project_api.sql
\ir ../migrations/202610090003_atomic_question_updates.sql
-- Compatibility and update migrations must be safe to reapply.
\ir ../migrations/202610090002_existing_project_api.sql
\ir ../migrations/202610090003_atomic_question_updates.sql

create function public.test_assert(ok boolean, message text)
returns void language plpgsql as $$ begin
  if ok is distinct from true then raise exception 'Check failed: %', message; end if;
end $$;

insert into auth.users values
  ('10000000-0000-4000-8000-000000000001'),
  ('10000000-0000-4000-8000-000000000002');
insert into public.games (id, name, slug, is_published) values
  ('20000000-0000-4000-8000-000000000001', 'Published', 'published', true),
  ('20000000-0000-4000-8000-000000000002', 'Draft', 'draft', false);
insert into public.levels (id, game_id, name, is_published) values
  ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Published', true),
  ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', 'Draft', false),
  ('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002', 'Hidden parent', true);
insert into public.questions (id, image_storage_path, primary_answer, is_active) values
  ('40000000-0000-4000-8000-000000000001', 'bike.webp', 'bike', true),
  ('40000000-0000-4000-8000-000000000002', 'draft.webp', 'draft', true),
  ('40000000-0000-4000-8000-000000000003', 'hidden.webp', 'hidden', true),
  ('40000000-0000-4000-8000-000000000004', 'inactive.webp', 'inactive', false);
insert into public.level_questions (level_id, question_id, order_index) values
  ('30000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', 0),
  ('30000000-0000-4000-8000-000000000002', '40000000-0000-4000-8000-000000000002', 0),
  ('30000000-0000-4000-8000-000000000003', '40000000-0000-4000-8000-000000000003', 0),
  ('30000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000004', 1);
insert into public.question_answers (question_id, answer, is_primary)
  select id, primary_answer, true from public.questions;
insert into public.question_answers (question_id, answer)
  values ('40000000-0000-4000-8000-000000000001', 'road bike');
insert into public.player_progress (supabase_user_id, level_id, score) values
  ('10000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', 1),
  ('10000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000001', 9);

set local role anon;
select public.test_assert((select count(*) = 1 from public.games), 'guest games exclude drafts');
select public.test_assert((select count(*) = 1 from public.levels), 'guest levels exclude draft parents');
select public.test_assert((select count(*) = 1 from public.questions), 'guest questions exclude drafts and inactive content');
select public.test_assert((select count(*) = 2 from public.question_answers), 'guest answers exclude draft and inactive content');
select public.test_assert((select count(*) = 1 from public.level_questions), 'guest assignments exclude hidden content');
select public.test_assert(not has_function_privilege(current_user, 'public.update_question_content(uuid,jsonb)', 'execute'), 'guest cannot update questions');
select public.test_assert(not has_function_privilege(current_user, 'public.set_question_answers(uuid,text,text[])', 'execute'), 'guest cannot replace answers');
select public.test_assert(not has_function_privilege(current_user, 'public.set_level_questions(uuid,uuid[])', 'execute'), 'guest cannot assign questions');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000001', true);
select public.test_assert((select count(*) = 1 from public.player_progress), 'player sees only own progress');
select public.test_assert((select score = 1 from public.player_progress), 'player cannot read another score');
select public.test_assert((select count(*) = 1 from public.games), 'signed-in players cannot read drafts');
select public.test_assert(not has_function_privilege(current_user, 'public.update_question_content(uuid,jsonb)', 'execute'), 'signed-in players cannot update questions');
update public.player_progress set score = 2;
do $$ begin
  begin
    insert into public.player_progress (supabase_user_id, level_id)
    values ('10000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000002');
    raise exception 'Player could forge progress ownership';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.player_progress set supabase_user_id = '10000000-0000-4000-8000-000000000002';
    raise exception 'Player could transfer progress ownership';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
select public.test_assert((select score = 9 from public.player_progress where supabase_user_id = '10000000-0000-4000-8000-000000000002'), 'other player progress unchanged');

-- Inject a failure after question metadata has been updated, while replacing
-- aliases, to prove that the entire RPC rolls back rather than partially saves.
create function public.test_reject_alias() returns trigger language plpgsql as $$ begin
  if new.answer = 'reject-me' then raise exception 'Injected answer failure' using errcode = '23514'; end if;
  return new;
end $$;
create trigger test_reject_alias before insert on public.question_answers
for each row execute function public.test_reject_alias();

set local role service_role;
select public.update_question_content('40000000-0000-4000-8000-000000000001', '{"primary_answer":"bicycle"}');
select public.test_assert(exists (select 1 from public.question_answers where answer = 'road bike'), 'primary answer change preserves aliases');
select public.test_assert(not exists (select 1 from public.question_answers where answer = 'bike'), 'old primary answer removed');
select public.update_question_content('40000000-0000-4000-8000-000000000001', '{"accepted_answers":[]}');
select public.test_assert((select count(*) = 1 from public.question_answers where question_id = '40000000-0000-4000-8000-000000000001'), 'explicit empty aliases clears aliases');
do $$ begin
  begin
    perform public.update_question_content('40000000-0000-4000-8000-000000000001', '{"image_storage_path":"changed.webp","primary_answer":"changed","accepted_answers":null}');
    raise exception 'Malformed aliases unexpectedly accepted';
  exception when invalid_parameter_value then null;
  end;
end $$;
select public.test_assert((select image_storage_path = 'bike.webp' and primary_answer = 'bicycle' from public.questions where id = '40000000-0000-4000-8000-000000000001'), 'failed question update rolls back metadata');
do $$ begin
  begin
    perform public.update_question_content('40000000-0000-4000-8000-000000000001', '{"image_storage_path":"changed.webp","primary_answer":"changed","accepted_answers":["reject-me"]}');
    raise exception 'Injected answer failure was ignored';
  exception when check_violation then null;
  end;
  begin
    perform public.update_question_content('40000000-0000-4000-8000-000000000099', '{}');
    raise exception 'Missing question was accepted';
  exception when no_data_found then null;
  end;
end $$;
select public.test_assert((select image_storage_path = 'bike.webp' and primary_answer = 'bicycle' from public.questions where id = '40000000-0000-4000-8000-000000000001'), 'answer failure rolls back metadata writes');
select public.test_assert((select count(*) = 1 and min(answer) = 'bicycle' from public.question_answers where question_id = '40000000-0000-4000-8000-000000000001'), 'answer failure restores previous answers');
do $$ begin
  begin
    perform public.set_level_questions('30000000-0000-4000-8000-000000000001', array['40000000-0000-4000-8000-000000000001'::uuid, '40000000-0000-4000-8000-000000000001'::uuid]);
    raise exception 'Duplicate question IDs unexpectedly accepted';
  exception when raise_exception then
    if sqlerrm <> 'Question IDs must not repeat' then raise; end if;
  end;
end $$;
select public.test_assert((select count(*) = 2 from public.level_questions where level_id = '30000000-0000-4000-8000-000000000001'), 'failed assignment preserves question order');
do $$ begin
  begin
    perform public.set_level_questions('30000000-0000-4000-8000-000000000001', array['40000000-0000-4000-8000-000000000099'::uuid]);
    raise exception 'Missing question assignment was accepted';
  exception when foreign_key_violation then null;
  end;
end $$;
select public.test_assert((select count(*) = 2 from public.level_questions where level_id = '30000000-0000-4000-8000-000000000001'), 'assignment failure rolls back deletion of previous questions');
reset role;

-- Prepare the earlier publication-isolation fixtures for readiness enforcement.
delete from public.level_questions where question_id = '40000000-0000-4000-8000-000000000004';
update public.levels set question_count = 1 where is_published;
insert into public.media_records (storage_path, original_filename, mime_type, size_bytes)
select image_storage_path, image_storage_path, 'image/webp', 100 from public.questions;
\ir ../migrations/202610090004_content_readiness.sql
\ir ../migrations/202610090005_progress_sync.sql
\ir ../migrations/202610090004_content_readiness.sql
\ir ../migrations/202610090005_progress_sync.sql

set local role service_role;
select public.assert_published_content();
set constraints all immediate;
set constraints all deferred;
do $$ begin
  begin
    update public.levels set is_published = true where id = '30000000-0000-4000-8000-000000000002';
    set constraints all immediate;
    raise exception 'Incomplete level was published';
  exception when sqlstate 'YP001' then null;
  end;
  begin
    update public.questions set is_active = false where id = '40000000-0000-4000-8000-000000000001';
    set constraints all immediate;
    raise exception 'Published level was broken by deactivation';
  exception when sqlstate 'YP001' then null;
  end;
  begin
    update public.levels set is_published = false where id = '30000000-0000-4000-8000-000000000001';
    set constraints all immediate;
    raise exception 'Published game lost its last level';
  exception when sqlstate 'YP001' then null;
  end;
  begin
    perform public.create_question_content('{"image_storage_path":"bike.webp","primary_answer":"new bike","difficulty":1,"accepted_answers":["reject-me"]}');
    raise exception 'Create with injected answer failure succeeded';
  exception when check_violation then null;
  end;
end $$;
select public.test_assert((select count(*) = 4 from public.questions), 'failed creation rolls back the new question');
select public.update_question_content('40000000-0000-4000-8000-000000000001', '{"accepted_answers":["cycle"]}');
set constraints all immediate;
set constraints all deferred;
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-4000-8000-000000000001', true);
select public.save_player_progress(jsonb_build_object(
  'levelId', '30000000-0000-4000-8000-000000000001', 'questionIndex', 1,
  'score', 1, 'correctCount', 1, 'completed', true, 'contentFingerprint', 'ordered-question-ids', 'lastPlayedAt', now() + interval '1 second'
));
select public.save_player_progress(jsonb_build_object(
  'levelId', '30000000-0000-4000-8000-000000000001', 'questionIndex', 0,
  'score', 0, 'correctCount', 0, 'lastPlayedAt', now() - interval '1 day'
));
select public.test_assert((select score = 1 and completed from public.player_progress), 'stale offline progress cannot overwrite a newer save');
select public.test_assert((select content_fingerprint = 'ordered-question-ids' from public.player_progress), 'progress content fingerprint survives a stale sync');
do $$ begin
  begin
    perform public.save_player_progress('{"levelId":"30000000-0000-4000-8000-000000000002","questionIndex":0,"score":0,"correctCount":0}');
    raise exception 'Progress saved against a draft level';
  exception when no_data_found then null;
  end;
  begin
    perform public.save_player_progress('{"levelId":"30000000-0000-4000-8000-000000000001","questionIndex":999,"score":999,"correctCount":999}');
    raise exception 'Out-of-bounds progress accepted';
  exception when sqlstate 'YP001' then null;
  end;
end $$;
reset role;

-- Exercise Storage setup against a minimal schema with an overly permissive
-- pre-existing policy, proving the restrictive bucket policy wins.
create schema storage;
create table storage.buckets (id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects (id uuid default gen_random_uuid(), bucket_id text, name text);
alter table storage.objects enable row level security;
grant usage on schema storage to anon, authenticated;
grant select, insert, update, delete on storage.objects to anon, authenticated;
create policy legacy_open_storage on storage.objects for all to anon, authenticated using (true) with check (true);
\ir ../migrations/202610090006_private_content_storage.sql
\ir ../migrations/202610090006_private_content_storage.sql
insert into storage.objects (bucket_id, name) values ('content-images', 'draft.webp'), ('other-bucket', 'unrelated.webp');
set local role anon;
select public.test_assert((select count(*) = 1 from storage.objects), 'private bucket blocks legacy anonymous read policies');
do $$ begin
  begin
    insert into storage.objects (bucket_id, name) values ('content-images', 'unauthorized.webp');
    raise exception 'Anonymous storage upload succeeded';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
select public.test_assert((select not public from storage.buckets where id = 'content-images'), 'content bucket is private');

-- Upgrade existing content; rerun verifies idempotence and preserved IDs.
insert into public.games (name, slug) values ('Empty legacy game', 'empty-legacy-game');
\ir ../migrations/202610130007_game_categories.sql
\ir ../migrations/202610130007_game_categories.sql
select public.test_assert((select count(*) = 4 from public.games), 'each legacy level becomes a standalone game');
select public.test_assert(not exists(select game_id from public.levels group by game_id having count(*) <> 1), 'one private session per game');
select public.test_assert((select count(*) = 1 from public.levels l join public.games g on g.id=l.game_id where g.slug='empty-legacy-game'), 'migration gives empty legacy games a question configuration');
select public.test_assert(exists(select 1 from public.player_progress where level_id = '30000000-0000-4000-8000-000000000001'), 'migration keeps saved progress IDs');
update public.game_categories set is_published = false where slug = 'guess';
set local role anon;
select public.test_assert((select count(*) = 0 from public.games), 'unpublished categories hide published games');
select public.test_assert((select count(*) = 0 from public.levels), 'unpublished categories hide playable sessions');
select public.test_assert((select count(*) = 0 from public.questions), 'unpublished categories hide questions');
select public.test_assert((select count(*) = 0 from public.question_answers), 'unpublished categories hide answer aliases');
reset role;
update public.game_categories set is_published = true where slug = 'guess';
set local role service_role;
select public.test_assert((public.create_standalone_game(jsonb_build_object('name','Foods','slug','foods','difficulty',1,'question_count',5))->>'name') = 'Foods', 'create a standalone game without a level step');
select public.test_assert((select l.question_count = 5 from public.levels l join public.games g on g.id = l.game_id where g.slug = 'foods'), 'game owns its question configuration');
do $$ begin
  begin
    perform public.update_standalone_game((select id from public.games where slug='foods'), '{"is_published":true}');
    set constraints all immediate;
    raise exception 'Incomplete game published';
  exception when sqlstate 'YP001' then null;
  end;
end $$;
set constraints all deferred;
select public.set_game_questions((select id from public.games where slug='foods'), array['40000000-0000-4000-8000-000000000001']::uuid[]);
select public.update_standalone_game((select id from public.games where slug='foods'), '{"question_count":1,"is_published":true}');
set constraints all immediate;
select public.test_assert((select g.is_published and l.is_published from public.games g join public.levels l on l.game_id=g.id where g.slug='foods'), 'ready game publishes atomically without a level step');
select public.update_standalone_game((select id from public.games where slug='foods'), '{"is_published":false}');
select public.test_assert((select not g.is_published and not l.is_published from public.games g join public.levels l on l.game_id=g.id where g.slug='foods'), 'unpublishing hides the game and its private session together');
set constraints all deferred;
reset role;

-- Prompts and safe removal/replacement of published game questions.
\ir ../migrations/202610130008_game_prompts_and_draft_edits.sql
\ir ../migrations/202610130008_game_prompts_and_draft_edits.sql
set local role service_role;
select public.update_standalone_game((select id from public.games where slug='foods'), '{"prompt_key":"name_food","is_published":true}');
set constraints all immediate;
set constraints all deferred;
select public.set_game_questions((select id from public.games where slug='foods'), array['40000000-0000-4000-8000-000000000001']::uuid[]);
select public.test_assert((select is_published from public.games where slug='foods'), 'unchanged selection does not unpublish a game');
do $$ begin
  begin
    perform public.set_game_questions((select id from public.games where slug='foods'), array['99999999-0000-4000-8000-000000000001']::uuid[]);
    raise exception 'Unknown image assignment succeeded';
  exception when foreign_key_violation then null;
  end;
end $$;
select public.test_assert((select is_published from public.games where slug='foods'), 'failed replacement rolls back publication and assignments');
select public.set_game_questions((select id from public.games where slug='foods'), '{}'::uuid[]);
set constraints all immediate;
select public.test_assert((select not g.is_published and not l.is_published from public.games g join public.levels l on l.game_id=g.id where g.slug='foods'), 'removing the last image safely makes the game a draft');
select public.test_assert((select count(*)=0 from public.level_questions lq join public.levels l on l.id=lq.level_id join public.games g on g.id=l.game_id where g.slug='foods'), 'last image is actually removed');
select public.set_game_questions((select id from public.games where slug='foods'), array['40000000-0000-4000-8000-000000000001','40000000-0000-4000-8000-000000000002']::uuid[]);
select public.test_assert((select question_count=2 from public.levels l join public.games g on g.id=l.game_id where g.slug='foods'), 'saved question count follows the selected images');
select public.test_assert((select prompt_key='name_food' from public.games where slug='foods'), 'selected game prompt persists');
do $$ begin
  begin
    perform public.update_standalone_game((select id from public.games where slug='foods'), '{"prompt_key":"unsupported"}');
    raise exception 'Unsupported prompt accepted';
  exception when check_violation then null;
  end;
end $$;
set constraints all deferred;
reset role;

-- Deleting a game cascades through levels, assignments and progress, but keeps
-- the reusable question/answer library intact, including shared questions.
set local role service_role;
delete from public.games where id = '20000000-0000-4000-8000-000000000001';
set constraints all immediate;
select public.test_assert(not exists(select 1 from public.levels where game_id = '20000000-0000-4000-8000-000000000001'), 'game deletion removes its levels');
select public.test_assert(not exists(select 1 from public.level_questions where level_id = '30000000-0000-4000-8000-000000000001'), 'game deletion removes its assignments');
select public.test_assert(not exists(select 1 from public.player_progress where level_id = '30000000-0000-4000-8000-000000000001'), 'game deletion removes saved level progress');
select public.test_assert(exists(select 1 from public.questions where id = '40000000-0000-4000-8000-000000000001'), 'game deletion preserves reusable questions');
select public.test_assert(exists(select 1 from public.question_answers where question_id = '40000000-0000-4000-8000-000000000001'), 'game deletion preserves answers');
select public.test_assert(exists(select 1 from public.games where id = '20000000-0000-4000-8000-000000000002'), 'game deletion preserves other games');
reset role;

rollback;
\echo 'Migration, publication RLS, progress ownership and atomic update checks passed; changes rolled back.'
