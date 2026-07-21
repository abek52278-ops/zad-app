package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.screens.auth.AuthTextField
import com.example.ui.theme.*
import com.example.ui.components.QrCode
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.FamilyState
import android.util.Log
import kotlinx.serialization.encodeToString
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import kotlinx.coroutines.launch

private const val TAG_FAM = "FamilyScreen"

@Composable
fun FamilyScreen(
    pendingInviteCode: String? = null,
    viewModel: FamilyViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onNotificationsClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            FamilyTopBar(onOpenDrawer, unreadCount = unreadNotificationCount, onNotificationsClick = onNotificationsClick)

            when (val currState = state) {
                is FamilyState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primary)
                    }
                }
                is FamilyState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(currState.message, color = MaterialTheme.colorScheme.error)
                    }
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
                        onUpdateRequestStatus = { messageId, newStatus, replyMsg -> viewModel.updateRequestStatus(messageId, newStatus, replyMsg) }
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
    var inviteCode by remember { mutableStateOf(pendingInviteCode ?: "") }
    var alias by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FamilyRestroom,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "أهلاً بك في عائلة زاد",
            style = Typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "يمكنك إنشاء عائلة جديدة لتكون أنت المدير، أو الانضمام لعائلة موجودة عبر كود الدعوة.",
            style = Typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onCreateFamily,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "إنشاء عائلة جديدة كمدير (Admin)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("أو", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        AuthTextField(label = "اسمك (Alias)", value = alias, onValueChange = { alias = it }, placeholder = "مثال: الابن أحمد")
        AuthTextField(label = "كود الدعوة", value = inviteCode, onValueChange = { inviteCode = it }, placeholder = "مثال: ZAD-1234")

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onJoinFamily(inviteCode, alias) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = inviteCode.isNotBlank() && alias.isNotBlank()
        ) {
            Icon(Icons.Default.ArrowForward, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "الانضمام للعائلة", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
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
    onUpdateRequestStatus: (String, String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var showInviteDialog by remember { mutableStateOf(false) }

    val isParent = state.myMemberInfo.role == "admin"
    val tabs = listOf(
        TabData(Icons.Default.Chat, "الشات"),
        TabData(Icons.Default.Assignment, "المهام"),
        TabData(Icons.Default.People, "الأعضاء"),
        TabData(Icons.Default.ShoppingCart, "البقالة")
    ) + if (isParent) listOf(TabData(Icons.Default.AccountBalanceWallet, "الأبناء")) else emptyList()

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
                            "عائلة ${state.familyGroup.inviteCode}",
                            style = Typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${state.members.size} أفراد",
                            style = Typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Row {
                        IconButton(onClick = { showInviteDialog = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = Color.White)
                        }
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "انضم إلى عائلتي في تطبيق زاد!\nكود الدعوة: ${state.familyGroup.inviteCode}\nأو اضغط: zad://invite?code=${state.familyGroup.inviteCode}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الدعوة"))
                        }) {
                            Icon(Icons.Default.Share, null, tint = Color.White)
                        }
                    }
                }
            }
        }

        // Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = surface,
            contentColor = primary,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selectedTab == index) primary else onSurfaceVariant)
                            Text(
                                tab.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ChatTab(state.messages, state.members, state.myMemberInfo, onSendMessage, onUpdateRequestStatus, viewModel = viewModel)
                1 -> TasksTab(state.chores, state.members, state.myMemberInfo.role == "admin", onToggleChore, onAddChore)
                2 -> MembersTab(state = state, viewModel = viewModel)
                3 -> GroceriesTab(state.groceries, onToggleGrocery)
                4 -> KidsSpendingTab(state = state, onUpdateRequestStatus = onUpdateRequestStatus)
            }
        }
    }

    if (showInviteDialog) {
        InviteMemberDialog(
            inviteCode = state.familyGroup.inviteCode,
            onDismiss = { showInviteDialog = false }
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
                Text("البقالة المشتركة", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "قائمة المشتريات التي تمت إضافتها من قبل أفراد العائلة أو اقترحها زاد.",
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        if (groceries.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد طلبات بقالة حالياً.", color = Color.Gray)
                }
            }
        }

        items(groceries) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceContainer)
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
                            color = if (item.isPurchased) Color.Gray else onSurface
                        )
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
    onUpdateRequestStatus: (String, String, String) -> Unit
) {
    val children = state.members.filter { it.role == "child" }
    val purchaseRequests = state.messages.filter { it.messageType == "PURCHASE_REQUEST" }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Text("مصاريف الأبناء", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
        }
        Spacer(Modifier.height(4.dp))
        Text("نظرة عامة على أرصدة وطلبات الأبناء", style = Typography.bodyMedium, color = onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (children.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("لا يوجد أبناء مسجلين بعد", color = Color.Gray)
            }
            return
        }

        children.forEach { child ->
            val childChores = state.chores.filter { it.assignedTo == child.id }
            val completed = childChores.count { it.isCompleted }
            val childRequests = purchaseRequests.filter { it.senderId == child.id }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.screens.KidAvatar(seed = child.id.ifBlank { child.alias }, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(child.alias, fontWeight = FontWeight.Bold, color = onSurface)
                            Text("الرصيد: ${child.balance} ريال" + if (child.savingsGoal > 0) " | الهدف: ${child.savingsGoal} ريال" else "", fontSize = 12.sp, color = onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = primary.copy(alpha = 0.1f)) {
                            Text("$completed/${childChores.size} مهام", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = primary)
                        }
                    }
                    if (childRequests.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = surface, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("الطلبات المعلقة (${childRequests.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = onSurfaceVariant)
                                childRequests.filter { it.metadata?.contains("\"status\":\"PENDING\"") == true }.take(3).forEach { req ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF9800))
                                        Spacer(Modifier.width(6.dp))
                                        Text(req.message, fontSize = 12.sp, color = onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { onUpdateRequestStatus(req.id, "APPROVED", "تمت الموافقة ✅") }, contentPadding = PaddingValues(4.dp)) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = primary)
                                                Spacer(Modifier.width(2.dp))
                                                Text("موافقة", fontSize = 10.sp)
                                            }
                                            TextButton(onClick = { onUpdateRequestStatus(req.id, "REJECTED", "تم الرفض ❌") }, contentPadding = PaddingValues(4.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(2.dp))
                                                Text("رفض", fontSize = 10.sp)
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
private fun MembersTab(state: FamilyState.Active, viewModel: FamilyViewModel) {
    var selectedMember by remember { mutableStateOf<com.example.data.FamilyMember?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "أفراد العائلة",
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "اضغط على أي عضو لتفاصيل إنجازاته",
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        items(state.members) { member ->
            MemberDetailCard(
                member = member,
                viewModel = viewModel,
                state = state,
                onClick = { selectedMember = member }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    if (selectedMember != null) {
        MemberDetailSheet(
            member = selectedMember!!,
            viewModel = viewModel,
            state = state,
            onDismiss = { selectedMember = null }
        )
    }
}

@Composable
private fun MemberDetailCard(
    member: com.example.data.FamilyMember,
    viewModel: FamilyViewModel,
    state: FamilyState.Active,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val memberTrees = viewModel.getMemberTrees(member.id)
    val memberChores = state.chores.filter { it.assignedTo == member.id }
    val completedChores = memberChores.count { it.isCompleted }
    val memberTransactions = mutableStateOf<List<ZadTransaction>>(emptyList())

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer)
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
                    Text(member.alias.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(member.alias, fontWeight = FontWeight.Bold, color = onSurface, fontSize = 18.sp)
                        if (member.role == "admin") {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = primary.copy(alpha = 0.15f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = primary)
                                    Text("مدير", fontSize = 11.sp, color = primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatPill(Icons.Default.MonetizationOn, com.example.data.CurrencyFormatter.format(context, member.balance))
                        StatPill(Icons.Default.CheckCircle, "$completedChores مهام")
                        StatPill(Icons.Default.Park, "${memberTrees.sumOf { it.score }} تسبيحة")
                    }
                }
                Icon(Icons.Default.ChevronRight, null, tint = onSurfaceVariant)
            }

            // Progress bar for savings goal
            if (member.savingsGoal > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val goalProgress = (member.balance / member.savingsGoal).toFloat().coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("هدف التوفير", style = Typography.labelSmall, color = onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { goalProgress },
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
            Text(text, fontSize = 12.sp, color = onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDetailSheet(
    member: com.example.data.FamilyMember,
    viewModel: FamilyViewModel,
    state: FamilyState.Active,
    onDismiss: () -> Unit
) {
    val memberTrees = viewModel.getMemberTrees(member.id)
    val memberChores = state.chores.filter { it.assignedTo == member.id }
    val completedChores = memberChores.filter { it.isCompleted }
    val pendingChores = memberChores.filter { !it.isCompleted }

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
                        if (member.role == "admin") "مدير العائلة" else "عضو",
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
                StatItem("الرصيد", "${member.balance} ريال")
                StatItem("الهدف", if (member.savingsGoal > 0) "${member.savingsGoal} ريال" else "---")
                StatItem("الأشجار", "${memberTrees.size}")
            }

            Spacer(Modifier.height(24.dp))

            // Completed Tasks
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = primary)
                Text("المهام المنجزة (${completedChores.size})", fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(8.dp))
            if (completedChores.isEmpty()) {
                Text("لا توجد مهام منجزة بعد", color = Color.Gray, fontSize = 14.sp)
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
                                if (chore.rewardAmount > 0) {
                                    Text("مكافأة: ${chore.rewardAmount} ريال", color = primary, fontSize = 12.sp)
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
                Text("المهام القادمة (${pendingChores.size})", fontWeight = FontWeight.Bold, color = onSurface)
            }
            Spacer(Modifier.height(8.dp))
            if (pendingChores.isEmpty()) {
                Text("لا توجد مهام قادمة", color = Color.Gray, fontSize = 14.sp)
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
                                if (chore.rewardAmount > 0) {
                                    Text("مكافأة: ${chore.rewardAmount} ريال", color = secondary, fontSize = 12.sp)
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
                    Icon(Icons.Default.Park, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF2E7D32))
                    Text("أشجار التسبيحة (${memberTrees.size})", fontWeight = FontWeight.Bold, color = onSurface)
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

            Spacer(Modifier.height(32.dp))
        }
    }
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
    onAddChore: (String, String, String?, Double) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("الكل") }
    val filters = listOf("الكل", "قادمة", "منجزة")

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
                    Text("المهام العائلية", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                }
                if (isAdmin) {
                    FilledTonalButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${chores.count { it.isCompleted }}/${chores.size} منجزة",
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }

        // Filter chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Per-member task sections
        choresByMember.forEach { (member, memberChores) ->
            val filteredChores = when (selectedFilter) {
                "قادمة" -> memberChores.filter { !it.isCompleted }
                "منجزة" -> memberChores.filter { it.isCompleted }
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

                items(filteredChores) { chore ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onToggle(chore.id, !chore.isCompleted) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (chore.isCompleted) primary.copy(alpha = 0.08f) else surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (chore.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (chore.isCompleted) primary else Color.LightGray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    chore.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (chore.isCompleted) Color.Gray else onSurface,
                                    textDecoration = if (chore.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                )
                                if (chore.rewardAmount > 0) {
                                    Text(
                                        "${chore.rewardAmount} ريال",
                                        color = if (chore.isCompleted) primary else secondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            if (chore.dueDate != null) {
                                Text(
                                    chore.dueDate.take(10),
                                    fontSize = 11.sp,
                                    color = onSurfaceVariant
                                )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مهمة جديدة") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("اسم المهمة") },
                    placeholder = { Text("مثال: ترتيب الغرفة") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = rewardAmount, onValueChange = { rewardAmount = it },
                    label = { Text("المكافأة (ريال)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("المكلف بالمهمة:", style = Typography.labelMedium, color = onSurface)
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
                    label = { Text("تاريخ التسليم (اختياري)") },
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
            ) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// ── INVITE DIALOG ──
@Composable
private fun InviteMemberDialog(
    inviteCode: String,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val shareText = "انضم إلى عائلتي في تطبيق زاد!\nكود الدعوة: $inviteCode\nأو افتح الرابط: zad://invite?code=$inviteCode"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = primary)
                Spacer(Modifier.width(8.dp))
                Text("دعوة أفراد جدد")
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
                        label = { Text("رابط") }
                    )
                    FilterChip(
                        selected = selectedMethod == 2,
                        onClick = { selectedMethod = 2 },
                        label = { Text("كود") }
                    )
                    FilterChip(
                        selected = selectedMethod == 3,
                        onClick = { selectedMethod = 3 },
                        label = { Text("واتساب") }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (selectedMethod) {
                    0 -> {
                        Text("مسح الرمز للانضمام:", style = Typography.labelMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            QrCode(content = "zad://invite?code=$inviteCode")
                        }
                    }
                    1 -> {
                        Text("رابط الدعوة:", style = Typography.labelMedium, color = onSurfaceVariant)
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
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, tint = primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    2 -> {
                        Text("كود الدعوة:", style = Typography.labelMedium, color = onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = primary.copy(alpha = 0.1f)) {
                            Text(inviteCode, modifier = Modifier.fillMaxWidth().padding(16.dp), fontWeight = FontWeight.Bold, color = primary, fontSize = 24.sp, textAlign = TextAlign.Center)
                        }
                    }
                    3 -> {
                        Text("شارك عبر واتساب:", style = Typography.labelMedium, color = onSurfaceVariant)
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
                                        context.startActivity(Intent.createChooser(fallback, "مشاركة الدعوة"))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("واتساب", color = Color.White)
                                }
                            }
                            Button(
                                onClick = {
                                    val general = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(general, "مشاركة الدعوة"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = onSurface, modifier = Modifier.size(16.dp))
                                    Text("جميع التطبيقات")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        val shareText = when (selectedMethod) {
                            0 -> "انضم إلى عائلتي في تطبيق زاد! امسح الرمز: zad://invite?code=$inviteCode"
                            1 -> "انضم إلى عائلتي في تطبيق زاد!\nرابط الدعوة: zad://invite?code=$inviteCode"
                            else -> "انضم إلى عائلتي في تطبيق زاد!\nكود الدعوة: $inviteCode"
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الدعوة"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مشاركة")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تم") }
        }
    )
}

// ── EXISTING TABS (Groceries, Chat, Etc.) ──



@Composable
fun BudgetGoalsTab(goals: List<FamilyGoal>, members: List<com.example.data.FamilyMember>, chores: List<Chore>) {
    val kids = members.filter { it.role != "admin" }
    val totalBalance = kids.sumOf { it.balance }
    val totalPaidRewards = chores.filter { it.isCompleted }.sumOf { it.rewardAmount }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(20.dp), tint = primary)
                Text("الميزانية والمكافآت", style = Typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (kids.isEmpty()) {
            item { Text("لا يوجد أبناء مضافين لعرض إحصائياتهم.", color = Color.Gray) }
            return@LazyColumn
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إحصائيات أرصدة الأبناء", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))

                    kids.forEach { kid ->
                        val maxBalance = maxOf(kids.maxOfOrNull { it.balance } ?: 1.0, 1.0)
                        val fraction = (kid.balance / maxBalance).toFloat()
                        var animatedFraction by remember { mutableStateOf(0f) }

                        LaunchedEffect(fraction) {
                            animate(
                                initialValue = 0f,
                                targetValue = fraction,
                                animationSpec = tween(1000, easing = FastOutSlowInEasing)
                            ) { value, _ -> animatedFraction = value }
                        }

                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(kid.alias, style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("${kid.balance} ريال", style = Typography.bodyMedium, fontWeight = FontWeight.Bold, color = primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.LightGray.copy(alpha = 0.3f))
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("إجمالي المكافآت المدفوعة", style = Typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalPaidRewards ريال", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary)
                    }
                }
            }
        }
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
    viewModel: FamilyViewModel? = null
) {
    var text by remember { mutableStateOf("") }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf<String?>(null) }
    var showQuickReplies by remember { mutableStateOf(false) }

    // Start typing monitor
    LaunchedEffect(myMemberInfo.familyId) {
        viewModel?.startTypingMonitor(myMemberInfo.familyId)
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
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(member.alias, fontSize = 12.sp, color = onSurfaceVariant)
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Group messages by date for date separators
            val groupedMessages = messages.reversed()
            var lastDate = ""

            items(groupedMessages) { msg ->
                val msgDate = msg.createdAt?.take(10) ?: ""
                val today = java.time.LocalDate.now().toString()
                val yesterday = java.time.LocalDate.now().minusDays(1).toString()
                val dateLabel = when (msgDate) {
                    today -> "اليوم"
                    yesterday -> "أمس"
                    else -> msgDate.replace("-", "/")
                }

                if (msgDate != lastDate && msgDate.isNotBlank()) {
                    lastDate = msgDate
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(12.dp), color = onSurface.copy(alpha = 0.08f)) {
                            Text(dateLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 11.sp, color = onSurfaceVariant)
                        }
                    }
                }

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
                                "$typingNames يكتب${
                                    if (typingMembers.size > 1) "ون" else ""
                                }...",
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

        // Input
        Box(
            modifier = Modifier.fillMaxWidth().background(surface).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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
                    IconButton(onClick = { showQuickReplies = !showQuickReplies }) {
                        Icon(Icons.Default.Add, contentDescription = "Quick Replies", tint = primary)
                    }
                    IconButton(onClick = { onSendMessage("الرجاء الانتباه، حالة طوارئ!", "SOS", null) }) {
                        Icon(Icons.Default.Warning, contentDescription = "SOS", tint = dangerColor)
                    }
                    IconButton(onClick = { showPurchaseDialog = true }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Request", tint = primary)
                    }
                    IconButton(onClick = { showPollDialog = true }) {
                        Icon(Icons.Default.BarChart, contentDescription = "Poll", tint = secondary)
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
                            placeholder = { Text("اكتب رسالة أو @Zad...") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        IconButton(onClick = {
                            if (text.isNotBlank()) { onSendMessage(text, "TEXT", null); text = "" }
                        }) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
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
            title = { Text("طلب مصروف أو مشتريات") },
            text = {
                Column {
                    OutlinedTextField(value = purchaseTitle, onValueChange = { purchaseTitle = it }, label = { Text("ماذا تريد أن تشتري؟") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = purchaseAmount, onValueChange = { purchaseAmount = it }, label = { Text("المبلغ المطلوب (ريال)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (purchaseTitle.isNotBlank() && purchaseAmount.isNotBlank()) {
                        val meta = """{"amount":${purchaseAmount.toDoubleOrNull() ?: 0.0}, "status":"PENDING"}"""
                        onSendMessage("أحتاج $purchaseAmount ريال لشراء $purchaseTitle", "PURCHASE_REQUEST", meta)
                        showPurchaseDialog = false
                    }
                }) { Text("إرسال الطلب") }
            },
            dismissButton = { TextButton(onClick = { showPurchaseDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showPollDialog) {
        var pollQuestion by remember { mutableStateOf("") }
        var pollOptions by remember { mutableStateOf(listOf("", "")) }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("تصويت عائلي") },
            text = {
                Column {
                    OutlinedTextField(value = pollQuestion, onValueChange = { pollQuestion = it }, label = { Text("السؤال") }, modifier = Modifier.fillMaxWidth())
                    pollOptions.forEachIndexed { i, opt ->
                        OutlinedTextField(value = opt, onValueChange = { n -> pollOptions = pollOptions.toMutableList().apply { set(i, n) } },
                            label = { Text("خيار ${i + 1}") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    TextButton(onClick = { pollOptions = pollOptions + "" }) { Text("+ إضافة خيار") }
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
                }) { Text("إرسال التصويت") }
            },
            dismissButton = { TextButton(onClick = { showPollDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SosBubble(msg: ChatMessage, senderAlias: String) {
    Box(modifier = Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(16.dp)).background(dangerColor).padding(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("نداء طوارئ من $senderAlias!", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(msg.message ?: "", color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PurchaseBubble(msg: ChatMessage, senderAlias: String, myMemberInfo: com.example.data.FamilyMember, isMe: Boolean, onUpdateRequestStatus: (String, String, String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(16.dp)).background(surfaceContainerHigh).border(1.dp, primary, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Request", tint = primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("طلب شراء من $senderAlias", fontWeight = FontWeight.Bold, color = primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(msg.message ?: "", color = onSurface)
            val isPending = msg.metadata?.contains("\"status\":\"PENDING\"") == true
            val isApproved = msg.metadata?.contains("\"status\":\"APPROVED\"") == true
            val isRejected = msg.metadata?.contains("\"status\":\"REJECTED\"") == true
            if (isPending && myMemberInfo.role == "admin" && !isMe) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { onUpdateRequestStatus(msg.id, "APPROVED", "تمت الموافقة على طلب: ${msg.message}") }, colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text("موافقة وخصم") }
                    OutlinedButton(onClick = { onUpdateRequestStatus(msg.id, "REJECTED", "تم رفض طلب: ${msg.message}") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)) { Text("رفض") }
                }
            } else if (isApproved) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                    Text("تمت الموافقة", style = Typography.labelMedium, color = primary, fontWeight = FontWeight.Bold)
                }
            } else if (isRejected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp), tint = dangerColor)
                    Text("مرفوض", style = Typography.labelMedium, color = dangerColor, fontWeight = FontWeight.Bold)
                }
            }
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
                Text("تصويت من $senderAlias", fontWeight = FontWeight.Bold, color = secondary)
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
        }
    }
}

@Composable
private fun TextBubble(msg: ChatMessage, isMe: Boolean, isAi: Boolean, senderAlias: String, onPin: () -> Unit, onReact: (String) -> Unit, showEmojiPicker: Boolean, onToggleEmojiPicker: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isMe) 16.dp else 4.dp, bottomEnd = if (isMe) 4.dp else 16.dp))
                .background(if (isMe) primary else surfaceContainerHigh)
                .padding(12.dp)
        ) {
            Column {
                if (!isMe) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(senderAlias, fontSize = 10.sp, color = if (isAi) primary else Color.Gray, fontWeight = FontWeight.Bold)
                        if (msg.isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = if (isAi) primary else Color.Gray, modifier = Modifier.size(12.dp))
                        }
                        if (msg.voiceUrl != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Mic, contentDescription = "Voice", tint = if (isAi) primary else Color.Gray, modifier = Modifier.size(12.dp))
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
                        Text("رسالة صوتية", color = if (isMe) Color.White else onSurface, fontSize = 13.sp)
                    }
                } else {
                    Text(msg.message ?: "", color = if (isMe) Color.White else onSurface)
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
                        shape = CircleShape, color = surfaceContainer, shadowElevation = 1.dp
                    ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 14.sp) } }
                }
            }
        }
    }
}

@Composable
fun FamilyTopBar(onOpenDrawer: () -> Unit, unreadCount: Int = 0, onNotificationsClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onOpenDrawer() }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = onSurfaceVariant)
            }
        }
        Text("العائلة الذكية", style = Typography.titleLarge, color = onSurface, fontWeight = FontWeight.Bold)
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(containerColor = Color.Red) {
                        Text("$unreadCount", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        ) {
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = onSurfaceVariant)
            }
        }
    }
}
