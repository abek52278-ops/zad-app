# دليل تشغيل المرحلة الجديدة — خطوات ما بعد الـ push

## 1️⃣ تطبيق الـ migration على Supabase (إجباري قبل النشر)

الكود بيتوقع عمود `specialist` في `zad_brain_runs`. لو مش موجود، كل agent_turn هيفشل.

**الطريقة الأسهل (SQL Editor في الـ dashboard):**

افتح Supabase Dashboard → SQL Editor وشغّل:

```sql
alter table public.zad_brain_runs
  add column if not exists specialist text;

do $$
begin
  if exists (
    select 1 from pg_constraint where conname = 'zad_brain_runs_specialist_check'
  ) then
    return;
  end if;
  alter table public.zad_brain_runs
    add constraint zad_brain_runs_specialist_check
    check (specialist in ('finance', 'pantry', 'pharmacy', 'family'));
end $$;
```

**أو بالـ CLI:**

```bash
npm i -g supabase        # لو مش متسطب
supabase login           # هيفتح المتصفح
supabase db push         # هيطبق كل الميجريشنز غير المطبقة
```

**أو انشر الـ Edge Function المعدّل (zad-brain):**

```bash
supabase functions deploy zad-brain
```

## 2️⃣ تدوير مفتاح ElevenLabs (إجباري — الأمان)

المفتاح القديم مكشوف في git history.

1. روح [elevenlabs.io](https://elevenlabs.io) → Profile → API Keys → **Rotate**
2. حدّث السيرفر:
   ```bash
   supabase secrets set ELEVENLABS_API_KEY=المفتاح_الجديد
   ```
3. اختبر: ابعت أي رسالة صوتية من التطبيق — لازم تسمع صوت سارة البشري.
   - لو رجعت صامتة: شيك `supabase functions logs zad-core-intelligence`

## 3️⃣ الرفع على GitHub

```bash
git push origin main
```

CI هيبني الـ APK تلقائيًا (`.github/workflows/build-debug-apk.yml`).

## 4️⃣ قائمة اختبار سريعة على الجهاز

| # | الاختبار | المتوقع |
|---|----------|---------|
| 1 | افتح التطبيق | إشعار هادئ "زاد يستمع" في الشريط |
| 2 | قول "يا زاد" | شاشة الصوت تفتح فورًا بصوت نجاح |
| 3 | اسأل سؤال صوتي | رد **بصوت بشري** (مش روبوت) — واضح بدون تشويش |
| 4 | شغّل موسيقى واطلب من زاد يتكلم | الموسيقى تهدى وزاد يتكلم فوقها (Audio Focus) |
| 5 | قول "سجل مصروف ٥٠ جنيه بنزين" | اقتراح + كارت تأكيد في شاشة الصوت |
| 6 | رد "أيوه" صوتي | التأكيد يشتغل ويظهر إيصال "اتسجل ✅" |
| 7 | من تليجرام ابعت "سجل مصروف ٣٠" ثم رد "أيوه" | يتأكد بالنص بدون زر |
| 8 | الإعدادات → تنبيهات المساعد → اقفل "يا زاد" | الخدمة توقف تمامًا (شيك البطارية بعد ساعة) |
| 9 | افتح شاشة الصوت والخدمة مفعلة | مفيش خطأ مايك (الخدمة paused) |
| 10 | اقفل شاشة الصوت | الخدمة ترجع تستمع |

## 5️⃣ لو الصوت طلع صامت

1. شيك المفتاح: `supabase secrets list`
2. شيك اللوجات: `supabase functions logs zad-core-intelligence`
3. شيك إن الجهاز متصل والجلسة مسجلة (المحرك محتاج auth)
4. الرسالة النصية هتفضل شغالة عادي — الصوت مش هيكسر التجربة
