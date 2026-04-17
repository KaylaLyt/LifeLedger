package com.codex.offlineledger.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class GiftDirection {
    SENT,
    RECEIVED,
}

enum class RecurrenceMode {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    ADVANCED,
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val accountNumber: String,
    val note: String = "",
    val includeInNetWorth: Boolean = true,
    val archived: Boolean = false,
)

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotDate: Long,
    val createdAt: Long,
    val nextRecordAt: Long? = null,
    val targetTotal: Long? = null,
    val debtLabel: String = "",
    val debtAmount: Long? = null,
    val note: String = "",
    val mood: Int? = null,
)

@Entity(
    tableName = "snapshot_balances",
    primaryKeys = ["snapshotId", "accountId"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class SnapshotBalanceEntity(
    val snapshotId: Long,
    val accountId: Long,
    val amount: Long,
)

@Entity(
    tableName = "expense_categories",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["sortOrder"]),
    ],
)
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "snapshot_expenses",
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("snapshotId"), Index("categoryId")],
)
data class SnapshotExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotId: Long,
    val categoryId: Long,
    val amount: Long,
)

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthdayMonth: Int? = null,
    val birthdayDay: Int? = null,
    val relation: String = "",
    val note: String = "",
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "gift_records",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("personId")],
)
data class GiftRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val date: Long,
    val direction: GiftDirection,
    val giftName: String,
    val price: Long,
    val note: String = "",
)

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val createdAt: Long,
    val isCompleted: Boolean = false,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val nextTriggerAt: Long? = null,
    val completedAt: Long? = null,
    val lastCompletedAt: Long? = null,
    val lastNotifiedAt: Long? = null,
)

@Entity(
    tableName = "notes",
    indices = [Index("updatedAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecurrenceRuleEntity(
    @PrimaryKey val todoId: Long,
    val mode: RecurrenceMode,
    val interval: Int = 1,
    val daysOfWeekCsv: String = "",
    val dayOfMonth: Int? = null,
    val monthsCsv: String = "",
    val hour: Int? = null,
    val minute: Int? = null,
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["sortOrder"]),
    ],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "snapshot_tags",
    primaryKeys = ["snapshotId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("tagId")],
)
data class SnapshotTagCrossRef(
    val snapshotId: Long,
    val tagId: Long,
)

@Entity(tableName = "app_lock")
data class AppLockSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val passwordHash: String = "",
    val failedAttempts: Int = 0,
)
