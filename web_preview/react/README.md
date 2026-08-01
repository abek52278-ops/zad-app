# 🎯 ZAD App — React preview (`web_preview/react/`)

نسخة React من واجهة زاد. منفصلة تماماً عن `web_preview/index.html` (الموكاب القديم بخط
Tajawal) — الملف ده ما اتلمسش.

---

## 📁 البنية الحالية

```
web_preview/react/
├── index.html                        # إطار الموبايل (402×874px + notch) + keyframes
├── README.md                         # الملف ده
├── app.jsx                           # الـ source — كل الـ app
├── app.js                            # Output من build.sh (JSX مترجم) — 97KB
├── build.sh                          # transform JSX → JS (esbuild)
└── vendor/
    ├── react.production.min.js       # React 18.3.1 (محلي) — 10.7KB
    └── react-dom.production.min.js   # ReactDOM 18.3.1 (محلي) — 132KB
```

### اللي في `app.jsx`

```javascript
// 1️⃣ كل البيانات HARD-CODED (مفيش fetching):
const STRINGS = { ar: {...}, en: {...} }   // النصوص
const OBLIGATIONS = [...]                   // الالتزامات
const INVENTORY = [...]                     // المخزون
const INV_CATEGORIES = [...]                // تصنيفات المخزون
const TICKER = [...]                        // مؤشر الأسعار
const SHORTCUTS = [...]                     // اختصارات الرئيسية
const HOME_STATS = [...]                    // إحصائيات
const HOME_TX = [...]                       // المعاملات
// ... و 20+ data structure تانية

// 2️⃣ State (React Hooks بس):
const [lang, setLang] = useState('ar')
const [screen, setScreen] = useState('home')
const [kidsMode, setKidsMode] = useState(false)
const [tasbihaCount, setTasbihaCount] = useState(0)
const [sheetOpen, setSheetOpen] = useState(false)
// + moreOpen / cameraOpen / drawerOpen / invCat / confirmed / assistantTab / familyTab

// 3️⃣ الشاشات (13):
Splash · Home · Inventory · Assistant · Pharmacy · Maintenance · Deals
Profile · Notifications · Budget · Shopping · Family · Subs

// 4️⃣ Overlays:
Drawer · MoreSheet · WhySheet · CameraSheet · KidsHome

// 5️⃣ Styling:
// inline CSS بس (لا Tailwind، لا CSS files)، keyframes في index.html،
// ألوان hard-coded: #064E3B, #0F9B76, #DC5B4B ...، RTL كامل
```

---

## ✅ الشاشات

| الشاشة | الـ ID | البيانات |
|--------|-------|---------|
| Splash | `splash` | Logo + CTA |
| Home | `home` | Ticker, بطاقة المتاح، اختصارات، Insights، Stats، ملخص AI، بستان التسبيح، شيف زاد، أمازون، المعاملات |
| Inventory | `inventory` | عناصر حسب الفئة + مؤشر النفاد + فلتر فئات |
| Assistant | `assistant` | نظرة عامة (قوة الإنفاق + sparkline + التوزيع حسب الفئة) · السلوك · المحادثة |
| Pharmacy | `pharmacy` | أدوية، جرعات، الالتزام بالجرعات |
| Maintenance | `maintenance` | أجهزة، ضمانات، مواعيد |
| Deals | `deals` | متاجر قريبة + مسافات |
| Profile | `profile` | بيانات المستخدم، toggle وضع الأطفال، قائمة |
| Notifications | `notifications` | تنبيهات info / warn / danger |
| Budget | `budget` | المتاح + إجمالي الالتزامات + كروت الالتزامات الأربعة |
| Shopping | `shopping` | عناصر بنطاق سعر + badge أولوية (حرج/متوسط/منخفض) — **مفيش checkboxes** |
| Family | `family` | المحادثة · المهام · الأعضاء |
| Subs | `subs` | اشتراكات + مواعيد التجديد |

---

## 🎮 التفاعلات

**Navigation:** تبديل الشاشات · تبديل اللغة (AR ↔ EN مع RTL/LTR) · drawer · More sheet · tabs (assistant, family)

**Features:** عدّاد بستان التسبيح (localStorage) · وضع الأطفال · شيت "لماذا تغير الرقم" · شيت الكاميرا (placeholder) · فلتر فئات المخزون · تأكيد عنصر منخفض

**Persistence:** `localStorage['zad-tasbiha-garden-standalone']` بس (يترست كل يوم تقويمي). اللغة والشاشة in-session.

---

## 🚀 التشغيل

```bash
cd web_preview
python3 -m http.server 8421
# افتح http://localhost:8421/react/index.html
```

فتح `index.html` مباشرة بـ `file://` بيشتغل كمان.

---

## ✏️ التعديل

**نصوص:** `app.jsx` → `STRINGS` → عدّل → `./build.sh` → Refresh.

**بيانات:** `app.jsx` → الـ constant المطلوب (مثلاً `OBLIGATIONS`) → عدّل القيم → `./build.sh`.

**ألوان:** inline في الـ component، غيّر الـ hex.

---

## 🔧 إضافة شاشة جديدة

```javascript
// 1. العنوان في المكانين
STRINGS.ar.titles.myScreen = 'شاشتي';
STRINGS.en.titles.myScreen = 'My Screen';

// 2. الـ component
function MyScreen({ t, ar, lang }) { /* ... */ }

// 3. في ZADApp مع باقي الـ else-if
else if (shown === 'myScreen') body = <MyScreen t={t} ar={ar} lang={lang} />;

// 4. entry في DRAWER_ITEMS أو MORE_ITEMS
{ icon:'M...', ar:'شاشتي', en:'My Screen', go:'myScreen' }
```

> مفيش `SCREENS_CONFIG` في الكود — العناوين كلها من `STRINGS[lang].titles`.

---

## 🐛 Debugging

Console لازم تكون نظيفة 100%. للتحقق:

```bash
ls -lh web_preview/react/app.js            # حجم الـ output
ls -lh web_preview/react/vendor/react*.js  # ملفات React المحلية
```

---

## ❓ أسئلة شائعة

**`app.jsx` vs `app.js`؟** `app.jsx` هو الـ source، `app.js` هو الناتج اللي المتصفح بيشغّله. لا تعدّل `app.js` يدوياً.

**ليه React محلي؟** `vendor/react.production.min.js` + `vendor/react-dom.production.min.js` — يشتغل بدون نت، بدون CDN.

**الحجم؟** `app.js` 97KB + React/ReactDOM 143KB + `index.html` 1.4KB = **~241KB** غير مضغوط.

**ربط API؟** ينفع — كل البيانات دلوقتي hard-coded، يتضاف `fetch()` مكانها.

---

## 📌 ملاحظات الدقة (مهمة لأي جلسة جاية)

منقولة من الـ **standalone offline HTML** (النسخة الكاملة)، مش من `ZAD-React-Clean.jsx` —
الملف ده كان ناقصه 5 شاشات + الـ drawer + شيت المزيد/الكاميرا + وضع الأطفال، وكان بيرجع
`lang` غير معرّف جوه `HomeScreen`/`InventoryScreen`/`FamilyScreen`.

الألوان والـ radii والـ paddings وأحجام الخط والظلال والـ keyframes منقولة حرف بحرف.
تغيير بنيوي واحد: الـ header والـ bottom nav والـ overlays بقوا siblings للجزء اللي بيـscroll
جوه إطار الموبايل بدل ما يكونوا جواه — عشان الـ nav يفضل ثابت تحت.

**العلاقة بتطبيق أندرويد:** ده preview/موكاب للتصميم بس. التطبيق الحقيقي Kotlin/Compose
في `app/src/main/java/com/example/`، وبياناته من Supabase. أي تعديل هنا **ما بيأثرش** على
التطبيق.
