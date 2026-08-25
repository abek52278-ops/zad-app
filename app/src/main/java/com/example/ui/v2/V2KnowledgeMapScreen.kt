package com.example.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZadV2

@Composable
fun V2KnowledgeMapScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF052E16))) { // aiPlate background
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            val cy = h / 2

            // Draw connection lines
            drawLine(Color.White.copy(alpha = 0.2f), Offset(cx, cy), Offset(cx - 200f, cy - 200f), strokeWidth = 4f)
            drawLine(Color.White.copy(alpha = 0.2f), Offset(cx, cy), Offset(cx + 250f, cy - 100f), strokeWidth = 4f)
            drawLine(Color.White.copy(alpha = 0.2f), Offset(cx, cy), Offset(cx, cy + 300f), strokeWidth = 4f)

            // Draw nodes
            drawCircle(ZadV2.mintGlow, radius = 40f, center = Offset(cx, cy))
            drawCircle(Color(0xFFF4A93B), radius = 25f, center = Offset(cx - 200f, cy - 200f)) // amber
            drawCircle(Color(0xFF2563EB), radius = 30f, center = Offset(cx + 250f, cy - 100f)) // info blue
            drawCircle(Color(0xFFFF8066), radius = 20f, center = Offset(cx, cy + 300f)) // coral
        }

        Text(
            "Zad Knowledge Graph",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ZadV2.mintGlow,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
        )
        
        Text(
            "Visualizing memory and relations.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        )
    }
}

