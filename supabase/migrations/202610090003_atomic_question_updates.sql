-- Update question metadata and its answers in one transaction. A missing
-- accepted_answers key preserves aliases; an explicit [] removes aliases.
create or replace function public.update_question_content(
  target_question_id uuid,
  changes jsonb
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  question public.questions%rowtype;
  aliases text[];
begin
  select * into question from public.questions
  where id = target_question_id for update;
  if not found then
    raise exception 'Question not found' using errcode = 'P0002';
  end if;

  if changes ? 'accepted_answers' then
    select coalesce(array_agg(value), '{}'::text[]) into aliases
    from jsonb_array_elements_text(changes->'accepted_answers');
  else
    select coalesce(array_agg(answer), '{}'::text[]) into aliases
    from public.question_answers
    where question_id = target_question_id and not is_primary;
  end if;

  update public.questions set
    image_storage_path = case when changes ? 'image_storage_path' then changes->>'image_storage_path' else image_storage_path end,
    primary_answer = case when changes ? 'primary_answer' then changes->>'primary_answer' else primary_answer end,
    difficulty = case when changes ? 'difficulty' then (changes->>'difficulty')::smallint else difficulty end,
    language = case when changes ? 'language' then changes->>'language' else language end,
    is_active = case when changes ? 'is_active' then (changes->>'is_active')::boolean else is_active end
  where id = target_question_id
  returning * into question;

  if changes ? 'primary_answer' or changes ? 'accepted_answers' then
    perform public.set_question_answers(target_question_id, question.primary_answer, aliases);
  end if;

  return to_jsonb(question) || jsonb_build_object('accepted_answers', (
    select coalesce(jsonb_agg(answer order by is_primary desc, answer), '[]'::jsonb)
    from public.question_answers where question_id = target_question_id
  ));
end;
$$;

revoke all on function public.update_question_content(uuid, jsonb)
  from public, anon, authenticated;
grant execute on function public.update_question_content(uuid, jsonb)
  to service_role;
