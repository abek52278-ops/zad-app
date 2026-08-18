# 🎙️ Genesis-Style Real-time Arabic Voice Assistant
### Powered by Gemini 2.0 Multimodal Live API, WebSockets & Model Context Protocol (MCP)

هذا المشروع يوفر تطبيقا كاملاً لبناء مساعد صوتي تفاعلي ذكي وسريع باللغة العربية (مثل Genesis)، يعتمد على:
1. **Gemini 2.0 Multimodal Live API**: لتبادل الصوت المباشر ثنائي الاتجاه (Bidirectional Streaming) بحد أدنى من التأخير (Latency).
2. **System Prompt عالي المرونة**: للتكيف الفوري مع اللهجة المحلية (مصري، خليجي، شامي)، بسلاسة وعفوية بدون تكلف.
3. **Model Context Protocol (MCP)**: لربط واستدعاء الأدوات الخارجية (Google Calendar, SQLite, Zad Pantry, Tasks) تلقائياً عبر الـ Function Calling.
4. **واجهة تجريبية تفاعلية**: صفحة ويب تدعم تسجيل الصوت PCM 16kHz وتشغيل الرد الصوتي PCM 24kHz مباشرة عبر WebSockets.

---

## 📂 الهيكل العام للمشروع

```
gemini-voice-assistant/
├── package.json               # التبعيات (ws, @google/genai, @modelcontextprotocol/sdk)
├── tsconfig.json              # إعدادات TypeScript
├── .env.example               # نموذج متغيرات البيئة
├── README.md                  # توثيق المشروع
├── src/
│   ├── index.ts               # سيرفر HTTP & WebSocket الرئيسي
│   ├── geminiLiveClient.ts    # مدير الاتصال بـ Gemini Multimodal Live API عبر WebSocket
│   ├── mcpManager.ts          # ربط وتنفيذ أدوات MCP و Function Calling
│   ├── systemPrompt.ts        # توجيه النظام والتعليمات الصوتية باللغة العربية
│   └── audioUtils.ts          # أدوات تشفير وفك ترميز صوت PCM
└── public/
    └── index.html             # واجهة اختبار متصفح تفاعلية مع مرئيات وميكروفون
```

---

## ⚡ خطوات التشغيل والاستخدام

### 1. تثبيت الحزم (Install Dependencies)
```bash
cd gemini-voice-assistant
npm install
```

### 2. إعداد مفتاح الـ API
قم بإنشاء ملف `.env` ووضع مفتاح Gemini الخاص بك:
```bash
cp .env.example .env
```
أضف المفتاح داخل `.env`:
```env
GEMINI_API_KEY=AIzaSy...
GEMINI_MODEL=gemini-2.0-flash
GEMINI_VOICE=Puck
PORT=8080
```

### 3. تشغيل الخادم (Start Server)
```bash
npm run dev
```

افتح المتصفح على العنوان: `http://localhost:8080`
واضغط على رمز الميكروفون لبدء المحادثة الصوتية الفورية!

---

## 🛠️ دمج سيرفرات MCP الخارجية

يمكنك ربط أي سيرفر MCP جاهز (مثل SQLite أو Google Calendar) بتعديل `src/mcpManager.ts` أو عبر متغيرات البيئة. 
يتم تحويل أدوات MCP تلقائياً إلى صيغة `functionDeclarations` وإرسالها لـ Gemini Live API، وعند طلب النموذج لتنفيذ أداة يتم تمريرها لسيرفر MCP وإعادة النتيجة في ذات الجلسة الصوتية.
