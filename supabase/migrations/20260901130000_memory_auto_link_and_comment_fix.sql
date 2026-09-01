-- تعليق zad_memory.embedding لسه بيقول gemini text-embedding-004 — الموديل ده اتأكد
-- حي (٢٠٢٦-٠٩-٠١) إنه بيرجع 404 على المشروع ده، واتغيّر لـgemini-embedding-001
-- (callModel.ts). تصحيح توثيقي بس، العمود نفسه ما اتغيّرش.
comment on column public.zad_memory.embedding is
  '768-d embedding لنص الملاحظة (gemini-embedding-001، مقطوع لـ768 بُعد عبر outputDimensionality — text-embedding-004 القديم بيرجع 404 على المشروع ده). null = لسه متولدش؛ الاسترجاع الدلالي بيتخطاه وبيرجع للكلمات المفتاحية.';
