package com.example.data

/**
 * تطبيع مبلغ فلوس لأقرب قرش — بيتطبق عند كل نقطة دخول (parsing نص بنكي، إدخال يدوي،
 * استيراد CSV) قبل ما يوصل لـ Room/Supabase، عشان انجراف binary floating point (زي
 * 19.999999999999996) ميظهرش في العرض أو في مقارنات لاحقة. القرار: الفلوس تفضل Double
 * (مش Long minor units) — انظر MASTER DIRECTIVE §3؛ ده الاحتواء اللي بيخليها آمنة.
 */
fun Double.asMoney(): Double = Math.round(this * 100) / 100.0
