package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Figure
import com.example.data.ZadInsight
import com.example.ui.components.GlassCard
import com.example.ui.components.ZadBottomNavBar
import com.example.ui.components.ZadCanvasBackground
import com.example.ui.components.ZadDrawerContent
import com.example.ui.components.ZadRoutes
import com.example.ui.components.ZadTopHeader
import com.example.ui.components.zadDrawerEntries
import com.example.ui.components.ZadCardHero
import com.example.ui.components.BudgetSetupPromptCard
import com.example.ui.components.ZadChefCard
import com.example.ui.components.TelegramBotCard
import com.example.ui.components.ZadDaysAndSafeSpendRow
import com.example.ui.components.PremiumTransactionsRow
import com.example.ui.components.ZadPageShortcutsGrid
import com.example.ui.components.ZadShortcutItem
import com.example.ui.components.PremiumTransactionsRow
import com.example.ui.components.ZadStatTile
import com.example.ui.widgets.ZadAmazonDealCard
import com.example.ui.screens.auth.OnboardingScreen
import com.example.ui.theme.*
import com.example.ui.widgets.ZadQuestionCard
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1080dp-h2400dp-xxhdpi")
class PreviewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureOnboardingScreen() {
        composeTestRule.setContent {
            AppTheme {
                OnboardingScreen(
                    onNavigateToLogin = {},
                    onNavigateToSignUp = {}
                )
            }
        }
        
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/onboarding_screen.png"
        )
    }

    /** بوت تليجرام بعد ما اتنقل من البروفايل للرئيسية. */
    @Test
    fun captureTelegramBotCard() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(20.dp)) {
                    TelegramBotCard(onClick = {})
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/telegram_bot_card.png"
        )
    }

    /** كارت شيف زاد — بيفتح الوصفة دلوقتي بدل ما يروح لعقل زاد. */
    @Test
    fun captureChefCardWithSuggestion() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(20.dp)) {
                    ZadChefCard(suggestion = "دجاج بالبطاطس بالفرن", onClick = {})
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/chef_card_suggestion.png"
        )
    }

    @Test
    fun captureZadQuestionCard_numberType() {
        composeTestRule.setContent {
            AppTheme {
                ZadQuestionCard(
                    insight = ZadInsight(
                        id = "preview-1",
                        kind = "question",
                        title = "كام كيلو رز فاضل؟",
                        body = "آخر مرة سجلت 5 كيلو، عايزين نحدث المخزون",
                        actionType = "number"
                    ),
                    onAnswer = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_question_card_number.png"
        )
    }

    @Test
    fun captureZadQuestionCard_yesNoType() {
        composeTestRule.setContent {
            AppTheme {
                ZadQuestionCard(
                    insight = ZadInsight(
                        id = "preview-2",
                        kind = "question",
                        title = "لسه بتاخد دوا الضغط؟",
                        body = "مبنيش على تذكير الصيدلية اللي فات",
                        actionType = "yes_no"
                    ),
                    onAnswer = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_question_card_yes_no.png"
        )
    }

    // Verbatim content of the first question zad-brain ever emitted from a real daily run
    // (zad_insights row 2312b7d4, dedupe_key=ask_eggs_qty) — kept as-is rather than
    // paraphrased so this capture proves what the user actually sees for that row.
    @Test
    fun captureZadQuestionCard_liveBrainQuestion() {
        composeTestRule.setContent {
            AppTheme {
                ZadQuestionCard(
                    insight = ZadInsight(
                        id = "2312b7d4-c87c-4a6f-97ca-e1a24f81c295",
                        kind = "question",
                        surface = "home_card",
                        title = "البيض",
                        body = "كم عدد البيض الذي لديك حاليًا؟",
                        actionType = "number",
                        aboutItem = "بيض"
                    ),
                    onAnswer = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_question_card_live_eggs.png"
        )
    }

    // Glassmorphism pass (iOS-design alignment task) — ZadCardHero is stateless (plain
    // params, no ViewModel), so it captures directly like the cards above.
    @Test
    fun captureZadCardHero_glassmorphism() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.padding(16.dp)) {
                    ZadCardHero(
                        spent = 1200.0,
                        remaining = 2300.0,
                        available = Figure(value = 2000.0, confident = true),
                        committed = 300.0,
                        monthlyLimit = 4000.0,
                        nextObligationText = "إيجار بعد 4 أيام"
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_card_hero_glass.png"
        )
    }

    // مرحلة ٥ب-١ — شريط التقدّم الجديد بألوانه الثلاثة (أخضر/كهرماني/أحمر) — لازم
    // نشوفهم فعلياً قبل الالتزام، مش نفترض إن الـ when() صح لمجرد إنه اتكتب صح.
    @Test
    fun captureZadCardHero_progressBar_danger() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.padding(16.dp)) {
                    ZadCardHero(
                        spent = 3800.0,
                        remaining = 200.0,
                        available = Figure(value = 200.0, confident = true),
                        monthlyLimit = 4000.0
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/zad_card_hero_progress_danger.png")
    }

    @Test
    fun captureZadCardHero_progressBar_warning() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.padding(16.dp)) {
                    ZadCardHero(
                        spent = 3000.0,
                        remaining = 1000.0,
                        available = Figure(value = 1000.0, confident = true),
                        monthlyLimit = 4000.0
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/zad_card_hero_progress_warning.png")
    }

    // السقف مش معروف — من غير monthlyLimit أصلاً، مفروض الشريط ميظهرش خالص (نسبة من صفر مالهاش معنى)
    @Test
    fun captureZadCardHero_noMonthlyLimit_hidesProgressBar() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.padding(16.dp)) {
                    ZadCardHero(
                        spent = 500.0,
                        remaining = 1500.0,
                        available = Figure(value = 1500.0, confident = true)
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/zad_card_hero_no_limit.png")
    }

    // Task 19.0 معيار ٦ — الحالة الفاضية (سقف لسه مش متحدد) لازم تحس إنها نفس عائلة
    // ZadCardHero البصرية بعد الـ glass pass، مش كارت من طابع مختلف.
    @Test
    fun captureBudgetSetupPromptCard_glassmorphism() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.padding(16.dp)) {
                    BudgetSetupPromptCard(onSetBudget = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/budget_setup_prompt_glass.png")
    }

    // مرحلة ٥ب-٢ — كروت اللوكيشن (docs/agent/PLAN_2026_08_06_rebuild.md): نفس عائلة
    // الزجاج البصرية (zadGlassBlur + 24dp) + دبوس مكان عائم بدل أيقونة مسطّحة.
    @Test
    fun captureLocationAlertsCard_glassmorphism() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    com.example.ui.components.LocationAlertsCard(dismissed = false, onDismiss = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/location_alerts_card_glass.png")
    }

    // Kids Mode crash fix — HomeScreen.kt wraps KidsModeContent in its own
    // Modifier.fillMaxSize().verticalScroll(...) Column. KidsModeContent used to add a
    // SECOND fillMaxSize().verticalScroll(...) Column inside that, which threw
    // "IllegalStateException: Vertically scrollable component was measured with an
    // infinity maximum height constraints" the moment it had enough content to need
    // scrolling (chores, messages). This reproduces that exact nesting — it would have
    // crashed before the fix, and composing + capturing without throwing is the proof.
    @Test
    fun captureKidsModeContent_noNestedScrollCrash() {
        val familyState = com.example.ui.viewmodels.FamilyState.Active(
            familyGroup = com.example.data.FamilyGroup(id = "fam1"),
            myMemberInfo = com.example.data.FamilyMember(id = "kid1", role = "child", alias = "سارة", balance = 45.0, savingsGoal = 100.0, dailyLimit = 20.0),
            members = listOf(com.example.data.FamilyMember(id = "kid1", role = "child", alias = "سارة")),
            messages = listOf(
                com.example.data.ChatMessage(id = "m1", senderId = "kid1", message = "أهلاً!"),
                com.example.data.ChatMessage(id = "m2", senderId = "kid1", message = "خلصت شغلي")
            ),
            groceries = emptyList(),
            goals = emptyList(),
            chores = listOf(
                com.example.data.Chore(id = "c1", assignedTo = "kid1", title = "رتب أوضتك", rewardAmount = 5.0, isCompleted = false),
                com.example.data.Chore(id = "c2", assignedTo = "kid1", title = "اعمل واجبك", rewardAmount = 3.0, isCompleted = true),
                com.example.data.Chore(id = "c3", assignedTo = "kid1", title = "اسقي النبات", rewardAmount = 2.0, isCompleted = false)
            )
        )
        composeTestRule.setContent {
            AppTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    KidsModeContent(
                        familyState = familyState,
                        onAddRequest = { _, _ -> }
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/kids_mode_content_no_crash.png")
    }

    @Test
    fun captureNearbyStoreCard_supermarket() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    NearbyStoreCard(
                        store = com.example.data.NearbyStore(name = "كارفور المرجان", lat = 24.7, lon = 46.6, distanceMeters = 450),
                        lowStockNames = listOf("حليب", "بيض"),
                        isPharmacy = false
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/nearby_store_card_supermarket.png")
    }

    @Test
    fun captureNearbyStoreCard_pharmacy() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    NearbyStoreCard(
                        store = com.example.data.NearbyStore(name = "صيدلية النهدي", lat = 24.7, lon = 46.6, distanceMeters = 1800),
                        lowStockNames = listOf("دواء الضغط"),
                        isPharmacy = true
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/nearby_store_card_pharmacy.png")
    }

    // مرحلة ٥ب-٣ — كارت المخزون التفاعلي (docs/agent/PLAN_2026_08_06_rebuild.md): نفس
    // عائلة الزجاج البصرية بخلفية كهرمانية + أزرار FilledTonalButton ملوّنة.
    @Test
    fun captureInventoryCheckInCard_glassmorphism() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    com.example.ui.components.InventoryCheckInCard(
                        candidate = com.example.data.InventoryFlowEngine.CheckInCandidate(
                            item = com.example.data.ZadInventory(itemName = "حليب المراعي", quantity = 1),
                            predictedDaysLeft = 0
                        ),
                        onDecrement = {},
                        onFinished = {},
                        onStillHave = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/inventory_checkin_card_glass.png")
    }

    // مرحلة ٥ب-٤ — شاشة الاشتراكات وأيقونات البراندات (docs/agent/PLAN_2026_08_06_rebuild.md):
    // 24dp + شارة برند عائمة ملوّنة (Netflix/Spotify) مقابل صنف مايتطابقش (رجوع للسلوك القديم).
    @Test
    fun captureSubscriptionCard_recognizedBrand() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    SubScreenSubscriptionCardFull(
                        sub = com.example.data.ZadSubscription(
                            title = "Netflix",
                            amount = 55.0,
                            renewalDate = LocalDate.now().plusDays(3).toString(),
                            isActive = true,
                            autoDeduct = true
                        ),
                        onToggleActive = {}, onToggleAutoDeduct = {}, onDelete = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/subscription_card_netflix.png")
    }

    @Test
    fun captureSubscriptionCard_unrecognizedBrand() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    SubScreenSubscriptionCardFull(
                        sub = com.example.data.ZadSubscription(
                            title = "اشتراك نادي القراءة",
                            amount = 30.0,
                            renewalDate = LocalDate.now().plusDays(15).toString(),
                            isActive = true,
                            autoDeduct = false
                        ),
                        onToggleActive = {}, onToggleAutoDeduct = {}, onDelete = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/subscription_card_generic.png")
    }

    // مرحلة ٥ب-٥ — البروفايل (docs/agent/PLAN_2026_08_06_rebuild.md): ZadMenuGroup بقت
    // 24dp بدل 18dp. ProfileScreen نفسها stateful (ZadViewModel+FamilyViewModel) فمش
    // قابلة للـ capture كاملة، فبنتحقق من الشكل المشترك اللي كل صفوف الإعدادات بتستخدمه.
    @Test
    fun captureZadMenuGroup_newCornerRadius() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    com.example.ui.components.ZadMenuGroup {
                        com.example.ui.components.ZadMenuRow(title = "تعديل الملف الشخصي", subtitle = "الاسم والصورة", onClick = {})
                        com.example.ui.components.ZadMenuRow(title = "إدارة العائلة", subtitle = "الأعضاء والصلاحيات", onClick = {}, showDivider = false)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/zad_menu_group_24dp.png")
    }

    /**
     * The Home screen's mockup sequence, composed out of the same components HomeScreen
     * uses, on the same canvas MainScreen paints.
     *
     * `HomeScreen` itself takes a `ZadViewModel` and can't be captured here, but every
     * piece of the layout being aligned to "ZAD App.dc.html" is stateless — so this
     * renders the actual arrangement (canvas → hero → days/safe-spend → 6-icon grid →
     * insight glass row → 2×2 stat grid → chef card → Amazon rail) rather than checking
     * it by reading the code.
     */
    // Phone width on purpose, overriding the class-level w1080dp: the six-column
    // shortcut grid and the 2×2 stat tiles are the two things most likely to break on a
    // real handset, and at 1080dp they always fit. 402×874 is the mockup's own iOS frame.
    @Config(sdk = [33], qualifiers = "w402dp-h874dp-xxhdpi")
    @Test
    fun captureHomeMockupSequence() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZadCanvasBackground(modifier = Modifier.fillMaxSize())
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        ZadCardHero(
                            spent = 1120.0,
                            remaining = 3880.0,
                            available = Figure(value = 3240.0, confident = false),
                            committed = 640.0,
                            nextObligationText = "إيجار بعد 4 أيام"
                        )

                        ZadDaysAndSafeSpendRow(daysLeft = 9, available = 3240.0)

                        ZadPageShortcutsGrid(
                            items = listOf(
                                ZadShortcutItem(Icons.Default.Inventory2, "المخزون", primary) {},
                                ZadShortcutItem(Icons.Default.ShoppingCart, "التسوق", catDailyIcon) {},
                                ZadShortcutItem(Icons.Default.FamilyRestroom, "العائلة", kidsPrimary) {},
                                ZadShortcutItem(Icons.Default.Subscriptions, "الاشتراكات", tertiary) {},
                                ZadShortcutItem(Icons.Default.LocalPharmacy, "الصيدلية", dangerColor) {},
                                ZadShortcutItem(Icons.Default.Park, "التسبيح", secondaryDark) {},
                            )
                        )

                        // The insight row exactly as HomeScreen builds it: glass card,
                        // priority dot, title/body stack.
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color.White.copy(alpha = 0.85f),
                            contentPadding = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(dangerColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("الحليب هيخلص بكرة", style = Typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = onSurface)
                                    Text("باقي يوم واحد على المعدل الحالي", style = Typography.bodyMedium, color = onSurfaceVariant)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ZadStatTile(modifier = Modifier.weight(1f), label = "قوة الإنفاق", value = "82%")
                            ZadStatTile(modifier = Modifier.weight(1f), label = "الصحة المالية", value = "74/100")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ZadStatTile(modifier = Modifier.weight(1f), label = "الإنفاق الشهري", value = "4,120 ر.س")
                            ZadStatTile(modifier = Modifier.weight(1f), label = "اتجاه 7 أيام", value = "↓ 6%")
                        }

                        ZadChefCard(
                            suggestion = "اقتراح اليوم: طبق سريع بمكونات مخزونك الحالي.",
                            onClick = {}
                        )

                        PremiumTransactionsRow(
                            transactions = listOf(
                                com.example.data.ZadTransaction(
                                    title = "سوبرماركت العائلة",
                                    amount = 240.0,
                                    isExpense = true,
                                    createdAt = java.time.Instant.now().toString()
                                ),
                                com.example.data.ZadTransaction(
                                    title = "راتب يوليو",
                                    amount = 9000.0,
                                    isExpense = false,
                                    createdAt = java.time.Instant.now().minusSeconds(2 * 86400).toString()
                                ),
                                com.example.data.ZadTransaction(
                                    title = "نتفليكس",
                                    amount = 45.0,
                                    isExpense = true,
                                    createdAt = java.time.Instant.now().minusSeconds(3 * 86400).toString()
                                ),
                            ),
                            onSeeAllClick = {}
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                listOf(
                                    "قدر ضغط كهربائي" to 189.0,
                                    "فيتامينات عائلية" to 85.0,
                                    "منظم مخزون مطبخ" to 49.0,
                                )
                            ) { (name, price) ->
                                ZadAmazonDealCard(
                                    product = com.example.data.AffiliateProduct(
                                        productNameAr = name,
                                        averagePriceSar = price
                                    ),
                                    onClick = {}
                                )
                            }
                        }

                        PremiumTransactionsRow(
                            transactions = listOf(
                                com.example.data.ZadTransaction(
                                    title = "سوبرماركت العائلة",
                                    amount = 240.0,
                                    isExpense = true,
                                    createdAt = java.time.Instant.now().toString()
                                ),
                                com.example.data.ZadTransaction(
                                    title = "راتب يوليو",
                                    amount = 9000.0,
                                    isExpense = false,
                                    createdAt = java.time.Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS).toString()
                                ),
                                com.example.data.ZadTransaction(
                                    title = "اشتراك نتفليكس",
                                    amount = 45.0,
                                    isExpense = true,
                                    createdAt = java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS).toString()
                                ),
                            ),
                            onSeeAllClick = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/home_mockup_sequence.png"
        )
    }

    /**
     * The mockup's app chrome, rendered as one frame: sticky header over the
     * canvas gradient, and the floating bottom pill with its raised camera
     * button. This is what replaced twelve per-screen `TopAppBar`s, so it is the
     * one composable worth a screenshot gate.
     */
    @Test
    fun captureZadShellChrome() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZadCanvasBackground(modifier = Modifier.fillMaxSize())
                    Column(modifier = Modifier.fillMaxSize()) {
                        ZadTopHeader(
                            title = "لوحة الميزانية",
                            hasUnreadNotifications = true
                        )
                        Spacer(Modifier.weight(1f))
                        ZadBottomNavBar(
                            currentRoute = ZadRoutes.HOME,
                            kidsMode = false,
                            onNavigate = {},
                            onOpenCamera = {},
                            onOpenMore = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_shell_chrome.png"
        )
    }

    @Test
    fun captureZadShellDrawer() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxWidth(0.78f).fillMaxSize()) {
                    ZadDrawerContent(
                        currentRoute = ZadRoutes.HOME,
                        entries = zadDrawerEntries,
                        userName = "سارة",
                        avatarUri = null,
                        onNavigate = {},
                        onProfileClick = {}
                    )
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/zad_shell_drawer.png"
        )
    }

    /**
     * Budget's obligation cards — the mockup's `OBLIGATIONS` block. Three rows
     * covering all three derived states: overdue-so-paid, due inside a week, and
     * scheduled further out.
     */
    @Test
    fun captureBudgetObligations() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZadCanvasBackground(modifier = Modifier.fillMaxSize())
                    Column(
                        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val today = java.time.LocalDate.now()
                        ObligationCard(
                            com.example.data.ZadObligation(
                                title = "الإيجار",
                                amount = 2400.0,
                                kind = "rent",
                                dueDate = today.plusDays(4).toString()
                            )
                        )
                        ObligationCard(
                            com.example.data.ZadObligation(
                                title = "الإنترنت والاتصالات",
                                amount = 260.0,
                                kind = "utility",
                                dueDate = today.minusDays(3).toString()
                            )
                        )
                        ObligationCard(
                            com.example.data.ZadObligation(
                                title = "قسط السيارة",
                                amount = 980.0,
                                kind = "installment",
                                dueDate = today.plusDays(11).toString()
                            )
                        )
                    }
                }
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/budget_obligations.png"
        )
    }

    // ── Design-port verification captures ────────────────────────────────────
    // Proof that the ported surfaces render as intended, not just that they compile.

    /** The auth canvas + brand mark + wordmark + slogan + pill CTA — the login screen's
     *  own header stack, composed without AuthViewModel. */
    @Test
    fun captureAuthHeaderAndCta() {
        composeTestRule.setContent {
            AppTheme {
                com.example.ui.components.ZadAuthBackground {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.size(64.dp))
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_carrot_logo),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.size(20.dp))
                        Text("ZAD", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = primaryLight)
                        Spacer(modifier = Modifier.size(10.dp))
                        Text("تدبير ذكي لبيت هادئ", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textSecondary)
                        Spacer(modifier = Modifier.size(40.dp))
                        com.example.ui.components.ZadPrimaryButton(
                            text = "دخول",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.size(16.dp))
                        com.example.ui.components.ZadPrimaryButton(
                            text = "دخول (معطّل)",
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/auth_header_and_cta.png"
        )
    }

    /** Profile's grouped settings card, the kids-mode switch, and the status pills. */
    @Test
    fun captureProfileMenuAndPrimitives() {
        composeTestRule.setContent {
            AppTheme {
                ZadCanvasBackground()
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    com.example.ui.components.ZadMenuGroup {
                        com.example.ui.components.ZadMenuRow(
                            title = "وضع الأطفال",
                            subtitle = "واجهة مبسطة للأبناء",
                            onClick = {},
                            showDivider = false,
                            trailing = {
                                com.example.ui.components.ZadSwitch(
                                    checked = true,
                                    onCheckedChange = {},
                                    checkedColor = kidsPrimary
                                )
                            }
                        )
                    }
                    com.example.ui.components.ZadMenuGroup {
                        com.example.ui.components.ZadMenuRow(title = "تعديل الملف الشخصي", subtitle = "الاسم والصورة", onClick = {})
                        com.example.ui.components.ZadMenuRow(title = "إدارة العائلة", subtitle = "الأعضاء والصلاحيات", onClick = {})
                        com.example.ui.components.ZadMenuRow(
                            title = "حذف الحساب",
                            subtitle = "حذف نهائي",
                            onClick = {},
                            titleColor = dangerColor,
                            showDivider = false
                        )
                    }
                    com.example.ui.components.ZadRowCard(
                        title = "نتفليكس",
                        subtitle = "يتجدد بعد 4 أيام",
                        leadingAccent = dangerColor,
                        trailing = { com.example.ui.components.ZadRowAmount("45 ر.س") }
                    )
                    com.example.ui.components.ZadRowCard(
                        title = "الإيجار",
                        subtitle = "مستحق بعد 4 أيام",
                        trailing = { com.example.ui.components.ZadStatusPill("مستحق", dangerColor) }
                    )
                    com.example.ui.components.ZadMeterBar(progress = 0.82f, color = primary, height = 8.dp)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/profile_menu_and_primitives.png"
        )
    }

    /** Zad Mind's spending-power card — the dark panel that replaced the drawn gauge. */
    @Test
    fun captureSpendingPowerPanel() {
        composeTestRule.setContent {
            AppTheme {
                ZadCanvasBackground()
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    com.example.ui.components.ZadDarkPanel(title = "قوة الصرف") {
                        Text("82%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        com.example.ui.components.ZadMeterBar(
                            progress = 0.82f,
                            color = primaryFixed,
                            height = 8.dp,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                    // the no-budget state: powerPct = null must read as "—", not as a full bar
                    com.example.ui.components.ZadDarkPanel(title = "قوة الصرف — بدون سقف") {
                        Text("—", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        com.example.ui.components.ZadMeterBar(
                            progress = 0f,
                            color = Color.White.copy(alpha = 0.35f),
                            height = 8.dp,
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ZadStatTile(modifier = Modifier.weight(1f), label = "قوة الإنفاق", value = "82%")
                        ZadStatTile(modifier = Modifier.weight(1f), label = "اتجاه 7 أيام", value = "↓ 6%")
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/spending_power_panel.png"
        )
    }

    /** الويدجتين الجداد (صيدلية/اشتراكات) — حالة فيها بيانات وحالة فاضية، عشان نتأكد
     *  الشكل صح قبل ما ينزل على جهاز حقيقي (مفيش emulator هنا). */
    @Test
    fun captureMiniPharmacyWidget_withLowStock() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(20.dp)) {
                    MiniPharmacyWidget(
                        pharmacyItems = listOf(
                            com.example.data.ZadPharmacyItem(name = "دواء الضغط", remainingQuantity = 2, dailyDoseCount = 1, unit = "قرص"),
                            com.example.data.ZadPharmacyItem(name = "فيتامين د", remainingQuantity = 1, dailyDoseCount = 1, unit = "كبسولة")
                        ),
                        onNavigateToPharmacy = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/mini_pharmacy_widget_low_stock.png")
    }

    @Test
    fun captureMiniPharmacyWidget_empty() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(20.dp)) {
                    MiniPharmacyWidget(pharmacyItems = emptyList(), onNavigateToPharmacy = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/mini_pharmacy_widget_empty.png")
    }

    @Test
    fun captureMiniSubscriptionsWidget_withUpcoming() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(20.dp)) {
                    MiniSubscriptionsWidget(
                        subscriptions = listOf(
                            com.example.data.ZadSubscription(title = "نتفليكس", amount = 55.0, renewalDate = LocalDate.now().plusDays(2).toString(), isActive = true),
                            com.example.data.ZadSubscription(title = "شاهد VIP", amount = 30.0, renewalDate = LocalDate.now().plusDays(5).toString(), isActive = true)
                        ),
                        onNavigateToSubscriptions = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/mini_subscriptions_widget_upcoming.png")
    }

    @Test
    fun captureMiniSubscriptionsWidget_empty() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(20.dp)) {
                    MiniSubscriptionsWidget(subscriptions = emptyList(), onNavigateToSubscriptions = {})
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/mini_subscriptions_widget_empty.png")
    }

    /** الصيدلية — حوار "إضافة دواء بالكلام" الجديد بدل الانتقال لشاشة عقل زاد. */
    @Test
    fun captureSmartAddMedicationDialog() {
        composeTestRule.setContent {
            AppTheme {
                SmartAddMedicationDialog(onDismiss = {}, onSubmit = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/smart_add_medication_dialog.png")
    }

    /** Companion orb — the four emotion states side by side, so eye shape and color read
     *  correctly before wiring the real emotion engine on top of it. */
    @Test
    fun captureCompanionOrbStates() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(24.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        com.example.ui.components.CompanionOrb(com.example.ui.components.CompanionState.Idle)
                        com.example.ui.components.CompanionOrb(com.example.ui.components.CompanionState.Focused)
                        com.example.ui.components.CompanionOrb(com.example.ui.components.CompanionState.Happy)
                        com.example.ui.components.CompanionOrb(com.example.ui.components.CompanionState.Alert)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/companion_orb_states.png"
        )
    }

    /** الالتزامات — كارت التزام حقيقي، للتأكد إن الـ FAB/زر الحذف/الحالة بيتعرضوا صح. */
    @Test
    fun captureObligationCard() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    ObligationCard(
                        obligation = com.example.data.ZadObligation(
                            title = "إيجار الشقة",
                            amount = 3000.0,
                            kind = "rent",
                            dueDay = 5,
                            recurrence = "monthly"
                        ),
                        onEdit = {},
                        onDelete = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/obligation_card.png")
    }

    /** حوار إضافة/تعديل التزام — وضع الإضافة (مفيش obligation ممرر). */
    @Test
    fun captureAddObligationDialog() {
        composeTestRule.setContent {
            AppTheme {
                AddEditObligationDialog(obligation = null, onDismiss = {}, onSave = { _, _, _, _, _ -> })
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/add_obligation_dialog.png")
    }

    /** عقل زاد — كارت التقرير الشهري، الحالة الابتدائية (قبل التوليد). */
    @Test
    fun captureMonthlyReportCard_empty() {
        composeTestRule.setContent {
            AppTheme {
                Box(modifier = Modifier.background(background).padding(16.dp)) {
                    MonthlyReportCard(
                        transactions = emptyList(),
                        budget = 3000.0,
                        totalIncome = 0.0,
                        totalExpense = 0.0,
                        topCategories = emptyList(),
                        cycleStart = java.time.LocalDate.now()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/monthly_report_card_empty.png")
    }
}
