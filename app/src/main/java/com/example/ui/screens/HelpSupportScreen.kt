package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import com.example.data.ZadAiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.pressableScale

data class SupportMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    // كانت الشاشة دي متعنونة "دعم زاد الذكي" / "متصل الآن" — بتوهم إنها دعم حي وصل لحسابك
    // الفعلي، بس الموديل هنا معندوش أي وصول لبيانات المستخدم الحقيقية (transactions/budget/
    // إلخ)، بس أسئلة عامة عن استخدام التطبيق. العنوان والرسالة الترحيبية بقوا صريحين في كده،
    // ولو حد سأل عن بياناته الفعلية بيتوجّه لشات "عقل زاد" الحقيقي (نفس نمط agent_summary:
    // ممنوع يوهم إنه شايف حاجة مش شايفها).
    var messages by remember { mutableStateOf(listOf(
        SupportMessage(
            "أهلاً! أنا مساعد أسئلة استخدام تطبيق زاد — أقدر أساعدك تفهم أي ميزة أو تحل مشكلة تقنية. لو سؤالك عن بياناتك الشخصية (مصاريفك، رصيدك، اشتراكاتك)، الأفضل تسأل شات \"عقل زاد\" لأنه هو بس اللي شايف حسابك الفعلي.",
            isUser = false
        )
    )) }
    var input by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppearOnEntry {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = "Agent", tint = primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("مساعدة استخدام التطبيق", style = Typography.titleMedium, fontWeight = FontWeight.Bold, color = onSurface)
                Text("أسئلة عامة — مش وصل لحسابك الشخصي", style = Typography.labelSmall, color = onSurfaceVariant)
            }
        }

        HorizontalDivider(color = outlineVariant)

        // Chat List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!msg.isUser) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = "Agent", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                    bottomEnd = if (msg.isUser) 4.dp else 16.dp
                                )
                            )
                            .background(if (msg.isUser) primary else surfaceContainerLow)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.text,
                            style = Typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = if (msg.isUser) Color.White else onSurface
                        )
                    }
                }
            }
            if (isTyping) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Spacer(modifier = Modifier.width(40.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(surfaceContainerLow)
                                .padding(12.dp)
                        ) {
                            Text("جاري الكتابة...", style = Typography.labelMedium, color = onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رسالتك هنا...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = outlineVariant,
                    focusedBorderColor = primary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        val userText = input
                        input = ""
                        messages = messages + SupportMessage(userText, isUser = true)
                        isTyping = true
                        
                        scope.launch {
                            val response = withContext(Dispatchers.IO) {
                                val systemPrompt = """
                                    أنت مساعد أسئلة استخدام تطبيق "زاد ZAD" لإدارة المصاريف العائلية والمخزون.
                                    مهمتك الرد على أسئلة عامة عن استخدام التطبيق وميزاته وحل مشاكل تقنية شائعة فقط.
                                    - التطبيق يحتوي على: إدارة ميزانية، شات عائلي، كاميرا ذكية لقراءة الفواتير، مخزون المنزل، إحصائيات، عقل زاد (المساعد الذكي الشخصي).
                                    - قاعدة إلزامية: معندكش أي وصول لبيانات المستخدم الفعلية (مصاريفه، رصيده، اشتراكاته، مخزونه). لو سأل عن أي حاجة من دي، وضّح إنك مش شايف حسابه، ووجّهه لشات "عقل زاد" اللي شايف بياناته الحقيقية.
                                    - كن مهذباً، محترفاً، ومتعاطفاً.
                                    - أجب باللغة العربية بوضوح وإيجاز.
                                """.trimIndent()
                                ZadAiRepository.callGeminiText(systemPrompt, userText) ?: "عذراً، لم أتمكن من معالجة طلبك حالياً، يرجى المحاولة لاحقاً."
                            }
                            isTyping = false
                            messages = messages + SupportMessage(response, isUser = false)
                        }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(primary)
                    .pressableScale()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
    }
}
