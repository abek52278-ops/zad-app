package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ZadSegmentedTabs
import com.example.ui.theme.Typography
import com.example.ui.theme.ZadHubListBottomPadding
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel

/**
 * البوابة 5 — عقل زاد والعائلة (Brain & Family), UI_ARCHITECTURE_SPEC.md §2.5/§4.4.
 * أكبر عملية دمج في الخريطة: 7 شاشات كانت متفرقة (Assistant, Family, Tasbiha,
 * KnowledgeMap, ZadMemory, AgentActionLog, Achievements) بقوا تبويبين بس.
 *
 * كل الشاشات الأصلية فضلت زي ما هي بمنطقها/الـViewModel calls بتاعتها — الدمج على
 * مستوى العرض/التنقل بس. Routes القديمة المستقلة (ZadNav.ZAD_MEMORY/AGENT_ACTION_LOG/
 * ACHIEVEMENTS, ZadRoutes.TASBIHA/KNOWLEDGE_MAP) فضلت شغالة زي ما هي (Profile/درج/
 * more-sheet لسه بيوصلولها) — دي بوابة وصول إضافية سريعة من جوه نفس الشاشة، مش بديل.
 *
 * **قيد وضع الأطفال**: هذه الشاشة أدوات بالغين بالكامل (تحليلات مالية + دردشة AI).
 * لا تُستدعى مباشرة في وضع الأطفال — MainScreen.kt بيفرّع على `kidsModeEffective`
 * ويرندر `FamilyScreen` وحدها بدون أي تبويبات لما يكون طفل، فالحماية الحالية
 * (`route != FAMILY` ممنوع في وضع الأطفال) فضلت زي ما هي تمامًا.
 */
enum class BrainFamilyTab { INTELLIGENCE, FAMILY }

/** نفس نمط [InventoryNavState]/[PantryShoppingNavState]/[FinancesNavState]. */
object BrainFamilyNavState {
    var pendingTab by mutableStateOf<BrainFamilyTab?>(null)
}

@Composable
fun BrainFamilyScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    initialTab: BrainFamilyTab = BrainFamilyTab.INTELLIGENCE,
    pendingInviteCode: String? = null,
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onNavigateToStatementImport: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(BrainFamilyNavState.pendingTab ?: initialTab) }
    LaunchedEffect(Unit) { BrainFamilyNavState.pendingTab = null }

    Column(modifier = Modifier.fillMaxSize()) {
        ZadSegmentedTabs(
            tabs = listOf(
                stringResource(R.string.screen_title_assistant),
                stringResource(R.string.nav_family)
            ),
            selectedIndex = selectedTab.ordinal,
            onSelect = { selectedTab = BrainFamilyTab.entries[it] },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                BrainFamilyTab.INTELLIGENCE -> BrainIntelligenceBody(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onNavigateToStatementImport = onNavigateToStatementImport,
                    onNavigateToRoute = onNavigateToRoute,
                    onSwitchToFamilyTab = { selectedTab = BrainFamilyTab.FAMILY }
                )
                BrainFamilyTab.FAMILY -> FamilyHubBody(
                    familyViewModel = familyViewModel,
                    pendingInviteCode = pendingInviteCode,
                    unreadNotificationCount = unreadNotificationCount,
                    onNotificationsClick = onNotificationsClick
                )
            }
        }
    }
}

private enum class BrainSubView { MAIN, MEMORY, KNOWLEDGE_MAP, AGENT_LOG }

/**
 * تاب "عقل زاد" — ZadIntelligenceScreen (التحليلات + الدردشة + الرؤى الاستباقية،
 * موجودين فيها أصلاً) هو الأساسي، مع "زر جانبي" (أيقونات في صف علوي) للذاكرة/خريطة
 * المعرفة/سجل القرارات — كل واحدة منهم شاشة كاملة قايمة، بتتبدّل local مش عبر
 * NavController (زي [PharmacyScreen]'s family-view toggle).
 */
@Composable
private fun BrainIntelligenceBody(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel,
    onNavigateToStatementImport: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onSwitchToFamilyTab: () -> Unit
) {
    var subView by remember { mutableStateOf(BrainSubView.MAIN) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (subView == BrainSubView.MAIN) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SideViewIconButton(Icons.Default.Psychology, stringResource(R.string.zad_memory_title)) { subView = BrainSubView.MEMORY }
                SideViewIconButton(Icons.Default.Hub, stringResource(R.string.knowledge_map_title)) { subView = BrainSubView.KNOWLEDGE_MAP }
                SideViewIconButton(Icons.Default.History, stringResource(R.string.agent_action_log_title)) { subView = BrainSubView.AGENT_LOG }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (subView) {
                BrainSubView.MAIN -> ZadIntelligenceScreen(
                    viewModel = viewModel,
                    familyViewModel = familyViewModel,
                    onNavigateToFamily = onSwitchToFamilyTab,
                    onNavigateToStatementImport = onNavigateToStatementImport,
                    onNavigateToKnowledgeMap = { subView = BrainSubView.KNOWLEDGE_MAP }
                )
                BrainSubView.MEMORY -> ZadMemoryScreen(onBack = { subView = BrainSubView.MAIN })
                BrainSubView.KNOWLEDGE_MAP -> ZadKnowledgeMapScreen(
                    viewModel = viewModel,
                    onBack = { subView = BrainSubView.MAIN },
                    onNavigateToRoute = onNavigateToRoute
                )
                BrainSubView.AGENT_LOG -> AgentActionLogScreen(onBack = { subView = BrainSubView.MAIN })
            }
        }
    }
}

private enum class FamilySubView { MAIN, SAVINGS, ACHIEVEMENTS, TASBIHA }

/**
 * تاب "العائلة" — FamilyScreen هو الأساسي، مع "زر جانبي" لصناديق الادخار (+تحديات
 * العائلة المالية، كانوا مقحمين مع بعض دايمًا)، الإنجازات، والتسبيحة.
 *
 * صناديق الادخار/تحديات العائلة كانوا جوه تاب "الديون" في بوابة Finances مؤقتًا
 * (قرار مؤجل وقتها، اتحدد دلوقتي) — دلوقتي هنا، مكانهم الطبيعي.
 */
@Composable
private fun FamilyHubBody(
    familyViewModel: FamilyViewModel,
    pendingInviteCode: String?,
    unreadNotificationCount: Int,
    onNotificationsClick: () -> Unit
) {
    var subView by remember { mutableStateOf(FamilySubView.MAIN) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (subView == FamilySubView.MAIN) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SideViewIconButton(Icons.Default.Savings, stringResource(R.string.sinking_funds_title)) { subView = FamilySubView.SAVINGS }
                SideViewIconButton(Icons.Default.EmojiEvents, stringResource(R.string.achievements_menu_title)) { subView = FamilySubView.ACHIEVEMENTS }
                SideViewIconButton(Icons.Default.Park, stringResource(R.string.tasbiha_short_label)) { subView = FamilySubView.TASBIHA }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (subView) {
                FamilySubView.MAIN -> FamilyScreen(
                    pendingInviteCode = pendingInviteCode,
                    viewModel = familyViewModel,
                    unreadNotificationCount = unreadNotificationCount,
                    onNotificationsClick = onNotificationsClick,
                    showFinancials = true
                )
                FamilySubView.SAVINGS -> Column(modifier = Modifier.fillMaxSize()) {
                    SubViewHeader(title = stringResource(R.string.sinking_funds_title), onBack = { subView = FamilySubView.MAIN })
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, ZadHubListBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { SinkingFundsCard(familyViewModel) }
                        item { FinancialChallengesCard(familyViewModel) }
                    }
                }
                FamilySubView.ACHIEVEMENTS -> AchievementsRoute(onBack = { subView = FamilySubView.MAIN })
                FamilySubView.TASBIHA -> Column(modifier = Modifier.fillMaxSize()) {
                    SubViewHeader(title = stringResource(R.string.tasbiha_garden_title), onBack = { subView = FamilySubView.MAIN })
                    TasbihaScreen(viewModel = familyViewModel)
                }
            }
        }
    }
}

@Composable
private fun SideViewIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** رأس بسيط لأي sub-view محلي جوه البوابة دي — رجوع + عنوان، بدل شاشة كاملة منفصلة. */
@Composable
private fun SubViewHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
    }
}
