package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.background
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.theme.Typography

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(background)) {
        SubScreenTopBar("Terms of Service", onBack)
        
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            
            Text(
                "Terms of Service & User Agreement",
                style = Typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Last Updated: July 2026\n\nPlease read these Terms of Service carefully before using ZAD App.",
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 1. Acceptance
            TermSection(
                title = "1. ACCEPTANCE OF TERMS",
                body = "By creating an account or using ZAD, you agree to these Terms. If you do not agree, do not use the App."
            )

            // 2. NATURE OF SERVICE — WE ARE A TOOL, NOT A RESPONSIBLE PARTY
            TermSection(
                title = "2. NATURE OF SERVICE — WE ARE A TOOL, NOT A RESPONSIBLE PARTY",
                body = "ZAD is strictly a personal organizational and assistive tool. We provide software that helps you track, organize, and visualize your own financial and household information. We do not manage your money, make decisions on your behalf, hold your funds, process payments, or act as a financial institution, advisor, or fiduciary of any kind.\n\nEvery feature in the App — including budgeting, expense tracking, receipt scanning, inventory monitoring, subscription tracking, notification-based transaction detection, and any \"smart\" or \"AI-powered\" insight — exists solely to assist you in organizing information you already possess or generate. We do not verify, guarantee, or take responsibility for the accuracy of this information, nor for any action you take based on it.\n\nYOU, AND ONLY YOU, ARE RESPONSIBLE FOR:\n- every financial decision you make;\n- verifying the accuracy of any data displayed, calculated, detected, or suggested by the App;\n- the consequences of relying on any automated feature, including AI-generated insights, predictions, or notification-based detection;\n- all outcomes related to your budget, spending, savings, or household management, whether the App's information was accurate or not.\n\nWe assume no responsibility, obligation, or liability of any kind for the outcomes of your use of the App. Using ZAD does not create any advisory relationship, fiduciary duty, or guarantee of results between you and us. The App is provided purely as a convenience and organizational utility — the responsibility for how you use the information it presents rests entirely with you."
            )

            // 3. Privacy
            TermSection(
                title = "3. PRIVACY & DATA",
                body = "We value your privacy. Your data is stored securely. We do not sell your personal financial data to third parties."
            )

            // 4. THIRD-PARTY AI FEATURES — ASSISTIVE ONLY, NOT AUTHORITATIVE
            TermSection(
                title = "4. THIRD-PARTY AI FEATURES — ASSISTIVE ONLY, NOT AUTHORITATIVE",
                body = "Some features (receipt scanning, spending predictions, subscription detection, behavioral insights, meal suggestions, voice entry) use third-party AI services to process data and generate suggestions. These outputs are provided as a convenience only and are never to be treated as accurate, complete, verified, or authoritative. AI-generated content may be wrong, outdated, or irrelevant to your situation.\n\nWe do not own or control the underlying AI models. We are not responsible for anything the AI generates, suggests, predicts, or fails to detect. You must independently verify any figure, categorization, or recommendation before acting on it. Treating any AI output as financial guidance is done entirely at your own risk and discretion."
            )

            // 5. Family
            TermSection(
                title = "5. FAMILY SHARING",
                body = "If you invite family members, you are responsible for their access to your household data."
            )

            // 6. Prohibited Conduct
            TermSection(
                title = "6. PROHIBITED CONDUCT",
                body = "You may not reverse engineer, hack, or use ZAD for any illegal activities."
            )

            // 7. WE PROVIDE THE TOOL — YOU OWN THE OUTCOME
            TermSection(
                title = "7. WE PROVIDE THE TOOL — YOU OWN THE OUTCOME",
                body = "We do not guarantee, warrant, or take responsibility for the accuracy of any balance, expense category, subscription status, inventory level, low-stock alert, or budget projection shown in the App. We do not guarantee that automatic transaction detection will catch every transaction, or that it will always be correct when it does.\n\nBy using ZAD, you acknowledge that:\n- we are a passive information-organizing tool, not an active party in your financial decisions;\n- any financial loss, missed payment, overspending, spoiled inventory, family disagreement, or other negative outcome resulting from your use of (or reliance on) the App is solely your responsibility;\n- we bear no liability, financial or otherwise, for such outcomes, to the maximum extent permitted by law."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun TermSection(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = Typography.bodyMedium,
            color = onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}
