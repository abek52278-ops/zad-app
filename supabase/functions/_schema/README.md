# _schema

`schema.json` هي لقطة `table -> [column names]` من الداتابيز الحي، و
`schema_contract_test.ts` بيقارن كل `.from("x").select("a,b")` / `.eq()` /
`.insert()` / `.update()` / `.upsert()` / `onConflict` في `supabase/functions/**`
بيها. أي عمود أو جدول مش موجود يفشّل البناء — بدل ما يرجع 42703/42P01 من
PostgREST ويتبلع بصمت في الإنتاج (زي ما حصل فعليًا 3 مرات قبل ما الاختبار ده
يتوصّل بالـCI، شوف تعليق أعلى `schema_contract_test.ts`).

## تحديث اللقطة بعد أي ميجريشن بتغيّر أعمدة

من أي مكان فيه اتصال بمشروع Supabase (Claude Code مع MCP، أو `supabase` CLI
مع `SUPABASE_ACCESS_TOKEN`):

1. اسحب الأنواع الحية (مثلاً عبر `mcp__supabase__generate_typescript_types`
   أو `supabase gen types typescript --project-id <ref>`).
2. لكل جدول تحت `public.Tables`, استخرج مفاتيح `Row` بس (مش `Insert`/`Update`)
   واكتبها في `schema.json` بنفس الشكل: `{"table_name": ["col_a", "col_b", ...]}`،
   مرتبين أبجديًا، زي الملف الحالي.
3. حدّث `_meta.generated_at` (ودي مهمة: قيمة قديمة بتوهم إن اللقطة أحدث مما هي).
4. شغّل `deno test --allow-all` من نفس الفولدر ده — لازم يعدي قبل الكوميت.

**متنساش** أي جدول جديد بيتضاف عبر ميجريشن لازم يتضاف هنا في **نفس الكوميت**
— مش بعده. لو الاختبار فشل بعد ميجريشن جديدة، ده الاختبار شغال صح، مش باج فيه.

## ملفات مستثناة (`IGNORED_FILES`)

كود مستحيل ينفّذ (مش متوصّل بأي `import`) بيقدر يتحط في `IGNORED_FILES` جوه
الاختبار عشان مايفشلش على استعلامات كسيرة مالهاش أي أثر فعلي — لكن أي ملف
اتوصّل يوم من الأيام لازم يتشال من القايمة دي فورًا.
