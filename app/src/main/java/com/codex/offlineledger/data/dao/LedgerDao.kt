package com.codex.offlineledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.AppLockSettingsEntity
import com.codex.offlineledger.data.entity.ExpenseCategoryEntity
import com.codex.offlineledger.data.entity.GiftRecordEntity
import com.codex.offlineledger.data.entity.NoteEntity
import com.codex.offlineledger.data.entity.PersonEntity
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.data.entity.SnapshotExpenseEntity
import com.codex.offlineledger.data.entity.SnapshotTagCrossRef
import com.codex.offlineledger.data.entity.TagEntity
import com.codex.offlineledger.data.entity.TodoEntity
import com.codex.offlineledger.data.model.PersonWithGifts
import com.codex.offlineledger.data.model.SnapshotWithDetails
import com.codex.offlineledger.data.model.TodoWithRule
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // --- Accounts ---

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY name ASC")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    suspend fun getAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getAccountByNameIgnoreCase(name: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setAccountArchived(id: Long, archived: Boolean)

    // --- Expense Categories ---

    @Query("SELECT * FROM expense_categories WHERE archived = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun observeActiveCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAllCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAllCategories(): List<ExpenseCategoryEntity>

    @Query("SELECT COUNT(*) FROM expense_categories WHERE archived = 0")
    suspend fun countActiveCategories(): Int

    @Query("SELECT COUNT(*) FROM snapshot_expenses WHERE categoryId = :categoryId")
    suspend fun countExpensesByCategory(categoryId: Long): Int

    @Query("SELECT * FROM expense_categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): ExpenseCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: ExpenseCategoryEntity): Long

    @Update
    suspend fun updateCategory(category: ExpenseCategoryEntity)

    // --- Snapshots ---

    @Transaction
    @Query("SELECT * FROM snapshots ORDER BY snapshotDate DESC, id DESC")
    fun observeSnapshots(): Flow<List<SnapshotWithDetails>>

    @Transaction
    @Query("SELECT * FROM snapshots ORDER BY snapshotDate DESC, id DESC")
    suspend fun getSnapshots(): List<SnapshotWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SnapshotEntity): Long

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshotBalances(balances: List<SnapshotBalanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshotExpenses(expenses: List<SnapshotExpenseEntity>)

    // --- People ---

    @Transaction
    @Query("SELECT * FROM people ORDER BY sortOrder ASC, id DESC")
    fun observePeopleWithGifts(): Flow<List<PersonWithGifts>>

    @Transaction
    @Query("SELECT * FROM people ORDER BY sortOrder ASC, id DESC")
    suspend fun getPeopleWithGifts(): List<PersonWithGifts>

    @Query("SELECT MIN(sortOrder) FROM people")
    suspend fun getMinPersonSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePeople(people: List<PersonEntity>)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGiftRecord(record: GiftRecordEntity): Long

    // --- Notes ---

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    // --- Todos ---

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

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodo(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecurrenceRule(rule: RecurrenceRuleEntity)

    @Query("DELETE FROM recurrence_rules WHERE todoId = :todoId")
    suspend fun deleteRecurrenceRule(todoId: Long)

    // --- App Lock ---

    @Query("SELECT * FROM app_lock WHERE id = 0")
    fun observeAppLockSettings(): Flow<AppLockSettingsEntity?>

    @Query("SELECT * FROM app_lock WHERE id = 0")
    suspend fun getAppLockSettings(): AppLockSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppLockSettings(settings: AppLockSettingsEntity)

    @Query("UPDATE app_lock SET passwordHash = '', failedAttempts = 0 WHERE id = 0")
    suspend fun clearPasswordHash()

    // --- Tags ---

    @Query("SELECT * FROM tags WHERE archived = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeActiveTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY archived ASC, sortOrder ASC, name ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY archived ASC, sortOrder ASC, name ASC")
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTagByNameIgnoreCase(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: TagEntity): Long

    @Update
    suspend fun updateTag(tag: TagEntity)

    @Query("UPDATE tags SET archived = :archived WHERE id = :id")
    suspend fun setTagArchived(id: Long, archived: Boolean)

    @Query("SELECT COUNT(*) FROM snapshot_tags WHERE tagId = :id")
    suspend fun countSnapshotTagsByTag(id: Long): Int

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTagById(id: Long)

    // --- Snapshot Tags ---

    @Query(
        "SELECT t.* FROM tags t INNER JOIN snapshot_tags st ON st.tagId = t.id " +
            "WHERE st.snapshotId = :snapshotId ORDER BY t.name ASC",
    )
    fun observeTagsForSnapshot(snapshotId: Long): Flow<List<TagEntity>>

    @Query(
        "SELECT st.snapshotId AS snapshotId, t.id AS id, t.name AS name, t.archived AS archived, t.sortOrder AS sortOrder " +
            "FROM snapshot_tags st INNER JOIN tags t ON t.id = st.tagId",
    )
    fun observeSnapshotTagJoin(): Flow<List<SnapshotTagJoinRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnapshotTagRefs(refs: List<SnapshotTagCrossRef>)

    @Query("DELETE FROM snapshot_tags WHERE snapshotId = :snapshotId AND tagId IN (:tagIds)")
    suspend fun deleteSnapshotTagRefs(snapshotId: Long, tagIds: List<Long>)

    @Query("DELETE FROM snapshot_tags WHERE snapshotId = :snapshotId")
    suspend fun clearSnapshotTagRefs(snapshotId: Long)

    @Query("SELECT tagId FROM snapshot_tags WHERE snapshotId = :snapshotId")
    suspend fun getTagIdsForSnapshot(snapshotId: Long): List<Long>

    @Query("SELECT * FROM snapshot_tags")
    suspend fun getAllSnapshotTagRefs(): List<SnapshotTagCrossRef>

    // --- Snapshot Annotation ---

    @Query("UPDATE snapshots SET mood = :mood, note = :note WHERE id = :snapshotId")
    suspend fun updateSnapshotMoodAndNote(snapshotId: Long, mood: Int?, note: String)
}

data class SnapshotTagJoinRow(
    val snapshotId: Long,
    val id: Long,
    val name: String,
    val archived: Boolean,
    val sortOrder: Int,
)
