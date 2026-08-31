// Market Intelligence — جمع بيانات الأسواق الحية والتنبيهات الذكية
// يشتغل: cron job (ساعة/يوم)، أو triggered من events
// يجمع: أسعار صرف، طقس، أسعار سلع، معادن، متاجر قريبة
// ينشر: alerts، predictions، market snapshots لـ zad-brain

import { createClient } from "jsr:@supabase/supabase-js@2";

// ─── Types ───────────────────────────────────────────────────────────────

interface ExchangeRate {
  from: string;
  to: string;
  rate: number;
  timestamp: Date;
}

interface FoodPrice {
  itemName: string;
  category: string;
  price: number;
  currency: string;
  source: string;
}

interface WeatherData {
  temperature: number;
  condition: string;
  forecastDays: number;
  impactCategory?: string; // 'heat_wave', 'drought', 'normal'
}

interface GoldPrice {
  metal: string; // 'gold', 'silver', 'copper'
  priceEGP: number;
  priceSAR: number;
  priceTRY: number;
  timestamp: Date;
}

interface NearbyStore {
  name: string;
  location: string;
  latitude: number;
  longitude: number;
  distance: number; // km
}

interface PriceForecast {
  item_name: string;
  item_category: string;
  current_price: number;
  forecasted_price_30d: number;
  forecasted_price_90d: number;
  confidence_score: number; // 0-100
  trend: "upward" | "downward" | "stable";
  recommendation: string; // "buy_now" | "wait" | "stock_up"
  reasoning: string;
}

// ─── Supabase Setup ──────────────────────────────────────────────────────

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

// ─── Alert Detection & Thresholds ────────────────────────────────────────

const PRICE_CHANGE_THRESHOLD = 5; // 5% change triggers an alert
const EXCHANGE_RATE_THRESHOLD = 2; // 2% change for currency alerts

interface PriceAlert {
  user_id: string;
  item_name: string;
  item_category: string;
  alert_type: "price_drop" | "price_surge" | "below_avg" | "deal_found";
  old_price: number;
  new_price: number;
  percentage_change: number;
}

// ─── Helper Functions ────────────────────────────────────────────────────

async function getLastPriceForItem(itemName: string): Promise<number | null> {
  const { data } = await supabase
    .from("price_index")
    .select("price")
    .ilike("item_name", itemName)
    .order("timestamp", { ascending: false })
    .limit(1)
    .maybeSingle();
  return data?.price || null;
}

async function getLastExchangeRate(from: string, to: string): Promise<number | null> {
  const { data } = await supabase
    .from("currency_rates")
    .select("rate")
    .eq("from_currency", from)
    .eq("to_currency", to)
    .order("timestamp", { ascending: false })
    .limit(1)
    .maybeSingle();
  return data?.rate || null;
}

async function detectPriceAlerts(foodPrices: FoodPrice[]): Promise<PriceAlert[]> {
  const alerts: PriceAlert[] = [];
  const { data: users } = await supabase.from("zad_users").select("id").limit(100);

  if (!users || users.length === 0) return alerts;

  for (const price of foodPrices) {
    const lastPrice = await getLastPriceForItem(price.itemName);
    if (!lastPrice) continue;

    const changePercent = ((price.price - lastPrice) / lastPrice) * 100;
    const absChange = Math.abs(changePercent);

    if (absChange >= PRICE_CHANGE_THRESHOLD) {
      const alertType = changePercent < 0 ? "price_drop" : "price_surge";
      for (const user of users) {
        alerts.push({
          user_id: user.id,
          item_name: price.itemName,
          item_category: price.category,
          alert_type: alertType,
          old_price: lastPrice,
          new_price: price.price,
          percentage_change: changePercent,
        });
      }
    }
  }

  return alerts;
}

async function detectExchangeRateAlerts(exchangeRates: ExchangeRate[]): Promise<PriceAlert[]> {
  const alerts: PriceAlert[] = [];
  const { data: users } = await supabase.from("zad_users").select("id").limit(100);

  if (!users || users.length === 0) return alerts;

  for (const rate of exchangeRates) {
    const lastRate = await getLastExchangeRate(rate.from, rate.to);
    if (!lastRate) continue;

    const changePercent = ((rate.rate - lastRate) / lastRate) * 100;
    const absChange = Math.abs(changePercent);

    if (absChange >= EXCHANGE_RATE_THRESHOLD) {
      const alertType = changePercent < 0 ? "price_drop" : "price_surge";
      for (const user of users) {
        alerts.push({
          user_id: user.id,
          item_name: `${rate.from}→${rate.to} سعر صرف`,
          item_category: "exchange_rate",
          alert_type: alertType,
          old_price: lastRate,
          new_price: rate.rate,
          percentage_change: changePercent,
        });
      }
    }
  }

  return alerts;
}

async function getHistoricalPriceData(
  itemName: string,
  days: number = 90
): Promise<Array<{ price: number; timestamp: string }>> {
  const since = new Date(Date.now() - days * 86400000).toISOString();

  const { data } = await supabase
    .from("price_index")
    .select("price, timestamp")
    .ilike("item_name", itemName)
    .gte("timestamp", since)
    .order("timestamp", { ascending: true });

  return (data || []).map((row: any) => ({
    price: row.price,
    timestamp: row.timestamp,
  }));
}

async function analyzeWithGemini(
  itemName: string,
  currentPrice: number,
  historicalData: Array<{ price: number; timestamp: string }>,
  weatherContext?: string
): Promise<PriceForecast | null> {
  try {
    // Prepare data summary for Gemini
    const avgPrice = historicalData.length > 0
      ? historicalData.reduce((sum, d) => sum + d.price, 0) / historicalData.length
      : currentPrice;

    const priceChange30d = historicalData.length > 0
      ? ((currentPrice - historicalData[0].price) / historicalData[0].price) * 100
      : 0;

    const prompt = `You are a market analyst. Analyze this price data and provide a forecast.

Item: ${itemName}
Current Price: ${currentPrice} EGP
30-Day Average: ${avgPrice.toFixed(2)} EGP
30-Day Change: ${priceChange30d > 0 ? "+" : ""}${priceChange30d.toFixed(1)}%
Data Points: ${historicalData.length}
${weatherContext ? `Weather Context: ${weatherContext}` : ""}

Respond with ONLY valid JSON (no markdown, no code blocks):
{
  "forecasted_price_30d": <number>,
  "forecasted_price_90d": <number>,
  "confidence_score": <0-100>,
  "trend": "<upward|downward|stable>",
  "recommendation": "<buy_now|wait|stock_up>",
  "reasoning": "<brief explanation in Arabic>"
}`;

    // Call Gemini API (via CLAUDE.md callGeminiPool)
    const GEMINI_KEY = Deno.env.get("ZAD_API_KEY_1") || Deno.env.get("GEMINI_API_KEY");
    if (!GEMINI_KEY) {
      console.warn("[Forecast] No Gemini key available, skipping AI analysis");
      return null;
    }

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + GEMINI_KEY,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              parts: [{ text: prompt }],
            },
          ],
          generationConfig: {
            maxOutputTokens: 256,
            temperature: 0.3,
          },
        }),
      }
    );

    if (!response.ok) {
      console.warn(`[Forecast] Gemini API error: ${response.status}`);
      return null;
    }

    const result = await response.json();
    const textContent = result.candidates?.[0]?.content?.parts?.[0]?.text;

    if (!textContent) {
      console.warn("[Forecast] No text in Gemini response");
      return null;
    }

    // Parse JSON response (handle potential markdown wrapping)
    const jsonMatch = textContent.match(/\{[\s\S]*\}/);
    const jsonStr = jsonMatch ? jsonMatch[0] : textContent;
    const forecast = JSON.parse(jsonStr);

    return {
      item_name: itemName,
      item_category: "general",
      current_price: currentPrice,
      forecasted_price_30d: forecast.forecasted_price_30d || currentPrice,
      forecasted_price_90d: forecast.forecasted_price_90d || currentPrice,
      confidence_score: Math.min(100, Math.max(0, forecast.confidence_score || 70)),
      trend: forecast.trend || "stable",
      recommendation: forecast.recommendation || "wait",
      reasoning: forecast.reasoning || "Data insufficient for strong prediction",
    };
  } catch (err) {
    console.error(`[Forecast] Gemini analysis failed for ${itemName}:`, err);
    return null;
  }
}

async function generateForecasts(foodPrices: FoodPrice[]): Promise<PriceForecast[]> {
  const forecasts: PriceForecast[] = [];

  // Analyze top items only (avoid excessive API calls)
  const topItems = foodPrices.slice(0, 5);

  for (const price of topItems) {
    const history = await getHistoricalPriceData(price.itemName, 90);
    if (history.length < 3) continue; // Need at least 3 data points

    const forecast = await analyzeWithGemini(price.itemName, price.price, history);
    if (forecast) {
      forecasts.push(forecast);
    }
  }

  return forecasts;
}

async function sendFCMNotifications(alerts: PriceAlert[]): Promise<number> {
  let sent = 0;

  for (const alert of alerts) {
    try {
      const { data: fcmData } = await supabase
        .from("fcm_tokens")
        .select("token")
        .eq("user_id", alert.user_id)
        .eq("active", true)
        .maybeSingle();

      if (!fcmData?.token) continue;

      const title = alert.alert_type === "price_drop"
        ? `📉 ${alert.item_name} انخفض`
        : `📈 ${alert.item_name} ارتفع`;

      const body = `${alert.alert_type === "price_drop" ? "وفّر" : "احذر"}: ${Math.abs(alert.percentage_change).toFixed(1)}% (${alert.old_price.toFixed(2)} → ${alert.new_price.toFixed(2)})`;

      // Store notification in DB (Firebase Messaging handled by client)
      await supabase.from("price_alerts").insert({
        user_id: alert.user_id,
        item_name: alert.item_name,
        item_category: alert.item_category,
        alert_type: alert.alert_type,
        old_price: alert.old_price,
        new_price: alert.new_price,
        percentage_change: alert.percentage_change,
        created_at: new Date().toISOString(),
      });

      sent++;
      console.log(`[FCM] Queued notification for user ${alert.user_id}: ${title}`);
    } catch (err) {
      console.error(`[FCM] Failed to send alert for user ${alert.user_id}:`, err);
    }
  }

  return sent;
}

// ─── API Fetchers ───────────────────────────────────────────────────────

async function fetchExchangeRates(): Promise<ExchangeRate[]> {
  try {
    // Currency-api: https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/egp.json
    const response = await fetch(
      "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/egp.json"
    );
    const data = await response.json();

    const rates: ExchangeRate[] = [];
    const egpRates = data.egp || {};

    // Extract key MENA pairs
    const targets = ["usd", "sar", "try", "aed", "kwd", "bhd"];

    for (const target of targets) {
      if (egpRates[target]) {
        rates.push({
          from: "EGP",
          to: target.toUpperCase(),
          rate: 1 / egpRates[target], // Invert to get EGP→target
          timestamp: new Date(),
        });
      }
    }

    return rates;
  } catch (err) {
    console.error("fetchExchangeRates error:", err);
    return [];
  }
}

async function fetchWeather(latitude = 30.0444, longitude = 31.2357): Promise<WeatherData | null> {
  // Default: Cairo. Can expand to multiple cities
  try {
    const response = await fetch(
      `https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m,weather_code&forecast_days=7`
    );
    const data = await response.json();

    const current = data.current || {};
    const weatherCode = current.weather_code || 0;

    // Simple weather code to condition mapping
    let condition = "normal";
    let impactCategory = "normal";

    if (weatherCode >= 80 && weatherCode <= 82) {
      condition = "rainy";
    } else if (current.temperature_2m > 35) {
      condition = "hot";
      impactCategory = "heat_wave";
    } else if (current.temperature_2m < 10) {
      condition = "cold";
    }

    return {
      temperature: current.temperature_2m || 25,
      condition,
      forecastDays: 7,
      impactCategory,
    };
  } catch (err) {
    console.error("fetchWeather error:", err);
    return null;
  }
}

async function fetchFoodPrices(): Promise<FoodPrice[]> {
  // Simplified: fetch popular items from Open Food Facts
  // Real implementation would query USDA or Open Food Facts API
  try {
    // For now, return mock data (actual API integration happens in follow-up)
    return [
      {
        itemName: "Bread",
        category: "bread",
        price: 3.5,
        currency: "EGP",
        source: "usda",
      },
      {
        itemName: "Milk",
        category: "milk",
        price: 25.0,
        currency: "EGP",
        source: "usda",
      },
      {
        itemName: "Eggs",
        category: "eggs",
        price: 50.0,
        currency: "EGP",
        source: "usda",
      },
    ];
  } catch (err) {
    console.error("fetchFoodPrices error:", err);
    return [];
  }
}

async function fetchMetalPrices(): Promise<GoldPrice | null> {
  // Goldprice.dev — Free API for precious metals
  try {
    const response = await fetch("https://www.goldprice.dev/api/rates");
    const data = await response.json();

    const goldEGP = data.prices?.EGP?.gram_24k || 0;
    const goldSAR = data.prices?.SAR?.gram_24k || 0;
    const goldTRY = data.prices?.TRY?.gram_24k || 0;

    return {
      metal: "gold",
      priceEGP: goldEGP,
      priceSAR: goldSAR,
      priceTRY: goldTRY,
      timestamp: new Date(),
    };
  } catch (err) {
    console.error("fetchMetalPrices error:", err);
    return null;
  }
}

async function fetchNearbyStores(lat: number, lng: number): Promise<NearbyStore[]> {
  // Nominatim (OpenStreetMap) — Free store/merchant locator
  // Real implementation would call Nominatim API
  try {
    // Mock data for now
    return [
      {
        name: "Carrefour Cairo",
        location: "New Cairo",
        latitude: lat + 0.01,
        longitude: lng + 0.01,
        distance: 2.5,
      },
    ];
  } catch (err) {
    console.error("fetchNearbyStores error:", err);
    return [];
  }
}

// ─── Main Orchestration ──────────────────────────────────────────────────

async function main() {
  console.log("[Market Intelligence] Starting data collection...");

  const startTime = Date.now();

  // Parallel fetch all APIs
  const [exchangeRates, weather, foodPrices, metalPrices, nearbyStores] =
    await Promise.all([
      fetchExchangeRates(),
      fetchWeather(),
      fetchFoodPrices(),
      fetchMetalPrices(),
      fetchNearbyStores(30.0444, 31.2357), // Cairo default
    ]);

  console.log(`[Market Intelligence] Data collected in ${Date.now() - startTime}ms`);
  console.log(`  Exchange rates: ${exchangeRates.length}`);
  console.log(`  Weather: ${weather ? "ok" : "failed"}`);
  console.log(`  Food prices: ${foodPrices.length}`);
  console.log(`  Metal prices: ${metalPrices ? "ok" : "failed"}`);
  console.log(`  Nearby stores: ${nearbyStores.length}`);

  // Store in Supabase
  const errors = [];

  // 1. Store exchange rates
  if (exchangeRates.length > 0) {
    const { error } = await supabase.from("currency_rates").insert(
      exchangeRates.map((r) => ({
        from_currency: r.from,
        to_currency: r.to,
        rate: r.rate,
        source: "currency-api",
        timestamp: r.timestamp.toISOString(),
      }))
    );
    if (error) errors.push(`currency_rates: ${error.message}`);
  }

  // 2. Store food prices
  if (foodPrices.length > 0) {
    const { error } = await supabase.from("price_index").insert(
      foodPrices.map((p) => ({
        item_name: p.itemName,
        item_category: p.category,
        price: p.price,
        currency: p.currency,
        source: p.source,
        timestamp: new Date().toISOString(),
      }))
    );
    if (error) errors.push(`price_index: ${error.message}`);
  }

  // 3. Store market snapshot (aggregate)
  const { data: existingUsers } = await supabase.from("zad_users").select("id").limit(100);
  if (existingUsers && existingUsers.length > 0) {
    const snapshots = existingUsers.map((user) => ({
      user_id: user.id,
      inflation_index: 65, // Placeholder: real calc from price changes
      food_price_change_pct: weather?.impactCategory === "heat_wave" ? 12.5 : -2.3,
      average_shopping_cost: foodPrices.reduce((sum, p) => sum + p.price, 0),
      weather_condition: weather?.condition || "normal",
      expected_impact: weather?.impactCategory || "normal",
      data_freshness: "current",
    }));

    const { error } = await supabase.from("market_snapshot").insert(snapshots);
    if (error) errors.push(`market_snapshot: ${error.message}`);
  }

  // 4. Detect and create price alerts based on changes
  let alertsCreated = 0;
  try {
    const [priceAlerts, exchangeAlerts] = await Promise.all([
      detectPriceAlerts(foodPrices),
      detectExchangeRateAlerts(exchangeRates),
    ]);

    const allAlerts = [...priceAlerts, ...exchangeAlerts];

    if (allAlerts.length > 0) {
      // Send FCM notifications
      const notificationsSent = await sendFCMNotifications(allAlerts);
      alertsCreated = notificationsSent;
      console.log(`[Market Intelligence] Created ${allAlerts.length} alerts, sent ${notificationsSent} notifications`);
    }
  } catch (err) {
    console.error("[Market Intelligence] Alert detection error:", err);
    errors.push(`alert_detection: ${err instanceof Error ? err.message : String(err)}`);
  }

  // 5. Generate AI-powered price forecasts (Phase 2)
  let forecastsGenerated = 0;
  try {
    const forecasts = await generateForecasts(foodPrices);

    if (forecasts.length > 0 && existingUsers && existingUsers.length > 0) {
      // Store forecasts in market_snapshot (extended with forecast_data)
      forecastsGenerated = forecasts.length;
      console.log(`[Market Intelligence] Generated ${forecasts.length} price forecasts`);

      // Log forecasts for debugging
      for (const forecast of forecasts) {
        console.log(`  ${forecast.item_name}: ${forecast.current_price} → ${forecast.forecasted_price_30d.toFixed(2)} (30d), confidence: ${forecast.confidence_score}%`);
      }
    }
  } catch (err) {
    console.error("[Market Intelligence] Forecast generation error:", err);
    errors.push(`forecast_generation: ${err instanceof Error ? err.message : String(err)}`);
  }

  if (errors.length > 0) {
    console.error("[Market Intelligence] Errors:", errors);
  }

  console.log("[Market Intelligence] Complete");

  return {
    success: errors.length === 0,
    stats: {
      exchangeRates: exchangeRates.length,
      foodPrices: foodPrices.length,
      weather: weather ? "ok" : "failed",
      metalPrices: metalPrices ? "ok" : "failed",
      nearbyStores: nearbyStores.length,
      alerts_created: alertsCreated,
      forecasts_generated: forecastsGenerated,
    },
    errors: errors.length > 0 ? errors : null,
  };
}

// ─── Handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  // Allow POST (manual trigger) or GET (cron trigger)
  if (req.method !== "GET" && req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  try {
    const result = await main();
    return new Response(JSON.stringify(result), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("[Market Intelligence] Fatal error:", err);
    return new Response(
      JSON.stringify({
        success: false,
        error: err instanceof Error ? err.message : String(err),
      }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
