package com.example.ui.components

import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * لوحة «شركة إدارة المنزل الحية» — كل سطر رقم حقيقي من الداتابيز:
 * - وكيل المال: عدد المعاملات اللي بيراقبها (zad_transactions آخر 30 يوم)
 * - وكيل المخزون: عدد المنتجات المتتبعة (zad_inventory)
 * - وكيل الصيدلية: جرعات النهاردة المجدولة (zad_pharmacy_items × dose_times)
 * - محلل الاستهلاك: عمر آخر تحليل (آخر observation)
 *
 * مبدأ المشروع: أرقام من المصدر فقط — لو مفيش داتا، السطر يقول "لسه مفيش"
 * بدل أي رقم مخترع. ده اللي بيخلي اللوحة "حية" فعلاً مش ديكور.
 */
data class AgentLiveStat(
    val agentName: String,
    val detail: String,
    val isBusy: Boolean, // true = شغال حالياً/حدث حديثاً، false = خامل
)

@Composable
fun NeuralMeshLivePanel(
    stats: List<AgentLiveStat>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00BFA6))
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.auto_comp_neuralmeshlivepanel_65281),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(10.dp))
        stats.forEach { stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (stat.isBusy) Color(0xFF00BFA6)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stat.agentName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                }
                Text(
                    stat.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}
