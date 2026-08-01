package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketPriceItem
import com.example.ui.theme.Typography
import com.example.ui.theme.dangerColor
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.theme.successColor
import com.example.ui.theme.surface
import com.example.ui.theme.textPrimary
import com.example.ui.theme.textTertiary
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

    // The mockup opens Home with a bare row of price pills — no section title, no
    // toolbar band. The refresh control survives as the last pill in the same row,
    // so the live-fetch retry stays reachable without that band coming back.
    Column(modifier = modifier.fillMaxWidth()) {
        if (prices.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(prices) { item -> MarketTickerCard(item) }
                item { RefreshButton(loading = fetchState == ZadViewModel.LiveFetchState.Loading, onClick = onRetry) }
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
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .zadCardShadow(shape, elevation = 6.dp)
            .clip(shape)
            .background(surface)
            .clickable(enabled = !loading) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "تحديث الأسعار",
            tint = onSurfaceVariant,
            modifier = Modifier.size(15.dp).rotate(if (loading) rotation else 0f)
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

/**
 * The mockup's ticker pill: white, fully rounded, one row of item + delta with a
 * soft shadow. Was a 108dp three-line card (name over price over trend) — five of
 * those is a wall of numbers directly above the hero, which is exactly what a
 * single glanceable strip exists to avoid.
 *
 * The price stays in the pill even though the mockup's hardcoded ticker omits it:
 * it is the number the user actually shops on, and dropping real data to match a
 * demo's placeholder would be matching the wrong thing.
 */
@Composable
private fun MarketTickerCard(item: MarketPriceItem) {
    val trendColor = when (item.trend) {
        "up" -> dangerColor
        "down" -> successColor
        else -> textTertiary
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .zadCardShadow(shape, elevation = 6.dp)
            .clip(shape)
            .background(surface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(item.symbol, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1)
        Text("%.1f".format(item.price), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = onSurfaceVariant, maxLines = 1)
        Text(
            "${if (item.changePercent > 0) "+" else ""}${"%.1f".format(item.changePercent)}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = trendColor,
            maxLines = 1
        )
    }
}
