var INSIGHTS = [
  { ar:{title:'تم رصد دورة راتب جديدة',subtitle:'استلمنا راتب هذا الشهر وأعدنا حساب الميزانية تلقائياً.'}, en:{title:'New salary cycle detected',subtitle:"This month's salary landed — your budget was recalculated."}, kind:'info' },
  { ar:{title:'تحقق من مطابقة النقد',subtitle:'هناك فرق صغير بين المصروف الفعلي والمسجل.'}, en:{title:'Cash reconciliation check-in',subtitle:'A small gap between actual and recorded spend.'}, kind:'warn' },
  { ar:{title:'تنبيه نفاد مخزون: حليب',subtitle:'الكمية المتبقية تكفي ليوم واحد فقط.'}, en:{title:'Inventory alert: Milk',subtitle:'Remaining stock covers only 1 more day.'}, kind:'danger' },
]

var OBLIGATIONS = [
  { ar:{name:'الإيجار',due:'يستحق بعد 4 أيام'}, en:{name:'Rent',due:'Due in 4 days'}, amountAr:'2,400 ر.س', amountEn:'SAR 2,400', pct:0, statusKind:'pending' },
  { ar:{name:'الإنترنت والاتصالات',due:'مدفوع'}, en:{name:'Internet & mobile',due:'Paid'}, amountAr:'260 ر.س', amountEn:'SAR 260', pct:100, statusKind:'paid' },
  { ar:{name:'قسط السيارة',due:'يستحق بعد 11 يوم'}, en:{name:'Car installment',due:'Due in 11 days'}, amountAr:'980 ر.س', amountEn:'SAR 980', pct:30, statusKind:'scheduled' },
  { ar:{name:'عضوية النادي',due:'مدفوع'}, en:{name:'Gym membership',due:'Paid'}, amountAr:'150 ر.س', amountEn:'SAR 150', pct:100, statusKind:'paid' },
]

var INV_CATEGORIES = [
  { id:'fruit', icon:'M12 8c-3 0-5.5 2.7-5.5 6.5S9 21 12 21s5.5-2.8 5.5-6.5S15 8 12 8z M12 8c0-2 1-4 3-5', ar:'فاكهة', en:'Fruit', bg:'#FCEAEA', fg:'#DC5B4B' },
  { id:'veg', icon:'M12 3c4 0 7 3 7 8 0 6-4 10-7 10s-7-4-7-10c0-5 3-8 7-8zM12 3v4', ar:'خضار', en:'Vegetables', bg:'#E9F5E9', fg:'#3CA06E' },
  { id:'dairy', icon:'M9 2h6l1 4-1 2v11a2 2 0 01-2 2h-2a2 2 0 01-2-2V8l-1-2 1-4z', ar:'ألبان', en:'Dairy', bg:'#EAF2FB', fg:'#2563EB' },
  { id:'bakery', icon:'M4 12c0-4 3.5-7 8-7s8 3 8 7-3.5 6-8 6-8-2-8-6z M6 15c1 2 3 3 6 3s5-1 6-3', ar:'خبز ومخبوزات', en:'Bakery', bg:'#FBF1E3', fg:'#B45309' },
  { id:'drinks', icon:'M12 2c3 4 5 6.5 5 10a5 5 0 01-10 0c0-3.5 2-6 5-10z', ar:'مياه ومشروبات', en:'Drinks', bg:'#E7F5F8', fg:'#0891B2' },
  { id:'sweets', icon:'M7 13a5 5 0 0110 0c0 1-.5 2-2 2.5V17a3 3 0 01-6 0v-1.5C7.5 15 7 14 7 13z', ar:'حلويات', en:'Sweets', bg:'#F1E9E3', fg:'#C2703D' },
  { id:'cleaning', icon:'M8 2l8 8-7 7a3 3 0 01-4-4l3-3M17 13l4 4-2 2-4-4', ar:'أدوات تنظيف', en:'Cleaning', bg:'#EEF0F3', fg:'#374151' },
]

var INVENTORY = [
  { ar:'تفاح', en:'Apples', cat:'fruit', days:6, low:false },
  { ar:'موز', en:'Bananas', cat:'fruit', days:2, low:true },
  { ar:'طماطم', en:'Tomatoes', cat:'veg', days:4, low:false },
  { ar:'بروكلي', en:'Broccoli', cat:'veg', days:5, low:false },
  { ar:'حليب', en:'Milk', cat:'dairy', days:1, low:true },
  { ar:'جبنة', en:'Cheese', cat:'dairy', days:9, low:false },
  { ar:'خبز', en:'Bread', cat:'bakery', days:2, low:true },
  { ar:'كرواسون', en:'Croissants', cat:'bakery', days:3, low:false },
  { ar:'مياه معدنية', en:'Mineral water', cat:'drinks', days:20, low:false },
  { ar:'عصير برتقال', en:'Orange juice', cat:'drinks', days:4, low:false },
  { ar:'شوكولاتة', en:'Chocolate', cat:'sweets', days:15, low:false },
  { ar:'منظف أرضيات', en:'Floor cleaner', cat:'cleaning', days:18, low:false },
  { ar:'صابون', en:'Soap', cat:'cleaning', days:5, low:false },
]

var CHATS = [
  { ar:'مرحباً، لاحظت أنك أنفقت أكثر من المعتاد هذا الأسبوع في التسوق.', en:'Hi — you spent more than usual on groceries this week.', user:false },
  { ar:'لماذا تغير الرقم المتاح؟', en:'Why did the available amount change?', user:true },
  { ar:'انخفض المتاح 320 ر.س بسبب مصروف جديد وحجز جزء من الراتب لالتزام قادم.', en:'Available dropped SAR 320 from a new expense plus a reserved upcoming obligation.', user:false },
  { ar:'تمام، شكراً لك', en:'Got it, thanks', user:true },
]

var TICKER = [
  { ar:'أرز', en:'Rice', delta:'+2%', up:true },
  { ar:'دجاج', en:'Chicken', delta:'-1%', up:false },
  { ar:'بنزين', en:'Fuel', delta:'0%', up:null },
]

var SHORTCUTS = [
  { icon:'M3 9l1-5h16l1 5M4 9h16v10a1 1 0 01-1 1H5a1 1 0 01-1-1V9zM9 13h6', bg:'#E3F5EC', fg:'#0B6B4E', ar:'المخزون', en:'Inventory', go:'inventory' },
  { icon:'M3 4h2l2.4 12.4a2 2 0 002 1.6h8.2a2 2 0 002-1.6L21 8H6M9 21a1 1 0 100-2 1 1 0 000 2zM18 21a1 1 0 100-2 1 1 0 000 2z', bg:'#FCEEE3', fg:'#C2703D', ar:'التسوق', en:'Shopping', go:'shopping' },
  { icon:'M7 8a3 3 0 100-6 3 3 0 000 6zM17 8a3 3 0 100-6 3 3 0 000 6zM1 21v-2a4 4 0 014-4h4a4 4 0 014 4v2M14 15h2a4 4 0 014 4v2', bg:'#F1EAFB', fg:'#7C3AED', ar:'العائلة', en:'Family', go:'family' },
  { icon:'M2 6a2 2 0 012-2h16a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V6zM2 10h20M6 15h4', bg:'#E8F1FC', fg:'#2563EB', ar:'الاشتراكات', en:'Subs', go:'subs' },
  { icon:'M9 2h6v5h5v6h-5v5H9v-5H4V7h5V2z', bg:'#FCE8ED', fg:'#DC5B4B', ar:'الصيدلية', en:'Pharmacy', go:'pharmacy' },
  { icon:'M14.7 6.3a4 4 0 01-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 015.4-5.4l-2.6 2.6-2-2 2.6-2.6z', bg:'#FDF3E1', fg:'#B45309', ar:'الصيانة', en:'Maintenance', go:'maintenance' },
]

var HOME_STATS = [
  { ar:'قوة الإنفاق', en:'Spending power', valAr:'82%', valEn:'82%' },
  { ar:'الصحة المالية', en:'Health score', valAr:'74/100', valEn:'74/100' },
  { ar:'صرف الشهر', en:'Monthly spend', valAr:'4,120 ر.س', valEn:'SAR 4,120' },
  { ar:'اتجاه 7 أيام', en:'7-day trend', valAr:'↓ 6%', valEn:'↓ 6%' },
]

var HOME_TX = [
  { ar:'سوبرماركت العائلة', en:'Family Supermarket', dateAr:'اليوم', dateEn:'Today', amountAr:'-240 ر.س', amountEn:'-SAR 240', neg:true },
  { ar:'راتب يوليو', en:'July salary', dateAr:'قبل يومين', dateEn:'2 days ago', amountAr:'+9,000 ر.س', amountEn:'+SAR 9,000', neg:false },
  { ar:'اشتراك نتفليكس', en:'Netflix', dateAr:'قبل 3 أيام', dateEn:'3 days ago', amountAr:'-45 ر.س', amountEn:'-SAR 45', neg:true },
]

var SHOP_ITEMS = [
  { ar:'حليب', en:'Milk', rangeAr:'8–10 ر.س', rangeEn:'SAR 8–10', p:'high' },
  { ar:'خبز', en:'Bread', rangeAr:'4–6 ر.س', rangeEn:'SAR 4–6', p:'med' },
  { ar:'فيتامين د', en:'Vitamin D', rangeAr:'35–45 ر.س', rangeEn:'SAR 35–45', p:'high' },
  { ar:'منظف أطباق', en:'Dish soap', rangeAr:'12–15 ر.س', rangeEn:'SAR 12–15', p:'low' },
]

var ASSIST_OVERVIEW = [
  { ar:'أيام حتى النفاد', en:'Stress-test days', valAr:'19', valEn:'19' },
  { ar:'التنبؤ الشهر القادم', en:'Next-month forecast', valAr:'4,600 ر.س', valEn:'SAR 4,600' },
  { ar:'أعلى فئة صرف', en:'Top category', valAr:'بقالة', valEn:'Groceries' },
  { ar:'اشتراكات نشطة', en:'Active subs', valAr:'5', valEn:'5' },
]

var ASSIST_BEHAVIOR = [
  { ar:{title:'ذروة إنفاق يوم الخميس',body:'تنفق أكثر بـ 30% كل خميس مقارنة بباقي الأسبوع.'}, en:{title:'Thursday spend spike',body:'You spend 30% more every Thursday than the rest of the week.'} },
  { ar:{title:'رادار التضخم الشخصي',body:'متوسط أسعار البقالة عندك ارتفع 4% هذا الشهر.'}, en:{title:'Personal inflation radar',body:'Your average grocery prices rose 4% this month.'} },
  { ar:{title:'توقيت شراء ذكي',body:'الأفضل شراء الإلكترونيات بعد 5 أيام من الآن.'}, en:{title:'Smart buying timing',body:'Best time to buy electronics is in 5 days.'} },
]

var SUBS_ITEMS = [
  { ar:'نتفليكس', en:'Netflix', priceAr:'45 ر.س', priceEn:'SAR 45', renewAr:'يتجدد بعد 6 أيام', renewEn:'Renews in 6 days', color:'#B45309' },
  { ar:'STC TV', en:'STC TV', priceAr:'99 ر.س', priceEn:'SAR 99', renewAr:'يتجدد بعد 12 يوم', renewEn:'Renews in 12 days', color:'#064E3B' },
  { ar:'اشتراك الجيم', en:'Gym', priceAr:'150 ر.س', priceEn:'SAR 150', renewAr:'يتجدد بعد يومين', renewEn:'Renews in 2 days', color:'#DC5B4B' },
]

var FAMILY_MEMBERS = [
  { ar:'سارة', en:'Sarah', roleAr:'أم · مشرف', roleEn:'Mom · Admin', statAr:'3 مهام', statEn:'3 tasks' },
  { ar:'أحمد', en:'Ahmed', roleAr:'أب · مشرف', roleEn:'Dad · Admin', statAr:'5 مهام', statEn:'5 tasks' },
  { ar:'يوسف', en:'Youssef', roleAr:'ابن', roleEn:'Son', statAr:'هدف 60%', statEn:'Goal 60%' },
]

var FAMILY_TASKS = [
  { ar:'ترتيب الغرفة', en:'Tidy room', whoAr:'يوسف', whoEn:'Youssef', done:false },
  { ar:'سقاية النبات', en:'Water plants', whoAr:'سارة', whoEn:'Sarah', done:true },
  { ar:'شراء بقالة', en:'Buy groceries', whoAr:'أحمد', whoEn:'Ahmed', done:false },
]

var PHARM_ITEMS = [
  { ar:'باراسيتامول', en:'Paracetamol', memberAr:'يوسف', memberEn:'Youssef', doseAr:'3 مرات يومياً', doseEn:'3x daily', kind:'ok' },
  { ar:'فيتامين د', en:'Vitamin D', memberAr:'سارة', memberEn:'Sarah', doseAr:'مرة يومياً', doseEn:'1x daily', kind:'low' },
  { ar:'أنسولين', en:'Insulin', memberAr:'أحمد', memberEn:'Ahmed', doseAr:'مرتين يومياً', doseEn:'2x daily', kind:'confirm' },
]

var MAINT_ITEMS = [
  { ar:'مكيف الصالة', en:'Living room AC', warrantyAr:'الضمان حتى 2027', warrantyEn:'Warranty until 2027', dueAr:'مستحق قريباً', dueEn:'Due soon', kind:'soon' },
  { ar:'الثلاجة', en:'Refrigerator', warrantyAr:'الضمان منتهي', warrantyEn:'Warranty expired', dueAr:'متأخر', dueEn:'Overdue', kind:'over' },
  { ar:'غسالة الملابس', en:'Washing machine', warrantyAr:'الضمان حتى 2026', warrantyEn:'Warranty until 2026', dueAr:'على الموعد', dueEn:'On schedule', kind:'ok' },
]

var DEALS_STORES = [
  { ar:'صيدلية النهدي', en:'Nahdi Pharmacy', typeAr:'صيدلية', typeEn:'Pharmacy', dist:'650m' },
  { ar:'بنده', en:'Panda', typeAr:'سوبرماركت', typeEn:'Supermarket', dist:'1.1km' },
  { ar:'أسواق التميمي', en:'Tamimi Markets', typeAr:'سوبرماركت', typeEn:'Supermarket', dist:'1.8km' },
]

var NOTIFS = [
  { ar:{title:'تم رصد دورة راتب جديدة',body:'أعدنا حساب ميزانيتك تلقائياً.'}, en:{title:'New salary cycle',body:'Your budget was recalculated.'}, kind:'info' },
  { ar:{title:'نفاد مخزون قريب',body:'الحليب يكفي ليوم واحد.'}, en:{title:'Low stock soon',body:'Milk covers 1 more day.'}, kind:'danger' },
  { ar:{title:'اشتراك يتجدد',body:'نتفليكس يتجدد بعد 6 أيام.'}, en:{title:'Subscription renewing',body:'Netflix renews in 6 days.'}, kind:'warn' },
]

var KIDS_TASKS = [
  { ar:'ترتيب الغرفة', en:'Tidy room', coins:10 },
  { ar:'حفظ سورة', en:'Memorize verse', coins:20 },
  { ar:'مساعدة في المطبخ', en:'Help in kitchen', coins:15 },
]

var KIDS_BADGES = [
  { emoji:'🔥', ar:'مثابرة', en:'Streak' },
  { emoji:'💰', ar:'موفر', en:'Saver' },
  { emoji:'⭐', ar:'نجم', en:'Star' },
]

var AMAZON_DEALS = [
  { ar:'قدر ضغط كهربائي', en:'Electric pressure cooker', price:'189 ر.س', priceEn:'SAR 189' },
  { ar:'فيتامينات عائلية', en:'Family vitamins pack', price:'85 ر.س', priceEn:'SAR 85' },
  { ar:'منظم مخزون مطبخ', en:'Kitchen storage organizer', price:'49 ر.س', priceEn:'SAR 49' },
]

var SPEND_TREND = [320,410,260,480,390,520,300]

var CATEGORY_SPEND = [
  { ar:'بقالة', en:'Groceries', amt:1240, max:1600, color:'#0F9B76' },
  { ar:'مواصلات', en:'Transport', amt:480, max:1600, color:'#2563EB' },
  { ar:'فواتير', en:'Bills', amt:690, max:1600, color:'#B45309' },
  { ar:'صحة', en:'Health', amt:210, max:1600, color:'#DC5B4B' },
  { ar:'ترفيه', en:'Entertainment', amt:150, max:1600, color:'#7C3AED' },
]

var MORE_ITEMS = [
  { icon:'M3 4h2l2.4 12.4a2 2 0 002 1.6h8.2a2 2 0 002-1.6L21 8H6M9 21a1 1 0 100-2 1 1 0 000 2zM18 21a1 1 0 100-2 1 1 0 000 2z', bg:'#FCEEE3', fg:'#C2703D', ar:'التسوق', en:'Shopping', go:'shopping' },
  { icon:'M7 8a3 3 0 100-6 3 3 0 000 6zM17 8a3 3 0 100-6 3 3 0 000 6zM1 21v-2a4 4 0 014-4h4a4 4 0 014 4v2M14 15h2a4 4 0 014 4v2', bg:'#F1EAFB', fg:'#7C3AED', ar:'العائلة', en:'Family', go:'family' },
  { icon:'M1 10h4.5v9H1zM7.7 5h4.5v14H7.7zM14.5 1H19v18h-4.5z', bg:'#E8F1FC', fg:'#2563EB', ar:'الميزانية', en:'Budget', go:'budget' },
  { icon:'M2 6a2 2 0 012-2h16a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V6zM2 10h20M6 15h4', bg:'#E8F1FC', fg:'#2563EB', ar:'الاشتراكات', en:'Subscriptions', go:'subs' },
  { icon:'M9 2h6v5h5v6h-5v5H9v-5H4V7h5V2z', bg:'#FCE8ED', fg:'#DC5B4B', ar:'الصيدلية', en:'Pharmacy', go:'pharmacy' },
  { icon:'M14.7 6.3a4 4 0 01-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 015.4-5.4l-2.6 2.6-2-2 2.6-2.6z', bg:'#FDF3E1', fg:'#B45309', ar:'الصيانة', en:'Maintenance', go:'maintenance' },
  { icon:'M12 2a7 7 0 00-7 7c0 5.3 7 13 7 13s7-7.7 7-13a7 7 0 00-7-7zM12 12a3 3 0 100-6 3 3 0 000 6z', bg:'#E3F5EC', fg:'#0B6B4E', ar:'عروض قريبة', en:'Nearby deals', go:'deals' },
  { icon:'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z', bg:'#EEF0F3', fg:'#374151', ar:'حسابي', en:'Profile', go:'profile' },
]

var DRAWER_ITEMS = [
  { icon:'M2 9L11 2l9 7v9a1 1 0 01-1 1h-5v-6H8v6H3a1 1 0 01-1-1V9z', ar:'الرئيسية', en:'Home', go:'home' },
  { icon:'M3 9l1-5h16l1 5M4 9h16v10a1 1 0 01-1 1H5a1 1 0 01-1-1V9zM9 13h6', ar:'المخزون', en:'Inventory', go:'inventory' },
  { icon:'M1 3.5A2 2 0 013 1.5h15a2 2 0 012 2V13a2 2 0 01-2 2H8l-5 4v-4H3a2 2 0 01-2-2V3.5z', ar:'عقل زاد', en:'Zad Mind', go:'assistant' },
  { icon:'M2 6a2 2 0 012-2h16a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V6zM2 10h20M6 15h4', ar:'الاشتراكات', en:'Subscriptions', go:'subs' },
  { icon:'M3 4h2l2.4 12.4a2 2 0 002 1.6h8.2a2 2 0 002-1.6L21 8H6M9 21a1 1 0 100-2 1 1 0 000 2zM18 21a1 1 0 100-2 1 1 0 000 2z', ar:'التسوق', en:'Shopping', go:'shopping' },
  { icon:'M7 8a3 3 0 100-6 3 3 0 000 6zM17 8a3 3 0 100-6 3 3 0 000 6zM1 21v-2a4 4 0 014-4h4a4 4 0 014 4v2M14 15h2a4 4 0 014 4v2', ar:'العائلة', en:'Family', go:'family' },
  { icon:'M1 10h4.5v9H1zM7.7 5h4.5v14H7.7zM14.5 1H19v18h-4.5z', ar:'الميزانية', en:'Budget', go:'budget' },
  { icon:'M9 2h6v5h5v6h-5v5H9v-5H4V7h5V2z', ar:'الصيدلية', en:'Pharmacy', go:'pharmacy' },
  { icon:'M14.7 6.3a4 4 0 01-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 015.4-5.4l-2.6 2.6-2-2 2.6-2.6z', ar:'الصيانة', en:'Maintenance', go:'maintenance' },
  { icon:'M12 2a7 7 0 00-7 7c0 5.3 7 13 7 13s7-7.7 7-13a7 7 0 00-7-7zM12 12a3 3 0 100-6 3 3 0 000 6z', ar:'عروض قريبة', en:'Nearby Deals', go:'deals' },
  { icon:'M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z', ar:'حسابي', en:'Profile', go:'profile' },
  { icon:'M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9zM13.7 21a2 2 0 01-3.4 0', ar:'الإشعارات', en:'Notifications', go:'notifications' },
]

var PROFILE_MENU = [
  { ar:'تعديل الملف الشخصي', en:'Edit profile' },
  { ar:'إدارة العائلة', en:'Manage family' },
  { ar:'الإشعارات', en:'Notifications', go:'notifications' },
  { ar:'استيراد كشف حساب بنكي', en:'Import bank statement' },
  { ar:'الدعم والمساعدة', en:'Help & support' },
  { ar:'تسجيل الخروج', en:'Logout' },
]