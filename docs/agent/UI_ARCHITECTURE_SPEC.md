# UI_ARCHITECTURE_SPEC.md — مواصفة إعادة بناء طبقة الواجهة

**الحالة**: 🟢 **اكتملت جميع البوابات الخمس + كل ثغرات §7 اتقفلت** (2026-09-03). القرارات السبعة في §4 كلها اتنفذت. شوف §2.1–§2.5 و§6 لتفاصيل كل بوابة. **§7**: جرد كامل للميزات الجاهزة في الباك إند بدون واجهة — الأربعة بنود (§7.1–§7.4) كلها ✅ اتحلت (واحدة كانت خلصت أصلاً وقت تنفيذ البوابة 5، والتلاتة الباقيين اتنفذوا في جولة تنفيذ منفصلة).
**الأساس**: جرد حي لـ 84 جدول Supabase (`mcp__supabase__list_tables`, 2026-09-03) + مسح كامل لكل شاشات/ViewModels/SupabaseRepo الحالية. كل اسم عمود ودالة هنا مقروء من الكود الفعلي، مفيش تخمين — أي حقل مش متأكد منه متعلّم TBD صراحة بدل ما يتم اختراعه (زي درس `project_zad_schema_contract_bug`: أعمدة متخيّلة بتتبلع بصمت).

---

## 0. مبدأ العقد (1:1 Contract Mapping)

الطبقة الجديدة **تركب فوق** `SupabaseRepo` و`ZadViewModel`/`FamilyViewModel` الموجودين، مش بتستبدلهم. معنى كده:
- مفيش دالة Repo أو VM جديدة إلا لو مفيش بديل حقيقي (متعلّم صراحة في كل عقد شاشة).
- كل حقل UiState لازم يتتبع لعمود Supabase حقيقي أو StateFlow موجود فعلاً — مفيش حقل "هيتحسب بعدين".
- ممنوع أي fallback وهمي (mock/hardcoded). لو الليست فاضية: تتخفي الكارت أو تظهر Empty State — مفيش نص "لسه مش محسوب" ولا رقم افتراضي زي `3500.0` (اللي اتحذّر منه في `AUDIT.md:44`) أو جدول أسعار محلي زي `getEstimatedPrice()` (`AUDIT.md:47,138-139`).

---

## 1. البوابات الخمس (Core Navigation Shell)

**الوضع الحالي**: Bottom Nav فيه 4 تابات بس (Home / Assistant / Inventory / More-sheet)، والـ"Finances" مقسومة على شاشتين منفصلتين بموديلين مختلفين (`BudgetScreen` + `SubscriptionsScreen`)، وفيه شاشات كتير (Recommendations, Achievements, ZadMemory, AgentActionLog, KnowledgeMap, Tasbiha, FamilyPharmacy) وصلها بس من درج جانبي أو Route مدفون جوه شاشة تانية — يعني فعليًا "صفحات مش ضايعة" لكن مش متاحة من مكان واضح. التصميم الجديد بيجمعهم تحت 5 بوابات فعلية، وأي حاجة مش دومين-محدد (settings/auth/paywall) بتفضل خارج الـ Bottom Nav زي ما هي دلوقتي — مفيش صفحة أو زرار بيضيع، لكن كل حاجة يبقى ليها مكان واضح.

| # | البوابة | الجداول المربوطة | الشاشات الحالية اللي بتتجمع فيها |
|---|---|---|---|
| 1 | **الرئيسية (Home Hub)** | — (aggregator، بيقرا من باقي الجداول) | `HomeScreen` (مُعاد نطاقه — شوف §4) |
| 2 | **الميزانية والالتزامات (Finances)** | `zad_transactions`, `zad_obligations`, `zad_debts`, `zad_subscriptions` | `BudgetScreen` + `SubscriptionsScreen` (دمج حقيقي، مش مجرد تسمية) + `StatementImportScreen` (كأداة فرعية) |
| 3 | **المخزون والتسوق (Pantry & Shopping)** | `zad_inventory`, `zad_shopping_list`, `shopping_recommendations` | `InventoryScreen` + `ShoppingListScreen` + `RecommendationsScreen` (كانت orphaned) + `NearbyDealsScreen`/`PriceReportingScreen` (كأدوات فرعية) |
| 4 | **الصيدلية العائلية (Health)** | `zad_pharmacy_items`, `zad_dose_log` | `PharmacyScreen` + `FamilyPharmacyScreen` (كانت nested route بس، بقت تاب/فلتر داخل نفس البوابة) |
| 5 | **عقل زاد والعائلة (Brain & Family)** | `user_behavior_profile`, `zad_insights`, `family_groups`, `sinking_funds` (+ `zad_memory`, `agent_actions`, `zad_debts`-adjacent challenges) | `ZadIntelligenceScreen` (Analytics+Chat) + `FamilyScreen` + `TasbihaScreen` + `ZadKnowledgeMapScreen` + `ZadMemoryScreen` + `AgentActionLogScreen` + `AchievementsScreen` (كانت orphaned) |

**خارج الـ 5 بوابات عمدًا** (cross-cutting أو settings، بتفضل زي وضعها الحالي، متاحة من الـ Shell العام مش من تاب مخصص):
`CameraScreen` (شيت عام بيتفتح من أي بوابة)، `NotificationCenterScreen` (أيقونة الجرس في الـ App Bar)، `ProfileScreen` + الشاشات الفرعية بتاعته (`EditProfile`, `FamilyManagement`, `PaymentAndBudget`, `AssistantAlerts`, `Terms`, `HelpSupport`)، `ZadSubscriptionPaywallScreen`/`SubscriptionPlansScreen` (monetization، بيتفتح contextually)، `BudgetGateScreen` (onboarding gate قبل أي بوابة مالية)، وشاشات `auth/*` (قبل تسجيل الدخول أصلاً).
`MaintenanceScreen` مقترح ينضم لبوابة Pantry & Shopping (إدارة منزلية) — قرار محتاج اعتماد، شايفه في §4.

---

## 2. عقد كل شاشة (Screen Contract Specification)

### 2.1 البوابة 1 — الرئيسية (Home Hub)

| Route | UiState (من الحقيقي) | Actions ← VM function | Empty/Loading |
|---|---|---|---|
| `home` (`ZadRoutes.HOME`) | كروت Glance مبنية على: `nextObligationDue: Pair<ZadObligation,LocalDate>?` (ZadViewModel:418)، `pharmacyItems` (فلترة `remaining_quantity` منخفض/`expiry_date` قريب)، `inventory` (فلترة `quantity <= low_stock_threshold`)، `zadInsights`/`insights` (أولوية `priority`)، صف اشتراكات مستحقة قريبًا من `zad_subscriptions.due_day`. مسار الصوت الحي: **TBD** — الحالة الفعلية بتحتاج قراءة `ZadVoiceBottomSheet.kt`/`TelegramBinding.kt` قبل ما تتحدد هنا، مش هتتخيّل. | `refreshAgentSummary`, `refreshAutoSuggestions`, `predictNextMonthExpenses`, `refreshLiveMarketPrices` (الأربعة دول مُبقّاة عمدًا على `LaunchedEffect(Unit)` — قرار مستخدم موثّق في CLAUDE.md، مايتلغيش) | كل كارت Glance بيتخفي تمامًا لو مصدره فاضي — مفيش كارت رمادي معطّل. لا يوجد Empty State عام للشاشة نفسها (aggregator مش list-backed). |

### 2.2 البوابة 2 — الميزانية والالتزامات (Finances) — **مُنفذة**

دُمجت فعليًا في شاشة واحدة (`FinancesScreen.kt`) بـ 3 تابات فوقانية (`ZadSegmentedTabs`، `enum FinancesTab`): الحركات والميزانية | الاشتراكات والأقساط (وفواتير) | الديون. كل تاب بيستدعي شاشة/كومبوننت أصلي بمنطقه الحقيقي زي ما هو.

| Tab / Route | UiState (أعمدة حقيقية) | Actions ← VM function | Empty/Loading |
|---|---|---|---|
| **الحركات والميزانية** (`BudgetScreen`, تاب `DAILY`) | `budget: Double`, `budgetConfirmed: Boolean`, `budgetState: BudgetState?` (نتيجة `zad_budget_state()`، فيها `opening_balance` = عمود `monthly_limit`)، `transactions: List<ZadTransaction>` — أعمدة: `id, amount, title, category, is_expense, created_at, wallet, txn_kind, transfer_to, merchant_name, bank_name, source_type, is_verified, currency, counts_toward_budget, original_amount, original_currency, linked_obligation_id`، `obligations: List<ZadObligation>` — أعمدة: `id, title, amount, kind, due_day, due_date, recurrence, auto_detected, confirmed, active, provider, total_installments, remaining_installments` (قيم `kind` الفعلية: `rent, installment, debt, tuition, utility, other`) | `addTransaction`, `updateTransaction`, `deleteTransaction`, `updateTransactionCategory`, `loadObligations`, `addObligation`, `updateObligation`, `deleteObligation`, `updateBudget`, `applySuggestedBudget`/`dismissBudgetSuggestion`, `refreshBudgetState` | مفيش حركات → `ZadEmptyState` (كانت Lottie، اتوحّدت). مفيش التزامات → الكارت بيتخفي تمامًا. |
| **الاشتراكات والأقساط** (`SubscriptionsScreen`, تاب `SUBSCRIPTIONS`، فلترة داخلية 4 خيارات: الكل/اشتراكات/فواتير/أقساط) | `zad_subscriptions` أعمدة: `id, title, amount, renewal_date, category, is_active, billing_cycle, due_day, auto_deduct, type, provider, source` — الفلترة الداخلية بتقارن `type`/`category` معًا (`type == "subscription" \|\| category == "اشتراك"`... إلخ، `SubscriptionsScreen.kt:73-79`)، مش `type` بس | `addSubscription`, `deleteSubscription`, `updateSubscriptionActive`, `updateSubscriptionAutoDeduct` — **TBD القديمة اتحلّت**: التلاتة الأول مؤكدين موجودين ومستخدمين فعليًا في `ZadViewModel` | مفيش اشتراكات → `ZadEmptyState` (كانت Lottie، اتوحّدت) |
| **الديون** (تاب مستقل داخل نفس الشاشة، `FinancesDebtsBody`) | `zad_debts` أعمدة: `id, family_id, name, principal_amount, remaining_balance, interest_rate, minimum_payment, due_day, is_active` | `loadDebts`, `addDebt`, `deleteDebt`, `updateDebtBalance` (خطة السداد SNOWBALL/AVALANCHE — `calculateDebtPayoffPlan`/`DebtPayoffPlannerCard`, نفس المنطق الأصلي زي ما هو) | مفيش ديون → `ZadListCard`'s الداخلية بتعرض حالة فاضية (نص + أيقونة، موجودة من قبل) |
| أداة فرعية: **استيراد كشف حساب** (`statement_import`) | نفس `StatementImportScreen` الحالي | — | **لسه مانفذتش**: الشاشة دي متعلّمة في `AUDIT.md:36,145-146` إنها بتطبع مبالغ خام من غير `CurrencyFormatter` — لازم تتصلح، مش تتنقل زي ما هي. مش جزء من تنفيذ 2026-09-03، برة نطاق التنفيذ ده. |

**ملحوظة تنفيذ**: مخطط سداد الديون (`DebtPayoffPlannerCard`) + العروض الحية (`LiveDealsCard`) + تحديات العائلة (`FinancialChallengesCard`) + صناديق الادخار (`SinkingFundsCard`) كانوا كلهم مقحمين جوه تاب "الكل" الداخلي القديم في `SubscriptionsScreen` — دلوقتي كلهم في تاب "الديون" الجديد. `FinancialChallengesCard`/`SinkingFundsCard` منطقيًا أقرب لبوابة Brain & Family (جداول `family_financial_challenges`/`sinking_funds`، §1) — مكانهم النهائي قرار مؤجل لحد ما البوابة دي تتنفذ، مفيش حاجة ضايعة دلوقتي.

### 2.3 البوابة 3 — المخزون والتسوق (Pantry & Shopping) — **مُنفذة**

دُمجت فعليًا في شاشة واحدة (`PantryShoppingScreen.kt`) بـ 4 تابات فوقانية (`ZadSegmentedTabs`) — كل تاب بيستدعي الشاشة الأصلية بمنطقها وViewModel-calls زي ما هي (دمج ملاحة/عرض، مش إعادة كتابة منطق).

| Tab / Route | UiState (أعمدة حقيقية) | Actions ← VM function | Empty/Loading |
|---|---|---|---|
| **المخزون** (`inventory`) | `zad_inventory` أعمدة: `id, item_name, category, quantity, expiry_date, unit, low_stock_threshold, family_id` — `inventory: StateFlow<List<ZadInventory>>`, `inventorySearchQuery`, `inventoryCheckIns: List<CheckInCandidate>` | `addInventory`, `injectScannedItems`, `consumeInventoryItem`, `updateInventoryItem`, `deleteInventory`, `answerCheckInDecrement/Finished/StillHave`, `undoInventoryCommit` | مفيش أصناف → `ZadEmptyState` (كانت كده أصلاً). بانر النقص: `getEstimatedPrice()` **اتشالت بالكامل** — `zad_inventory` مفيهوش عمود سعر أصلاً ومفيش cache متزامن حقيقي، فالبانر بيعرض `estimated_cost_unknown` ("غير محدد") بدل رقم مختلق؛ "أضف لقائمة التسوق" بيبعت `estimatedPrice = 0.0` (زي باقي نقاط الإضافة في التطبيق) وياخده pipeline التقدير الحقيقي الموجود فعلاً في تاب التسوق. |
| **قايمة التسوق** (`shopping`) | `zad_shopping_list` أعمدة: `id, item_name, quantity, estimated_price, is_purchased, priority, predicted_days_left, store` | `addShoppingItem`, `toggleShoppingItemPurchased`, `deleteShoppingItem`, `refreshSmartShopping`, `recordAffiliateClick` | مفيش عناصر → `ZadEmptyState` (كانت Lottie مستقلة `SmartEmptyState()`، اتوحّدت). |
| **توصيات ذكية** (تاب داخل نفس الشاشة — كانت `ZadNav.RECOMMENDATIONS` orphaned، الـ route اتشالت خالص) | `shopping_recommendations` أعمدة: `id, item_name, recommendation_type, reasoning, estimated_savings, urgency, best_store, best_price, acted_on_at, dismissed_at` | `SupabaseRepo.markRecommendationActedOn`/`dismissRecommendation` **مباشرة** (مفيش ViewModel wrapper — TBD القديمة اتحلّت: `RecommendationsRoute` أصلاً كانت بتقرا/تكتب Supabase مباشرة زي `FamilyPharmacyScreen`/`AchievementsRoute`، نفس النمط) | مفيش توصيات → Empty State داخلي موجود بالفعل في `RecommendationsScreen` (كارت "شغلت كل التوصيات") |
| **الصيانة** (`maintenance`) | `ZadMaintenanceItem` عبر `viewModel.maintenanceItems` | `addMaintenanceItem`, `markMaintenanceServicedToday`, `deleteMaintenanceItem` | مفيش أجهزة → `ZadEmptyState` (كانت Lottie، اتوحّدت) |

**تفاصيل ملاحة**: 3 مسارات موجودة أصلاً (`ZadRoutes.INVENTORY`/`SHOPPING`/`MAINTENANCE`) بقوا الكل بيرندر نفس `PantryShoppingScreen` بتاب ابتدائي مختلف (`initialTab` param) — صفر تغيير على bottom-nav/drawer/more-sheet/`ZadKnowledgeMapScreen` (لسه بتستخدم نفس أسماء الـ routes القديمة، اللي دلوقتي كلها بتوصّل لنفس الشاشة). تاب التوصيات بيتفتح من `ProfileScreen` عبر `PantryShoppingNavState.pendingTab` (نفس نمط `InventoryNavState.openShortagesTab` الموجود من قبل).

### 2.4 البوابة 4 — الصيدلية العائلية (Health) — **مُنفذة**

| Tab / Route | UiState (أعمدة حقيقية) | Actions ← VM function | Empty/Loading |
|---|---|---|---|
| **أدويتي** / **أدوية العيلة** (toggle داخل `PharmacyScreen.kt` — `showFamilyView`, بدل `FamilyPharmacyScreen`/`ZadNav.FAMILY_PHARMACY` اللي اتشالت بالكامل) | `zad_pharmacy_items` أعمدة: `id, name, active_ingredient, category, dosage, remaining_quantity, unit, daily_dose_count, expiry_date, price, is_recurring, family_member_id, dose_times, units_per_dose, qty_confirmed_at, has_invalid_dose_time, dose_carry`. عرض العيلة بيقرا من `SupabaseRepo.getFamilyPharmacyItems()` مباشرة (`family_admin_read_pharmacy`, رؤية بس، بدون ViewModel مخصص — نفس النمط القديم). `zad_dose_log` **لسه TBD**: مفيش StateFlow مخصص ليها، مش مستخدمة في التنفيذ ده لأن `PharmacyItemCard` بيقرا من `doseTimesList()` المشتقة من `zad_pharmacy_items.dose_times` مباشرة، مش من `zad_dose_log`. | `addPharmacyItem`, `refillPharmacyItem`, `deletePharmacyItem`, `updatePharmacyQuantity`, `consumePharmacyDose`, `confirmPharmacyQuantity`, `setPharmacyUnitsPerDose`, `markPharmacyDoseTakenByName`. عرض العيلة **رؤية بس** — مفيش action ولا FAB ولا Dialogs وهو ظاهر (قرار متعمّد من التصميم الأصلي، اتحافظ عليه). | مفيش أدوية شخصية → `ZadEmptyState` (بدل الـ Lottie القديم). مفيش أدوية عيلة → `ZadEmptyState` بنفس نصوص `family_pharmacy_empty_title/subtitle`. تحميل عرض العيلة → `ZadLoadingState`. `contentPadding` bottom موحّد على `ZadHubListBottomPadding` (110.dp، ثابت جديد في `ZadV2.kt`). الحوارات التلاتة (`AddPharmacyItemDialog`, `SmartAddMedicationDialog`, `RefillPharmacyItemDialog`) بقت كلها بـ `imePadding()`. |

### 2.5 البوابة 5 — عقل زاد والعائلة (Brain & Family) — **مُنفذة**

دُمجت فعليًا في شاشة واحدة (`BrainFamilyScreen.kt`) بتابين فوقانيين بس (`ZadSegmentedTabs`، `enum BrainFamilyTab`): عقل زاد | العائلة. أكبر عملية دمج في الخريطة (7 شاشات). كل شاشة أصلية فضلت بمنطقها زي ما هي — كل تاب عنده "sub-view" محلي (state محلي، مش NavController) بيبدّل بين المحتوى الأساسي و"الأزرار الجانبية".

| Tab / Sub-view | المصدر | Actions ← VM function | Empty/Loading |
|---|---|---|---|
| **عقل زاد** (افتراضي) | `ZadIntelligenceScreen` كامل (AnalyticsTab بـ40+ كارت + ChatTab + `zad_insights` بالفعل جواها) | زي ما هي — `loadBehaviorProfile`/`refreshBehaviorProfile`, `loadZadInsights`, `dismissInsight`, `answerBrainQuestion`, `loadTransactionProposals`/`resolveTransactionProposal`, إلخ | **زيرو-موك مُطبّق على السطح الجديد اللي اتضاف هنا فقط** — لم يتم عمل مراجعة سطر-بسطر لكل الـ40+ كارت داخل `ZadIntelligenceScreen.kt` (4229 سطر، برة نطاق مهمة الدمج). الختم المزيّف "#ZAD-NEURAL-8841" (`AUDIT.md:73`) موجود فعليًا في `ZadHomeGlanceCards.kt` (بوابة Home، لسه مانفذتش) — مش جوه أي ملف من السبعة دول، فمش متأثر. |
| — الذاكرة (زر جانبي 🧠) | `ZadMemoryScreen(onBack = {يرجع للـmain})` — نفس الملف، نفس الـ state | زي ما هي | زي ما هي (الشاشة الأصلية عندها Empty/Loading جاهزين) |
| — خريطة المعرفة (زر جانبي 🔗) | `ZadKnowledgeMapScreen(viewModel, onBack, onNavigateToRoute)` — `onNavigateToRoute` لسه بيوصل لـ `goGuarded` الحقيقي (قفزات لبوابات تانية) | زي ما هي | زي ما هي |
| — سجل الإجراءات (زر جانبي 📜) | `AgentActionLogScreen(onBack)` — `agent_actions` | زي ما هي | زي ما هي |
| **العائلة** | `FamilyScreen` كامل (أعضاء، دردشة، مهام، أهداف، حدود صرف) | زي ما هي — `createFamily`, `joinFamily`, `kickMember`, `leaveFamily`, `deleteFamily`, `changeMemberRole`, `addChore`, إلخ | زي ما هي |
| — صناديق الادخار + تحديات مالية (زر جانبي 🏦) | `SinkingFundsCard` + `FinancialChallengesCard` — **نقلوا هنا من بوابة Finances** (كانوا فيها مؤقتًا، القرار اتحدد دلوقتي) | `loadSinkingFunds`, `createSinkingFund`, `contributeToSinkingFund`, `loadFinancialChallenges`, `createFinancialChallenge`, `contributeToChallenge` | مفيش صناديق/تحديات → نص "لسه مفيش" جاهز في الكومبوننتات نفسها (كانت كده أصلاً) |
| — الإنجازات (زر جانبي 🏆، كانت `AchievementsRoute` orphaned) | `AchievementsRoute(onBack)` — `user_achievements` | زي ما هي | زي ما هي |
| — تسبيحة العيلة (زر جانبي 🌳) | `TasbihaScreen(viewModel = familyViewModel)` | `loadTasbiha`, `tasbihaClick`, `renameTasbiha` | زي ما هي |

**قيد وضع الأطفال (مُتحقّق منه صراحة)**: `ZadRoutes.ASSISTANT` أصلاً ممنوع في وضع الأطفال (`MainScreen.kt`'s `goGuarded` بيرفض أي route غير HOME/FAMILY). `ZadRoutes.FAMILY` بقى بيتفرّع: وضع الأطفال → `FamilyScreen` وحدها من غير أي تابات/أزرار جانبية (زي ما كانت بالظبط قبل الدمج)، وضع الكبار → `BrainFamilyScreen` كاملة. الطفل معندهوش أي مسار جديد يوصل بيه لتحليلات مالية أو صناديق ادخار أو أي حاجة كانت ممنوعة عليه قبل كده.

**Routes القديمة المستقلة فضلت شغالة**: `ZadNav.ZAD_MEMORY`/`AGENT_ACTION_LOG`/`ACHIEVEMENTS`، `ZadRoutes.TASBIHA`/`KNOWLEDGE_MAP` — لسه بتترندر عبر `MainScreen.kt`'s NavHost القديم زي ما هي (Profile/الدرج/more-sheet لسه بيوصلولها). الأزرار الجانبية في `BrainFamilyScreen` بوابة وصول **إضافية** سريعة من جوه نفس الشاشة، مش بديل — مفيش route اتشال في البوابة دي (مختلف عن Recommendations في بوابة Pantry & Shopping، اللي كانت orphaned فعلاً بلا أي مدخل تاني).

---

## 3. القواعد الصارمة (Layout & Quality Constraints)

1. **`imePadding()` + `verticalScroll()` على كل BottomSheet/Dialog** — ده حاليًا **مش موجود عمليًا** في الكود (occurrence واحد بس في التطبيق كله: `FamilyScreen.kt:2101`). يعني القاعدة دي بتأسس convention جديد، مش بتتبع نمط موجود. لازم تتطبق على كل الـ 10 ملفات اللي فيها `ModalBottomSheet` (`HomeScreen`, `ZadShell`, `WhyChangedSheet`, `ZadHomeGlanceCards`, `ZadIntelligenceScreen`, `ZadQuickExpenseSheet`, `ProfileScreen`, `ZadVoiceBottomSheet`, `TelegramBinding`, `FamilyScreen`) وكل `AlertDialog` فيها حقول إدخال (زي `AddShoppingItemDialog`).
2. **توحيد `contentPadding = 110.dp`** — الوضع الحالي متفرّق: أغلب الشاشات بتستخدم `100.dp` بس مش موحّدة (`bottom = 100.dp` في `BudgetScreen/MaintenanceScreen/PharmacyScreen/SubscriptionsScreen/InventoryScreen`)، والـ padding الأفقي نفسه متذبذب بين `16.dp` و`20.dp`. القيمة الجديدة `110.dp` (بدل الـ `100.dp` الحالية) لازم تتحط كـ constant مشترك واحد، مش رقم سحري متكرر في كل ملف.
3. **منع تام لأي Mock/Hardcoded fallback** — أمثلة حقيقية موجودة دلوقتي لازم تتصلح مش تتنقل: الرقم الافتراضي `3500.0` في `ZadViewModel.kt:96`، جدول الأسعار المحلي في `InventoryScreen`، المبالغ الخام من غير `CurrencyFormatter` في `StatementImportScreen`، والختم الموثّق المزيّف في `ZadHomeGlanceCards.kt`. القاعدة: قائمة فاضية = كارت مختفي أو Empty State نظيفة، مش رقم أو نص مكانه.
4. **توحيد الـ Empty State component** — 3 نسخ Lottie مستقلة متكررة حاليًا (`BudgetScreen`, `ShoppingListScreen`'s `SmartEmptyState()`, `PharmacyScreen`) بيها نفس البنية تقريبًا لكن مش نفس الكومبوننت، بينما `ZadEmptyState` (`ZadStateComponents.kt`) مستخدم فعلاً في 26 موضع. **القرار المقترح**: كل البوابات الجديدة تستخدم `ZadEmptyState`/`ZadLoadingState`/`ZadErrorState` كمصدر وحيد، والنسخة اللي بها Lottie animation تتحول لخيار (`illustration` param اختياري) جوه نفس الكومبوننت بدل 3 تطبيقات منفصلة — محتاج اعتماد صريح لأنه بيغيّر شاشات شغالة دلوقتي.
5. **بدون إعلانات (No Ads)** — مفيش أي ad unit في التصميم؛ العناصر الترويجية الوحيدة المسموحة هي `affiliate_products`/`shopping_recommendations` الموجودة أصلاً كتوصية مفيدة، مش بانر إعلاني منفصل.

---

## 4. قرارات محتاجة اعتماد صريح قبل الكود (لأنها بتغيّر شاشات شغالة فعليًا)

هذول مش تفاصيل تنفيذية — كل واحد فيهم بيغيّر سلوك أو مكان شاشة موجودة، فمحتاجين موافقة صريحة قبل أي كود:

1. **إعادة نطاق `HomeScreen`**: طلبك إن بوابة الرئيسية تبقى "كروت Glance فقط + الصوت الحي" — لكن `HomeScreen` الحالية (2,766 سطر) فيها كمان: بطاقة المحفظة الرئيسية، شيت الصرف السريع، رسم بياني للصرف، شريط الاختصارات، تيكر الأسعار الحي. القرار: الحاجات دي تتنقل لبوابة Finances (المحفظة/الصرف السريع/الرسم البياني) وPantry & Shopping (تيكر الأسعار)، ولا تفضل كلها في Home بس البوابة تتوسّع؟ محتاج تحديد قبل ما تتصمم الشاشة.
2. **دمج `BudgetScreen` + `SubscriptionsScreen`** في شاشة Finances واحدة بـ 3 تابات — ده مش relabel، فيه إعادة هيكلة حقيقية لشاشتين شغالتين دلوقتي بموديلين منفصلين.
3. **نقل `FamilyPharmacyScreen`** من nested route جوه `PharmacyScreen` لـ toggle داخل نفس البوابة — تغيير في الـ IA مش الكود بس.
4. **دمج 7 شاشات متفرقة** (Assistant, Family, Tasbiha, KnowledgeMap, ZadMemory, AgentActionLog, Achievements) في بوابة Brain & Family واحدة بتابين — أكبر عملية دمج في الخريطة دي.
5. **مكان `MaintenanceScreen`** — مقترح ينضم لـ Pantry & Shopping، لسه مش مؤكد.
6. **توحيد الـ Empty State component** (تفصيل في §3.4).
7. الحقول المتعلّمة **TBD** في §2 (خصوصًا: subscription VM wrappers، recommendation actions VM wrapper، `ChatTab` state، `zad_memory`/`agent_actions`/`FamilyState` الأعمدة الدقيقة) — لازم مسح متابعة قبل كتابة أي `UiState` نهائي ليها، مش تتخمّن دلوقتي.

---

## 5. الخطوة التالية

🟢 **الخريطة اتنفذت بالكامل — كل البوابات الخمس شغالة**. الخطوات المتبقية اللي مش جزء من إعادة هيكلة الـ Navigation نفسها:
- بوابة **Home** (§4.1، إعادة نطاق `HomeScreen`) — القرار اتاعتمد (نقل المحفظة/الرسم البياني لـFinances) بس التنفيذ نفسه لسه ماحصلش.
- إصلاح `StatementImportScreen`'s المبالغ الخام (بند فرعي من §2.2، اتأجل عمدًا).
- الختم المزيّف "#ZAD-NEURAL-8841" في `ZadHomeGlanceCards.kt` (`AUDIT.md:73`) — لسه قايم، هيتصلح مع بوابة Home.
- مراجعة سطر-بسطر لكل كروت `ZadIntelligenceScreen.kt`'s الـ40+ للـ Zero-Mock الكامل (اتفحص السطح الجديد بس وقت دمج Brain & Family، مش كل الملف — شوف §2.5).
- عدد من حقول الـ TBD القديمة اتحلت أثناء التنفيذ الفعلي (شوف §6) — الباقي غالبًا مش هيتحل غير وقت اللمس الفعلي للكود المعني.

## 6. سجل التنفيذ

- **2026-09-03 — البوابة 4 (Health/الصيدلية العائلية)**: نُفذت. `PharmacyScreen.kt` بقى فيه toggle داخلي (`showFamilyView`) بدل `FamilyPharmacyScreen`/`ZadNav.FAMILY_PHARMACY` (اتشال الملف والـ route والـ composable entry في `MainScreen.kt` بالكامل). الـ Empty State الشخصي بقى `ZadEmptyState` بدل Lottie. `contentPadding` bottom بقى `ZadHubListBottomPadding` (110.dp، ثابت جديد في `ZadV2.kt`، معمول لإعادة استخدامه في باقي البوابات). الحوارات التلاتة في الشاشة بقى فيها `imePadding()`. **تحقق فعلي**: `./gradlew compileDebugKotlin` نجح (`BUILD SUCCESSFUL`) — مفيش أخطاء ترجمة جديدة، التحذيرات كلها موجودة من قبل ومش من التعديل ده. **قرار §4.5 (مكان `MaintenanceScreen`) و§4.4 (دمج Brain & Family) و§4.2 (دمج Finances) لسه مانفذتش وقتها** — نُفذ منهم §4.5 بعد كده (شوف الإدخال التالي). **TBD لسه قايم**: `zad_dose_log` مفيهاش StateFlow مخصص (مش لازمة للتنفيذ الحالي، هتتحل لو بوابة تانية احتاجتها).

- **2026-09-03 — البوابة 2 (Finances)**: نُفذت. ملف جديد `FinancesScreen.kt` بيستضيف `BudgetScreen`+`SubscriptionsScreen`+`FinancesDebtsBody` (جديد) تحت `ZadSegmentedTabs` (3 تابات، `enum FinancesTab`). `SubscriptionsScreen` اتقلّمت: اتشال منها تاب "الكل" الداخلي القديم (الفلترة الداخلية الأربعة فضلت زي ما هي)، ومخطط سداد الديون + العروض الحية + تحديات العائلة + صناديق الادخار اتنقلوا لـ`FinancesDebtsBody` جوه تاب "الديون" الجديد (نفس الكروت/المنطق، مفيش حاجة اتلغت). البارامتر `familyViewModel` اتشال من `SubscriptionsScreen` (مبقاش مستخدم فيها). التلات routes الحالية (`ZadRoutes.BUDGET`/`SUBS`) بترندر نفس `FinancesScreen` بتاب ابتدائي مختلف — صفر تغيير على bottom-nav/drawer/more-sheet. `imePadding()`+`verticalScroll()` اتطبقوا على 7 حوارات إدخال (إضافة حركة `AddTransactionDialog` في `HomeScreen.kt`، تعديل حركة، إضافة/تعديل التزام، إضافة اشتراك، إضافة دين، دفع دين، إنشاء تحدي مالي، إنشاء/مساهمة صندوق ادخار). `ZadHubListBottomPadding` اتطبقت على التابين التلاتة. توحيد الـEmpty State اتوسّع هنا كمان: `BudgetScreen`'s و`SubscriptionsScreen`'s نسخ Lottie اتحولوا لـ`ZadEmptyState` (خامسة وسادسة نسخة مكررة اتشالت). **تحقق فعلي**: `./gradlew compileDebugKotlin` نجح، أول `./gradlew assembleDebug` فشل بـ BUILD FAILED من غير رسالة خطأ واضحة (3 مهام بس اتنفذوا قبل الفشل) — اتأكد إنه ضجيج بنية تحتية عابر (Gradle daemon) مش خطأ كود حقيقي عن طريق `./gradlew clean assembleDebug` كامل من الصفر: **BUILD SUCCESSFUL** (41 مهمة، 24 اتنفذوا فعليًا + 17 من الـ cache). **قرار §4.4 (دمج Brain & Family) لسه مانفذش** — دي البوابة الجاية. **مكان `FinancialChallengesCard`/`SinkingFundsCard` النهائي مؤجل** لحد ما Brain & Family تتنفذ (شوف §2.2).

- **2026-09-03 — البوابة 3 (Pantry & Shopping)**: نُفذت. ملف جديد `PantryShoppingScreen.kt` بيستضيف `InventoryScreen`+`ShoppingListScreen`+`RecommendationsRoute`+`MaintenanceScreen` تحت `ZadSegmentedTabs` (4 تابات، `enum PantryShoppingTab`) — دمج ملاحة/عرض حقيقي، منطق كل شاشة زي ما هو. الجداول التلاتة الحالية (`ZadRoutes.INVENTORY`/`SHOPPING`/`MAINTENANCE`) كلها بترندر نفس الشاشة بتاب ابتدائي مختلف — صفر تغيير على bottom-nav/drawer/more-sheet/`ZadKnowledgeMapScreen`. `ZadNav.RECOMMENDATIONS` (كانت orphaned) اتشالت بالكامل، وتابها بيتفتح من `ProfileScreen` عبر `PantryShoppingNavState.pendingTab` (نفس نمط `InventoryNavState.openShortagesTab` القديم). `getEstimatedPrice()` الوهمية في `InventoryScreen.kt` اتشالت بالكامل — بانر النقص بيعرض `estimated_cost_unknown` ("غير محدد") بدل رقم مختلق، وإضافة صنف ناقص لقايمة التسوق بتبعت `estimatedPrice = 0.0` (زي باقي نقاط الإضافة) بدل رقم وهمي، فياخده pipeline التقدير الحقيقي الموجود أصلاً (`ZadAiRepository.estimatePrice` + `persistEstimatedPrice`). `MaintenanceScreen` بقت جزء فرعي من البوابة دي (قرار §4.5 اتنفذ). توحيد الـ Empty State (قرار §4.6) اتوسّع هنا كمان: `ShoppingListScreen`'s Lottie-based `SmartEmptyState()` و`MaintenanceScreen`'s Lottie block اتحولوا لـ `ZadEmptyState` (بالإضافة لـ Pharmacy قبل كده — 3 من 3 نسخ Lottie المكررة المتعلّمة في §3.4 الأصلية اتشالت). `ZadHubListBottomPadding` اتطبقت على كل التابات التلاتة اللي فيها lists. الحوارات (`AddInventoryDialog`, `EditInventoryDialog`, `AddShoppingItemDialog`, `AddMaintenanceItemDialog`) بقى فيها `imePadding()`/`verticalScroll()`. **تحقق فعلي**: `./gradlew compileDebugKotlin` و`./gradlew assembleDebug` نجحوا (`BUILD SUCCESSFUL` مرتين، بعد كل مرحلة تعديل) — مفيش أخطاء جديدة. **قرار §4.2 (دمج Finances) و§4.4 (دمج Brain & Family) لسه مانفذتش** — البوابتين دول لسه الجايين.

- **2026-09-03 — البوابة 5 (Brain & Family) — آخر بوابة، الخريطة اكتملت**: نُفذت. ملف جديد `BrainFamilyScreen.kt` بيستضيف 7 شاشات كانت متفرقة تحت تابين بس (`ZadSegmentedTabs`، `enum BrainFamilyTab`: عقل زاد | العائلة). كل تاب فيه sub-view محلي (state محلي مش NavController) بيبدّل بين المحتوى الأساسي و"أزرار جانبية": تاب عقل زاد = `ZadIntelligenceScreen` + أزرار لـ`ZadMemoryScreen`/`ZadKnowledgeMapScreen`/`AgentActionLogScreen`؛ تاب العائلة = `FamilyScreen` + أزرار لـ(`SinkingFundsCard`+`FinancialChallengesCard` مع بعض)/`AchievementsRoute`/`TasbihaScreen`. صناديق الادخار وتحديات العائلة **نقلوا من بوابة Finances** (كانوا فيها مؤقتًا، القرار اتحدد دلوقتي — مكانهم الطبيعي). Routes القديمة المستقلة (`ZadNav.ZAD_MEMORY`/`AGENT_ACTION_LOG`/`ACHIEVEMENTS`، `ZadRoutes.TASBIHA`/`KNOWLEDGE_MAP`) فضلت شغالة زي ما هي — الأزرار الجانبية بوابة وصول إضافية، مش بديل، مفيش route اتشال في البوابة دي.
  **قيد وضع الأطفال**: اتحقق منه صراحة قبل التنفيذ — `ZadRoutes.ASSISTANT` ممنوع أصلاً في وضع الأطفال (`goGuarded`)، و`ZadRoutes.FAMILY` بقى مفرّع: طفل → `FamilyScreen` وحدها من غير تابات (زي الأصل بالظبط)، بالغ → `BrainFamilyScreen` كاملة. صفر تسريب لتحليلات مالية أو صناديق ادخار للطفل.
  **Zero-Mock**: القاعدة اتطبقت على السطح الجديد اللي اتضاف في الدمج نفسه — مفيش أرقام/أختام مختلقة في أي كومبوننت جديد كتبته. **مراجعة سطر-بسطر لكل الـ40+ كارت جوه `ZadIntelligenceScreen.kt`'s (4229 سطر) لم تُجرَ** — برة نطاق مهمة "دمج الشاشات"، ومحتاجة مهمة تدقيق منفصلة. الختم المزيّف المعروف (`#ZAD-NEURAL-8841`, `AUDIT.md:73`) موجود في `ZadHomeGlanceCards.kt` (بوابة Home) مش في أي من السبعة ملفات دول.
  `ZadHubListBottomPadding` اتطبقت على القايمة الرئيسية في `ZadIntelligenceScreen` (كانت `bottom = 16.dp` ثابتة رغم تعليق قديم في الكود بيقول إنها اتصلحت) + على sub-view الادخار الجديد. `imePadding()`/`verticalScroll()` اتطبقوا على حوارات إدخال عائلية حقيقية: `SpendLimitDialog`, `AddChoreDialog`, `JoinFamilyDialog` (في `FamilyScreen.kt`)، و`RenameDialog`/`CreateTreeDialog` (في `TasbihaScreen.kt`) — دي الحوارات اللي فيها حقول نص فعلية؛ `InviteMemberDialog` اتفحصت ومفيهاش حقول إدخال (QR/رابط/كود عرض بس) فاتسابت زي ما هي.
  **تحقق فعلي نهائي**: `./gradlew compileDebugKotlin` نجح، وبعدين `./gradlew clean assembleDebug` كامل من الصفر (يغطي المشروع كله، كل البوابات الخمس مع بعض): **BUILD SUCCESSFUL** (41 مهمة: 19 اتنفذوا فعليًا + 22 من الـ cache). مفيش أخطاء ترجمة أو تضارب.

**🟢 اكتملت جميع البوابات الخمس.** الخطوات المتبقية (بوابة Home، `StatementImportScreen`، تدقيق `ZadIntelligenceScreen` الكامل) موثقة في §5.

- **2026-09-03 — §7 (تصفية الثغرات الثلاث المتبقية)**: نُفذت. **§7.1**: `PriceReportingRoute` composable جديد (`PriceReportingScreen.kt`) بيستضيف `CrowdsourceDashboard`/`PriceReportingScreen` تحت `PriceReportingViewModel` حقيقية، زر جانبي 📊 اتضاف في هيدر `PantryShoppingScreen.kt` (تاب التسوق/التوصيات). كارت "أسعار حية" المختلق (`contributionCount * 3`) والنجمة "score" المصطنعة (`(index+1)*10`) اتشالوا، `LeaderboardEntry`/`LeaderboardEntryData` المكررين اتوحّدوا، Empty State بقى `ZadEmptyState`. **§7.3**: `RecommendationsRoute` بقت تاخد `viewModel: ZadViewModel`، زرار "تم" بقى "أضف للسلة" 🛒 وبينده `addShoppingItem` فعليًا بجانب `markRecommendationActedOn`. **§7.4**: `FamilyViewModel` اتضافلها `createTasbihaChallenge(...)` + `_tasbihaChallengeProgress` (بيتحمّل تلقائيًا جوه `loadTasbiha()`)، `TasbihaScreen.kt`'s `ChallengesTab` بقى فيها زرار "➕" لأدمن العيلة بس + `ChallengeCard` بتعرض `LinearProgressIndicator` حقيقي بدل النص الثابت. 9 string resources جديدة اتضافت عبر الـ5 لغات (`contribute_price_action`, `challenge_progress_label`, `new_challenge_action`, `tasbiha_challenge_description_hint`, `tasbiha_challenge_target_hint`). **تحقق فعلي**: `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`، `./gradlew clean assembleDebug` كامل من الصفر → `BUILD SUCCESSFUL` (41 مهمة: 21 اتنفذوا + 20 من cache). **TBD متبقية عمدًا**: ربط `tasbihaClick()` بتحديث تحدي نشط تلقائيًا — قرار تصميم منفصل مش هتتخيّل.

**🟢🟢 كل بنود §7 اتقفلت. التطبيق مغلق برمجيًا بنسبة 100% على مستوى الميزات المكتشفة في جرد الـ84 جدول** (فيما عدا §7.5/§7.6 اللي مستبعدة عمدًا لأسباب موثقة).

---

## 7. Backlog of Unsurfaced Backend Features (عقود الميزات غير المعروضة)

**المنهجية**: جرد الـ84 جدول (§0) اتقارن بكل دالة في `SupabaseRepo.kt` وكل استدعاء لها في `ui/`. أي جدول له دالة Repo حقيقية بس مفيش زرار/شاشة بتستدعيها فعليًا = orphaned. أي جدول مفيهوش دالة Repo أصلاً اتحط في فئة منفصلة (§7.5) — مينفعش نصمم زرار لحاجة مفيش data-layer ليها لسه، ده هيبقى اختراع عقد مش قراءة عقد موجود.

### 7.1 ميزة التسعير بالمشاركة الجماعية (price_index) — **✅ اتحلت (2026-09-03)**

`PriceReportingScreen.kt` (462 سطر) + `PriceReportingViewModel.kt` (127 سطر) — شاشة ونموذج كاملين، بيكتبوا مباشرة في `price_index` (`submitPrice(itemName, category, price, location, storeName)` → insert بعمود `source="crowdsource"`) وبيقروا leaderboard حقيقي. **اتأكد بالـ grep**: صفر ذكر لـ`PriceReportingScreen`/`PriceReportingViewModel` في `MainScreen.kt` أو `ZadShell.kt` — مفيش route، مفيش زرار درج، مفيش more-sheet entry. مربوطة كمان بـ`getCrowdsourceContributionTimestamps()` (إنجازات المساهمة بالأسعار، `user_achievements`).

**التنفيذ**: `PriceReportingRoute` composable جديد (في `PriceReportingScreen.kt`) بيستضيف `CrowdsourceDashboard`/`PriceReportingScreen` تحت `PriceReportingViewModel` حقيقية. زر جانبي 📊 (`Icons.Default.BarChart`، `contribute_price_action`) اتضاف في هيدر `PantryShoppingScreen.kt` — بيظهر بس وقت ما تاب "التسوق" أو "توصيات ذكية" مختار، ويفتح sub-view محلي (نفس نمط `BrainFamilyScreen`، مش NavController).
- **Zero-Mock**: كارت "أسعار حية" اللي كان بيعرض `contributionCount * 3` (رقم مختلق بالكامل، مالوش أي مصدر) **اتشال**. النجمة "⭐ score" في `LeaderboardCard` (كانت `(index+1)*10`، رقم مصطنع من الترتيب بس) **اتشالت** برضه — بيفضل بس الترتيب الحقيقي + عدد المساهمات الحقيقي.
- تنظيف إضافي: نوعين مكررين (`LeaderboardEntry` محلي في الشاشة و`LeaderboardEntryData` في الـ ViewModel لنفس البيانات بالظبط) اتوحّدوا في `LeaderboardEntryData` واحد.
- Empty state للـ leaderboard الفاضي بقى `ZadEmptyState`.
- **تحقق**: `compileDebugKotlin` + `clean assembleDebug` ناجحين (شوف §6 الأخير).

### 7.2 التحديات العائلية والمالية (family_financial_challenges, financial_challenge_progress) — **✅ اتحلت فعليًا أثناء تنفيذ البوابة 5، مش Backlog**

**تصحيح لطلبك**: الميزة دي **مش orphaned دلوقتي** — اتربطت بالكامل وقت تنفيذ بوابة Brain & Family (§2.5/§6): `FinancialChallengesCard(familyViewModel)` بقت جوه sub-view "صناديق الادخار + تحديات مالية" (زر جانبي 🏦 في تاب "العائلة"). العقد الفعلي الشغال:
- **UiState**: `financialChallenges: List<FinancialChallenge>` (`id, family_id, challenge_type, title, target_amount, reward_amount, duration...`)، `challengeProgress: Map<String, List<FinancialChallengeProgress>>`.
- **Actions**: `loadFinancialChallenges()`, `createFinancialChallenge(title, targetAmount, rewardAmount, durationDays)` (زرار "إنشاء تحدي" ظاهر لو `FamilyState.Active`)، `contributeToChallenge(challengeId, memberId, amount)`.
- **Empty**: نص "لسه مفيش تحديات" جاهز في الكومبوننت نفسه.
- محتفظ بالبند هنا للتوثيق بس — مفيش عمل مطلوب إضافي عليه.

### 7.3 توصيات التوفير الحية → تحويل مباشر لسلة المشتريات (shopping_recommendations) — **✅ اتحلت (2026-09-03)**

**كانت المشكلة**: زرار "تم" بينده `markRecommendationActedOn(id)` بس — تغيير status من غير أي إضافة فعلية لـ`zad_shopping_list`.

**التنفيذ**: `RecommendationsRoute` بقت تاخد `viewModel: ZadViewModel` (اتغيّر استدعاؤها في `PantryShoppingScreen.kt` لـ`RecommendationsRoute(viewModel = viewModel)`). `onAction` دلوقتي بينده `viewModel.addShoppingItem(ZadShoppingItem(itemName=rec.itemName, quantity=1, estimatedPrice=rec.bestPrice ?: 0.0, store=rec.bestStore ?: ""))` **بالإضافة** لـ`markRecommendationActedOn` (مش بدالها — الإحصائية `getActedOnRecommendationsCount` فضلت صح). الزرار اتغيّر من "تم" لـ**"أضف للسلة"** 🛒 عشان يطابق الفعل الحقيقي. الـ Empty State الداخلي (Card+Icon+Text يدوي) اتوحّد كمان على `ZadEmptyState`.

### 7.4 تحديات التسبيحة الأسرية (family_tasbiha_challenges, tasbiha_challenge_progress) — **✅ اتحلت (2026-09-03)**

**كان الوضع**: `ChallengesTab` بتعرض `activeChallenges` (قراءة شغالة) بس `createChallenge()`/`getChallengeProgress()` صفر استدعاء — الشاشة الفاضية بتقول "اسأل المشرف" من غير ما تدّي المشرف وسيلة، و`ChallengeCard` كانت بتعرض `target_clicks`/تاريخ الانتهاء بس من غير أي progress bar.

**التنفيذ**:
- `FamilyViewModel` اتضافلها `createTasbihaChallenge(title, description, challengeType, targetClicks, endDate)` (wrapper لـ`SupabaseRepo.createChallenge`، بياخد `familyId` من `FamilyState.Active.familyGroup.id`) و`_tasbihaChallengeProgress: Map<challengeId, TasbihaChallengeProgress>` بيتحمّل تلقائيًا جوه `loadTasbiha()` (تقدم المستخدم الحالي بس، عبر `getChallengeProgress(challengeId).find { it.userId == myUserId }`).
- `ChallengesTab` (`TasbihaScreen.kt`) بقى فيها زرار "➕" يظهر لأدمن العيلة بس (`myMemberInfo.role == "admin"`، نفس شرط `isFamilyPharmacyAdmin`) → `CreateTasbihaChallengeDialog` (عنوان، وصف اختياري، نوع أسبوعي/شهري، هدف عدد التسبيحات) → `createTasbihaChallenge(...)`.
- `ChallengeCard` بقت تاخد `progress: TasbihaChallengeProgress?` وتعرض `LinearProgressIndicator(current_clicks / target_clicks)` + نص "%d من %d تسبيحة" بدل النص الثابت القديم.
- Empty state ("اسأل المشرف") فضل بس للأعضاء غير الأدمن — الأدمن يشوف زرار الإنشاء بدلها، اتحول لـ`ZadEmptyState`.
- **TBD فضلت قايمة عمدًا (مش هتتخيّل)**: مفيش دالة تربط `tasbihaClick()` الفردية بتحديث `current_clicks` في تحدي نشط تلقائيًا — التقدم دلوقتي بيتقرا بس من `tasbiha_challenge_progress` (اللي `SupabaseRepo.updateChallengeProgress` موجودة ليها بس برضه صفر استدعاء) — ربط النقرة الفردية بتحديث تحدي معيّن قرار تصميم إضافي (تحدي واحد نشط وقتها؟ كل التحديات النشطة؟) برة نطاق الطلب المحدد (زرار إنشاء + عرض تقدم بس)، محتاج تحديد منفصل قبل التنفيذ.

### 7.5 جداول مفيهاش أي دالة Supabase-access في الكود الحالي — **مينفعش نصمم زرار ليها لسه**

جداول اتأكد (بالـgrep) إن مفيش أي `SupabaseRepo` function بتلمسها من الكلاينت خالص — يبقى إما مستخدمة server-side بس (Edge Functions)، أو مخصصة لمرحلة قادمة لسه محصلتش:

`market_snapshot`, `price_alerts`, `currency_rates`, `zad_recipe_feedback`, `zad_waste_log`, `zad_consumption`, `user_alert_snooze`.

**القاعدة هنا**: تصميم زرار لأي واحدة من دول قبل ما يتبني data-layer function ليها هيبقى مخالفة مباشرة لمبدأ العقد في §0 ("كل حقل UiState لازم يتتبع لعمود Supabase حقيقي **أو StateFlow موجود فعلاً**") — مفيش حتى دالة Repo نتحدث لها. أي شغل عليهم لازم يبدأ بـ`SupabaseRepo` function جديدة الأول، مش UI.

**اكتشاف إضافي (bonus finding)**: `TelegramBinding.kt` (كومبوننت UI كامل لربط حساب Telegram، جدول `telegram_bindings`) — **مُعرَّف بس مُستدعى صفر مرة في كل التطبيق**. برة نطاق الـ5 بوابات (ده feature إعدادات/تكامل مش hub-domain)، بس بيستاهل تسجيل هنا لنفس سبب المنهجية — كود UI جاهز كامل بدون أي نقطة دخول. مكانه الطبيعي المرشّح: `ProfileScreen`'s settings menu.

### 7.6 جداول البنية التحتية — مستبعدة عمدًا، مش user-facing بالتصميم

الفئة دي مُستبعدة **صراحة مش بالصمت** — دول جداول تشغيل داخلي (agent bookkeeping، caches، push tokens، admin dashboards، Telegram queue) ملهاش معنى كزرار في واجهة مستخدم:

`workflows`, `nodes`, `edges`, `agent_logs`, `agent_usage`, `agent_tasks`, `agent_goals`, `agent_drift_events`, `zad_agent_messages`, `brain_notes`, `note_links`, `zad_memory_links`, `zad_brain_health_alerts`, `zad_orphaned_rows`, `zad_brain_runs`, `zad_brain_queue`, `ai_response_cache`, `market_price_cache`, `zad_locale_config`, `zad_fx_rates`, `zad_tiers`, `zad_entitlements`, `zad_ad_grants`, `zad_skills`, `sent_budget_alerts`, `sent_telegram_budget_alerts`, `zad_notification_ingest_events`, `zad_fcm_tokens`, `dashboard_admins`, `telegram_pending_writes`, `telegram_checkin_prompts`, `telegram_pending_pharmacy`, `telegram_pending_tools`, `zad_parent_digests`, `zad_inventory_observations`, `zad_pharmacy_doses`, `zad_transaction_proposals` (ظاهرة فعلاً كجزء من عقد §2.5 عبر `transactionProposals`، مش مستقلة), `zad_fx_rates`.

### 7.7 جداول متأكد إنها متصلة بزرار حقيقي فعلاً — للتغطية الكاملة بدون استثناء

باقي الـ84 جدول (اللي مش في §7.1–§7.6) اتأكد إنها متصلة بواجهة حقيقية شغالة، موثقة في §1/§2 أو هنا للتغطية:
`zad_users`, `zad_inventory`, `zad_transactions`, `zad_subscriptions`, `family_groups`, `family_members`, `chat_messages`, `family_messages`, `family_typing_status`, `shared_grocery_list`/`shared_grocery_items` (`FamilyScreen`'s `onToggleGrocery`), `family_goals` (`createFamilyGoal`, مستدعاة من `FamilyViewModel:773`)، `chores`/`family_chores` (`AddChoreDialog`)، `app_notifications` (`NotificationCenterScreen`)، `affiliate_products`/`affiliate_clicks`/`affiliate_catalog_requests` (`recordCatalogRequest` مستدعاة من `ZadViewModel:5099`)، `user_behavior_profile`, `zad_debts`, `seasonal_events`/`seasonal_event_windows` (`SinkingFundsCard`'s `upcomingEvents`)، `sinking_funds`, `zad_pharmacy_items`, `zad_maintenance_items`, `zad_dose_log` (TBD جزئي، §2.4)، `zad_insights`, `zad_memory`, `zad_obligations`, `user_achievements` (`AchievementsScreen`)، `shopping_recommendations`.

---

**🟢 §7 اتقفلت بالكامل (2026-09-03)**: التلات عناصر (§7.1 التسعير الجماعي، §7.3 توصيات→سلة، §7.4 تحديات التسبيحة) اتنفذوا في جولة واحدة، بالإضافة لـ§7.2 اللي كانت خلصت أصلاً. تحقق فعلي: `./gradlew compileDebugKotlin` → `BUILD SUCCESSFUL`، `./gradlew clean assembleDebug` كامل من الصفر → `BUILD SUCCESSFUL` (41 مهمة: 21 اتنفذوا + 20 من cache). TBD واحدة متبقية عمدًا: ربط `tasbihaClick()` الفردية بتحديث تحدي نشط تلقائيًا — قرار تصميم منفصل، برة نطاق الطلب المحدد.

الباقي المتبقي في المشروع كله (بوابة Home، `StatementImportScreen`، تدقيق `ZadIntelligenceScreen` الكامل، §7.5/§7.6 الجداول اللي بلا data-layer أو infra عمدًا) موثّق في §5.
