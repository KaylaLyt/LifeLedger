package com.codex.offlineledger.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.codex.offlineledger.data.AppDatabase
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
import com.codex.offlineledger.data.model.ExportAccount
import com.codex.offlineledger.data.model.ExportBundle
import com.codex.offlineledger.data.model.ExportGiftRecord
import com.codex.offlineledger.data.model.ExportNote
import com.codex.offlineledger.data.model.ExportNoteCategory
import com.codex.offlineledger.data.model.ExportPerson
import com.codex.offlineledger.data.model.ExportRecurrenceRule
import com.codex.offlineledger.data.model.ExportSnapshot
import com.codex.offlineledger.data.model.ExportSnapshotBalance
import com.codex.offlineledger.data.model.ExportSnapshotExpense
import com.codex.offlineledger.data.model.ExportTodo
import com.codex.offlineledger.domain.LedgerLogic
import com.codex.offlineledger.domain.LockAction
import com.codex.offlineledger.domain.LockFeedback
import com.codex.offlineledger.domain.RecurrenceDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

class LedgerRepository(
    private val database: AppDatabase,
) {
    private val dao = database.ledgerDao()
    private val json = Json { prettyPrint = true }

    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val snapshots = dao.observeSnapshots()
    val people = dao.observePeopleWithGifts()
    val todos = dao.observeTodos()
    val lockSettings = dao.observeAppLockSettings()

    suspend fun saveAccount(
        name: String,
        type: String,
        accountNumber: String,
        note: String,
        includeInNetWorth: Boolean,
    ) {
        dao.upsertAccount(
            AccountEntity(
                name = name.trim(),
                type = type.trim(),
                accountNumber = accountNumber.trim(),
                note = note.trim(),
                includeInNetWorth = includeInNetWorth,
            ),
        )
    }

    suspend fun saveSnapshot(
        snapshotDate: Long,
        nextRecordAt: Long?,
        targetTotalInCents: Long?,
        debtLabel: String,
        debtAmountInCents: Long?,
        note: String,
        balances: List<Pair<Long, Long>>,
        expenses: List<Pair<String, Long>>,
    ) {
        require(LedgerLogic.validateExpenseCategoryCount(expenses.size)) { "花销分类最多 10 个" }
        database.withTransaction {
            val snapshotId = dao.insertSnapshot(
                SnapshotEntity(
                    snapshotDate = snapshotDate,
                    createdAt = System.currentTimeMillis(),
                    nextRecordAt = nextRecordAt,
                    targetTotalInCents = targetTotalInCents,
                    debtLabel = debtLabel.trim(),
                    debtAmountInCents = debtAmountInCents,
                    note = note.trim(),
                ),
            )
            dao.insertSnapshotBalances(
                balances.map { (accountId, amountInCents) ->
                    SnapshotBalanceEntity(
                        snapshotId = snapshotId,
                        accountId = accountId,
                        amountInCents = amountInCents,
                    )
                },
            )
            dao.insertSnapshotExpenses(
                expenses.map { (categoryName, amountInCents) ->
                    SnapshotExpenseEntity(
                        snapshotId = snapshotId,
                        categoryName = categoryName.trim(),
                        amountInCents = amountInCents,
                    )
                },
            )
        }
    }

    suspend fun savePerson(
        name: String,
        birthdayMonth: Int,
        birthdayDay: Int,
        relation: String,
        note: String,
    ) {
        dao.upsertPerson(
            PersonEntity(
                name = name.trim(),
                birthdayMonth = birthdayMonth,
                birthdayDay = birthdayDay,
                relation = relation.trim(),
                note = note.trim(),
            ),
        )
    }

    suspend fun saveGiftRecord(
        personId: Long,
        date: Long,
        direction: GiftDirection,
        giftName: String,
        priceInCents: Long,
        note: String,
    ) {
        dao.insertGiftRecord(
            GiftRecordEntity(
                personId = personId,
                date = date,
                direction = direction,
                giftName = giftName.trim(),
                priceInCents = priceInCents,
                note = note.trim(),
            ),
        )
    }

    suspend fun saveTodo(
        title: String,
        description: String,
        dueAt: Long?,
        reminderAt: Long?,
        recurrence: RecurrenceDescriptor?,
        sourceType: String? = null,
        sourceRefId: Long? = null,
        sourceCycleKey: String? = null,
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
                    sourceType = sourceType,
                    sourceRefId = sourceRefId,
                    sourceCycleKey = sourceCycleKey,
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

    suspend fun wipeInternalData() {
        database.clearAllTables()
    }

    suspend fun generateBirthdayTodos(today: LocalDate = LocalDate.now()): Int {
        val zoneId = ZoneId.systemDefault()
        var created = 0
        val birthdayInTwoWeeks = today.plusDays(14)
        val peopleWithBirthday = dao.getPeopleByBirthday(
            month = birthdayInTwoWeeks.monthValue,
            day = birthdayInTwoWeeks.dayOfMonth,
        )
        peopleWithBirthday.forEach { person ->
            val cycleKey = "${birthdayInTwoWeeks.year}-${person.id}"
            if (!dao.hasGeneratedTodo("birthday-gift", person.id, cycleKey)) {
                val reminder = today.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
                saveTodo(
                    title = "给 ${person.name} 准备礼物",
                    description = "生日临近，提前两周准备礼物。",
                    dueAt = reminder,
                    reminderAt = reminder,
                    recurrence = null,
                    sourceType = "birthday-gift",
                    sourceRefId = person.id,
                    sourceCycleKey = cycleKey,
                )
                created += 1
            }
        }
        return created
    }

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

    suspend fun exportJson(outputStream: OutputStream) {
        val accounts = dao.getAccounts()
        val snapshots = dao.getSnapshots()
        val people = dao.getPeopleWithGifts()
        val todos = dao.getTodos()
        val noteCategories = dao.getNoteCategories()
        val notes = dao.getNotes()
        val bundle = ExportBundle(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            accounts = accounts.map {
                ExportAccount(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    accountNumber = it.accountNumber,
                    note = it.note,
                    includeInNetWorth = it.includeInNetWorth,
                )
            },
            snapshots = snapshots.map { snapshot ->
                ExportSnapshot(
                    id = snapshot.snapshot.id,
                    snapshotDate = snapshot.snapshot.snapshotDate,
                    createdAt = snapshot.snapshot.createdAt,
                    nextRecordAt = snapshot.snapshot.nextRecordAt,
                    targetTotalInCents = snapshot.snapshot.targetTotalInCents,
                    debtLabel = snapshot.snapshot.debtLabel,
                    debtAmountInCents = snapshot.snapshot.debtAmountInCents,
                    note = snapshot.snapshot.note,
                    balances = snapshot.balances.map {
                        ExportSnapshotBalance(
                            accountId = it.accountId,
                            amountInCents = it.amountInCents,
                        )
                    },
                    expenses = snapshot.expenses.map {
                        ExportSnapshotExpense(
                            categoryName = it.categoryName,
                            amountInCents = it.amountInCents,
                        )
                    },
                )
            },
            people = people.map {
                ExportPerson(
                    id = it.person.id,
                    name = it.person.name,
                    birthdayMonth = it.person.birthdayMonth,
                    birthdayDay = it.person.birthdayDay,
                    relation = it.person.relation,
                    note = it.person.note,
                )
            },
            giftRecords = people.flatMap { person ->
                person.gifts.map {
                    ExportGiftRecord(
                        id = it.id,
                        personId = it.personId,
                        date = it.date,
                        direction = it.direction.name,
                        giftName = it.giftName,
                        priceInCents = it.priceInCents,
                        note = it.note,
                    )
                }
            },
            todos = todos.map {
                ExportTodo(
                    id = it.todo.id,
                    title = it.todo.title,
                    description = it.todo.description,
                    createdAt = it.todo.createdAt,
                    isCompleted = it.todo.isCompleted,
                    dueAt = it.todo.dueAt,
                    reminderAt = it.todo.reminderAt,
                    nextTriggerAt = it.todo.nextTriggerAt,
                    completedAt = it.todo.completedAt,
                    lastCompletedAt = it.todo.lastCompletedAt,
                    sourceType = it.todo.sourceType,
                    sourceRefId = it.todo.sourceRefId,
                    sourceCycleKey = it.todo.sourceCycleKey,
                    recurrence = it.rule?.let { rule ->
                        ExportRecurrenceRule(
                            mode = rule.mode.name,
                            interval = rule.interval,
                            daysOfWeekCsv = rule.daysOfWeekCsv,
                            dayOfMonth = rule.dayOfMonth,
                            monthsCsv = rule.monthsCsv,
                            hour = rule.hour,
                            minute = rule.minute,
                        )
                    },
                )
            },
            noteCategories = noteCategories.map {
                ExportNoteCategory(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    sortOrder = it.sortOrder,
                )
            },
            notes = notes.map {
                ExportNote(
                    id = it.id,
                    categoryId = it.categoryId,
                    body = it.body,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
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
                        id = it.id,
                        name = it.name,
                        type = it.type,
                        accountNumber = it.accountNumber,
                        note = it.note,
                        includeInNetWorth = it.includeInNetWorth,
                    ),
                )
            }
            bundle.snapshots.forEach { snapshot ->
                dao.insertSnapshot(
                    SnapshotEntity(
                        id = snapshot.id,
                        snapshotDate = snapshot.snapshotDate,
                        createdAt = snapshot.createdAt,
                        nextRecordAt = snapshot.nextRecordAt,
                        targetTotalInCents = snapshot.targetTotalInCents,
                        debtLabel = snapshot.debtLabel,
                        debtAmountInCents = snapshot.debtAmountInCents,
                        note = snapshot.note,
                    ),
                )
                dao.insertSnapshotBalances(
                    snapshot.balances.map {
                        SnapshotBalanceEntity(
                            snapshotId = snapshot.id,
                            accountId = it.accountId,
                            amountInCents = it.amountInCents,
                        )
                    },
                )
                dao.insertSnapshotExpenses(
                    snapshot.expenses.map {
                        SnapshotExpenseEntity(
                            snapshotId = snapshot.id,
                            categoryName = it.categoryName,
                            amountInCents = it.amountInCents,
                        )
                    },
                )
            }
            bundle.people.forEach { person ->
                dao.upsertPerson(
                    PersonEntity(
                        id = person.id,
                        name = person.name,
                        birthdayMonth = person.birthdayMonth,
                        birthdayDay = person.birthdayDay,
                        relation = person.relation,
                        note = person.note,
                    ),
                )
            }
            bundle.giftRecords.forEach { gift ->
                dao.insertGiftRecord(
                    GiftRecordEntity(
                        id = gift.id,
                        personId = gift.personId,
                        date = gift.date,
                        direction = GiftDirection.valueOf(gift.direction),
                        giftName = gift.giftName,
                        priceInCents = gift.priceInCents,
                        note = gift.note,
                    ),
                )
            }
            bundle.noteCategories.forEach { category ->
                dao.insertNoteCategory(
                    NoteCategoryEntity(
                        id = category.id,
                        name = category.name,
                        createdAt = category.createdAt,
                        sortOrder = category.sortOrder,
                    )
                )
            }
            bundle.notes.forEach { note ->
                dao.insertNote(
                    NoteEntity(
                        id = note.id,
                        categoryId = note.categoryId,
                        body = note.body,
                        createdAt = note.createdAt,
                        updatedAt = note.updatedAt,
                    )
                )
            }
            bundle.todos.forEach { todo ->
                dao.insertTodo(
                    TodoEntity(
                        id = todo.id,
                        title = todo.title,
                        description = todo.description,
                        createdAt = todo.createdAt,
                        isCompleted = todo.isCompleted,
                        dueAt = todo.dueAt,
                        reminderAt = todo.reminderAt,
                        nextTriggerAt = todo.nextTriggerAt,
                        completedAt = todo.completedAt,
                        lastCompletedAt = todo.lastCompletedAt,
                        sourceType = todo.sourceType,
                        sourceRefId = todo.sourceRefId,
                        sourceCycleKey = todo.sourceCycleKey,
                    ),
                )
                todo.recurrence?.let { rule ->
                    dao.upsertRecurrenceRule(
                        RecurrenceRuleEntity(
                            todoId = todo.id,
                            mode = RecurrenceMode.valueOf(rule.mode),
                            interval = rule.interval,
                            daysOfWeekCsv = rule.daysOfWeekCsv,
                            dayOfMonth = rule.dayOfMonth,
                            monthsCsv = rule.monthsCsv,
                            hour = rule.hour,
                            minute = rule.minute,
                        ),
                    )
                }
            }
        }
    }

    suspend fun importFromUri(context: Context, uriString: String) {
        val uri = android.net.Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { input ->
            importJson(input)
        }
    }
}
