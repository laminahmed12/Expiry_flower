package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ActivationCode
import com.example.data.model.StoredItem

@Database(
    entities = [StoredItem::class, ActivationCode::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun activationCodeDao(): ActivationCodeDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stored_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        expiryDate TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        storageLocation TEXT NOT NULL,
                        imageUri TEXT,
                        barcode TEXT,
                        notes TEXT NOT NULL,
                        isSensitive INTEGER NOT NULL,
                        isConsumed INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO stored_items_new
                    (id,name,category,expiryDate,quantity,storageLocation,imageUri,barcode,notes,isSensitive,isConsumed,createdAt)
                    SELECT id,name,category,expiryDate,quantity,storageLocation,imageUri,barcode,notes,isSensitive,isConsumed,createdAt
                    FROM stored_items
                """.trimIndent())
                db.execSQL("DROP TABLE stored_items")
                db.execSQL("ALTER TABLE stored_items_new RENAME TO stored_items")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expiry_guard.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
