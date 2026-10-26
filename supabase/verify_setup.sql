-- Read-only checks. Run in Supabase SQL Editor after setup/upgrade.
select name as required_table, to_regclass('public.' || name) is not null as present
from unnest(array['game_categories', 'games', 'levels', 'questions', 'question_answers',
  'level_questions', 'admin_users', 'player_progress', 'media_records']) t(name);

select name as required_function, to_regprocedure(name) is not null as present
from unnest(array['public.update_question_content(uuid,jsonb)',
  'public.create_question_content(jsonb)', 'public.set_level_questions(uuid,uuid[])',
  'public.save_player_progress(jsonb)', 'public.assert_published_content()',
  'public.create_standalone_game(jsonb)', 'public.update_standalone_game(uuid,jsonb)',
  'public.set_game_questions(uuid,uuid[])', 'public.is_published_game(uuid)']) t(name);

select id, public, file_size_limit, allowed_mime_types
from storage.buckets where id = 'content-images';
-- Expect: public=false, file_size_limit=5242880, image/webp,image/jpeg,image/png.

select policyname, permissive, roles, cmd, qual, with_check
from pg_policies where schemaname = 'storage' and tablename = 'objects'
  and policyname = 'content images require backend';
-- Expect one RESTRICTIVE policy for anon/authenticated, cmd=ALL.

select column_name from information_schema.columns
where table_schema = 'public' and table_name = 'player_progress'
  and column_name in ('completed', 'content_fingerprint');

-- Raises an actionable error if existing published content is incomplete.
-- Fix through Studio: unpublish the game, upload/select active image questions,
-- save the exact configured question count, then publish the game and its category.
select public.assert_published_content();

select indexname from pg_indexes where schemaname = 'public' and indexname = 'levels_one_session_per_game';
select id, name, is_published, gameplay_kind from public.game_categories;

select column_name from information_schema.columns where table_schema = 'public'
  and table_name = 'games' and column_name = 'prompt_key';

select table_name, column_name from information_schema.columns where table_schema = 'public'
  and table_name in ('games', 'game_categories') and column_name = 'icon_storage_path';
-- Expect both catalogue tables after migration 009.

select table_name, column_name from information_schema.columns where table_schema = 'public'
  and table_name in ('games', 'game_categories') and column_name = 'icon_key';
-- Expect both catalogue tables after migration 010.
