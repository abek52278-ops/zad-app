package com.example.ui.v2

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppNotification
import com.example.ui.theme.ZadMeterBar
import com.example.ui.theme.ZadStatusPill
import com.example.ui.theme.ZadV3
import com.example.ui.theme.zadCardShadow
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.FamilyViewModel
import java.time.LocalDate

// ═══════════════════════════════════════════════════════════════════════════════
// V3 Family / Deals / Notifications / Tasbiha — per prototype renderFamily,
// renderDeals, renderNotifications, tasbiha garden card.
// ═══════════════════════════════════════════════════════════════════════════════

// ── Family ───────────────────────────────────────────────────────────────────

@Composable
fun V3FamilyScreen(
    modifier: Modifier = Modifier,
    familyViewModel: FamilyViewModel? = null,
) {
    var tab by remember { mutableStateOf("members") }
    val state: FamilyState? = if (familyViewModel != null) {
        val s by familyViewModel.state.collectAsState(); s
    } else null

    Column(modifier = modifier.fillMaxSize()) {
        V3SegmentTabsRow(
            tabs = listOf(
                "chat" to stringResource(R.string.v3x_tab_chat),
                "tasks" to stringResource(R.string.v3x_tab_tasks),
                "members" to stringResource(R.string.v3x_tab_members),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        val active = state as? FamilyState.Active
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        when (tab) {
            "chat" -> {
                item {
                    val active = state as? FamilyState.Active
                    val msgs = active?.messages.orEmpty()
                    val myMemberId = active?.myMemberInfo?.id
                    var messageText by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (msgs.isEmpty()) {
                            V3EmptyStateHint(emoji = "💬")
                        } else {
                            msgs.takeLast(25).forEach { m ->
                                val isMe = m.senderId == myMemberId
                                val senderName = active?.members?.firstOrNull { it.id == m.senderId }?.alias ?: ""
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .zadCardShadow(
                                                shape = RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = if (isMe) 18.dp else 4.dp,
                                                    bottomEnd = if (isMe) 4.dp else 18.dp
                                                ),
                                                elevation = if (isMe) 8.dp else 3.dp
                                            )
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = if (isMe) 18.dp else 4.dp,
                                                    bottomEnd = if (isMe) 4.dp else 18.dp
                                                )
                                            )
                                            .background(if (isMe) ZadV3.green800 else Color.White)
                                            .padding(horizontal = 14.dp, vertical = 11.dp)
                                    ) {
                                        if (!isMe && senderName.isNotBlank()) {
                                            Text(
                                                senderName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ZadV3.green800,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                        Text(
                                            m.message,
                                            fontSize = 13.5.sp,
                                            lineHeight = 19.sp,
                                            color = if (isMe) Color.White else ZadV3.ink
                                        )
                                    }
                                }
                            }
                        }

                        // Chat input box (.chat-input pill with send button)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .zadCardShadow(ZadV3.rPill)
                                .clip(ZadV3.rPill)
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    color = ZadV3.ink,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default
                                ),
                                decorationBox = { innerTextField ->
                                    if (messageText.isEmpty()) {
                                        Text(
                                            stringResource(R.string.v2_send),
                                            fontSize = 13.5.sp,
                                            color = ZadV3.gray400
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ZadV3.green800)
                                    .pressableScale(
                                        onClick = {
                                            if (messageText.isNotBlank() && familyViewModel != null) {
                                                val txt = messageText
                                                messageText = ""
                                                familyViewModel.sendMessage(txt, "TEXT")
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.send_action),
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
                "tasks" -> {
                    val chores = active?.chores.orEmpty()
                    if (chores.isEmpty()) item { V3EmptyStateHint(emoji = "✅") }
                    items(chores.size) { i ->
                        val chore = chores[i]
                        val assignee = active?.members?.firstOrNull { it.userId == chore.assignedTo || it.id == chore.assignedTo }?.alias ?: ""
                        V3TaskRow(chore.title, assignee, chore.isCompleted, index = i)
                    }
                }
                else -> {
                    val members = active?.members.orEmpty()
                    if (members.isEmpty()) item { V3EmptyStateHint(emoji = "👨‍👩‍👧") }
                    items(members.size) { i ->
                        val member = members[i]
                        val stat = if (member.savingsGoal > 0)
                            stringResource(R.string.v3x_goal_pct, ((member.balance / member.savingsGoal) * 100).toInt().coerceIn(0, 100))
                        else ""
                        V3MemberCard(member.alias, member.role, stat, index = i)
                    }
                }
            }
        }
    }
}

@Composable
internal fun V3SegmentTabsRow(tabs: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(ZadV3.rPill).background(Color.White).zadCardShadow(ZadV3.rPill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { idx, (key, label) ->
            val on = selected == key
            Box(
                modifier = Modifier.weight(1f).clip(ZadV3.rPill)
                    .fadeUpOnAppear(idx * 40L)
                    .background(if (on) ZadV3.green800 else Color.Transparent)
                    .pressableScale { onSelect(key) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else ZadV3.gray500)
            }
        }
    }
}

@Composable
private fun V3MemberCard(name: String, role: String, stat: String, index: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
            .fadeUpOnAppear(index * 70L).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name.ifBlank { "—" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
            Text(role, fontSize = 11.5.sp, color = ZadV3.gray400, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (stat.isNotBlank()) {
            ZadStatusPill(text = stat, color = ZadV3.green800, containerColor = Color(0x14064E3B))
        }
    }
}

@Composable
private fun V3TaskRow(title: String, who: String, done: Boolean, index: Int = 0) {
    Row(
        modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
            .fadeUpOnAppear(index * 70L).padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
            if (who.isNotBlank()) Text(who, fontSize = 11.5.sp, color = ZadV3.gray400)
        }
        if (done) ZadStatusPill(stringResource(R.string.v3x_task_done), ZadV3.green800, containerColor = Color(0x14064E3B))
        else ZadStatusPill(stringResource(R.string.v3x_task_pending), ZadV3.warn, containerColor = Color(0x1AB45309))
    }
}

// ── Deals ────────────────────────────────────────────────────────────────────

@Composable
fun V3DealsScreen(viewModel: com.example.ui.viewmodels.ZadViewModel? = null, modifier: Modifier = Modifier) {
    val outing = if (viewModel != null) {
        val o by viewModel.outingSuggestion.collectAsState(); o
    } else null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Disclaimer banner
        item {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0x0F064E3B)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.v3x_deals_disclaimer), fontSize = 12.sp, color = ZadV3.green800, lineHeight = 18.sp)
            }
        }
        val store = outing
        if (store != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(store.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
                        Text(stringResource(R.string.v3x_nearby_store), fontSize = 11.5.sp, color = ZadV3.gray400)
                    }
                    Text("${store.distanceMeters}m", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.green800)
                }
            }
        } else {
            item { V3EmptyStateHint(emoji = "🛍️") }
        }
    }
}

// ── Notifications ────────────────────────────────────────────────────────────

@Composable
fun V3NotificationsScreen(viewModel: com.example.ui.viewmodels.ZadViewModel? = null, modifier: Modifier = Modifier) {
    val notifications: List<com.example.data.AppNotification> = if (viewModel != null) {
        val n by viewModel.appNotifications.collectAsState(); n
    } else emptyList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (notifications.isEmpty()) {
            item { V3EmptyStateHint(emoji = "🔔") }
        } else {
            items(notifications.size) { i ->
                val n = notifications[i]
                Row(
                    modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCard).clip(ZadV3.rCard).background(Color.White)
                        .fadeUpOnAppear(i * 60L).padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(if (n.isRead) ZadV3.green800 else ZadV3.danger))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ZadV3.ink)
                        if (n.message.isNotBlank()) Text(n.message, fontSize = 12.5.sp, color = ZadV3.gray500)
                    }
                }
            }
        }
    }
}

// ── Tasbiha Garden ───────────────────────────────────────────────────────────

private const val TASBIHA_GOAL = 100
private const val TASBIHA_PREFS = "zad_prefs"
private const val TASBIHA_KEY = "zad-tasbiha-garden-screen"

private fun loadTasbihaCount(context: Context): Int {
    val prefs = context.getSharedPreferences(TASBIHA_PREFS, Context.MODE_PRIVATE)
    val savedDate = prefs.getString("$TASBIHA_KEY-date", null)
    val today = LocalDate.now().toString()
    return if (savedDate == today) prefs.getInt("$TASBIHA_KEY-count", 0) else 0
}

private fun saveTasbihaCount(context: Context, count: Int) {
    context.getSharedPreferences(TASBIHA_PREFS, Context.MODE_PRIVATE).edit()
        .putString("$TASBIHA_KEY-date", LocalDate.now().toString())
        .putInt("$TASBIHA_KEY-count", count)
        .apply()
}

@Composable
fun V3TasbihaScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(loadTasbihaCount(context)) }
    var showConfetti by remember { mutableStateOf(false) }

    val pct = ((count.toFloat() / TASBIHA_GOAL) * 100f).coerceIn(0f, 100f)
    val emoji = when {
        count >= TASBIHA_GOAL -> "🌳"
        count >= 75 -> "🌿🌿"
        count >= 50 -> "🦋"
        count >= 25 -> "🌿"
        else -> "🌱"
    }

    fun onTap() {
        if (count >= TASBIHA_GOAL) return
        val next = (count + 1).coerceAtMost(TASBIHA_GOAL)
        saveTasbihaCount(context, next)
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(12)
        }
        count = next
        if (next == TASBIHA_GOAL) showConfetti = true
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        ConfettiOverlay(modifier = Modifier.fillMaxSize(), isTriggered = showConfetti)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().zadCardShadow(ZadV3.rCardLg).clip(ZadV3.rCardLg)
                    .background(Color.White).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.v3x_tasbiha_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ZadV3.slate)
                    Text("${pct.toInt()}%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ZadV3.green600)
                }

                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(ZadV3.tileTasbihaBg)
                        .pressableScale(onClick = ::onTap),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 52.sp, lineHeight = 52.sp, modifier = Modifier.floatingIdle(2000))
                }

                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                        .background(Color(0x140F172A)),
                ) {
                    Box(
                        Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF0F9B76)))),
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("$count / $TASBIHA_GOAL", fontSize = 12.sp, color = ZadV3.gray500)
                    Box(
                        modifier = Modifier.clip(ZadV3.rPill).background(ZadV3.violet)
                            .pressableScale(onClick = ::onTap).padding(horizontal = 20.dp, vertical = 9.dp),
                    ) {
                        Text(stringResource(R.string.v3x_tasbiha_tap), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun V3EmptyStateHint(emoji: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, fontSize = 44.sp)
            Text(stringResource(R.string.v2_empty_section), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ZadV3.ink, textAlign = TextAlign.Center)
        }
    }
}
