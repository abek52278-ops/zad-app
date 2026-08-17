package com.example.data

/**
 * تحويل اسم أكلة زي ما العميل بيقوله ← استعلام Pexels يرجّع صورة أكل فعلاً.
 *
 * المشكلة اللي الملف ده اتعمل عشانها: الاسم كان بيروح لـPexels زي ما هو. "مطبخ" رجّعت
 * صورة مطبخ فاضي، و"لبن ومية" رجّعت منظر طبيعي — كلمة "مية" لوحدها بحث عن مياه، والنتيجة
 * بحيرة وجبل في كارت المفروض يعرض أكل. البحث ماكانش فاشل، هو نجح في إنه يجيب الحاجة الغلط.
 *
 * تلات طبقات، بالترتيب:
 *
 * ١. **تنضيف الحشو العامي.** "عايز"، "شوية"، "بتاع"، "حلو"، "طبق" — كلمات مش بتوصف أكلة
 *    بس بتغيّر نتيجة البحث. و"و" اللي بتربط صنفين ("لبن ومية") بتقسم الاستعلام، وبناخد
 *    أول صنف معروف بدل ما نبحث عن الاتنين مع بعض ونجيب حاجة تالتة خالص.
 *
 * ٢. **الترجمة للإنجليزي.** Pexels فهمها للعربي ضعيفة — بتعمل مطابقة فضفاضة وبترجّع أي
 *    حاجة بدل ما ترجّع فاضي، وده أسوأ من فاضي لأن الكارت بيبان كأنه شغال. القاموس تحت
 *    مصري/خليجي في الأساس لأن دي أكلات العميل.
 *
 * ٣. **مؤهِّل الأكل.** أي استعلام بيتبعت وجنبه `food` — من غيرها "مطبخ" → `kitchen`
 *    بترجّع تصوير عقارات، و`kitchen food` بترجّع أكل متحضّر.
 *
 * ولو كل ده رجّع مفيش، [fallbackUrl] بترجّع صورة أكل متأكدين منها بدل `null`. التلات
 * روابط اتفتحوا واتشافوا فعلاً (2026-08-16) — مش IDs متولّدة. الاختيار بالهاش مش
 * عشوائي: نفس الأكلة بتاخد نفس البديل كل مرة، فالكارت مابيرقصش بين صورتين مع كل تمرير.
 */
object FoodImageQuery {

    /** كلمات مالهاش أي قيمة بحثية بس بتغيّر النتيجة — بتتشال قبل أي حاجة. */
    private val FILLER = setOf(
        "عايز", "عاوز", "ابغى", "أبغى", "ابي", "أبي", "ممكن", "لو", "سمحت", "من", "فضلك",
        "شوية", "شويه", "كام", "كتير", "حبة", "حبه", "بتاع", "بتاعت", "بتاعة", "على",
        "في", "مع", "عن", "ال", "الي", "اللي", "دي", "ده", "دا", "حلو", "حلوة", "جميل",
        "لذيذ", "لذيذة", "طازة", "طازج", "سريع", "سريعة", "سهل", "سهلة", "بسيط", "بسيطة",
        "اكل", "أكل", "اكلة", "أكلة", "وجبة", "وجبه", "طبق", "طبخة", "طبخه", "وصفة",
        "وصفه", "عشا", "عشاء", "غدا", "غداء", "فطار", "فطور", "النهاردة", "النهارده",
        "بكرة", "بكره", "انهاردة", "يومي", "ليوم", "كده", "كدا", "يا", "زاد", "شيف",
        "a", "an", "the", "some", "recipe", "meal", "dish", "make", "cook", "want",
    )

    /**
     * عربي ← إنجليزي. الطرف الشمال بيتقارن بالاحتواء مش بالتساوي، عشان "فراخ مشوية"
     * تلاقي "فراخ". الترتيب مهم: الأطول الأول، عشان "بطاطس محمرة" ماتتاخدش كـ"بطاطس".
     */
    private val DICTIONARY: List<Pair<String, String>> = listOf(
        // أكلات كاملة
        "كشري" to "koshari egyptian rice lentils",
        "ملوخية" to "molokhia green soup",
        "محشي" to "stuffed vine leaves dolma",
        "مسقعة" to "moussaka eggplant bake",
        "بشاميل" to "bechamel pasta bake",
        "مكرونة" to "pasta",
        "معكرونة" to "pasta",
        "صيادية" to "fish rice",
        "كبسة" to "kabsa rice chicken",
        "مندي" to "mandi lamb rice",
        "برياني" to "biryani rice",
        "مقلوبة" to "maqluba rice",
        "شاورما" to "shawarma wrap",
        "فلافل" to "falafel",
        "طعمية" to "falafel",
        "فول" to "fava beans breakfast",
        "حمص" to "hummus dip",
        "بابا غنوج" to "baba ganoush dip",
        "تبولة" to "tabbouleh salad",
        "فتة" to "fatteh rice bread yogurt",
        "شكشوكة" to "shakshuka eggs tomato",
        "كفتة" to "kofta grilled meat",
        "برجر" to "burger",
        "بيتزا" to "pizza",
        "سندوتش" to "sandwich",
        "ساندويتش" to "sandwich",
        "شوربة" to "soup bowl",
        "حساء" to "soup bowl",
        "سلطة" to "fresh salad",
        "معجنات" to "pastry baked",
        "فطير" to "layered pastry",
        "سمبوسة" to "samosa fried pastry",
        "سمبوسك" to "samosa fried pastry",
        "بانيه" to "breaded chicken cutlet",
        "شيش" to "grilled skewers",
        "مشاوي" to "mixed grill",
        // بروتين
        "فراخ" to "chicken",
        "دجاج" to "chicken",
        "لحمة" to "beef meat",
        "لحم" to "beef meat",
        "بتلو" to "veal",
        "ضاني" to "lamb",
        "خروف" to "lamb",
        "سمك" to "fish",
        "جمبري" to "shrimp",
        "تونة" to "tuna",
        "بيض" to "eggs",
        "كبد" to "liver",
        "كبدة" to "liver",
        "سجق" to "sausage",
        // نشويات
        "رز" to "rice",
        "أرز" to "rice",
        "ارز" to "rice",
        "عيش" to "bread",
        "خبز" to "bread",
        "توست" to "toast bread",
        "بطاطس" to "potatoes",
        "بطاطا" to "sweet potato",
        "عدس" to "lentils",
        "فاصوليا" to "beans",
        "لوبيا" to "black eyed peas",
        "حمام" to "stuffed pigeon",
        // خضار
        "طماطم" to "tomatoes",
        "بندورة" to "tomatoes",
        "بصل" to "onions",
        "ثوم" to "garlic",
        "خيار" to "cucumber",
        "جزر" to "carrots",
        "كوسة" to "zucchini",
        "باذنجان" to "eggplant",
        "بامية" to "okra",
        "سبانخ" to "spinach",
        "فلفل" to "bell peppers",
        "خس" to "lettuce",
        "خضار" to "vegetables",
        "خضروات" to "vegetables",
        // ألبان
        "لبن" to "milk glass",
        "حليب" to "milk glass",
        "زبادي" to "yogurt bowl",
        "روب" to "yogurt bowl",
        "جبنة" to "cheese",
        "جبن" to "cheese",
        "قشطة" to "cream",
        "زبدة" to "butter",
        // فاكهة
        "تفاح" to "apples",
        "موز" to "bananas",
        "برتقال" to "oranges",
        "مانجو" to "mango",
        "عنب" to "grapes",
        "بطيخ" to "watermelon",
        "فراولة" to "strawberries",
        "تمر" to "dates",
        "بلح" to "dates",
        "فواكه" to "fruits",
        "فاكهة" to "fruits",
        // مشروبات وحلويات
        "قهوة" to "coffee cup",
        "شاي" to "tea cup",
        "عصير" to "juice glass",
        "كنافة" to "kunafa dessert",
        "بسبوسة" to "basbousa semolina cake",
        "أرز بلبن" to "rice pudding",
        "مهلبية" to "milk pudding",
        "كيك" to "cake",
        "حلويات" to "dessert",
        "بسكويت" to "biscuits",
        "شوكولاتة" to "chocolate",
        // مصطلحات عامة كانت بترجّع صور غلط
        "مطبخ" to "home cooked meal",
        "مية" to "water glass",
        "ماء" to "water glass",
        "سكر" to "sugar bowl",
        "ملح" to "salt",
        "زيت" to "cooking oil",
        "دقيق" to "flour",
        "توابل" to "spices",
    ).sortedByDescending { it.first.length }

    /**
     * طرق التحضير مش أطباق. "فراخ مشوية" كانت بتطابق "مشوي" الأول (نفس طول "فراخ"،
     * والفرز مستقر) فترجع `grilled food` — بحث عن أي حاجة مشوية بدل بحث عن فراخ. الوصف
     * بيتحط قدام الصنف لما الصنف يتعرف، ومابيبقاش هو الاستعلام لوحده.
     */
    private val COOKING_MODIFIERS: List<Pair<String, String>> = listOf(
        "مشوي" to "grilled", "مشوية" to "grilled",
        "مقلي" to "fried", "مقلية" to "fried",
        "محمر" to "roasted", "محمرة" to "roasted",
        "مسلوق" to "boiled", "مسلوقة" to "boiled",
        "بالفرن" to "baked", "مدخن" to "smoked",
    )

    /**
     * صور أكل حقيقية على CDN بتاع Pexels. الروابط دي **اتفتحت واتشافت** أثناء كتابة
     * الملف (2026-08-16) — مش IDs متولّدة زي اللي حصل في كتالوج الأفلييت وخلى كل لينك
     * منتج يفتح 404. لو واحدة ماتت، الكارت بيرجع للأيقونة زي الأول.
     */
    private val FALLBACKS = listOf(
        "https://images.pexels.com/photos/1640777/pexels-photo-1640777.jpeg?auto=compress&cs=tinysrgb&w=800",
        "https://images.pexels.com/photos/958545/pexels-photo-958545.jpeg?auto=compress&cs=tinysrgb&w=800",
        "https://images.pexels.com/photos/262959/pexels-photo-262959.jpeg?auto=compress&cs=tinysrgb&w=800",
    )

    /**
     * كلمات في `alt` بتاع الصورة بتقول إنها مش أكل. Pexels بترجّع أقرب حاجة عندها بدل
     * ما ترجّع فاضي، فالفلتر ده هو اللي بيمنع الجبل والبحيرة يوصلوا للكارت.
     */
    private val NON_FOOD_MARKERS = listOf(
        "mountain", "landscape", "forest", "beach", "sea", "ocean", "lake", "river",
        "sky", "sunset", "sunrise", "building", "architecture", "car", "street",
        "portrait", "woman", "man", "people", "office", "computer", "laptop", "phone",
        "animal", "dog", "cat", "flower", "tree", "snow", "desert", "waterfall",
    )

    private val FOOD_MARKERS = listOf(
        "food", "meal", "dish", "plate", "bowl", "cook", "kitchen", "recipe", "cuisine",
        "bread", "meat", "chicken", "fish", "rice", "pasta", "salad", "soup", "fruit",
        "vegetable", "dessert", "cake", "cheese", "egg", "milk", "coffee", "tea", "juice",
        "breakfast", "lunch", "dinner", "snack", "delicious", "tasty", "restaurant",
    )

    /**
     * الاستعلام اللي بيتبعت لـPexels فعلاً. بيرجع "" لو مفيش أي كلمة مفيدة فاضلة —
     * والكولر ساعتها بيروح على [fallbackUrl] على طول من غير نداء شبكة.
     */
    fun toSearchTerm(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\p{Punct}\\u060C\\u061B\\u061F]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() !in FILLER }

        if (cleaned.isEmpty()) return ""

        // "لبن ومية" — الواو بتربط صنفين مالهمش صورة مشتركة. أول صنف نعرف نترجمه هو
        // الموضوع، والباقي وصف. من غير الخطوة دي البحث بيجمع الاتنين ويطلع حاجة تالتة.
        val phrase = cleaned.joinToString(" ")
        val modifier = COOKING_MODIFIERS.firstOrNull { phrase.contains(it.first) }?.second
        fun withModifier(term: String) = if (modifier != null) "$modifier $term food" else "$term food"

        val translatedWhole = DICTIONARY.firstOrNull { phrase.contains(it.first) }?.second
        if (translatedWhole != null) return withModifier(translatedWhole)

        val perWord = cleaned.mapNotNull { word ->
            val stripped = word.removePrefix("و").removePrefix("ال")
            DICTIONARY.firstOrNull { stripped.contains(it.first) || word.contains(it.first) }?.second
        }
        if (perWord.isNotEmpty()) return withModifier(perWord.first())

        // طريقة تحضير من غير صنف معروف لسه أحسن من العبارة الخام.
        if (modifier != null) return "$modifier food"

        // مش في القاموس — يبقى إما اسم إنجليزي أصلاً أو أكلة محلية نادرة. المؤهِّل لوحده
        // بيفرق: Pexels بترجّع أكل لأي كلمة مجهولة طالما "food" جنبها.
        return "$phrase food"
    }

    /** بديل ثابت لكل مصطلح — نفس الأكلة نفس الصورة، مش صورة جديدة كل تمرير. */
    fun fallbackUrl(raw: String): String {
        val idx = Math.floorMod(raw.trim().lowercase().hashCode(), FALLBACKS.size)
        return FALLBACKS[idx]
    }

    /**
     * هل الصورة دي أكل؟ `alt` فاضي = منقولش لأ (كتير من صور Pexels مالهاش وصف)، بس
     * وصف فيه "mountain" وملوش أي علامة أكل بيترفض.
     */
    fun looksLikeFood(alt: String?): Boolean {
        val a = alt?.lowercase()?.trim().orEmpty()
        if (a.isEmpty()) return true
        if (FOOD_MARKERS.any { it in a }) return true
        return NON_FOOD_MARKERS.none { it in a }
    }
}
