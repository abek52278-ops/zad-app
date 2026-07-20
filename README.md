# زاد (Zad) — مساعد العائلة الذكي

تطبيق أندرويد لإدارة المخزون المنزلي، المصاريف، والعائلة، مدعوم بذكاء اصطناعي (Groq / Llama) بيتعلم من سلوكك ويتنبأ باحتياجاتك.

**التقنيات:** Kotlin · Jetpack Compose (Material 3) · Supabase (Postgres, Auth, Realtime, Edge Functions) · Room · Groq (Llama 3.3 70B + رؤية بالصور)

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
   - `GEMINI_API_KEY` — **اختياري**، سيبه زي ما هو. كل ميزات الذكاء الاصطناعي (شات، تحليل، مسح فواتير بالصور) شغالة بمفتاح `GROQ_API_KEY` واحد مُسجّل سيرفر-سايد في Supabase Secrets (Edge Function `zad-ai-proxy`)، مش هنا.
3. شغّل قاعدة البيانات: نفّذ ملفات `supabase/migrations/*.sql` بالترتيب في Supabase SQL Editor.
4. شغّل التطبيق على محاكي أو جهاز حقيقي (`minSdk 24`).

## بناء APK بدون Android Studio

كل push على `main` بيشغّل [GitHub Actions](.github/workflows/build-debug-apk.yml) وبيبني نسخة Debug جاهزة للتنزيل من تبويب **Actions** — محتاج بس تضيف secretين في Settings → Secrets and variables → Actions: `SUPABASE_URL` و`SUPABASE_ANON_KEY`. (مفيش `GEMINI_API_KEY` هنا خالص — مش مستخدم).

## البنية
راجع [PROJECT_MAP.md](./PROJECT_MAP.md) لخريطة كاملة للكود، تدفق البيانات، وسجل التغييرات لكل Sprint.
