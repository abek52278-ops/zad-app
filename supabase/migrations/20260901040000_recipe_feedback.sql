-- تقوية شيف زاد: العميل يقدر يقول "عجبتني"/"معجبتنيش" على وصفة، والاقتراح الجاي
-- يستفيد من ده. الوصفات مالهاش id ثابت (بتتولّد كل مرة من الموديل)، فالمفتاح الطبيعي
-- هو recipe_name — unique(user_id, recipe_name) يخلي رأيه في نفس الاسم يتحدّث بدل
-- ما يتكرر (لو غيّر رأيه بعدين).
create table if not exists public.zad_recipe_feedback (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  recipe_name text not null,
  liked boolean not null,
  created_at timestamptz not null default now(),
  unique (user_id, recipe_name)
);

create index if not exists idx_zad_recipe_feedback_user on public.zad_recipe_feedback(user_id, created_at desc);

alter table public.zad_recipe_feedback enable row level security;

create policy "user_own_recipe_feedback" on public.zad_recipe_feedback
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
