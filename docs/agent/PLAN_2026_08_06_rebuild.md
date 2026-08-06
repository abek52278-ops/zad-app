# خطة إعادة البناء — 2026-08-06

ملف الحالة الجاري لمسار "الأرقام الحقيقية + الواجهة العالمية". **يتحدّث مع كل مرحلة تخلص**
(زي `SESSION_2026_07_30_phaseA.md` بالظبط)، عشان أي جلسة جديدة تبدأ عارفة إحنا فين
من غير ما تعيد استنتاج الحكاية من الـ commits.

الخطة دي **مش** بديلة لـ `PRODUCT_PLAN.md` ولا لترقيم التاسكات في `ZAD_MASTER.md` —
هي ترتيب تنفيذ عرضي فوقهم، جاي من شكوى المستخدم: "كل البيانات وهمية والأرقام غلط".

## قرارات مثبّتة (اتحسمت 2026-08-06)

- الواجهة: **بناء من الصفر بـ Figma MCP** على iOS HIG + Material 3 — الـ mockup القديم
  (`ZAD App.dc.html`) مش موجود في الريبو ومش هيتنطر. `web_preview/index.html` مرجع تاريخي بس.
- ترتيب البدء: **مرحلة ٠ الأول**، وبعدها مرحلة ١ على طول.
- الفلوس تفضل `Double` مع `asMoney()` — مفيش هجرة minor-units (قاعدة CLAUDE.md).

---

## مرحلة ٠ — أوقف نزيف الأرقام · ✅ خلصت

المشكلة: التطبيق بيعرض أصفار وأرقام افتراضية كأنها حقيقة، ونص للمطوّر ظاهر للمستخدم.

| # | البند | الحالة |
|---|------|--------|
| ٠أ | `budget_save_note` ("سيتم حفظ الميزانية في Supabase → جدول zad_users") يتشال من الـ ٤ ملفات strings ويتحول لرسالة للمستخدم | ✅ |
| ٠ب | `BudgetMath.remaining` (وأخواتها) ترجع `null` بدل `0.0` لما `monthlyLimit <= 0` | ✅ |
| ٠ج | بوابة إجبارية للسقف بعد الدخول — مفيش أي رقم قبل ما المستخدم يحدد سقفه | ✅ |
| ٠د | تحديث `BudgetMathTest` + compile + unit tests | ✅ — `:app:compileDebugKotlin` نظيف، `:app:testDebugUnitTest` = 134/134 ناجح (28 منهم BudgetMathTest) |

تفاصيل ٠ب — الدوال اللي بقت `Double?`: `remaining`، `remainingInCycle`،
`dailyAllowanceInCycle`، `velocityInCycle`، `availableInCycle`. المستهلكين اللي اتعدّلوا:
`ZadViewModel._remainingBalance`/`_availableFigure` (بقوا nullable)، `BudgetScreen`،
`HomeScreen`، `ZadVoiceFab`، `FamilyState`، `BudgetTracker`، `ZadCentralBrain`
(`BrainReport.remaining` بقى `Double?`).

تفاصيل ٠ج — البوابة في `MainScreen` (مش في `OnboardingScreen`، ده carousel قبل الدخول):
`ZadViewModel.budgetLoaded` علشان مايحصلش وميض لمستخدم سقفه متأكد، وبعدين
`BudgetGateScreen` لحد ما `updateBudget()` تتنادى. مفيش زرار تخطي — إجباري بقرار المستخدم.

## مرحلة ١ — العملة والدولة الحقيقية · ✅ خلصت (مع فجوة موثّقة)

| # | البند | الحالة |
|---|------|--------|
| ١أ | عمود `zad_transactions.currency` — Supabase migration (`zad_transactions_currency`) + Room `MIGRATION_14_15` (v14→v15) + `ZadTransaction.currency` | ✅ |
| ١ب | `SaBankParser.extractCurrency()` يستخرج العملة من نص الرسالة نفسها (يعيد استخدام `CUR` regex الموجود، `SaBankParser.kt`) بدل افتراض ريال، متسلّك في `detectAndParse` | ✅ |
| ١ج | `BankRulesEngine.tryParse` يستنتج العملة من بلد القاعدة (`rule.country`، القواعد أصلاً مفلترة بالسوق النشط) | ✅ |
| ١د | `CurrencyFormatter.format(context, tx: ZadTransaction)` overload — يعرض عملة المعاملة، مش عملة الـ Market الحالي؛ يسقط لعملة الـ Market لو `tx.currency == null` (صفوف قديمة) | ✅ |
| ١ه | `UnifiedBankListener` يمرر `parsed.currency` لـ `ZadTransaction` عند الحفظ | ✅ |
| ١و | كشف سفر: `TravelDetector` (`networkCountryIso` + fallback `Locale`) + `TravelBanner` composable في `HomeScreen` — اقتراح تحويل قابل للتجاهل، مش تبديل صامت | ✅ |
| ١ز | اختبارات: `SaBankParserTest` (+4 `extractCurrency`)، `BankRulesEngineTest` (+تأكيد `EGP`)، `CurrencyFormatterTest` جديد (4) | ✅ — 142/142 |

**فجوة موثّقة، مش مقفولة**: مسار الـ AI fallback (`ZadAiRepository.analyzeBankNotification` في
`UnifiedBankListener.kt`، بيتفعّل لما `SaBankParser`/`BankRulesEngine` الاتنين يرجّعوا
null) بيرجّع `ZadTransaction` من غير `currency` — بيسقط لعملة الـ Market الحالي زي
السلوك القديم، مش كذب لكنه مش "عملة حقيقية" كمان. يحتاج تعديل الـ Edge Function
(`zad-core-intelligence`) عشان يستخرج العملة كمان، خارج نطاق تعديل كوتلن-فقط. مرشّح
لمرحلة لاحقة أو لتاسك منفصل.

**غير منفّذ عمداً** (خارج نطاق "عملة حقيقية"، جزء من مرحلة ٢): توسيع `Market` enum
لأسواق تانية غير السعودية/مصر/تركيا — `TravelDetector.suggestedMarketFor` بيرجع null
لأي بلد مكتشف مش من التلاتة دول لسه.

## مرحلة ٢ — الشرق الأوسط + البرسونا · ⬜

- توسيع `Market` لكل الشرق الأوسط وشمال أفريقيا + تركيا.
- `dialectInstruction` حقيقي لكل سوق (خليجي/مصري/شامي/عراقي/مغاربي) — الحقن شغال فعلاً
  في `ZadAiRepository.kt:827`.
- شاشة اختيار البلد: `Row` تبقى `LazyVerticalGrid` + بحث + علم، ونفس المكوّن في الـ onboarding
  وفي البروفايل.

## مرحلة ٣ — المخزون الحي · ⬜

- `InventoryFlowEngine`: معاملة فئة "بقالة" تسأل "ضيف إيه للمخزون؟".
- `ConsumptionLearner`: سؤال دوري فعلي ("خلص الحليب؟") بأزرار خصم سريعة (−1 / خلص / لسه).

## مرحلة ٤ — اللوكيشن والـ geofence · ⬜

- تشغيل الـ geofence من الـ onboarding بشرح واضح، مش مدفون في الإعدادات.
- تأكيد `LOCATIONIQ_API_KEY` على Supabase (فحص بالـ MCP)، وإلا Overpass fallback بس.
- ملاحظة Play Store: `ACCESS_BACKGROUND_LOCATION` لسه غير محسومة (شوف CLAUDE.md).

## مرحلة ٥ — الواجهة والـ agent الحي · ⬜

- تصميم من الصفر بـ Figma MCP: corner radii كبيرة، كروت هادية، blur، تدرّج هرمي واضح.
- الـ agent الحي: blob متدرّج بحركة تنفّس + عيون تتحرك مع الحالة (بيسمع/بيفكر/بيتكلم)،
  يستبدل `ZadVoiceFab`.

---

## قواعد شغل على الخطة دي

- مرحلة = commit واحد + تقرير.
- مفيش "تمّت" من غير مخرجات بناء بعد التعديل (قاعدة CLAUDE.md).
- الجدول فوق هو مصدر الحقيقة للحالة — يتحدّث في نفس الـ commit بتاع المرحلة.
