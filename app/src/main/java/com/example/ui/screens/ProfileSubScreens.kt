package com.example.ui.screens

import android.util.Log
import androidx.compose.foundation.background
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
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

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

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        SubScreenTopBar(stringResource(R.string.edit_profile_title), onBack)
        
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
                            onBack()
                        } else {
                            Toast.makeText(context, saveFailedText, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save_changes), fontWeight = FontWeight.Bold)
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

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        SubScreenTopBar(stringResource(R.string.manage_family), onBack)
        
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            if (state is FamilyState.Active) {
                val activeState = state as FamilyState.Active
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = primary)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.family_invite_code_label), style = Typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                        Text(activeState.familyGroup.inviteCode, style = Typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
                            Text(member.role, fontSize = 12.sp, color = onSurfaceVariant)
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

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        SubScreenTopBar(stringResource(R.string.payment_and_budget_title), onBack)
        
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = surface)
            ) {
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
                            Button(onClick = {
                                val parsed = newBudgetStr.toDoubleOrNull() ?: budget
                                Log.d(TAG_SUB_PROF, "Save budget clicked → parsed=$parsed → calling viewModel.updateBudget()")
                                viewModel.updateBudget(parsed)
                                editMode = false
                            }) { Text(stringResource(R.string.save)) }
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
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.country_and_currency), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.country_and_currency_hint), style = Typography.bodySmall, color = onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            var selectedMarket by remember { mutableStateOf(com.example.data.MarketPrefs.getMarket(context)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                com.example.data.Market.entries.forEach { market ->
                    val isSelected = market == selectedMarket
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedMarket = market
                                com.example.data.MarketPrefs.setMarket(context, market)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) primary.copy(alpha = 0.15f) else surfaceContainer,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, primary) else null
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(market.displayNameAr, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) primary else onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(market.currencySymbol, fontSize = 12.sp, color = onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.auto_bank_sync), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            fun checkBankSyncGranted() = android.provider.Settings.Secure
                .getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(context.packageName) == true
            var isBankSyncEnabled by remember { mutableStateOf(checkBankSyncGranted()) }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        isBankSyncEnabled = checkBankSyncGranted()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            AlertSwitchItem(stringResource(R.string.enable_bank_sync), stringResource(R.string.enable_bank_sync_desc), isBankSyncEnabled) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.payment_methods_soon), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.card_linking_soon), color = onSurfaceVariant)
        }
    }
}

// 4. Assistant Alerts Screen — الإعدادات محفوظة فعلياً وتتحكم في الإشعارات
object AlertPrefs {
    private const val PREFS = "zad_alert_prefs"
    const val KEY_LOW_INVENTORY = "alert_low_inventory"
    const val KEY_BUDGET_OVERRUN = "alert_budget_overrun"
    const val KEY_MEAL_SUGGESTIONS = "alert_meal_suggestions"
    const val KEY_TASBIH_REMINDER = "alert_tasbih_reminder"

    fun isEnabled(context: android.content.Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(key, true)

    fun setEnabled(context: android.content.Context, key: String, enabled: Boolean) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(key, enabled).apply()
}

@Composable
fun AssistantAlertsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lowInventoryAlerts by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_LOW_INVENTORY)) }
    var budgetOverrunAlerts by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_BUDGET_OVERRUN)) }
    var mealSuggestions by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_MEAL_SUGGESTIONS)) }
    var tasbihReminder by remember { mutableStateOf(AlertPrefs.isEnabled(context, AlertPrefs.KEY_TASBIH_REMINDER)) }

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        SubScreenTopBar(stringResource(R.string.assistant_alerts_title), onBack)

        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            AlertSwitchItem(stringResource(R.string.low_inventory_alerts), stringResource(R.string.low_inventory_alerts_desc), lowInventoryAlerts) {
                lowInventoryAlerts = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_LOW_INVENTORY, it)
            }
            AlertSwitchItem(stringResource(R.string.budget_overrun_alerts), stringResource(R.string.budget_overrun_alerts_desc), budgetOverrunAlerts) {
                budgetOverrunAlerts = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_BUDGET_OVERRUN, it)
            }
            AlertSwitchItem(stringResource(R.string.meal_suggestions_alert), stringResource(R.string.meal_suggestions_alert_desc), mealSuggestions) {
                mealSuggestions = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_MEAL_SUGGESTIONS, it)
            }
            AlertSwitchItem(stringResource(R.string.tasbih_reminder_alert), stringResource(R.string.tasbih_reminder_alert_desc), tasbihReminder) {
                tasbihReminder = it
                AlertPrefs.setEnabled(context, AlertPrefs.KEY_TASBIH_REMINDER, it)
            }
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// 5. Help and Support Screen
@Composable
fun HelpAndSupportScreen(onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        Log.d(TAG_SUB_PROF, "HelpAndSupportScreen loaded")
    }

    Column(modifier = Modifier.fillMaxSize().background(background)) {
        SubScreenTopBar(stringResource(R.string.help_and_support_title), onBack)
        
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.faq_title), style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
            Spacer(modifier = Modifier.height(16.dp))
            FaqItem(stringResource(R.string.faq_add_subscription_q), stringResource(R.string.faq_add_subscription_a))
            FaqItem(stringResource(R.string.faq_share_budget_q), stringResource(R.string.faq_share_budget_a))
            Spacer(modifier = Modifier.height(32.dp))
            
            val supportContext = LocalContext.current
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:support@zad-app.com")
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Zad App — طلب دعم")
                        }
                        supportContext.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG_SUB_PROF, "No email app found: ${e.message}")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.contact_support_team), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FaqItem(q: String, a: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(q, fontWeight = FontWeight.Bold, color = primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(a, style = Typography.bodyMedium, color = onSurfaceVariant)
    }
}
