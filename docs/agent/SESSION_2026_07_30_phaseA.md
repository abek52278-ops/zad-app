# جلسة 2026-07-30 — إقفال Epic 2 + تاسكات 25-28 (Phase A جزئي + Phase C) + بداية B4

> **الجلسة دي لسه مفتوحة.** ده ملف الحالة الجاري لتاسكات 25-28 — اقرأه ده قبل أي شغل
> تاني عليهم أو على Phase A/C. `SESSION_2026_07_26_epic19.md` بقى أرشيف (Epic 1+4 خلص
> من جلسات سابقة). Epic 2 (`EPIC_2_ai_screen.md`) اتقفل بالكامل النهارده — تفاصيله
> هناك، مش هنا.
>
> **تصحيح مهم (اتكتشف وقت كتابة توثيق إقفال Phase A)**: العنوان الأصلي للملف ده كان
> بيقول "Phase A" لتاسكات 25-28 الأربعة — ده غلط. حسب `PRODUCT_PLAN.md` §3 نفسه:
> Task 25 = A2، Task 26 = A3 (فعلاً **Phase A**)، لكن Task 27 = C2+C3، Task 28 = C1
> (فعلاً **Phase C**). الأربع تاسكات خلصت (كود)، بس دي مش "جلسة Phase A واحدة" —
> اتنين من كل فيز. وأهم من كده: ~~**Phase A مش مقفولة** — عندها A6 (شيل `RECEIVE_SMS`)~~ *(اتقفلت 2026-08-01)*
> لسه مفتوحة، انظر القسم تحت.

## ⚠️ أهم حاجة تعرفها الجلسة الجاية

**التعديلات كلها اتعملت وبعدين اتطلبت push — تم فعلاً (كوميت آخره في اللوج تحت).**
بس Supabase (migrations + Edge Functions) مختلفة تماماً عن git: السيشن دي **مفيش
عندها Supabase MCP auth ولا CLI access خالص** طول الوقت. يعني:

- كل الـ migrations اللي اتكتبت النهارده (schema جديد، شامل Task 25's `salary_cycle`،
  Task 26's `zad_obligations`، Task 28's `dismiss_reason`، و**B4's `telegram_bindings`**)
  **مش مطبّقة على قاعدة البيانات الحية** — لازم المستخدم يعمل `supabase db push` أو
  يطبّقها يدوي من الداشبورد. المستخدم قال هيطبقها بنفسه — لو جلسة جاية لقت
  `zad_obligations`/`cycle_start_day`/`dismiss_reason`/`telegram_bindings` موجودين
  فعلاً في `list_tables`، ده معناه اتطبقوا، حدّث الملاحظة دي بدل ما تفترض.
- `zad-brain` فيه تعديلات جوهرية (Task 25: دورة الراتب، Task 26: الالتزامات/committed/
  available، Task 28: dismissed_keys بتستبعد timing + dismissal_reasons) **مش منشورة**
  — لازم redeploy (نفس ملاحظة الـ migrations فوق). **Task 27 مفيهاش أي migration أو
  تعديل zad-brain خالص** (كلاينت-سايد بالكامل + قراءة `zad_brain_runs` الموجود أصلاً)
  — متضيفهاش لقايمة اللي محتاجة نشر.
- **`zad-telegram-bot` دالة جديدة بالكامل، لسه متعملهاش deploy خالص** — والأهم:
  `TELEGRAM_WEBHOOK_SECRET` لسه مش متسجل (بس `TELEGRAM_BOT_TOKEN` اتسجل الجلسة دي)،
  يعني حتى بعد الـ deploy الدالة هتقبل أي حد يبعتلها POST من غير أي تحقق. خطوات كاملة
  في `PROGRESS.md` (قسم Phase B4) — الجلسة دي معندهاش وصول Supabase CLI/MCP، فمتعرفش
  تعمل deploy ولا تنادي `setWebhook` بنفسها.
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
10. **Task 26 — الالتزامات الثابتة ورقم "متاح"** (Phase A، A3): جدول
    `zad_obligations`، `committed`/`available` في zad-brain + كلاينت، اكتشاف تلقائي
    + `confirm_obligation` tool، `ZadCardHero`/`BudgetScreen` عرضوا "متاح" لأول مرة.
    deno 42/42، Android 118/118. (كوميت `32d2236`)
11. **Task 27 (staged) — الثقة المرئية (`≈`) + "ليه الرقم اتغيّر؟"** (Phase C،
    C2+C3): `Figure` type، `is_verified` بقى له معنى حقيقي (تأكيد يدوي بس)،
    `WhyChangedSheet` بيقرا `zad_brain_runs.mutations` لأول مرة من الكلاينت، تصحيح
    فجوة `PharmacyItemGridCard`. 27.1(c)/(d) اتأجلوا (schema جديد محتاج). Android
    122/122. (كوميت `6aa1b42`)
12. **Task 28 — رفض بمعنى** (Phase C، C1): استبدال زرار الرفض بـ٣ خيارات، عمود
    `dismiss_reason`، `DismissalMemory.noteFor()` (مختبرة)، `DismissReasonMenu`.
    Android 127/127. (كوميت `947b731`)
13. **تصحيح توثيق Phase A/C** — اتكشف إن تاسكات 27/28 كانت متسمّاة "Phase A" غلط
    (فعليًا Phase C)، وإن Phase A نفسها كانت **مش مقفولة** وقتها (A6 — شيل `RECEIVE_SMS` — خلصت 2026-08-01، كانت لسه
    مفتوحة، اتفحص فعليًا في الكود). `CLAUDE.md`/`PROGRESS.md`/ملف الجلسة ده اتصححوا.
    (كوميت `9a584c5`)
14. **Phase B4 — بنية تحتية بوت تليجرام** (read-only v1، grammY): جدول
    `telegram_bindings`، دالة `zad-telegram-bot` (كود ربط لمرة واحدة، ٣ أزرار: رصيد/
    معاملات/تنبيهات + رفض Task 28)، `TelegramLinkDialog` في `ProfileScreen`. deno
    17/17. **`TELEGRAM_WEBHOOK_SECRET` لسه مش متسجل — فجوة أمان حقيقية موثّقة.**
    (كوميت `6f52c23`)
15. **UI الميزانية — glassmorphism** (مش تاسك مرقّم، طلب مستخدم مباشر): `ZadCardHero`
    و`BudgetScreen` بقوا يستخدموا `HeroGradientCard`/`GlassCard` الموجودين فعلاً في
    الكود، حذف `BudgetCardSection` الميت. **لقيت وصلحت bug حقيقي فعليًا عن طريق
    screenshot** (`GlassCard` كان بيمسح محتواه بصريًا بالـ blur على API 31+ — بيأثر
    على ٤ شاشات تانية). Android 128/128. (كوميت `f2296db`)

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

## Task 28 — رفض بمعنى (2026-07-30)

استبدال زرار الرفض الوحيد بـ٣ خيارات (مش مهم=not_relevant/الرقم غلط=wrong_data/عرفت
خلاص=timing). migration جديدة `20260730140000_informative_dismissal.sql`
(`zad_insights.dismiss_reason`). zad-brain: `dismissed_keys` بتستبعد `timing` عمدًا
(نفس dedupe_key يرجع pending تلقائي أول ما يتكتب تاني — مش block دائم)، `dismissal_reasons`
جديدة في الـ snapshot. كلاينت: `DismissalMemory.noteFor()` (pure، مختبرة) +
`SupabaseRepo.dismissInsightWithReason()` (بينادي `zad_memory_upsert` مباشرة، مش عن
طريق العقل) + `DismissReasonMenu` (DropdownMenu) متوصلة في `HomeScreen` و
`NotificationCenterScreen`. `ZadQuestionCard`'s dismiss (رفض سؤال، مش رؤية) اتسيب عمدًا
زي ما هو — تفاصيل كاملة في `PROGRESS.md`. deno 42/42 (زي ما هي)، Android 127/127 (5 جداد).

**مؤجل من Task 27** (لسه فاضل): 27.1(c) (محتاج قراءة `zad_consumption` من الكلاينت)،
27.1(d) (محتاج عمود `reconciled_at` جديد — مفيش أصلاً)، ومثال "رسالة من CIB" الحرفي في
27.2 (محتاج تخزين نص SMS خام على `ZadTransaction` — قرار خصوصية اتسيب عمدًا مش مقرر لوحدي).

## حالة Phase A فعليًا (PRODUCT_PLAN.md §3) — مش مقفولة

| # | التاسك | الحالة |
|---|---|---|
| A1 | بق سحب الصراف/المحافظ | ✅ خلص (Task 19.1/19.2، جلسة سابقة) |
| A2 | دورة الراتب بدل الشهر التقويمي | ✅ خلص (Task 25، النهارده) |
| A3 | الالتزامات الثابتة/"متاح" | ✅ خلص (Task 26، النهارده) |
| A4 | جرعات الصيدلية (وحدات الجرعة) | ✅ خلص (Task 17.2، جلسة سابقة) |
| A5 | قواعد بنوك مصرية | ✅ خلص (Task 21، جلسة سابقة) |
| A6 | شيل `RECEIVE_SMS`، الاعتماد على `NotificationListenerService` بس | ✅ خلصت (2026-08-01) |

**A6 خلصت على مرحلتين — والمرحلة التانية هي اللي أقفلتها فعلاً.** كوميت
`757f41c` شال الصلاحية من `AndroidManifest.xml` وحوّل `UnifiedSmsReceiver` لـ
`UnifiedBankListener` (قراءة من إشعار تطبيق الرسايل بدل الـ SMS نفسه). بس فضل
كود حي بينده صلاحية اتشالت:

- `BankReadingStatus.isSmsPermissionGranted()` بيعمل `checkSelfPermission` على
  `RECEIVE_SMS` — صلاحية مش معلَنة يعني النتيجة `DENIED` دايماً.
- `BankReadingStatusScreen` كان فيه صف "قراءة الرسايل" بيفضل OFF على طول مع زرار
  "تفعيل" بيطلب `RECEIVE_SMS` — طلب صلاحية غير معلَنة النظام بيرفضه فوراً من غير
  ما يعرض أي dialog. يعني زرار مسدود بيقول للمستخدم إن قراءة البنك مكسورة.

الاتنين اتشالوا في 2026-08-01، ومعاهم `sms_reading_status_label` من الأربع لغات،
وعنوان الشاشة بقى "قراءة إشعارات البنك" لأنها بقت إشعارات + بطارية بس.

**التحقق النهائي على المستوى اللي Play بيفحصه**: الـ merged manifest
(`:app:processDebugMainManifest`) — يعني بعد دمج مانيفستات كل المكتبات — فيه 14
صلاحية ومفيهوش ولا واحدة SMS. الباقي في الكود مجرد كومنتات بتوثّق القرار.

## حالة Phase C فعليًا (PRODUCT_PLAN.md §3 — C1/C2/C3)

| # | التاسك | الحالة |
|---|---|---|
| C1 | رفض بمعنى ("الرقم غلط") | ✅ خلص (Task 28، النهارده) |
| C2 | أرقام ظاهر ثقتها (`≈`) | 🟡 staged — 27.1(a)+(b) خلصوا، 27.1(c)+(d) مؤجلين (schema جديد محتاج) |
| C3 | "ليه الرقم اتغيّر؟" | ✅ خلص (Task 27.2، النهارده — نسخة مبسطة، انظر تفاصيل Task 27) |

Phase C اتقفلت فعليًا (C1 كامل، C3 كامل، C2 معظمه) — الباقي في C2 موثّق كمؤجل
مش سهو، انظر تفاصيل Task 27 فوق.

## Phase B4 — بوت تليجرام (2026-07-30، بدأ النهارده)

Phase B (`PRODUCT_PLAN.md` §3): B1 (habit chips)/B2 (كارت الكاش)/B3 (تسوية كاش
أسبوعية) خلصوا من جلسات سابقة (Task 22/19.4/19.5). B4 (بوت تليجرام) كان "after
Phase C" — Phase C اتقفلت النهارده فبقى مش محظور. مفيش سبيك تفصيلي لـ B4 في
`PRODUCT_PLAN.md` (سطر واحد بس: "text and buttons only, no voice") — السبيك الحقيقي
الوحيد كان في `EPIC_1_4.md`: اسم الدالة `zad-telegram-bot`، كود ربط لمرة واحدة،
أزرار inline، وتحذير صريح "a chat_id is never an identity". اتقال للمستخدم قبل
البناء، قرار صريح: **read-only v1** (رصيد/آخر معاملات/تنبيهات معلقة + رفض Task 28)،
البنية التحتية دلوقتي، التوكن بعدين. المستخدم بعت التوكن وسجله كـ Supabase secret
أثناء الشغل، وصحّح قرارين: **grammY** بدل تنفيذ يدوي، والجدول اسمه **`telegram_bindings`**
(مش بادئة `zad_` زي كل جدول تاني في الـ schema — اتوثقت المخالفة في تعليق الـ migration).

Migration `20260730150000_telegram_bindings.sql` + دالة `zad-telegram-bot` (grammY،
`telegram.ts` منطق pure مختبر بالكامل بدون Supabase/شبكة، `index.ts` webhook thin
layer) + كلاينت (`SupabaseRepo.generateTelegramBindingCode/isTelegramLinked/
unlinkTelegram` + `TelegramLinkDialog` في `ProfileScreen`). deno 17/17 (كل شيء pure،
مفيش استدعاء تليجرام/Supabase حقيقي اتعمل أو أمكن اختباره). Android 127/127 (زي ما
هي). تفاصيل كاملة + خطوات الـ deploy/setWebhook المطلوبة من المستخدم في `PROGRESS.md`
تحت "Phase B4".

**فجوة أمان حقيقية موثّقة**: `TELEGRAM_WEBHOOK_SECRET` لسه مش متسجل، يعني حتى بعد
الـ deploy الدالة هتقبل أي POST بدون تحقق لحد ما يتسجل ويتعمل `setWebhook` بيه.

**مؤجل عمدًا من B4**: رقم "الرصيد المتبقي" في البوت شهر تقويمي بسيط، مش "متاح"
الحقيقي (دورة راتب + التزامات، `BudgetMath.availableInCycle`) — دمجهم محتاج موديول
مشترك بين الكلاينت (Kotlin) ودالتين Deno منفصلتين، مؤجل عمدًا. مفيش تسجيل مصروفات
من البوت (read-only بس).

## فاضل عمومًا

- **Phase A**: ✅ مقفولة بالكامل (A6 خلصت 2026-08-01، اتأكدت من الـ merged
  manifest مش من ملف المصدر بس).
- **Phase C follow-ups**: 27.1(c) (قراءة `zad_consumption` من الكلاينت)، 27.1(d)
  (عمود `reconciled_at` جديد — مفيش أصلاً)، مثال "رسالة من CIB" الحرفي (نص SMS خام
  على `ZadTransaction` — قرار خصوصية اتسيب عمدًا مش مقرر لوحدي).
- **Phase B4**: `TELEGRAM_WEBHOOK_SECRET` + deploy + `setWebhook` (فوق)، توسيع البوت
  لأكتر من read-only (لو المستخدم طلب)، دمج حساب "الرصيد" مع "متاح" الحقيقي.
- **تطبيق الـ migrations + redeploy zad-brain + deploy zad-telegram-bot** — انظر
  "أهم حاجة" فوق.

**ملاحظة ترتيب من الدوك نفسه**: "Task 28 moved ahead of Task 27 deliberately" —
الدوك بيفضّل 28 قبل 27 منطقيًا (قناة "الرقم غلط" أهم من عرض الثقة اللي بيعتمد
عليها). المستخدم طلب صراحة "دورة الراتب، حساب وتثبيت 'المتاح'... مع شاشة الثقة
وزر 'الرقم غلط'" بترتيب 25→26→27→28 الرقمي — لو الجلسة الجاية عايزة تراجع
الترتيب، اسأل الأول، الاتنين قرارات صريحة من طرفين مختلفين.

## تحديث UI الميزانية — glassmorphism (2026-07-30، مش من PRODUCT_PLAN.md)

طلب مستخدم مباشر (مش تاسك مرقّم في الدوك): مواءمة Hero Card + هيدر `BudgetScreen` مع
"تصميم iOS جديد" — glassmorphism، حواف 24dp، إظهار "متاح". مفيش Figma/screenshot
مرجعي، فالتنفيذ اعتمد على الصفات التلاتة المذكورة + إعادة استخدام نظام الزجاج
الموجود فعلاً في الكود (`GlassCard`/`HeroGradientCard`/`ZadCanvasBackground` في
`PremiumSurfaces.kt`) بدل اختراع نمط جديد. `ZadCardHero` و`BudgetScreen` بقوا
يستخدموا نفس المكونات. **لقيت وصلحت bug حقيقي عن طريق screenshot فعلي** (مش تخمين):
`GlassCard` كان بيعمل blur على نفس الـ Column اللي فيها النص/الأيقونات، يعني أي محتوى
جواه بيتمسح بصريًا على API 31+ — bug موجود من قبل في ٤ شاشات تانية بتستخدم `GlassCard`،
مصلّح دلوقتي في المصدر. اتحذف كمان composable ميت (`BudgetCardSection` في
`HomeScreen.kt`) كان بروتوتايب قديم لنفس الفكرة من غير available/committed. تفاصيل
كاملة في `PROGRESS.md`. Android 128/128 (1 جديد — Roborazzi capture test).
`BudgetScreen`'s header متتأكدش بصريًا (محتاج ViewModel، خارج نطاق الـ driver) —
ثقة عالية بس مش إثبات بصري مباشر، موثّق الفرق صراحة.

## حقائق تشغيلية للجلسة الجاية

- **الـ container ده مفيش فيه Android SDK افتراضيًا** — لازم تتأكد/تنصّبه الأول
  قبل أي build (انظر `.claude/skills/run-zad-app/SKILL.md`، أو كرر خطوات التنصيب
  اللي في `PROGRESS.md` تحت Task 29.1).
- **مفيش `deno` افتراضيًا برضو** — لو محتاج تتأكد من كود zad-brain/zad-core-intelligence،
  نصّبه: `curl -fsSL https://deno.land/install.sh | sh`.
- **مفيش Supabase MCP/CLI auth** — أي migration أو نشر Edge Function لازم تتكتب
  وتتطلب من المستخدم يطبقها بنفسه، مش تتفترض إنها حصلت.
- Git: push شغال فعليًا لما يتطلب صراحة (مش تلقائي كل كوميت).
