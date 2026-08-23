package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.background
import com.example.ui.theme.onSurface
import com.example.ui.theme.onSurfaceVariant
import com.example.ui.theme.primary
import com.example.ui.theme.Typography
import com.example.ui.components.AppearOnEntry

// كانت الشاشة دي نص إنجليزي ثابت بالكامل وسط تطبيق عربي أولاً بالكامل — النص نُقل هنا
// عربي مع الحفاظ على نفس المعنى القانوني بالظبط (كل بند مسؤولية/إخلاء طرف اتترجم حرفياً،
// مش تلخيص). ده نص قانوني حقيقي (شروط استخدام)، فمراجعة قانونية بشرية للترجمة قبل الإصدار
// النهائي مستحسنة رغم كده.
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(stringResource(R.string.terms_of_service_menu_title), onBack)

        AppearOnEntry {
        Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {

            Text(stringResource(R.string.auto_termsofservice_93174),
                style = Typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.auto_termsofservice_95498),
                style = Typography.bodyMedium,
                color = onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 1. الموافقة على الشروط
            TermSection(
                title = "١. الموافقة على الشروط",
                body = "بإنشائك حساب أو استخدامك لتطبيق زاد، فإنك توافق على هذه الشروط. لو مش موافق، من فضلك متستخدمش التطبيق."
            )

            // 2. طبيعة الخدمة — إحنا أداة، مش جهة مسؤولة
            TermSection(
                title = "٢. طبيعة الخدمة — إحنا أداة، مش جهة مسؤولة",
                body = "زاد هو أداة تنظيمية ومساعدة شخصية فقط. إحنا بنوفر برنامج بيساعدك تتابع وتنظم وتعرض بياناتك المالية والمنزلية الخاصة بيك. إحنا مش بندير فلوسك، ولا بناخد قرارات نيابةً عنك، ولا بنحتفظ بأموالك، ولا بنعالج مدفوعات، ولا بنعمل كمؤسسة مالية أو مستشار مالي أو أمين استئماني من أي نوع.\n\nكل ميزة في التطبيق — بما فيها إدارة الميزانية، تتبع المصاريف، مسح الفواتير، متابعة المخزون، تتبع الاشتراكات، اكتشاف المعاملات عن طريق الإشعارات، وأي رؤية \"ذكية\" أو \"مبنية على الذكاء الاصطناعي\" — موجودة فقط عشان تساعدك تنظم معلومات إنت أصلاً عندك أو بتنتجها. إحنا مش بنتحقق من دقة المعلومات دي ولا بنضمنها ولا بنتحمل مسؤوليتها، ولا مسؤولية أي إجراء تتخذه بناءً عليها.\n\nإنت، وإنت بس، المسؤول عن:\n- كل قرار مالي بتاخده؛\n- التأكد من دقة أي بيانات معروضة أو محسوبة أو مكتشَفة أو مقترحة من التطبيق؛\n- نتائج اعتمادك على أي ميزة آلية، بما فيها الرؤى أو التوقعات المولّدة بالذكاء الاصطناعي أو الاكتشاف عن طريق الإشعارات؛\n- كل النتائج المتعلقة بميزانيتك أو إنفاقك أو مدخراتك أو إدارة منزلك، سواء كانت معلومات التطبيق دقيقة أو لأ.\n\nإحنا مش بنتحمل أي مسؤولية أو التزام من أي نوع عن نتائج استخدامك للتطبيق. استخدامك لزاد مش بينشئ أي علاقة استشارية أو التزام أمانة أو ضمان لأي نتيجة بينك وبيننا. التطبيق متاح كأداة تنظيمية وتسهيلية بحتة — مسؤولية إزاي بتستخدم المعلومات اللي بيعرضها بالكامل عليك."
            )

            // 3. الخصوصية والبيانات
            TermSection(
                title = "٣. الخصوصية والبيانات",
                body = "بنحترم خصوصيتك. بياناتك مخزنة بشكل آمن. إحنا مش بنبيع بياناتك المالية الشخصية لأي طرف تالت."
            )

            // 4. ميزات الذكاء الاصطناعي من أطراف خارجية — مساعدة فقط، مش مرجعية
            TermSection(
                title = "٤. ميزات الذكاء الاصطناعي من أطراف خارجية — مساعدة فقط، مش مرجعية",
                body = "بعض الميزات (مسح الفواتير، توقعات الإنفاق، اكتشاف الاشتراكات، الرؤى السلوكية، اقتراحات الوجبات، الإدخال الصوتي) بتستخدم خدمات ذكاء اصطناعي من أطراف خارجية لمعالجة البيانات وتوليد الاقتراحات. المخرجات دي متاحة كتسهيل فقط، وميتعاملش معاها أبدًا كأنها دقيقة أو كاملة أو موثّقة أو مرجعية. المحتوى المولّد بالذكاء الاصطناعي ممكن يكون غلط أو قديم أو مش مناسب لحالتك.\n\nإحنا مش مالكين ولا متحكمين في نماذج الذكاء الاصطناعي المستخدمة. إحنا مش مسؤولين عن أي حاجة الذكاء الاصطناعي يولدها أو يقترحها أو يتوقعها أو يفشل يكتشفها. لازم تتأكد بنفسك من أي رقم أو تصنيف أو توصية قبل ما تتصرف بناءً عليها. اعتمادك على أي مخرج من الذكاء الاصطناعي كتوجيه مالي بيكون بالكامل على مسؤوليتك الخاصة."
            )

            // 5. مشاركة العائلة
            TermSection(
                title = "٥. مشاركة العائلة",
                body = "لو دعيت أفراد من عائلتك، إنت المسؤول عن وصولهم لبيانات منزلك."
            )

            // 6. السلوك الممنوع
            TermSection(
                title = "٦. السلوك الممنوع",
                body = "ممنوع تعمل هندسة عكسية أو اختراق أو تستخدم زاد في أي نشاط غير قانوني."
            )

            // 7. إحنا بنوفر الأداة — وإنت المسؤول عن النتيجة
            TermSection(
                title = "٧. إحنا بنوفر الأداة — وإنت المسؤول عن النتيجة",
                body = "إحنا مش بنضمن ولا بنتحمل مسؤولية دقة أي رصيد أو فئة مصروف أو حالة اشتراك أو مستوى مخزون أو تنبيه نقص أو توقع ميزانية معروض في التطبيق. وإحنا مش بنضمن إن الاكتشاف التلقائي للمعاملات هيلتقط كل معاملة، ولا إنه هيكون صح دايمًا لما يلتقطها.\n\nباستخدامك لزاد، إنت مقر بإن:\n- إحنا أداة تنظيم معلومات سلبية، مش طرف فاعل في قراراتك المالية؛\n- أي خسارة مالية أو دفعة فاتت أو إسراف في الإنفاق أو تلف مخزون أو خلاف عائلي أو أي نتيجة سلبية تانية ناتجة عن استخدامك أو اعتمادك على التطبيق هي مسؤوليتك وحدك؛\n- إحنا مش هنتحمل أي مسؤولية، مالية أو غيرها، عن النتايج دي، لأقصى حد يسمح بيه القانون."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
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
