# تقرير تجربة العميل — دورة السحب/الإيداع والبادجت (2026-08-30)

## الدورة الكاملة تم تتبعها سطراً بسطر وشغالة

### سحب (خصم):
1. UnifiedBankListener يلتقط إشعار البنك (فلترة ضوضاء + فشل/معلق + حصة يومية للـ broad-catch)
2. Parser يستخرج (amount, txn_kind, confidence) — مع offline retry عبر SyncOutbox
3. notification_ingest → zad-brain: dedupe حرفي (sha256) + ضبابي (مبلغ+تاجر خلال 20 د، migration 20260829020000)
4. اقتراح واحد دائم (zad_transaction_proposals) بـ idempotency_key — repost بنص مختلف يعيد استخدام نفس الاقتراح
5. تأكيد من تليجرام أو التطبيق → نفس RPC (zad_resolve_transaction_proposal[_service])
6. RPC: idempotent (posted يرد already_resolved)، يكتب zad_transactions (is_expense=true) → البادجت ينقص فوراً
7. syncData() بعد التأكيد في التطبيق يحدّث الشاشة

### إيداع (زيادة):
- نفس الدورة، لكن income بيتكتب counts_toward_budget=null (بالتصميم — العميل شكوى تاريخية إن الإيداع كان يرفع السقف بصمت)
- العقل يشوفه في income_awaiting_decision وبيسأل: "ده للبيت؟" → allocate_income (counts=true) → السقف يزيد
- counts=false (مدخرات/فلوس حد تاني) → ما يتحسبش أبداً

### الحمايات اليومية المؤكدة:
- تكرار الإشعار: dedupe حرفي + ضبابي + status machine (logged/ignored/rejected) → ما يخصم مرتين
- ضغطتين سريعتين على زر التأكيد: for update lock + already_resolved + _resolvingTransactionProposals guard
- إشعار غامق (مبلغ مش واضح): سؤال حقيقي في رؤى زاد + تليجرام بدل التخمين
- تصنيف خاطئ: العميل يصحح (expense/income/transfer) في التأكيد
- offline: SyncOutbox يعيد الإرسال (WorkManager)
- رفض: status=rejected + never asked again

## اختبارات: 294/294 green (brain 197 + bot 76 + core-intel 21)
## Live: آخر deploy (d64e9fe) success — كل الدوال شغالة

## الملاحظة الوحيدة
- الإيداع ما بيزودش "المتاح" تلقائياً — ده قرار تصميم مؤكد من شكوى العميل نفسه (إيداع 20k مش معناه 20k لمصروف البيت). العقل بيسأل الأول. لو عايزها تلقائية قول.
