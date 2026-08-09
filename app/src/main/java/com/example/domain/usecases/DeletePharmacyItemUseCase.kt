package com.example.domain.usecases

import com.example.data.SupabaseRepo

/**
 * W7 — أول usecase في طبقة `domain/usecases` (السبيك Phase 4.5: "no UI action whose
 * logic exists only inside a Compose onClick handler"). قبل كده `PharmacyScreen`'s زرار
 * الحذف بينادي `ZadViewModel.deletePharmacyItem` واللي بينادي `SupabaseRepo` مباشرة —
 * شغال، بس مفيش نقطة واحدة توصف "حذف دواء" كفعل مستقل عن كونه زرار.
 *
 * **حدود الشير الحقيقية**: العميل (Kotlin/Compose) والوكيل (Deno Edge Function) رانتايمين
 * مختلفين — مفيش ملف Kotlin واحد الاتنين بينادوه. اللي بيتشارك فعلياً هو **العقد**: نفس
 * الجدول (`zad_pharmacy_items`)، نفس شرط الملكية (`user_id`)، ونفس معنى النجاح/الفشل.
 * الأداة المقابلة في zad-brain (`delete_pharmacy_item`) بتنفّذ نفس العقد ده على السيرفر.
 * الفرق الوحيد المتعمّد: الكلاينت بيمسح نسخته المحلية في Room كمان (كاش أوفلاين، مالوش
 * معنى عند الوكيل اللي مالوش قاعدة بيانات محلية).
 */
object DeletePharmacyItemUseCase {
    suspend fun invoke(itemId: String) = SupabaseRepo.deletePharmacyItem(itemId)
}
