package com.example.ui.components

import com.example.R
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketPriceItem
import com.example.ui.theme.Typography
import com.example.ui.components.rememberMarqueeFraction
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
    modifier: Modifier = Modifier,
    onContributePrice: (() -> Unit)? = null
) {
    // كانت بتستبدل قايمة فاضية بـ5 أسعار مخترعة، فـ effectivePrices مكانتش تفضى أبدًا —
    // ده كان بيخلي LoadingRow()/RetryRow() تحت (اللي مبنيين صح فعلاً) كود ميت مستحيل
    // يتنفذ، وده بالظبط عكس القصد الموصوف في تعليق الفانكشن فوق (فرّق بين "لسه ما
    // جربناش" و"جرّبنا وما لقيناش" بدل بيانات وهمية).
    val effectivePrices = prices

    Column(modifier = modifier.fillMaxWidth()) {
        if (effectivePrices.isNotEmpty()) {
            val marqueeFraction by rememberMarqueeFraction()
            var copyWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            // translate negative→0..-copyWidth; in RTL Compose mirrors X for us
                            translationX = -marqueeFraction * copyWidthPx
                        }
                        .padding(vertical = 2.dp)
                        .onSizeChanged { copyWidthPx = it.width.toFloat() / 2f },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    effectivePrices.forEach { item -> MarketTickerCard(item) }
                    RefreshButton(loading = fetchState == ZadViewModel.LiveFetchState.Loading, onClick = onRetry)
                    effectivePrices.forEach { item -> MarketTickerCard(item) }
                }
            }
        } else if (fetchState == ZadViewModel.LiveFetchState.Loading) {
            LoadingRow()
        } else {
            RetryRow(onRetry = onRetry)
        }

        // الأسعار التشاركية الحية (price_index) — زر فرعي بس، مش جزء من الشريط
        // الأساسي المحسوب سيرفر-سايد فوق. price_index/PriceReportingScreen بيقرا
        // ويكتب حقيقي أصلاً (§7.1 UI_ARCHITECTURE_SPEC.md) — هنا بس مدخل سريع ليه.
        if (onContributePrice != null) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onContributePrice() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📊", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text("ساهم بسعر", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = primary)
            }
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
        Text(stringResource(R.string.auto_comp_livemarketticker_80931), style = Typography.labelSmall, color = onSurfaceVariant)
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
        Text(stringResource(R.string.auto_comp_livemarketticker_69159),
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
