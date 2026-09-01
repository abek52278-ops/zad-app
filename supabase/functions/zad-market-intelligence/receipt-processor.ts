// Smart Receipt Processing — Phase 5
// استخدم Gemini Vision لتفريغ الفواتير تلقائياً

interface ReceiptItem {
  itemName: string;
  itemCategory: string;
  price: number;
  quantity: number;
  unitPrice: number;
}

interface ProcessedReceipt {
  userId: string;
  storeName: string;
  storeLocation: string;
  totalAmount: number;
  items: ReceiptItem[];
  purchaseDate: string;
  confidence: number; // 0-100
}

export async function processReceiptImage(
  base64Image: string,
  userId: string
): Promise<ProcessedReceipt | null> {
  try {
    const GEMINI_KEY = Deno.env.get("ZAD_API_KEY_1") || Deno.env.get("GEMINI_API_KEY");
    if (!GEMINI_KEY) {
      console.warn("[Receipt] No Gemini key available");
      return null;
    }

    const prompt = `Extract receipt information from this shopping invoice image. Return ONLY valid JSON.

Arabic context:
- Store names: كارفور, كازيون, سبينيس, بندة, السيف, فتيش
- Categories: خبز, ألبان, بيض, زيت, خضار, فواكه, مشروبات, تنظيف
- Prices in EGP (جنيه مصري)

Return exactly this JSON format:
{
  "storeName": "store name or unknown",
  "storeLocation": "city or area if visible",
  "totalAmount": <number>,
  "items": [
    {
      "itemName": "product name (English or Arabic)",
      "itemCategory": "bread|milk|eggs|oil|vegetables|fruits|drinks|cleaning|other",
      "price": <unit price>,
      "quantity": <number>,
      "unitPrice": <price per unit>
    }
  ],
  "purchaseDate": "YYYY-MM-DD or unknown",
  "confidence": <0-100 confidence score>
}`;

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=" +
        GEMINI_KEY,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              parts: [
                {
                  inlineData: {
                    mimeType: "image/jpeg",
                    data: base64Image,
                  },
                },
                {
                  text: prompt,
                },
              ],
            },
          ],
          generationConfig: {
            maxOutputTokens: 1024,
            temperature: 0.2,
          },
        }),
      }
    );

    if (!response.ok) {
      console.warn(`[Receipt] Gemini API error: ${response.status}`);
      return null;
    }

    const result = await response.json();
    const textContent = result.candidates?.[0]?.content?.parts?.[0]?.text;

    if (!textContent) {
      console.warn("[Receipt] No text in Gemini response");
      return null;
    }

    // Parse JSON response
    const jsonMatch = textContent.match(/\{[\s\S]*\}/);
    const jsonStr = jsonMatch ? jsonMatch[0] : textContent;
    const receiptData = JSON.parse(jsonStr);

    return {
      userId,
      storeName: receiptData.storeName || "Unknown",
      storeLocation: receiptData.storeLocation || "Unknown",
      totalAmount: receiptData.totalAmount || 0,
      items: (receiptData.items || []).map((item: any) => ({
        itemName: item.itemName || "Unknown",
        itemCategory: item.itemCategory || "other",
        price: item.price || 0,
        quantity: item.quantity || 1,
        unitPrice: item.unitPrice || item.price || 0,
      })),
      purchaseDate:
        receiptData.purchaseDate ||
        new Date().toISOString().split("T")[0],
      confidence: receiptData.confidence || 70,
    };
  } catch (err) {
    console.error("[Receipt] Processing failed:", err);
    return null;
  }
}

export async function storeReceiptItems(
  sb: any,
  receipt: ProcessedReceipt
): Promise<boolean> {
  try {
    if (!receipt.items || receipt.items.length === 0) {
      return true;
    }

    const insertData = receipt.items.map((item) => ({
      item_name: item.itemName,
      item_category: item.itemCategory,
      price: item.unitPrice,
      currency: "EGP",
      user_id: receipt.userId,
      location: receipt.storeLocation,
      merchant_name: receipt.storeName,
      source: "receipt_ocr",
      timestamp: new Date(receipt.purchaseDate).toISOString(),
    }));

    const { error } = await sb.from("price_index").insert(insertData);

    if (error) {
      console.error("[Receipt] DB error:", error.message);
      return false;
    }

    console.log(
      `[Receipt] Stored ${receipt.items.length} items from ${receipt.storeName}`
    );
    return true;
  } catch (err) {
    console.error("[Receipt] Storage failed:", err);
    return false;
  }
}

export async function getReceiptHistory(
  sb: any,
  userId: string,
  limit: number = 20
): Promise<
  Array<{
    storeName: string;
    totalAmount: number;
    itemCount: number;
    date: string;
  }>
> {
  try {
    const { data, error } = await sb
      .from("price_index")
      .select("merchant_name, timestamp")
      .eq("user_id", userId)
      .eq("source", "receipt_ocr")
      .order("timestamp", { ascending: false })
      .limit(limit * 5); // Fetch more to group by store

    if (error || !data) return [];

    // Group by store and date
    const grouped = new Map<
      string,
      { items: any[]; date: string; store: string }
    >();

    (data as any[]).forEach((row) => {
      const key = `${row.merchant_name}_${row.timestamp}`;
      if (!grouped.has(key)) {
        grouped.set(key, {
          items: [],
          date: row.timestamp.split("T")[0],
          store: row.merchant_name,
        });
      }
      grouped.get(key)!.items.push(row);
    });

    return Array.from(grouped.values())
      .slice(0, limit)
      .map((g) => ({
        storeName: g.store,
        totalAmount: 0, // Would need to sum
        itemCount: g.items.length,
        date: g.date,
      }));
  } catch (err) {
    console.error("[Receipt] History fetch failed:", err);
    return [];
  }
}
