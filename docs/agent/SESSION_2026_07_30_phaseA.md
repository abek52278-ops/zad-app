# جلسة 2026-07-30 — إقفال Epic 2 + بداية PRODUCT_PLAN Phase A (تاسكات 25-28)

> **الجلسة دي لسه مفتوحة.** ده ملف الحالة الجاري لـ Phase A — اقرأه ده قبل أي شغل
> تاني على تاسكات 25-28. `SESSION_2026_07_26_epic19.md` بقى أرشيف (Epic 1+4 خلص من
> جلسات سابقة). Epic 2 (`EPIC_2_ai_screen.md`) اتقفل بالكامل النهارده — تفاصيله هناك،
> مش هنا.

## ⚠️ أهم حاجة تعرفها الجلسة الجاية

**التعديلات كلها اتعملت وبعدين اتطلبت push — تم فعلاً (كوميت آخره في اللوج تحت).**
بس Supabase (migrations + Edge Functions) مختلفة تماماً عن git: السيشن دي **مفيش
عندها Supabase MCP auth ولا CLI access خالص** طول الوقت. يعني:

- كل الـ migrations اللي اتكتبت النهارده (schema جديد، شامل Task 25's `salary_cycle`
  و Task 26's `zad_obligations`) **مش مطبّقة على قاعدة البيانات الحية** — لازم المستخدم
  يعمل `supabase db push` أو يطبّقها يدوي من الداشبورد. المستخدم قال هيطبقها بنفسه بعد
  Task 26 — لو جلسة جاية لقت `zad_obligations`/`cycle_start_day` موجودين فعلاً في
  `list_tables`، ده معناه اتطبقوا، حدّث الملاحظة دي بدل ما تفترض.
- `zad-brain` فيه تعديلات جوهرية (Task 25: دورة الراتب، Task 26: الالتزامات/committed/
  available) **مش منشورة** — لازم redeploy (نفس ملاحظة الـ migrations فوق). **Task 27
  مفيهاش أي migration أو تعديل zad-brain خالص** (كلاينت-سايد بالكامل + قراءة
  `zad_brain_runs` الموجود أصلاً) — متضيفهاش لقايمة اللي محتاجة نشر.
- `zad-core-intelligence` **كان اتنشر** أثناء الجلسة (المستخدم أكّد بنفسه بعد ما
  طلبنا) — ده شامل تصحيح prompts الإيجار/الاشتراكات وأكشن nearby_pois الجديد.
  بس أي تعديل زاد-core-intelligence بعد كده (مفيش لسه) لازم إعادة نشر تانية.

**قاعدة عملية**: أي migration/Edge Function جديدة في الجلسة الجاية، لازم نص واضح
في تقرير التاسك يقول "مش مطبّقة/منشورة، محتاجة كذا من المستخدم" — زي ما اتعمل
النهارده. متقولش "خلص" على حاجة سيرفر-سايد من غير تأكيد نشر حقيقي.

## اللي خلص النهارده (بالترتيب)

1. **Task 29.1 build اتأكد فعلياً** — الـ container ده معندوش Android SDK أصلاً
   (مختلف عن مشكلة الذاكرة بتاعت جلسات فاتت)؛ اتنصّب SDK محلي، `compileDebugKotlin`/
   `assembleDebug`/`testDebugUnitTest` نجحوا. (كوميت `0fd57af`)
2. **`detectSubscriptions()` auto-write fix** — بقى `pendingSubscriptions` + كارت
   تأكيد/رفض بدل كتابة صامتة. (كوميت `24874fc`)
3. **منطق الإيجار/الاشتراكات** — `ZadCentralBrain.isFixedObligation()` +
   3 prompts في zad-core-intelligence اتصلحوا عشان ميقترحوش إلغاء إيجار/قسط.
   (كوميت `0678d3b`)
4. **وضوح القائمة الجانبية** — "الاشتراكات والأقساط" بدل "اشتراكات". (كوميت `50fc73b`)
5. **تنبيهات قرب السوبرماركت/الصيدلية (opt-in geofencing)** — `GroceryGeofenceManager`
   + `GeofenceBroadcastReceiver` + `GeofenceRefreshWorker`، استعلام لحظي وقت الدخول،
   استبعاد تصنيف "غالي/رخيص" (مفيش مصدر بيانات أسعار). (كوميت `35cff38`)
6. **LocationIQ + صيدليات** — أكشن `nearby_pois` سيرفر-سايد (المفتاح سر سيرفر، مش
   في الكلاينت — `AmazonAssociateTag` كان استثناء لأنه عمومي، LocationIQ مختلف).
   geofencing بقى بيغطي صيدليات كمان. (كوميت `3ef5849`، المستخدم أكّد نشر
   zad-core-intelligence بعد كده)
7. **توحيد 5 شاشات دخل/مصروف** — `BudgetMath.totalIncome/totalExpense` بدل كل
   شاشة تعمل فلترة لوحدها، وبقوا يستخدموا `txnKind` مش `isExpense`. (كوميت `468da96`)
8. **Epic 2 اتقفل بالكامل** — قرار HomeScreen (الكروت تفضل تتحدث عند الفتح، قرار
   مستخدم صريح، موثّق كاستثناء في `CLAUDE.md`). (كوميت `edbb848`)
9. **Task 25 — دورة الراتب بدل الشهر التقويمي** (`PRODUCT_PLAN.md` Phase A):
   migration (`cycle_start_day`/`cycle_anchor`، nullable)، `CycleMath.kt` +
   `BudgetMath` cycle-aware functions، zad-brain buildSnapshot بقى يحسب بحدود
   الدورة، اكتشاف + تأكيد عن طريق `confirm_cycle_start` tool (نفس نمط Task 19.5).
   نصّبت `deno` في الـ container عشان أتأكد فعليًا (`deno check` + `deno test`،
   34/34). (كوميت `cdd62d7`، تفاصيل كاملة في `PROGRESS.md`)

**Deliberately deferred من Task 25**: دقة `zad_brain_self_review()` التاريخية
(مفيش طريقة صح تعيد تفسير تحذيرات قديمة بأثر رجعي على دورة متكانتش موجودة وقتها)،
`last_working_day` سيرفر-سايد مش واعي بالسوق. UI الكلاينت اللي كان بيعرض أرقام الشهر
التقويمي بدل الدورة — ده اتقفل في Task 26 تحت.

## Task 26 — الالتزامات الثابتة ورقم "متاح" (2026-07-30, كوميت لاحق لـ 3e35693)

جدول `zad_obligations` (migration `20260730130000`) + zad-brain (`committed`/`available`
في buildSnapshot، اكتشاف تلقائي + `confirm_obligation` tool، 42/42 deno tests) + كلاينت
(`BudgetMath.committedInCycle/availableInCycle`، `ZadViewModel` بقى يحسب على الدورة مش
الشهر التقويمي — ده كان دين Task 25 المؤجل — `ZadCardHero`/`BudgetScreen` بيعرضوا "متاح"
كرقم أساسي). تفاصيل كاملة في `PROGRESS.md`. **مهم**: المستخدم قال هيطبق الـ migration
وهيعمل redeploy لـ zad-brain بنفسه الجلسة دي — لو لقيت `zad_obligations` موجودة فعلاً
في `list_tables`، متفترضش إن ده لسه معلق زي باقي الملاحظات في الملف ده، تأكد أول.

## Task 27 — الثقة المرئية (`≈`) + "ليه الرقم اتغيّر؟" (2026-07-30, staged scope)

بحث أول (Explore agent) لقى الدوك بيفترض بنية مش موجودة (مفيش timestamp تسوية كاش خالص،
الكلاينت مبيقراش `zad_consumption.rate_known`/`sample_count`، `ZadIngest` لسه Task 12
مش مبنية). اتقال للمستخدم قبل البناء — **قرار صريح: staged scope** — بُني 27.1(a)
`is_verified` + 27.1(b) تصحيح صيدلية + 27.2 شيت "آخر التغييرات" بس بالبيانات الموجودة
فعلاً؛ 27.1(c) ثقة عينات المخزون و27.1(d) عمر تسوية الكاش اتأجلوا (محتاجين schema
جديد). قرار تاني: طول الضغط بيفضل "حذف" في صف المعاملة في `TransactionsScreen` زي ما هو
— شيت "ليه اتغيّر" بس على أرقام كارت الميزانية، مفيش تعارض هناك. تفاصيل كاملة في
`PROGRESS.md`، شامل اكتشاف إن `is_verified` كان ميت فعليًا (بس `SubscriptionAutoDeductWorker`
بيحطه true) وإزاي اتصلح بمعنى حقيقي (تأكيد يدوي فعلي بس، مش صوت/كاميرا/چيب).

## اللي فاضل — PRODUCT_PLAN.md Phase A (تاسك 28 + follow-up من Task 27)

- **Task 28 — رفض بمعنى**: استبدال "تجاهل" بـ٣ خيارات (مش مهم/الرقم غلط/عرفت خلاص)،
  كل واحد بيسجل `zad_memory` بالسبب.
- **مؤجل من Task 27**: 27.1(c) (محتاج قراءة `zad_consumption` من الكلاينت)، 27.1(d)
  (محتاج عمود `reconciled_at` جديد — مفيش أصلاً)، ومثال "رسالة من CIB" الحرفي في 27.2
  (محتاج تخزين نص SMS خام على `ZadTransaction` — قرار خصوصية اتسيب عمدًا مش مقرر لوحدي).

**ملاحظة ترتيب من الدوك نفسه**: "Task 28 moved ahead of Task 27 deliberately" —
الدوك بيفضّل 28 قبل 27 منطقيًا (قناة "الرقم غلط" أهم من عرض الثقة اللي بيعتمد
عليها). المستخدم طلب صراحة "دورة الراتب، حساب وتثبيت 'المتاح'... مع شاشة الثقة
وزر 'الرقم غلط'" بترتيب 25→26→27→28 الرقمي — لو الجلسة الجاية عايزة تراجع
الترتيب، اسأل الأول، الاتنين قرارات صريحة من طرفين مختلفين.

## حقائق تشغيلية للجلسة الجاية

- **الـ container ده مفيش فيه Android SDK افتراضيًا** — لازم تتأكد/تنصّبه الأول
  قبل أي build (انظر `.claude/skills/run-zad-app/SKILL.md`، أو كرر خطوات التنصيب
  اللي في `PROGRESS.md` تحت Task 29.1).
- **مفيش `deno` افتراضيًا برضو** — لو محتاج تتأكد من كود zad-brain/zad-core-intelligence،
  نصّبه: `curl -fsSL https://deno.land/install.sh | sh`.
- **مفيش Supabase MCP/CLI auth** — أي migration أو نشر Edge Function لازم تتكتب
  وتتطلب من المستخدم يطبقها بنفسه، مش تتفترض إنها حصلت.
- Git: push شغال فعليًا لما يتطلب صراحة (مش تلقائي كل كوميت).
