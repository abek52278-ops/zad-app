package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Showcase for Lucide Icons - مثال عملي لاستخدام Lucide Icons في Zad
 * يمكن استخدام هذا الملف للاختبار وكمرجع
 */

@Composable
fun LucideIconsShowcase() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFF5F5F5))
      .padding(16.dp)
  ) {
    Text(
      text = "🎨 Lucide Icons في Zad",
      style = MaterialTheme.typography.headlineMedium,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    LazyVerticalGrid(
      columns = GridCells.Fixed(3),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item { IconCard("home", LucideIcons.HOME) }
      item { IconCard("wallet", LucideIcons.WALLET) }
      item { IconCard("shopping-cart", LucideIcons.SHOPPING_CART) }
      item { IconCard("trending-up", LucideIcons.TRENDING_UP) }
      item { IconCard("alert-circle", LucideIcons.ALERT_CIRCLE) }
      item { IconCard("check-circle", LucideIcons.CHECK_CIRCLE) }
      item { IconCard("users", LucideIcons.USERS) }
      item { IconCard("settings", LucideIcons.SETTINGS) }
      item { IconCard("plus", LucideIcons.PLUS) }
      item { IconCard("trash", LucideIcons.TRASH_2) }
      item { IconCard("edit", LucideIcons.EDIT) }
      item { IconCard("search", LucideIcons.SEARCH) }
    }
  }
}

@Composable
fun IconCard(label: String, iconName: String) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      LucideIcon(
        icon = iconName,
        size = 40.dp,
        modifier = Modifier.padding(bottom = 8.dp)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2
      )
    }
  }
}

// مثال عملي: Navigation Bar مع Lucide Icons
@Composable
fun LucideNavigationBar() {
  NavigationBar {
    NavigationBarItem(
      selected = true,
      onClick = { },
      icon = { LucideIcon(icon = LucideIcons.HOME, size = 24.dp) },
      label = { Text("الرئيسية") }
    )
    NavigationBarItem(
      selected = false,
      onClick = { },
      icon = { LucideIcon(icon = LucideIcons.SHOPPING_CART, size = 24.dp) },
      label = { Text("التسوق") }
    )
    NavigationBarItem(
      selected = false,
      onClick = { },
      icon = { LucideIcon(icon = LucideIcons.WALLET, size = 24.dp) },
      label = { Text("المحفظة") }
    )
    NavigationBarItem(
      selected = false,
      onClick = { },
      icon = { LucideIcon(icon = LucideIcons.USERS, size = 24.dp) },
      label = { Text("العائلة") }
    )
  }
}

// مثال عملي: Button مع Lucide Icon
@Composable
fun LucideButton(text: String, icon: String) {
  Button(onClick = { }) {
    LucideIcon(icon = icon, size = 20.dp)
    Spacer(modifier = Modifier.width(8.dp))
    Text(text)
  }
}

// مثال عملي: Statistics Card
@Composable
fun StatCard(title: String, value: String, icon: String, trendIcon: String? = null) {
  Card(
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        LucideIcon(icon = icon, size = 32.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(text = title, style = MaterialTheme.typography.labelSmall)
          Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
      }
      if (trendIcon != null) {
        LucideIcon(icon = trendIcon, size = 24.dp)
      }
    }
  }
}
