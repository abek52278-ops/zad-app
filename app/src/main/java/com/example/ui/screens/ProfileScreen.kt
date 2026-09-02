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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.zadCardShadow
import com.example.ui.components.zadGlassBlur
import com.example.ui.theme.*
import androidx.compose.runtime.*
import com.example.data.SupabaseRepo
import com.example.data.findActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import com.example.ui.viewmodels.FamilyViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.ZadViewModel
import android.util.Log
import androidx.navigation.NavController

private const val TAG_PROF = "ProfileScreen"

@Composable
fun ProfileScreen(
    viewModel: ZadViewModel,
    familyViewModel: FamilyViewModel = viewModel(),
    onLogout: () -> Unit = {},
    navController: NavController? = null,
    /** يفعّل وضع الأطفال يدوياً (بلا PIN — الخروج منه بس هو اللي محتاج PIN، في MainScreen) */
    onSwitchToKidsMode: () -> Unit = {}
) {
    val loadingText = stringResource(R.string.loading_ellipsis)
    val newUserText = stringResource(R.string.new_user_default)
    val saveFailedText = stringResource(R.string.changes_save_failed)
    val deleteAccountFailedText = stringResource(R.string.delete_account_failed)
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

    var showSaveSuccess by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
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
            // الصورة بتتكتب على تخزين التطبيق الداخلي وبتظهر في الواجهة فوراً هنا —
            // الرفع لـ Supabase بيكمل في الخلفية جوه saveAvatarLocally()، من غير ما
            // المستخدم يستنى شبكة عشان يشوف الصورة اللي هو لسه واخدها.
            viewModel.saveAvatarLocally(jpegBytes, "image/jpeg")
            isUploadingAvatar = false
            showSaveSuccess = true
        }
    }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showHelpSupport by remember { mutableStateOf(false) }
    var showBehaviorConsentDialog by remember { mutableStateOf(false) }
    var showRegionalSettings by remember { mutableStateOf(false) }
    // حلقة الأهداف — زر "هدف جديد" يفتح حوار بيبعت الهدف للعقل (set_life_goal).
    var showNewGoalDialog by remember { mutableStateOf(false) }

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
                showEditNameDialog = false
                viewModel.updateUserProfile(newName, globalAvatarUri) { success ->
                    if (success) {
                        showSaveSuccess = true
                    } else {
                        android.widget.Toast.makeText(context, saveFailedText, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.delete_account), fontWeight = FontWeight.Bold, color = dangerColor) },
            text = { Text(stringResource(R.string.delete_account_confirm), color = onSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        viewModel.deleteAccount { deleted ->
                            isDeletingAccount = false
                            if (deleted) {
                                showDeleteAccountDialog = false
                                onLogout()
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    deleteAccountFailedText,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text(stringResource(R.string.confirm_delete_account))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount
                ) { Text(stringResource(R.string.cancel), color = primary) }
            },
            containerColor = surface
        )
    }

    if (showHelpSupport) {
        HelpSupportScreen(onBack = { showHelpSupport = false })
    }

    if (showNewGoalDialog) {
        NewLifeGoalDialog(
            onDismiss = { showNewGoalDialog = false },
            onSubmit = { title, metric, deadline ->
                viewModel.submitLifeGoal(title, metric, deadline) { _ ->
                    showNewGoalDialog = false
                }
            }
        )
    }
    if (showRegionalSettings) {
        RegionalSettingsSheet(
            viewModel = viewModel,
            scope = scope,
            saveFailedText = saveFailedText,
            onDismiss = { showRegionalSettings = false }
        )
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


    Box(modifier = Modifier.fillMaxSize()) {
    // Transparent, not `background` (white): MainScreen paints the mockup's neutral
    // canvas gradient behind every screen, and a white fill here would flatten the
    // white menu cards back into the "dead white blocks" the design review called out.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Profile header, mockup `renderProfile` ──────────────────────
            // The mockup's is an inset 22dp gradient card: avatar, name, member id.
            // What stood here was a 280dp full-bleed banner with four decorative
            // translucent circles and an animated glow ring around the avatar —
            // a third of the screen spent on ornament before the first setting.
            // The avatar picker, its edit badge and the upload spinner survive,
            // because those are controls, not decoration.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF0B6B4E))))
            ) {
                // مرحلة ٥ب-٥ — نفس بقعة الضوء الزجاجية بتاعة باقي كروت الـ glass family
                // (ZadCardHero، اللوكيشن، المخزون، الاشتراكات) — هيدر البروفايل عنصر
                // واحد في الشاشة، مرشّح طبيعي زيهم بالظبط.
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopStart)
                        .offset(x = (-24).dp, y = (-24).dp)
                        .zadGlassBlur(32.dp)
                        .background(Color.White.copy(alpha = 0.16f), CircleShape)
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(64.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable(enabled = !isUploadingAvatar) {
                                    avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!globalAvatarUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = globalAvatarUri,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    placeholder = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.avatar),
                                    error = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.avatar),
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    displayUserName.trim().take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable(enabled = !isUploadingAvatar) {
                                    avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_profile_title), tint = primary, modifier = Modifier.size(13.dp))
                        }
                        if (isUploadingAvatar) {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Text(displayUserName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("#ZAD-$userId", color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp)
                }
            }

            // ── Kids-mode toggle card (mockup `renderProfile`) ───────────────
            // The mockup puts this directly under the header as a card with a real
            // switch. It was one more row in the menu list, indistinguishable from
            // "terms of service", for a control that flips the whole app's UI.
            Spacer(Modifier.height(14.dp))
            if ((familyState as? FamilyState.Active)?.myMemberInfo?.role == "admin") {
                com.example.ui.components.ZadMenuGroup(
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.switch_to_kids_mode),
                        subtitle = stringResource(R.string.switch_to_kids_mode_subtitle),
                        onClick = onSwitchToKidsMode,
                        showDivider = false,
                        trailing = {
                            com.example.ui.components.ZadSwitch(
                                checked = false,
                                onCheckedChange = { if (it) onSwitchToKidsMode() }
                            )
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // ── Settings, as the mockup's single grouped card ────────────────
                // Was ten separate 16dp cards, each with its own 44dp gradient icon
                // tile in a different colour pair. Ten gradients in one scroll is the
                // opposite of the design, which spends colour on the hero and the AI
                // card and keeps settings neutral. The rows, their order and every
                // destination are unchanged.
                SectionTitle(stringResource(R.string.settings_title))
                Spacer(Modifier.height(12.dp))

                com.example.ui.components.ZadMenuGroup {
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.edit_profile_title),
                        subtitle = stringResource(R.string.edit_profile_subtitle),
                        onClick = { navController?.navigate(com.example.ZadNav.EDIT_PROFILE) }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.manage_family),
                        subtitle = stringResource(R.string.members_and_permissions),
                        onClick = { navController?.navigate(com.example.ZadNav.FAMILY_MANAGEMENT) }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.budget_and_payment_methods),
                        subtitle = stringResource(R.string.monthly_budget_and_bank_link),
                        onClick = { navController?.navigate(com.example.ZadNav.PAYMENT_BUDGET) }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.regional_settings_title),
                        subtitle = stringResource(R.string.regional_settings_subtitle),
                        onClick = { showRegionalSettings = true }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.assistant_alerts_title),
                        subtitle = stringResource(R.string.control_smart_alerts),
                        onClick = { navController?.navigate(com.example.ZadNav.ASSISTANT_ALERTS) }
                    )
                    // Phase A6 — this slot held "rescan SMS", which needed READ_SMS.
                    // Reading is done by the notification listener now. Live status +
                    // the actual toggle live in AssistantAlertsScreen's
                    // BankReadingStatusSection — this row and PaymentAndBudgetScreen's
                    // "auto bank sync" switch used to duplicate that same permission
                    // control two more times with no live status of their own.
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.statement_import_title),
                        subtitle = stringResource(R.string.import_bank_statement_subtitle),
                        onClick = { navController?.navigate(com.example.ui.components.ZadRoutes.STATEMENT) }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.nav_help),
                        subtitle = stringResource(R.string.contact_us),
                        onClick = { showHelpSupport = true }
                    )
                    // W5 — سجل agent_actions + تراجع. مكانه هنا لأنه إعداد/شفافية زي
                    // باقي الصف، مش فعل يومي بيتكرر.
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.agent_action_log_title),
                        subtitle = stringResource(R.string.agent_action_log_subtitle),
                        onClick = { navController?.navigate(com.example.ZadNav.AGENT_ACTION_LOG) }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.zad_memory_title),
                        subtitle = stringResource(R.string.zad_memory_subtitle),
                        onClick = { navController?.navigate(com.example.ZadNav.ZAD_MEMORY) }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.new_life_goal_title),
                        subtitle = stringResource(R.string.new_life_goal_subtitle),
                        onClick = { showNewGoalDialog = true }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.terms_of_service_menu_title),
                        subtitle = stringResource(R.string.terms_of_service_menu_subtitle),
                        onClick = { navController?.navigate(com.example.ZadNav.TERMS) },
                        showDivider = false
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.account_title))
                Spacer(Modifier.height(12.dp))

                val behaviorConsent = viewModel.behaviorConsentGiven.collectAsState().value
                com.example.ui.components.ZadMenuGroup {
                    // ── Data Analysis Consent (PDPL) ──
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.smart_behavior_analysis),
                        subtitle = if (behaviorConsent) stringResource(R.string.behavior_analysis_enabled_subtitle)
                                   else stringResource(R.string.behavior_analysis_disabled_subtitle),
                        onClick = { showBehaviorConsentDialog = true },
                        trailing = {
                            com.example.ui.components.ZadSwitch(
                                checked = behaviorConsent,
                                onCheckedChange = { showBehaviorConsentDialog = true },
                                checkedColor = primary
                            )
                        }
                    )
                    com.example.ui.components.ZadMenuRow(
                        title = stringResource(R.string.delete_account),
                        subtitle = stringResource(R.string.delete_account_permanently),
                        onClick = { showDeleteAccountDialog = true },
                        titleColor = dangerColor,
                        showDivider = false
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Logout Button
                Button(
                    onClick = {
                        Log.d(TAG_PROF, "Logout button clicked -> clearing account state and signing out")
                        viewModel.logout {
                            Log.d(TAG_PROF, "Logout complete")
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
                .zadCardShadow(RoundedCornerShape(20.dp), elevation = 12.dp)
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

@Composable
fun SectionTitle(text: String) {
    Text(text, style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
}

/** "الإعدادات الإقليمية" — شيت واحد للغة/البلد/العملة بدل ما تكون متفرقة في شاشات مختلفة.
 * العملة مش قابلة للاختيار المستقل عمداً — كل سوق (Market) بيحدد عملته، فهنا بس عرض
 * لنتيجة اختيار البلد، مش تحكّم ثالث منفصل (نفس منطق CurrencyFormatter/MarketPrefs). */
@Composable
private fun RegionalSettingsSheet(
    viewModel: ZadViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    saveFailedText: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentLanguage by remember { mutableStateOf(com.example.data.LocaleHelper.currentLanguage()) }
    var selectedMarket by remember { mutableStateOf(com.example.data.MarketPrefs.getMarket(context)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.regional_settings_title), style = Typography.titleLarge, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.language_section_label), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.height(8.dp))
            // شريحة لكل لغة ليها مجلد values-* فعلاً، مش زرارين بولياني. التركي كان
            // مترجم بالكامل ومحدش يقدر يوصله من أي شاشة — الاختيار كان isArabic
            // true/false، فالتالتة مكانش ليها مكان أصلاً.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.data.LocaleHelper.supported.forEach { (tag, label) ->
                    FilterChip(
                        selected = currentLanguage == tag,
                        onClick = {
                            com.example.data.LocaleHelper.setLanguage(context, tag)
                            currentLanguage = tag
                            context.findActivity()?.recreate()
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.country_section_label), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.height(8.dp))
            com.example.ui.components.MarketPickerGrid(
                selected = selectedMarket,
                onSelect = { market ->
                    val previousMarket = com.example.data.MarketPrefs.currentMarket
                    selectedMarket = market
                    com.example.data.MarketPrefs.setMarket(context, market)
                    viewModel.convertLimitsForMarketChange(context, previousMarket, market)
                    scope.launch {
                        val synced = com.example.data.SupabaseRepo.syncMarketProfile(market)
                        if (!synced) {
                            android.widget.Toast.makeText(context, saveFailedText, android.widget.Toast.LENGTH_LONG).show()
                            com.example.data.SyncOutbox.enqueueMarketProfile(context, market.currencyCode, market.countryCode)
                        }
                    }
                    context.findActivity()?.recreate()
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.currency_section_label), style = Typography.titleSmall, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "${selectedMarket.currencySymbol} (${selectedMarket.currencyCode})",
                style = Typography.headlineSmall, fontWeight = FontWeight.Bold, color = primary
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.currency_derived_note), style = Typography.labelSmall, color = onSurfaceVariant)
        }
    }
}

/**
 * حوار "هدف جديد" — حلقة الأهداف: العميل يكتب الهدف والمقياس والاستحقاق،
 * والنداء بيمشي في أنبوب الشات العادي (agent_turn) فالعقل بيسجل بـ set_life_goal
 * ويقترح تفكيكه مهام فوراً في رده.
 */
@Composable
fun NewLifeGoalDialog(onDismiss: () -> Unit, onSubmit: (title: String, metric: String?, deadline: String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_life_goal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.new_life_goal_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = metric,
                    onValueChange = { metric = it },
                    label = { Text(stringResource(R.string.new_life_goal_metric)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text(stringResource(R.string.new_life_goal_deadline)) },
                    placeholder = { Text(stringResource(R.string.new_life_goal_deadline_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(title, metric.ifBlank { null }, deadline.ifBlank { null }) },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.save_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) }
        }
    )
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
