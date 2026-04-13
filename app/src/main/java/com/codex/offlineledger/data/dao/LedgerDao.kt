package com.codex.offlineledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.AppLockSettingsEntity
import com.codex.offlineledger.data.entity.GiftRecordEntity
import com.codex.offlineledger.data.entity.NoteCategoryEntity
import com.codex.offlineledger.data.entity.NoteEntity
import com.codex.offlineledger.data.entity.PersonEntity
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.data.entity.SnapshotExpenseEntity
import com.codex.offlineledger.data.entity.TodoEntity
import com.codex.offlineledger.data.model.NoteCategoryWithCount
import com.codex.offlineledger.data.model.PersonWithGifts
import com.codex.offlineledger.data.model.SnapshotWithDetails
import com.codex.offlineledger.data.model.TodoWithRule
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    suspend fun getAccounts(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity): Long

    @Transaction
    @Query("SELECT * FROM snapshots ORDER BY snapshotDate DESC, id DESC")
    fun observeSnapshots(): Flow<List<SnapshotWithDetails>>

    @Transaction
    @Query("SELECT * FROM snapshots ORDER BY snapshotDate DESC, id DESC")
    suspend fun getSnapshots(): List<SnapshotWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SnapshotEntity): Long

    @Update
    suspend fun updateSnapshot(snapshot: SnapshotEntity)

    @Query("DELETE FROM snapshot_balances WHERE snapshotId = :snapshotId")
    suspend fun deleteSnapshotBalances(snapshotId: Long)

    @Query("DELETE FROM snapshot_expenses WHERE snapshotId = :snapshotId")
    suspend fun deleteSnapshotExpenses(snapshotId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshotBalances(balances: List<SnapshotBalanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshotExpenses(expenses: List<SnapshotExpenseEntity>)

    @Transaction
    @Query("SELECT * FROM people ORDER BY name ASC")
    fun observePeopleWithGifts(): Flow<List<PersonWithGifts>>

    @Transaction
    @Query("SELECT * FROM people ORDER BY name ASC")
    suspend fun getPeopleWithGifts(): List<PersonWithGifts>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGiftRecord(record: GiftRecordEntity): Long

    @Query(
        "SELECT c.id, c.name, c.createdAt, c.sortOrder, COUNT(n.id) AS noteCount " +
            "FROM note_categories c " +
            "LEFT JOIN notes n ON n.categoryId = c.id " +
            "GROUP BY c.id " +
            "ORDER BY c.sortOrder ASC, c.createdAt DESC, c.id DESC",
    )
    fun observeNoteCategoriesWithCounts(): Flow<List<NoteCategoryWithCount>>

    @Query("SELECT * FROM note_categories ORDER BY sortOrder ASC, createdAt DESC, id DESC")
    suspend fun getNoteCategories(): List<NoteCategoryEntity>

    @Query("SELECT COUNT(*) FROM note_categories WHERE name = :name")
    suspend fun countNoteCategoriesByName(name: String): Int

    @Query("SELECT MIN(sortOrder) FROM note_categories")
    suspend fun getTopCategorySortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNoteCategory(category: NoteCategoryEntity): Long

    @Update
    suspend fun updateNoteCategories(categories: List<NoteCategoryEntity>)

    @Query("DELETE FROM note_categories WHERE id = :categoryId")
    suspend fun deleteNoteCategory(categoryId: Long)

    @Query("SELECT COUNT(*) FROM notes WHERE categoryId IS NULL")
    fun observeUncategorizedNoteCount(): Flow<Int>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE categoryId = :categoryId AND id IN (:noteIds)")
    suspend fun deleteNotesByIdsInCategory(categoryId: Long, noteIds: List<Long>)

    @Query("DELETE FROM notes WHERE categoryId IS NULL AND id IN (:noteIds)")
    suspend fun deleteNotesByIdsInUncategorized(noteIds: List<Long>)

    @Transaction
    @Query(
        "SELECT * FROM todos " +
            "ORDER BY isCompleted ASC, COALESCE(nextTriggerAt, reminderAt, dueAt, createdAt) ASC, id DESC",
    )
    fun observeTodos(): Flow<List<TodoWithRule>>

    @Transaction
    @Query(
        "SELECT * FROM todos " +
            "ORDER BY isCompleted ASC, COALESCE(nextTriggerAt, reminderAt, dueAt, createdAt) ASC, id DESC",
    )
    suspend fun getTodos(): List<TodoWithRule>

    @Transaction
    @Query(
        "SELECT * FROM todos " +
            "WHERE COALESCE(nextTriggerAt, reminderAt, dueAt) IS NOT NULL " +
            "AND COALESCE(nextTriggerAt, reminderAt, dueAt) <= :untilMillis " +
            "ORDER BY COALESCE(nextTriggerAt, reminderAt, dueAt) ASC",
    )
    suspend fun getDueTodos(untilMillis: Long): List<TodoWithRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoEntity): Long

    @Update
    suspend fun updateTodo(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurrenceRule(rule: RecurrenceRuleEntity)

    @Query("DELETE FROM recurrence_rules WHERE todoId = :todoId")
    suspend fun deleteRecurrenceRule(todoId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM todos WHERE sourceType = :sourceType AND sourceRefId = :sourceRefId AND sourceCycleKey = :cycleKey)")
    suspend fun hasGeneratedTodo(sourceType: String, sourceRefId: Long, cycleKey: String): Boolean

    @Query("SELECT * FROM people WHERE birthdayMonth = :month AND birthdayDay = :day ORDER BY name ASC")
    suspend fun getPeopleByBirthday(month: Int, day: Int): List<PersonEntity>

    @Query("SELECT * FROM app_lock WHERE id = 0")
    fun observeAppLockSettings(): Flow<AppLockSettingsEntity?>

    @Query("SELECT * FROM app_lock WHERE id = 0")
    suspend fun getAppLockSettings(): AppLockSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppLockSettings(settings: AppLockSettingsEntity)
}
