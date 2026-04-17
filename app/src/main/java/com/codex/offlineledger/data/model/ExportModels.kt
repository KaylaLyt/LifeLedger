package com.codex.offlineledger.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportBundle(
    val schemaVersion: Int,
    val exportedAt: Long,
    val accounts: List<ExportAccount>,
    val snapshots: List<ExportSnapshot>,
    val people: List<ExportPerson>,
    val giftRecords: List<ExportGiftRecord>,
    val todos: List<ExportTodo>,
    val expenseCategories: List<ExportExpenseCategory>,
    val notes: List<ExportNote>,
    val lock: ExportLock? = null,
    val tags: List<ExportTag> = emptyList(),
    val snapshotTags: List<ExportSnapshotTag> = emptyList(),
)

@Serializable
data class ExportAccount(
    val id: Long,
    val name: String,
    val type: String,
    val accountNumber: String,
    val note: String,
    val includeInNetWorth: Boolean,
    val archived: Boolean = false,
)

@Serializable
data class ExportLock(
    val passwordHash: String = "",
)

@Serializable
data class ExportSnapshot(
    val id: Long,
    val snapshotDate: Long,
    val createdAt: Long,
    val nextRecordAt: Long?,
    val targetTotal: Long?,
    val debtLabel: String,
    val debtAmount: Long?,
    val note: String,
    val balances: List<ExportSnapshotBalance>,
    val expenses: List<ExportSnapshotExpense>,
    val mood: Int? = null,
)

@Serializable
data class ExportSnapshotBalance(
    val accountId: Long,
    val amount: Long,
)

@Serializable
data class ExportSnapshotExpense(
    val categoryId: Long,
    val amount: Long,
)

@Serializable
data class ExportPerson(
    val id: Long,
    val name: String,
    val birthdayMonth: Int? = null,
    val birthdayDay: Int? = null,
    val relation: String,
    val note: String,
    val sortOrder: Int,
)

@Serializable
data class ExportGiftRecord(
    val id: Long,
    val personId: Long,
    val date: Long,
    val direction: String,
    val giftName: String,
    val price: Long,
    val note: String,
)

@Serializable
data class ExportTodo(
    val id: Long,
    val title: String,
    val description: String,
    val createdAt: Long,
    val isCompleted: Boolean,
    val dueAt: Long?,
    val reminderAt: Long?,
    val nextTriggerAt: Long?,
    val completedAt: Long?,
    val lastCompletedAt: Long?,
    val recurrence: ExportRecurrenceRule?,
)

@Serializable
data class ExportRecurrenceRule(
    val mode: String,
    val interval: Int,
    val daysOfWeekCsv: String,
    val dayOfMonth: Int?,
    val monthsCsv: String,
    val hour: Int?,
    val minute: Int?,
)

@Serializable
data class ExportExpenseCategory(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
)

@Serializable
data class ExportNote(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ExportTag(
    val id: Long,
    val name: String,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class ExportSnapshotTag(
    val snapshotId: Long,
    val tagId: Long,
)
