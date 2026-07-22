package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.KidsModePin
import com.example.ui.theme.dangerColor
import com.example.ui.theme.onSurfaceVariant

/**
 * PIN موحّد لدخول/خروج وضع الأطفال. أول استخدام = تحديد PIN جديد (مع تأكيد)،
 * بعد كده = تحقق عادي. الحالة (setup/verify) بتتحدد تلقائياً من [KidsModePin.hasPinSet].
 */
@Composable
fun PinPromptDialog(onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val pinAlreadySet = remember { KidsModePin.hasPinSet(context) }
    var stage by remember { mutableStateOf(if (pinAlreadySet) "verify" else "setup_enter") }
    var pin by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (pin.length < 4) {
            errorMsg = "أدخل 4 أرقام على الأقل"
            return
        }
        when (stage) {
            "verify" -> {
                if (KidsModePin.verifyPin(context, pin)) {
                    onUnlocked()
                } else {
                    errorMsg = "PIN غلط، حاول تاني"
                    pin = ""
                }
            }
            "setup_enter" -> {
                firstEntry = pin
                pin = ""
                errorMsg = null
                stage = "setup_confirm"
            }
            "setup_confirm" -> {
                if (pin == firstEntry) {
                    KidsModePin.setPin(context, pin)
                    onUnlocked()
                } else {
                    errorMsg = "الرقمين مش متطابقين، من الأول"
                    pin = ""
                    firstEntry = ""
                    stage = "setup_enter"
                }
            }
        }
    }

    val title = when (stage) {
        "verify" -> "PIN وضع الأطفال"
        "setup_enter" -> "حدّد PIN للخروج من وضع الأطفال"
        else -> "أكّد الـ PIN"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (stage != "verify") {
                    Text("هتحتاجه في كل مرة تحب تخرج من وضع الأطفال على الجهاز ده", style = androidx.compose.ui.text.TextStyle(color = onSurfaceVariant))
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { new -> pin = new.filter { it.isDigit() }.take(6); errorMsg = null },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorMsg!!, style = androidx.compose.ui.text.TextStyle(color = dangerColor))
                }
            }
        },
        confirmButton = { Button(onClick = { submit() }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
