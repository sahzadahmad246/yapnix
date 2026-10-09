-- Read-only checks. Run in Supabase SQL Editor after setup/upgrade.
select name as required_table, to_regclass('public.' || name) is not null as present
from unnest(array['games', 'levels', 'questions', 'question_answers',
  'level_questions', 'admin_users', 'player_progress', 'media_records']) t(name);

select name as required_function, to_regprocedure(name) is not null as present
from unnest(array['public.update_question_content(uuid,jsonb)',
  'public.create_question_content(jsonb)', 'public.set_level_questions(uuid,uuid[])',
  'public.save_player_progress(jsonb)', 'public.assert_published_content()']) t(name);

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
-- Fix through Studio: unpublish a game before its last level, then upload/select
-- images, activate and assign the exact question count, publish level then game.
select public.assert_published_content();
