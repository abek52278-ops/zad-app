# زاد (Zad) — مساعد العائلة الذكي

تطبيق أندرويد لإدارة المخزون المنزلي، المصاريف، والعائلة، مدعوم بذكاء اصطناعي (Gemini أساسي، وGroq احتياطي للنصوص) بيتعلم من سلوكك ويتنبأ باحتياجاتك.

**التقنيات:** Kotlin · Jetpack Compose (Material 3) · Supabase (Postgres, Auth, Realtime, Edge Functions) · Room · Gemini (نصوص + رؤية بالصور، مسبح ٥ مفاتيح) مع Groq كاحتياطي للنصوص فقط

## أهم الميزات
- مخزون منزلي بدورة مغلقة: مسح الفواتير/الرف بالكاميرا → يحدّث المخزون → ينزّل النواقص تلقائياً في قائمة التسوق
- تتبع مصاريف من رسائل SMS/إشعارات البنك + ميزانية لكل فئة
- ذكاء زاد: نقاط صحة مالية، عداد "قوة الصرف"، تحليل سلوكيات، مقارنة شهرية، شات بسياق كامل عن حياتك المالية والمنزلية
- عائلة مشتركة: شات، مهام وأهداف للأطفال، بستان تسبيحة، محفظة أطفال
- إشعارات ذكية استباقية + ملخص صباحي يومي

## التشغيل محلياً

**المتطلبات:** [Android Studio](https://developer.android.com/studio) (Narwhal أو أحدث) · JDK 17

1. افتح المشروع في Android Studio ودع IDE يزامن Gradle.
2. أنشئ ملف `.env` في جذر المشروع (انسخ من `.env.example`) وحط فيه:
   - `SUPABASE_URL` و `SUPABASE_ANON_KEY` — **مطلوبين** (من إعدادات مشروعك في Supabase)
   - `GEMINI_API_KEY` — **اختياري**، سيبه زي ما هو. كل ميزات الذكاء الاصطناعي (شات، تحليل، مسح فواتير بالصور) شغالة بمفاتيح مُسجّلة سيرفر-سايد في Supabase Secrets (`ZAD_API_KEY_1..5` لـ Gemini، و`GROQ_API_KEY` كـ fallback للنصوص) — مش هنا. راجع CLAUDE.md لتفاصيل توزيع المزوّدين.
3. شغّل قاعدة البيانات بـ`supabase db push` (أو سيب CI يعملها — الـmigrations بتتنشر من
   `.github/workflows/edge-functions.yml`). **ماتنفّذش الملفات بالإيد في SQL Editor:** أي مسار
   بيسجّل نسخته الخاصة من غير ما يضيف ملف مقابل في الريبو بيكسر `supabase db push` للكل
   بـ"Remote migration versions not found in local migrations directory".
4. شغّل التطبيق على محاكي أو جهاز حقيقي (`minSdk 24`).

## بناء APK بدون Android Studio

كل push على `main` بيشغّل [GitHub Actions](.github/workflows/build-debug-apk.yml) وبيبني نسخة Debug جاهزة للتنزيل من تبويب **Actions** — محتاج بس تضيف secretين في Settings → Secrets and variables → Actions: `SUPABASE_URL` و`SUPABASE_ANON_KEY`. (مفيش `GEMINI_API_KEY` هنا خالص — مش مستخدم).

## البنية
راجع [PROJECT_MAP.md](./PROJECT_MAP.md) لخريطة كاملة للكود، تدفق البيانات، وسجل التغييرات لكل Sprint.
