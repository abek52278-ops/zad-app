# Zad — دليل النشر (Updated)

## ملخص الإصلاحات

### 1. توجيه AI عبر Edge Function
- تم تعديل `ZadAiProxyClient.kt` لاستخدام `SupabaseRepo.client.functions.invoke("zad-ai-proxy")` بدلاً من الاتصال المباشر بـ Gemini API
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
npx supabase functions deploy zad-ai-proxy --project-ref YOUR_PROJECT_REF

# ضع مفتاح Gemini API في Supabase Secrets
npx supabase secrets set GEMINI_API_KEY="YOUR_GEMINI_API_KEY" --project-ref YOUR_PROJECT_REF
```

### الخطوة 3: التحقق
```bash
curl -X POST "https://YOUR_PROJECT_REF.functions.supabase.co/zad-ai-proxy" \
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
مفتاح Gemini API موجود الآن **فقط** في Supabase Secrets (سيرفر سايد).
التطبيق لا يضم المفتاح في الـ APK أبداً — كل طلبات AI تذهب عبر Edge Function.
