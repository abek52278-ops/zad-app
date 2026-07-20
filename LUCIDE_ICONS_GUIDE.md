# 🎨 Lucide Icons - دليل الاستخدام

Lucide Icons مكتبة احترافية تحتوي على **400+ icon** عالي الجودة. تم إضافتها لـ Zad لاستبدال Material Icons بـ icons حديثة وأنيقة.

## ✨ الميزات

- ✅ **400+ icon احترافي** - SVG scalable
- ✅ **محسّن للأداء** - التحميل من CDN
- ✅ **قابل للتخصيص** - أحجام وألوان مخصصة
- ✅ **متوافق 100%** - مع Jetpack Compose
- ✅ **بدون dependencies** - تحميل من الإنترنت

---

## 📖 الاستخدام الأساسي

### في ملف Compose:

```kotlin
import com.example.ui.components.LucideIcon
import com.example.ui.components.LucideIcons

// استخدام بسيط
LucideIcon(icon = "home")

// مع حجم مخصص
LucideIcon(icon = "shopping-cart", size = 32.dp)

// مع لون مخصص
LucideIcon(
    icon = "wallet",
    size = 24.dp,
    tint = Color.Red
)

// في Button
Button(onClick = { }) {
    LucideIcon(icon = "plus", size = 20.dp)
    Spacer(modifier = Modifier.width(8.dp))
    Text("إضافة")
}

// قائمة navigation
NavigationItem(
    icon = { LucideIcon(icon = "home") },
    label = { Text("الرئيسية") }
)
```

---

## 🎯 الـ Icons المشهورة في Zad

### المالية و الميزانية
```kotlin
LucideIcon(icon = LucideIcons.WALLET)           // المحفظة
LucideIcon(icon = LucideIcons.CREDIT_CARD)      // البطاقة
LucideIcon(icon = LucideIcons.TRENDING_UP)      // صعود الإيرادات
LucideIcon(icon = LucideIcons.TRENDING_DOWN)    // هبوط الإنفاق
LucideIcon(icon = LucideIcons.TARGET)           // الأهداف
```

### التسوق و المخزون
```kotlin
LucideIcon(icon = LucideIcons.SHOPPING_CART)    // سلة التسوق
LucideIcon(icon = LucideIcons.PACKAGE)          // الصندوق
LucideIcon(icon = LucideIcons.TAG)              // الكوبون
LucideIcon(icon = LucideIcons.FILTER)           // الفلتر
```

### العائلة و التفاعل
```kotlin
LucideIcon(icon = LucideIcons.USERS)            // الأسرة
LucideIcon(icon = LucideIcons.USER)             // المستخدم
LucideIcon(icon = LucideIcons.HEART)            // الحب
LucideIcon(icon = LucideIcons.SMILE)            // الابتسامة
LucideIcon(icon = LucideIcons.AWARD)            // الإنجازات
```

### التنبيهات و الحالة
```kotlin
LucideIcon(icon = LucideIcons.BELL)             // الجرس
LucideIcon(icon = LucideIcons.ALERT_CIRCLE)     // التحذير
LucideIcon(icon = LucideIcons.CHECK_CIRCLE)     // النجاح
LucideIcon(icon = LucideIcons.INFO)             // المعلومات
```

### الإجراءات
```kotlin
LucideIcon(icon = LucideIcons.PLUS)             // إضافة
LucideIcon(icon = LucideIcons.MINUS)            // حذف
LucideIcon(icon = LucideIcons.EDIT)             // تعديل
LucideIcon(icon = LucideIcons.TRASH_2)          // حذف نهائي
LucideIcon(icon = LucideIcons.SETTINGS)         // الإعدادات
```

### الملاحة و التقارير
```kotlin
LucideIcon(icon = LucideIcons.MENU)             // القائمة
LucideIcon(icon = LucideIcons.SEARCH)           // البحث
LucideIcon(icon = LucideIcons.ARROW_RIGHT)      // التالي
LucideIcon(icon = LucideIcons.ARROW_LEFT)       // السابق
LucideIcon(icon = LucideIcons.GRID)             // الشبكة
LucideIcon(icon = LucideIcons.LIST)             // القائمة
```

### التواريخ و الأوقات
```kotlin
LucideIcon(icon = LucideIcons.CALENDAR)         // التقويم
LucideIcon(icon = LucideIcons.CLOCK)            // الساعة
LucideIcon(icon = LucideIcons.REPEAT)           // التكرار
```

### الأخرى
```kotlin
LucideIcon(icon = LucideIcons.QR_CODE)          // كود QR
LucideIcon(icon = LucideIcons.SHARE_2)          // مشاركة
LucideIcon(icon = LucideIcons.DOWNLOAD)         // تحميل
LucideIcon(icon = LucideIcons.LOADER)           // التحميل جاري
```

---

## 🔗 قائمة كاملة بـ Icons

للحصول على قائمة كاملة بجميع الـ icons المتاحة:
👉 https://lucide.dev

---

## 💡 أمثلة متقدمة

### Icon مع Tooltip
```kotlin
TooltipBox(
    tooltip = { Text("اضغط للمزيد") },
    modifier = Modifier.size(24.dp)
) {
    LucideIcon(icon = "info")
}
```

### Icon محرّك
```kotlin
var isLoading by remember { mutableStateOf(true) }

if (isLoading) {
    LucideIcon(
        icon = "loader",
        modifier = Modifier
            .rotate(angle = 45f)
            .animateRotation()
    )
} else {
    LucideIcon(icon = "check-circle")
}
```

### Icon مع Badge
```kotlin
Box(modifier = Modifier.size(24.dp)) {
    LucideIcon(icon = "bell")
    Badge(
        modifier = Modifier.align(Alignment.TopEnd)
    ) {
        Text("3")
    }
}
```

---

## 🚀 الأداء

- **خفيف الوزن:** تحميل من CDN يعني عدم إضافة حجم للـ app
- **Cached:** المتصفح يخزن الـ SVGs محلياً
- **Fast:** تحميل سريع جداً من jsDelivr CDN

---

## 🎓 نصائح

1. **استخدم الثوابت:** استخدم `LucideIcons.HOME` بدل `"home"` لتجنب الأخطاء
2. **حجم مناسب:** استخدم 24.dp للـ UI العادي، 32.dp للـ buttons
3. **لون متسق:** استخدم الألوان من Material3 theme
4. **التحميل:** أول استخدام يحمل من الإنترنت، بعدها يكون محفوظ في الـ cache

---

## 📝 ملاحظات

- جميع الـ icons تحت Creative Commons License
- يمكن استخدام Lucide Icons مجاناً بدون قيود
- التحديثات تأتي تلقائياً من CDN

---

**استمتع مع 400+ icon احترافي! 🎨✨**
