package com.example.ui.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AffiliateProduct
import com.example.data.CurrencyFormatter
import com.example.ui.components.pressableScale
import com.example.ui.components.zadCardShadow
import com.example.ui.theme.*

/**
 * The mockup's Amazon rail card: a fixed 140dp-wide, 16dp-radius tile — 80dp image,
 * product name, then price + "أمازون" badge on one baseline.
 *
 * `AffiliateProductCard` below is the full-width *list* card (Shopping screen). It was
 * also being used inside Home's `LazyRow`, where its `fillMaxWidth()` made every card
 * expand to the viewport width and its own `padding(horizontal = 16.dp)` added margins
 * on top of the row's spacing — cards ran off the edge and visually collided. The two
 * layouts are genuinely different shapes in the design, so they're two composables now
 * rather than one with a flag.
 */
@Composable
fun ZadAmazonDealCard(
    product: AffiliateProduct,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(140.dp)
            .zadCardShadow(shape)
            .clip(shape)
            .background(surface)
            .pressableScale(pressedScale = 0.97f, withHaptic = false)
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.productNameAr,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            product.productNameAr,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (product.averagePriceSar > 0)
                    CurrencyFormatter.format(LocalContext.current, product.averagePriceSar)
                else "—",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = secondaryDark
            )
            Text(
                "أمازون",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = textTertiary
            )
        }
    }
}

@Composable
fun AffiliateProductCard(
    product: AffiliateProduct,
    onBuyClick: () -> Unit,
    isLoading: Boolean = false
) {
    com.example.ui.components.ZadListCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pressableScale(pressedScale = 0.97f, withHaptic = false)
            .clickable { onBuyClick() },
        shape = RoundedCornerShape(24.dp),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surfaceContainerLow)
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.productNameAr,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 10.dp),
                    color = Color(0xFFFF9900),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "أمازون",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.productNameAr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (product.averagePriceSar > 0) {
                    val context = LocalContext.current
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = primary)
                        Text(
                            CurrencyFormatter.format(context, product.averagePriceSar),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = primary
                        )
                    }
                }
            }

            BuyButton(onClick = onBuyClick)
        }
    }
}

@Composable
fun BuyButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .pressableScale(pressedScale = 0.93f, withHaptic = false),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF9900) // Amazon orange
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("اشترِ من أمازون", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun AffiliateEmptyState(searchedTerm: String) {
    // The curated catalog is a handful of products, so most searched items never
    // match one — this used to be a dead end with no button at all, so every
    // unmatched purchase lost the affiliate commission entirely. Fall back to a
    // tagged Amazon search link (same AFFILIATE_TAG as matched products) so the
    // commission still tracks even without a curated match.
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "لسه بنجهز ترشيحات لـ \"$searchedTerm\"",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "قريباً هتلاقي منتجات مقترحة من أمازون",
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = {
            com.example.data.AffiliateHelper.open(
                context,
                com.example.data.AffiliateHelper.productUrl(asin = null, fallbackSearchTerm = searchedTerm)
            )
        }) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("ابحث في أمازون")
        }
    }
}

@Composable
fun AffiliateLoadingSkeleton() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.6f),
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.6f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset(translateAnim - 200, 0f),
        end = androidx.compose.ui.geometry.Offset(translateAnim, 0f)
    )

    com.example.ui.components.ZadListCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(brush)
            )
        }
    }
}


@Composable
fun AffiliateConsentBanner(onAccept: () -> Unit) {
    com.example.ui.components.ZadListCard(
        modifier = Modifier.padding(16.dp),
        containerColor = primaryContainer,
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(32.dp), tint = primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "ترشيحات الشراء من أمازون",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "زاد بيقترح عليك منتجات من أمازون تناسب احتياجاتك. قد نحصل على عمولة من المشتريات.",
                style = MaterialTheme.typography.bodySmall,
                color = onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تفعيل الترشيحات")
            }
        }
    }
}
