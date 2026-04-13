package com.codex.offlineledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codex.offlineledger.data.dao.LedgerDao
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.AppLockSettingsEntity
import com.codex.offlineledger.data.entity.GiftDirection
import com.codex.offlineledger.data.entity.GiftRecordEntity
import com.codex.offlineledger.data.entity.NoteCategoryEntity
import com.codex.offlineledger.data.entity.NoteEntity
import com.codex.offlineledger.data.entity.PersonEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.data.entity.SnapshotExpenseEntity
import com.codex.offlineledger.data.entity.TodoEntity

class AppConverters {
    @TypeConverter
    fun giftDirectionToString(direction: GiftDirection?): String? = direction?.name

    @TypeConverter
    fun stringToGiftDirection(value: String?): GiftDirection? = value?.let(GiftDirection::valueOf)

    @TypeConverter
    fun recurrenceModeToString(mode: RecurrenceMode?): String? = mode?.name

    @TypeConverter
    fun stringToRecurrenceMode(value: String?): RecurrenceMode? = value?.let(RecurrenceMode::valueOf)
}

@Database(
    entities = [
        AccountEntity::class,
        SnapshotEntity::class,
        SnapshotBalanceEntity::class,
        SnapshotExpenseEntity::class,
        PersonEntity::class,
        GiftRecordEntity::class,
        TodoEntity::class,
        NoteCategoryEntity::class,
        NoteEntity::class,
        RecurrenceRuleEntity::class,
        AppLockSettingsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_note_categories_name` ON `note_categories` (`name`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_categories_sortOrder` ON `note_categories` (`sortOrder`)",
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `categoryId` INTEGER,
                        `body` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`categoryId`) REFERENCES `note_categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_categoryId` ON `notes` (`categoryId`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)",
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline-ledger.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
