package com.codex.offlineledger.domain

import com.codex.offlineledger.data.entity.RecurrenceMode
import java.time.DayOfWeek
import java.time.Month

data class SnapshotSummary(
    val id: Long,
    val snapshotDate: Long,
    val total: Long,
    val changeFromPrevious: Long? = null,
    val targetTotal: Long? = null,
    val goalReached: Boolean? = null,
    val debtLabel: String,
    val debtAmount: Long?,
    val nextRecordAt: Long?,
    val note: String,
    val mood: Int? = null,
    val tags: List<TagSummary> = emptyList(),
    val balances: List<AccountBalanceSummary>,
    val expenses: List<ExpenseSummary>,
)

data class TagSummary(
    val id: Long,
    val name: String,
    val archived: Boolean = false,
)

data class AccountBalanceSummary(
    val accountId: Long,
    val accountName: String,
    val amount: Long,
    val deltaFromPrevious: Long? = null,
)

data class ExpenseSummary(
    val categoryId: Long,
    val categoryName: String,
    val amount: Long,
)

data class PersonLedgerSummary(
    val id: Long,
    val name: String,
    val birthdayMonth: Int?,
    val birthdayDay: Int?,
    val relation: String,
    val note: String,
    val sortOrder: Int,
    val gifts: List<GiftSummary>,
)

data class GiftSummary(
    val id: Long,
    val personId: Long,
    val date: Long,
    val directionLabel: String,
    val giftName: String,
    val price: Long,
    val note: String,
)

data class RecurrenceDescriptor(
    val mode: RecurrenceMode,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val months: Set<Month> = emptySet(),
    val hour: Int? = null,
    val minute: Int? = null,
)

data class TodoSummary(
    val id: Long,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val dueAt: Long?,
    val reminderAt: Long?,
    val nextTriggerAt: Long?,
    val completedAt: Long?,
    val lastCompletedAt: Long?,
    val recurrence: RecurrenceDescriptor?,
)

enum class LockAction {
    KEEP_LOCKED,
    WARN_FINAL_ATTEMPT,
    WIPE_DATA,
}

data class LockFeedback(
    val failedAttempts: Int,
    val attemptsRemaining: Int,
    val action: LockAction,
)
