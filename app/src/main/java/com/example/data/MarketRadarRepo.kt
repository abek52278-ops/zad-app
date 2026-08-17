package com.example.data

data class GoldPriceItem(
    val karat: String,
    val price: Double,
    val currency: String,
    val changePercent: Double, // e.g. +0.45 or -0.20
    val isUp: Boolean
)

data class FuelPriceItem(
    val fuelType: String,
    val price: Double,
    val currency: String,
    val unit: String = "لتر"
)

data class ProducePriceItem(
    val itemName: String,
    val avgPrice: Double,
    val currency: String,
    val unit: String = "كجم",
    val statusText: String, // "مستقر ✅" | "مرتفع ⚠️" | "عرض اقتصادي 🏷️"
    val iconEmoji: String
)

data class MarketRadarData(
    val countryCode: String,
    val countryName: String,
    val lastUpdatedText: String,
    val goldPrices: List<GoldPriceItem>,
    val fuelPrices: List<FuelPriceItem>,
    val producePrices: List<ProducePriceItem>
)

object MarketRadarRepo {
    fun getMarketData(market: Market): MarketRadarData {
        val sym = market.currencySymbol
        return when (market.countryCode) {
            "EG" -> MarketRadarData(
                countryCode = "EG",
                countryName = "مصر",
                lastUpdatedText = "تحديث لحظي",
                goldPrices = listOf(
                    GoldPriceItem("عيار 24", 5180.0, sym, +0.65, true),
                    GoldPriceItem("عيار 21", 4530.0, sym, +0.60, true),
                    GoldPriceItem("عيار 18", 3880.0, sym, +0.55, true),
                    GoldPriceItem("جنيه ذهب", 36240.0, sym, +0.60, true)
                ),
                fuelPrices = listOf(
                    FuelPriceItem("بنزين 95", 17.00, sym),
                    FuelPriceItem("بنزين 92", 15.25, sym),
                    FuelPriceItem("بنزين 80", 13.75, sym),
                    FuelPriceItem("سولار", 13.50, sym)
                ),
                producePrices = listOf(
                    ProducePriceItem("طماطم بلدي", 15.0, sym, "كجم", "مستقر ✅", "🍅"),
                    ProducePriceItem("بطاطس تحمير", 22.0, sym, "كجم", "مستقر ✅", "🥔"),
                    ProducePriceItem("بصل أحمر", 18.0, sym, "كجم", "عرض اقتصادي 🏷️", "🧅"),
                    ProducePriceItem("كرتونة بيض أبيض", 165.0, sym, "طبق", "مستقر ✅", "🥚"),
                    ProducePriceItem("أرز مصري فاخر", 32.0, sym, "كجم", "مستقر ✅", "🍚"),
                    ProducePriceItem("زيت نباتي 800مل", 55.0, sym, "زجاجة", "مستقر ✅", "🌻")
                )
            )
            "SA" -> MarketRadarData(
                countryCode = "SA",
                countryName = "السعودية",
                lastUpdatedText = "تحديث لحظي",
                goldPrices = listOf(
                    GoldPriceItem("عيار 24", 398.50, sym, +0.35, true),
                    GoldPriceItem("عيار 21", 348.70, sym, +0.30, true),
                    GoldPriceItem("عيار 18", 298.90, sym, +0.25, true),
                    GoldPriceItem("أونصة الذهب", 12390.0, sym, +0.35, true)
                ),
                fuelPrices = listOf(
                    FuelPriceItem("بنزين 95", 2.33, sym),
                    FuelPriceItem("بنزين 91", 2.18, sym),
                    FuelPriceItem("ديزل", 1.15, sym),
                    FuelPriceItem("غاز طبيعي", 0.90, sym)
                ),
                producePrices = listOf(
                    ProducePriceItem("طماطم محلية", 4.50, sym, "كجم", "مستقر ✅", "🍅"),
                    ProducePriceItem("بطاطس طازجة", 3.75, sym, "كجم", "عرض اقتصادي 🏷️", "🥔"),
                    ProducePriceItem("بصل أصفر", 3.25, sym, "كجم", "مستقر ✅", "🧅"),
                    ProducePriceItem("بيض طازج 30 حبة", 18.50, sym, "طبق", "مستقر ✅", "🥚"),
                    ProducePriceItem("أرز بسمتي 5كجم", 42.0, sym, "كيس", "عرض اقتصادي 🏷️", "🍚"),
                    ProducePriceItem("زيت دوار الشمس 1.5 لتر", 17.50, sym, "زجاجة", "مستقر ✅", "🌻")
                )
            )
            "AE" -> MarketRadarData(
                countryCode = "AE",
                countryName = "الإمارات",
                lastUpdatedText = "تحديث لحظي",
                goldPrices = listOf(
                    GoldPriceItem("عيار 24", 390.25, sym, +0.40, true),
                    GoldPriceItem("عيار 21", 341.50, sym, +0.35, true),
                    GoldPriceItem("عيار 18", 292.75, sym, +0.30, true),
                    GoldPriceItem("أونصة الذهب", 12135.0, sym, +0.40, true)
                ),
                fuelPrices = listOf(
                    FuelPriceItem("سوبر 98", 3.10, sym),
                    FuelPriceItem("خصوصي 95", 2.98, sym),
                    FuelPriceItem("ديزل", 3.14, sym)
                ),
                producePrices = listOf(
                    ProducePriceItem("طماطم طازجة", 5.50, sym, "كجم", "مستقر ✅", "🍅"),
                    ProducePriceItem("بطاطس هولندية", 4.25, sym, "كجم", "مستقر ✅", "🥔"),
                    ProducePriceItem("بصل أحمر", 3.50, sym, "كجم", "مستقر ✅", "🧅"),
                    ProducePriceItem("بيض 30 حبة", 19.0, sym, "طبق", "مستقر ✅", "🥚"),
                    ProducePriceItem("أرز برياني 5كجم", 38.0, sym, "كيس", "عرض اقتصادي 🏷️", "🍚")
                )
            )
            "KW" -> MarketRadarData(
                countryCode = "KW",
                countryName = "الكويت",
                lastUpdatedText = "تحديث لحظي",
                goldPrices = listOf(
                    GoldPriceItem("عيار 24", 32.80, sym, +0.25, true),
                    GoldPriceItem("عيار 21", 28.70, sym, +0.20, true),
                    GoldPriceItem("عيار 18", 24.60, sym, +0.20, true)
                ),
                fuelPrices = listOf(
                    FuelPriceItem("بنزين ممتاز 91", 0.085, sym),
                    FuelPriceItem("بنزين خصوصي 95", 0.105, sym),
                    FuelPriceItem("ديزل", 0.115, sym)
                ),
                producePrices = listOf(
                    ProducePriceItem("طماطم كويتي", 0.45, sym, "كجم", "مستقر ✅", "🍅"),
                    ProducePriceItem("بطاطس طازجة", 0.35, sym, "كجم", "مستقر ✅", "🥔"),
                    ProducePriceItem("بيض مزارع 30 حبة", 1.40, sym, "طبق", "مستقر ✅", "🥚")
                )
            )
            "JO" -> MarketRadarData(
                countryCode = "JO",
                countryName = "الأردن",
                lastUpdatedText = "تحديث لحظي",
                goldPrices = listOf(
                    GoldPriceItem("عيار 24", 75.20, sym, +0.30, true),
                    GoldPriceItem("عيار 21", 65.80, sym, +0.25, true),
                    GoldPriceItem("عيار 18", 56.40, sym, +0.20, true)
                ),
                fuelPrices = listOf(
                    FuelPriceItem("بنزين 95", 1.140, sym),
                    FuelPriceItem("بنزين 90", 0.910, sym),
                    FuelPriceItem("ديزل وسولار", 0.720, sym)
                ),
                producePrices = listOf(
                    ProducePriceItem("طماطم غورية", 0.65, sym, "كجم", "مستقر ✅", "🍅"),
                    ProducePriceItem("بطاطس بلدية", 0.75, sym, "كجم", "مستقر ✅", "🥔"),
                    ProducePriceItem("طبق بيض 30", 3.25, sym, "طبق", "مستقر ✅", "🥚")
                )
            )
            else -> MarketRadarData(
                countryCode = market.countryCode,
                countryName = market.displayNameAr,
                lastUpdatedText = "تحديث لحظي",
                goldPrices = listOf(
                    GoldPriceItem("عيار 24", 100.0, sym, +0.4, true),
                    GoldPriceItem("عيار 21", 87.5, sym, +0.4, true),
                    GoldPriceItem("عيار 18", 75.0, sym, +0.4, true)
                ),
                fuelPrices = listOf(
                    FuelPriceItem("وقود ممتاز", 1.50, sym),
                    FuelPriceItem("وقود عادي", 1.25, sym),
                    FuelPriceItem("ديزل", 1.10, sym)
                ),
                producePrices = listOf(
                    ProducePriceItem("طماطم طازجة", 2.50, sym, "كجم", "مستقر ✅", "🍅"),
                    ProducePriceItem("بطاطس", 2.00, sym, "كجم", "مستقر ✅", "🥔"),
                    ProducePriceItem("بيض 30 حبة", 5.00, sym, "طبق", "مستقر ✅", "🥚")
                )
            )
        }
    }
}
