-- تصحيح فوري لميجريشن 20260901180000: إضافة p_family_id بقيمة افتراضية عن طريق
-- create or replace عملت overload جديد بدل ما تستبدل الأصلي (Postgres بيحتاج نفس
-- التوقيع بالظبط عشان REPLACE، مش مجرد نفس الاسم) — اتأكد حي: zad_memory_upsert
-- بقى موجود بنسختين، (uuid,text,text,real) القديمة و(uuid,text,text,real,uuid)
-- الجديدة. القديمة بقت زيادة: أي نداء بـ4 أرجيومنتس بيشتغل صح برضه على الجديدة
-- (p_family_id ليها default null)، فمفيش داعي نسختين، والاتنين ممكن يلخبطوا أي
-- استدعاء PostgREST مستقبلي.
drop function if exists public.zad_memory_upsert(uuid, text, text, real);
