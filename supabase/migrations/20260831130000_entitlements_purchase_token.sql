-- بند 30.6ب — ربط اشتراك Google Play بالعميل عشان RTDN تشتغل.
--
-- المشكلة: إشعارات Google Play (RTDN) بتعرّف نفسها بـ purchase_token وبس.
-- مفيش أي جدول في القاعدة كان بيخزّن التوكن ده، فالـ webhook مكانش قادر
-- يترجم توكن لعميل حتى لو كل حاجة تانية فيه كانت سليمة. النتيجة: التجديد
-- والإلغاء والاسترجاع من جوجل **مبيوصلوش خالص**.
--
-- (الـ webhook كان كمان بيحدّث zad_subscriptions بأعمدة مش موجودة فيه —
--  status/rtdn_last_event/updated_at/purchase_token، ولا واحد منهم موجود.
--  وأصلاً zad_subscriptions ده جدول اشتراكات العميل الشخصية: نتفليكس وشاهد
--  وأنغامي. مش جدول فوترة التطبيق. الجدول الصح هو zad_entitlements — وهو
--  اللي entitlement.ts بيقرا منه فعلاً.)

alter table public.zad_entitlements
  add column if not exists purchase_token text;

-- unique مش مجرد index: التوكن الواحد لعميل واحد. من غير القيد ده، حد يقدر
-- ياخد توكن شراء صحيح (من جهازه أو من حد تاني) ويستخدمه على أكتر من حساب —
-- وجوجل هتأكده صح في كل مرة لأنه فعلاً توكن مدفوع. القيد ده هو اللي بيخلي
-- verify-purchase يقدر يرفض إعادة الاستخدام بدل ما يكتشفها بعد فوات الأوان.
-- partial عشان الصفوف اللي لسه من غير اشتراك (الأغلبية) متتصادمش على null.
create unique index if not exists zad_entitlements_purchase_token_uniq
  on public.zad_entitlements (purchase_token)
  where purchase_token is not null;

comment on column public.zad_entitlements.purchase_token is
  'توكن اشتراك Google Play. بيتكتب من verify-purchase بعد ما جوجل تأكد الشراء، وzad-billing-webhook بيستخدمه عشان يترجم إشعار RTDN لعميل. unique — التوكن لحساب واحد بس.';
