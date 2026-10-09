alter table public.player_progress add column if not exists completed boolean not null default false;
alter table public.player_progress add column if not exists content_fingerprint text not null default '';

create or replace function public.save_player_progress(entry jsonb)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  owner_id uuid := auth.uid();
  target_level uuid := (entry->>'levelId')::uuid;
  level_count integer;
  saved public.player_progress%rowtype;
begin
  if owner_id is null then raise exception 'Sign in to sync progress' using errcode = '42501'; end if;
  select question_count into level_count from public.levels where id = target_level and public.is_published_level(id);
  if not found then raise exception 'Published level not found' using errcode = 'P0002'; end if;
  if (entry->>'questionIndex')::integer > level_count or (entry->>'correctCount')::integer > level_count
    or (entry->>'score')::integer > level_count then
    raise exception 'Progress exceeds the number of questions in this level' using errcode = 'YP001';
  end if;
  insert into public.player_progress (supabase_user_id, level_id, question_index, score, correct_count, completed, content_fingerprint, last_played_at, synced_at)
  values (owner_id, target_level, (entry->>'questionIndex')::integer, (entry->>'score')::integer,
    (entry->>'correctCount')::integer, coalesce((entry->>'completed')::boolean, false), coalesce(entry->>'contentFingerprint', ''),
    coalesce((entry->>'lastPlayedAt')::timestamptz, now()), now())
  on conflict (supabase_user_id, level_id) do update set
    question_index = excluded.question_index, score = excluded.score, correct_count = excluded.correct_count,
    completed = excluded.completed, content_fingerprint = excluded.content_fingerprint, last_played_at = excluded.last_played_at, synced_at = now()
  where excluded.last_played_at >= public.player_progress.last_played_at
  returning * into saved;
  if not found then
    select * into saved from public.player_progress where supabase_user_id = owner_id and level_id = target_level;
  end if;
  return to_jsonb(saved) - 'id' - 'supabase_user_id';
end $$;
revoke all on function public.save_player_progress(jsonb) from public, anon;
grant execute on function public.save_player_progress(jsonb) to authenticated;
