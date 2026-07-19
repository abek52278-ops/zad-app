package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    com.example.data.AffiliateCatalogRequest::class
], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ZadDatabase : RoomDatabase() {
    abstract fun zadDao(): ZadDao

    companion object {
        @Volatile
        private var INSTANCE: ZadDatabase? = null

        fun getDatabase(context: Context): ZadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZadDatabase::class.java,
                    "zad_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
