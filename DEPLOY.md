# Zad — دليل النشر (Updated)

> ⚠️ **تحديث 2026-08-01 — اقرأ ده الأول:**
>
> - الدالة المستخدمة فعلياً اسمها **`zad-core-intelligence`**. `zad-ai-proxy` **اتحذفت من
>   الريبو خالص** — مفيش مجلد ليها تحت `supabase/functions/`. أي أمر تحت في الملف ده بينشرها
>   أو بينده عليها بقى تأريخ، متنفذهوش.
> - المزوّد الأساسي بقى **Gemini** عن طريق pool من ٥ مفاتيح (`ZAD_API_KEY_1..5`) بنداء
>   `generateContent` الأصلي. Groq بقى fallback **للنصوص/JSON بس**.
> - **الصور عمرها ما بتروح لـ Groq.** Groq بيرفض JSON mode على أي طلب فيه صورة (400)، وكل
>   أفعال المسح محتاجة JSON منظم، فمسار الصور عند Gemini لوحده.
> - الموديلات بتتظبط بأسرار المشروع `ZAD_MODEL_ROUTINE` و`ZAD_MODEL_BRAIN`، والاتنين دلوقتي
>   `gemini-3.5-flash`. **السر بيغلب الافتراضي اللي في الكود** — تغيير الافتراضي لوحده مابيعملش
>   حاجة على مشروع منشور.
> - `gemini-2.5-flash` مات: بيرجّع 404 "no longer available to new users" (لسه بيظهر في قائمة
>   الموديلات، بس مابينفعش يتنادى). ده بالظبط اللي كان مكسّر الماسح الذكي.
>
> - المحادثة (تطبيق + بوت تليجرام) بقت بتعدي على **`zad-brain`** عبر `agent_turn`/`agent_confirm`
>   — استدعاء أدوات حقيقي. راجع `docs/agent/AGENT_GAP_ANALYSIS.md`.
>
> باقي الملف تحت لسه فيه خطوات قديمة بأسماء `zad-ai-proxy`/`GEMINI_API_KEY` المفرد — سيبناها
> كتأريخ، بس اتبع قسم "النشر الحالي" اللي بعد ده مباشرة. **الأوامر اللي فيها `zad-ai-proxy`
> هتفشل** لأن الدالة دي مش موجودة.

## ملخص الإصلاحات

### 1. توجيه AI عبر Edge Function
- (تأريخ) `ZadAiProxyClient.kt` كان بينادي `zad-ai-proxy` بدل Gemini مباشرة. الملف والدالة الاتنين مش موجودين دلوقتي
- الـ Edge Function الآن تدعم 11 نوع طلب:
  `receipt_analysis`, `meal_suggestions`, `spending_insights`, `bank_sms_parsing`,
  `subscription_detection`, `grocery_suggestions`, `chat`, `inventory_scan`,
  `recipe_details`, `brain_evaluate`, `ai_text`

### 2. دمج خدمات الأتمتة
- **3 Notification Listeners → 1**: `UnifiedBankListener` (يجمع regex + AI + offline-first)
- **2 SMS Receivers → 1**: `UnifiedSmsReceiver` (يجمع regex + AI + offline-first)

### 3. إصلاح الأذونات
- تم إضافة طلب `RECEIVE_SMS` و `READ_SMS` عند بدء التشغيل

### 4. تحسينات أخرى
- تنظيف رسالة `@Zad` قبل إرسالها للمساعد
- تغيير `PeriodicAnalysisWorker` إلى `Result.failure()` بدلاً من `Result.retry()`
- حذف 5 ملفات خدمات مكررة و 9 ملفات log

---

## خطوات النشر

### الخطوة 1: بناء المشروع
```bash
cd "E:\app zad"
./gradlew clean assembleDebug
```

### الخطوة 2: نشر Edge Function
```bash
# سجل الدخول إلى Supabase
npx supabase login

# اربط مشروعك
npx supabase link --project-ref YOUR_PROJECT_REF

# انشر الدالة
# ⚠️ تأريخ — zad-ai-proxy محذوفة. الأمر الحالي:
npx supabase functions deploy zad-core-intelligence --project-ref YOUR_PROJECT_REF

# ضع مفتاح Gemini API في Supabase Secrets
npx supabase secrets set GEMINI_API_KEY="YOUR_GEMINI_API_KEY" --project-ref YOUR_PROJECT_REF
```

### الخطوة 3: التحقق
```bash
# ⚠️ تأريخ — العنوان ده مش موجود. استبدله بـ zad-core-intelligence
curl -X POST "https://YOUR_PROJECT_REF.functions.supabase.co/zad-core-intelligence" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ANON_KEY" \
  -d '{
    "request_type": "chat",
    "payload": { "message": "مرحبا" }
  }'
```

### الخطوة 4: إعداد Supabase Database
شغّل ملف `database_updates.sql` في SQL Editor في Supabase Dashboard.
تأكد من وجود الجداول: `family_groups`, `family_members`, `chat_messages`,
`shared_grocery_list`, `family_chores`, `family_goals`, `app_notifications`,
`zad_inventory`, `zad_transactions`, `zad_subscriptions`, `zad_shopping_list`,
`zad_users`, `zad_behavior_patterns`.

### الخطوة 5: المتغيرات البيئية للتطبيق
تأكد من وجود ملف `.env` في جذر المشروع بالمحتوى:
```
GEMINI_API_KEY=YOUR_GEMINI_API_KEY
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_ANON_KEY
```

---

## النشر الحالي (2026-08-01)

### الأسرار المطلوبة على Supabase

```bash
export SUPABASE_ACCESS_TOKEN="sbp_..."      # من dashboard → Account → Access Tokens

# Gemini — الـ pool الأساسي، نفس الأسماء اللي zad-brain وzad-core-intelligence بيقروها
npx supabase secrets set \
  ZAD_API_KEY_1="..." ZAD_API_KEY_2="..." ZAD_API_KEY_3="..." \
  ZAD_API_KEY_4="..." ZAD_API_KEY_5="..." \
  --project-ref YOUR_PROJECT_REF

# الموديلات (لازم تكون موديلات المفاتيح دي بتقدر تناديها فعلاً)
npx supabase secrets set \
  ZAD_MODEL_ROUTINE="gemini-3.5-flash" ZAD_MODEL_BRAIN="gemini-3.5-flash" \
  --project-ref YOUR_PROJECT_REF

# Groq — fallback نصوص/JSON بس، زائد Whisper والبحث الحي (المفتاح المفرد)
npx supabase secrets set \
  GROQ_API_KEY_1="..." GROQ_API_KEY_2="..." GROQ_API_KEY="..." \
  --project-ref YOUR_PROJECT_REF
```

`GEMINI_API_KEY` المفرد بقى fallback قديم بس — بيتقرا لو مفيش أي مفتاح من `ZAD_API_KEY_1..5`.
لو مفيش ولا واحد فيهم، مفيش مزوّد للرؤية أصلاً، والدالة بتسجّل الخطأ بصوت عالي بدل ما ترجّع
الصور لـ Groq (لأن Groq مابيقدرش يخدمها).

### نشر الدوال

```bash
npx supabase functions deploy zad-core-intelligence --project-ref YOUR_PROJECT_REF
npx supabase functions deploy zad-brain            --project-ref YOUR_PROJECT_REF
```

### التحقق (نداء حي، مش تخمين)

```bash
# مسار النص
curl -s -X POST "https://YOUR_PROJECT_REF.supabase.co/functions/v1/zad-core-intelligence" \
  -H "Authorization: Bearer YOUR_ANON_KEY" -H "Content-Type: application/json" \
  -d '{"action":"estimate_price","payload":{"item_name":"حليب 1 لتر","store":"بنده"}}'
# المتوقع: أسعار حقيقية، مش أصفار

# مسار الرؤية (صورة فاتورة base64)
curl -s -X POST "https://YOUR_PROJECT_REF.supabase.co/functions/v1/zad-core-intelligence" \
  -H "Authorization: Bearer YOUR_ANON_KEY" -H "Content-Type: application/json" \
  -d '{"action":"analyze_receipt","payload":{"image_base64":"...","mime_type":"image/jpeg"}}'
# المتوقع: total واسم المتجر والأصناف. لو رجعت {"total":0,...,"items":[]} يبقى الرؤية واقعة.
```

**الفخ الأهم:** الدالة بترجّع `200` مع نتيجة فاضية حتى لما المزوّد يفشل — عشان كده لازم تبص على
القيم نفسها، مش على status code. ولو Gemini رجّع 200 من غير نص (finishReason = MAX_TOKENS
لما الموديل يصرف الميزانية كلها "تفكير"، أو SAFETY) الدالة بتسجّل `finishReason` و`usageMetadata`
في اللوج.

### تنبيه: المنشور ممكن يختلف عن الريبو
حصل فعلاً مرتين (`zad-core-intelligence` v89 و`zad-brain` v64). قبل ما تفترض إن أي نسخة هي
الصح، قارن:

```bash
# عن طريق MCP: mcp__supabase__get_edge_function ثم diff مع supabase/functions/<name>/index.ts
```

---

## هيكل الخدمات بعد الإصلاح

```
قبل (مشاكل):
├── BankNotificationListener.kt ❌ (غير مسجل في Manifest)
├── ZadNotificationListenerService.kt ✅ (مسجل)
├── ZadNotificationService.kt ❌ (غير مسجل، لكن فيه أفضل منطق)
├── SmsReceiver.kt ❌ (غير مسجل، لكن فيه أفضل منطق)
├── ZadSmsReceiver.kt ✅ (مسجل)

بعد (منظم):
├── UnifiedBankListener.kt ✅ (يجمع regex + AI + offline-first من الثلاثة)
├── UnifiedSmsReceiver.kt ✅ (يجمع best logic من الاثنين)
```

## ملاحظة
مفاتيح Gemini موجودة **فقط** في Supabase Secrets (سيرفر سايد). التطبيق مابيحطّش أي مفتاح
في الـ APK — كل طلبات AI بتروح عبر Edge Function.

الاستثناء الوحيد: المستخدم يقدر يلصق مفتاح Gemini بتاعه في شاشة الكاميرا (`ZadAiGeminiClient`)،
وساعتها المفتاح بيتخزن على جهازه هو بس في SharedPreferences. المسار ده اختياري بالكامل، وبيقبل
أكتر من مفتاح مفصولين بفاصلة مع دوران عند الـ 429، ولو فشل بيرجع تلقائي لـ
`zad-core-intelligence`.
