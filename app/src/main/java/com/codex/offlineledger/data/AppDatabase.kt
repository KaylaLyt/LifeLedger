package com.codex.offlineledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.codex.offlineledger.data.dao.LedgerDao
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.AppLockSettingsEntity
import com.codex.offlineledger.data.entity.ExpenseCategoryEntity
import com.codex.offlineledger.data.entity.GiftDirection
import com.codex.offlineledger.data.entity.GiftRecordEntity
import com.codex.offlineledger.data.entity.NoteEntity
import com.codex.offlineledger.data.entity.PersonEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.data.entity.SnapshotExpenseEntity
import com.codex.offlineledger.data.entity.SnapshotTagCrossRef
import com.codex.offlineledger.data.entity.TagEntity
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
        ExpenseCategoryEntity::class,
        PersonEntity::class,
        GiftRecordEntity::class,
        TodoEntity::class,
        NoteEntity::class,
        RecurrenceRuleEntity::class,
        AppLockSettingsEntity::class,
        TagEntity::class,
        SnapshotTagCrossRef::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline-ledger.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
