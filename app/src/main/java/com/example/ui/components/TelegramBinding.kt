package com.example.ui.components

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SupabaseRepo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private const val TAG_TG = "TelegramBinding"

/**
 * The bot's real Telegram **username**, confirmed against `getMe` on 2026-07-31.
 *
 * `ZadSmartBot` — which the app used to print, and which still gets asked for by
 * name — is only the bot's *display name*. Searching Telegram for `@ZadSmartBot`
 * finds nothing, so a fully working bot looked broken to anyone following the app's
 * own instructions. Anything user-facing must use this constant, never the display
 * name.
 */
const val TELEGRAM_BOT_USERNAME = "ZadhApp_bot"

/**
 * Phase B4 (PRODUCT_PLAN.md) — Telegram binding, as an inline section rather than the
 * dialog it replaces.
 *
 * The dialog generated a code and then asked the customer to memorise it, switch app,
 * find the bot by hand and type `/start <code>`. This section does the same binding
 * with one tap: `https://t.me/<bot>?start=<code>` is Telegram's deep-link form, and
 * Telegram delivers it to the bot as literally `/start <code>` — which is exactly what
 * `zad-telegram-bot`'s existing start handler already parses. Nothing on the server had
 * to change, and nobody has to touch the database by hand.
 *
 * The code stays visible with a copy target and a manual-fallback line, because the
 * deep link fails on a device with no Telegram app and no browser.
 *
 * Security note kept from the dialog — EPIC_1_4.md: "a chat_id is never an identity".
 * This one-time code is the only proof of identity in the flow; the row it inserts
 * binds nothing on its own until the bot fills in `chat_id`/`bound_at`.
 */
@Composable
fun TelegramBindingSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var linked by remember { mutableStateOf<Boolean?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    val copiedToast = stringResource(R.string.telegram_code_copied_toast)
    val noAppToast = stringResource(R.string.telegram_no_app_toast)

    suspend fun load() {
        val alreadyLinked = SupabaseRepo.isTelegramLinked()
        linked = alreadyLinked
        // Only mint a code when there's actually something to bind — otherwise every
        // visit to Profile would insert a throwaway telegram_bindings row.
        if (!alreadyLinked && code == null) code = SupabaseRepo.generateTelegramBindingCode()
    }

    LaunchedEffect(refreshTick) { load() }

    // The binding completes in Telegram, not here, so the only moment this screen can
    // learn about it is when the customer comes back to the app.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && linked != true) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(BrandTelegram.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = BrandTelegram, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.telegram_link_title),
                    style = Typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Text(
                    stringResource(R.string.telegram_link_subtitle),
                    style = Typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
            // Connection status is stated up front — this is the question the section exists
            // to answer, so it should not require reading the body to find out.
            val statusLinked = linked == true
            if (linked != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background((if (statusLinked) successColor else textTertiary).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (statusLinked) successColor else textTertiary)
                    )
                    Text(
                        stringResource(if (statusLinked) R.string.telegram_status_linked else R.string.telegram_status_not_linked),
                        style = Typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (statusLinked) successColor else onSurfaceVariant
                    )
                }
            }
        }

        when {
            linked == null -> Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            linked == true -> {
                Text(stringResource(R.string.telegram_already_linked), style = Typography.bodyMedium, color = onSurfaceVariant)
                TextButton(
                    onClick = {
                        if (isBusy) return@TextButton
                        isBusy = true
                        scope.launch {
                            SupabaseRepo.unlinkTelegram()
                            code = null
                            isBusy = false
                            refreshTick++
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.telegram_unlink_action), color = dangerColor) }
            }

            code == null -> {
                Text(stringResource(R.string.telegram_code_failed), style = Typography.bodyMedium, color = dangerColor)
                TextButton(
                    onClick = { if (!isBusy) refreshTick++ },
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.telegram_new_code_action)) }
            }

            else -> {
                val activeCode = code!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.telegram_bot_handle_label), style = Typography.labelMedium, color = onSurfaceVariant)
                    Text("@$TELEGRAM_BOT_USERNAME", style = Typography.labelLarge, fontWeight = FontWeight.Bold, color = BrandTelegram)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfaceContainerLow)
                        .clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(activeCode))
                            android.widget.Toast.makeText(context, copiedToast, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        activeCode,
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        letterSpacing = 3.sp
                    )
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = onSurfaceVariant, modifier = Modifier.size(18.dp))
                }

                Button(
                    onClick = {
                        // ?start=<code> is Telegram's deep-link payload: the bot receives
                        // "/start <code>", which its existing command handler already binds on.
                        val uri = android.net.Uri.parse("https://t.me/$TELEGRAM_BOT_USERNAME?start=$activeCode")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: Exception) {
                            Log.e(TAG_TG, "Telegram deep link failed: ${e.message}")
                            android.widget.Toast.makeText(context, noAppToast, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTelegram, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.telegram_open_bot_action), fontWeight = FontWeight.Bold)
                }

                Text(
                    stringResource(R.string.telegram_manual_hint, activeCode),
                    style = Typography.labelSmall,
                    color = onSurfaceVariant
                )
                Text(
                    stringResource(R.string.telegram_code_expiry_note),
                    style = Typography.labelSmall,
                    color = textTertiary
                )
                TextButton(
                    onClick = {
                        if (isBusy) return@TextButton
                        isBusy = true
                        scope.launch {
                            code = SupabaseRepo.generateTelegramBindingCode()
                            isBusy = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.telegram_new_code_action)) }
            }
        }
    }
}

/**
 * Home entry point for the Telegram bot (mockup shortcut-card shape: 44dp tinted tile,
 * title + subtitle, trailing chevron).
 *
 * A button rather than the section itself, because [TelegramBindingSection] hits the
 * network on first composition (`isTelegramLinked`, and a code mint when unlinked) and
 * Home is the app's most-opened screen — inlining it would add a Supabase round-trip to
 * every launch for a screen most customers finish with once. Opening it in a sheet keeps
 * that call on the tap.
 */
@Composable
fun TelegramBotCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(BrandTelegram.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = BrandTelegram, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.telegram_link_title),
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Text(
                stringResource(R.string.telegram_link_subtitle),
                style = Typography.bodySmall,
                color = onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The binding flow itself, hosted in a bottom sheet so Home stays a button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramBotSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            TelegramBindingSection()
        }
    }
}

/**
 * تنبيه الربط اللي بيظهر أول دخول للرئيسية ([com.example.data.TelegramLinkPrompt]).
 *
 * نفس [TelegramBindingSection] بالظبط — مفيش مسار ربط تاني — بس قبله سطور بتقول العميل
 * هيكسب إيه، لأن "ربط تليجرام" لوحده مابيقولش ليه. كل سطر هنا ميزة بتوصل البوت فعلاً
 * النهاردة (تأكيد الاقتراح البنكي، live_checkin للنواقص، نتايج agent_tasks الاستباقية،
 * تسجيل مصروف برسالة) — متضيفش سطر لميزة لسه مابتوصلش.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLinkPromptSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.telegram_prompt_title),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
                Text(
                    stringResource(R.string.telegram_prompt_subtitle),
                    style = Typography.bodyMedium,
                    color = onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.telegram_prompt_benefit_confirm,
                    R.string.telegram_prompt_benefit_alerts,
                    R.string.telegram_prompt_benefit_forecast,
                    R.string.telegram_prompt_benefit_log,
                ).forEach { benefit ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandTelegram)
                        )
                        Text(stringResource(benefit), style = Typography.bodyMedium, color = onSurface)
                    }
                }
            }
            TelegramBindingSection()
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.telegram_prompt_later), color = onSurfaceVariant)
            }
        }
    }
}

/**
 * كارت مجتمع وقناة تليجرام — squircle card أسفل منتقي الأقسام
 * يفتح القناة/البوت مباشرة بأمان مع معالجة الاستثناءات.
 */
@Composable
fun ZadTelegramCommunityCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/ZadhApp_bot"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w("TelegramBinding", "Failed to open Telegram: ${e.message}")
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(BrandTelegram.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Send, contentDescription = null, tint = BrandTelegram, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.telegram_community_title),
                style = Typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = onSurface
            )
            Text(
                stringResource(R.string.telegram_community_subtitle),
                style = Typography.bodySmall,
                color = onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
