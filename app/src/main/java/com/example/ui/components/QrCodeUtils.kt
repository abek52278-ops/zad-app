package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val bitmap = remember(content) {
        generateQrBitmap(content)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code for $content",
            modifier = modifier.size(size)
        )
    }
}

fun generateQrBitmap(content: String): Bitmap? {
    if (content.isBlank()) return null
    try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

/**
 * فك تشفير محتوى رمز QR من صورة (Bitmap) باستخدام ZXing
 */
fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decode(binaryBitmap)
        result.text
    } catch (e: Exception) {
        null
    }
}

/**
 * استخراج كود الدعوة النظيف سواء كان المدخل رابطاً zad:// أو https:// أو الكود مباشرة
 */
fun extractInviteCode(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    return try {
        val uri = Uri.parse(trimmed)
        val codeParam = uri.getQueryParameter("code")
        if (!codeParam.isNullOrBlank()) {
            codeParam.trim()
        } else if (trimmed.startsWith("zad://") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val lastSegment = uri.lastPathSegment
            if (!lastSegment.isNullOrBlank() && lastSegment != "invite" && lastSegment != "family") {
                lastSegment.trim()
            } else {
                trimmed
            }
        } else {
            trimmed
        }
    } catch (e: Exception) {
        trimmed
    }
}
