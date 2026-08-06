-- مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — عملة كل معاملة على حدة، مش عملة
-- السوق المختار في التطبيق وقت العرض. مستخدم مسافر أو معاه تحويل بعملة تانية كان بيتسجل
-- بعملة السوق افتراضياً، فالتقرير الشهري كان بيجمع أرقام من عملات مختلفة كأنها نفس العملة.
--
-- nullable — null يعني "مش معروفة" (كل الصفوف القديمة، ورسايل بنكية من غير رمز عملة
-- صريح في النص). العميل (CurrencyFormatter.format(context, tx)) بيرجع لعملة الـ Market
-- الحالي وقت العرض في الحالة دي، زي السلوك القديم بالظبط — مفيش تغيير بأثر رجعي.

alter table public.zad_transactions
  add column if not exists currency text;

comment on column public.zad_transactions.currency is
  'رمز عملة العملية نفسها (SAR/EGP/TRY) — من نص رسالة البنك (SaBankParser.extractCurrency) أو من بلد قاعدة bank_rules.json. null = غير معروفة، الواجهة بترجع لعملة الـ Market الحالي.';
