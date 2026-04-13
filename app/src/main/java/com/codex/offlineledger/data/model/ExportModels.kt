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
    val noteCategories: List<ExportNoteCategory>,
    val notes: List<ExportNote>,
)

@Serializable
data class ExportAccount(
    val id: Long,
    val name: String,
    val type: String,
    val accountNumber: String,
    val note: String,
    val includeInNetWorth: Boolean,
)

@Serializable
data class ExportSnapshot(
    val id: Long,
    val snapshotDate: Long,
    val createdAt: Long,
    val nextRecordAt: Long?,
    val targetTotalInCents: Long?,
    val debtLabel: String,
    val debtAmountInCents: Long?,
    val note: String,
    val balances: List<ExportSnapshotBalance>,
    val expenses: List<ExportSnapshotExpense>,
)

@Serializable
data class ExportSnapshotBalance(
    val accountId: Long,
    val amountInCents: Long,
)

@Serializable
data class ExportSnapshotExpense(
    val categoryName: String,
    val amountInCents: Long,
)

@Serializable
data class ExportPerson(
    val id: Long,
    val name: String,
    val birthdayMonth: Int,
    val birthdayDay: Int,
    val relation: String,
    val note: String,
)

@Serializable
data class ExportGiftRecord(
    val id: Long,
    val personId: Long,
    val date: Long,
    val direction: String,
    val giftName: String,
    val priceInCents: Long,
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
    val sourceType: String?,
    val sourceRefId: Long?,
    val sourceCycleKey: String?,
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
data class ExportNoteCategory(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
)

@Serializable
data class ExportNote(
    val id: Long,
    val categoryId: Long?,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)
