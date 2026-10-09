-- Sign in to Studio once with sahzadahmad246@gmail.com, then run this SQL.
-- Run only in Supabase SQL Editor as the project owner.
do $$
declare
  admin_email text := 'sahzadahmad246@gmail.com';
  user_id uuid;
begin
  select id into user_id from auth.users where lower(email) = lower(admin_email);
  if user_id is null then
    raise exception 'Account not found. Sign in to Studio with this Google account first.';
  end if;
  insert into public.admin_users (supabase_user_id, role, approved)
  values (user_id, 'super_admin', true)
  on conflict (supabase_user_id) do update set role = 'super_admin', approved = true;
end $$;
