package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.ZadInventory
import com.example.data.ZadSubscription
import com.example.data.ZadTransaction

import androidx.room.TypeConverters

@Database(entities = [
    ZadTransaction::class,
    ZadInventory::class,
    ZadSubscription::class,
    com.example.data.ZadBehaviorPattern::class,
    com.example.data.ZadShoppingItem::class,
    com.example.data.AffiliateProduct::class,
    com.example.data.AffiliateClick::class,
    com.example.data.AffiliateCatalogRequest::class,
    com.example.data.ZadChatMessage::class,
    com.example.data.ZadPharmacyItem::class,
    com.example.data.ZadMaintenanceItem::class,
    com.example.data.ZadDoseLog::class,
    com.example.data.PendingSyncOp::class,
    com.example.data.RejectedBankMessage::class
], version = 17, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ZadDatabase : RoomDatabase() {
    abstract fun zadDao(): ZadDao

    companion object {
        @Volatile
        private var INSTANCE: ZadDatabase? = null

        /**
         * Task 19.2 — zad_transactions gets wallet/txnKind/transferTo. Every prior version
         * bump on this database relied on fallbackToDestructiveMigration (wipes the whole
         * local DB — every table, not just this one — on any unhandled version jump). That
         * was survivable so far because Supabase is resynced into Room on next load
         * (ZadViewModel.syncData), EXCEPT for whatever's still sitting in PendingSyncOp
         * (offline retry queue) at the exact moment of the wipe — that data has no other
         * copy and would be lost. A real transaction-history table is exactly the wrong
         * place to keep accepting that risk by default, so this one jump gets a real
         * migration instead.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE zad_transactions ADD COLUMN wallet TEXT NOT NULL DEFAULT 'card'")
                db.execSQL("ALTER TABLE zad_transactions ADD COLUMN txnKind TEXT NOT NULL DEFAULT 'expense'")
                db.execSQL("ALTER TABLE zad_transactions ADD COLUMN transferTo TEXT")

                // Task 19.3 backfill, mirrored from the Supabase migration — the ADD COLUMN
                // DEFAULT above just labeled every existing row 'expense' unconditionally,
                // which is wrong for every historical income row (isExpense=0). Must run
                // before anything reads txnKind, or a synced-but-not-yet-resynced device
                // would briefly count old salary/income rows as spending.
                db.execSQL("UPDATE zad_transactions SET txnKind = 'income' WHERE isExpense = 0 AND txnKind = 'expense'")

                // Historical ATM withdrawals -> transfer/cash (the actual Task 19 bug).
                // Keyword match mirrors SaBankParser's WITHDRAWAL typeRule.
                db.execSQL(
                    "UPDATE zad_transactions SET txnKind = 'transfer', transferTo = 'cash' " +
                    "WHERE isExpense = 1 AND txnKind = 'expense' AND (" +
                    "title LIKE '%سحب%' OR title LIKE '%صراف%' OR title LIKE '%ATM%' " +
                    "OR title LIKE '%withdrawal%' OR title LIKE '%çekme%')"
                )
            }
        }

        /**
         * affiliate_products gains `asin_verified`, and `asin` becomes nullable —
         * mirroring supabase/migrations/20260731000000_affiliate_asin_verification.sql.
         *
         * A real migration rather than a destructive fallback, for the same reason
         * MIGRATION_12_13 is one: the fallback wipes *every* table, including the
         * PendingSyncOp offline retry queue, which has no second copy anywhere.
         *
         * SQLite can't relax NOT NULL in place, so the table is recreated. Safe to do
         * bluntly here — affiliate_products is a read-only catalog mirror that
         * ZadViewModel refetches from Supabase, so nothing is lost even in the worst case.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS affiliate_products_new")
                db.execSQL(
                    "CREATE TABLE affiliate_products_new (" +
                        "id TEXT NOT NULL, " +
                        "product_name_ar TEXT NOT NULL, " +
                        "product_name_search_keywords TEXT NOT NULL, " +
                        "category TEXT, " +
                        "asin TEXT, " +
                        "asin_verified INTEGER NOT NULL DEFAULT 0, " +
                        "image_url TEXT, " +
                        "average_price_sar REAL NOT NULL, " +
                        "is_active INTEGER NOT NULL, " +
                        "created_at TEXT, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "INSERT INTO affiliate_products_new (" +
                        "id, product_name_ar, product_name_search_keywords, category, asin, " +
                        "asin_verified, image_url, average_price_sar, is_active, created_at) " +
                        "SELECT id, product_name_ar, product_name_search_keywords, category, asin, " +
                        "0, image_url, average_price_sar, is_active, created_at " +
                        "FROM affiliate_products"
                )
                db.execSQL("DROP TABLE affiliate_products")
                db.execSQL("ALTER TABLE affiliate_products_new RENAME TO affiliate_products")
            }
        }

        /**
         * مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — zad_transactions.currency، مرآة
         * لـ supabase/migrations/…_zad_transactions_currency.sql. null للصفوف القديمة كلها
         * (مفيش افتراض SAR بأثر رجعي) — CurrencyFormatter.format(context, tx) بيرجع لعملة
         * الـ Market الحالي وقت العرض في الحالة دي، زي السلوك القديم بالظبط.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE zad_transactions ADD COLUMN currency TEXT")
            }
        }

        /**
         * مرآة لـ supabase/migrations/…_normalize_transaction_currency.sql — معاملة بعملة
         * غير عملة الحساب بتتحوّل عند الكتابة على السيرفر، والقيمة الأصلية بتتحفظ. لازم
         * يبقوا هنا كمان: `select()` بيرجع كل الأعمدة، وRoom بيخزّن اللي الموديل بيعرّفه،
         * فعمود موجود في الجدول ومش في الاتنين دول بيبقى فرق يظهر أول مزامنة.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE zad_transactions ADD COLUMN original_amount REAL")
                db.execSQL("ALTER TABLE zad_transactions ADD COLUMN original_currency TEXT")
            }
        }

        /**
         * Task 30 — مرآة لـ supabase/migrations/20260901010000_family_shared_inventory.sql.
         * zad_inventory بقى فيه family_id (سيرفر-سايد بس، الكلاينت مايكتبوش) — لازم يبقى
         * هنا كمان لنفس السبب في تعليق MIGRATION_15_16: select() بيرجّعه.
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE zad_inventory ADD COLUMN familyId TEXT")
            }
        }

        fun getDatabase(context: Context): ZadDatabase {
            return INSTANCE ?: synchronized(this) {
                // تشفير SQLCipher: مفتاح القاعدة بيولّد مرة واحدة ويتخزن في
                // SharedPreferences الخاصة بالتطبيق (معزولة بـ sandbox أندرويد).
                // لو القاعدة موجودة plain من نسخة قديمة، أول فتح بعد التحديث هيشتغل
                // عادي (الـ openHelperFactory مش هينفع يقراها) — عشان كده بنستخدم
                // مسار آمن: لو فشل الفتح plain، ننشئ قاعدة جديدة مشفرة ونسيب
                // fallbackToDestructiveMigration يمسك الباقي. بيانات تجريبية أفضل من crash.
                val passphraseBytes = getOrCreatePassphrase(context)
                val factory = net.sqlcipher.database.SupportFactory(passphraseBytes, null, false)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZadDatabase::class.java,
                    "zad_database"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** مفتاح 32 بايت عشوائي، بيولد مرة واحدة ويتخزن محليًا (sandbox-protected). */
        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val prefs = context.getSharedPreferences("zad_db_key", Context.MODE_PRIVATE)
            val existing = prefs.getString("key_b64", null)
            if (existing != null) {
                return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
            }
            val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            prefs.edit().putString(
                "key_b64",
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            ).apply()
            return bytes
        }
    }
}
