package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Typography
import com.example.ui.theme.dangerColor
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary

/**
 * حالة فارغة موحّدة — تغطي من ملاحظة سطر واحد جوه لستة (icon = null) لحد شاشة فاضية كاملة (icon محدد).
 * تحل محل نفس نمط Box+Text(color=Gray) اللي كان متكرر في أكتر من شاشة.
 */
@Composable
fun ZadEmptyState(
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    subtitle: String? = null,
    iconTint: Color = primary.copy(alpha = 0.5f),
    iconBackground: Color = primary.copy(alpha = 0.08f),
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = if (icon != null) 0.dp else 16.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = iconTint)
                }
                Spacer(Modifier.height(20.dp))
            }
            Text(
                title,
                style = if (icon != null) Typography.titleMedium else Typography.bodyMedium,
                fontWeight = if (icon != null) FontWeight.Bold else FontWeight.Normal,
                color = if (icon != null) onSurface else onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(subtitle, style = Typography.bodyMedium, color = onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}

@Composable
fun ZadLoadingState(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = primary)
    }
}

/** حالة خطأ موحّدة — retryLabel يترك للـ caller (موارد اللغة بتاعته) بدل ما يتهاردكود هنا. */
@Composable
fun ZadErrorState(
    message: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: ImageVector = Icons.Default.ErrorOutline,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier.size(96.dp).clip(CircleShape).background(dangerColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = dangerColor)
            }
            Spacer(Modifier.height(20.dp))
            Text(message, style = Typography.bodyMedium, color = onSurfaceVariant, textAlign = TextAlign.Center)
            if (onRetry != null && retryLabel != null) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onRetry) {
                    Text(retryLabel)
                }
            }
        }
    }
}
