package com.example.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.ZadLottieAsset
import com.example.ui.components.pressableScale
import com.example.ui.theme.Typography
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.viewmodels.AuthState
import com.example.ui.viewmodels.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onNavigateToMain: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var termsAgreed by remember { mutableStateOf(false) }
    
    var showTermsDialog by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            showSuccessMessage = true
            delay(1500)
            viewModel.resetState()
            onNavigateToLogin()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        com.example.ui.components.AppearOnEntry {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.ic_carrot_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(60.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))

        AppearOnEntry(delayMs = 80) {
            Column {
                Text(
                    text = "إنشاء حساب",
                    style = Typography.displayMedium.copy(fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "أدخل بياناتك للمتابعة",
                    style = Typography.bodyMedium,
                    color = onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AppearOnEntry(delayMs = 150) {
            Column {
                AuthTextField(
                    label = "اسم المستخدم",
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "محمد أحمد"
                )

                Spacer(modifier = Modifier.height(16.dp))

                AuthTextField(
                    label = "البريد الإلكتروني",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "your.email@gmail.com"
                )

                Spacer(modifier = Modifier.height(16.dp))

                AuthTextField(
                    label = "كلمة المرور",
                    value = password,
                    onValueChange = { password = it },
                    isPassword = !passwordVisible,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = onSurfaceVariant
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Privacy Policy & Terms of Service Checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAgreed,
                        onCheckedChange = { termsAgreed = it },
                        colors = CheckboxDefaults.colors(checkedColor = primary)
                    )
                    Text(text = "I agree to the ", color = onSurfaceVariant, fontSize = 13.sp)
                    TextButton(onClick = { showTermsDialog = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(text = "Terms & Privacy Policy", color = primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showSuccessMessage) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ZadLottieAsset(
                    resId = R.raw.lottie_success_check,
                    iterations = 1,
                    modifier = Modifier.size(72.dp),
                    contentDescription = "تم التسجيل بنجاح"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تم التسجيل بنجاح! جاري التوجيه...",
                    color = primary,
                    style = Typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = Typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        Button(
            onClick = { viewModel.signUp(email, password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .pressableScale(),
            colors = ButtonDefaults.buttonColors(containerColor = primary),
            shape = RoundedCornerShape(50),
            enabled = authState !is AuthState.Loading && email.isNotBlank() && password.isNotBlank() && termsAgreed
        ) {
            if (authState is AuthState.Loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            } else {
                Text(text = "إنشاء حساب", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "لديك حساب بالفعل؟ ", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            TextButton(
                onClick = onNavigateToLogin,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(text = "تسجيل الدخول", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Column {
                    Text(
                        TermsContent.TITLE,
                        style = Typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Version ${TermsContent.VERSION} — ${TermsContent.LAST_UPDATED}",
                        style = Typography.bodySmall,
                        color = onSurfaceVariant
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        TermsContent.FULL_TEXT,
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        termsAgreed = true
                        showTermsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text("Agree & Continue", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    termsAgreed = false
                    showTermsDialog = false
                }) {
                    Text("Decline", color = onSurfaceVariant)
                }
            }
        )
    }
}

