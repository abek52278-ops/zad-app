// Food-image search terms, shared shape with the app's FoodImageQuery.kt.
//
// Split out of index.ts purely so it is testable: index.ts calls Deno.serve at module
// scope, so importing it from a test boots a server.

/**
 * نفس منطق [FoodImageQuery] بتاع التطبيق، على السيرفر — المسار ده هو اللي بيشتغل لما
 * الـAPK يتشحن من غير مفتاح Pexels، وهو الافتراضي. لو الاتنين مااتفقوش، نفس الأكلة
 * بتطلع صورتين مختلفتين حسب إذا كان فيه مفتاح مجمّع ولا لأ.
 */
const FOOD_FILLER = new Set([
  "عايز", "عاوز", "ممكن", "شوية", "شويه", "بتاع", "بتاعة", "على", "في", "مع", "من",
  "اللي", "دي", "ده", "حلو", "حلوة", "لذيذ", "لذيذة", "طازة", "سريع", "سهل", "بسيط",
  "اكل", "أكل", "اكلة", "أكلة", "وجبة", "وجبه", "طبق", "طبخة", "وصفة", "وصفه",
  "عشاء", "غداء", "فطار", "فطور", "النهاردة", "بكرة", "كده", "يا",
  "a", "an", "the", "some", "recipe", "meal", "dish", "cook", "make",
]);

const FOOD_DICTIONARY = ([
  ["كشري", "koshari egyptian rice lentils"], ["ملوخية", "molokhia green soup"],
  ["محشي", "stuffed vine leaves dolma"], ["مسقعة", "moussaka eggplant bake"],
  ["بشاميل", "bechamel pasta bake"], ["مكرونة", "pasta"], ["معكرونة", "pasta"],
  ["كبسة", "kabsa rice chicken"], ["مندي", "mandi lamb rice"], ["برياني", "biryani rice"],
  ["مقلوبة", "maqluba rice"], ["شاورما", "shawarma wrap"], ["فلافل", "falafel"],
  ["طعمية", "falafel"], ["فول", "fava beans breakfast"], ["حمص", "hummus dip"],
  ["تبولة", "tabbouleh salad"], ["فتة", "fatteh rice bread yogurt"],
  ["شكشوكة", "shakshuka eggs tomato"], ["كفتة", "kofta grilled meat"],
  ["برجر", "burger"], ["بيتزا", "pizza"], ["سندوتش", "sandwich"], ["ساندويتش", "sandwich"],
  ["شوربة", "soup bowl"], ["سلطة", "fresh salad"], ["فطير", "layered pastry"],
  ["سمبوسة", "samosa fried pastry"], ["بانيه", "breaded chicken cutlet"],
  ["مشاوي", "mixed grill"],
  ["فراخ", "chicken"], ["دجاج", "chicken"], ["لحمة", "beef meat"], ["لحم", "beef meat"],
  ["ضاني", "lamb"], ["خروف", "lamb"], ["سمك", "fish"], ["جمبري", "shrimp"],
  ["تونة", "tuna"], ["بيض", "eggs"], ["كبدة", "liver"], ["سجق", "sausage"],
  ["رز", "rice"], ["أرز", "rice"], ["ارز", "rice"], ["عيش", "bread"], ["خبز", "bread"],
  ["بطاطس", "potatoes"], ["بطاطا", "sweet potato"], ["عدس", "lentils"],
  ["فاصوليا", "beans"], ["لوبيا", "black eyed peas"], ["حمام", "stuffed pigeon"],
  ["طماطم", "tomatoes"], ["بندورة", "tomatoes"], ["بصل", "onions"], ["ثوم", "garlic"],
  ["خيار", "cucumber"], ["جزر", "carrots"], ["كوسة", "zucchini"], ["باذنجان", "eggplant"],
  ["بامية", "okra"], ["سبانخ", "spinach"], ["فلفل", "bell peppers"], ["خس", "lettuce"],
  ["خضار", "vegetables"], ["خضروات", "vegetables"],
  ["لبن", "milk glass"], ["حليب", "milk glass"], ["زبادي", "yogurt bowl"],
  ["جبنة", "cheese"], ["جبن", "cheese"], ["قشطة", "cream"], ["زبدة", "butter"],
  ["تفاح", "apples"], ["موز", "bananas"], ["برتقال", "oranges"], ["مانجو", "mango"],
  ["عنب", "grapes"], ["بطيخ", "watermelon"], ["فراولة", "strawberries"],
  ["تمر", "dates"], ["بلح", "dates"], ["فواكه", "fruits"], ["فاكهة", "fruits"],
  ["قهوة", "coffee cup"], ["شاي", "tea cup"], ["عصير", "juice glass"],
  ["كنافة", "kunafa dessert"], ["بسبوسة", "basbousa semolina cake"],
  ["مهلبية", "milk pudding"], ["كيك", "cake"], ["حلويات", "dessert"],
  ["بسكويت", "biscuits"], ["شوكولاتة", "chocolate"],
  ["مطبخ", "home cooked meal"], ["مية", "water glass"], ["ماء", "water glass"],
  ["سكر", "sugar bowl"], ["ملح", "salt"], ["زيت", "cooking oil"], ["دقيق", "flour"],
  ["توابل", "spices"],
] as Array<[string, string]>).sort((a, b) => b[0].length - a[0].length);

/**
 * طرق التحضير مش أطباق. "فراخ مشوية" كانت بتطابق "مشوي" الأول (نفس طول "فراخ"، وترتيب
 * الفرز مستقر) فترجع `grilled food` — بحث عن أي حاجة مشوية بدل بحث عن فراخ. الوصف
 * بيتحط قدام الصنف لما الصنف نفسه يتعرف، ومابيبقاش هو الاستعلام لوحده.
 */
const COOKING_MODIFIERS: Array<[string, string]> = [
  ["مشوي", "grilled"], ["مشوية", "grilled"], ["مقلي", "fried"], ["مقلية", "fried"],
  ["محمر", "roasted"], ["محمرة", "roasted"], ["مسلوق", "boiled"], ["مسلوقة", "boiled"],
  ["بانيه", "breaded"], ["بالفرن", "baked"], ["مدخن", "smoked"],
];

function modifierFor(phrase: string): string | null {
  const hit = COOKING_MODIFIERS.find(([ar]) => phrase.includes(ar));
  return hit ? hit[1] : null;
}

const NON_FOOD_MARKERS = [
  "mountain", "landscape", "forest", "beach", "sea", "ocean", "lake", "river", "sky",
  "sunset", "sunrise", "building", "architecture", "car", "street", "portrait", "woman",
  "man", "people", "office", "computer", "laptop", "phone", "animal", "dog", "cat",
  "flower", "tree", "snow", "desert", "waterfall",
];
const FOOD_MARKERS = [
  "food", "meal", "dish", "plate", "bowl", "cook", "kitchen", "recipe", "cuisine",
  "bread", "meat", "chicken", "fish", "rice", "pasta", "salad", "soup", "fruit",
  "vegetable", "dessert", "cake", "cheese", "egg", "milk", "coffee", "tea", "juice",
  "breakfast", "lunch", "dinner", "snack", "delicious", "tasty", "restaurant",
];

/** روابط أكل حقيقية على CDN بتاع Pexels — اتفتحت واتشافت 2026-08-16، مش IDs متولّدة. */
const FOOD_FALLBACKS = [
  "https://images.pexels.com/photos/1640777/pexels-photo-1640777.jpeg?auto=compress&cs=tinysrgb&w=800",
  "https://images.pexels.com/photos/958545/pexels-photo-958545.jpeg?auto=compress&cs=tinysrgb&w=800",
  "https://images.pexels.com/photos/262959/pexels-photo-262959.jpeg?auto=compress&cs=tinysrgb&w=800",
];

export function toFoodSearchTerm(raw: string): string {
  const cleaned = raw
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .split(/\s+/)
    .map((w) => w.trim())
    .filter((w) => w.length > 0 && !FOOD_FILLER.has(w.toLowerCase()));
  if (cleaned.length === 0) return "";

  const phrase = cleaned.join(" ");
  const modifier = modifierFor(phrase);
  const withModifier = (term: string) => (modifier ? `${modifier} ${term} food` : `${term} food`);

  const whole = FOOD_DICTIONARY.find(([ar]) => phrase.includes(ar));
  if (whole) return withModifier(whole[1]);

  for (const word of cleaned) {
    const stripped = word.replace(/^و/, "").replace(/^ال/, "");
    const hit = FOOD_DICTIONARY.find(([ar]) => stripped.includes(ar) || word.includes(ar));
    if (hit) return withModifier(hit[1]);
  }
  // A cooking method with no recognised ingredient is still better than the raw phrase.
  if (modifier) return `${modifier} food`;
  return `${phrase} food`;
}

export function looksLikeFoodAlt(alt: string | null | undefined): boolean {
  const a = (alt ?? "").toLowerCase().trim();
  if (a.length === 0) return true;
  if (FOOD_MARKERS.some((m) => a.includes(m))) return true;
  return !NON_FOOD_MARKERS.some((m) => a.includes(m));
}

export function foodFallbackUrl(raw: string): string {
  let h = 0;
  for (const ch of raw.trim().toLowerCase()) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return FOOD_FALLBACKS[Math.abs(h) % FOOD_FALLBACKS.length];
}
