-- Duplicate medicines and duplicate shopping rows, cleaned up and then made impossible.
--
-- How they got there. Neither writer looked before inserting, and both sit behind a retry:
--
--   * Pharmacy. On 2026-08-15 one account held six rows for two medicines — اجمانتين and
--     سبروفار, three times each, inserted at 01:24:24, 01:24:50, 01:27:01 and 01:30:23.
--     Those are the same minutes the Telegram bot was replying "تعذر تنفيذ الطلب": the
--     agent turn failed *after* its write landed, the customer retried, and the medicine
--     was inserted again. Duplicate medicine rows are not cosmetic — each one schedules its
--     own dose alarm, which is why the notification shade showed "موعد الدواء" for
--     اجمانتين twice.
--   * Shopping. مياه إيلانو and بيض were each present twice, one pair written 09:12 and the
--     other 17:02 — the restock path running a second time with nothing checking first.
--
-- The tool handlers in zad-brain now merge instead of insert. These indexes are the half
-- that does not rely on every future writer remembering to check. Both app-side inserts
-- (SupabaseRepo.addPharmacyItem / addShoppingItem) already wrap their call in try/catch and
-- log, so a rejected duplicate degrades to "the row that already existed stays", never to a
-- crash.

-- ── Repair existing rows ───────────────────────────────────────────────────────

-- Pharmacy: keep one row per (owner, medicine). Preference order is deliberate — a row
-- carrying a dosage knows more than one that doesn't, and among equals the first one wins
-- so the customer keeps the entry they originally created. zad_dose_log and
-- zad_pharmacy_doses cascade from here; the duplicates being removed have no logs of their
-- own that the surviving row doesn't also have.
with ranked as (
  select id,
         row_number() over (
           partition by user_id, lower(trim(name)), coalesce(family_member_id::text, '')
           order by (dosage is null), (dose_times is null), created_at
         ) as rn
    from public.zad_pharmacy_items
)
delete from public.zad_pharmacy_items p
 using ranked r
 where p.id = r.id and r.rn > 1;

-- Shopping: fold the duplicates' quantities into the surviving row rather than discarding
-- them — "بيض ×2 و بيض ×1" meant the customer wanted three, and silently keeping two would
-- be a different wrong answer.
with ranked as (
  select id, user_id, lower(trim(item_name)) as key, quantity,
         row_number() over (
           partition by user_id, lower(trim(item_name))
           order by created_at
         ) as rn
    from public.zad_shopping_list
   where is_purchased = false
), totals as (
  select user_id, key, sum(coalesce(quantity, 1)) as total
    from ranked group by user_id, key
)
update public.zad_shopping_list s
   set quantity = t.total
  from ranked r
  join totals t on t.user_id = r.user_id and t.key = r.key
 where s.id = r.id and r.rn = 1;

with ranked as (
  select id,
         row_number() over (
           partition by user_id, lower(trim(item_name))
           order by created_at
         ) as rn
    from public.zad_shopping_list
   where is_purchased = false
)
delete from public.zad_shopping_list s
 using ranked r
 where s.id = r.id and r.rn > 1;

-- ── Make it impossible ─────────────────────────────────────────────────────────

-- family_member_id is nullable and NULLs never collide in a unique index, so it is
-- coalesced to '' — otherwise "the household's own paracetamol" could still be inserted
-- any number of times. Two family members legitimately taking the same medicine stay two
-- distinct rows, which is the behaviour we want to keep.
create unique index if not exists zad_pharmacy_items_unique_per_owner
  on public.zad_pharmacy_items (user_id, lower(trim(name)), coalesce(family_member_id::text, ''));

-- Partial on is_purchased = false: buying an item and needing it again next month must
-- still work. Only the *open* list is constrained to one row per item.
create unique index if not exists zad_shopping_list_unique_open_item
  on public.zad_shopping_list (user_id, lower(trim(item_name)))
  where is_purchased = false;
