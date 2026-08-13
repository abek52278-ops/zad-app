package com.example.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.ui.viewmodels.FamilyState
import com.example.ui.viewmodels.FamilyViewModel
import com.example.ui.viewmodels.ZadViewModel
import com.example.data.SupabaseRepo
import com.example.data.findActivity
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.data.BankReadingStatus
import com.example.data.SaBankParser

private const val TAG_SUB_PROF = "ProfileSubScreens"

@Composable
fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            Log.d(TAG_SUB_PROF, "Back button clicked in $title")
            onBack()
        }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Back", tint = onSurface)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = Typography.titleLarge,
            color = onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

// 1. Edit Profile Screen
@Composable
fun EditProfileScreen(viewModel: ZadViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("...") }
    var alias by remember { mutableStateOf("") }
    var showSaveSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accountName by viewModel.userName.collectAsState()
    val newUserText = stringResource(R.string.new_user_default)
    val savedChangesText = stringResource(R.string.changes_saved)
    val saveFailedText = stringResource(R.string.changes_save_failed)

    LaunchedEffect(Unit) {
        val session = SupabaseRepo.client.auth.currentSessionOrNull()
        email = session?.user?.email ?: ""
        // الاسم المعروض في باقي التطبيق (زاد_users.name) هو مصدر الحقيقة —
        // مع fallback للقب العائلة (family_members.alias) لو الاسم الأساسي لسه فاضي
        val myMember = SupabaseRepo.getMyFamilyMember()
        alias = accountName?.takeIf { it.isNotBlank() && it != newUserText } ?: (myMember?.alias ?: "")
        Log.d(TAG_SUB_PROF, "EditProfileScreen loaded — userEmail=$email, alias=$alias")
    }

    // نفس نمط التأكيد اللحظي المستخدم في ProfileScreen.showSaveSuccess —
    // يظهر لفترة قصيرة ثم يرجع تلقائياً للشاشة السابقة
    LaunchedEffect(showSaveSuccess) {
        if (showSaveSuccess) {
            kotlinx.coroutines.delay(1200)
            showSaveSuccess = false
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(stringResource(R.string.edit_profile_title), onBack)

        AppearOnEntry {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            // Avatar circle
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(alias.ifEmpty { email.take(1).uppercase() }.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge, color = primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true, enabled = false,
                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = onSurfaceVariant)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(stringResource(R.string.name_alias_label)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.eg_father)) }
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    Log.d(TAG_SUB_PROF, "Save profile clicked — alias=$alias")
                    if (alias.isBlank()) {
                        onBack()
                        return@Button
                    }
                    // zad_users هو المصدر اللي بتقرا منه بقية شاشات التطبيق (اسم الترحيب، الشات، إلخ)
                    viewModel.updateUserProfile(alias, null) { saved ->
                        if (saved) {
                            // مزامنة أفضل جهد للقب العائلة كمان لو المستخدم عضو في عائلة بالفعل
                            scope.launch { SupabaseRepo.updateFamilyMemberAlias(alias) }
                            Toast.makeText(context, savedChangesText, Toast.LENGTH_SHORT).show()
                            showSaveSuccess = true
                        } else {
                            Toast.makeText(context, saveFailedText, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp).pressableScale(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save_changes), fontWeight = FontWeight.Bold)
            }
        }
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showSaveSuccess,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut()
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(savedChangesText, style = Typography.bodyMedium, color = onSurface, fontWeight = FontWeight.Bold)
        }
    }
    }
}

// 2. Family Management Screen
@Composable
fun FamilyManagementScreen(familyViewModel: FamilyViewModel, onBack: () -> Unit) {
    val state by familyViewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Log.d(TAG_SUB_PROF, "FamilyManagementScreen loaded — state=${state::class.simpleName}")
        
        launch {
            familyViewModel.toastMessage.collect { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(stringResource(R.string.manage_family), onBack)

        AppearOnEntry {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            if (state is FamilyState.Active) {
                val activeState = state as FamilyState.Active
                // the design's inset mesh banner, same as every other headline number
                com.example.ui.components.ZadScreenBanner(contentPadding = 20.dp) {
                    Column {
                        Text(stringResource(R.string.family_invite_code_label), style = Typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                        Text(activeState.familyGroup.inviteCode, style = Typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.family_members), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(modifier = Modifier.height(12.dp))
                activeState.members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(surfaceContainerLow, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.alias, fontWeight = FontWeight.Bold, color = onSurface)
                            Text(
                                when (member.role) {
                                    "admin" -> stringResource(R.string.family_admin_role)
                                    "child" -> stringResource(R.string.child_role)
                                    else -> stringResource(R.string.member_role)
                                },
                                fontSize = 12.sp, color = onSurfaceVariant
                            )
                        }
                        
                        // Check if current user is admin to show controls
                        if (activeState.myMemberInfo.role == "admin" && member.id != activeState.myMemberInfo.id) {
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.promote_to_admin)) },
                                        onClick = {
                                            Log.d(TAG_SUB_PROF, "Change role to admin clicked for member ${member.id}")
                                            familyViewModel.changeMemberRole(member.id, "admin")
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.demote_to_member)) },
                                        onClick = {
                                            Log.d(TAG_SUB_PROF, "Change role to member clicked for member ${member.id}")
                                            familyViewModel.changeMemberRole(member.id, "member")
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.set_as_child)) },
                                        onClick = {
                                            Log.d(TAG_SUB_PROF, "Change role to child clicked for member ${member.id}")
                                            familyViewModel.changeMemberRole(member.id, "child")
                                            expanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.kick_member), color = dangerColor) },
                                        onClick = {
                                            Log.d(TAG_SUB_PROF, "Kick member clicked for member ${member.id}")
                                            familyViewModel.kickMember(member.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.not_in_family_yet), color = onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
            }
        }
        }
    }
}

// 3. Payment and Budget Screen
@Composable
fun PaymentAndBudgetScreen(viewModel: ZadViewModel, onBack: () -> Unit) {
    val budget by viewModel.budget.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var newBudgetStr by remember { mutableStateOf(budget.toString()) }
    val context = LocalContext.current

    LaunchedEffect(budget) {
        Log.d(TAG_SUB_PROF, "PaymentAndBudgetScreen loaded — current budget=$budget")
        newBudgetStr = budget.toString()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(stringResource(R.string.payment_and_budget_title), onBack)

        AppearOnEntry {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            com.example.ui.components.ZadListCard(contentPadding = 0.dp) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.current_monthly_budget), style = Typography.labelMedium, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (editMode) {
                        OutlinedTextField(
                            value = newBudgetStr,
                            onValueChange = { newBudgetStr = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Button(
                                onClick = {
                                    val parsed = newBudgetStr.toDoubleOrNull() ?: budget
                                    Log.d(TAG_SUB_PROF, "Save budget clicked → parsed=$parsed → calling viewModel.updateBudget()")
                                    viewModel.updateBudget(parsed)
                                    editMode = false
                                },
                                modifier = Modifier.pressableScale()
                            ) { Text(stringResource(R.string.save)) }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = { editMode = false }) { Text(stringResource(R.string.cancel)) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(com.example.data.CurrencyFormatter.format(context, budget), style = Typography.headlineMedium, color = primary, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { editMode = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Budget", tint = primary)
                            }
                        }
                    }
                }
            }
            // البلد/العملة بقى مكان واحد بس (الإعدادات الإقليمية جوا البروفايل الرئيسي) —
            // كان فيه بيكر تاني هنا بيعمل بالظبط نفس المهمة (MarketPrefs.setMarket +
            // syncMarketProfile)، يعني تغيير من هنا وتغيير من هناك مكانش بينهم فرق، بس
            // مكانين مختلفين لنفس القرار مربكين.
            Spacer(modifier = Modifier.height(24.dp))
            BankReadingStatusSection()
        }
        }
    }
}

// 4. Assistant Alerts Screen — الإعدادات محفوظة فعلياً وتتحكم في الإشعارات
object AlertPrefs {
    private const val PREFS = "zad_alert_prefs"
    const val KEY_LOW_INVENTORY = "alert_low_inventory"
    const val KEY_BUDGET_OVERRUN = "alert_budget_overrun"
    const val KEY_TASBIH_REMINDER = "alert_tasbih_reminder"
    private const val KEY_NOTIFICATION_SOUND_URI = "notification_sound_uri"
    // NotificationChannel.sound مينفعش يتغيّر بعد ما القناة اتعملت (Android O+) — القناة
    // القديمة بصوتها القديم بتفضل موجودة على الجهاز، والرقم ده بيتزوّد كل مرة يتغيّر فيها
    // الصوت عشان ZadNotifier ينشئ قناة جديدة (ID مختلف) بدل ما يحاول يعدّل قناة قديمة.
    private const val KEY_NOTIFICATION_SOUND_VERSION = "notification_sound_version"

    fun isEnabled(context: android.content.Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(key, true)

    fun setEnabled(context: android.content.Context, key: String, enabled: Boolean) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(key, enabled).apply()

    /** null = لسه محددش صوت مخصص، النظام هيستخدم صوت قناة الإشعارات الافتراضي. */
    fun getNotificationSoundUri(context: android.content.Context): String? =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_NOTIFICATION_SOUND_URI, null)

    fun setNotificationSoundUri(context: android.content.Context, uri: String?) {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val version = prefs.getInt(KEY_NOTIFICATION_SOUND_VERSION, 0) + 1
        prefs.edit()
            .putString(KEY_NOTIFICATION_SOUND_URI, uri)
            .putInt(KEY_NOTIFICATION_SOUND_VERSION, version)
            .apply()
    }

    fun getNotificationSoundVersion(context: android.content.Context): Int =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getInt(KEY_NOTIFICATION_SOUND_VERSION, 0)
}

@Composable
fun AssistantAlertsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lowInventoryAlerts by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_LOW_INVENTORY)) }
    var budgetOverrunAlerts by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_BUDGET_OVERRUN)) }
    var tasbihReminder by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_TASBIH_REMINDER)) }
    var soundUri by remember { mutableStateOf(AlertPrefs.getNotificationSoundUri(context)) }

    val soundPickerLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val picked = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val pickedStr = picked?.toString()
        soundUri = pickedStr
        AlertPrefs.setNotificationSoundUri(context, pickedStr)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(stringResource(R.string.assistant_alerts_title), onBack)

        AppearOnEntry {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            AlertSwitchItem(stringResource(R.string.low_inventory_alerts), stringResource(R.string.low_inventory_alerts_desc), lowInventoryAlerts) {
                lowInventoryAlerts = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_LOW_INVENTORY, it)
            }
            AlertSwitchItem(stringResource(R.string.budget_overrun_alerts), stringResource(R.string.budget_overrun_alerts_desc), budgetOverrunAlerts) {
                budgetOverrunAlerts = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_BUDGET_OVERRUN, it)
            }
            AlertSwitchItem(stringResource(R.string.tasbih_reminder_alert), stringResource(R.string.tasbih_reminder_alert_desc), tasbihReminder) {
                tasbihReminder = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_TASBIH_REMINDER, it)
            }

            HorizontalDivider(color = outlineVariant, modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.notification_sound_title), fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.notification_sound_desc), fontSize = 12.sp, color = onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSoundName = remember(soundUri) {
                    val uri = soundUri?.let { android.net.Uri.parse(it) }
                        ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    try {
                        android.media.RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                    } catch (e: Exception) { null } ?: context.getString(R.string.notification_sound_default)
                }
                Text(currentSoundName, fontSize = 13.sp, color = onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.notification_sound_title))
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        val existing = soundUri?.let { android.net.Uri.parse(it) }
                            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                    }
                    soundPickerLauncher.launch(intent)
                }) { Text(stringResource(R.string.change_action)) }
            }
            // حالة قراءة رسايل البنك بقت مكان واحد بس — جوا "الميزانية وطرق الدفع"
            // (PaymentAndBudgetScreen)، فعليًا هو أصلها الموضوعي (بيانات مالية)، مش هنا.
        }
        }
    }
}

/**
 * حالة قراءة رسايل البنك — حية فعلاً، مش toggle بيكذب. لو المستخدم فعّل الصلاحية من إعدادات
 * النظام ثم سحبها، الصف ده هيعرف فوراً (بيقرا NotificationManagerCompat/ContextCompat
 * مباشرة، مش قيمة متخزنة). زر "اختبار" بيشغل الـ parser على رسالة عينة عشان المستخدم
 * يتأكد إن القراءة شغالة من غير ما يستنى معاملة حقيقية.
 */
@Composable
fun BankReadingStatusSection() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    var listenerEnabled by remember { mutableStateOf(BankReadingStatus.isNotificationListenerEnabled(context)) }
    var lastParsedAt by remember { mutableStateOf(BankReadingStatus.lastParsedAt(context)) }
    var testResult by remember { mutableStateOf<String?>(null) }

    // الرجوع من إعدادات النظام (بعد منح/سحب صلاحية) لازم يحدّث الحالة فوراً — مش بس أول فتح للشاشة
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                listenerEnabled = BankReadingStatus.isNotificationListenerEnabled(context)
                lastParsedAt = BankReadingStatus.lastParsedAt(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column {
        Text(stringResource(R.string.bank_reading_status_title), fontWeight = FontWeight.Bold, color = onSurface)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.bank_reading_status_desc), fontSize = 12.sp, color = onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        BankReadingStatusRow(
            label = stringResource(R.string.notification_reading_status_label),
            isOn = listenerEnabled,
            actionLabel = if (!listenerEnabled) stringResource(R.string.enable_action) else null,
            onAction = {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            lastParsedAt?.let {
                val minutesAgo = (System.currentTimeMillis() - it) / 60000
                stringResource(R.string.last_parsed_at_format, minutesAgo)
            } ?: stringResource(R.string.last_parsed_never),
            fontSize = 12.sp,
            color = onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val sample = "CIB: تم خصم مبلغ 250.00 جنيه من حسابك لدى كارفور مصر الجديدة. مرجع: TX48213"
                val parsed = SaBankParser.detectAndParse("CIB", "", sample, context)
                testResult = if (parsed != null) {
                    "${context.getString(R.string.test_parse_success)}\n" +
                        "${context.getString(R.string.test_parse_amount)}: ${parsed.amount} ${parsed.category}\n" +
                        "${context.getString(R.string.test_parse_merchant)}: ${parsed.merchantName ?: "—"}\n" +
                        "${context.getString(R.string.test_parse_type)}: ${parsed.txType.arabicLabel}"
                } else {
                    context.getString(R.string.test_parse_failed)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.test_bank_parser_button))
        }

        testResult?.let { result ->
            AlertDialog(
                onDismissRequest = { testResult = null },
                title = { Text(stringResource(R.string.test_bank_parser_button), fontWeight = FontWeight.Bold) },
                text = { Text(result) },
                confirmButton = {
                    TextButton(onClick = { testResult = null }) { Text(stringResource(R.string.ok_action)) }
                }
            )
        }

        // كان مفيش أي طريقة يشوف بيها المستخدم (أو حتى إحنا) ليه إشعار بنك معين اترفض/
        // محصلش تسجيل — العميل حس "الرقم ثابت والإيجنت أعمى" من غير ما يقدر يشخّص السبب.
        // آخر الرسائل المرفوضة ظاهرة هنا بسببها ونصها، فلو InstaPay مثلاً مبيتسجلش، السبب
        // بيبان (صلاحية مقفولة، ولا الرسالة اتفهمت غلط). كانت مكررة بنسخة يدوية منفصلة
        // في PaymentAndBudgetScreen — دمجت هنا عشان تبقى مكان واحد بس لحالة قراءة البنك.
        if (listenerEnabled) {
            var rejectedMessages by remember { mutableStateOf<List<com.example.data.RejectedBankMessage>>(emptyList()) }
            LaunchedEffect(Unit) {
                rejectedMessages = com.example.data.local.ZadDatabase.getDatabase(context).zadDao().getRejectedBankMessages()
            }
            if (rejectedMessages.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.bank_sync_rejected_title), style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = onSurface)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.bank_sync_rejected_hint), style = Typography.bodySmall, color = onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                rejectedMessages.take(15).forEach { msg ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(msg.source, style = Typography.labelMedium, fontWeight = FontWeight.SemiBold, color = onSurface)
                            Text(msg.reason, style = Typography.labelSmall, color = dangerColor)
                        }
                        Text(msg.rawText, style = Typography.bodySmall, color = onSurfaceVariant, maxLines = 2)
                    }
                    HorizontalDivider(color = outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun BankReadingStatusRow(label: String, isOn: Boolean, actionLabel: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(if (isOn) successColor else dangerColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$label: ${if (isOn) stringResource(R.string.status_on) else stringResource(R.string.status_off)}",
                color = onSurface, fontSize = 13.sp
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel, color = primary) }
        }
    }
}

@Composable
fun AlertSwitchItem(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = onSurface)
            Text(desc, fontSize = 12.sp, color = onSurfaceVariant)
        }
        com.example.ui.components.ZadSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

