# جلسة 2026-07-26 — Epic 19: سلطة الميزانية الموحدة + wallet/txn_kind

> ملف حالة الجلسة دي بالذات. الصق الرابط ده أو محتواه في أول شات جديد عشان أرجع أعرف
> إحنا وصلنا فين. غير `SESSION_2026_07_26.md` (جلسة سابقة، Phase 0-5، شغل تاني خالص —
> لوحة العيلة والتنبيهات، خلصت قبل الجلسة دي).

## الطلب الأصلي

المستخدم بعت باقة ضخمة من ٨ أفكار/تاسكات مرة واحدة (إشعارات البنك، إعادة تنظيم شاشة
"ذكاء زاد"، وكلاء متعددين، تتبع الكاش السلبي، بوت تيليجرام، ركود المخزون، ٥ أفكار
إضافية، Knowledge graph). بعد استكشاف الكود الحقيقي، تبيّن إن معظمها بعيد عن الكود
الفعلي (Kotlin/Compose مش Flutter). المستخدم بعدين بعت مستندات تخطيط حقيقية ومركّزة:
`docs/agent/EPIC_1_4.md` (تاسكات 19-24) و`docs/agent/PRODUCT_PLAN.md` (Phases A-D،
تاسكات 25-28) — دول المرجع الحقيقي دلوقتي، مش الرسالة الأولى.

## اللي خلص فعلياً هذه الجلسة (بالترتيب)

### Task 19.1 — الأوديت (تقرير، بلا كود)
لقينا: سحب ATM بيتسجل `is_expense=true` عادي، يعني السحب والصرف من الكاش بيتحسبوا
مصروف مرتين. مفيش مفهوم wallet/كاش خالص في الكود وقتها.

### Task 19.0 — سلطة ميزانية مشتقة واحدة (مش في الخطة الأصلية — ضروري اكتشفناه)
- `zad_users.budget` كان عمود بوظيفتين متضاربتين: السقف اللي المستخدم بيحدده + رصيد
  متراكم الـ trigger بينقّصه بكل معاملة. اتقسموا: `monthly_limit` (سقف، المستخدم بس
  بيغيّره) + الرصيد بقى **مشتق دايماً** من `BudgetMath.kt` (ملف جديد، الحساب الوحيد
  لـ "مصروف الشهر"/"المتبقي" في التطبيق كله).
- الـ trigger (`update_budget_on_transaction`) اتشال نهائي، اتأكد فعلياً (مش افتراض).
- `BudgetTracker`: الجزء اللي بيعدّل رصيد إجمالي اتشال، كروت الفئات والتنبيهات فضلوا.
  تنبيه ٧٥/٩٠/١٠٠٪ بقى محسوب لحظياً من Room (مسار الخلفية، `checkOverallBudgetThreshold`).
- `BudgetSetupPromptCard` — لو السقف مش مؤكد، الشاشة تسأل بدل ما تعرض رقم غلط.
- Room database: لقينا `fallbackToDestructiveMigration()` بيمسح كل قاعدة البيانات
  المحلية (مش بس المعاملات) على أي version bump غير متوقع. كتبنا `Migration` حقيقية
  بدل قبول الخطر ده.
- Commits: `abead82`, `6de438f`, `a19ea3d`, `60ea32f`, `8fb8caf`, `0c79f80`

### Task 19.2 — schema: `wallet`/`txn_kind`/`transfer_to`
- أعمدة جديدة على `zad_transactions` (Supabase + Room، الاتنين — `ZadTransaction`
  Room entity حقيقي مش بس DTO).
- سحب ATM بقى يتصنف `txn_kind='transfer', transfer_to='cash'` تلقائي من
  `BankTransactionApplier` (نقطة التجمّع الوحيدة لكل معاملة بنكية).
- Commit: `7cbf1a0`

### Task 19.3 — قراءة `txn_kind` بدل `isExpense` + backfill + `zad_cash_balance()`
- Backfill حقيقي على قاعدة البيانات الحية: صحّح ٣ صفوف كان `txn_kind` غلط فيهم
  (دخل متصنف مصروف بالخطأ من الـ default). **صفر سحوبات ATM موجودة فعلياً دلوقتي،
  فصفر تأثير على أي رقم ظاهر للمستخدم** — تأكدنا بالاستعلام قبل وبعد.
- `BudgetMath` بقى يفلتر `txnKind`، مش `isExpense`. كل حاجة اتوصلت بـ 19.0
  (`ZadViewModel`, `FamilyState`, تنبيه ٨٥٪) اتصلحت تلقائي بلا لمس إضافي.
- `zad-brain` (السيرفر) بقى يقرا `txn_kind` بردو — منشور version 29.
- `notify_parents_on_child_spend` و`zad_brain_self_review` (SQL functions) اتصلحوا —
  ده بالظبط البق اللي كان موثّق "مش بيتصلح هنا، متعمد" في كوميت 19.0، دلوقتي اتقفل.
- Commit: `58b69eb`

### Task 20 — dedupe كـ per-country config (كان نص شغل، دلوقتي خلص)
- جدول `zad_locale_config` (EG/SA/TR، افتراضي 36h/5%) — اتطبق حي، RLS مفعّل.
- `TxDeduplicator.WINDOW_MS`/التسامح بقوا محمّلين من `refreshLocaleConfig()` (مكاش في
  SharedPreferences، بيتنادى من `ZadViewModel.init`) بدل constants ثابتة.
- قفل صلب عند ٥٪ في الكود نفسه (مش توثيق بس) — لو صف على السيرفر طلب نسبة أعلى، بيتقفل.
- Commit: `8dfe84a`

### Task 21 — Egypt SMS tuning, country-keyed
- المستخدم بعت عينات SMS تركيبية (مش نسخ حقيقي من الجهاز) للسعودية ومصر، وطلب معمارية
  عالمية جديدة لكل الدول المستقبلية. اتوضح إن ده "Global Fallback Engine" موجود فعلاً
  (`ZadAiRepository.analyzeBankNotification` — AI fallback شغال لأي بلد/عملة أصلاً)،
  فاتنفذ نطاق Task 21 الموثّق فقط (Egypt tuning + country-keyed rule loading)، من غير
  طبقة regex عالمية جديدة — نفس انضباط قسم "rejected proposals" في EPIC_1_4.md.
- `BankRulesEngine.tryParse` بقى يفلتر بـ `country` النشط (`MarketPrefs.currentMarket`)
  + fallback عام "ALL" (مفيش قواعد عامة لسه). قبل كده كان بيجرب كل قواعد bank_rules.json
  بغض النظر عن البلد.
- بق حقيقي اتلقى وانصلح: صيغة "Purchase of EGP 450.00" (العملة قبل الرقم) ماكانتش
  بتتطابق — الـ regex كان بيطلب العملة بعد الرقم إجباري. العملة بقت اختيارية في كل الـ 9
  قواعد المصرية.
- قاعدة سحب ATM مصرية جديدة (`eg_atm_withdrawal`) → `TxType.WITHDRAWAL` →
  `txn_kind='transfer'` (نفس فكرة 19.2)، و`typeToTxType` بقى يفهم `"withdrawal"`.
- InstaPay: قاعدة واردة جديدة (`eg_instapay_credit`) جنب الصادرة الموجودة.
- `SaBankParser`: أضيف "كود التفعيل" (ضجيج OTP) و"مدفوعة" (شراء سعودي).
- 7 اختبارات جديدة في `BankRulesEngineTest`. Suite كامل: 83 اختبار، 0 فشل.
  `lintDebug` و`assembleDebug` نجحوا (`./gradlew --no-daemon`).
- Commit: `df46bf3`

### Task 22 — Habit chips
- توثيق التاسك مفترض بنية تحتية مش موجودة: `ZadIngest` (Task 12، لسه تعليق TODO بس)،
  كارت الكاش (Task 19.4، ❌ مبدأش)، و"quick-add sheet" (مش موجودة أصلاً). اتوقف واتسأل
  المستخدم — اختار: نعمل الدالة SQL + الـ chips على شاشة موجودة (`TransactionsScreen`)،
  والإدراج عن طريق `ZadViewModel.addTransaction()` الحالي (مش ZadIngest).
- بق تاني اتلقى وقت التنفيذ: الـ SQL في التوثيق بيعمل `group by merchant_name`، والعمود
  ده مش موجود في `zad_transactions` الحي (اتأكد بالاستعلام المباشر — الأعمدة الفعلية:
  id/user_id/amount/title/category/is_expense/created_at/wallet/txn_kind/transfer_to).
  استُخدم `title` بدل، وهو أقرب عمود نصي حر فعلي موجود.
- `zad_habit_chips(p_user, p_same_weekday)` اتطبقت حية وتأكدت بالاستعلام (رجعت فاضية
  لمستخدم وهمي، وفاضية كمان على الجدول الحقيقي — طبيعي، البيانات لسه قليلة زي ما
  Task 19.3 لقى).
- `SupabaseRepo.getHabitChips()`, `ZadViewModel.habitChips` (StateFlow، يتحمّل عند
  init + بعد أي إضافة معاملة)، `HabitChipsRow` composable في `TransactionsScreen` —
  Tap بيسجل مصروف كاش (`wallet="cash"`) فوري.
- **مؤجل عمداً** (مش من ضمن الخيارين المطلوبين): "تكلفة العادة السنوية" (البونص المجاني
  في نفس التاسك) — محتاج آلية cap ربع-سنوي لكل عادة، مش جزء من نطاق الكوميت ده.
- 83 اختبار (0 جديد — منطق SQL/شبكة، مفيش mock framework للـ Supabase في المشروع أصلاً،
  نفس نمط باقي دوال SupabaseRepo). lintDebug وassembleDebug نجحوا.
- Commit: `48e49ed`

### Task 19.4 — كارت الكاش على الشاشة الرئيسية
- `BudgetMath.cashOnHand(txs)` — نفس منطق `zad_cash_balance()` SQL بالظبط، لكن محسوب من
  Room مباشرة (مش نداء شبكة لنفس الـ RPC) — نفس مبدأ سلطة واحدة مشتقة من 19.0. الدالة على
  السيرفر فضلت زي ما هي لمستهلكين تانيين (زاد-برين).
  `ZadViewModel.cashOnHand` StateFlow بيتحدث مع كل تحديث معاملات (نفس نقطة
  `recalculateRemainingBalance`).
- `CashCard` (كومبوننت جديد `ZadCashCard.kt`) على `HomeScreen` — بيظهر بس لو
  `cashOnHand > 0`، ويختفي لوحده عند الصفر. فيه: الرصيد، `HabitChipsRow` المعاد استخدامها
  من Task 22 (بقت `internal` بدل `private`، وأضيف لها `horizontalPadding` عشان متتكررش
  البادينج جوه الكارت)، وزرار "صرفت منهم" بيفتح حوار بسيط (مصروف دايماً، بلا toggle دخل/مصروف).
- رسالة تعليمية لمرة واحدة طول العمر عند أول سحب ATM — في `BankTransactionApplier`
  (نقطة التجمّع الوحيدة الموجودة لكل معاملة بنكية)، عن طريق نفس مسار
  `sendAppNotification()` اللي `UnifiedSmsReceiver` بيستخدمه أصلاً لإشعار الراتب. ده
  بيتخطى `ZadAlertRouter` (اللي المفروض يبقى نقطة العبور الوحيدة حسب معيار Task 24) —
  اتوثّق في تعليق إن ده نفس النمط الموجود فعلاً، مش تناقض جديد، وTask 24 هو اللي هيوحّدهم.
- `BudgetMathTest.kt` جديد (أول اختبارات لـ `BudgetMath` في المشروع) — 6 حالات لـ
  `cashOnHand` بس، مش `spentThisMonth`/`remaining` (خارج نطاق التاسك ده).
- 89 اختبار (6 جديد)، 0 فشل. `lintDebug`/`assembleDebug` نجحوا.
- Commit: `bfda9a2`

### Task 19.5 — تسوية الكاش الأسبوعية عبر ask_user
- `buildSnapshot()` (zad-brain) بقى يستهلك `zad_cash_balance()` فعلياً أخيراً — الـ RPC
  اللي 19.3 عملها و19.4 سابها عمداً من غير استهلاك من العميل "لمستهلك تاني زي زاد-برين"،
  ده هو المستهلك ده بالظبط دلوقتي.
- `dedupe_key` أسبوعي (`cash_reconciliation_<سنة>_w<أسبوع>`) بيتحسب في الكود (isoWeekKey)
  مش الموديل — نفس مبدأ `suggest_budget_change` الشهري الموجود أصلاً.
- `validateAskUser` (validators.ts): رفض صلب (مش تعليمة prompt بس) لو الموديل اخترع مفتاح
  تاني، أو حاول يسأل تاني في نفس الأسبوع، أو حاول يسأل بعد رفضين — "احترام الرفض تماماً"
  بقى بوابة كود حتمية.
- أداة جديدة `reconcile_cash_balance` (تسجل صف تصحيحي واحد). لقيت فجوة صياغة/آلية: التوثيق
  بيقول "transfer row" للاتجاهين، بس الآلية الحقيقية (`BudgetMath.cashOnHand`) بتنقّص الكاش
  بس مع `expense+wallet=cash`، مش transfer — اتوثّق في تعليق كود، مش انحراف صامت.
- System prompt: فقرة جديدة توضح إمتى يسأل/يبطل/يسجل الدرس، ونداء `reconcile_cash_balance`
  فوراً لما يجيله الرد.
- 27 اختبار Deno (7 جديدة)، كلها ناجحة. `deno check` نظيف على كل ملفات zad-brain.
- **نُشر live (zad-brain v31)**. اكتشاف مهم أثناء التحقق بعد النشر: smoke test كشف إن
  `ZAD_BASE_URL` secret فيه قيمة ملفوفة بصيغة Markdown link
  (`[https://api.groq.com/openai/v1](...)`) بدل رابط خام — ده بيوقف أي تشغيل حقيقي لـ
  zad-brain (daily/event/chat كلهم، مش بس تسوية الكاش) لحد ما يتصلح. مفيش أداة عندي
  لتعديل الـ secrets — لازم المستخدم يصلحها يدوي (Supabase dashboard أو
  `supabase secrets set`).
- Commit: `6037768`

## اللي لسه فاضل — بالترتيب المتفق عليه في EPIC_1_4.md

```
19.1 ✅ → 19.0 ✅ (إضافي) → 19.2 ✅ → 19.3 ✅ → 20 ✅ → 21 ✅ → 22 ✅ → 19.4 ✅ → 19.5 ✅ → 23 → 24
```

| # | الموضوع | الحالة |
|---|---|---|
| **habit lifetime cost** | بونص Task 22 (تكلفة سنوية، cap ربع-سنوي) | ❌ مؤجل عمداً |
| ~~**ZAD_BASE_URL**~~ | secret كان فيه Markdown-wrapped URL | ✅ اتصلح من المستخدم — smoke test اتأكد (`ok:true`) |
| **23** | Inventory stagnation | ❌ مبدأش |
| **24** | Full-app consistency audit (`AUDIT.md`) | ❌ مبدأش |

**ملاحظة ترتيب:** Task 21 محجوز (blocked) لحد ما المستخدم يبعت رسائل SMS حقيقية من بنوك
مصرية. Task 22 (habit chips) مفيش له dependency زي كده، ممكن يتعمل قبل 21 من غير ما
يكسر ترتيب EPIC_1_4.md الفعلي (21 قبل 22 كان افتراض إن 21 هيبقى جاهز، مش حقيقة ثابتة).

**`PRODUCT_PLAN.md` Phase A-D (تاسكات 25-28):** كله لسه مبدأش — دورة الراتب، رقم
"متاح" (الالتزامات)، `≈` للثقة، "الرقم غلط"، Telegram، حلقة التعلّم، family alerts.

**فجوة موثّقة عمداً، مش هتتصلح قريب:** `ZadCentralBrain` عنده ١٥ موقع بيستخدموا
`isExpense` — بس ٢ بس (اللي بيغذوا "المتبقي") اتصلحوا. الـ ١٣ الباقيين (تحليل الفئات،
أكتر التجار، الاتجاه اليومي، اكتشاف الشذوذ) لسه ممكن يحسبوا تحويل (سحب ATM) كأنه
مصروف حقيقي في تحليلات ثانوية — نفس الانضباط اللي طبّقناه في 19.0 (رقم واحد بس،
مش كل تحليل مشتق). نفس الفجوة موجودة في `update-behavior-profile` والـ seasonal
forecasting على السيرفر.

## ملفات مرجعية

- `docs/agent/PRODUCT_PLAN.md` — **ابدأ هنا** لأي تاسك جديد، بيحدد الأولوية
- `docs/agent/EPIC_1_4.md` — تفاصيل تاسكات 19-24، فيه قسم "rejected proposals" صريح
- `docs/agent/PROGRESS.md` — اللوج الرسمي append-only لكل تاسك خلص
- `docs/agent/19_0_single_authority.md` — سبيسفيكيشن Task 19.0 الكاملة
- `CLAUDE.md` — قواعد المشروع الثابتة (حقائق الـ AI provider، قواعد الأمان، إلخ)

## قواعد ثابتة اتطبقت طول الجلسة دي (من CLAUDE.md)

- تاسك واحد، كوميت واحد، تقرير واحد، وقف.
- ماحدش يتقال "خلص" غير بعد build حقيقي بعد التعديل (`assembleDebug` + `testDebugUnitTest`
  + `lintDebug` كلهم لازم ينجحوا، الأرقام اتأكدت مش افتُرضت).
- أي migration بتتطبق فعلياً على قاعدة البيانات الحية، مش بس تتكتب — واتحقق منها
  بالاستعلام المباشر بعدها، مش بافتراض إن الأداة نجحت.
