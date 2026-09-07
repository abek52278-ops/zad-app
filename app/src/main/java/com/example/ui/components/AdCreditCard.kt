package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.Typography
import com.example.ui.theme.onPrimary
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.outlineVariant
import com.example.ui.theme.primary
import com.example.ui.theme.secondary
import com.example.ui.theme.surface

/**
 * كارت شحن الرصيد بمشاهدة إعلان.
 *
 * بيظهر **لما الرصيد يقرب يخلص بس** — مش دايماً. كارت دائم بيطلب مشاهدة إعلانات
 * على شاشة العميل الرئيسية بيتقري إلحاح، والإلحاح بيتجاهَل. الظهور عند الحاجة
 * بيخلّيه عرض مفيد في لحظته.
 *
 * [nextReward] بييجي من `zad_ad_reward_grant` وبيتغيّر مع كل مشاهدة (٣ ثم ٤ ثم ٥…).
 * عرضه صراحة هو اللي بيخلّي التصاعد شغّال كحافز: العميل لازم يشوف إن المشاهدة
 * الجاية أحسن من اللي فاتت، وإلا التصاعد مجرد رقم في الداتابيز.
 */
@Composable
fun AdCreditCard(
    chatLeft: Int,
    nextReward: Int,
    adsToday: Int,
    dailyCap: Int,
    isAdReady: Boolean,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capReached = adsToday >= dailyCap
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(secondary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text("⚡", style = Typography.titleMedium) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    stringResource(R.string.ad_credit_title),
                    style = Typography.titleSmall,
                    color = onSurface,
                )
                Text(
                    // الرصيد المتبقي بالرقم — "قليل" مش معلومة يتصرف عليها.
                    stringResource(R.string.ad_credit_left, chatLeft),
                    style = Typography.bodySmall,
                    color = onSurfaceVariant,
                )
            }
        }

        if (capReached) {
            // السقف اليومي مش عقوبة — بيحمي حساب AdMob من تصنيف الحركة كغير صالحة.
            // بنقولها كحقيقة محايدة بدل ما نسيب الزر مطفي بلا سبب.
            Text(
                stringResource(R.string.ad_credit_daily_cap, dailyCap),
                style = Typography.bodySmall,
                color = onSurfaceVariant,
            )
        } else {
            Button(
                onClick = onWatch,
                enabled = isAdReady,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ad_credit_watch, nextReward),
                    style = Typography.labelLarge,
                )
            }
            Text(
                stringResource(R.string.ad_credit_progress, adsToday, dailyCap),
                style = Typography.labelSmall,
                color = outlineVariant,
            )
        }
    }
}
