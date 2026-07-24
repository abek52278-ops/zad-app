package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketPriceItem
import com.example.ui.theme.Typography
import com.example.ui.theme.dangerColor
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.theme.secondary
import com.example.ui.theme.successColor
import com.example.ui.viewmodels.ZadViewModel

/**
 * شريط أسعار زاد الحي — كاروسيل أفقي بأسلوب شاشة بورصة لعرض أسعار سلع أساسية
 * حقيقية (بحث حي، بكاش 12 ساعة على السيرفر). Glassmorphism + إيموجي/مؤشر
 * صعود-هبوط بدل رسم بياني كامل لأن السلع القليلة (2-4) لا تحتاج جرافيك ثقيل.
 *
 * البحث الحي (groq/compound-mini) غير حتمي — أحياناً يلاقي بيانات حقيقية
 * وأحياناً لأ حتى بعد إعادة محاولة داخلية على السيرفر (راجع تعليق
 * fetch_live_market_prices في zad-core-intelligence). عشان كده الشريط هنا
 * لازم يفرّق بين "لسه ما جربناش" (اختفاء تام، مفيش داعي نشغل مساحة) و"جرّبنا
 * وما لقيناش" (صف صغير فيه زر إعادة محاولة — المستخدم قادر يعيد المحاولة
 * بنفسه لما البحث يطلع حظه أحسن)، بدل ما تختفي الميزة تماماً وتحس إنها معطوبة.
 */
@Composable
fun LiveMarketTicker(
    prices: List<MarketPriceItem>,
    fetchState: ZadViewModel.LiveFetchState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // لسه ما اتبعتش أول محاولة (LaunchedEffect(Unit) في HomeScreen لسه ما نفذش) —
    // من غير داعي نظهر أي حاجة أو نلمّح لفشل لم يحدث بعد.
    if (fetchState == ZadViewModel.LiveFetchState.NotFetchedYet && prices.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(secondary))
                Text(
                    "زاد الحي — أسعار اليوم",
                    style = Typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface
                )
            }
            RefreshButton(loading = fetchState == ZadViewModel.LiveFetchState.Loading, onClick = onRetry)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (prices.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(prices) { item -> MarketTickerCard(item) }
            }
        } else if (fetchState == ZadViewModel.LiveFetchState.Loading) {
            LoadingRow()
        } else {
            // Error أو Fetched-لكن-فاضي (بحث حي رجع بلا نتائج حقيقية هالمرة) — نفس المعاملة:
            // إحنا مش عارفين نفرّق من هنا، والمستخدم مش محتاج يعرف الفرق، بس يقدر يعيد المحاولة.
            RetryRow(onRetry = onRetry)
        }
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ticker_refresh_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    IconButton(onClick = onClick, enabled = !loading, modifier = Modifier.size(28.dp)) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "تحديث الأسعار",
            tint = onSurfaceVariant,
            modifier = Modifier.size(16.dp).rotate(if (loading) rotation else 0f)
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = primary)
        Text("جاري جلب الأسعار...", style = Typography.labelSmall, color = onSurfaceVariant)
    }
}

@Composable
private fun RetryRow(onRetry: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(onSurfaceVariant.copy(alpha = 0.06f))
            .clickable { onRetry() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "تعذر جلب الأسعار الحية الآن — جرب تاني",
            style = Typography.labelSmall,
            color = onSurfaceVariant
        )
        Icon(Icons.Default.Refresh, contentDescription = null, tint = primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MarketTickerCard(item: MarketPriceItem) {
    val (trendColor, trendIcon) = when (item.trend) {
        "up" -> dangerColor to Icons.Default.TrendingUp
        "down" -> successColor to Icons.Default.TrendingDown
        else -> onSurfaceVariant to Icons.AutoMirrored.Filled.TrendingFlat
    }
    val revealAlpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(500, easing = FastOutSlowInEasing), label = "ticker_reveal")

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .widthIn(min = 108.dp)
            .alpha(revealAlpha)
            .zadGlassBlur(radius = 12.dp)
            .clip(shape)
            .background(primary.copy(alpha = 0.06f))
            .border(1.dp, primary.copy(alpha = 0.14f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            item.symbol,
            style = Typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%.1f".format(item.price),
                style = Typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = onSurface
            )
            if (item.unit.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(item.unit, style = Typography.labelSmall.copy(fontSize = 9.sp), color = onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(trendIcon, contentDescription = null, tint = trendColor, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                "${if (item.changePercent > 0) "+" else ""}${"%.1f".format(item.changePercent)}%",
                style = Typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = trendColor
            )
        }
    }
}
