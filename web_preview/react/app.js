(() => {
  const { useState, useEffect } = React;
  const STRINGS = {
    ar: {
      appName: "ZAD",
      langToggle: "EN",
      available: "متاح",
      spent: "مصروف",
      committed: "محجوز للالتزامات",
      daysLeft: "الأيام المتبقية",
      safeSpend: "معدل الصرف اليومي الآمن",
      insightsTitle: "زاد الذكي",
      totalObligations: "إجمالي الالتزامات",
      quickChip: "لماذا تغير الرقم؟",
      sheetTitle: "لماذا تغير الرقم",
      sheetClose: "تمام، فهمت",
      sheetBody: "المبلغ المتاح انخفض بسبب مصروفات جديدة تم رصدها وحجز جزء من الراتب للالتزامات القادمة خلال هذه الدورة.",
      tabHome: "الرئيسية",
      tabInventory: "المخزون",
      tabAssistant: "عقل زاد",
      tabFamily: "العائلة",
      more: "المزيد",
      titles: { home: "لوحة الميزانية", inventory: "المخزون", shopping: "قائمة التسوق", budget: "الميزانية والالتزامات", assistant: "عقل زاد", family: "العائلة", pharmacy: "الصيدلية", maintenance: "الصيانة", deals: "عروض قريبة", profile: "حسابي", notifications: "الإشعارات", subs: "الاشتراكات" },
      daysLeftValue: "9 أيام",
      safeSpendValue: "128 ر.س",
      slogan: "تدبير ذكي لبيت هادئ",
      ctaEnter: "ابدأ مع زاد",
      aiSummaryTitle: "ملخص زاد الذكي",
      recentTx: "آخر المعاملات",
      seeAll: "عرض الكل",
      liveSpendTitle: "نبض الإنفاق الحي",
      liveSpendSub: "آخر 7 أيام",
      categoryBreakdown: "التوزيع حسب الفئة",
      tasbihaTitle: "بستان التسبيح",
      tasbihaTap: "سبحان الله",
      chefTitle: "شيف زاد",
      chefSub: "اقتراح اليوم: طبق سريع بمكونات مخزونك الحالي.",
      amazonTitle: "ترشيحات أمازون",
      amazonBadge: "أمازون",
      shoppingTotal: "إجمالي القائمة",
      smartFill: "تعبئة ذكية",
      spendingPower: "قوة الإنفاق",
      adherence: "الالتزام بالجرعات",
      monthlyCost: "التكلفة الشهرية",
      dealsDisclaimer: "المسافات من خرائط مفتوحة — ليست أسعاراً أو عروضاً فعلية.",
      kidsBadge: "وضع الأطفال",
      exitKids: "🔓 الوضع الكامل",
      kidsBalance: "رصيدي",
      kidsGoal: "هدف التوفير",
      kidsAsk: "✋ محتاج مصروف",
      kidsTasks: "مهامي",
      kidsToggleLabel: "وضع الأطفال",
      kidsToggleSub: "شاشة مبسطة وآمنة للصغار",
      cameraTitle: "زاد الذكي بالكاميرا",
      cameraPreview: "معاينة الكاميرا",
      scanInventory: "مسح مخزون",
      scanReceipt: "مسح فاتورة",
      cancel: "إلغاء"
    },
    en: {
      appName: "ZAD",
      langToggle: "AR",
      available: "Available",
      spent: "Spent",
      committed: "Committed",
      daysLeft: "Days left",
      safeSpend: "Daily safe spend",
      insightsTitle: "Zad Intelligence",
      totalObligations: "Total obligations",
      quickChip: "Why did this change?",
      sheetTitle: "Why did this change",
      sheetClose: "Got it",
      sheetBody: "Your available balance dropped from new expenses we detected, plus a portion of salary reserved for upcoming obligations this cycle.",
      tabHome: "Home",
      tabInventory: "Inventory",
      tabAssistant: "Zad Mind",
      tabFamily: "Family",
      more: "More",
      titles: { home: "Budget Dashboard", inventory: "Inventory", shopping: "Shopping List", budget: "Budget & Obligations", assistant: "Zad Mind", family: "Family", pharmacy: "Pharmacy", maintenance: "Maintenance", deals: "Nearby Deals", profile: "Profile", notifications: "Notifications", subs: "Subscriptions" },
      daysLeftValue: "9 days",
      safeSpendValue: "SAR 128",
      slogan: "Smart care for your home",
      ctaEnter: "Get started",
      aiSummaryTitle: "Zad AI summary",
      recentTx: "Recent transactions",
      seeAll: "See all",
      liveSpendTitle: "Live spend pulse",
      liveSpendSub: "Last 7 days",
      categoryBreakdown: "Category breakdown",
      tasbihaTitle: "Tasbiha Garden",
      tasbihaTap: "Tap to grow",
      chefTitle: "Zad Chef",
      chefSub: "Today's pick: a quick recipe from what's already in your stock.",
      amazonTitle: "Amazon picks",
      amazonBadge: "Amazon",
      shoppingTotal: "List total",
      smartFill: "Smart fill",
      spendingPower: "Spending power",
      adherence: "Dose adherence",
      monthlyCost: "Monthly cost",
      dealsDisclaimer: "Distances from open map data — not live prices or deals.",
      kidsBadge: "Kids Mode",
      exitKids: "🔓 Full mode",
      kidsBalance: "Your balance",
      kidsGoal: "Savings goal",
      kidsAsk: "✋ Ask for money",
      kidsTasks: "My tasks",
      kidsToggleLabel: "Kids Mode",
      kidsToggleSub: "Simplified, safe screen for kids",
      cameraTitle: "Zad AI camera",
      cameraPreview: "Camera preview",
      scanInventory: "Scan inventory",
      scanReceipt: "Scan receipt",
      cancel: "Cancel"
    }
  };
  const INSIGHTS = [
    { ar: { title: "تم رصد دورة راتب جديدة", subtitle: "استلمنا راتب هذا الشهر وأعدنا حساب الميزانية تلقائياً." }, en: { title: "New salary cycle detected", subtitle: "This month's salary landed — your budget was recalculated." }, kind: "info" },
    { ar: { title: "تحقق من مطابقة النقد", subtitle: "هناك فرق صغير بين المصروف الفعلي والمسجل." }, en: { title: "Cash reconciliation check-in", subtitle: "A small gap between actual and recorded spend." }, kind: "warn" },
    { ar: { title: "تنبيه نفاد مخزون: حليب", subtitle: "الكمية المتبقية تكفي ليوم واحد فقط." }, en: { title: "Inventory alert: Milk", subtitle: "Remaining stock covers only 1 more day." }, kind: "danger" }
  ];
  const OBLIGATIONS = [
    { ar: { name: "الإيجار", due: "مستحق بعد 4 أيام" }, en: { name: "Rent", due: "Due in 4 days" }, amountAr: "2,400 ر.س", amountEn: "SAR 2,400", pct: 0, statusKind: "pending" },
    { ar: { name: "الإنترنت والاتصالات", due: "مدفوع" }, en: { name: "Internet & mobile", due: "Paid" }, amountAr: "260 ر.س", amountEn: "SAR 260", pct: 100, statusKind: "paid" },
    { ar: { name: "قسط السيارة", due: "مستحق بعد 11 يوم" }, en: { name: "Car installment", due: "Due in 11 days" }, amountAr: "980 ر.س", amountEn: "SAR 980", pct: 30, statusKind: "scheduled" },
    { ar: { name: "عضوية النادي", due: "مدفوع" }, en: { name: "Gym membership", due: "Paid" }, amountAr: "150 ر.س", amountEn: "SAR 150", pct: 100, statusKind: "paid" }
  ];
  const INV_CATEGORIES = [
    { id: "fruit", icon: "M12 8c-3 0-5.5 2.7-5.5 6.5S9 21 12 21s5.5-2.8 5.5-6.5S15 8 12 8z M12 8c0-2 1-4 3-5", ar: "فاكهة", en: "Fruit", bg: "#FCEAEA", fg: "#DC5B4B" },
    { id: "veg", icon: "M12 3c4 0 7 3 7 8 0 6-4 10-7 10s-7-4-7-10c0-5 3-8 7-8zM12 3v4", ar: "خضار", en: "Vegetables", bg: "#E9F5E9", fg: "#3CA06E" },
    { id: "dairy", icon: "M9 2h6l1 4-1 2v11a2 2 0 01-2 2h-2a2 2 0 01-2-2V8l-1-2 1-4z", ar: "ألبان", en: "Dairy", bg: "#EAF2FB", fg: "#2563EB" },
    { id: "bakery", icon: "M4 12c0-4 3.5-7 8-7s8 3 8 7-3.5 6-8 6-8-2-8-6z M6 15c1 2 3 3 6 3s5-1 6-3", ar: "خبز ومخبوزات", en: "Bakery", bg: "#FBF1E3", fg: "#B45309" },
    { id: "drinks", icon: "M12 2c3 4 5 6.5 5 10a5 5 0 01-10 0c0-3.5 2-6 5-10z", ar: "مياه ومشروبات", en: "Drinks", bg: "#E7F5F8", fg: "#0891B2" },
    { id: "sweets", icon: "M7 13a5 5 0 0110 0c0 1-.5 2-2 2.5V17a3 3 0 01-6 0v-1.5C7.5 15 7 14 7 13z", ar: "حلويات", en: "Sweets", bg: "#F1E9E3", fg: "#C2703D" },
    { id: "cleaning", icon: "M8 2l8 8-7 7a3 3 0 01-4-4l3-3M17 13l4 4-2 2-4-4", ar: "أدوات تنظيف", en: "Cleaning", bg: "#EEF0F3", fg: "#374151" }
  ];
  const INVENTORY = [
    { ar: "تفاح", en: "Apples", cat: "fruit", days: 6, low: false },
    { ar: "موز", en: "Bananas", cat: "fruit", days: 2, low: true },
    { ar: "طماطم", en: "Tomatoes", cat: "veg", days: 4, low: false },
    { ar: "بروكلي", en: "Broccoli", cat: "veg", days: 5, low: false },
    { ar: "حليب", en: "Milk", cat: "dairy", days: 1, low: true },
    { ar: "جبنة", en: "Cheese", cat: "dairy", days: 9, low: false },
    { ar: "خبز", en: "Bread", cat: "bakery", days: 2, low: true },
    { ar: "كرواسون", en: "Croissants", cat: "bakery", days: 3, low: false },
    { ar: "مياه معدنية", en: "Mineral water", cat: "drinks", days: 20, low: false },
    { ar: "عصير برتقال", en: "Orange juice", cat: "drinks", days: 4, low: false },
    { ar: "شوكولاتة", en: "Chocolate", cat: "sweets", days: 15, low: false },
    { ar: "منظف أرضيات", en: "Floor cleaner", cat: "cleaning", days: 18, low: false },
    { ar: "صابون", en: "Soap", cat: "cleaning", days: 5, low: false }
  ];
  const CHATS = [
    { ar: "مرحباً، لاحظت أنك أنفقت أكثر من المعتاد هذا الأسبوع في التسوق.", en: "Hi — you spent more than usual on groceries this week.", user: false },
    { ar: "لماذا تغير الرقم المتاح؟", en: "Why did the available amount change?", user: true },
    { ar: "انخفض المتاح 320 ر.س بسبب مصروف جديد وحجز جزء من الراتب لالتزام قادم.", en: "Available dropped SAR 320 from a new expense plus a reserved upcoming obligation.", user: false },
    { ar: "تمام، شكراً لك", en: "Got it, thanks", user: true }
  ];
  const TICKER = [
    { ar: "أرز", en: "Rice", delta: "+2%", up: true },
    { ar: "دجاج", en: "Chicken", delta: "-1%", up: false },
    { ar: "بنزين", en: "Fuel", delta: "0%", up: null }
  ];
  const SHORTCUTS = [
    { icon: "M3 9l1-5h16l1 5M4 9h16v10a1 1 0 01-1 1H5a1 1 0 01-1-1V9zM9 13h6", bg: "#E3F5EC", fg: "#0B6B4E", ar: "المخزون", en: "Inventory", go: "inventory" },
    { icon: "M3 4h2l2.4 12.4a2 2 0 002 1.6h8.2a2 2 0 002-1.6L21 8H6M9 21a1 1 0 100-2 1 1 0 000 2zM18 21a1 1 0 100-2 1 1 0 000 2z", bg: "#FCEEE3", fg: "#C2703D", ar: "التسوق", en: "Shopping", go: "shopping" },
    { icon: "M7 8a3 3 0 100-6 3 3 0 000 6zM17 8a3 3 0 100-6 3 3 0 000 6zM1 21v-2a4 4 0 014-4h4a4 4 0 014 4v2M14 15h2a4 4 0 014 4v2", bg: "#F1EAFB", fg: "#7C3AED", ar: "العائلة", en: "Family", go: "family" },
    { icon: "M2 6a2 2 0 012-2h16a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V6zM2 10h20M6 15h4", bg: "#E8F1FC", fg: "#2563EB", ar: "الاشتراكات", en: "Subs", go: "subs" },
    { icon: "M9 2h6v5h5v6h-5v5H9v-5H4V7h5V2z", bg: "#FCE8ED", fg: "#DC5B4B", ar: "الصيدلية", en: "Pharmacy", go: "pharmacy" },
    { icon: "M14.7 6.3a4 4 0 01-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 015.4-5.4l-2.6 2.6-2-2 2.6-2.6z", bg: "#FDF3E1", fg: "#B45309", ar: "الصيانة", en: "Maintenance", go: "maintenance" }
  ];
  const HOME_STATS = [
    { ar: "قوة الإنفاق", en: "Spending power", valAr: "82%", valEn: "82%" },
    { ar: "الصحة المالية", en: "Health score", valAr: "74/100", valEn: "74/100" },
    { ar: "صرف الشهر", en: "Monthly spend", valAr: "4,120 ر.س", valEn: "SAR 4,120" },
    { ar: "اتجاه 7 أيام", en: "7-day trend", valAr: "↓ 6%", valEn: "↓ 6%" }
  ];
  const HOME_TX = [
    { ar: "سوبرماركت العائلة", en: "Family Supermarket", dateAr: "اليوم", dateEn: "Today", amountAr: "-240 ر.س", amountEn: "-SAR 240", neg: true },
    { ar: "راتب يوليو", en: "July salary", dateAr: "قبل يومين", dateEn: "2 days ago", amountAr: "+9,000 ر.س", amountEn: "+SAR 9,000", neg: false },
    { ar: "اشتراك نتفليكس", en: "Netflix", dateAr: "قبل 3 أيام", dateEn: "3 days ago", amountAr: "-45 ر.س", amountEn: "-SAR 45", neg: true }
  ];
  const SHOP_ITEMS = [
    { ar: "حليب", en: "Milk", rangeAr: "8–10 ر.س", rangeEn: "SAR 8–10", p: "high" },
    { ar: "خبز", en: "Bread", rangeAr: "4–6 ر.س", rangeEn: "SAR 4–6", p: "med" },
    { ar: "فيتامين د", en: "Vitamin D", rangeAr: "35–45 ر.س", rangeEn: "SAR 35–45", p: "high" },
    { ar: "منظف أطباق", en: "Dish soap", rangeAr: "12–15 ر.س", rangeEn: "SAR 12–15", p: "low" }
  ];
  const ASSIST_OVERVIEW = [
    { ar: "أيام حتى النفاد", en: "Stress-test days", valAr: "19", valEn: "19" },
    { ar: "التنبؤ الشهر القادم", en: "Next-month forecast", valAr: "4,600 ر.س", valEn: "SAR 4,600" },
    { ar: "أعلى فئة صرف", en: "Top category", valAr: "بقالة", valEn: "Groceries" },
    { ar: "اشتراكات نشطة", en: "Active subs", valAr: "5", valEn: "5" }
  ];
  const ASSIST_BEHAVIOR = [
    { ar: { title: "ذروة إنفاق يوم الخميس", body: "تنفق أكثر بـ 30% كل خميس مقارنة ببقية الأسبوع." }, en: { title: "Thursday spend spike", body: "You spend 30% more every Thursday than the rest of the week." } },
    { ar: { title: "رادار التضخم الشخصي", body: "متوسط أسعار البقالة عندك ارتفع 4% هذا الشهر." }, en: { title: "Personal inflation radar", body: "Your average grocery prices rose 4% this month." } },
    { ar: { title: "توقيت شراء ذكي", body: "الأفضل شراء الإلكترونيات بعد 5 أيام من الآن." }, en: { title: "Smart buying timing", body: "Best time to buy electronics is in 5 days." } }
  ];
  const SUBS_ITEMS = [
    { ar: "نتفليكس", en: "Netflix", priceAr: "45 ر.س", priceEn: "SAR 45", renewAr: "يتجدد بعد 6 أيام", renewEn: "Renews in 6 days", color: "#B45309" },
    { ar: "STC TV", en: "STC TV", priceAr: "99 ر.س", priceEn: "SAR 99", renewAr: "يتجدد بعد 12 يوم", renewEn: "Renews in 12 days", color: "#064E3B" },
    { ar: "اشتراك الجيم", en: "Gym", priceAr: "150 ر.س", priceEn: "SAR 150", renewAr: "يتجدد بعد يومين", renewEn: "Renews in 2 days", color: "#DC5B4B" }
  ];
  const FAMILY_MEMBERS = [
    { ar: "سارة", en: "Sarah", roleAr: "أم · مشرف", roleEn: "Mom · Admin", statAr: "3 مهام", statEn: "3 tasks" },
    { ar: "أحمد", en: "Ahmed", roleAr: "أب · مشرف", roleEn: "Dad · Admin", statAr: "5 مهام", statEn: "5 tasks" },
    { ar: "يوسف", en: "Youssef", roleAr: "ابن", roleEn: "Son", statAr: "هدف 60%", statEn: "Goal 60%" }
  ];
  const FAMILY_TASKS = [
    { ar: "ترتيب الغرفة", en: "Tidy room", whoAr: "يوسف", whoEn: "Youssef", done: false },
    { ar: "سقاية النبات", en: "Water plants", whoAr: "سارة", whoEn: "Sarah", done: true },
    { ar: "شراء بقالة", en: "Buy groceries", whoAr: "أحمد", whoEn: "Ahmed", done: false }
  ];
  const PHARM_ITEMS = [
    { ar: "باراسيتامول", en: "Paracetamol", memberAr: "يوسف", memberEn: "Youssef", doseAr: "3 مرات يومياً", doseEn: "3x daily", kind: "ok" },
    { ar: "فيتامين د", en: "Vitamin D", memberAr: "سارة", memberEn: "Sarah", doseAr: "مرة يومياً", doseEn: "1x daily", kind: "low" },
    { ar: "أنسولين", en: "Insulin", memberAr: "أحمد", memberEn: "Ahmed", doseAr: "مرتين يومياً", doseEn: "2x daily", kind: "confirm" }
  ];
  const MAINT_ITEMS = [
    { ar: "مكيف الصالة", en: "Living room AC", warrantyAr: "الضمان حتى 2027", warrantyEn: "Warranty until 2027", dueAr: "مستحق قريباً", dueEn: "Due soon", kind: "soon" },
    { ar: "الثلاجة", en: "Refrigerator", warrantyAr: "الضمان منتهي", warrantyEn: "Warranty expired", dueAr: "متأخر", dueEn: "Overdue", kind: "over" },
    { ar: "غسالة الملابس", en: "Washing machine", warrantyAr: "الضمان حتى 2026", warrantyEn: "Warranty until 2026", dueAr: "على الموعد", dueEn: "On schedule", kind: "ok" }
  ];
  const DEALS_STORES = [
    { ar: "صيدلية النهدي", en: "Nahdi Pharmacy", typeAr: "صيدلية", typeEn: "Pharmacy", dist: "650m" },
    { ar: "بندة", en: "Panda", typeAr: "سوبرماركت", typeEn: "Supermarket", dist: "1.1km" },
    { ar: "أسواق التميمي", en: "Tamimi Markets", typeAr: "سوبرماركت", typeEn: "Supermarket", dist: "1.8km" }
  ];
  const NOTIFS = [
    { ar: { title: "تم رصد دورة راتب جديدة", body: "أعدنا حساب ميزانيتك تلقائياً." }, en: { title: "New salary cycle", body: "Your budget was recalculated." }, kind: "info" },
    { ar: { title: "نفاد مخزون قريب", body: "الحليب يكفي ليوم واحد." }, en: { title: "Low stock soon", body: "Milk covers 1 more day." }, kind: "danger" },
    { ar: { title: "اشتراك يتجدد", body: "نتفليكس يتجدد بعد 6 أيام." }, en: { title: "Subscription renewing", body: "Netflix renews in 6 days." }, kind: "warn" }
  ];
  const KIDS_TASKS = [
    { ar: "ترتيب الغرفة", en: "Tidy room", coins: 10 },
    { ar: "حفظ سورة", en: "Memorize verse", coins: 20 },
    { ar: "مساعدة في المطبخ", en: "Help in kitchen", coins: 15 }
  ];
  const KIDS_BADGES = [
    { emoji: "🔥", ar: "مثابرة", en: "Streak" },
    { emoji: "💰", ar: "موفر", en: "Saver" },
    { emoji: "⭐", ar: "نجم", en: "Star" }
  ];
  const AMAZON_DEALS = [
    { ar: "قدر ضغط كهربائي", en: "Electric pressure cooker", price: "189 ر.س", priceEn: "SAR 189" },
    { ar: "فيتامينات عائلية", en: "Family vitamins pack", price: "85 ر.س", priceEn: "SAR 85" },
    { ar: "منظم مخزون مطبخ", en: "Kitchen storage organizer", price: "49 ر.س", priceEn: "SAR 49" }
  ];
  const SPEND_TREND = [320, 410, 260, 480, 390, 520, 300];
  const CATEGORY_SPEND = [
    { ar: "بقالة", en: "Groceries", amt: 1240, max: 1600, color: "#0F9B76" },
    { ar: "مواصلات", en: "Transport", amt: 480, max: 1600, color: "#2563EB" },
    { ar: "فواتير", en: "Bills", amt: 690, max: 1600, color: "#B45309" },
    { ar: "صحة", en: "Health", amt: 210, max: 1600, color: "#DC5B4B" },
    { ar: "ترفيه", en: "Entertainment", amt: 150, max: 1600, color: "#7C3AED" }
  ];
  const MORE_ITEMS = [
    { icon: "M3 4h2l2.4 12.4a2 2 0 002 1.6h8.2a2 2 0 002-1.6L21 8H6M9 21a1 1 0 100-2 1 1 0 000 2zM18 21a1 1 0 100-2 1 1 0 000 2z", bg: "#FCEEE3", fg: "#C2703D", ar: "التسوق", en: "Shopping", go: "shopping" },
    { icon: "M7 8a3 3 0 100-6 3 3 0 000 6zM17 8a3 3 0 100-6 3 3 0 000 6zM1 21v-2a4 4 0 014-4h4a4 4 0 014 4v2M14 15h2a4 4 0 014 4v2", bg: "#F1EAFB", fg: "#7C3AED", ar: "العائلة", en: "Family", go: "family" },
    { icon: "M1 10h4.5v9H1zM7.7 5h4.5v14H7.7zM14.5 1H19v18h-4.5z", bg: "#E8F1FC", fg: "#2563EB", ar: "الميزانية", en: "Budget", go: "budget" },
    { icon: "M2 6a2 2 0 012-2h16a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V6zM2 10h20M6 15h4", bg: "#E8F1FC", fg: "#2563EB", ar: "الاشتراكات", en: "Subscriptions", go: "subs" },
    { icon: "M9 2h6v5h5v6h-5v5H9v-5H4V7h5V2z", bg: "#FCE8ED", fg: "#DC5B4B", ar: "الصيدلية", en: "Pharmacy", go: "pharmacy" },
    { icon: "M14.7 6.3a4 4 0 01-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 015.4-5.4l-2.6 2.6-2-2 2.6-2.6z", bg: "#FDF3E1", fg: "#B45309", ar: "الصيانة", en: "Maintenance", go: "maintenance" },
    { icon: "M12 2a7 7 0 00-7 7c0 5.3 7 13 7 13s7-7.7 7-13a7 7 0 00-7-7zM12 12a3 3 0 100-6 3 3 0 000 6z", bg: "#E3F5EC", fg: "#0B6B4E", ar: "عروض قريبة", en: "Nearby deals", go: "deals" },
    { icon: "M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z", bg: "#EEF0F3", fg: "#374151", ar: "حسابي", en: "Profile", go: "profile" }
  ];
  const DRAWER_ITEMS = [
    { icon: "M2 9L11 2l9 7v9a1 1 0 01-1 1h-5v-6H8v6H3a1 1 0 01-1-1V9z", ar: "الرئيسية", en: "Home", go: "home" },
    { icon: "M3 9l1-5h16l1 5M4 9h16v10a1 1 0 01-1 1H5a1 1 0 01-1-1V9zM9 13h6", ar: "المخزون", en: "Inventory", go: "inventory" },
    { icon: "M1 3.5A2 2 0 013 1.5h15a2 2 0 012 2V13a2 2 0 01-2 2H8l-5 4v-4H3a2 2 0 01-2-2V3.5z", ar: "عقل زاد", en: "Zad Mind", go: "assistant" },
    { icon: "M2 6a2 2 0 012-2h16a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V6zM2 10h20M6 15h4", ar: "الاشتراكات", en: "Subscriptions", go: "subs" },
    { icon: "M3 4h2l2.4 12.4a2 2 0 002 1.6h8.2a2 2 0 002-1.6L21 8H6M9 21a1 1 0 100-2 1 1 0 000 2zM18 21a1 1 0 100-2 1 1 0 000 2z", ar: "التسوق", en: "Shopping", go: "shopping" },
    { icon: "M7 8a3 3 0 100-6 3 3 0 000 6zM17 8a3 3 0 100-6 3 3 0 000 6zM1 21v-2a4 4 0 014-4h4a4 4 0 014 4v2M14 15h2a4 4 0 014 4v2", ar: "العائلة", en: "Family", go: "family" },
    { icon: "M1 10h4.5v9H1zM7.7 5h4.5v14H7.7zM14.5 1H19v18h-4.5z", ar: "الميزانية", en: "Budget", go: "budget" },
    { icon: "M9 2h6v5h5v6h-5v5H9v-5H4V7h5V2z", ar: "الصيدلية", en: "Pharmacy", go: "pharmacy" },
    { icon: "M14.7 6.3a4 4 0 01-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 015.4-5.4l-2.6 2.6-2-2 2.6-2.6z", ar: "الصيانة", en: "Maintenance", go: "maintenance" },
    { icon: "M12 2a7 7 0 00-7 7c0 5.3 7 13 7 13s7-7.7 7-13a7 7 0 00-7-7zM12 12a3 3 0 100-6 3 3 0 000 6z", ar: "عروض قريبة", en: "Nearby Deals", go: "deals" },
    { icon: "M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z", ar: "حسابي", en: "Profile", go: "profile" },
    { icon: "M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9zM13.7 21a2 2 0 01-3.4 0", ar: "الإشعارات", en: "Notifications", go: "notifications" }
  ];
  const PROFILE_MENU = [
    { ar: "تعديل الملف الشخصي", en: "Edit profile" },
    { ar: "إدارة العائلة", en: "Manage family" },
    { ar: "الإشعارات", en: "Notifications", go: "notifications" },
    { ar: "استيراد كشف حساب بنكي", en: "Import bank statement" },
    { ar: "الدعم والمساعدة", en: "Help & support" },
    { ar: "تسجيل الخروج", en: "Logout" }
  ];
  const SHADOW_CARD = "0 1px 2px rgba(15,23,42,.04), 0 8px 20px rgba(15,23,42,.07)";
  const SHADOW_SOFT = "0 2px 4px rgba(15,23,42,.03), 0 12px 28px rgba(15,23,42,.06)";
  const SHADOW_CHIP = "0 1px 2px rgba(15,23,42,.04), 0 6px 14px rgba(15,23,42,.06)";
  const SHEET_STYLE = {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    background: "#fff",
    borderRadius: "24px 24px 0 0",
    boxShadow: "0 -12px 32px rgba(15,23,42,.18)",
    transition: "transform .32s cubic-bezier(.32,.72,0,1)"
  };
  function Icon({ path, size, color, strokeWidth }) {
    return /* @__PURE__ */ React.createElement(
      "svg",
      {
        width: size,
        height: size,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: color,
        strokeWidth: strokeWidth || 1.9,
        strokeLinejoin: "round",
        strokeLinecap: "round"
      },
      /* @__PURE__ */ React.createElement("path", { d: path })
    );
  }
  function CarrotSm({ size }) {
    const s = size || 16;
    return /* @__PURE__ */ React.createElement("svg", { width: s, height: s, viewBox: "0 0 100 100" }, /* @__PURE__ */ React.createElement("defs", null, /* @__PURE__ */ React.createElement("linearGradient", { id: "hzg", x1: "0", y1: "0", x2: "1", y2: "1" }, /* @__PURE__ */ React.createElement("stop", { offset: "0", stopColor: "#FCD34D" }), /* @__PURE__ */ React.createElement("stop", { offset: "1", stopColor: "#D97706" }))), /* @__PURE__ */ React.createElement("path", { d: "M46 30 C60 40 66 58 60 78 C58 85 50 92 44 90 C38 88 34 80 36 70 C40 52 40 40 46 30 Z", fill: "url(#hzg)", stroke: "#B45309", strokeWidth: "2" }), /* @__PURE__ */ React.createElement("path", { d: "M44 32 C40 24 44 16 40 10 M50 30 C50 20 56 16 54 8 M56 33 C58 24 66 22 66 14", fill: "none", stroke: "#16A34A", strokeWidth: "6", strokeLinecap: "round" }));
  }
  function Card({ children, extra, onClick, className }) {
    return /* @__PURE__ */ React.createElement("div", { onClick, className, style: {
      background: "#fff",
      borderRadius: 16,
      padding: 14,
      boxShadow: SHADOW_CARD,
      ...extra
    } }, children);
  }
  function SegBtn({ label, on, onClick }) {
    return /* @__PURE__ */ React.createElement("button", { onClick, style: {
      flex: 1,
      border: "none",
      borderRadius: 9999,
      padding: "9px 4px",
      fontSize: 12,
      fontWeight: 700,
      cursor: "pointer",
      background: on ? "#064E3B" : "transparent",
      color: on ? "#fff" : "#6B7280"
    } }, label);
  }
  function Overlay({ open, onClick, z }) {
    return /* @__PURE__ */ React.createElement("div", { onClick, style: {
      position: "absolute",
      inset: 0,
      background: "rgba(15,23,42,.4)",
      opacity: open ? 1 : 0,
      pointerEvents: open ? "auto" : "none",
      transition: "opacity .25s",
      zIndex: z
    } });
  }
  function Grabber() {
    return /* @__PURE__ */ React.createElement("div", { style: { width: 36, height: 5, borderRadius: 99, background: "rgba(0,0,0,.15)", margin: "10px auto 4px" } });
  }
  function Splash({ t, ar, onEnter, onToggleLang }) {
    return /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      inset: 0,
      zIndex: 80,
      background: "radial-gradient(120% 100% at 15% 10%,rgba(252,211,199,.55) 0%,rgba(255,255,255,0) 55%),radial-gradient(120% 100% at 85% 90%,rgba(191,227,209,.55) 0%,rgba(255,255,255,0) 55%),#FBFAF8",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      overflow: "hidden"
    } }, /* @__PURE__ */ React.createElement("button", { onClick: onToggleLang, style: {
      position: "absolute",
      top: 54,
      [ar ? "left" : "right"]: 20,
      border: "none",
      background: "rgba(6,78,59,.06)",
      color: "#064E3B",
      fontSize: 12.5,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "7px 14px",
      cursor: "pointer"
    } }, t.langToggle), /* @__PURE__ */ React.createElement("div", { style: { animation: "zadCarrotIn .7s cubic-bezier(.22,1,.36,1) both, zadCarrotFloat 3s ease-in-out .7s infinite" } }, /* @__PURE__ */ React.createElement("svg", { width: "60", height: "60", viewBox: "0 0 64 64", fill: "none" }, /* @__PURE__ */ React.createElement("path", { d: "M31 16C36 20 42 30 40 44C39 51 34 58 29 57C24 56 21 50 22 43C24 30 27 21 31 16Z", fill: "#F0703D" }), /* @__PURE__ */ React.createElement("path", { d: "M25.5 30.5L34.5 33.5M24 38L33 41M25.5 45.5L31.5 47.5", stroke: "#FBFAF8", strokeWidth: "2", strokeLinecap: "round" }), /* @__PURE__ */ React.createElement("path", { d: "M29 17C27 13 28 9 26 6M32 15C32 11 34 8 33 4M35 17C37 14 40 12 40 8", stroke: "#3CA06E", strokeWidth: "3.2", strokeLinecap: "round" }))), /* @__PURE__ */ React.createElement("div", { style: { marginTop: 20, fontSize: 32, fontWeight: 800, color: "#0F9B76", letterSpacing: "-.5px", animation: "zadFadeUp .6s ease .35s both" } }, t.appName), /* @__PURE__ */ React.createElement("div", { style: { marginTop: 10, fontSize: 15, fontWeight: 600, color: "#374151", animation: "zadFadeUp .6s ease .48s both" } }, t.slogan), /* @__PURE__ */ React.createElement("div", { style: { marginTop: 2, fontSize: 12.5, fontWeight: 500, color: "#9CA3AF", animation: "zadFadeUp .6s ease .55s both" } }, "Smart care for your home"), /* @__PURE__ */ React.createElement("div", { style: { marginTop: 60, width: 36, height: 4, borderRadius: 99, background: "rgba(15,23,42,.12)", animation: "zadFadeUp .6s ease .5s both" } }), /* @__PURE__ */ React.createElement("button", { onClick: onEnter, style: {
      position: "absolute",
      bottom: 64,
      border: "none",
      background: "#064E3B",
      color: "#fff",
      fontSize: 15.5,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "15px 46px",
      cursor: "pointer",
      boxShadow: "0 10px 26px rgba(6,78,59,.25)",
      animation: "zadFadeUp .7s ease .7s both"
    } }, t.ctaEnter));
  }
  function Header({ t, screen, kidsMode, onToggleLang, onExitKids, onOpenDrawer }) {
    const shown = kidsMode && screen !== "family" ? "home" : screen;
    return /* @__PURE__ */ React.createElement("div", { style: {
      position: "relative",
      zIndex: 5,
      background: "rgba(249,250,251,.92)",
      backdropFilter: "blur(14px)",
      padding: "54px 20px 14px",
      display: "flex",
      flexDirection: "column",
      gap: 12,
      borderBottom: "1px solid rgba(6,78,59,.06)",
      flexShrink: 0
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", justifyContent: "space-between" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 10 } }, /* @__PURE__ */ React.createElement("button", { onClick: onOpenDrawer, style: {
      border: "none",
      background: "rgba(6,78,59,.08)",
      borderRadius: 10,
      width: 32,
      height: 32,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      cursor: "pointer"
    } }, /* @__PURE__ */ React.createElement("svg", { width: "16", height: "12", viewBox: "0 0 16 12", fill: "none", stroke: "#064E3B", strokeWidth: "1.8", strokeLinecap: "round" }, /* @__PURE__ */ React.createElement("line", { x1: "0", y1: "1", x2: "16", y2: "1" }), /* @__PURE__ */ React.createElement("line", { x1: "0", y1: "6", x2: "16", y2: "6" }), /* @__PURE__ */ React.createElement("line", { x1: "0", y1: "11", x2: "16", y2: "11" }))), /* @__PURE__ */ React.createElement("div", { style: { width: 32, height: 32, borderRadius: 9, background: "#E6F4EC", display: "flex", alignItems: "center", justifyContent: "center" } }, /* @__PURE__ */ React.createElement(CarrotSm, null)), /* @__PURE__ */ React.createElement("span", { style: { fontWeight: 700, fontSize: 19, color: "#064E3B", letterSpacing: "-.2px" } }, t.appName), kidsMode && /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, fontWeight: 700, color: "#7C3AED", background: "rgba(124,58,237,.1)", borderRadius: 9999, padding: "3px 9px" } }, t.kidsBadge)), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 8, alignItems: "center" } }, kidsMode && /* @__PURE__ */ React.createElement("button", { onClick: onExitKids, style: {
      border: "none",
      background: "rgba(124,58,237,.1)",
      color: "#7C3AED",
      fontSize: 11.5,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "6px 12px",
      cursor: "pointer"
    } }, t.exitKids), /* @__PURE__ */ React.createElement("button", { onClick: onToggleLang, style: {
      border: "none",
      background: "rgba(6,78,59,.08)",
      borderRadius: 9999,
      padding: "7px 14px",
      fontSize: 13,
      fontWeight: 600,
      color: "#064E3B",
      cursor: "pointer"
    } }, t.langToggle))), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 26, fontWeight: 700, color: "#0F172A", letterSpacing: "-.3px" } }, t.titles[shown] || t.titles.home));
  }
  function HomeScreen({ t, ar, lang, onOpenSheet, onNavigate, tasbihaCount, onTasbiha, showConfetti }) {
    const kindDot = { info: "#064E3B", warn: "#B45309", danger: "#DC5B4B" };
    const kindTagBg = { info: "rgba(6,78,59,.1)", warn: "rgba(180,83,9,.1)", danger: "rgba(220,91,75,.12)" };
    const kindTag = { info: ar ? "معلومة" : "Info", warn: ar ? "مطلوب" : "Action", danger: ar ? "تحذير" : "Alert" };
    const gardenPct = Math.round(tasbihaCount / 100 * 100);
    const gardenEmoji = tasbihaCount >= 100 ? "🌳" : tasbihaCount >= 75 ? "🌿🌿" : tasbihaCount >= 50 ? "🌦" : tasbihaCount >= 25 ? "🌿" : "🌱";
    const aiChips = ar ? ["ألغي اشتراك؟", "زود الميزانية", "شوف التفاصيل"] : ["Cancel a sub?", "Increase budget", "See details"];
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 18, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 8, overflow: "auto" } }, TICKER.map((tk, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      flexShrink: 0,
      display: "flex",
      alignItems: "center",
      gap: 6,
      background: "#fff",
      borderRadius: 9999,
      padding: "7px 12px",
      boxShadow: "0 2px 6px rgba(15,23,42,.05)"
    } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, fontWeight: 700, color: "#0F172A" } }, tk[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, fontWeight: 700, color: tk.up === true ? "#064E3B" : tk.up === false ? "#DC5B4B" : "#9CA3AF" } }, tk.delta)))), /* @__PURE__ */ React.createElement("div", { onClick: onOpenSheet, className: "press", style: {
      background: "linear-gradient(120deg,#0B6B4E,#0F9B76,#064E3B,#0B6B4E)",
      backgroundSize: "300% 300%",
      animation: "zadMeshShift 9s ease infinite",
      border: "1px solid rgba(255,255,255,.16)",
      borderRadius: 28,
      padding: "26px 24px 22px",
      color: "#fff",
      cursor: "pointer",
      display: "flex",
      flexDirection: "column",
      gap: 16,
      boxShadow: "inset 0 1px 0 rgba(255,255,255,.2), 0 20px 44px rgba(6,78,59,.35)"
    } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, fontWeight: 700, letterSpacing: ".4px", color: "rgba(255,255,255,.72)" } }, t.available), /* @__PURE__ */ React.createElement("span", { style: {
      fontSize: 44,
      fontWeight: 800,
      letterSpacing: "-1.2px",
      background: "linear-gradient(180deg,#fff,#D9F2E6)",
      WebkitBackgroundClip: "text",
      backgroundClip: "text",
      WebkitTextFillColor: "transparent"
    } }, ar ? "↓ 3,240 ر.س" : "↓ SAR 3,240"), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 10, flexWrap: "wrap" } }, /* @__PURE__ */ React.createElement("div", { style: {
      display: "flex",
      alignItems: "center",
      gap: 7,
      background: "rgba(255,255,255,.14)",
      backdropFilter: "blur(10px)",
      border: "1px solid rgba(255,255,255,.14)",
      borderRadius: 9999,
      padding: "7px 13px",
      fontSize: 12.5,
      fontWeight: 600
    } }, /* @__PURE__ */ React.createElement("div", { style: { width: 7, height: 7, borderRadius: "50%", background: "#F4A93B", boxShadow: "0 0 8px 2px rgba(244,169,59,.7)" } }), t.spent, ": ", ar ? "1,120 ر.س" : "SAR 1,120"), /* @__PURE__ */ React.createElement("div", { style: {
      display: "flex",
      alignItems: "center",
      gap: 7,
      background: "rgba(255,255,255,.14)",
      backdropFilter: "blur(10px)",
      border: "1px solid rgba(255,255,255,.14)",
      borderRadius: 9999,
      padding: "7px 13px",
      fontSize: 12.5,
      fontWeight: 600
    } }, /* @__PURE__ */ React.createElement("div", { style: { width: 7, height: 7, borderRadius: "50%", background: "#FF8066", boxShadow: "0 0 8px 2px rgba(255,128,102,.7)" } }), t.committed, ": ", ar ? "640 ر.س" : "SAR 640"))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 12 } }, /* @__PURE__ */ React.createElement(Card, { extra: { flex: 1, display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, fontWeight: 600, color: "#6B7280" } }, t.daysLeft), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 24, fontWeight: 700, color: "#0F172A" } }, t.daysLeftValue)), /* @__PURE__ */ React.createElement(Card, { extra: { flex: 1, display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, fontWeight: 600, color: "#6B7280" } }, t.safeSpend), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 24, fontWeight: 700, color: "#0F172A" } }, t.safeSpendValue))), /* @__PURE__ */ React.createElement("div", { style: { display: "grid", gridTemplateColumns: "repeat(6,1fr)", gap: 8 } }, SHORTCUTS.map((s, idx) => /* @__PURE__ */ React.createElement("div", { key: idx, onClick: () => onNavigate(s.go), className: "press", style: {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 6,
      cursor: "pointer",
      transition: "transform .15s cubic-bezier(.34,1.56,.64,1)"
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 46,
      height: 46,
      borderRadius: 15,
      background: s.bg,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      boxShadow: SHADOW_CHIP,
      animation: `zadFadeUp .45s ease ${idx * 0.06}s both`
    } }, /* @__PURE__ */ React.createElement(Icon, { path: s.icon, size: 20, color: s.fg })), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10, fontWeight: 600, color: "#6B7280", textAlign: "center" } }, s[lang])))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 15, fontWeight: 700, color: "#0F172A" } }, t.insightsTitle), INSIGHTS.map((i, idx) => /* @__PURE__ */ React.createElement("div", { key: idx, className: "press", style: {
      background: "rgba(255,255,255,.85)",
      backdropFilter: "blur(14px)",
      border: "1px solid rgba(0,0,0,.05)",
      borderRadius: 16,
      padding: "14px 16px",
      display: "flex",
      gap: 12,
      alignItems: "flex-start",
      boxShadow: SHADOW_SOFT,
      animation: `zadFadeUp .45s ease ${idx * 0.09}s both`
    } }, /* @__PURE__ */ React.createElement("div", { style: { width: 10, height: 10, borderRadius: "50%", background: kindDot[i.kind], marginTop: 5, flexShrink: 0 } }), /* @__PURE__ */ React.createElement("div", { style: { flex: 1, display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14.5, fontWeight: 600, color: "#0F172A" } }, i[lang].title), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, color: "#6B7280", lineHeight: 1.4 } }, i[lang].subtitle)), /* @__PURE__ */ React.createElement("span", { style: {
      fontSize: 11,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "4px 9px",
      background: kindTagBg[i.kind],
      color: kindDot[i.kind],
      flexShrink: 0,
      whiteSpace: "nowrap"
    } }, kindTag[i.kind])))), /* @__PURE__ */ React.createElement("div", { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 } }, HOME_STATS.map((s, idx) => /* @__PURE__ */ React.createElement(Card, { key: idx, extra: { display: "flex", flexDirection: "column", gap: 4, animation: `zadFadeUp .4s ease ${idx * 0.07}s both` } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, fontWeight: 600, color: "#9CA3AF" } }, s[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 18, fontWeight: 700, color: "#0F172A" } }, ar ? s.valAr : s.valEn)))), /* @__PURE__ */ React.createElement("div", { style: { background: "#052E16", borderRadius: 20, padding: 18, display: "flex", flexDirection: "column", gap: 10 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, fontWeight: 700, color: "#6EE7B7" } }, t.aiSummaryTitle), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, color: "#fff", lineHeight: 1.55 } }, ar ? "صرفك هذا الأسبوع أعلى من المعتاد بـ 12%، لكن مخزونك ودورة راتبك تحت السيطرة." : "Spend this week is 12% above usual, but your stock and salary cycle are under control."), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 8, flexWrap: "wrap", marginTop: 4 } }, aiChips.map((c, i) => /* @__PURE__ */ React.createElement("span", { key: i, style: {
      background: "rgba(255,255,255,.1)",
      color: "#fff",
      fontSize: 11.5,
      fontWeight: 600,
      borderRadius: 9999,
      padding: "6px 12px"
    } }, c)))), /* @__PURE__ */ React.createElement("div", { style: {
      background: "rgba(255,255,255,.7)",
      backdropFilter: "blur(14px)",
      border: "1px solid rgba(0,0,0,.05)",
      borderRadius: 18,
      padding: "16px 18px",
      display: "flex",
      flexDirection: "column",
      gap: 10,
      boxShadow: SHADOW_SOFT
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, fontWeight: 700, color: "#374151" } }, t.tasbihaTitle), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, fontWeight: 700, color: "#0F9B76" } }, gardenPct, "%")), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", justifyContent: "center", padding: "10px 0", position: "relative" } }, /* @__PURE__ */ React.createElement("span", { className: "press", style: { fontSize: 52, lineHeight: 1, display: "inline-block" } }, gardenEmoji), showConfetti && /* @__PURE__ */ React.createElement("div", { style: { position: "absolute", inset: 0, pointerEvents: "none", overflow: "visible" } }, ["🌟", "🌸", "🌟", "🌸", "🌟", "🌸", "🌟", "🌸", "🌟", "🌸"].map((e, i) => /* @__PURE__ */ React.createElement("span", { key: i, style: {
      position: "absolute",
      left: `${10 + i * 8}%`,
      top: "50%",
      fontSize: 16,
      animation: `zadRise ${1.2 + i % 3 * 0.3}s ease-out ${i * 0.05}s forwards`
    } }, e)))), /* @__PURE__ */ React.createElement("div", { style: { height: 8, borderRadius: 99, background: "rgba(15,23,42,.08)", overflow: "hidden" } }, /* @__PURE__ */ React.createElement("div", { style: { width: `${gardenPct}%`, height: "100%", borderRadius: 99, background: "linear-gradient(90deg,#7C3AED,#0F9B76)", transition: "width .4s ease" } })), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, color: "#6B7280" } }, tasbihaCount, " / 100"), /* @__PURE__ */ React.createElement("button", { onClick: onTasbiha, className: "press", style: {
      border: "none",
      background: "#7C3AED",
      color: "#fff",
      fontSize: 13,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "9px 20px",
      cursor: "pointer",
      boxShadow: "0 8px 18px rgba(124,58,237,.3)"
    } }, t.tasbihaTap))), /* @__PURE__ */ React.createElement("div", { style: { background: "#fff", borderRadius: 18, padding: 16, display: "flex", gap: 14, alignItems: "center", boxShadow: SHADOW_CARD } }, /* @__PURE__ */ React.createElement("div", { style: { width: 56, height: 56, borderRadius: 16, background: "#FDF3E1", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 } }, /* @__PURE__ */ React.createElement("svg", { width: "28", height: "28", viewBox: "0 0 24 24", fill: "none", stroke: "#B45309", strokeWidth: "1.6", strokeLinejoin: "round", strokeLinecap: "round" }, /* @__PURE__ */ React.createElement("path", { d: "M6 13a6 6 0 1112 0v1H6v-1z" }), /* @__PURE__ */ React.createElement("path", { d: "M5 14h14v2a1 1 0 01-1 1H6a1 1 0 01-1-1v-2z", fill: "#B45309", stroke: "none" }), /* @__PURE__ */ React.createElement("path", { d: "M9 21h6" }))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 700, color: "#0F172A" } }, t.chefTitle), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, color: "#6B7280", lineHeight: 1.4 } }, t.chefSub))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 15, fontWeight: 700, color: "#0F172A" } }, t.amazonTitle), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 12, overflow: "auto", paddingBottom: 2 } }, AMAZON_DEALS.map((d, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      flexShrink: 0,
      width: 140,
      background: "#fff",
      borderRadius: 16,
      padding: 10,
      display: "flex",
      flexDirection: "column",
      gap: 8,
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: "100%",
      height: 80,
      borderRadius: 10,
      background: "#F1F4F3",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      color: "#9CA3AF",
      fontSize: 11,
      textAlign: "center",
      padding: 6
    } }, d[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, fontWeight: 600, color: "#0F172A", lineHeight: 1.3 } }, d[lang]), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, fontWeight: 800, color: "#B45309" } }, ar ? d.price : d.priceEn), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 9.5, fontWeight: 700, color: "#9CA3AF" } }, t.amazonBadge)))))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 15, fontWeight: 700, color: "#0F172A" } }, t.recentTx), /* @__PURE__ */ React.createElement("span", { onClick: () => onNavigate("budget"), style: { fontSize: 12.5, fontWeight: 600, color: "#064E3B", cursor: "pointer" } }, t.seeAll)), HOME_TX.map((x, idx) => /* @__PURE__ */ React.createElement("div", { key: idx, style: {
      background: "#fff",
      borderRadius: 14,
      padding: "12px 14px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      boxShadow: SHADOW_CARD,
      animation: `zadFadeUp .4s ease ${idx * 0.07}s both`
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 2 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13.5, fontWeight: 600, color: "#0F172A" } }, x[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? x.dateAr : x.dateEn)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 700, color: x.neg ? "#DC5B4B" : "#064E3B" } }, ar ? x.amountAr : x.amountEn)))));
  }
  function KidsHomeScreen({ t, ar, lang, onOpenSheet }) {
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 16, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { background: "linear-gradient(135deg,#7C3AED,#EC4899)", borderRadius: 24, padding: 22, color: "#fff", display: "flex", flexDirection: "column", gap: 14 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, fontWeight: 600, opacity: 0.85 } }, t.kidsBalance), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 38, fontWeight: 800 } }, ar ? "85 ر.س" : "SAR 85"), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 6 } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", fontSize: 11.5, fontWeight: 600 } }, /* @__PURE__ */ React.createElement("span", null, t.kidsGoal), /* @__PURE__ */ React.createElement("span", null, "60%")), /* @__PURE__ */ React.createElement("div", { style: { height: 8, borderRadius: 99, background: "rgba(255,255,255,.25)", overflow: "hidden" } }, /* @__PURE__ */ React.createElement("div", { style: { width: "60%", height: "100%", borderRadius: 99, background: "#fff" } }))), /* @__PURE__ */ React.createElement("button", { onClick: onOpenSheet, style: {
      marginTop: 4,
      border: "none",
      background: "#fff",
      color: "#7C3AED",
      fontWeight: 700,
      fontSize: 13.5,
      borderRadius: 9999,
      padding: 11,
      cursor: "pointer"
    } }, t.kidsAsk)), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 15, fontWeight: 700, color: "#0F172A" } }, t.kidsTasks), KIDS_TASKS.map((k, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 16,
      padding: "13px 15px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, k[lang]), /* @__PURE__ */ React.createElement("span", { style: { background: "rgba(245,158,11,.12)", color: "#B45309", fontSize: 12, fontWeight: 700, borderRadius: 9999, padding: "4px 10px" } }, "🪙 ", k.coins)))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 10 } }, KIDS_BADGES.map((b, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      flex: 1,
      background: "#fff",
      borderRadius: 16,
      padding: "14px 8px",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 6,
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 22 } }, b.emoji), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10.5, fontWeight: 600, color: "#6B7280", textAlign: "center" } }, b[lang])))));
  }
  function InventoryScreen({ t, ar, lang, invCat, onSetInvCat, confirmed, onConfirm }) {
    const catMap = {};
    INV_CATEGORIES.forEach((c) => {
      catMap[c.id] = c;
    });
    const allCats = [{ id: "all", icon: "M4 6h16M4 12h16M4 18h16", bg: "#EDEEF0", fg: "#374151", ar: "الكل", en: "All" }].concat(INV_CATEGORIES);
    const filtered = INVENTORY.filter((it) => invCat === "all" || it.cat === invCat);
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { background: "rgba(220,91,75,.08)", borderRadius: 14, padding: "12px 14px", fontSize: 12.5, fontWeight: 600, color: "#DC5B4B" } }, ar ? "ثلاثة عناصر منخفضة: موز، حليب، خبز" : "3 items low: Bananas, Milk, Bread"), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 8, overflow: "auto", paddingBottom: 2 } }, allCats.map((c, idx) => {
      const on = invCat === c.id;
      return /* @__PURE__ */ React.createElement("button", { key: c.id, onClick: () => onSetInvCat(c.id), style: {
        flexShrink: 0,
        display: "flex",
        alignItems: "center",
        gap: 6,
        border: "none",
        borderRadius: 9999,
        padding: "6px 12px 6px 6px",
        fontSize: 12.5,
        fontWeight: 700,
        cursor: "pointer",
        background: on ? "#064E3B" : "#fff",
        color: on ? "#fff" : "#374151",
        boxShadow: on ? "none" : SHADOW_CHIP,
        animation: `zadFadeUp .35s ease ${idx * 0.04}s both`
      } }, /* @__PURE__ */ React.createElement("div", { style: {
        width: 20,
        height: 20,
        borderRadius: "50%",
        background: on ? "rgba(255,255,255,.2)" : c.bg,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
        animation: on ? "zadCarrotFloat 1.6s ease-in-out infinite" : void 0
      } }, /* @__PURE__ */ React.createElement(Icon, { path: c.icon, size: 13, color: on ? "#fff" : c.fg })), c[lang]);
    })), /* @__PURE__ */ React.createElement("div", { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 } }, filtered.map((it, idx) => {
      const origIdx = INVENTORY.indexOf(it);
      const showConfirm = it.low && !confirmed[origIdx];
      const pct = Math.min(100, it.days / 20 * 100);
      const color = it.days <= 2 ? "#DC5B4B" : it.days <= 6 ? "#B45309" : "#064E3B";
      const cat = catMap[it.cat];
      return /* @__PURE__ */ React.createElement("div", { key: origIdx, style: {
        background: "#fff",
        borderRadius: 16,
        padding: 14,
        display: "flex",
        flexDirection: "column",
        gap: 8,
        boxShadow: SHADOW_CARD,
        animation: `zadFadeUp .4s ease ${idx * 0.05}s both`
      } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "flex-start" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 8 } }, /* @__PURE__ */ React.createElement("div", { style: { width: 30, height: 30, borderRadius: 9, background: cat.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 } }, /* @__PURE__ */ React.createElement(Icon, { path: cat.icon, size: 15, color: cat.fg })), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, it[lang])), showConfirm && /* @__PURE__ */ React.createElement("button", { onClick: () => onConfirm(origIdx), style: {
        border: "none",
        background: "rgba(228,87,46,.12)",
        color: "#DC5B4B",
        fontSize: 10.5,
        fontWeight: 700,
        borderRadius: 9999,
        padding: "3px 8px",
        cursor: "pointer"
      } }, ar ? "تأكيد" : "Confirm")), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, color: "#9CA3AF" } }, cat[lang]), /* @__PURE__ */ React.createElement("div", { style: { height: 5, borderRadius: 99, background: "#F1F4F3", overflow: "hidden" } }, /* @__PURE__ */ React.createElement("div", { style: { width: `${pct}%`, height: "100%", borderRadius: 99, background: color, transition: `width .7s ease ${idx * 0.05}s` } })), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, fontWeight: 600, color: "#6B7280" } }, ar ? `متبقي ${it.days} يوم` : `${it.days}d left`));
    })));
  }
  function ShoppingScreen({ t, ar, lang, onOpenSheet }) {
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { background: "linear-gradient(135deg,#064E3B,#0B6B4E)", borderRadius: 18, padding: "16px 18px", color: "#fff", display: "flex", flexDirection: "column", gap: 8 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, opacity: 0.8 } }, t.shoppingTotal), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 26, fontWeight: 700 } }, ar ? "210 ر.س من أصل 300 ر.س" : "SAR 210 of SAR 300"), /* @__PURE__ */ React.createElement("button", { onClick: onOpenSheet, style: {
      alignSelf: "flex-start",
      marginTop: 4,
      border: "none",
      background: "rgba(255,255,255,.16)",
      color: "#fff",
      fontSize: 12,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "7px 14px",
      cursor: "pointer"
    } }, t.smartFill)), SHOP_ITEMS.map((si, i) => {
      const label = si.p === "high" ? ar ? "حرج" : "Critical" : si.p === "med" ? ar ? "متوسط" : "Medium" : ar ? "منخفض" : "Low";
      const bg = si.p === "high" ? "rgba(220,91,75,.12)" : si.p === "med" ? "rgba(180,83,9,.1)" : "rgba(6,78,59,.08)";
      const color = si.p === "high" ? "#DC5B4B" : si.p === "med" ? "#B45309" : "#064E3B";
      return /* @__PURE__ */ React.createElement("div", { key: i, style: {
        background: "#fff",
        borderRadius: 16,
        padding: "14px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        boxShadow: SHADOW_CARD
      } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, si[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? si.rangeAr : si.rangeEn)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, fontWeight: 700, borderRadius: 9999, padding: "4px 10px", background: bg, color } }, label));
    }));
  }
  function BudgetScreen({ t, ar, lang, onOpenSheet }) {
    const statusLabel = { paid: ar ? "مدفوع" : "Paid", pending: ar ? "مستحق" : "Pending", scheduled: ar ? "مجدول" : "Scheduled" };
    const statusColor = { paid: "#064E3B", pending: "#DC5B4B", scheduled: "#B45309" };
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { onClick: onOpenSheet, style: {
      background: "#064E3B",
      borderRadius: 22,
      padding: 20,
      color: "#fff",
      cursor: "pointer",
      display: "flex",
      flexDirection: "column",
      gap: 6
    } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, fontWeight: 600, color: "rgba(255,255,255,.7)" } }, t.available), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 34, fontWeight: 700 } }, ar ? "↓ 3,240 ر.س" : "↓ SAR 3,240")), /* @__PURE__ */ React.createElement("div", { style: {
      background: "#fff",
      borderRadius: 18,
      padding: "16px 18px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, fontWeight: 600, color: "#6B7280" } }, t.totalObligations), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 19, fontWeight: 700, color: "#B45309" } }, ar ? "3,790 ر.س" : "SAR 3,790")), OBLIGATIONS.map((o, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 18,
      padding: 16,
      display: "flex",
      flexDirection: "column",
      gap: 10,
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 15, fontWeight: 600, color: "#0F172A" } }, o[lang].name), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 15, fontWeight: 700, color: "#0F172A" } }, ar ? o.amountAr : o.amountEn)), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, color: "#9CA3AF" } }, o[lang].due), /* @__PURE__ */ React.createElement("span", { style: {
      fontSize: 11,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "3px 9px",
      background: "rgba(6,78,59,.06)",
      color: statusColor[o.statusKind]
    } }, statusLabel[o.statusKind])), /* @__PURE__ */ React.createElement("div", { style: { height: 6, borderRadius: 99, background: "#F1F4F3", overflow: "hidden" } }, /* @__PURE__ */ React.createElement("div", { style: { width: `${o.pct}%`, height: "100%", borderRadius: 99, background: statusColor[o.statusKind] } })))));
  }
  function AssistantScreen({ t, ar, lang, tab, onSetTab, onOpenSheet }) {
    const tabs = [["overview", ar ? "نظرة عامة" : "Overview"], ["behavior", ar ? "السلوك" : "Behavior"], ["chat", ar ? "المحادثة" : "Chat"]];
    let body = null;
    if (tab === "overview") {
      const max = Math.max.apply(null, SPEND_TREND);
      const min = Math.min.apply(null, SPEND_TREND);
      const w = 280, h = 64;
      const pts = SPEND_TREND.map((v, i) => `${i / (SPEND_TREND.length - 1) * w},${h - (v - min) / (max - min || 1) * (h - 8) - 4}`);
      const sparkPoints = pts.join(" ");
      const sparkFillPoints = `0,${h} ${pts.join(" ")} ${w},${h}`;
      body = /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12 } }, /* @__PURE__ */ React.createElement("div", { style: { background: "#052E16", borderRadius: 20, padding: 18, display: "flex", flexDirection: "column", gap: 10, color: "#fff" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, fontWeight: 700, color: "#6EE7B7" } }, t.spendingPower), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 30, fontWeight: 800 } }, "82%"), /* @__PURE__ */ React.createElement("div", { style: { height: 8, borderRadius: 99, background: "rgba(255,255,255,.15)", overflow: "hidden" } }, /* @__PURE__ */ React.createElement("div", { style: { width: "82%", height: "100%", borderRadius: 99, background: "#6EE7B7" } }))), /* @__PURE__ */ React.createElement("div", { style: { background: "#fff", borderRadius: 18, padding: 16, display: "flex", flexDirection: "column", gap: 10, boxShadow: SHADOW_CARD } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "baseline" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, fontWeight: 700, color: "#0F172A" } }, t.liveSpendTitle), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, fontWeight: 600, color: "#9CA3AF" } }, t.liveSpendSub)), /* @__PURE__ */ React.createElement("svg", { width: "100%", height: "64", viewBox: "0 0 280 64", preserveAspectRatio: "none" }, /* @__PURE__ */ React.createElement("defs", null, /* @__PURE__ */ React.createElement("linearGradient", { id: "sparkGrad", x1: "0", y1: "0", x2: "0", y2: "1" }, /* @__PURE__ */ React.createElement("stop", { offset: "0", stopColor: "#0F9B76", stopOpacity: ".4" }), /* @__PURE__ */ React.createElement("stop", { offset: "1", stopColor: "#0F9B76", stopOpacity: "0" }))), /* @__PURE__ */ React.createElement("polyline", { points: sparkPoints, fill: "none", stroke: "#0F9B76", strokeWidth: "2.5", strokeLinecap: "round", strokeLinejoin: "round" }), /* @__PURE__ */ React.createElement("polygon", { points: sparkFillPoints, fill: "url(#sparkGrad)", opacity: ".5" }))), /* @__PURE__ */ React.createElement("div", { style: { background: "#fff", borderRadius: 18, padding: 16, display: "flex", flexDirection: "column", gap: 12, boxShadow: SHADOW_CARD } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, fontWeight: 700, color: "#0F172A" } }, t.categoryBreakdown), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, CATEGORY_SPEND.map((c, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: { display: "flex", flexDirection: "column", gap: 5 } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, fontWeight: 600, color: "#374151" } }, c[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, fontWeight: 700, color: "#0F172A" } }, ar ? `${c.amt} ر.س` : `SAR ${c.amt}`)), /* @__PURE__ */ React.createElement("div", { style: { height: 7, borderRadius: 99, background: "#F1F4F3", overflow: "hidden" } }, /* @__PURE__ */ React.createElement("div", { style: { width: `${Math.round(c.amt / c.max * 100)}%`, height: "100%", borderRadius: 99, background: c.color, transition: "width .6s ease" } })))))), /* @__PURE__ */ React.createElement("div", { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 } }, ASSIST_OVERVIEW.map((o, i) => /* @__PURE__ */ React.createElement(Card, { key: i, extra: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, fontWeight: 600, color: "#9CA3AF" } }, o[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 18, fontWeight: 700, color: "#0F172A" } }, ar ? o.valAr : o.valEn)))));
    } else if (tab === "behavior") {
      body = /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, ASSIST_BEHAVIOR.map((b, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
        background: "#fff",
        borderRadius: 16,
        padding: "14px 16px",
        display: "flex",
        flexDirection: "column",
        gap: 4,
        boxShadow: SHADOW_CARD
      } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, b[lang].title), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, color: "#6B7280", lineHeight: 1.4 } }, b[lang].body))));
    } else {
      body = /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12 } }, /* @__PURE__ */ React.createElement(ChatThread, { lang }), /* @__PURE__ */ React.createElement("button", { onClick: onOpenSheet, style: {
        alignSelf: "flex-start",
        border: "1px solid rgba(6,78,59,.2)",
        background: "#fff",
        color: "#064E3B",
        fontSize: 12.5,
        fontWeight: 600,
        borderRadius: 9999,
        padding: "8px 14px",
        cursor: "pointer"
      } }, t.quickChip));
    }
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "14px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 6, background: "#fff", borderRadius: 9999, padding: 4, boxShadow: SHADOW_CARD } }, tabs.map((tb) => /* @__PURE__ */ React.createElement(SegBtn, { key: tb[0], label: tb[1], on: tab === tb[0], onClick: () => onSetTab(tb[0]) }))), body);
  }
  function ChatThread({ lang }) {
    return /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12 } }, CHATS.map((m, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: { display: "flex", justifyContent: m.user ? "flex-end" : "flex-start" } }, /* @__PURE__ */ React.createElement("div", { style: {
      maxWidth: "78%",
      padding: "11px 15px",
      borderRadius: 16,
      fontSize: 14,
      lineHeight: 1.45,
      background: m.user ? "#064E3B" : "#fff",
      color: m.user ? "#fff" : "#0F172A",
      boxShadow: m.user ? "none" : "0 2px 8px rgba(15,23,42,.05)"
    } }, m[lang]))));
  }
  function SubsScreen({ ar, lang }) {
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { background: "linear-gradient(135deg,#064E3B,#0B6B4E)", borderRadius: 16, padding: "14px 16px", color: "#fff", fontSize: 13, fontWeight: 600 } }, ar ? "إجمالي الاشتراكات الشهرية: 294 ر.س" : "Total monthly subscriptions: SAR 294"), SUBS_ITEMS.map((s, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 16,
      padding: "14px 16px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      boxShadow: SHADOW_CARD,
      [ar ? "borderRight" : "borderLeft"]: `3px solid ${s.color}`
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, s[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? s.renewAr : s.renewEn)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 700, color: "#0F172A" } }, ar ? s.priceAr : s.priceEn))));
  }
  function FamilyScreen({ ar, lang, tab, onSetTab }) {
    const tabs = [["chat", ar ? "المحادثة" : "Chat"], ["tasks", ar ? "المهام" : "Tasks"], ["members", ar ? "الأعضاء" : "Members"]];
    let body = null;
    if (tab === "chat") {
      body = /* @__PURE__ */ React.createElement(ChatThread, { lang });
    } else if (tab === "members") {
      body = /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, FAMILY_MEMBERS.map((m, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
        background: "#fff",
        borderRadius: 16,
        padding: "14px 16px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        boxShadow: SHADOW_CARD
      } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, m[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? m.roleAr : m.roleEn)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, fontWeight: 700, color: "#064E3B", background: "rgba(6,78,59,.08)", borderRadius: 9999, padding: "4px 10px" } }, ar ? m.statAr : m.statEn))));
    } else {
      body = /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10 } }, FAMILY_TASKS.map((f, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
        background: "#fff",
        borderRadius: 16,
        padding: "13px 15px",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        boxShadow: SHADOW_CARD
      } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 2 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, f[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? f.whoAr : f.whoEn)), /* @__PURE__ */ React.createElement("span", { style: {
        fontSize: 11,
        fontWeight: 700,
        borderRadius: 9999,
        padding: "4px 10px",
        background: f.done ? "rgba(6,78,59,.08)" : "rgba(180,83,9,.1)",
        color: f.done ? "#064E3B" : "#B45309"
      } }, f.done ? ar ? "تم" : "Done" : ar ? "قيد التنفيذ" : "Pending"))));
    }
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "14px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 6, overflow: "auto" } }, tabs.map((tb) => /* @__PURE__ */ React.createElement(SegBtn, { key: tb[0], label: tb[1], on: tab === tb[0], onClick: () => onSetTab(tb[0]) }))), body);
  }
  function PharmacyScreen({ t, ar, lang }) {
    const kindLabel = { ok: ar ? "منتظم" : "On track", low: ar ? "كمية منخفضة" : "Low stock", confirm: ar ? "يحتاج تأكيد" : "Needs confirm" };
    const kindColor = { ok: "#064E3B", low: "#B45309", confirm: "#DC5B4B" };
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 10 } }, /* @__PURE__ */ React.createElement(Card, { extra: { flex: 1 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, color: "#9CA3AF", fontWeight: 600 } }, t.adherence), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 20, fontWeight: 700, color: "#064E3B" } }, "91%")), /* @__PURE__ */ React.createElement(Card, { extra: { flex: 1 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11, color: "#9CA3AF", fontWeight: 600 } }, t.monthlyCost), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 20, fontWeight: 700, color: "#0F172A" } }, ar ? "180 ر.س" : "SAR 180"))), PHARM_ITEMS.map((p, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 16,
      padding: "14px 16px",
      display: "flex",
      flexDirection: "column",
      gap: 8,
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, p[lang]), /* @__PURE__ */ React.createElement("span", { style: {
      fontSize: 11,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "4px 10px",
      background: "rgba(6,78,59,.06)",
      color: kindColor[p.kind]
    } }, kindLabel[p.kind])), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12, color: "#9CA3AF" } }, ar ? p.memberAr : p.memberEn, " · ", ar ? p.doseAr : p.doseEn))));
  }
  function MaintenanceScreen({ ar, lang }) {
    const kindColor = { ok: "#064E3B", soon: "#B45309", over: "#DC5B4B" };
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, MAINT_ITEMS.map((m, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 16,
      padding: "14px 16px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, m[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? m.warrantyAr : m.warrantyEn)), /* @__PURE__ */ React.createElement("span", { style: {
      fontSize: 11,
      fontWeight: 700,
      borderRadius: 9999,
      padding: "4px 10px",
      background: "rgba(6,78,59,.06)",
      color: kindColor[m.kind]
    } }, ar ? m.dueAr : m.dueEn))));
  }
  function DealsScreen({ t, ar, lang }) {
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: { background: "rgba(6,78,59,.06)", borderRadius: 14, padding: "12px 14px", fontSize: 12, color: "#064E3B", lineHeight: 1.5 } }, t.dealsDisclaimer), DEALS_STORES.map((d, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 16,
      padding: "14px 16px",
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 4 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, d[lang]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, ar ? d.typeAr : d.typeEn)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, fontWeight: 600, color: "#064E3B" } }, d.dist))));
  }
  function ProfileScreen({ t, ar, lang, kidsMode, onToggleKids, onNavigate }) {
    const name = ar ? "سارة العتيبي" : "Sarah Al-Otaibi";
    const initial = ar ? "س" : "S";
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 14, animation: "zadFadeUp .4s ease both" } }, /* @__PURE__ */ React.createElement("div", { style: {
      background: "linear-gradient(135deg,#064E3B,#0B6B4E)",
      borderRadius: 22,
      padding: 20,
      color: "#fff",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 8
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 64,
      height: 64,
      borderRadius: "50%",
      background: "rgba(255,255,255,.15)",
      border: "2px solid rgba(255,255,255,.4)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontSize: 22,
      fontWeight: 700
    } }, initial), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 16, fontWeight: 700 } }, name), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, opacity: 0.7 } }, "#ZAD-2481")), /* @__PURE__ */ React.createElement("div", { style: { background: "#fff", borderRadius: 18, padding: 4, boxShadow: SHADOW_CARD } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 2 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, t.kidsToggleLabel), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 11.5, color: "#9CA3AF" } }, t.kidsToggleSub)), /* @__PURE__ */ React.createElement("div", { onClick: onToggleKids, style: {
      width: 44,
      height: 26,
      borderRadius: 9999,
      background: kidsMode ? "#7C3AED" : "#E5E7EB",
      position: "relative",
      cursor: "pointer",
      transition: "background .2s",
      flexShrink: 0
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 20,
      height: 20,
      borderRadius: "50%",
      background: "#fff",
      position: "absolute",
      top: 3,
      [ar ? "right" : "left"]: kidsMode ? 21 : 3,
      transition: "all .2s",
      boxShadow: "0 1px 3px rgba(0,0,0,.2)"
    } })))), /* @__PURE__ */ React.createElement("div", { style: { background: "#fff", borderRadius: 18, boxShadow: SHADOW_CARD, overflow: "hidden" } }, PROFILE_MENU.map((p, i) => /* @__PURE__ */ React.createElement("div", { key: i, onClick: () => p.go && onNavigate(p.go), style: {
      padding: "14px 16px",
      fontSize: 14,
      fontWeight: 600,
      color: "#0F172A",
      borderBottom: "1px solid rgba(0,0,0,.05)",
      cursor: "pointer"
    } }, p[lang]))));
  }
  function NotificationsScreen({ lang }) {
    const kindColor = { info: "#064E3B", warn: "#B45309", danger: "#DC5B4B" };
    return /* @__PURE__ */ React.createElement("div", { style: { padding: "18px 20px 130px", display: "flex", flexDirection: "column", gap: 12, animation: "zadFadeUp .4s ease both" } }, NOTIFS.map((n, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      background: "#fff",
      borderRadius: 16,
      padding: "14px 16px",
      display: "flex",
      gap: 12,
      alignItems: "flex-start",
      boxShadow: SHADOW_CARD
    } }, /* @__PURE__ */ React.createElement("div", { style: { width: 10, height: 10, borderRadius: "50%", background: kindColor[n.kind], marginTop: 5, flexShrink: 0 } }), /* @__PURE__ */ React.createElement("div", { style: { flex: 1, display: "flex", flexDirection: "column", gap: 3 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, n[lang].title), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, color: "#6B7280" } }, n[lang].body)))));
  }
  const NAV_WRAP = {
    position: "absolute",
    left: 16,
    right: 16,
    bottom: 22,
    height: 64,
    borderRadius: 9999,
    background: "rgba(255,255,255,.72)",
    backdropFilter: "blur(16px) saturate(180%)",
    boxShadow: "0 8px 24px rgba(15,23,42,.14)",
    display: "flex",
    alignItems: "center",
    zIndex: 20
  };
  function KidsNav({ t, onNavigate }) {
    return /* @__PURE__ */ React.createElement("div", { style: { ...NAV_WRAP, justifyContent: "space-around", padding: "0 8px" } }, /* @__PURE__ */ React.createElement("button", { onClick: () => onNavigate("home"), style: { border: "none", background: "transparent", display: "flex", flexDirection: "column", alignItems: "center", gap: 3, cursor: "pointer", padding: "4px 20px" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10.5, fontWeight: 700, color: "#064E3B" } }, t.tabHome)), /* @__PURE__ */ React.createElement("button", { onClick: () => onNavigate("family"), style: { border: "none", background: "transparent", display: "flex", flexDirection: "column", alignItems: "center", gap: 3, cursor: "pointer", padding: "4px 20px" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10.5, fontWeight: 600, color: "#9CA3AF" } }, t.tabFamily)));
  }
  function FullNav({ t, screen, onNavigate, onOpenMore, onOpenCamera }) {
    const ic = (on) => on ? "#064E3B" : "#9CA3AF";
    const lb = (on) => ({ fontSize: 10.5, fontWeight: on ? 700 : 600, color: on ? "#064E3B" : "#9CA3AF" });
    const btn = { border: "none", background: "transparent", display: "flex", flexDirection: "column", alignItems: "center", gap: 3, cursor: "pointer", padding: "4px 6px" };
    return /* @__PURE__ */ React.createElement("div", { style: { ...NAV_WRAP, justifyContent: "space-around", padding: "0 6px", border: ".5px solid rgba(0,0,0,.06)" } }, /* @__PURE__ */ React.createElement("button", { onClick: () => onNavigate("home"), style: btn }, /* @__PURE__ */ React.createElement("svg", { width: "20", height: "18", viewBox: "0 0 22 20", style: { stroke: ic(screen === "home") } }, /* @__PURE__ */ React.createElement("path", { d: "M2 9L11 2l9 7v9a1 1 0 01-1 1h-5v-6H8v6H3a1 1 0 01-1-1V9z", fill: "none", strokeWidth: "1.8", strokeLinejoin: "round" })), /* @__PURE__ */ React.createElement("span", { style: lb(screen === "home") }, t.tabHome)), /* @__PURE__ */ React.createElement("button", { onClick: () => onNavigate("inventory"), style: btn }, /* @__PURE__ */ React.createElement("svg", { width: "19", height: "18", viewBox: "0 0 21 20", style: { stroke: ic(screen === "inventory") } }, /* @__PURE__ */ React.createElement("rect", { x: "1", y: "6", width: "19", height: "13", rx: "1.5", fill: "none", strokeWidth: "1.8" }), /* @__PURE__ */ React.createElement("path", { d: "M1 6l3.5-4.5h12L20 6", fill: "none", strokeWidth: "1.8", strokeLinejoin: "round" })), /* @__PURE__ */ React.createElement("span", { style: lb(screen === "inventory") }, t.tabInventory)), /* @__PURE__ */ React.createElement("button", { onClick: onOpenCamera, style: {
      border: "none",
      background: "#064E3B",
      width: 46,
      height: 46,
      borderRadius: "50%",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      cursor: "pointer",
      boxShadow: "0 6px 16px rgba(6,78,59,.35)",
      marginTop: -16
    } }, /* @__PURE__ */ React.createElement("svg", { width: "20", height: "20", viewBox: "0 0 24 24", fill: "none", stroke: "#fff", strokeWidth: "1.8" }, /* @__PURE__ */ React.createElement("rect", { x: "3", y: "7", width: "18", height: "13", rx: "2" }), /* @__PURE__ */ React.createElement("circle", { cx: "12", cy: "13.5", r: "3.5" }), /* @__PURE__ */ React.createElement("path", { d: "M8 7l1.5-2.5h5L16 7" }))), /* @__PURE__ */ React.createElement("button", { onClick: () => onNavigate("assistant"), style: btn }, /* @__PURE__ */ React.createElement("svg", { width: "19", height: "18", viewBox: "0 0 21 20", style: { stroke: ic(screen === "assistant") } }, /* @__PURE__ */ React.createElement("path", { d: "M1 3.5A2 2 0 013 1.5h15a2 2 0 012 2V13a2 2 0 01-2 2H8l-5 4v-4H3a2 2 0 01-2-2V3.5z", fill: "none", strokeWidth: "1.8", strokeLinejoin: "round" })), /* @__PURE__ */ React.createElement("span", { style: lb(screen === "assistant") }, t.tabAssistant)), /* @__PURE__ */ React.createElement("button", { onClick: onOpenMore, style: btn }, /* @__PURE__ */ React.createElement("svg", { width: "18", height: "18", viewBox: "0 0 20 20" }, /* @__PURE__ */ React.createElement("circle", { cx: "4", cy: "10", r: "1.7", fill: "#9CA3AF" }), /* @__PURE__ */ React.createElement("circle", { cx: "10", cy: "10", r: "1.7", fill: "#9CA3AF" }), /* @__PURE__ */ React.createElement("circle", { cx: "16", cy: "10", r: "1.7", fill: "#9CA3AF" })), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10.5, fontWeight: 600, color: "#9CA3AF" } }, t.more)));
  }
  function Drawer({ t, ar, lang, open, screen, onClose, onNavigate }) {
    return /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement(Overlay, { open, onClick: onClose, z: 45 }), /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      top: 0,
      bottom: 0,
      [ar ? "right" : "left"]: 0,
      width: "78%",
      maxWidth: 300,
      background: "#fff",
      boxShadow: ar ? "-8px 0 30px rgba(15,23,42,.18)" : "8px 0 30px rgba(15,23,42,.18)",
      transform: `translateX(${open ? "0" : ar ? "100%" : "-100%"})`,
      transition: "transform .3s cubic-bezier(.32,.72,0,1)",
      zIndex: 46,
      display: "flex",
      flexDirection: "column"
    } }, /* @__PURE__ */ React.createElement("div", { style: { padding: "56px 20px 18px", display: "flex", alignItems: "center", gap: 10, borderBottom: "1px solid rgba(0,0,0,.06)" } }, /* @__PURE__ */ React.createElement("div", { style: { width: 34, height: 34, borderRadius: 10, background: "#E6F4EC", display: "flex", alignItems: "center", justifyContent: "center" } }, /* @__PURE__ */ React.createElement(CarrotSm, null)), /* @__PURE__ */ React.createElement("span", { style: { fontWeight: 700, fontSize: 18, color: "#064E3B" } }, t.appName)), /* @__PURE__ */ React.createElement("div", { style: { padding: 10, display: "flex", flexDirection: "column", gap: 2, overflow: "auto" } }, DRAWER_ITEMS.map((d, i) => {
      const active = screen === d.go;
      return /* @__PURE__ */ React.createElement("div", { key: i, onClick: () => onNavigate(d.go), style: {
        display: "flex",
        alignItems: "center",
        gap: 14,
        padding: "13px 14px",
        borderRadius: 12,
        cursor: "pointer",
        background: active ? "rgba(6,78,59,.08)" : "transparent"
      } }, /* @__PURE__ */ React.createElement(Icon, { path: d.icon, size: 19, color: active ? "#064E3B" : "#6B7280" }), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14.5, fontWeight: active ? 700 : 600, color: active ? "#064E3B" : "#374151" } }, d[lang]));
    }))));
  }
  function MoreSheet({ lang, open, onClose, onNavigate }) {
    return /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement(Overlay, { open, onClick: onClose, z: 40 }), /* @__PURE__ */ React.createElement("div", { style: { ...SHEET_STYLE, transform: `translateY(${open ? "0" : "110%"})`, zIndex: 41 } }, /* @__PURE__ */ React.createElement(Grabber, null), /* @__PURE__ */ React.createElement("div", { style: { padding: "10px 12px 24px", display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 } }, MORE_ITEMS.map((m, idx) => /* @__PURE__ */ React.createElement("div", { key: idx, onClick: () => onNavigate(m.go), className: "press", style: {
      background: "#F9FAFB",
      borderRadius: 14,
      padding: "16px 12px",
      display: "flex",
      flexDirection: "column",
      gap: 10,
      cursor: "pointer",
      animation: `zadFadeUp .35s ease ${idx * 0.04}s both`
    } }, /* @__PURE__ */ React.createElement("div", { style: { width: 38, height: 38, borderRadius: 12, background: m.bg, display: "flex", alignItems: "center", justifyContent: "center" } }, /* @__PURE__ */ React.createElement(Icon, { path: m.icon, size: 18, color: m.fg })), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 12.5, fontWeight: 600, color: "#0F172A" } }, m[lang]))))));
  }
  function CameraSheet({ t, open, onClose }) {
    return /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement(Overlay, { open, onClick: onClose, z: 50 }), /* @__PURE__ */ React.createElement("div", { style: { ...SHEET_STYLE, transform: `translateY(${open ? "0" : "110%"})`, zIndex: 51 } }, /* @__PURE__ */ React.createElement(Grabber, null), /* @__PURE__ */ React.createElement("div", { style: { padding: "14px 22px 28px", display: "flex", flexDirection: "column", gap: 14 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 18, fontWeight: 700, color: "#0F172A" } }, t.cameraTitle), /* @__PURE__ */ React.createElement("div", { style: {
      height: 150,
      borderRadius: 16,
      background: "#0F172A",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      color: "rgba(255,255,255,.4)",
      fontSize: 13
    } }, t.cameraPreview), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 10 } }, /* @__PURE__ */ React.createElement("button", { style: { flex: 1, border: "none", background: "#064E3B", color: "#fff", fontSize: 13.5, fontWeight: 700, borderRadius: 12, padding: 12, cursor: "pointer" } }, t.scanInventory), /* @__PURE__ */ React.createElement("button", { style: { flex: 1, border: "none", background: "#F1F4F3", color: "#0F172A", fontSize: 13.5, fontWeight: 700, borderRadius: 12, padding: 12, cursor: "pointer" } }, t.scanReceipt)), /* @__PURE__ */ React.createElement("button", { onClick: onClose, style: { border: "none", background: "transparent", color: "#6B7280", fontSize: 13.5, fontWeight: 600, cursor: "pointer" } }, t.cancel))));
  }
  function WhySheet({ t, ar, open, onClose }) {
    return /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement(Overlay, { open, onClick: onClose, z: 30 }), /* @__PURE__ */ React.createElement("div", { style: { ...SHEET_STYLE, transform: `translateY(${open ? "0" : "110%"})`, zIndex: 31 } }, /* @__PURE__ */ React.createElement(Grabber, null), /* @__PURE__ */ React.createElement("div", { style: { padding: "14px 22px 28px", display: "flex", flexDirection: "column", gap: 16 } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 18, fontWeight: 700, color: "#0F172A" } }, t.sheetTitle), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12 } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 8 } }, /* @__PURE__ */ React.createElement("div", { style: { width: 8, height: 8, borderRadius: "50%", background: "#F4A93B" } }), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, color: "#374151" } }, t.spent)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, ar ? "1,120 ر.س" : "SAR 1,120")), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 8 } }, /* @__PURE__ */ React.createElement("div", { style: { width: 8, height: 8, borderRadius: "50%", background: "#FF8066" } }), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, color: "#374151" } }, t.committed)), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 14, fontWeight: 600, color: "#0F172A" } }, ar ? "640 ر.س" : "SAR 640")), /* @__PURE__ */ React.createElement("div", { style: { height: 1, background: "rgba(0,0,0,.06)" } }), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 13, color: "#6B7280", lineHeight: 1.55 } }, t.sheetBody)), /* @__PURE__ */ React.createElement("button", { onClick: onClose, style: {
      border: "none",
      background: "#064E3B",
      color: "#fff",
      fontSize: 15,
      fontWeight: 600,
      borderRadius: 14,
      padding: 14,
      cursor: "pointer"
    } }, t.sheetClose))));
  }
  const TASBIHA_KEY = "zad-tasbiha-garden-standalone";
  function ZADApp() {
    const [lang, setLang] = useState("ar");
    const [screen, setScreen] = useState("home");
    const [showSplash, setShowSplash] = useState(true);
    const [kidsMode, setKidsMode] = useState(false);
    const [sheetOpen, setSheetOpen] = useState(false);
    const [moreOpen, setMoreOpen] = useState(false);
    const [cameraOpen, setCameraOpen] = useState(false);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [invCat, setInvCat] = useState("all");
    const [confirmed, setConfirmed] = useState({});
    const [assistantTab, setAssistantTab] = useState("overview");
    const [familyTab, setFamilyTab] = useState("chat");
    const [tasbihaCount, setTasbihaCount] = useState(0);
    const [showConfetti, setShowConfetti] = useState(false);
    const t = STRINGS[lang];
    const ar = lang === "ar";
    useEffect(() => {
      const today = (/* @__PURE__ */ new Date()).toDateString();
      try {
        const saved = JSON.parse(localStorage.getItem(TASBIHA_KEY) || "null");
        if (saved && saved.date === today) setTasbihaCount(saved.count);
        else localStorage.setItem(TASBIHA_KEY, JSON.stringify({ date: today, count: 0 }));
      } catch (e) {
      }
    }, []);
    const handleTasbiha = () => {
      const goal = 100;
      const next = Math.min(goal, tasbihaCount + 1);
      try {
        localStorage.setItem(TASBIHA_KEY, JSON.stringify({ date: (/* @__PURE__ */ new Date()).toDateString(), count: next }));
      } catch (e) {
      }
      if (navigator.vibrate) navigator.vibrate(12);
      const justCompleted = next === goal && tasbihaCount < goal;
      setTasbihaCount(next);
      if (justCompleted) {
        setShowConfetti(true);
        setTimeout(() => setShowConfetti(false), 2200);
      }
    };
    const goTo = (s) => {
      setScreen(s);
      setMoreOpen(false);
    };
    const goToFromDrawer = (s) => {
      setScreen(s);
      setDrawerOpen(false);
    };
    const shown = kidsMode && screen !== "family" ? "home" : screen;
    let body = null;
    if (shown === "home" && kidsMode) body = /* @__PURE__ */ React.createElement(KidsHomeScreen, { t, ar, lang, onOpenSheet: () => setSheetOpen(true) });
    else if (shown === "home") body = /* @__PURE__ */ React.createElement(HomeScreen, { t, ar, lang, onOpenSheet: () => setSheetOpen(true), onNavigate: goTo, tasbihaCount, onTasbiha: handleTasbiha, showConfetti });
    else if (shown === "inventory") body = /* @__PURE__ */ React.createElement(InventoryScreen, { t, ar, lang, invCat, onSetInvCat: setInvCat, confirmed, onConfirm: (i) => setConfirmed({ ...confirmed, [i]: true }) });
    else if (shown === "shopping") body = /* @__PURE__ */ React.createElement(ShoppingScreen, { t, ar, lang, onOpenSheet: () => setSheetOpen(true) });
    else if (shown === "budget") body = /* @__PURE__ */ React.createElement(BudgetScreen, { t, ar, lang, onOpenSheet: () => setSheetOpen(true) });
    else if (shown === "assistant") body = /* @__PURE__ */ React.createElement(AssistantScreen, { t, ar, lang, tab: assistantTab, onSetTab: setAssistantTab, onOpenSheet: () => setSheetOpen(true) });
    else if (shown === "subs") body = /* @__PURE__ */ React.createElement(SubsScreen, { ar, lang });
    else if (shown === "family") body = /* @__PURE__ */ React.createElement(FamilyScreen, { ar, lang, tab: familyTab, onSetTab: setFamilyTab });
    else if (shown === "pharmacy") body = /* @__PURE__ */ React.createElement(PharmacyScreen, { t, ar, lang });
    else if (shown === "maintenance") body = /* @__PURE__ */ React.createElement(MaintenanceScreen, { ar, lang });
    else if (shown === "deals") body = /* @__PURE__ */ React.createElement(DealsScreen, { t, ar, lang });
    else if (shown === "profile") body = /* @__PURE__ */ React.createElement(ProfileScreen, { t, ar, lang, kidsMode, onToggleKids: () => {
      setKidsMode(!kidsMode);
      setScreen("home");
    }, onNavigate: goTo });
    else if (shown === "notifications") body = /* @__PURE__ */ React.createElement(NotificationsScreen, { lang });
    else body = /* @__PURE__ */ React.createElement(HomeScreen, { t, ar, lang, onOpenSheet: () => setSheetOpen(true), onNavigate: goTo, tasbihaCount, onTasbiha: handleTasbiha, showConfetti });
    return /* @__PURE__ */ React.createElement("div", { style: { minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", padding: 40 } }, /* @__PURE__ */ React.createElement("div", { dir: ar ? "rtl" : "ltr", style: {
      width: 402,
      height: 874,
      borderRadius: 48,
      overflow: "hidden",
      position: "relative",
      background: "#F2F2F7",
      boxShadow: "0 40px 80px rgba(0,0,0,.18), 0 0 0 1px rgba(0,0,0,.12)",
      display: "flex",
      flexDirection: "column",
      fontFamily: "-apple-system, 'SF Pro Text', system-ui, sans-serif"
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      top: 11,
      left: "50%",
      transform: "translateX(-50%)",
      width: 126,
      height: 37,
      borderRadius: 24,
      background: "#000",
      zIndex: 60
    } }), showSplash ? /* @__PURE__ */ React.createElement(Splash, { t, ar, onEnter: () => setShowSplash(false), onToggleLang: () => setLang(ar ? "en" : "ar") }) : /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement(
      Header,
      {
        t,
        screen,
        kidsMode,
        onToggleLang: () => setLang(ar ? "en" : "ar"),
        onExitKids: () => {
          setKidsMode(false);
          setScreen("profile");
        },
        onOpenDrawer: () => setDrawerOpen(true)
      }
    ), /* @__PURE__ */ React.createElement("div", { id: "scroll", style: {
      flex: 1,
      overflowY: "auto",
      position: "relative",
      background: "linear-gradient(165deg,#F4F5F7 0%,#ECEEF1 45%,#E9ECEF 100%)"
    } }, body), kidsMode ? /* @__PURE__ */ React.createElement(KidsNav, { t, onNavigate: goTo }) : /* @__PURE__ */ React.createElement(FullNav, { t, screen, onNavigate: goTo, onOpenMore: () => setMoreOpen(true), onOpenCamera: () => setCameraOpen(true) }), /* @__PURE__ */ React.createElement(Drawer, { t, ar, lang, open: drawerOpen, screen, onClose: () => setDrawerOpen(false), onNavigate: goToFromDrawer }), /* @__PURE__ */ React.createElement(MoreSheet, { lang, open: moreOpen, onClose: () => setMoreOpen(false), onNavigate: goTo }), /* @__PURE__ */ React.createElement(CameraSheet, { t, open: cameraOpen, onClose: () => setCameraOpen(false) }), /* @__PURE__ */ React.createElement(WhySheet, { t, ar, open: sheetOpen, onClose: () => setSheetOpen(false) }))));
  }
  ReactDOM.createRoot(document.getElementById("root")).render(/* @__PURE__ */ React.createElement(ZADApp, null));
})();
