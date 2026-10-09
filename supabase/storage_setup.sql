-- Generated compatibility shortcut for migration 006.
-- Run in Supabase SQL Editor as postgres; safe to reapply.
begin;
-- Supabase Storage is provided by the hosted/local Supabase project.
-- Keep this exact bucket private; approved Studio uploads use the backend.
do $$ begin
  if to_regclass('storage.buckets') is null or to_regclass('storage.objects') is null then
    raise exception 'Supabase Storage schema is missing. Run this on a Supabase project.';
  end if;
end $$;
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('content-images', 'content-images', false, 5242880, array['image/webp', 'image/jpeg', 'image/png'])
on conflict (id) do update set public = false,
  file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- Restrictive policies also block older permissive policies for this bucket.
-- Studio uploads/reads through the backend service role; gameplay gets signed URLs.
drop policy if exists "content images require backend" on storage.objects;
create policy "content images require backend" on storage.objects as restrictive
for all to anon, authenticated
using (bucket_id <> 'content-images')
with check (bucket_id <> 'content-images');

notify pgrst, 'reload schema';
commit;
