-- توسيع تقوية زاد: الوالدين (role=admin) يقدروا يشوفوا أدوية العيلة كلها — مش يعدّلوها،
-- الدوا شخصي وحساس على عكس المخزون العام (Task 30). قرار المنتج (2026-09-01): رؤية بس.
--
-- نفس نمط family_admin_read_child_budget بالظبط (20260726000000)، بس أوسع: أي admin يشوف
-- أي فرد في نفس العيلة (مش الأطفال بس) — الأب والأم لازم يشوفوا بعض كمان، مش بس الأولاد.
-- Policy إضافية جنب user_own_pharmacy_items الموجودة، RLS بتعمل OR بينهم — القراءة اتوسعت،
-- الكتابة/الحذف لسه مقصورة على صاحب الصف بس (الـpolicy القديمة FOR ALL هي اللي بتحكمهم).
create policy "family_admin_read_pharmacy" on public.zad_pharmacy_items for select
  using (
    exists (
      select 1
        from public.family_members fm_admin
        join public.family_members fm_target on fm_target.family_id = fm_admin.family_id
       where fm_admin.user_id = auth.uid()
         and fm_admin.role = 'admin'
         and fm_target.user_id = zad_pharmacy_items.user_id
    )
  );
