package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Lucide Icons Helper - تحميل icons احترافية من CDN
 * يدعم 400+ icon عالي الجودة
 *
 * الاستخدام:
 * LucideIcon(name = "home", size = 24.dp)
 * LucideIcon(name = "shopping-cart", tint = Color.Red)
 */

// قائمة الـ icons المشهورة في Zad
object LucideIcons {
  const val HOME = "home"
  const val SHOPPING_CART = "shopping-cart"
  const val WALLET = "wallet"
  const val TRENDING_UP = "trending-up"
  const val ALERT_CIRCLE = "alert-circle"
  const val CHECK_CIRCLE = "check-circle"
  const val X_CIRCLE = "x-circle"
  const val SETTINGS = "settings"
  const val USER = "user"
  const val USERS = "users"
  const val MENU = "menu"
  const val SEARCH = "search"
  const val PLUS = "plus"
  const val MINUS = "minus"
  const val EDIT = "edit"
  const val TRASH_2 = "trash-2"
  const val ARROW_RIGHT = "arrow-right"
  const val ARROW_LEFT = "arrow-left"
  const val CALENDAR = "calendar"
  const val CLOCK = "clock"
  const val BELL = "bell"
  const val FILTER = "filter"
  const val DOWNLOAD = "download"
  const val SHARE_2 = "share-2"
  const val QR_CODE = "qr-code"
  const val PACKAGE = "package"
  const val TAG = "tag"
  const val ZOOMOUT = "zoom-out"
  const val HELP_CIRCLE = "help-circle"
  const val INFO = "info"
  const val MAIL = "mail"
  const val SEND = "send"
  const val REPEAT = "repeat"
  const val PAUSE = "pause"
  const val PLAY = "play"
  const val VOLUME_2 = "volume-2"
  const val EYE = "eye"
  const val EYE_OFF = "eye-off"
  const val LOCK = "lock"
  const val UNLOCK = "unlock"
  const val CREDIT_CARD = "credit-card"
  const val LAYERS = "layers"
  const val GRID = "grid"
  const val LIST = "list"
  const val TRENDING_DOWN = "trending-down"
  const val ACTIVITY = "activity"
  const val TARGET = "target"
  const val AWARD = "award"
  const val SMILE = "smile"
  const val HEART = "heart"
  const val STAR = "star"
  const val SLASH = "slash"
  const val ZAPPED = "zapped"
  const val MOVE = "move"
  const val LOADER = "loader"
  const val CLOUD = "cloud"
  const val DROPLET = "droplet"
  const val WIND = "wind"
  const val UMBRELLA = "umbrella"
  const val SUN = "sun"
  const val MOON = "moon"
  const val NAVIGATION = "navigation"
  const val COMPASS = "compass"
  const val PHONE = "phone"
  const val CAMERA = "camera"
  const val IMAGE = "image"
  const val VIDEO = "video"
  const val MUSIC = "music"
  const val BOOK = "book"
  const val CODE = "code"
  const val TERMINAL = "terminal"
  const val DATABASE = "database"
  const val HARD_DRIVE = "hard-drive"
  const val CPU = "cpu"
  const val COMMAND = "command"
  const val CODESANDBOX = "codesandbox"
}

@Composable
fun LucideIcon(
  name: String,
  modifier: Modifier = Modifier,
  size: Dp = 24.dp,
  tint: Color = Color.Unspecified,
  contentDescription: String? = null
) {
  val iconUrl = "https://cdn.jsdelivr.net/npm/lucide-static@latest/icons/$name.svg"

  AsyncImage(
    model = iconUrl,
    contentDescription = contentDescription ?: name,
    modifier = modifier.size(size),
    contentScale = ContentScale.Fit
  )
}

// Convenience overloads
@Composable
fun LucideIcon(
  icon: String,
  contentDescription: String? = null,
  modifier: Modifier = Modifier
) = LucideIcon(
  name = icon,
  contentDescription = contentDescription,
  modifier = modifier
)

@Composable
fun LucideIcon(
  icon: String,
  size: Int,
  modifier: Modifier = Modifier
) = LucideIcon(
  name = icon,
  size = size.dp,
  modifier = modifier
)
