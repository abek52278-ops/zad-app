package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.*
import com.example.ui.screens.auth.AuthTextField
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*
import com.example.ui.components.QrCode
import com.example.ui.components.pressableScale
import com.example.ui.components.GlassCard
import com.example.ui.components.zadGlassBlur
import com.example.ui.components.ZadLottieAsset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.airbnb.lottie.compose.LottieConstants
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.FamilyState
import android.util.Log
import kotlinx.serialization.encodeToString
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import com.example.ui.components.decodeQrFromBitmap
import com.example.ui.components.extractInviteCode
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private const val TAG_FAM = "FamilyScreen"

/** HH:mm محلي لعرضه تحت كل فقاعة — مفيش أي وقت كان بيتعرض على الرسائل خالص قبل كده،
 * msg.createdAt كان مستخدم بس لتجميع فواصل التاريخ (يوم كامل)، مش وقت الرسالة نفسها. */
private fun formatMessageTime(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(createdAt)
        java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun FamilyScreen(
    pendingInviteCode: String? = null,
    viewModel: FamilyViewModel = viewModel(),
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    /** false في وضع الأطفال — يخفي الأرصدة/أهداف الادخار/حدود الصرف/مكافآت المهام */
    showFinancials: Boolean = true
) {
    val state by viewModel.state.collectAsState()
    val toastContext = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val currState = state) {
                is FamilyState.Loading -> {
                    com.example.ui.components.ZadLoadingState()
                }
                is FamilyState.Error -> {
                    com.example.ui.components.ZadErrorState(message = currState.message)
                }
                is FamilyState.NoFamily -> {
                    NoFamilyScreen(
                        pendingInviteCode = pendingInviteCode,
                        onCreateFamily = { viewModel.createFamily() },
                        onJoinFamily = { code, alias -> viewModel.joinFamily(code, alias) }
                    )
                }
                is FamilyState.Active -> {
                    ActiveFamilyScreen(
                        viewModel = viewModel,
                        state = currState,
                        onSendMessage = { msg, type, meta -> viewModel.sendMessage(msg, type, meta) },
                        onToggleGrocery = { id, isPurchased -> viewModel.toggleGroceryItem(id, isPurchased) },
                        onToggleChore = { id, isCompleted -> viewModel.toggleChore(id, isCompleted) },
                        onAddChore = { assignedTo, title, dueDate, rewardAmount -> viewModel.addChore(assignedTo, title, dueDate, rewardAmount) },
                        onUpdateRequestStatus = { messageId, newStatus, replyMsg -> viewModel.updateRequestStatus(messageId, newStatus, replyMsg) },
                        showFinancials = showFinancials
                    )
                }
            }
        }
    }
}

@Composable
fun NoFamilyScreen(
    pendingInviteCode: String? = null,
    onCreateFamily: () -> Unit,
    onJoinFamily: (String, String) -> Unit
) {
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf(pendingInviteCode ?: "") }
    var alias by remember { mutableStateOf("") }

    LaunchedEffect(pendingInviteCode) {
        if (!pendingInviteCode.isNullOrBlank()) {
            inviteCode = pendingInviteCode
            showJoinDialog = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        com.example.ui.components.AppearOnEntry {
            ZadLottieAsset(
                resId = R.raw.lottie_empty_box,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(140.dp),
                contentDescription = stringResource(R.string.welcome_to_zad_family)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.welcome_to_zad_family),
            style = Typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.family_intro_hint),
            style = Typography.bodyMedium,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCreateFamily,
            modifier = Modifier.fillMaxWidth().height(54.dp).pressableScale(),
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.create_new_family_admin), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { showJoinDialog = true },
            modifier = Modifier.fillMaxWidth().height(54.dp).pressableScale(),
            colors = ButtonDefaults.buttonColors(containerColor = secondary),
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Default.GroupAdd, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.join_family_action), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.or_word), color = onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))

        AuthTextField(label = stringResource(R.string.your_alias_label), value = alias, onValueChange = { alias = it }, placeholder = stringResource(R.string.eg_child_alias))
        AuthTextField(label = stringResource(R.string.invite_code_label), value = inviteCode, onValueChange = { inviteCode = extractInviteCode(it) }, placeholder = stringResource(R.string.eg_invite_code))

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onJoinFamily(inviteCode.trim(), alias.trim()) },
            modifier = Modifier.fillMaxWidth().height(54.dp).pressableScale(),
            shape = RoundedCornerShape(50),
            enabled = inviteCode.isNotBlank() && alias.isNotBlank()
        ) {
            Icon(Icons.Default.ArrowForward, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.join_family_action), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showJoinDialog) {
        JoinFamilyDialog(
            initialCode = inviteCode,
            onDismiss = { showJoinDialog = false },
            onJoin = { code, name ->
                onJoinFamily(code, name)
                showJoinDialog = false
            }
        )
    }
}

@Composable
fun ActiveFamilyScreen(
    viewModel: FamilyViewModel,
    state: FamilyState.Active,
    onSendMessage: (String, String, String?) -> Unit,
    onToggleGrocery: (String, Boolean) -> Unit,
    onToggleChore: (String, Boolean) -> Unit,
    onAddChore: (String, String, String?, Double) -> Unit,
    onUpdateRequestStatus: (String, String, String) -> Unit,
    showFinancials: Boolean = true
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var showInviteDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    val isParent = state.myMemberInfo.role == "admin"
    val needAmountPattern = stringResource(R.string.need_amount_purchase)
    val shareInviteTitle = stringResource(R.string.share_invite_title)
    val joinFamilyShareText = stringResource(R.string.join_family_message)
    val inviteCodeColon = stringResource(R.string.invite_code_colon)
    val tapToOpen = stringResource(R.string.tap_to_open)
    val sosMessageText = stringResource(R.string.sos_message)
    val headerHaptic = LocalHapticFeedback.current
    var showSosConfirm by remember { mutableStateOf(false) }
    val tabs = listOf(
        TabData(Icons.Default.Chat, stringResource(R.string.chat_tab)),
        TabData(Icons.Default.Assignment, stringResource(R.string.tasks_tab)),
        TabData(Icons.Default.People, stringResource(R.string.members_tab)),
        TabData(Icons.Default.ShoppingCart, stringResource(R.string.groceries_tab))
    ) + if (isParent && showFinancials) listOf(
        TabData(Icons.Default.AccountBalanceWallet, stringResource(R.string.children_tab)),
        TabData(Icons.Default.Savings, stringResource(R.string.budget_goals_tab))
    ) else emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with family info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(primary, primaryLight)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.family_with_code, state.familyGroup.inviteCode),
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            stringResource(R.string.members_count, state.members.size),
                            style = Typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                headerHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSosConfirm = true
                            },
                            modifier = Modifier.pressableScale()
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.sos_message), tint = Color.White)
                        }
                        IconButton(
                            onClick = { showJoinDialog = true },
                            modifier = Modifier.pressableScale()
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = stringResource(R.string.join_family_action), tint = Color.White)
                        }
                        IconButton(onClick = { showInviteDialog = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color.White)
                        }
                        IconButton(onClick = {
                            val webLink = "https://zad.app/invite?code=${state.familyGroup.inviteCode}"
                            val deepLink = "zad://invite?code=${state.familyGroup.inviteCode}"
                            val shareMessage = """
🌱 دعوة للانضمام إلى عائلتي على تطبيق زاد

📲 اضغط على الرابط التالي للانضمام فوراً وفتح التطبيق:
$webLink

🔑 كود الدعوة المباشر:
${state.familyGroup.inviteCode}

🔗 رابط التطبيق المباشر:
$deepLink
                            """.trimIndent()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, shareInviteTitle))
                        }) {
                            Icon(Icons.Default.Share, null, tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // شريط أفاتارات الأعضاء
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    state.members.take(6).forEach { member ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(2.dp, primary, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member.alias.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    if (state.members.size > 6) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(2.dp, primary, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${state.members.size - 6}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (showSosConfirm) {
            AlertDialog(
                onDismissRequest = { showSosConfirm = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = dangerColor) },
                title = { Text(stringResource(R.string.sos_confirm_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.sos_confirm_body)) },
                confirmButton = {
                    Button(
                        onClick = {
                            onSendMessage(sosMessageText, "SOS", null)
                            showSosConfirm = false
                            selectedTab = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                    ) { Text(stringResource(R.string.sos_send_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSosConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        // The mockup's segmented pill, shared with Zad Mind.
        com.example.ui.components.ZadSegmentedTabs(
            tabs = tabs.map { it.title },
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it }
        )

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ChatTab(state.messages, state.members, state.myMemberInfo, onSendMessage, onUpdateRequestStatus, viewModel = viewModel, showFinancials = showFinancials)
                1 -> TasksTab(state.chores, state.members, state.myMemberInfo.role == "admin", onToggleChore, onAddChore, showFinancials = showFinancials)
                2 -> MembersTab(state = state, viewModel = viewModel, showFinancials = showFinancials, onOpenJoinDialog = { showJoinDialog = true })
                3 -> GroceriesTab(state.groceries, onToggleGrocery)
                4 -> if (isParent && showFinancials) KidsSpendingTab(state = state, viewModel = viewModel, onUpdateRequestStatus = onUpdateRequestStatus)
                5 -> if (isParent && showFinancials) BudgetGoalsTab(state.goals, state.members, state.chores, viewModel)
            }
        }
    }

    if (showInviteDialog) {
        InviteMemberDialog(
            inviteCode = state.familyGroup.inviteCode,
            onDismiss = { showInviteDialog = false }
        )
    }

    if (showJoinDialog) {
        JoinFamilyDialog(
            initialCode = "",
            onDismiss = { showJoinDialog = false },
            onJoin = { code, alias ->
                viewModel.joinFamily(code, alias)
                showJoinDialog = false
            }
        )
    }
}

data class TabData(val icon: ImageVector, val title: String)

// ── GROCERIES TAB ──
@Composable
private fun GroceriesTab(
    groceries: List<SharedGroceryItem>,
    onToggleGrocery: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.shared_grocery_list), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.shared_grocery_list_hint),
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        if (groceries.isEmpty()) {
            item {
                com.example.ui.components.ZadEmptyState(
                    title = stringResource(R.string.no_grocery_requests),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                )
            }
        }

        itemsIndexed(groceries) { index, item ->
            com.example.ui.components.AppearOnEntry(delayMs = (index * 40).coerceAtMost(400)) {
                val groceryShape = RoundedCornerShape(18.dp)
                com.example.ui.components.ZadListCard(
                    shape = groceryShape,
                    contentPadding = 0.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isPurchased,
                            onCheckedChange = { onToggleGrocery(item.id, it) },
                            colors = CheckboxDefaults.colors(checkedColor = primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.itemName,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isPurchased) onSurfaceVariant else onSurface
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── KIDS SPENDING TAB ──
@Composable
private fun KidsSpendingTab(
    state: FamilyState.Active,
    viewModel: FamilyViewModel,
    onUpdateRequestStatus: (String, String, String) -> Unit
) {
    val children = state.members.filter { it.role == "child" }
    val purchaseRequests = state.messages.filter { it.messageType == "PURCHASE_REQUEST" }
    val currencyContext = LocalContext.current
    val approvedEmojiText = stringResource(R.string.approved_emoji)
    val rejectedEmojiText = stringResource(R.string.rejected_emoji)
    val realSpending by viewModel.childrenSpending.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadChildrenSpending() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.childrens_expenses), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.children_overview_subtitle), style = Typography.bodyMedium, color = onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (children.isEmpty()) {
            com.example.ui.components.ZadEmptyState(
                title = stringResource(R.string.no_children_registered),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
            )
            return
        }

        children.forEach { child ->
            val childChores = state.chores.filter { it.assignedTo == child.id }
            val completed = childChores.count { it.isCompleted }
            val childRequests = purchaseRequests.filter { it.senderId == child.id }

            com.example.ui.components.ZadListCard(
                modifier = Modifier.padding(vertical = 6.dp),
                contentPadding = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.screens.KidAvatar(seed = child.id.ifBlank { child.alias }, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(child.alias, fontWeight = FontWeight.Bold, color = onSurface)
                            Text(stringResource(R.string.balance_colon, com.example.data.CurrencyFormatter.format(currencyContext, child.balance)) + if (child.savingsGoal > 0) " | " + stringResource(R.string.goal_colon, com.example.data.CurrencyFormatter.format(currencyContext, child.savingsGoal)) else "", style = Typography.labelSmall, color = onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = primary.copy(alpha = 0.1f)) {
                            Text(stringResource(R.string.chores_completed_ratio_pill, completed, childChores.size), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = Typography.labelSmall, color = primary)
                        }
                    }
                    if (child.dailyLimit != null && child.dailyLimit > 0) {
                        Spacer(Modifier.height(8.dp))
                        val spentToday = com.example.data.approvedSpendSince(state.messages, child.id, java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                        SpendLimitBar(stringResource(R.string.daily_limit_label), spentToday, child.dailyLimit, currencyContext)
                    }
                    // مصروف بنكي حقيقي (zad_transactions) مقابل سقف ميزانية الابن — منفصل
                    // تماماً عن المصروف المعتمد من الشات فوق (نظام مصروف/مهام مختلف)
                    realSpending[child.id]?.let { spending ->
                        Spacer(Modifier.height(8.dp))
                        SpendLimitBar(
                            stringResource(R.string.real_monthly_spend_label),
                            spending.monthlyTotal,
                            spending.budgetCeiling,
                            currencyContext
                        )
                    }
                    if (childRequests.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = surface, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(stringResource(R.string.pending_requests_count, childRequests.size), style = Typography.labelSmall, fontWeight = FontWeight.Bold, color = onSurfaceVariant)
                                childRequests.filter { it.metadata?.contains("\"status\":\"PENDING\"") == true }.take(3).forEach { req ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(14.dp), tint = warningColor)
                                        Spacer(Modifier.width(6.dp))
                                        Text(req.message, style = Typography.labelSmall, color = onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { onUpdateRequestStatus(req.id, "APPROVED", approvedEmojiText) }, contentPadding = PaddingValues(4.dp)) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = primary)
                                                Spacer(Modifier.width(2.dp))
                                                Text(stringResource(R.string.approve), style = Typography.labelSmall)
                                            }
                                            TextButton(onClick = { onUpdateRequestStatus(req.id, "REJECTED", rejectedEmojiText) }, contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(2.dp))
                                                Text(stringResource(R.string.reject), style = Typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── MEMBERS TAB ──
@Composable
private fun MembersTab(
    state: FamilyState.Active,
    viewModel: FamilyViewModel,
    showFinancials: Boolean = true,
    onOpenJoinDialog: () -> Unit = {}
) {
    var selectedMember by remember { mutableStateOf<com.example.data.FamilyMember?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isSoleMember = state.members.size <= 1

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                stringResource(R.string.family_members),
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tap_member_for_achievements),
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        itemsIndexed(state.members) { index, member ->
            com.example.ui.components.AppearOnEntry(delayMs = (index * 40).coerceAtMost(400)) {
                MemberDetailCard(
                    member = member,
                    viewModel = viewModel,
                    state = state,
                    onClick = { selectedMember = member },
                    showFinancials = showFinancials
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        // مغادرة/حذف العائلة — تحت تاب الأعضاء، مش في الهيدر المزدحم أصلاً بأيقونات
        // نداء الطوارئ/الدعوة/المشاركة. لو أنا آخر فرد، الخيار بيتحول لحذف العائلة نهائياً
        // بدل مغادرة (مغادرتها كآخر فرد تسيبها يتيمة بلا مالك).
        item {
            HorizontalDivider(color = onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(16.dp))
            if (isSoleMember) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_family_action))
                }
            } else {
                OutlinedButton(
                    onClick = { showLeaveConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.leave_family_action))
                }
            }
        }
    }

    if (selectedMember != null) {
        MemberDetailSheet(
            member = selectedMember!!,
            viewModel = viewModel,
            state = state,
            onDismiss = { selectedMember = null },
            showFinancials = showFinancials
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = dangerColor) },
            title = { Text(stringResource(R.string.leave_family_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.leave_family_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.leaveFamily(); showLeaveConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) { Text(stringResource(R.string.leave_family_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = dangerColor) },
            title = { Text(stringResource(R.string.delete_family_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_family_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteFamily(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) { Text(stringResource(R.string.delete_family_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun MemberDetailCard(
    member: com.example.data.FamilyMember,
    viewModel: FamilyViewModel,
    state: FamilyState.Active,
    onClick: () -> Unit,
    showFinancials: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val memberTrees = viewModel.getMemberTrees(member.id)
    val memberChores = state.chores.filter { it.assignedTo == member.id }
    val completedChores = memberChores.count { it.isCompleted }
    val memberTransactions = mutableStateOf<List<ZadTransaction>>(emptyList())

    val memberAccent = if (member.role == "admin") primary else secondary
    val memberCardShape = RoundedCornerShape(20.dp)
    com.example.ui.components.ZadListCard(
        modifier = Modifier.clickable { onClick() }.pressableScale(),
        shape = memberCardShape,
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(
                        Brush.verticalGradient(
                            colors = if (member.role == "admin") listOf(primary, primaryLight) else listOf(secondary, secondaryLight)
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(member.alias.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, style = Typography.titleLarge)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.alias, fontWeight = FontWeight.Bold, color = onSurface, style = Typography.titleMedium)
                        if (member.role == "admin") {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = primary.copy(alpha = 0.15f)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = primary)
                                    Text(stringResource(R.string.admin_badge), style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (member.role == "child") {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(50), color = secondary.copy(alpha = 0.15f)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ChildCare, contentDescription = null, modifier = Modifier.size(14.dp), tint = secondary)
                                    Text(stringResource(R.string.child_role), style = Typography.labelSmall, color = secondary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (showFinancials) StatPill(Icons.Default.MonetizationOn, com.example.data.CurrencyFormatter.format(context, member.balance))
                        StatPill(Icons.Default.CheckCircle, stringResource(R.string.chores_count_pill, completedChores))
                        StatPill(Icons.Default.Park, stringResource(R.string.tasbiha_count_pill, memberTrees.sumOf { it.score }))
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = onSurfaceVariant)
            }

            // Progress bar for savings goal
            if (showFinancials && member.savingsGoal > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val goalProgress = (member.balance / member.savingsGoal).toFloat().coerceIn(0f, 1f)
                val animatedGoalProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = goalProgress,
                    animationSpec = com.example.ui.components.ZadSprings.Screen,
                    label = "goalProgress"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.savings_goal_label), style = Typography.labelSmall, color = onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedGoalProgress },
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = primary,
                        trackColor = onSurface.copy(alpha = 0.1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${((goalProgress * 100).toInt())}%", style = Typography.labelSmall, color = primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = onSurface.copy(alpha = 0.05f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = onSurfaceVariant)
            Text(text, style = Typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDetailSheet(
    member: com.example.data.FamilyMember,
    viewModel: FamilyViewModel,
    state: FamilyState.Active,
    onDismiss: () -> Unit,
    showFinancials: Boolean = true
) {
    val memberTrees = viewModel.getMemberTrees(member.id)
    val memberChores = state.chores.filter { it.assignedTo == member.id }
    val completedChores = memberChores.filter { it.isCompleted }
    val pendingChores = memberChores.filter { !it.isCompleted }
    val currencyContext = LocalContext.current
    var showLimitDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Member header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(
                        Brush.verticalGradient(
                            colors = if (member.role == "admin") listOf(primary, primaryLight) else listOf(secondary, secondaryLight)
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(member.alias.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(member.alias, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 22.sp)
                    Text(
                        stringResource(
                            when (member.role) {
                                "admin" -> R.string.family_admin_role
                                "child" -> R.string.child_role
                                else -> R.string.member_role
                            }
                        ),
                        color = onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (showFinancials) {
                    StatItem(stringResource(R.string.balance_label), com.example.data.CurrencyFormatter.format(currencyContext, member.balance))
                    StatItem(stringResource(R.string.goal_label), if (member.savingsGoal > 0) com.example.data.CurrencyFormatter.format(currencyContext, member.savingsGoal) else "---")
                }
                StatItem(stringResource(R.string.trees_label), "${memberTrees.size}")
            }

            Spacer(Modifier.height(24.dp))

            // Completed Tasks
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = primary)
                Text(stringResource(R.string.completed_tasks_count, completedChores.size), fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(8.dp))
            if (completedChores.isEmpty()) {
                com.example.ui.components.ZadEmptyState(title = stringResource(R.string.no_completed_tasks))
            } else {
                completedChores.forEach { chore ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = primary.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chore.title, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 14.sp)
                                if (showFinancials && chore.rewardAmount > 0) {
                                    Text(stringResource(R.string.reward_colon, com.example.data.CurrencyFormatter.format(currencyContext, chore.rewardAmount)), color = primary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Pending Tasks
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(18.dp), tint = secondary)
                Text(stringResource(R.string.upcoming_tasks_count, pendingChores.size), fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(8.dp))
            if (pendingChores.isEmpty()) {
                com.example.ui.components.ZadEmptyState(title = stringResource(R.string.no_upcoming_tasks))
            } else {
                pendingChores.forEach { chore ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = secondary.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = secondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chore.title, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 14.sp)
                                if (showFinancials && chore.rewardAmount > 0) {
                                    Text(stringResource(R.string.reward_colon, com.example.data.CurrencyFormatter.format(currencyContext, chore.rewardAmount)), color = secondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tasbiha trees
            if (memberTrees.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(18.dp), tint = successColor)
                    Text(stringResource(R.string.tasbiha_trees_count, memberTrees.size), fontWeight = FontWeight.Bold, color = onSurface)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memberTrees) { tree ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(tree.stageEmoji(), fontSize = 28.sp)
                                Text(tree.treeName, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 12.sp)
                                Text("${tree.score}", color = primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Spend limits (parent-managed, child only)
            if (showFinancials && member.role == "child" && state.myMemberInfo.role == "admin") {
                Spacer(Modifier.height(16.dp))
                SpendLimitSection(member = member, messages = state.messages, onEdit = { showLimitDialog = true })
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLimitDialog) {
        SpendLimitDialog(
            member = member,
            onConfirm = { daily, weekly ->
                viewModel.updateSpendLimits(member.id, daily, weekly)
                showLimitDialog = false
            },
            onDismiss = { showLimitDialog = false }
        )
    }
}

@Composable
private fun SpendLimitSection(member: com.example.data.FamilyMember, messages: List<ChatMessage>, onEdit: () -> Unit) {
    val currencyContext = LocalContext.current
    val now = java.time.Instant.now()
    val spentToday = com.example.data.approvedSpendSince(messages, member.id, now.minus(1, java.time.temporal.ChronoUnit.DAYS))
    val spentThisWeek = com.example.data.approvedSpendSince(messages, member.id, now.minus(7, java.time.temporal.ChronoUnit.DAYS))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp), tint = secondary)
        Text(stringResource(R.string.spend_limits_title), fontWeight = FontWeight.Bold, color = onSurface, modifier = Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_limits_action), fontSize = 12.sp) }
    }
    Spacer(Modifier.height(8.dp))
    SpendLimitBar(stringResource(R.string.daily_limit_label), spentToday, member.dailyLimit, currencyContext)
    Spacer(Modifier.height(8.dp))
    SpendLimitBar(stringResource(R.string.weekly_limit_label), spentThisWeek, member.weeklyLimit, currencyContext)
}

@Composable
fun SpendLimitBar(label: String, spent: Double, limit: Double?, currencyContext: android.content.Context) {
    if (limit == null || limit <= 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Typography.labelSmall, color = onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.no_limit_set), style = Typography.labelSmall, color = onSurfaceVariant)
        }
        return
    }
    val ratio = (spent / limit).toFloat().coerceIn(0f, 1f)
    val barColor = when {
        spent / limit >= 1.0 -> dangerColor
        spent / limit >= 0.8 -> warningColor
        else -> primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Typography.labelSmall, color = onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = onSurface.copy(alpha = 0.1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${com.example.data.CurrencyFormatter.format(currencyContext, spent)} / ${com.example.data.CurrencyFormatter.format(currencyContext, limit)}",
            style = Typography.labelSmall, color = barColor, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SpendLimitDialog(
    member: com.example.data.FamilyMember,
    onConfirm: (Double?, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var dailyStr by remember { mutableStateOf(member.dailyLimit?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } ?: "") }
    var weeklyStr by remember { mutableStateOf(member.weeklyLimit?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() } ?: "") }
    val currencySymbol = com.example.data.CurrencyFormatter.symbol(LocalContext.current)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spend_limits_title)) },
        text = {
            Column {
                Text(stringResource(R.string.spend_limits_hint, member.alias), style = Typography.bodySmall, color = onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dailyStr, onValueChange = { dailyStr = it },
                    label = { Text(stringResource(R.string.daily_limit_with_currency, currencySymbol)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = weeklyStr, onValueChange = { weeklyStr = it },
                    label = { Text(stringResource(R.string.weekly_limit_with_currency, currencySymbol)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(dailyStr.toDoubleOrNull(), weeklyStr.toDoubleOrNull())
            }) { Text(stringResource(R.string.done_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 18.sp)
        Text(label, color = onSurfaceVariant, fontSize = 12.sp)
    }
}

// ── TASKS TAB ──
@Composable
private fun TasksTab(
    chores: List<Chore>,
    members: List<com.example.data.FamilyMember>,
    isAdmin: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onAddChore: (String, String, String?, Double) -> Unit,
    showFinancials: Boolean = true
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // The chip label and the value compared in the `when` blocks below used to be the same
    // Arabic string, so translating the chip would have silently matched nothing and every
    // filter would have fallen through to "all". Key is stable, label is looked up.
    var selectedFilter by remember { mutableStateOf("all") }
    val filterKeys = listOf("all", "pending", "done")
    val context = androidx.compose.ui.platform.LocalContext.current

    // Group chores by member
    val choresByMember = members.associateWith { member ->
        chores.filter { it.assignedTo == member.id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(22.dp), tint = primary)
                    Text(stringResource(R.string.family_tasks), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                }
                if (isAdmin && showFinancials) {
                    FilledTonalButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_action))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.tasks_completed_count, chores.count { it.isCompleted }, chores.size),
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        // Filter chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterKeys) { key ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                when (key) {
                                    "pending" -> stringResource(R.string.chores_filter_pending)
                                    "done" -> stringResource(R.string.chores_filter_done)
                                    else -> stringResource(R.string.chores_filter_all)
                                }
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // مفيش أي مهمة تتعرض (مفيش مهام أصلاً، أو الفلتر الحالي مطلعش حاجة) — كانت التاب
        // بتفضل فاضية تماماً (بس العنوان + الفلاتر) من غير أي رسالة توضح إن ده طبيعي.
        val hasAnyVisibleChore = choresByMember.any { (_, memberChores) ->
            when (selectedFilter) {
                "pending" -> memberChores.any { !it.isCompleted }
                "done" -> memberChores.any { it.isCompleted }
                else -> memberChores.isNotEmpty()
            }
        }
        if (!hasAnyVisibleChore) {
            item {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.Assignment,
                    title = stringResource(R.string.no_family_tasks_title),
                    subtitle = stringResource(R.string.no_family_tasks_subtitle),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                )
            }
        }

        // Per-member task sections
        choresByMember.forEach { (member, memberChores) ->
            val filteredChores = when (selectedFilter) {
                "pending" -> memberChores.filter { !it.isCompleted }
                "done" -> memberChores.filter { it.isCompleted }
                else -> memberChores
            }

            if (filteredChores.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member.alias.take(1).uppercase(), color = primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(member.alias, fontWeight = FontWeight.Bold, color = onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "(${memberChores.count { it.isCompleted }}/${memberChores.size})",
                            color = onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                itemsIndexed(filteredChores) { index, chore ->
                    com.example.ui.components.AppearOnEntry(delayMs = (index * 40).coerceAtMost(400)) {
                    val choreShape = RoundedCornerShape(16.dp)
                    com.example.ui.components.ZadListCard(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { onToggle(chore.id, !chore.isCompleted) }
                            .pressableScale(),
                        shape = choreShape,
                        containerColor = if (chore.isCompleted) primary.copy(alpha = 0.08f) else surface,
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (chore.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (chore.isCompleted) primary else outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    chore.title,
                                    fontWeight = FontWeight.Bold,
                                    style = Typography.bodyLarge,
                                    color = if (chore.isCompleted) onSurfaceVariant else onSurface,
                                    textDecoration = if (chore.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (showFinancials && chore.rewardAmount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = (if (chore.isCompleted) primary else secondary).copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                com.example.data.CurrencyFormatter.format(context, chore.rewardAmount),
                                                color = if (chore.isCompleted) primary else secondary,
                                                style = Typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (chore.dueDate != null) {
                                        Text(
                                            chore.dueDate.take(10),
                                            style = Typography.labelSmall,
                                            color = onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showAddDialog) {
        AddChoreDialog(
            members = members,
            onConfirm = { assignedTo, title, dueDate, reward ->
                onAddChore(assignedTo, title, dueDate, reward)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun AddChoreDialog(
    members: List<com.example.data.FamilyMember>,
    onConfirm: (String, String, String?, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var assignedTo by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }
    var dueDate by remember { mutableStateOf("") }
    var rewardAmount by remember { mutableStateOf("") }
    val currencyContext = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_new_task)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_name)) },
                    placeholder = { Text(stringResource(R.string.eg_tidy_room)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rewardAmount, onValueChange = { rewardAmount = it },
                    label = { Text(stringResource(R.string.reward_with_currency, com.example.data.CurrencyFormatter.symbol(currencyContext))) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.assigned_to_colon), style = Typography.labelMedium, color = onSurface)
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(members) { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { assignedTo = member.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = assignedTo == member.id, onClick = { assignedTo = member.id })
                            Spacer(Modifier.width(8.dp))
                            Text(member.alias)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it },
                    label = { Text(stringResource(R.string.due_date_optional)) },
                    placeholder = { Text("2025-01-01") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && assignedTo.isNotBlank()) {
                        val reward = rewardAmount.toDoubleOrNull() ?: 0.0
                        onConfirm(assignedTo, title, if (dueDate.isNotBlank()) dueDate else null, reward)
                    }
                },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.add_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ── INVITE DIALOG ──
@Composable
private fun InviteMemberDialog(
    inviteCode: String,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(0) }
    var showSentConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val joinFamilyText = stringResource(R.string.join_family_message)
    val shareText = "$joinFamilyText\n${stringResource(R.string.invite_code_colon)} $inviteCode\n${stringResource(R.string.or_open_link)} zad://invite?code=$inviteCode"
    val shareInviteTitle = stringResource(R.string.share_invite_title)
    val joinScanText = stringResource(R.string.join_family_scan_message, inviteCode)
    val joinLinkText = stringResource(R.string.join_family_link_message, inviteCode)
    val joinCodeText = stringResource(R.string.join_family_code_message, inviteCode)

    // تأكيد بصري عابر بعد إرسال الدعوة — بيقفل نفسه لوحده زي toast
    LaunchedEffect(showSentConfirmation) {
        if (showSentConfirmation) {
            delay(1200)
            showSentConfirmation = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.invite_new_members))
            }
        },
        text = {
            Column {
                // Method selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = selectedMethod == 0,
                        onClick = { selectedMethod = 0 },
                        label = { Text("QR") }
                    )
                    FilterChip(
                        selected = selectedMethod == 1,
                        onClick = { selectedMethod = 1 },
                        label = { Text(stringResource(R.string.link_method)) }
                    )
                    FilterChip(
                        selected = selectedMethod == 2,
                        onClick = { selectedMethod = 2 },
                        label = { Text(stringResource(R.string.code_method)) }
                    )
                    FilterChip(
                        selected = selectedMethod == 3,
                        onClick = { selectedMethod = 3 },
                        label = { Text(stringResource(R.string.whatsapp_method)) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (selectedMethod) {
                    0 -> {
                        Text(stringResource(R.string.scan_to_join), style = Typography.labelMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            QrCode(content = "zad://invite?code=$inviteCode")
                        }
                    }
                    1 -> {
                        Text(stringResource(R.string.invite_link_colon), style = Typography.labelMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = surfaceContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("zad://invite?code=$inviteCode", modifier = Modifier.weight(1f), fontSize = 12.sp, color = primary)
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Invite", "zad://invite?code=$inviteCode"))
                                    showSentConfirmation = true
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, tint = primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    2 -> {
                        Text(stringResource(R.string.invite_code_colon), style = Typography.labelMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = primary.copy(alpha = 0.1f)) {
                            Text(inviteCode, modifier = Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Bold, color = primary, fontSize = 24.sp, textAlign = TextAlign.Center)
                        }
                    }
                    3 -> {
                        Text(stringResource(R.string.share_via_whatsapp_colon), style = Typography.labelMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    try {
                                        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            `package` = "com.whatsapp"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(whatsappIntent)
                                    } catch (e: Exception) {
                                        val fallback = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(fallback, shareInviteTitle))
                                    }
                                    showSentConfirmation = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.whatsapp_method), color = Color.White)
                                }
                            }
                            Button(
                                onClick = {
                                    val general = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(general, shareInviteTitle))
                                    showSentConfirmation = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = onSurface, modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.all_apps))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val shareText = when (selectedMethod) {
                            0 -> joinScanText
                            1 -> joinLinkText
                            else -> joinCodeText
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, shareInviteTitle))
                        showSentConfirmation = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_action))
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showSentConfirmation,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZadLottieAsset(
                            resId = R.raw.lottie_success_check,
                            iterations = 1,
                            modifier = Modifier.size(28.dp),
                            contentDescription = stringResource(R.string.done_action)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.done_action), color = primary, fontWeight = FontWeight.Bold, style = Typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done_action)) }
        }
    )
}

// ── JOIN FAMILY DIALOG ──
@Composable
private fun JoinFamilyDialog(
    initialCode: String = "",
    onDismiss: () -> Unit,
    onJoin: (code: String, alias: String) -> Unit
) {
    var inviteCode by remember { mutableStateOf(initialCode) }
    var alias by remember { mutableStateOf("") }
    val context = LocalContext.current
    var scanError by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val rawCode = decodeQrFromBitmap(bitmap)
                if (!rawCode.isNullOrBlank()) {
                    inviteCode = extractInviteCode(rawCode)
                    scanError = false
                } else {
                    scanError = true
                }
            } catch (e: Exception) {
                Log.e(TAG_FAM, "Error decoding QR from gallery image", e)
                scanError = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.join_family_action))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuthTextField(
                    label = stringResource(R.string.your_alias_label),
                    value = alias,
                    onValueChange = { alias = it },
                    placeholder = stringResource(R.string.eg_child_alias)
                )

                AuthTextField(
                    label = stringResource(R.string.invite_code_label),
                    value = inviteCode,
                    onValueChange = { inviteCode = extractInviteCode(it) },
                    placeholder = stringResource(R.string.eg_invite_code)
                )

                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().pressableScale(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_to_join), fontSize = 14.sp)
                }

                if (scanError) {
                    Text(
                        stringResource(R.string.changes_save_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(inviteCode.trim(), alias.trim()) },
                enabled = inviteCode.isNotBlank() && alias.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.join_family_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ── EXISTING TABS (Groceries, Chat, Etc.) ──



@Composable
fun BudgetGoalsTab(goals: List<FamilyGoal>, members: List<com.example.data.FamilyMember>, chores: List<Chore>, viewModel: com.example.ui.viewmodels.FamilyViewModel) {
    val context = LocalContext.current
    val kids = members.filter { it.role != "admin" }
    val totalBalance = kids.sumOf { it.balance }
    val totalPaidRewards = chores.filter { it.isCompleted }.sumOf { it.rewardAmount }
    val suggestedGoal by viewModel.suggestedGoal.collectAsState()
    val isSuggestingGoal by viewModel.isSuggestingGoal.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(20.dp), tint = primary)
                Text(stringResource(R.string.budget_and_rewards), style = Typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            val currentGoal = goals.maxByOrNull { it.monthYear }
            com.example.ui.components.ZadListCard(
                modifier = Modifier.padding(bottom = 16.dp),
                contentPadding = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.family_savings_goal_title), style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (currentGoal != null) {
                        val fraction = if (currentGoal.targetAmount > 0) (currentGoal.currentAmount / currentGoal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                        Text("${com.example.data.CurrencyFormatter.format(context, currentGoal.currentAmount)} / ${com.example.data.CurrencyFormatter.format(context, currentGoal.targetAmount)}", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        val animatedFamilyGoal by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = com.example.ui.components.ZadSprings.Screen,
                            label = "familyGoalProgress"
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(onSurface.copy(alpha = 0.1f))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(animatedFamilyGoal).height(10.dp).clip(RoundedCornerShape(5.dp)).background(primary))
                        }
                        if (!currentGoal.rewardSuggestion.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text(currentGoal.rewardSuggestion, style = Typography.bodySmall, color = onSurfaceVariant)
                            }
                        }
                    } else {
                        Text(stringResource(R.string.family_savings_goal_empty), style = Typography.bodySmall, color = onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.suggestFamilyGoal() },
                            enabled = !isSuggestingGoal,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSuggestingGoal) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = onPrimary)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.family_savings_goal_suggest))
                            }
                        }
                    }
                }
            }
        }

        if (kids.isEmpty()) {
            item { com.example.ui.components.ZadEmptyState(title = stringResource(R.string.no_children_for_stats)) }
            return@LazyColumn
        }

        item {
            com.example.ui.components.ZadListCard(
                modifier = Modifier.padding(bottom = 16.dp),
                contentPadding = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.children_balance_stats), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))

                    kids.forEach { kid ->
                        val maxBalance = maxOf(kids.maxOfOrNull { it.balance } ?: 1.0, 1.0)
                        val fraction = (kid.balance / maxBalance).toFloat()
                        val animatedFraction by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = fraction,
                            animationSpec = com.example.ui.components.ZadSprings.Screen,
                            label = "kidBalanceFraction"
                        )

                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(kid.alias, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(com.example.data.CurrencyFormatter.format(context, kid.balance), style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(onSurface.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Brush.horizontalGradient(colors = listOf(primaryLight, primary)))
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.total_rewards_paid), style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(com.example.data.CurrencyFormatter.format(context, totalPaidRewards), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
                    }
                }
            }
        }
    }

    suggestedGoal?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { viewModel.clearSuggestedGoal() },
            title = { Text("${suggestion.emoji} ${suggestion.goalTitle}", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.family_savings_goal_target, com.example.data.CurrencyFormatter.format(context, suggestion.targetAmount), suggestion.durationDays), style = Typography.bodyMedium)
                    if (suggestion.rewardSuggestion.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Text(suggestion.rewardSuggestion, style = Typography.bodySmall, color = onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.createSuggestedGoal() }) { Text(stringResource(R.string.family_savings_goal_approve)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearSuggestedGoal() }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

// ── CHAT TAB ──
@Composable
fun ChatTab(
    messages: List<ChatMessage>,
    members: List<com.example.data.FamilyMember>,
    myMemberInfo: com.example.data.FamilyMember,
    onSendMessage: (String, String, String?) -> Unit,
    onUpdateRequestStatus: (String, String, String) -> Unit,
    viewModel: FamilyViewModel? = null,
    // مفيش أرقام مالية عائلية في تاب الشات نفسه — بس محتفظ بيه هنا عشان يفضل نفس الشكل
    // مع باقي التابات، ولو حبينا نستخدمه مستقبلاً (زي إخفاء اقتراح "طلب شراء" في وضع الأطفال)
    showFinancials: Boolean = true
) {
    var text by remember { mutableStateOf("") }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showGroceryQuickDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf<String?>(null) }
    var showQuickReplies by remember { mutableStateOf(false) }
    val sosMessageText = stringResource(R.string.sos_message)
    val context = LocalContext.current
    val needAmountPattern = stringResource(R.string.need_amount_purchase)
    val haptic = LocalHapticFeedback.current

    // Start typing monitor. The loop runs *inside* this effect now, so leaving the screen
    // cancels it — it used to hand the loop to viewModelScope and every visit stacked
    // another one that outlived the screen.
    LaunchedEffect(myMemberInfo.familyId) {
        viewModel?.monitorTyping(myMemberInfo.familyId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Online members bar
        val onlineMembers = remember(members) {
            members.filter { it.isOnline && it.id != myMemberInfo.id }
        }
        if (onlineMembers.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = primary.copy(alpha = 0.05f)
            ) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(onlineMembers) { member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(successColor))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(member.alias, fontSize = 12.sp, color = onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            // كانت اللستة بتتعرض فاضية تماماً — بس الـ Spacer وعنصر typing indicator لو
            // موجود، من غير أي حاجة توضح إن ده تشات جديد لسه ملوش رسائل. دلوقتي فيه أفاتار
            // + عنوان + شيبس تفاعلية (تودي مباشرة لنفس الحوارات اللي شريط الأيقونات تحت بيفتحها).
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.components.ZadEmptyState(
                    icon = Icons.Default.Chat,
                    title = stringResource(R.string.no_messages_yet_title),
                    subtitle = stringResource(R.string.no_messages_yet_subtitle),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip(
                                onClick = { showTaskDialog = true },
                                icon = { Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.empty_chat_suggest_task)) }
                            )
                            SuggestionChip(
                                onClick = { showGroceryQuickDialog = true },
                                icon = { Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.empty_chat_suggest_grocery)) }
                            )
                            SuggestionChip(
                                onClick = { text = "@Zad " },
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.empty_chat_suggest_ask_zad)) }
                            )
                        }
                    }
                )
            }
        } else {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Group messages by date for date separators — messages.reversed() iterates
            // newest→oldest, but reverseLayout=true means the FIRST-composed item lands at
            // the BOTTOM of the screen. Emitting a separator *before* the newest message of
            // each date group (old code) therefore visually placed it *below* that group
            // instead of above it. Fix: emit the separator *after* the OLDEST message of a
            // group (index == lastIndex, or the next message belongs to a different date) —
            // composing it later means it renders higher up, i.e. correctly above the group.
            val groupedMessages = messages.reversed()

            itemsIndexed(groupedMessages) { index, msg ->
                val msgDate = msg.createdAt?.take(10) ?: ""
                val today = java.time.LocalDate.now().toString()
                val yesterday = java.time.LocalDate.now().minusDays(1).toString()
                val dateLabel = when (msgDate) {
                    today -> stringResource(R.string.today_label)
                    yesterday -> stringResource(R.string.yesterday_label)
                    else -> msgDate.replace("-", "/")
                }
                val nextDate = groupedMessages.getOrNull(index + 1)?.createdAt?.take(10) ?: ""
                val isGroupBoundary = msgDate.isNotBlank() && (index == groupedMessages.lastIndex || nextDate != msgDate)

                val isMe = msg.senderId == myMemberInfo.id
                val sender = members.find { it.id == msg.senderId }
                val senderAlias = sender?.alias ?: "Zad AI"
                val isAi = msg.senderId == "zad_ai"

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        if (!isMe && !isAi) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(senderAlias.take(1), color = primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        when (msg.messageType) {
                            "SOS" -> SosBubble(msg, senderAlias)
                            "PURCHASE_REQUEST" -> PurchaseBubble(msg, senderAlias, myMemberInfo, isMe, onUpdateRequestStatus)
                            "POLL" -> PollBubble(msg, senderAlias, members)
                            else -> TextBubble(
                                msg = msg, isMe = isMe, isAi = isAi, senderAlias = senderAlias,
                                onPin = { viewModel?.togglePinMessage(msg.id) },
                                onReact = { viewModel?.toggleReaction(msg.id, it) },
                                showEmojiPicker = showEmojiPicker == msg.id,
                                onToggleEmojiPicker = { showEmojiPicker = if (showEmojiPicker == msg.id) null else msg.id }
                            )
                        }
                    }
                    if (msg.reactions != null && msg.reactions!!.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Box(modifier = Modifier.padding(start = if (isMe) 0.dp else 40.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    msg.reactions!!.split(", ").filter { it.contains(":") }.forEach { entry ->
                                        val parts = entry.split(":")
                                        if (parts.size == 2) {
                                            Surface(shape = RoundedCornerShape(12.dp), color = surfaceContainer, modifier = Modifier.height(24.dp)) {
                                                Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                    Text("${parts[0]} ${parts[1]}", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isGroupBoundary) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(12.dp), color = onSurface.copy(alpha = 0.08f)) {
                            Text(dateLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 11.sp, color = onSurfaceVariant)
                        }
                    }
                }
            }

            // Typing indicator
            val typingUserIds = viewModel?.typingUsers ?: emptySet()
            val typingMembers = members.filter { it.id in typingUserIds && it.id != myMemberInfo.id }
            if (typingMembers.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        val typingNames = typingMembers.joinToString(", ") { it.alias }
                        Surface(shape = RoundedCornerShape(12.dp), color = surfaceContainer) {
                            Text(
                                // Was "يكتب" + "ون" glued on for the plural. That only works
                                // in Arabic; every other locale needs a whole different
                                // sentence, so both forms are full strings now.
                                if (typingMembers.size > 1)
                                    stringResource(R.string.family_typing_many, typingNames)
                                else
                                    stringResource(R.string.family_typing_one, typingNames),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        }

        // Input — navigationBarsPadding/imePadding عشان الشريط ميقعدش تحت الـ nav bar أو
        // لوحة المفاتيح؛ كان معتمد بالكامل على innerPadding بتاعة الـ Scaffold في MainScreen
        // من غيرهم، وده مش مضمون وقت فتح الكيبورد تحديداً.
        Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Box(modifier = Modifier.matchParentSize().zadGlassBlur(16.dp).background(surface.copy(alpha = 0.9f)))
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Column {
                // Quick replies
                if (showQuickReplies) {
                    val quickReplies = viewModel?.getQuickReplies() ?: emptyList()
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(quickReplies) { reply ->
                            Surface(
                                modifier = Modifier.clickable {
                                    onSendMessage(reply.text, "TEXT", null)
                                    showQuickReplies = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = primaryContainer.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    "${reply.emoji} ${reply.text}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 13.sp,
                                    color = onSurface
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // شريط أيقونات قابل للتمرير — بعد إضافة مهمة/تسوق بقى ٦ أزرار، تكديسها
                    // في صف ثابت العرض كان هيضغطها/يقصّها؛ التمرير الأفقي يحافظ على ٤٨دp
                    // (حد أدنى مساحة اللمس) لكل زرار بدل تصغيرها. widthIn(max) هنا يمنع
                    // الشريط ده من ابتلاع عرض الصف كله على شاشة ضيقة — كان بيسيب حقل الكتابة
                    // بعرض شبه معدوم لحد ما تعمل سكرول للأيقونات الأول (تراكب فعلي مع منطقة الشات).
                    Row(
                        modifier = Modifier.widthIn(max = 150.dp).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    IconButton(onClick = { showQuickReplies = !showQuickReplies }, modifier = Modifier.pressableScale()) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Replies", tint = primary)
                    }
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSendMessage(sosMessageText, "SOS", null)
                        },
                        modifier = Modifier.pressableScale()
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "SOS", tint = dangerColor)
                    }
                    IconButton(onClick = { showPurchaseDialog = true }, modifier = Modifier.pressableScale()) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Request", tint = primary)
                    }
                    IconButton(onClick = { showPollDialog = true }, modifier = Modifier.pressableScale()) {
                        Icon(Icons.Default.BarChart, contentDescription = "Poll", tint = secondary)
                    }
                    IconButton(onClick = { showTaskDialog = true }, modifier = Modifier.pressableScale()) {
                        Icon(Icons.Default.Assignment, contentDescription = stringResource(R.string.tasks_tab), tint = primary)
                    }
                    IconButton(onClick = { showGroceryQuickDialog = true }, modifier = Modifier.pressableScale()) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = stringResource(R.string.groceries_tab), tint = secondary)
                    }
                    }
                    Row(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(surfaceContainerLow).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = text, onValueChange = {
                                text = it
                                viewModel?.onTyping(myMemberInfo.familyId)
                            },
                            placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        IconButton(
                            onClick = {
                                if (text.isNotBlank()) { onSendMessage(text, "TEXT", null); text = "" }
                            },
                            modifier = Modifier.pressableScale()
                        ) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (showPurchaseDialog) {
        var purchaseTitle by remember { mutableStateOf("") }
        var purchaseAmount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPurchaseDialog = false },
            title = { Text(stringResource(R.string.expense_purchase_request)) },
            text = {
                Column {
                    OutlinedTextField(value = purchaseTitle, onValueChange = { purchaseTitle = it }, label = { Text(stringResource(R.string.what_to_buy)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = purchaseAmount, onValueChange = { purchaseAmount = it }, label = { Text(stringResource(R.string.requested_amount_with_currency, com.example.data.CurrencyFormatter.symbol(context))) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (purchaseTitle.isNotBlank() && purchaseAmount.isNotBlank()) {
                        val meta = """{"amount":${purchaseAmount.toDoubleOrNull() ?: 0.0}, "status":"PENDING"}"""
                        onSendMessage(String.format(needAmountPattern, purchaseAmount, purchaseTitle), "PURCHASE_REQUEST", meta)
                        showPurchaseDialog = false
                    }
                }) { Text(stringResource(R.string.send_request)) }
            },
            dismissButton = { TextButton(onClick = { showPurchaseDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showPollDialog) {
        var pollQuestion by remember { mutableStateOf("") }
        var pollOptions by remember { mutableStateOf(listOf("", "")) }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text(stringResource(R.string.family_poll)) },
            text = {
                Column {
                    OutlinedTextField(value = pollQuestion, onValueChange = { pollQuestion = it }, label = { Text(stringResource(R.string.question_label)) }, modifier = Modifier.fillMaxWidth())
                    pollOptions.forEachIndexed { i, opt ->
                        OutlinedTextField(value = opt, onValueChange = { n -> pollOptions = pollOptions.toMutableList().apply { set(i, n) } },
                            label = { Text(stringResource(R.string.option_number, i + 1)) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    TextButton(onClick = { pollOptions = pollOptions + "" }) { Text(stringResource(R.string.add_option)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val valid = pollOptions.filter { it.isNotBlank() }
                    if (pollQuestion.isNotBlank() && valid.size >= 2) {
                        val optsJson = kotlinx.serialization.json.Json.encodeToString(valid)
                        val meta = """{"question":"$pollQuestion","options":$optsJson,"votes":{}}"""
                        onSendMessage("📊 $pollQuestion", "POLL", meta)
                        showPollDialog = false
                    }
                }) { Text(stringResource(R.string.send_poll)) }
            },
            dismissButton = { TextButton(onClick = { showPollDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // مهمة/عنصر تسوق مباشرة من شريط الشات — كان الوحيد اللي يقدر يضيف عنصر تسوق أصلاً هو
    // كتابة "أضف حليب" كنص حر (تاب المخزون العائلي مفيهوش زرار إضافة خالص)، ومفيش أي طريقة
    // لإضافة مهمة من غير الخروج لتاب المهام. الاتنين بيستخدموا نفس المسارات الحقيقية
    // (viewModel.addChore / quickAddGroceryItem) اللي التابات المنفصلة بتستخدمها.
    if (showTaskDialog) {
        var taskTitle by remember { mutableStateOf("") }
        var selectedAssignee by remember { mutableStateOf(myMemberInfo.id) }
        var showAssigneeMenu by remember { mutableStateOf(false) }
        val assigneeAlias = members.find { it.id == selectedAssignee }?.alias ?: myMemberInfo.alias
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text(stringResource(R.string.add_new_task)) },
            text = {
                Column {
                    OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it }, label = { Text(stringResource(R.string.task_name)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.assigned_to_colon), style = Typography.labelMedium, color = onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedTextField(
                            value = assigneeAlias, onValueChange = {}, readOnly = true, singleLine = true,
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.clickable { showAssigneeMenu = true }) },
                            modifier = Modifier.fillMaxWidth().clickable { showAssigneeMenu = true }
                        )
                        DropdownMenu(expanded = showAssigneeMenu, onDismissRequest = { showAssigneeMenu = false }) {
                            members.forEach { m ->
                                DropdownMenuItem(text = { Text(m.alias) }, onClick = { selectedAssignee = m.id; showAssigneeMenu = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (taskTitle.isNotBlank()) {
                        viewModel?.addChore(selectedAssignee, taskTitle.trim(), null, 0.0)
                        showTaskDialog = false
                    }
                }) { Text(stringResource(R.string.add_action)) }
            },
            dismissButton = { TextButton(onClick = { showTaskDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (showGroceryQuickDialog) {
        var groceryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGroceryQuickDialog = false },
            title = { Text(stringResource(R.string.add_to_shopping_list)) },
            text = {
                OutlinedTextField(value = groceryName, onValueChange = { groceryName = it }, placeholder = { Text(stringResource(R.string.eg_milk_placeholder)) }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    if (groceryName.isNotBlank()) {
                        viewModel?.quickAddGroceryItem(groceryName.trim())
                        showGroceryQuickDialog = false
                    }
                }) { Text(stringResource(R.string.add_action)) }
            },
            dismissButton = { TextButton(onClick = { showGroceryQuickDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/**
 * كانت خلفية حمراء مصمتة (Box.background(dangerColor)) — "raw red bubble" بالظبط زي ما
 * اتوصف. بدّلتها بنفس لغة كارت التنبيه الموجودة أصلاً (InventoryScreen's LowStockBanner):
 * خلفية دانجر فاتحة + شارة دائرية بلون مصمت للأيقونة + نص ملوّن، مش خلفية كاملة مصمتة —
 * كارت M3 حقيقي، مش لافتة تحذير خام.
 */
@Composable
private fun SosBubble(msg: ChatMessage, senderAlias: String) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(shape)
            .background(dangerColor.copy(alpha = 0.1f))
            .border(1.dp, dangerColor.copy(alpha = 0.3f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(dangerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.sos_call_from, senderAlias), color = dangerColor, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(msg.message ?: "", color = onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(formatMessageTime(msg.createdAt), style = Typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

@Composable
private fun PurchaseBubble(msg: ChatMessage, senderAlias: String, myMemberInfo: com.example.data.FamilyMember, isMe: Boolean, onUpdateRequestStatus: (String, String, String) -> Unit) {
    val approvedPattern = stringResource(R.string.request_approved_message)
    val rejectedPattern = stringResource(R.string.request_rejected_message)
    val haptic = LocalHapticFeedback.current
    val bubbleShape = RoundedCornerShape(16.dp)
    Box(modifier = Modifier.fillMaxWidth(0.85f).clip(bubbleShape)) {
        Box(modifier = Modifier.matchParentSize().zadGlassBlur(14.dp).background(surfaceContainerHigh.copy(alpha = 0.8f)))
        Column(modifier = Modifier.border(1.dp, primary, bubbleShape).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Request", tint = primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.purchase_request_from, senderAlias), fontWeight = FontWeight.Bold, color = primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(msg.message ?: "", color = onSurface)
            val isPending = msg.metadata?.contains("\"status\":\"PENDING\"") == true
            val isApproved = msg.metadata?.contains("\"status\":\"APPROVED\"") == true
            val isRejected = msg.metadata?.contains("\"status\":\"REJECTED\"") == true
            if (isPending && myMemberInfo.role == "admin" && !isMe) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUpdateRequestStatus(msg.id, "APPROVED", String.format(approvedPattern, msg.message))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        modifier = Modifier.pressableScale()
                    ) { Text(stringResource(R.string.approve_and_deduct)) }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUpdateRequestStatus(msg.id, "REJECTED", String.format(rejectedPattern, msg.message))
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor),
                        modifier = Modifier.pressableScale()
                    ) { Text(stringResource(R.string.reject)) }
                }
            } else if (isApproved) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                    Text(stringResource(R.string.request_approved), style = Typography.labelMedium, color = primary, fontWeight = FontWeight.Bold)
                }
            } else if (isRejected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = dangerColor)
                    Text(stringResource(R.string.request_rejected), style = Typography.labelMedium, color = dangerColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(formatMessageTime(msg.createdAt), style = Typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

@Composable
private fun PollBubble(msg: ChatMessage, senderAlias: String, members: List<com.example.data.FamilyMember>) {
    val question = msg.metadata?.let {
        try { it.substringAfter("\"question\":\"").substringBefore("\"").replace("\\\"", "\"") } catch (e: Exception) { msg.message }
    } ?: msg.message
    val options = msg.metadata?.let {
        try {
            val optsRaw = it.substringAfter("\"options\":").substringBefore("],\"votes")
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<String>>(optsRaw)
        } catch (e: Exception) { emptyList() }
    } ?: emptyList()

    Box(modifier = Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(16.dp)).background(surfaceContainerHigh).border(1.dp, secondary, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = "Poll", tint = secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.poll_from, senderAlias), fontWeight = FontWeight.Bold, color = secondary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(question.removePrefix("📊 ").removePrefix("POLL: "), fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            options.forEachIndexed { i, opt ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { }.background(surfaceContainer).padding(12.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. $opt", color = onSurface)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(formatMessageTime(msg.createdAt), style = Typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

@Composable
private fun TextBubble(msg: ChatMessage, isMe: Boolean, isAi: Boolean, senderAlias: String, onPin: () -> Unit, onReact: (String) -> Unit, showEmojiPicker: Boolean, onToggleEmojiPicker: () -> Unit) {
    Column {
        // فقاعة الـ AI كانت بتاخد نفس خلفية أي عضو تاني (surfaceContainerHigh) — تمييزها
        // الوحيد كان لون اسم المرسل. دلوقتي خلفية متمازجة (primaryContainer) + أيقونة
        // ✨ جنب الاسم — فقاعة زاد بقت متعرّفة بصرياً من أول نظرة، مش لازم تقرا الاسم.
        val bubbleColor = when {
            isMe -> primary
            isAi -> primaryContainer.copy(alpha = 0.45f)
            else -> surfaceContainerHigh
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMe) 16.dp else 4.dp, bottomEnd = if (isMe) 4.dp else 16.dp))
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
                if (!isMe) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAi) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(senderAlias, fontSize = 10.sp, color = if (isAi) primary else onSurfaceVariant, fontWeight = FontWeight.Bold)
                        if (msg.isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = if (isAi) primary else onSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                        if (msg.voiceUrl != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Mic, contentDescription = "Voice", tint = if (isAi) primary else onSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                } else {
                    if (msg.isPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    }
                }
                if (msg.voiceUrl != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isMe) Color.White else primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.voice_message), color = if (isMe) Color.White else onSurface, fontSize = 13.sp)
                    }
                } else {
                    Text(msg.message ?: "", color = if (isMe) Color.White else onSurface)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        formatMessageTime(msg.createdAt),
                        fontSize = 9.sp,
                        color = if (isMe) Color.White.copy(alpha = 0.7f) else onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    // إيصال قراءة حقيقي محتاج عمود seen_by/is_read على chat_messages (مش
                    // موجود دلوقتي) — علامة صح مزدوجة زرقاء بس لرسالتي أنا لحد ما العمود
                    // ده يتضاف، عشان ميبقاش "إيصال" وهمي بيقول تم القراءة وهو مش متأكد.
                    if (isMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Row(modifier = Modifier.width(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onPin, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (msg.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = "Pin",
                    tint = if (msg.isPinned) primary else onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(onClick = onToggleEmojiPicker, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.EmojiEmotions, contentDescription = "React", tint = onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
        }
        if (showEmojiPicker) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F").forEach { emoji ->
                    Surface(
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onReact(emoji); onToggleEmojiPicker() },
                        shape = CircleShape, color = surface
                    ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 14.sp) } }
                }
            }
        }
    }
}

