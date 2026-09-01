// Lightweight Web Scraper — Phase 5
// جمع أسعار المتاجر الكبرى (كارفور، سبينيس، بندة) عبر RSS/Feeds

interface StorePrice {
  storeName: string;
  itemName: string;
  category: string;
  price: number;
  originalPrice?: number;
  discount?: number;
  url?: string;
  fetchedAt: string;
}

export async function scrapeCarrefourDeals(): Promise<StorePrice[]> {
  try {
    // Carrefour Egypt RSS feed for weekly deals
    const url =
      "https://www.carrefouregypt.com/en/rss-feeds/weekly-offers"; // Placeholder
    const response = await fetch(url);

    if (!response.ok) return [];

    const text = await response.text();
    // Parse RSS/HTML and extract prices
    // This is a simplified example - real scraping would parse HTML properly
    const prices: StorePrice[] = [];

    // Look for price patterns in feed
    const pricePattern = /(\d+(?:\.\d{2})?)\s*EGP/gi;
    const itemPattern = /(?:<title>|>)([^<]+)(?:<\/title>|<)/gi;

    let itemMatch;
    while ((itemMatch = itemPattern.exec(text))) {
      const item = itemMatch[1].trim();
      const priceMatch = pricePattern.exec(text);

      if (priceMatch && item.length > 2) {
        prices.push({
          storeName: "Carrefour Egypt",
          itemName: item,
          category: categorizeItem(item),
          price: parseFloat(priceMatch[1]),
          fetchedAt: new Date().toISOString(),
        });
      }
    }

    return prices;
  } catch (err) {
    console.error("[Scraper] Carrefour scrape failed:", err);
    return [];
  }
}

export async function scrapeSafarMart(): Promise<StorePrice[]> {
  try {
    // SafarMart (Saudi Arabia) - simplified scraping
    const url = "https://www.safarmart.com/offers"; // Placeholder
    const response = await fetch(url);

    if (!response.ok) return [];

    const text = await response.text();
    const prices: StorePrice[] = [];

    // Parse basic HTML structure
    // Real implementation would use jsdom or cheerio
    // For now, extract simple patterns

    const lines = text.split("\n");
    for (const line of lines) {
      if (line.includes("price") || line.includes("SAR")) {
        const priceMatch = line.match(/(\d+(?:\.\d{2})?)/);
        if (priceMatch) {
          prices.push({
            storeName: "SafarMart",
            itemName: "Item from SafarMart",
            category: "general",
            price: parseFloat(priceMatch[1]),
            fetchedAt: new Date().toISOString(),
          });
        }
      }
    }

    return prices;
  } catch (err) {
    console.error("[Scraper] SafarMart scrape failed:", err);
    return [];
  }
}

export async function scrapeHyperPandaKSA(): Promise<StorePrice[]> {
  try {
    // Hyper Panda (Saudi Arabia)
    const url = "https://www.hyperpandaksa.com/en/offers"; // Placeholder
    const response = await fetch(url);

    if (!response.ok) return [];

    const prices: StorePrice[] = [];
    // Similar parsing logic

    return prices;
  } catch (err) {
    console.error("[Scraper] HyperPanda scrape failed:", err);
    return [];
  }
}

export async function storeDealPrices(
  sb: any,
  prices: StorePrice[]
): Promise<number> {
  let stored = 0;

  for (const price of prices) {
    try {
      const { error } = await sb.from("price_index").insert({
        item_name: price.itemName,
        item_category: price.category,
        price: price.price,
        currency: price.storeName.includes("Hyper") ? "SAR" : "EGP",
        merchant_name: price.storeName,
        location: price.storeName.includes("Hyper") ? "السعودية" : "مصر",
        source: "web_scrape",
        timestamp: new Date().toISOString(),
      });

      if (!error) stored++;
    } catch (err) {
      console.error(`[Scraper] Failed to store price for ${price.itemName}:`, err);
    }
  }

  return stored;
}

export async function runDealScraping(sb: any): Promise<{
  carrefour: number;
  safarmart: number;
  hyperpanda: number;
}> {
  console.log("[Scraper] Starting deal scraping...");

  const [carrefourPrices, safarmartPrices, hyperpandaPrices] =
    await Promise.all([
      scrapeCarrefourDeals(),
      scrapeSafarMart(),
      scrapeHyperPandaKSA(),
    ]);

  const results = {
    carrefour: await storeDealPrices(sb, carrefourPrices),
    safarmart: await storeDealPrices(sb, safarmartPrices),
    hyperpanda: await storeDealPrices(sb, hyperpandaPrices),
  };

  console.log("[Scraper] Completed:", results);
  return results;
}

function categorizeItem(itemName: string): string {
  const name = itemName.toLowerCase();

  if (
    name.includes("bread") ||
    name.includes("خبز") ||
    name.includes("عيش")
  ) {
    return "bread";
  }
  if (
    name.includes("milk") ||
    name.includes("لبن") ||
    name.includes("جبن")
  ) {
    return "milk";
  }
  if (
    name.includes("egg") ||
    name.includes("بيض")
  ) {
    return "eggs";
  }
  if (
    name.includes("oil") ||
    name.includes("زيت") ||
    name.includes("سمن")
  ) {
    return "oil";
  }
  if (
    name.includes("vegetable") ||
    name.includes("خضار") ||
    name.includes("طماطم") ||
    name.includes("خيار")
  ) {
    return "vegetables";
  }
  if (
    name.includes("fruit") ||
    name.includes("فاكهة") ||
    name.includes("تفاح") ||
    name.includes("برتقال")
  ) {
    return "fruits";
  }

  return "general";
}
