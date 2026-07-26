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
], version = 13, exportSchema = false)
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

        fun getDatabase(context: Context): ZadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZadDatabase::class.java,
                    "zad_database"
                )
                    .addMigrations(MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
