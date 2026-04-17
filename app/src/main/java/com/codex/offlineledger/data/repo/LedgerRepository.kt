package com.codex.offlineledger.data.repo

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.codex.offlineledger.data.AppDatabase
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
import com.codex.offlineledger.data.model.ExportAccount
import com.codex.offlineledger.data.model.ExportBundle
import com.codex.offlineledger.data.model.ExportExpenseCategory
import com.codex.offlineledger.data.model.ExportGiftRecord
import com.codex.offlineledger.data.model.ExportLock
import com.codex.offlineledger.data.model.ExportNote
import com.codex.offlineledger.data.model.ExportPerson
import com.codex.offlineledger.data.model.ExportRecurrenceRule
import com.codex.offlineledger.data.model.ExportSnapshot
import com.codex.offlineledger.data.model.ExportSnapshotBalance
import com.codex.offlineledger.data.model.ExportSnapshotExpense
import com.codex.offlineledger.data.model.ExportSnapshotTag
import com.codex.offlineledger.data.model.ExportTag
import com.codex.offlineledger.data.model.ExportTodo
import com.codex.offlineledger.domain.LedgerLogic
import com.codex.offlineledger.domain.LockAction
import com.codex.offlineledger.domain.LockFeedback
import com.codex.offlineledger.domain.RecurrenceDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class LedgerRepository(
    private val database: AppDatabase,
) {
    private val dao = database.ledgerDao()
    private val json = Json { prettyPrint = true }

    val accounts: Flow<List<AccountEntity>> = dao.observeActiveAccounts()
    val allAccounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val snapshots = dao.observeSnapshots()
    val people = dao.observePeopleWithGifts()
    val todos = dao.observeTodos()
    val lockSettings = dao.observeAppLockSettings()
    val activeCategories = dao.observeActiveCategories()
    val allCategories = dao.observeAllCategories()
    val notes = dao.observeNotes()
    val activeTags: Flow<List<TagEntity>> = dao.observeActiveTags()
    val allTags: Flow<List<TagEntity>> = dao.observeAllTags()
    val snapshotTagJoin = dao.observeSnapshotTagJoin()

    // --- Accounts ---

    sealed class AccountNameStatus {
        data object Available : AccountNameStatus()
        data object ConflictActive : AccountNameStatus()
        data class ConflictArchived(val id: Long) : AccountNameStatus()
    }

    suspend fun checkAccountNameAvailability(name: String): AccountNameStatus {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return AccountNameStatus.Available
        val existing = dao.getAccountByNameIgnoreCase(trimmed) ?: return AccountNameStatus.Available
        return if (existing.archived) {
            AccountNameStatus.ConflictArchived(existing.id)
        } else {
            AccountNameStatus.ConflictActive
        }
    }

    suspend fun saveAccount(
        name: String,
        type: String,
        accountNumber: String,
        note: String,
        includeInNetWorth: Boolean,
    ): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("账户名不能为空"))
        val existing = dao.getAccountByNameIgnoreCase(trimmed)
        if (existing != null) {
            if (existing.archived) {
                return Result.failure(
                    AccountArchivedConflict(existing.id, existing.name),
                )
            }
            return Result.failure(IllegalArgumentException("已存在同名账户"))
        }
        val id = dao.upsertAccount(
            AccountEntity(
                name = trimmed,
                type = type.trim(),
                accountNumber = accountNumber.trim(),
                note = note.trim(),
                includeInNetWorth = includeInNetWorth,
            ),
        )
        return Result.success(id)
    }

    suspend fun updateAccount(
        id: Long,
        name: String,
        type: String,
        accountNumber: String,
        note: String,
        includeInNetWorth: Boolean,
    ): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("账户名不能为空"))
        val conflict = dao.getAccountByNameIgnoreCase(trimmed)
        if (conflict != null && conflict.id != id) {
            return Result.failure(IllegalArgumentException("已存在同名账户"))
        }
        val existing = dao.getAccounts().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("账户不存在"))
        dao.updateAccount(
            existing.copy(
                name = trimmed,
                type = type.trim(),
                accountNumber = accountNumber.trim(),
                note = note.trim(),
                includeInNetWorth = includeInNetWorth,
            ),
        )
        return Result.success(Unit)
    }

    suspend fun archiveAccount(id: Long) {
        dao.setAccountArchived(id, true)
    }

    suspend fun unarchiveAccount(id: Long) {
        dao.setAccountArchived(id, false)
    }

    suspend fun restoreAndUpdateAccount(
        id: Long,
        name: String,
        type: String,
        accountNumber: String,
        note: String,
        includeInNetWorth: Boolean,
    ): Result<Unit> {
        val existing = dao.getAccounts().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("账户不存在"))
        dao.updateAccount(
            existing.copy(
                name = name.trim(),
                type = type.trim(),
                accountNumber = accountNumber.trim(),
                note = note.trim(),
                includeInNetWorth = includeInNetWorth,
                archived = false,
            ),
        )
        return Result.success(Unit)
    }

    class AccountArchivedConflict(val id: Long, val name: String) :
        RuntimeException("已存在同名归档账户")

    // --- Expense Categories ---

    suspend fun saveExpenseCategory(name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("名称不能为空"))
        val existing = dao.getCategoryByName(trimmed)
        if (existing != null) {
            if (existing.archived) {
                dao.updateCategory(existing.copy(archived = false))
                return Result.success(existing.id)
            }
            return Result.failure(IllegalArgumentException("分类已存在"))
        }
        if (dao.countActiveCategories() >= 10) {
            return Result.failure(IllegalArgumentException("花销分类最多 10 个"))
        }
        val id = dao.insertCategory(
            ExpenseCategoryEntity(
                name = trimmed,
                sortOrder = (dao.getAllCategories().maxOfOrNull { it.sortOrder } ?: -1) + 1,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return Result.success(id)
    }

    suspend fun renameExpenseCategory(id: Long, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("名称不能为空"))
        val category = dao.getAllCategories().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("分类不存在"))
        val conflict = dao.getCategoryByName(trimmed)
        if (conflict != null && conflict.id != id) {
            return Result.failure(IllegalArgumentException("已存在同名分类"))
        }
        dao.updateCategory(category.copy(name = trimmed))
        return Result.success(Unit)
    }

    suspend fun archiveExpenseCategory(id: Long) {
        val category = dao.getAllCategories().firstOrNull { it.id == id } ?: return
        dao.updateCategory(category.copy(archived = true))
    }

    // --- Snapshots ---

    suspend fun saveSnapshot(
        snapshotDate: Long,
        nextRecordAt: Long?,
        targetTotal: Long?,
        debtLabel: String,
        debtAmount: Long?,
        note: String,
        balances: List<Pair<Long, Long>>,
        expenses: List<Pair<Long, Long>>,
        mood: Int? = null,
        tagIds: List<Long> = emptyList(),
    ) {
        database.withTransaction {
            val snapshotId = dao.insertSnapshot(
                SnapshotEntity(
                    snapshotDate = snapshotDate,
                    createdAt = System.currentTimeMillis(),
                    nextRecordAt = nextRecordAt,
                    targetTotal = targetTotal,
                    debtLabel = debtLabel.trim(),
                    debtAmount = debtAmount,
                    note = note.trim(),
                    mood = LedgerLogic.normalizeMood(mood),
                ),
            )
            dao.insertSnapshotBalances(
                balances.map { (accountId, amount) ->
                    SnapshotBalanceEntity(
                        snapshotId = snapshotId,
                        accountId = accountId,
                        amount = amount,
                    )
                },
            )
            dao.insertSnapshotExpenses(
                expenses.map { (categoryId, amount) ->
                    SnapshotExpenseEntity(
                        snapshotId = snapshotId,
                        categoryId = categoryId,
                        amount = amount,
                    )
                },
            )
            if (tagIds.isNotEmpty()) {
                dao.insertSnapshotTagRefs(
                    tagIds.distinct().map {
                        SnapshotTagCrossRef(snapshotId = snapshotId, tagId = it)
                    },
                )
            }
        }
    }

    suspend fun deleteSnapshot(id: Long) {
        dao.deleteSnapshot(id)
    }

    // --- Tags ---

    sealed class TagNameStatus {
        data object Available : TagNameStatus()
        data object ConflictActive : TagNameStatus()
        data class ConflictArchived(val id: Long) : TagNameStatus()
    }

    class TagInUseException(val referencedBy: Int) :
        RuntimeException("被 $referencedBy 条快照引用，只能归档")

    suspend fun checkTagNameAvailability(name: String): TagNameStatus {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return TagNameStatus.Available
        val existing = dao.getTagByNameIgnoreCase(trimmed) ?: return TagNameStatus.Available
        return if (existing.archived) {
            TagNameStatus.ConflictArchived(existing.id)
        } else {
            TagNameStatus.ConflictActive
        }
    }

    suspend fun saveTag(name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("标签名不能为空"))
        val existing = dao.getTagByNameIgnoreCase(trimmed)
        if (existing != null) {
            if (existing.archived) {
                dao.setTagArchived(existing.id, false)
            }
            return Result.success(existing.id)
        }
        val id = dao.insertTag(TagEntity(name = trimmed))
        return Result.success(id)
    }

    suspend fun renameTag(id: Long, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("标签名不能为空"))
        val current = dao.getAllTags().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("标签不存在"))
        val conflict = dao.getTagByNameIgnoreCase(trimmed)
        if (conflict != null && conflict.id != id) {
            return Result.failure(IllegalArgumentException("已存在同名标签"))
        }
        dao.updateTag(current.copy(name = trimmed))
        return Result.success(Unit)
    }

    suspend fun archiveTag(id: Long) {
        dao.setTagArchived(id, true)
    }

    suspend fun unarchiveTag(id: Long) {
        dao.setTagArchived(id, false)
    }

    suspend fun deleteTagIfUnused(id: Long): Result<Unit> {
        val count = dao.countSnapshotTagsByTag(id)
        if (count > 0) return Result.failure(TagInUseException(count))
        dao.deleteTagById(id)
        return Result.success(Unit)
    }

    suspend fun updateSnapshotAnnotation(
        snapshotId: Long,
        mood: Int?,
        note: String,
        tagIds: List<Long>,
    ): Result<Unit> = runCatching {
        val normalizedMood = LedgerLogic.normalizeMood(mood)
        val trimmedNote = note.trim()
        val targetSet = tagIds.distinct().toSet()
        database.withTransaction {
            dao.updateSnapshotMoodAndNote(snapshotId, normalizedMood, trimmedNote)
            val current = dao.getTagIdsForSnapshot(snapshotId).toSet()
            val toRemove = (current - targetSet).toList()
            val toAdd = (targetSet - current).toList()
            if (toRemove.isNotEmpty()) {
                dao.deleteSnapshotTagRefs(snapshotId, toRemove)
            }
            if (toAdd.isNotEmpty()) {
                dao.insertSnapshotTagRefs(
                    toAdd.map { SnapshotTagCrossRef(snapshotId = snapshotId, tagId = it) },
                )
            }
        }
    }

    // --- People ---

    suspend fun savePerson(
        name: String,
        birthdayMonth: Int?,
        birthdayDay: Int?,
        relation: String,
        note: String,
    ) {
        val minOrder = dao.getMinPersonSortOrder() ?: 0
        dao.upsertPerson(
            PersonEntity(
                name = name.trim(),
                birthdayMonth = birthdayMonth,
                birthdayDay = birthdayDay,
                relation = relation.trim(),
                note = note.trim(),
                sortOrder = minOrder - 1,
            ),
        )
    }

    suspend fun updatePerson(
        id: Long,
        name: String,
        birthdayMonth: Int?,
        birthdayDay: Int?,
        relation: String,
        note: String,
    ) {
        val existing = dao.getPeopleWithGifts().firstOrNull { it.person.id == id } ?: return
        dao.upsertPerson(
            existing.person.copy(
                name = name.trim(),
                birthdayMonth = birthdayMonth,
                birthdayDay = birthdayDay,
                relation = relation.trim(),
                note = note.trim(),
            ),
        )
    }

    suspend fun reorderPeople(orderedIds: List<Long>) {
        val people = dao.getPeopleWithGifts().associate { it.person.id to it.person }
        val updates = orderedIds.mapIndexedNotNull { index, id ->
            people[id]?.copy(sortOrder = index)
        }
        if (updates.isNotEmpty()) dao.updatePeople(updates)
    }

    suspend fun deletePerson(id: Long) {
        dao.deletePerson(id)
    }

    suspend fun saveGiftRecord(
        personId: Long,
        date: Long,
        direction: GiftDirection,
        giftName: String,
        price: Long,
        note: String,
    ) {
        dao.insertGiftRecord(
            GiftRecordEntity(
                personId = personId,
                date = date,
                direction = direction,
                giftName = giftName.trim(),
                price = price,
                note = note.trim(),
            ),
        )
    }

    // --- Notes ---

    suspend fun saveNote(title: String, body: String) {
        val now = System.currentTimeMillis()
        dao.insertNote(
            NoteEntity(
                title = title.trim(),
                body = body.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateNote(id: Long, title: String, body: String) {
        val existing = dao.getNotes().firstOrNull { it.id == id } ?: return
        dao.updateNote(
            existing.copy(
                title = title.trim(),
                body = body.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteNote(id: Long) {
        dao.deleteNote(id)
    }

    // --- Todos ---

    suspend fun saveTodo(
        title: String,
        description: String,
        dueAt: Long?,
        reminderAt: Long?,
        recurrence: RecurrenceDescriptor?,
    ) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            val initialTrigger = recurrence?.let {
                LedgerLogic.computeNextOccurrence(
                    descriptor = it,
                    anchorMillis = reminderAt ?: dueAt ?: now,
                    afterMillis = now - 1,
                )
            }
            val todoId = dao.insertTodo(
                TodoEntity(
                    title = title.trim(),
                    description = description.trim(),
                    createdAt = now,
                    dueAt = dueAt,
                    reminderAt = reminderAt,
                    nextTriggerAt = initialTrigger ?: reminderAt ?: dueAt,
                ),
            )
            if (recurrence == null || recurrence.mode == RecurrenceMode.NONE) {
                dao.deleteRecurrenceRule(todoId)
            } else {
                dao.upsertRecurrenceRule(
                    RecurrenceRuleEntity(
                        todoId = todoId,
                        mode = recurrence.mode,
                        interval = recurrence.interval,
                        daysOfWeekCsv = LedgerLogic.recurrenceToCsv(recurrence.daysOfWeek.map { it.value }.toSet()),
                        dayOfMonth = recurrence.dayOfMonth,
                        monthsCsv = LedgerLogic.recurrenceToCsv(recurrence.months.map { it.value }.toSet()),
                        hour = recurrence.hour,
                        minute = recurrence.minute,
                    ),
                )
            }
        }
    }

    suspend fun completeTodo(todoId: Long) {
        database.withTransaction {
            val todoWithRule = dao.getTodos().firstOrNull { it.todo.id == todoId } ?: return@withTransaction
            val descriptor = LedgerLogic.recurrenceFromEntity(todoWithRule.rule)
            val now = System.currentTimeMillis()
            if (descriptor == null) {
                dao.updateTodo(
                    todoWithRule.todo.copy(
                        isCompleted = true,
                        completedAt = now,
                        lastCompletedAt = now,
                    ),
                )
            } else {
                val anchor = todoWithRule.todo.nextTriggerAt ?: todoWithRule.todo.reminderAt ?: todoWithRule.todo.dueAt ?: now
                val next = LedgerLogic.computeNextOccurrence(descriptor, anchorMillis = anchor, afterMillis = now)
                dao.updateTodo(
                    todoWithRule.todo.copy(
                        isCompleted = false,
                        completedAt = null,
                        lastCompletedAt = now,
                        reminderAt = next,
                        nextTriggerAt = next,
                        lastNotifiedAt = null,
                    ),
                )
            }
        }
    }

    suspend fun reopenTodo(todoId: Long) {
        database.withTransaction {
            val todoWithRule = dao.getTodos().firstOrNull { it.todo.id == todoId } ?: return@withTransaction
            dao.updateTodo(
                todoWithRule.todo.copy(
                    isCompleted = false,
                    completedAt = null,
                ),
            )
        }
    }

    suspend fun deleteTodo(todoId: Long) {
        dao.deleteTodo(todoId)
    }

    // --- App Lock ---

    suspend fun setPassword(rawPassword: String) {
        dao.upsertAppLockSettings(
            AppLockSettingsEntity(
                id = 0,
                passwordHash = LedgerLogic.hashPassword(rawPassword),
                failedAttempts = 0,
            ),
        )
    }

    suspend fun verifyPassword(rawPassword: String): Boolean {
        val settings = dao.getAppLockSettings() ?: return false
        return LedgerLogic.hashPassword(rawPassword) == settings.passwordHash
    }

    suspend fun recordFailedUnlock(): LockFeedback {
        val current = dao.getAppLockSettings() ?: AppLockSettingsEntity()
        val next = current.copy(failedAttempts = current.failedAttempts + 1)
        dao.upsertAppLockSettings(next)
        return LedgerLogic.lockFeedback(next.failedAttempts)
    }

    suspend fun resetFailedUnlocks() {
        val current = dao.getAppLockSettings() ?: return
        dao.upsertAppLockSettings(current.copy(failedAttempts = 0))
    }

    suspend fun disablePassword() {
        dao.upsertAppLockSettings(
            AppLockSettingsEntity(
                id = 0,
                passwordHash = "",
                failedAttempts = 0,
            ),
        )
    }

    suspend fun wipeInternalData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    // --- Notifications ---

    suspend fun dueTodoNotifications(nowMillis: Long = System.currentTimeMillis()): List<TodoEntity> {
        val dueItems = dao.getDueTodos(nowMillis)
        return dueItems.mapNotNull { item ->
            val trigger = item.todo.nextTriggerAt ?: item.todo.reminderAt ?: item.todo.dueAt
            if (trigger != null && (item.todo.lastNotifiedAt == null || item.todo.lastNotifiedAt < trigger) && !item.todo.isCompleted) {
                item.todo
            } else {
                null
            }
        }
    }

    suspend fun markTodoNotified(todoId: Long, atMillis: Long) {
        val item = dao.getTodos().firstOrNull { it.todo.id == todoId } ?: return
        dao.updateTodo(item.todo.copy(lastNotifiedAt = atMillis))
    }

    // --- Export / Import ---

    suspend fun exportJson(outputStream: OutputStream) {
        val accounts = dao.getAccounts()
        val snapshots = dao.getSnapshots()
        val people = dao.getPeopleWithGifts()
        val todos = dao.getTodos()
        val categories = dao.getAllCategories()
        val notes = dao.getNotes()
        val lockSettings = dao.getAppLockSettings()
        val tags = dao.getAllTags()
        val snapshotTagRefs = dao.getAllSnapshotTagRefs()
        val bundle = ExportBundle(
            schemaVersion = 4,
            exportedAt = System.currentTimeMillis(),
            accounts = accounts.map {
                ExportAccount(
                    id = it.id, name = it.name, type = it.type,
                    accountNumber = it.accountNumber, note = it.note,
                    includeInNetWorth = it.includeInNetWorth,
                    archived = it.archived,
                )
            },
            snapshots = snapshots.map { snapshot ->
                ExportSnapshot(
                    id = snapshot.snapshot.id,
                    snapshotDate = snapshot.snapshot.snapshotDate,
                    createdAt = snapshot.snapshot.createdAt,
                    nextRecordAt = snapshot.snapshot.nextRecordAt,
                    targetTotal = snapshot.snapshot.targetTotal,
                    debtLabel = snapshot.snapshot.debtLabel,
                    debtAmount = snapshot.snapshot.debtAmount,
                    note = snapshot.snapshot.note,
                    balances = snapshot.balances.map {
                        ExportSnapshotBalance(accountId = it.accountId, amount = it.amount)
                    },
                    expenses = snapshot.expenses.map {
                        ExportSnapshotExpense(categoryId = it.categoryId, amount = it.amount)
                    },
                    mood = snapshot.snapshot.mood,
                )
            },
            people = people.map {
                ExportPerson(
                    id = it.person.id, name = it.person.name,
                    birthdayMonth = it.person.birthdayMonth, birthdayDay = it.person.birthdayDay,
                    relation = it.person.relation, note = it.person.note,
                    sortOrder = it.person.sortOrder,
                )
            },
            giftRecords = people.flatMap { person ->
                person.gifts.map {
                    ExportGiftRecord(
                        id = it.id, personId = it.personId, date = it.date,
                        direction = it.direction.name, giftName = it.giftName,
                        price = it.price, note = it.note,
                    )
                }
            },
            todos = todos.map {
                ExportTodo(
                    id = it.todo.id, title = it.todo.title,
                    description = it.todo.description, createdAt = it.todo.createdAt,
                    isCompleted = it.todo.isCompleted, dueAt = it.todo.dueAt,
                    reminderAt = it.todo.reminderAt, nextTriggerAt = it.todo.nextTriggerAt,
                    completedAt = it.todo.completedAt, lastCompletedAt = it.todo.lastCompletedAt,
                    recurrence = it.rule?.let { rule ->
                        ExportRecurrenceRule(
                            mode = rule.mode.name, interval = rule.interval,
                            daysOfWeekCsv = rule.daysOfWeekCsv, dayOfMonth = rule.dayOfMonth,
                            monthsCsv = rule.monthsCsv, hour = rule.hour, minute = rule.minute,
                        )
                    },
                )
            },
            expenseCategories = categories.map {
                ExportExpenseCategory(
                    id = it.id, name = it.name, sortOrder = it.sortOrder,
                    archived = it.archived, createdAt = it.createdAt,
                )
            },
            notes = notes.map {
                ExportNote(
                    id = it.id, title = it.title, body = it.body,
                    createdAt = it.createdAt, updatedAt = it.updatedAt,
                )
            },
            lock = ExportLock(passwordHash = lockSettings?.passwordHash.orEmpty()),
            tags = tags.map {
                ExportTag(id = it.id, name = it.name, archived = it.archived, sortOrder = it.sortOrder)
            },
            snapshotTags = snapshotTagRefs.map {
                ExportSnapshotTag(snapshotId = it.snapshotId, tagId = it.tagId)
            },
        )
        outputStream.writer().use { writer ->
            writer.write(json.encodeToString(ExportBundle.serializer(), bundle))
        }
    }

    suspend fun exportToUri(context: Context, uriString: String) {
        val uri = android.net.Uri.parse(uriString)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            exportJson(output)
        }
    }

    suspend fun importJson(inputStream: InputStream) {
        val bundle = inputStream.bufferedReader().use { reader ->
            json.decodeFromString(ExportBundle.serializer(), reader.readText())
        }
        database.withTransaction {
            database.clearAllTables()
            bundle.accounts.forEach {
                dao.upsertAccount(
                    AccountEntity(
                        id = it.id, name = it.name, type = it.type,
                        accountNumber = it.accountNumber, note = it.note,
                        includeInNetWorth = it.includeInNetWorth,
                        archived = it.archived,
                    ),
                )
            }
            bundle.expenseCategories.forEach {
                dao.insertCategory(
                    ExpenseCategoryEntity(
                        id = it.id, name = it.name, sortOrder = it.sortOrder,
                        archived = it.archived, createdAt = it.createdAt,
                    ),
                )
            }
            bundle.tags.forEach {
                dao.insertTag(
                    TagEntity(
                        id = it.id, name = it.name,
                        archived = it.archived, sortOrder = it.sortOrder,
                    ),
                )
            }
            bundle.snapshots.forEach { snapshot ->
                dao.insertSnapshot(
                    SnapshotEntity(
                        id = snapshot.id, snapshotDate = snapshot.snapshotDate,
                        createdAt = snapshot.createdAt, nextRecordAt = snapshot.nextRecordAt,
                        targetTotal = snapshot.targetTotal, debtLabel = snapshot.debtLabel,
                        debtAmount = snapshot.debtAmount, note = snapshot.note,
                        mood = snapshot.mood?.takeIf { it in 1..5 },
                    ),
                )
                dao.insertSnapshotBalances(
                    snapshot.balances.map {
                        SnapshotBalanceEntity(snapshotId = snapshot.id, accountId = it.accountId, amount = it.amount)
                    },
                )
                dao.insertSnapshotExpenses(
                    snapshot.expenses.map {
                        SnapshotExpenseEntity(snapshotId = snapshot.id, categoryId = it.categoryId, amount = it.amount)
                    },
                )
            }
            bundle.people.forEach { person ->
                dao.upsertPerson(
                    PersonEntity(
                        id = person.id, name = person.name,
                        birthdayMonth = person.birthdayMonth, birthdayDay = person.birthdayDay,
                        relation = person.relation, note = person.note,
                        sortOrder = person.sortOrder,
                    ),
                )
            }
            bundle.giftRecords.forEach { gift ->
                dao.insertGiftRecord(
                    GiftRecordEntity(
                        id = gift.id, personId = gift.personId, date = gift.date,
                        direction = GiftDirection.valueOf(gift.direction),
                        giftName = gift.giftName, price = gift.price, note = gift.note,
                    ),
                )
            }
            bundle.todos.forEach { todo ->
                dao.insertTodo(
                    TodoEntity(
                        id = todo.id, title = todo.title, description = todo.description,
                        createdAt = todo.createdAt, isCompleted = todo.isCompleted,
                        dueAt = todo.dueAt, reminderAt = todo.reminderAt,
                        nextTriggerAt = todo.nextTriggerAt, completedAt = todo.completedAt,
                        lastCompletedAt = todo.lastCompletedAt,
                    ),
                )
                todo.recurrence?.let { rule ->
                    dao.upsertRecurrenceRule(
                        RecurrenceRuleEntity(
                            todoId = todo.id, mode = RecurrenceMode.valueOf(rule.mode),
                            interval = rule.interval, daysOfWeekCsv = rule.daysOfWeekCsv,
                            dayOfMonth = rule.dayOfMonth, monthsCsv = rule.monthsCsv,
                            hour = rule.hour, minute = rule.minute,
                        ),
                    )
                }
            }
            bundle.notes.forEach { note ->
                dao.insertNote(
                    NoteEntity(
                        id = note.id, title = note.title, body = note.body,
                        createdAt = note.createdAt, updatedAt = note.updatedAt,
                    ),
                )
            }
            if (bundle.snapshotTags.isNotEmpty()) {
                dao.insertSnapshotTagRefs(
                    bundle.snapshotTags.map {
                        SnapshotTagCrossRef(snapshotId = it.snapshotId, tagId = it.tagId)
                    },
                )
            }
            val importedHash = bundle.lock?.passwordHash.orEmpty()
            dao.upsertAppLockSettings(
                AppLockSettingsEntity(
                    id = 0,
                    passwordHash = importedHash,
                    failedAttempts = 0,
                ),
            )
        }
    }

    suspend fun importFromUri(context: Context, uriString: String) {
        val uri = android.net.Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
            importJson(input)
        }
    }
}
