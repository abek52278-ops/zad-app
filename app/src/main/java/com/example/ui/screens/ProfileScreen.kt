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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.zadCardShadow
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
    navController: NavController? = null,
    /** يفعّل وضع الأطفال يدوياً (بلا PIN — الخروج منه بس هو اللي محتاج PIN، في MainScreen) */
    onSwitchToKidsMode: () -> Unit = {}
) {
    val loadingText = stringResource(R.string.loading_ellipsis)
    val newUserText = stringResource(R.string.new_user_default)
    var userEmail by remember { mutableStateOf(loadingText) }
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
    val displayUserName = userNameState ?: newUserText
    val globalAvatarUri by viewModel.avatarUri.collectAsState()
    val myTasbiha = familyViewModel.myTasbiha

    var animTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        userEmail = session?.user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: newUserText
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

    var showSaveSuccess by remember { mutableStateOf(false) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploadingAvatar = true
        scope.launch {
            val jpegBytes = withContext(Dispatchers.IO) {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val maxDim = 512
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap
                ByteArrayOutputStream().apply { scaled.compress(Bitmap.CompressFormat.JPEG, 85, this) }.toByteArray()
            }
            viewModel.uploadAvatar(jpegBytes, "image/jpeg") { success ->
                isUploadingAvatar = false
                showSaveSuccess = success
            }
        }
    }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showHelpSupport by remember { mutableStateOf(false) }
    var showBehaviorConsentDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) } // Phase B4 — ربط تليجرام

    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            kotlinx.coroutines.delay(1200)
            showSaveSuccess = false
        }
    }

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = displayUserName,
            onDismiss = { showEditNameDialog = false },
            onSave = { newName ->
                viewModel.updateUserProfile(newName, globalAvatarUri)
                showEditNameDialog = false
                showSaveSuccess = true
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.delete_account), fontWeight = FontWeight.Bold, color = dangerColor) },
            text = { Text(stringResource(R.string.delete_account_confirm), color = onSurface) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAccount()
                    showDeleteAccountDialog = false
                    onLogout()
                }, colors = ButtonDefaults.buttonColors(containerColor = dangerColor)) { Text(stringResource(R.string.confirm_delete_account)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAccountDialog = false }) { Text(stringResource(R.string.cancel), color = primary) } },
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
                    Text(stringResource(R.string.smart_behavior_analysis), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.behavior_consent_explanation))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setBehaviorConsent(true)
                        showBehaviorConsentDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (viewModel.behaviorConsentGiven.collectAsState().value) stringResource(R.string.deactivate) else stringResource(R.string.enable)) }
            },
            dismissButton = {
                if (viewModel.behaviorConsentGiven.collectAsState().value) {
                    TextButton(onClick = {
                        viewModel.setBehaviorConsent(false)
                        showBehaviorConsentDialog = false
                    }) { Text(stringResource(R.string.deactivate), color = dangerColor) }
                } else {
                    TextButton(onClick = { showBehaviorConsentDialog = false }) { Text(stringResource(R.string.later_action)) }
                }
            }
        )
    }

    if (showTelegramDialog) {
        TelegramLinkDialog(onDismiss = { showTelegramDialog = false })
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).clickable(enabled = !isUploadingAvatar) { avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable(enabled = !isUploadingAvatar) { avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
                                .clickable(enabled = !isUploadingAvatar) { avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF0D5C3F), modifier = Modifier.size(14.dp))
                        }
                        if (isUploadingAvatar) {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(displayUserName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("#ZAD-$userId", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
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
                    label = stringResource(R.string.in_inventory_label),
                    value = inventory.size,
                    color = primaryFixed,
                    animTriggered = animTriggered,
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCard(
                    icon = Icons.Default.Subscriptions,
                    label = stringResource(R.string.subscriptions),
                    value = subscriptions.size,
                    color = secondary,
                    animTriggered = animTriggered,
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCard(
                    icon = Icons.Default.FamilyRestroom,
                    label = stringResource(R.string.family_members),
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
                SectionTitle(stringResource(R.string.settings_title))
                Spacer(Modifier.height(12.dp))

                AppearOnEntry(delayMs = 0) {
                    ProfileMenuItem(
                        icon = Icons.Default.Edit,
                        title = stringResource(R.string.edit_profile_title),
                        subtitle = stringResource(R.string.edit_profile_subtitle),
                        gradient = listOf(Color(0xFF0D5C3F), Color(0xFF1A7A55)),
                        onClick = { navController?.navigate(Screen.EditProfile.route) }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 40) {
                    ProfileMenuItem(
                        icon = Icons.Default.FamilyRestroom,
                        title = stringResource(R.string.manage_family),
                        subtitle = stringResource(R.string.members_and_permissions),
                        gradient = listOf(Color(0xFFC8963E), Color(0xFFE8BC6A)),
                        onClick = { navController?.navigate(Screen.FamilyManagement.route) }
                    )
                }
                Spacer(Modifier.height(10.dp))

                if ((familyState as? FamilyState.Active)?.myMemberInfo?.role == "admin") {
                    AppearOnEntry(delayMs = 80) {
                        ProfileMenuItem(
                            icon = Icons.Default.ChildCare,
                            title = stringResource(R.string.switch_to_kids_mode),
                            subtitle = stringResource(R.string.switch_to_kids_mode_subtitle),
                            gradient = listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
                            onClick = onSwitchToKidsMode
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                AppearOnEntry(delayMs = 120) {
                    ProfileMenuItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = stringResource(R.string.budget_and_payment_methods),
                        subtitle = stringResource(R.string.monthly_budget_and_bank_link),
                        gradient = listOf(Color(0xFF1C6EA4), Color(0xFF60A5FA)),
                        onClick = { navController?.navigate(Screen.PaymentBudget.route) }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 160) {
                    ProfileMenuItem(
                        icon = Icons.Default.SmartToy,
                        title = stringResource(R.string.assistant_alerts_title),
                        subtitle = stringResource(R.string.control_smart_alerts),
                        gradient = listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)),
                        onClick = { navController?.navigate(Screen.AssistantAlerts.route) }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 180) {
                    var isRescanning by remember { mutableStateOf(false) }
                    val rescanningText = stringResource(R.string.rescanning_sms_toast)
                    val rescanDoneTextTemplate = stringResource(R.string.rescan_done_toast)
                    val permissionMissingText = stringResource(R.string.read_sms_permission_missing_toast)
                    ProfileMenuItem(
                        icon = Icons.Default.Sms,
                        title = stringResource(R.string.rescan_sms_title),
                        subtitle = stringResource(R.string.rescan_sms_subtitle),
                        gradient = listOf(Color(0xFF0EA5E9), Color(0xFF7DD3FC)),
                        onClick = {
                            if (isRescanning) return@ProfileMenuItem
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                android.widget.Toast.makeText(context, permissionMissingText, android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                isRescanning = true
                                android.widget.Toast.makeText(context, rescanningText, android.widget.Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val count = com.example.data.SmsBackfillScanner.rescan(context, com.example.data.SmsBackfillScanner.DEFAULT_SINCE_DAYS)
                                    isRescanning = false
                                    android.widget.Toast.makeText(context, rescanDoneTextTemplate.format(count), android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 190) {
                    ProfileMenuItem(
                        icon = Icons.Default.UploadFile,
                        title = stringResource(R.string.statement_import_title),
                        subtitle = stringResource(R.string.import_bank_statement_subtitle),
                        gradient = listOf(Color(0xFF059669), Color(0xFF6EE7B7)),
                        onClick = { navController?.navigate(Screen.StatementImport.route) }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 190) {
                    ProfileMenuItem(
                        icon = Icons.Default.Send,
                        title = stringResource(R.string.telegram_link_title),
                        subtitle = stringResource(R.string.telegram_link_subtitle),
                        gradient = listOf(Color(0xFF229ED9), Color(0xFF6FC6EE)),
                        onClick = { showTelegramDialog = true }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 200) {
                    ProfileMenuItem(
                        icon = Icons.Default.SupportAgent,
                        title = stringResource(R.string.nav_help),
                        subtitle = stringResource(R.string.contact_us),
                        gradient = listOf(Color(0xFF0D5C3F), Color(0xFF34C77B)),
                        onClick = { showHelpSupport = true }
                    )
                }
                Spacer(Modifier.height(10.dp))

                AppearOnEntry(delayMs = 220) {
                    ProfileMenuItem(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.terms_of_service_menu_title),
                        subtitle = stringResource(R.string.terms_of_service_menu_subtitle),
                        gradient = listOf(Color(0xFF64748B), Color(0xFF94A3B8)),
                        onClick = { navController?.navigate(Screen.TermsOfService.route) }
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.account_title))
                Spacer(Modifier.height(12.dp))

                AppearOnEntry(delayMs = 240) {
                    ProfileMenuItem(
                        icon = Icons.Default.DeleteForever,
                        title = stringResource(R.string.delete_account),
                        subtitle = stringResource(R.string.delete_account_permanently),
                        gradient = listOf(dangerColor, Color(0xFFE57373)),
                        onClick = { showDeleteAccountDialog = true }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Data Analysis Consent (PDPL) ──
                val behaviorConsent = viewModel.behaviorConsentGiven.collectAsState().value
                ProfileMenuItem(
                    icon = if (behaviorConsent) Icons.Default.CheckCircle else Icons.Default.TrackChanges,
                    title = stringResource(R.string.smart_behavior_analysis),
                    subtitle = if (behaviorConsent) stringResource(R.string.behavior_analysis_enabled_subtitle)
                               else stringResource(R.string.behavior_analysis_disabled_subtitle),
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
                    Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showSaveSuccess,
        modifier = Modifier.align(Alignment.Center),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f),
        exit = androidx.compose.animation.fadeOut()
    ) {
        Column(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZadLottieAsset(
                resId = R.raw.lottie_success_check,
                modifier = Modifier.size(72.dp),
                iterations = 1
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.changes_saved), style = Typography.bodyMedium, color = onSurface, fontWeight = FontWeight.Bold)
        }
    }
    }
}

/**
 * Phase B4 (PRODUCT_PLAN.md) — كود ربط تليجرام لمرة واحدة. EPIC_1_4.md: "a chat_id is
 * never an identity" — الكود ده هو إثبات الهوية الوحيد، مش أي حاجة تانية. الربط
 * الفعلي بيحصل من zad-telegram-bot لما العميل يبعت /start <code> في تليجرام.
 */
@Composable
private fun TelegramLinkDialog(onDismiss: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var linked by remember { mutableStateOf<Boolean?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var isUnlinking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val alreadyLinked = SupabaseRepo.isTelegramLinked()
        linked = alreadyLinked
        if (!alreadyLinked) code = SupabaseRepo.generateTelegramBindingCode()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telegram_link_title)) },
        text = {
            when {
                linked == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
                linked == true -> Text(stringResource(R.string.telegram_already_linked))
                code == null -> Text(stringResource(R.string.telegram_code_failed))
                else -> Column {
                    // اليوزرنيم الحقيقي من getMe (2026-07-31). "ZadSmartBot" هو الاسم
                    // المعروض للبوت مش اليوزرنيم — اللي كان مكتوب هنا قبل كده، والبحث
                    // بيه في تليجرام مكانش بيلاقي البوت أصلاً.
                    Text(stringResource(R.string.telegram_link_instructions, "@ZadhApp_bot"))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(background)
                            .clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code!!)) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(code!!, style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.telegram_code_expiry_note), style = Typography.labelSmall, color = onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (linked == true) {
                TextButton(
                    onClick = {
                        isUnlinking = true
                    }
                ) { Text(stringResource(R.string.telegram_unlink_action), color = dangerColor) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_action)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )

    if (isUnlinking) {
        LaunchedEffect(Unit) {
            SupabaseRepo.unlinkTelegram()
            isUnlinking = false
            onDismiss()
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

    // لا shadow/pressableScale هنا عمداً — دي بطاقة إحصائية للعرض فقط، مش زرار
    // (كانت بتبان زي زرار قابل للضغط بس من غير أي onClick حقيقي، مربكة للمستخدم).
    Column(
        modifier = modifier
            .scale(scaleAnim)
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.05f))
            .border(1.dp, color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
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
        SectionTitle(stringResource(R.string.achievements_title))
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AchievementBadge(
                icon = Icons.Default.Inventory2,
                label = stringResource(R.string.inventory_short_label),
                unlocked = inventoryCount >= 5,
                earned = inventoryCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.Subscriptions,
                label = stringResource(R.string.subscriptions),
                unlocked = subscriptionsCount >= 3,
                earned = subscriptionsCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.People,
                label = stringResource(R.string.family_short_label),
                unlocked = familyMembersCount >= 3,
                earned = familyMembersCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.Favorite,
                label = stringResource(R.string.tasbiha_short_label),
                unlocked = tasbihaCount >= 1,
                earned = tasbihaCount >= 1
            )
            AchievementBadge(
                icon = Icons.Default.Payments,
                label = stringResource(R.string.transactions_short_label),
                unlocked = transactionsCount >= 20,
                earned = transactionsCount >= 1
            )
        }
    }
}

@Composable
fun AchievementBadge(icon: ImageVector, label: String, unlocked: Boolean, earned: Boolean) {
    val alpha = if (earned) 1f else 0.4f
    // من غير خلفية بطاقة/كارت هنا عمداً — دي شارة إنجاز للعرض بس، مش زرار قابل
    // للضغط، فمفيش داعي تتلبس شكل كارت قابل للنقر زي ProfileMenuItem الحقيقية.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(10.dp)
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
            .zadCardShadow(RoundedCornerShape(16.dp), elevation = 6.dp)
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
fun EditNameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_name), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = primary) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = primary)) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = primary) } },
        containerColor = surface
    )
}
