export interface ConversationProfile {
  locale: string;
  instruction: string;
}

const PROFILES: Record<string, ConversationProfile> = {
  SA: { locale: "ar-SA", instruction: "تحدث بلهجة سعودية/خليجية طبيعية ودودة من غير مبالغة في العبارات المحلية." },
  EG: { locale: "ar-EG", instruction: "تحدث باللهجة المصرية العامية الطبيعية الودودة من غير مبالغة أو ألفاظ مصطنعة." },
  AE: { locale: "ar-AE", instruction: "تحدث باللهجة الإماراتية الخليجية الطبيعية الودودة." },
  KW: { locale: "ar-KW", instruction: "تحدث باللهجة الكويتية الطبيعية الودودة." },
  QA: { locale: "ar-QA", instruction: "تحدث باللهجة القطرية الخليجية الطبيعية الودودة." },
  BH: { locale: "ar-BH", instruction: "تحدث باللهجة البحرينية الخليجية الطبيعية الودودة." },
  OM: { locale: "ar-OM", instruction: "تحدث باللهجة العمانية الخليجية الطبيعية الودودة." },
  JO: { locale: "ar-JO", instruction: "تحدث باللهجة الأردنية الشامية الطبيعية الودودة." },
  LB: { locale: "ar-LB", instruction: "تحدث باللهجة اللبنانية الطبيعية الودودة." },
  IQ: { locale: "ar-IQ", instruction: "تحدث باللهجة العراقية الطبيعية الودودة." },
  SY: { locale: "ar-SY", instruction: "تحدث باللهجة السورية الشامية الطبيعية الودودة." },
  YE: { locale: "ar-YE", instruction: "تحدث باللهجة اليمنية الطبيعية الودودة." },
  PS: { locale: "ar-PS", instruction: "تحدث باللهجة الفلسطينية الشامية الطبيعية الودودة." },
  LY: { locale: "ar-LY", instruction: "تحدث باللهجة الليبية الطبيعية الودودة." },
  SD: { locale: "ar-SD", instruction: "تحدث باللهجة السودانية الطبيعية الودودة." },
  MA: { locale: "ar-MA", instruction: "تحدث بالدارجة المغربية الطبيعية الودودة." },
  TN: { locale: "ar-TN", instruction: "تحدث بالدارجة التونسية الطبيعية الودودة." },
  DZ: { locale: "ar-DZ", instruction: "تحدث بالدارجة الجزائرية الطبيعية الودودة." },
  TR: { locale: "tr-TR", instruction: "Tamamen doğal, samimi ve günlük Türkçe konuş. Arapça kullanma." },
};

const COUNTRY_ALIASES: Record<string, string> = {
  "السعودية": "SA", "مصر": "EG", "الإمارات": "AE", "الامارات": "AE",
  "الكويت": "KW", "قطر": "QA", "البحرين": "BH", "عمان": "OM", "عُمان": "OM",
  "الأردن": "JO", "الاردن": "JO", "لبنان": "LB", "العراق": "IQ", "سوريا": "SY",
  "اليمن": "YE", "فلسطين": "PS", "ليبيا": "LY", "السودان": "SD", "المغرب": "MA",
  "تونس": "TN", "الجزائر": "DZ", "تركيا": "TR",
};

export function conversationProfile(country: unknown): ConversationProfile {
  const raw = String(country ?? "").trim();
  const code = COUNTRY_ALIASES[raw] ?? raw.toUpperCase();
  return PROFILES[code] ?? {
    locale: "auto",
    instruction: "طابق لغة المستخدم ولهجته الظاهرة في رسالته، واستعمل لغة عربية محايدة إذا لم تتضح.",
  };
}

export function voiceModeInstruction(enabled: boolean): string {
  return enabled
    ? "هذه مكالمة صوتية: رد بجمل قصيرة سهلة السماع، من دون Markdown أو قوائم طويلة، واترك مساحة طبيعية للمستخدم كي يقاطع أو يكمل."
    : "هذه محادثة مكتوبة: كن موجزاً ومنظماً، ويمكنك استخدام تنسيق بسيط حين يساعد القراءة.";
}
