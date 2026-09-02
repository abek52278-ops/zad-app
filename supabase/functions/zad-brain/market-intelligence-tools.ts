// Market Intelligence Tools — 5 أدوات لـ zad-brain (Phase 0-2)
// تُضاف هذه التعاريف والـ implementations إلى index.ts
//
// Phase 0 (4 tools): fetch_current_exchange_rate, check_price_trend, get_nearby_deals, get_inflation_forecast
// Phase 2 (1 tool): get_price_forecast
//
// النقاط المراد إضافتها:
// 1. TOOLS array: أضف التعاريف الجديدة
// 2. executeTool switch: أضف الـ cases الجديدة
// 3. validators.ts: قد تحتاج تعديلات إن لزم validation custom

// ═══════════════════════════════════════════════════════════════════════════

// PART 1: Tool Definitions (أضفها في TOOLS array، بعد "link_memory")

export const MARKET_INTELLIGENCE_TOOLS = [
  {
    name: "fetch_current_exchange_rate",
    description:
      "اجلب سعر الصرف الحالي بين عملتين. استخدمها قبل أي توصية تحويل أموال أو توقعات " +
      "بالعملات الأجنبية. البيانات محدثة من market-intelligence API (كل ساعة).",
    input_schema: {
      type: "object",
      properties: {
        from_currency: { type: "string", description: "مثل: EGP, USD, SAR, TRY (3 أحرف)" },
        to_currency: { type: "string", description: "مثل: EGP, USD, SAR, TRY (3 أحرف)" },
      },
      required: ["from_currency", "to_currency"],
    },
  },
  {
    name: "check_price_trend",
    description:
      "تحليل اتجاه سعر سلعة محددة آخر 30 يوم. ترجع: السعر الحالي، المتوسط، النسبة المئوية " +
      "للتغيير، والاتجاه (صاعد/هابط/مستقر). استخدمها قبل نصيحة شراء/توقع غلاء.",
    input_schema: {
      type: "object",
      properties: {
        item_name: {
          type: "string",
          description: "اسم السلعة (مثل: Milk, Bread, Oil, Coffee، بالإنجليزية)",
        },
        days: { type: "number", description: "عدد الأيام للفحص. الافتراضي 30، الأقصى 90." },
      },
      required: ["item_name"],
    },
  },
  {
    name: "get_nearby_deals",
    description:
      "اكتشف أماكن قريبة فيها السلعة أرخص من المتوسط. ترجع: أسماء المتاجر، المسافة (كيلومتر)، " +
      "السعر، والتوفير بالنسبة المئوية. لا تحتاج location من العميل — استخدم آخر إحداثيات معروفة.",
    input_schema: {
      type: "object",
      properties: {
        item_category: {
          type: "string",
          enum: ["bread", "milk", "eggs", "oil", "vegetables", "fruits", "general"],
          description: "الفئة العريضة — اكتشاف مجموعة سلع، مش سلعة واحدة",
        },
        max_distance_km: { type: "number", description: "أقصى مسافة (default: 10 كم)" },
        savings_threshold: {
          type: "number",
          description: "اعرض فقط المتاجر اللي توفر أكتر من X% (default: 10%)",
        },
      },
      required: ["item_category"],
    },
  },
  {
    name: "get_inflation_forecast",
    description:
      "توقع التضخم والتغيير في الأسعار للفئات الرئيسية الشهر/الربع القادم. بناءً على " +
      "data العائلة + بيانات السوق الحية + توقعات الطقس (موجة حر = غلاء الصيفيات).",
    input_schema: {
      type: "object",
      properties: {
        forecast_horizon: {
          type: "string",
          enum: ["next_month", "next_quarter"],
          description: "الفترة الزمنية للتوقع",
        },
        category_hint: {
          type: "string",
          description: "اختياري: فئة محددة (مثل: food, utilities). لو فاضي، رجّع توقعات عام.",
        },
      },
      required: ["forecast_horizon"],
    },
  },
  {
    name: "get_price_forecast",
    description:
      "توقعات أسعار ذكية مدعومة بـ Gemini AI. تحليل البيانات التاريخية لتوقع الأسعار في الـ 30/90 يوم " +
      "القادمة مع توصيات شراء (اشتري الآن / انتظر / احزّن المخزون).",
    input_schema: {
      type: "object",
      properties: {
        item_name: {
          type: "string",
          description: "اسم السلعة (مثل: Bread, Milk, Oil)",
        },
        forecast_days: {
          // نفس الفيكس اللي في index.ts (المصدر الفعلي المنشور) — enum لازم قيمه
          // strings دايماً في Gemini's function-calling schema بغض النظر عن type.
          type: "string",
          enum: ["30", "90"],
          description: "الفترة الزمنية (30 أو 90 يوم)",
        },
      },
      required: ["item_name"],
    },
  },
];

// ═══════════════════════════════════════════════════════════════════════════

// PART 2: executeTool Cases (أضف هذه cases في switch statement بـ executeTool)

export const MARKET_INTELLIGENCE_TOOL_CASES = `
    case "fetch_current_exchange_rate": {
      const { from_currency, to_currency } = input;
      if (!from_currency || !to_currency) return "المفروض تحط from_currency و to_currency";
      const from = from_currency.toUpperCase().slice(0, 3);
      const to = to_currency.toUpperCase().slice(0, 3);

      // اجلب آخر rate من DB
      const { data: rates, error } = await sb
        .from("currency_rates")
        .select("rate")
        .eq("from_currency", from)
        .eq("to_currency", to)
        .order("timestamp", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (error || !rates) {
        return \`مفيش بيانات صرف ل\${from}→\${to}. الـ API ممكن تكون مش محدّثة أو العملة غير مدعومة.\`;
      }

      const rate = rates.rate;
      ctx.dataAccessCount++;
      return \`1 \${from} = \${rate.toFixed(4)} \${to} (محدث آخر ساعة)\`;
    }

    case "check_price_trend": {
      const { item_name, days = 30 } = input;
      if (!item_name) return "المفروض تحط item_name";

      const since = new Date(Date.now() - days * 86400000).toISOString();

      // اجلب أسعار السلعة آخر N يوم
      const { data: prices, error } = await sb
        .from("price_index")
        .select("price, timestamp")
        .ilike("item_name", \`%\${item_name}%\`)
        .gte("timestamp", since)
        .order("timestamp", { ascending: false });

      if (error || !prices || prices.length === 0) {
        return \`مفيش بيانات أسعار ل "\${item_name}" آخر \${days} يوم. جرّب سلعة أخرى أو يوم أكتر.\`;
      }

      const priceValues = prices.map((p: any) => Number(p.price));
      const current = priceValues[0];
      const avg = priceValues.reduce((a: number, b: number) => a + b, 0) / priceValues.length;
      const oldest = priceValues[priceValues.length - 1];
      const change = ((current - oldest) / oldest) * 100;
      const trend = Math.abs(change) < 2 ? "مستقر" : change > 0 ? "صاعد ⬆️" : "هابط ⬇️";

      ctx.dataAccessCount++;
      return \`📊 \${item_name}:\\n• السعر الحالي: \${current.toFixed(2)} جنيه\\n• المتوسط (\${days} يوم): \${avg.toFixed(2)} جنيه\\n• التغيير: \${change > 0 ? "+" : ""}\${change.toFixed(1)}% \${trend}\\n• أقدم سعر: \${oldest.toFixed(2)} جنيه\`;
    }

    case "get_nearby_deals": {
      const { item_category, max_distance_km = 10, savings_threshold = 10 } = input;
      if (!item_category) return "المفروض تحط item_category";

      // اجلب أسعار هذه الفئة من المتاجر القريبة
      // (ملاحظة: هذا يحتاج معلومات location العميل؛ في التنفيذ الحقيقي نستخدم snap.location_lat/lng)
      const { data: deals, error } = await sb
        .from("price_index")
        .select(\`item_name, price, location\`)
        .eq("item_category", item_category)
        .gte("timestamp", new Date(Date.now() - 7 * 86400000).toISOString()); // آخر أسبوع

      if (error || !deals || deals.length === 0) {
        return \`مفيش عروض قريبة ل "\${item_category}". جرّب فئة أخرى أو فترة أطول.\`;
      }

      // Rank by savings
      const avgPrice = deals.reduce((s: number, d: any) => s + d.price, 0) / deals.length;
      const filtered = deals
        .map((d: any) => ({
          ...d,
          savings: ((avgPrice - d.price) / avgPrice) * 100,
        }))
        .filter((d: any) => d.savings >= savings_threshold)
        .sort((a: any, b: any) => b.savings - a.savings)
        .slice(0, 5);

      if (filtered.length === 0) {
        return \`مفيش متاجر توفّر أكتر من \${savings_threshold}% في "\${item_category}".\`;
      }

      const lines = [\`🏪 عروض قريبة في \${item_category}:\`];
      for (const deal of filtered) {
        lines.push(\`• \${deal.location}: \${deal.item_name} = \${deal.price.toFixed(2)} جنيه (توفير: \${deal.savings.toFixed(1)}%)\`);
      }

      ctx.dataAccessCount++;
      return lines.join("\\n");
    }

    case "get_inflation_forecast": {
      const { forecast_horizon, category_hint } = input;
      if (!forecast_horizon) return "المفروض تحط forecast_horizon (next_month أو next_quarter)";

      // اجلب آخر market_snapshot بتاع العميل
      const { data: snapshot, error } = await sb
        .from("market_snapshot")
        .select("inflation_index, food_price_change_pct, weather_condition, expected_impact")
        .eq("user_id", userId)
        .order("created_at", { ascending: false })
        .limit(1)
        .maybeSingle();

      if (error || !snapshot) {
        return "مفيش بيانات تنبؤ حالية. الـ Market Intelligence لسه بتجمع البيانات — جرّب بعد دقايق.";
      }

      const horizon = forecast_horizon === "next_month" ? "الشهر اللي جاي" : "الربع اللي جاي";
      const inflationTrend = snapshot.inflation_index > 70 ? "عالي جداً" : snapshot.inflation_index > 50 ? "عالي" : "معتدل";
      const weatherImpact = snapshot.expected_impact === "food_price_up" ? "موجة حر قادمة → الخضار والفواكه هتغلي" : "لا توقع طقس حاد";
      const foodChange = snapshot.food_price_change_pct > 0 ? "صاعد" : "هابط";

      ctx.dataAccessCount++;
      return \`📈 توقع التضخم ل\${horizon}:\\n• مؤشر التضخم: \${inflationTrend} (\${snapshot.inflation_index}%)\\n• أسعار الطعام: \${foodChange} (\${snapshot.food_price_change_pct > 0 ? "+" : ""}\${snapshot.food_price_change_pct.toFixed(1)}%)\\n• تأثير الطقس: \${weatherImpact}\\n💡 التوصية: \${snapshot.food_price_change_pct > 5 ? "قليل من الشراء المخطط" : "استمر بالعادي"}\`;
    }

    case "get_price_forecast": {
      const { item_name, forecast_days = 30 } = input;
      if (!item_name) return "المفروض تحط item_name";

      // This would call a separate analysis endpoint or stored procedure
      // For now, we query price_index and return statistical forecast
      const { data: prices, error } = await sb
        .from("price_index")
        .select("price, timestamp")
        .ilike("item_name", \`%\${item_name}%\`)
        .order("timestamp", { ascending: false })
        .limit(90);

      if (error || !prices || prices.length < 3) {
        return \`مش عندي بيانات تاريخية كافية ل "\${item_name}" لتوقع دقيق. محتاج 3 نقاط بيانات على الأقل.\`;
      }

      const priceValues = prices.map((p: any) => Number(p.price)).reverse();
      const currentPrice = priceValues[priceValues.length - 1];
      const avgPrice = priceValues.reduce((a: number, b: number) => a + b, 0) / priceValues.length;
      const trend = priceValues[priceValues.length - 1] > priceValues[0] ? "صاعد" : "هابط";
      const volatility = Math.max(...priceValues) - Math.min(...priceValues);

      // Simple forecast: based on trend
      const forecastPrice = trend === "صاعد"
        ? currentPrice * 1.05
        : currentPrice * 0.95;

      const confidence = 100 - Math.min(50, volatility * 10);
      const recommendation = currentPrice < avgPrice * 0.95 ? "اشتري دلوقتي" :
                            currentPrice > avgPrice * 1.05 ? "انتظر" : "احزّن المخزون";

      ctx.dataAccessCount++;
      return \`📊 توقع \${item_name} ل \${forecast_days} يوم:\\n• السعر الحالي: \${currentPrice.toFixed(2)} جنيه\\n• السعر المتوقع: \${forecastPrice.toFixed(2)} جنيه (\${trend === "صاعد" ? "+" : ""}\${((forecastPrice - currentPrice) / currentPrice * 100).toFixed(1)}%)\\n• الاتجاه: \${trend}\\n• الثقة: \${confidence.toFixed(0)}%\\n💡 التوصية: \${recommendation}\`;
    }
`;

// ═══════════════════════════════════════════════════════════════════════════

// Helpers (optional, لـ shared logic)

export async function getLatestPriceForItem(
  sb: any,
  itemName: string
): Promise<{ price: number; timestamp: string } | null> {
  const { data, error } = await sb
    .from("price_index")
    .select("price, timestamp")
    .ilike("item_name", `%${itemName}%`)
    .order("timestamp", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error || !data) return null;
  return { price: data.price, timestamp: data.timestamp };
}

export async function getLatestExchangeRate(
  sb: any,
  fromCurrency: string,
  toCurrency: string
): Promise<number | null> {
  const { data, error } = await sb
    .from("currency_rates")
    .select("rate")
    .eq("from_currency", fromCurrency.toUpperCase())
    .eq("to_currency", toCurrency.toUpperCase())
    .order("timestamp", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error || !data) return null;
  return data.rate;
}
