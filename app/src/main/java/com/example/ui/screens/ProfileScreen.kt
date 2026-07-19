package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import androidx.compose.runtime.*
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import com.example.ui.viewmodels.FamilyViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.ZadViewModel
import android.util.Log
import androidx.navigation.NavController
import com.example.Screen

private const val TAG_PROF = "ProfileScreen"

@Composable
fun ProfileScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    onLogout: () -> Unit = {},
    navController: NavController? = null
) {
    var userEmail by remember { mutableStateOf("تحميل...") }
    var userId by remember { mutableStateOf("...") }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val inventory by viewModel.inventory.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val familyState by familyViewModel.state.collectAsState()
    val familyMembersCount = if (familyState is FamilyState.Active) {
        (familyState as FamilyState.Active).members.size
    } else 0

    val userNameState by viewModel.userName.collectAsState()
    val displayUserName = userNameState ?: "مستخدم جديد"
    val globalAvatarUri by viewModel.avatarUri.collectAsState()
    val myTasbiha = familyViewModel.myTasbiha

    var animTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        userEmail = session?.user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "مستخدم جديد"
        userId = session?.user?.id?.take(8)?.uppercase() ?: "9921"
        viewModel.loadUserProfile()
        animTriggered = true
        Log.d(TAG_PROF, "ProfileScreen loaded — userName=$displayUserName, userId=$userId")
    }

    val infiniteTransition = rememberInfiniteTransition(label = "profile_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    val headerAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "header_shift"
    )

    var showAvatarDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showHelpSupport by remember { mutableStateOf(false) }
    var showBehaviorConsentDialog by remember { mutableStateOf(false) }

    if (showAvatarDialog) {
        AvatarSelectionDialog(
            onDismiss = { showAvatarDialog = false },
            onAvatarSelected = { avatarUri ->
                viewModel.updateUserProfile(displayUserName, avatarUri)
                showAvatarDialog = false
            }
        )
    }

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = displayUserName,
            onDismiss = { showEditNameDialog = false },
            onSave = { newName ->
                viewModel.updateUserProfile(newName, globalAvatarUri)
                showEditNameDialog = false
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("حذف الحساب", fontWeight = FontWeight.Bold, color = dangerColor) },
            text = { Text("هل أنت متأكد من حذف حسابك نهائياً؟ لا يمكن التراجع عن هذا الإجراء.", color = onSurface) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAccount()
                    showDeleteAccountDialog = false
                    onLogout()
                }, colors = ButtonDefaults.buttonColors(containerColor = dangerColor)) { Text("نعم، احذف الحساب") }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text("إلغاء", color = primary) } },
            containerColor = surface
        )
    }

    if (showHelpSupport) {
        HelpSupportScreen(onBack = { showHelpSupport = false })
    }

    if (showBehaviorConsentDialog) {
        AlertDialog(
            onDismissRequest = { showBehaviorConsentDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TrackChanges, contentDescription = null, modifier = Modifier.size(24.dp), tint = primary)
                    Text("التحليل الذكي للسلوك", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "زاد يحلل بيانات صرفك واستهلاكك (المعاملات، المخزون، الاشتراكات) لتقديم:\n\n" +
                        "• تنبؤات مخصصة للمصاريف\n" +
                        "• ترشيحات ذكية للمنتجات\n" +
                        "• تحليل أسبوعي للسلوك المالي\n\n" +
                        "هذا مطلوب بموجب نظام حماية البيانات الشخصية السعودي (PDPL).\n" +
                        "يمكنك إلغاء التفعيل في أي وقت."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setBehaviorConsent(true)
                        showBehaviorConsentDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (viewModel.behaviorConsentGiven.collectAsState().value) "إلغاء التفعيل" else "تفعيل") }
            },
            dismissButton = {
                if (viewModel.behaviorConsentGiven.collectAsState().value) {
                    TextButton(onClick = {
                        viewModel.setBehaviorConsent(false)
                        showBehaviorConsentDialog = false
                    }) { Text("إلغاء التفعيل", color = dangerColor) }
                } else {
                    TextButton(onClick = { showBehaviorConsentDialog = false }) { Text("لاحقاً") }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // -- Premium Animated Header --
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0D5C3F),
                                Color(0xFF1A7A55),
                                Color(0xFF0D5C3F),
                                Color(0xFF094730)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f * headerAnim, 1000f * headerAnim)
                        )
                    )
            ) {
                // Decorative floating circles
                Box(
                    Modifier
                        .size(200.dp).offset(x = (-60).dp, y = (-80).dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.04f))
                )
                Box(
                    Modifier
                        .size(140.dp).offset(x = 250.dp, y = (-40).dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.06f))
                )
                Box(
                    Modifier
                        .size(100.dp).offset(x = (-20).dp, y = 200.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.03f))
                )
                Box(
                    Modifier
                        .size(180.dp).offset(x = 200.dp, y = 150.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(top = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar with animated glow ring
                    Box(modifier = Modifier.size(100.dp)) {
                        // Glow ring
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(1.15f)
                                .clip(CircleShape)
                                .background(primaryFixed.copy(alpha = glowAlpha * 0.3f))
                        )
                        // Outer ring
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        ) {
                            if (!globalAvatarUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = globalAvatarUri,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    placeholder = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.avatar),
                                    error = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.avatar),
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).clickable { showAvatarDialog = true }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable { showAvatarDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                                }
                            }
                        }
                        // Edit badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp).clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, Color(0xFF0D5C3F), CircleShape)
                                .clickable { showAvatarDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF0D5C3F), modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(displayUserName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = primaryFixed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("#ZAD-$userId", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(displayUserName.ifEmpty { "مستخدم جديد" }.take(1).uppercase(), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            // -- Quick Stats with Animated Counters --
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedStatCard(
                    icon = Icons.Default.Inventory2,
                    label = "في المخزون",
                    value = inventory.size,
                    color = primaryFixed,
                    animTriggered = animTriggered,
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCard(
                    icon = Icons.Default.Subscriptions,
                    label = "اشتراكات",
                    value = subscriptions.size,
                    color = secondary,
                    animTriggered = animTriggered,
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCard(
                    icon = Icons.Default.FamilyRestroom,
                    label = "أفراد العائلة",
                    value = familyMembersCount,
                    color = tertiary,
                    animTriggered = animTriggered,
                    modifier = Modifier.weight(1f)
                )
            }

            // -- Achievements Section --
            Spacer(Modifier.height(28.dp))
            AchievementsSection(
                inventoryCount = inventory.size,
                subscriptionsCount = subscriptions.size,
                familyMembersCount = familyMembersCount,
                tasbihaCount = if (myTasbiha != null) 1 else 0,
                transactionsCount = viewModel.transactions.collectAsState().value.size
            )

            Spacer(Modifier.height(24.dp))
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // -- Menu Items with Premium Styling --
                SectionTitle("الإعدادات")
                Spacer(Modifier.height(12.dp))

                ProfileMenuItem(
                    icon = Icons.Default.Edit,
                    title = "تعديل الملف الشخصي",
                    subtitle = "الاسم، الصورة، والبريد",
                    gradient = listOf(Color(0xFF0D5C3F), Color(0xFF1A7A55)),
                    onClick = { navController?.navigate(Screen.EditProfile.route) }
                )
                Spacer(Modifier.height(10.dp))

                ProfileMenuItem(
                    icon = Icons.Default.FamilyRestroom,
                    title = "إدارة العائلة",
                    subtitle = "الأعضاء والصلاحيات",
                    gradient = listOf(Color(0xFFC8963E), Color(0xFFE8BC6A)),
                    onClick = { navController?.navigate(Screen.FamilyManagement.route) }
                )
                Spacer(Modifier.height(10.dp))

                ProfileMenuItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "الميزانية وطرق الدفع",
                    subtitle = "الميزانية الشهرية والربط البنكي",
                    gradient = listOf(Color(0xFF1C6EA4), Color(0xFF60A5FA)),
                    onClick = { navController?.navigate(Screen.PaymentBudget.route) }
                )
                Spacer(Modifier.height(10.dp))

                ProfileMenuItem(
                    icon = Icons.Default.SmartToy,
                    title = "تنبيهات المساعد الذكي",
                    subtitle = "التحكم في التنبيهات الذكية",
                    gradient = listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)),
                    onClick = { navController?.navigate(Screen.AssistantAlerts.route) }
                )
                Spacer(Modifier.height(10.dp))

                ProfileMenuItem(
                    icon = Icons.Default.SupportAgent,
                    title = "الدعم الفني",
                    subtitle = "تواصل معنا",
                    gradient = listOf(Color(0xFF0D5C3F), Color(0xFF34C77B)),
                    onClick = { showHelpSupport = true }
                )

                Spacer(Modifier.height(24.dp))
                SectionTitle("الحساب")
                Spacer(Modifier.height(12.dp))

                ProfileMenuItem(
                    icon = Icons.Default.DeleteForever,
                    title = "حذف الحساب",
                    subtitle = "حذف الحساب نهائياً",
                    gradient = listOf(dangerColor, Color(0xFFE57373)),
                    onClick = { showDeleteAccountDialog = true }
                )

                Spacer(Modifier.height(8.dp))

                // ── Data Analysis Consent (PDPL) ──
                val behaviorConsent = viewModel.behaviorConsentGiven.collectAsState().value
                ProfileMenuItem(
                    icon = if (behaviorConsent) Icons.Default.CheckCircle else Icons.Default.TrackChanges,
                    title = "التحليل الذكي للسلوك",
                    subtitle = if (behaviorConsent) "مفعل — يتم تحليل بياناتك لتقديم تنبؤات مخصصة"
                               else "غير مفعل — فعل لتحصل على تنبؤات ذكية",
                    onClick = { showBehaviorConsentDialog = true }
                )

                Spacer(Modifier.height(24.dp))

                // Logout Button
                Button(
                    onClick = {
                        Log.d(TAG_PROF, "Logout button clicked -> calling Supabase auth.signOut()")
                        scope.launch {
                            try {
                                SupabaseRepo.client.auth.signOut()
                                com.example.data.SessionHelper.saveSession(context)
                                Log.d(TAG_PROF, "Logout success")
                            } catch (e: Exception) {
                                Log.e(TAG_PROF, "Logout error: ${e.message}")
                            }
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = errorContainer, contentColor = dangerColor)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("تسجيل الخروج", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
}

@Composable
fun AnimatedStatCard(
    icon: ImageVector,
    label: String,
    value: Int,
    color: Color,
    animTriggered: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedValue by animateFloatAsState(
        targetValue = if (animTriggered) value.toFloat() else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "stat_counter"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (animTriggered) 1f else 0.8f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "stat_scale"
    )

    Column(
        modifier = modifier
            .scale(scaleAnim)
            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = color.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${animatedValue.toInt()}",
            style = Typography.headlineMedium.copy(fontSize = 22.sp),
            fontWeight = FontWeight.Bold,
            color = onSurface
        )
        Text(label, style = Typography.labelSmall, color = onSurfaceVariant)
    }
}

@Composable
fun AchievementsSection(
    inventoryCount: Int,
    subscriptionsCount: Int,
    familyMembersCount: Int,
    tasbihaCount: Int,
    transactionsCount: Int
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        SectionTitle("الإنجازات")
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AchievementBadge(
                icon = Icons.Default.Inventory2,
                label = "مخزون",
                unlocked = inventoryCount >= 5,
                earned = inventoryCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.Subscriptions,
                label = "اشتراكات",
                unlocked = subscriptionsCount >= 3,
                earned = subscriptionsCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.People,
                label = "عائلة",
                unlocked = familyMembersCount >= 3,
                earned = familyMembersCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.Favorite,
                label = "تسبيحة",
                unlocked = tasbihaCount >= 1,
                earned = tasbihaCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.Payments,
                label = "معاملات",
                unlocked = transactionsCount >= 20,
                earned = transactionsCount >= 1
            )
        }
    }
}

@Composable
fun AchievementBadge(icon: ImageVector, label: String, unlocked: Boolean, earned: Boolean) {
    val alpha = if (earned) 1f else 0.4f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(surface).padding(10.dp)
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                .background(if (unlocked) primary.copy(alpha = 0.12f) else surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = primary.copy(alpha = alpha), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = Typography.labelSmall, color = onSurfaceVariant.copy(alpha = alpha), fontSize = 9.sp, maxLines = 1)
        Box(
            modifier = Modifier
                .size(6.dp).clip(CircleShape)
                .background(if (unlocked) primaryFixed else surfaceContainerLow)
                .padding(top = 2.dp)
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradient: List<Color> = listOf(primary, primaryFixed),
    onClick: () -> Unit = {}
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(100), label = "menu_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = gradient[0].copy(alpha = 0.1f))
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }.also { src ->
                    LaunchedEffect(src) {
                        src.interactions.collect { interaction ->
                            when (interaction) {
                                is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed = true
                                is androidx.compose.foundation.interaction.PressInteraction.Release -> pressed = false
                                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed = false
                                else -> {}
                            }
                        }
                    }
                }
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(colors = gradient))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = Typography.titleMedium.copy(fontSize = 14.sp), color = onSurface, fontWeight = FontWeight.Bold)
                Text(subtitle, style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun AvatarSelectionDialog(onDismiss: () -> Unit, onAvatarSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر شخصيتك", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary) },
        text = {
            Column {
                Text("اختر فاكهتك المفضلة كصورة شخصية لك:", style = Typography.bodyMedium, color = onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AvatarOption("avatar_carrot", onAvatarSelected)
                    AvatarOption("avatar_apple", onAvatarSelected)
                    AvatarOption("avatar_banana", onAvatarSelected)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = primary) } },
        containerColor = surface
    )
}

@Composable
fun AvatarOption(avatarName: String, onClick: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resId = context.resources.getIdentifier(avatarName, "drawable", context.packageName)
    val painter = if (resId != 0) { androidx.compose.ui.res.painterResource(id = resId) } else { null }
    Box(
        modifier = Modifier.size(60.dp).clip(CircleShape).border(2.dp, primaryFixed, CircleShape).clickable { onClick("drawable://$avatarName") },
        contentAlignment = Alignment.Center
    ) {
        if (painter != null) {
            androidx.compose.foundation.Image(painter = painter, contentDescription = "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = primary)
        }
    }
}

@Composable
fun EditNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل الاسم", style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = primary) } },
        containerColor = surface
    )
}
