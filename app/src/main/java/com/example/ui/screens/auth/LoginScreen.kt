package com.example.ui.screens.auth
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.findActivity
import com.example.ui.theme.Typography
import com.example.ui.theme.background
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.outline
import com.example.ui.theme.primary
import com.example.ui.components.AppearOnEntry
import com.example.ui.components.pressableScale
import com.example.ui.viewmodels.AuthState
import com.example.ui.viewmodels.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToMain: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            onNavigateToMain()
            viewModel.resetState()
        }
    }

    // The mockup's splash canvas, not flat white — this is the first screen anyone sees,
    // and on `MaterialTheme.colorScheme.background` it shared nothing with the rest of
    // the app. Brand mark + wordmark + slogan below it are the splash's own stack.
    com.example.ui.components.ZadAuthBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        com.example.ui.components.AppearOnEntry {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_carrot_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(60.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "ZAD",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = com.example.ui.theme.primaryLight
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    stringResource(R.string.slogan),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = com.example.ui.theme.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        AppearOnEntry(delayMs = 80) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.auth_login_title),
                        style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    // Language Toggle — كان زرار شكلي (isArabic محلي بيقلب نص من غير أي
                    // تأثير فعلي). دلوقتي موصول بـ LocaleHelper (نفس الآلية اللي
                    // OnboardingScreen بيستخدمها) فيبدّل اتجاه الواجهة RTL/LTR فعلياً.
                    // كود اللغة الحالية نفسها، مش AR/EN بولياني — toggleLanguage بقت
                    // بتلف على تلات لغات، فـ "EN" كانت هتظهر والواجهة تركي.
                    var language by remember { mutableStateOf(com.example.data.LocaleHelper.currentLanguage()) }
                    TextButton(onClick = {
                        com.example.data.LocaleHelper.toggleLanguage(context)
                        language = com.example.data.LocaleHelper.currentLanguage()
                        context.findActivity()?.recreate()
                    }) {
                        Text(language.uppercase(), color = primary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.auth_login_subtitle),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        AppearOnEntry(delayMs = 160) {
            Column {
                Text(stringResource(R.string.email), color = onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    // white fields, not transparent — they now sit on the splash
                    // gradient, where a transparent field has no edge to read against
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = outline
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(stringResource(R.string.password), color = onSurfaceVariant, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    // white fields, not transparent — they now sit on the splash
                    // gradient, where a transparent field has no edge to read against
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = outline
                    ),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = stringResource(R.string.auth_toggle_password_visibility),
                                tint = onSurfaceVariant
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showForgotPasswordDialog = true }) {
                        Text(text = stringResource(R.string.auth_forgot_password), color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = Typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Was a Button whose label color was `colorScheme.onSurface` — near-black text on
        // the deep-green container, which read as a disabled button.
        com.example.ui.components.ZadPrimaryButton(
            text = stringResource(R.string.auth_login_action),
            onClick = { viewModel.signIn(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotBlank(),
            loading = authState is AuthState.Loading
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.auth_no_account), color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = onNavigateToSignUp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(text = stringResource(R.string.auth_register_now), color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text(text = stringResource(R.string.auth_reset_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                    Text(text = stringResource(R.string.auth_reset_body))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(stringResource(R.string.email)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (authState is AuthState.PasswordResetSent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(R.string.auth_reset_sent), color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetPassword(resetEmail)
                }) {
                    Text(stringResource(R.string.send_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showForgotPasswordDialog = false
                    viewModel.resetState()
                }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}
