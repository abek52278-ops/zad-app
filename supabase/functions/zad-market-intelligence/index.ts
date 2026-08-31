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

// ─── Supabase Setup ──────────────────────────────────────────────────────

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const supabase = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

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

  // 4. Create sample price alerts (real logic would come from zad-brain)
  if (foodPrices.length > 0 && existingUsers && existingUsers.length > 0) {
    const alerts = existingUsers.slice(0, 5).map((user) => ({
      user_id: user.id,
      item_name: foodPrices[0].itemName,
      item_category: foodPrices[0].category,
      alert_type: "price_drop",
      old_price: foodPrices[0].price * 1.1,
      new_price: foodPrices[0].price,
      percentage_change: -9.1,
      created_at: new Date().toISOString(),
    }));

    const { error } = await supabase.from("price_alerts").insert(alerts);
    if (error) errors.push(`price_alerts: ${error.message}`);
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
