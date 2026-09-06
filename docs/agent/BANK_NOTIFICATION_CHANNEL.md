# قناة الرسائل البنكية — اقرأ ده قبل ما تضيّق أي فلترة على الإشعارات

**تاريخ القياس: 2026-09-06.** الملف ده اتكتب بعد ما تحقيق في `SyncOutbox` كشف حاجة
كانت هتسبب ضرر حقيقي لو حد اشتغل عليها من غير ما يقيس.

## الخلاصة في سطر

قايمة `trackedPackages` الـ170 في `UnifiedBankListener` **مش** تغطية القناة البنكية.
رسايل البنك بتوصل من **حزمة غير متتبَّعة** — تطبيق المراسلة.

## ليه

التطبيق كان بيقرا رسايل البنك عن طريق إذن `RECEIVE_SMS` و`UnifiedSmsReceiver`. الإذن
ده اتشال في `757f41c` (مخاطرة امتثال Play Store، بند A6 في `PRODUCT_PLAN.md` §5)
واتبدل بـ`UnifiedBankListener` اللي بيقرا **إشعار تطبيق المراسلة** بدل بث الـSMS.

النتيجة اللي مش واضحة من قراءة الكود: `com.google.android.apps.messaging` **مش** في
`trackedPackages`، ولا أي تطبيق مراسلة تاني. فرسالة البنك بتعدّي من واحد من مسارين:

1. `isFinancialNotification` — فحص `moneyKeywords` + `SaBankParser.extractAmount`.
2. **الالتقاط الشامل** في `onNotificationPosted` — لما صيغة البنك ماتطابقش الكلمات.
   بسقف `BROAD_CATCH_DAILY_CAP` = 40 إشعار/يوم.

يعني المسار التاني **شبكة الأمان للقناة البنكية الأساسية**، مش مسار هامشي لتطبيقات
مجهولة.

## القياس (كل جدول `zad_notification_ingest_events`، 6 صفوف)

| الحزمة | متتبَّعة؟ | التصنيف | النتيجة | صفوف | بقت معاملة |
|---|---|---|---|---|---|
| `com.google.android.apps.messaging` | **لأ** | ambiguous | logged | 3 | **3** |
| `com.google.android.apps.messaging` | **لأ** | ambiguous | ambiguous | 1 | 0 |
| `com.google.android.apps.photos` | **لأ** | informational_only | ambiguous | 2 | 0 |

**الستة كلهم من حزم غير متتبَّعة.** التلاتة اللي اتسجّلوا فعلًا (`transaction_id`
مش `null`) كلهم من تطبيق المراسلة وكلهم `client_classification = "ambiguous"` — يعني
العميل مقدرش يحسمهم والسيرفر هو اللي عمل الشغل.

**١٠٠٪ من المعاملات البنكية المسجّلة في المشروع جت من مسار الحزم غير المتتبَّعة.**

الهدر المرصود في نفس العينة صفّين من تطبيق الصور (رقم شبه مبلغ في نص إشعار)، وهما
اللي `SaBankParser.shouldSendToBrain` بتمسكهم دلوقتي — شوف `917ac88`.

## القاعدة

**أي فلترة إضافية على الحزم غير المتتبَّعة لازم تتقاس ضد الأرقام دي قبل ما تشحن.**

الفخ إن مسار الحزم غير المتتبَّعة **شكله** قناة ضجيج: تطبيقات مجهولة، سقف يومي،
وتعليق بيتكلم عن "إشعارات زبالة". الأرقام بتقول العكس بالظبط. قاعدة تالتة تضيّق عليه
كانت هتضرب المسار اللي جت منه كل معاملة ناجحة.

## حدود القياس ده — اقرأها قبل ما تستشهد بالأرقام

العينة **6 صفوف من مستخدم واحد**، آخر نشاط 2026-08-29، و**صفر إشعار في آخر 7 أيام**.
ده حساب تطوير مش حركة إنتاج: المعاملات حواليه أرقام مدوّرة و"تصحيح رصيد يدوي" متكرر.

فالأرقام دي **بتثبت اتجاه، مش حجم**. اللي بتثبته إن المسار حي وبيجيب معاملات حقيقية.
اللي **ما بتثبتوش**: نسبة الهدر عند حجم حقيقي، ولا إن سقف الـ40/يوم مناسب. أول ما
تبقى فيه حركة فعلية، أعِد القياس بنفس الاستعلام قبل أي قرار على السقف.

## استعلام إعادة القياس

```sql
select package_name, client_classification, status,
       count(*) as rows,
       count(*) filter (where transaction_id is not null) as produced_transaction,
       count(*) filter (where confirmation_prompt_delivered_at is not null) as prompted_user,
       min(created_at)::date as first_seen, max(created_at)::date as last_seen
from zad_notification_ingest_events
group by package_name, client_classification, status
order by rows desc;
```

## مواضع الكود

- `UnifiedBankListener.trackedPackages` — تحذير مختصر عند القايمة نفسها
- `UnifiedBankListener.onNotificationPosted` — التحذير المقاس عند فرع الالتقاط الشامل
- `SaBankParser.shouldSendToBrain` — القاعدتين اللي بيمسكوا الهدر الحقيقي بدل التضييق
