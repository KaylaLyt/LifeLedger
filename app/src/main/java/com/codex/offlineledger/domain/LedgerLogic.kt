package com.codex.offlineledger.domain

import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.model.PersonWithGifts
import com.codex.offlineledger.data.model.SnapshotWithDetails
import com.codex.offlineledger.data.model.TodoWithRule
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

object LedgerLogic {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parseCurrencyToCents(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        return runCatching {
            BigDecimal(trimmed).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    fun formatCurrency(cents: Long?): String {
        if (cents == null) return "-"
        val amount = BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
        return amount.toPlainString()
    }

    fun formatDate(millis: Long?): String {
        if (millis == null) return "-"
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().format(dateFormatter)
    }

    fun formatDateTime(millis: Long?): String {
        if (millis == null) return "-"
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime().format(dateTimeFormatter)
    }

    fun parseOptionalDate(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            LocalDate.parse(trimmed, dateFormatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun parseOptionalDateTime(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            LocalDateTime.parse(trimmed, dateTimeFormatter).atZone(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }

    fun parseDateOrNow(text: String, nowMillis: Long = System.currentTimeMillis()): Long {
        return parseOptionalDate(text) ?: nowMillis
    }

    fun maskAccountNumber(accountNumber: String): String {
        if (accountNumber.length <= 4) return accountNumber
        val suffix = accountNumber.takeLast(4)
        return "•••• $suffix"
    }

    fun hashPassword(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isGoalReached(totalInCents: Long, targetInCents: Long?): Boolean? {
        return targetInCents?.let { totalInCents >= it }
    }

    fun validateExpenseCategoryCount(count: Int): Boolean = count <= 10

    fun lockFeedback(failedAttempts: Int, threshold: Int = 5): LockFeedback {
        val remaining = max(threshold - failedAttempts, 0)
        val action = when {
            failedAttempts >= threshold -> LockAction.WIPE_DATA
            failedAttempts == threshold - 1 -> LockAction.WARN_FINAL_ATTEMPT
            else -> LockAction.KEEP_LOCKED
        }
        return LockFeedback(
            failedAttempts = failedAttempts,
            attemptsRemaining = remaining,
            action = action,
        )
    }

    fun shouldGenerateBirthdayTodo(today: LocalDate, birthdayMonth: Int, birthdayDay: Int): Boolean {
        val thisYearBirthday = runCatching { LocalDate.of(today.year, birthdayMonth, birthdayDay) }.getOrNull()
            ?: return false
        val targetBirthday = if (thisYearBirthday.isBefore(today)) {
            runCatching { LocalDate.of(today.year + 1, birthdayMonth, birthdayDay) }.getOrNull() ?: return false
        } else {
            thisYearBirthday
        }
        return targetBirthday.minusDays(14) == today
    }

    fun recurrenceFromEntity(entity: RecurrenceRuleEntity?): RecurrenceDescriptor? {
        if (entity == null || entity.mode == RecurrenceMode.NONE) return null
        return RecurrenceDescriptor(
            mode = entity.mode,
            interval = entity.interval,
            daysOfWeek = entity.daysOfWeekCsv.split(",").mapNotNull {
                it.toIntOrNull()?.let(DayOfWeek::of)
            }.toSet(),
            dayOfMonth = entity.dayOfMonth,
            months = entity.monthsCsv.split(",").mapNotNull {
                it.toIntOrNull()?.let(Month::of)
            }.toSet(),
            hour = entity.hour,
            minute = entity.minute,
        )
    }

    fun recurrenceToCsv(values: Set<Int>): String = values.sorted().joinToString(",")

    fun computeNextOccurrence(
        descriptor: RecurrenceDescriptor?,
        anchorMillis: Long,
        afterMillis: Long = System.currentTimeMillis(),
    ): Long? {
        if (descriptor == null || descriptor.mode == RecurrenceMode.NONE) return null
        val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zoneId)
        val after = Instant.ofEpochMilli(afterMillis).atZone(zoneId)
        val hour = descriptor.hour ?: anchor.hour
        val minute = descriptor.minute ?: anchor.minute
        return when (descriptor.mode) {
            RecurrenceMode.DAILY -> {
                var candidate = anchor.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
                while (!candidate.isAfter(after)) {
                    candidate = candidate.plusDays(descriptor.interval.toLong())
                }
                candidate.toInstant().toEpochMilli()
            }

            RecurrenceMode.WEEKLY -> {
                val validDays = if (descriptor.daysOfWeek.isEmpty()) setOf(anchor.dayOfWeek) else descriptor.daysOfWeek
                var cursor = after.toLocalDate()
                repeat(366) {
                    val candidate = cursor.atTime(hour, minute).atZone(zoneId)
                    val weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(
                        anchor.toLocalDate().with(DayOfWeek.MONDAY),
                        cursor.with(DayOfWeek.MONDAY),
                    )
                    if (
                        candidate.isAfter(after) &&
                        validDays.contains(cursor.dayOfWeek) &&
                        weeksBetween >= 0 &&
                        weeksBetween % descriptor.interval == 0L
                    ) {
                        return candidate.toInstant().toEpochMilli()
                    }
                    cursor = cursor.plusDays(1)
                }
                null
            }

            RecurrenceMode.MONTHLY -> {
                val day = descriptor.dayOfMonth ?: anchor.dayOfMonth
                var candidateMonth = anchor.withDayOfMonth(1)
                repeat(60) {
                    val candidateDay = minOf(day, candidateMonth.toLocalDate().lengthOfMonth())
                    val candidate = candidateMonth.withDayOfMonth(candidateDay)
                        .withHour(hour)
                        .withMinute(minute)
                        .withSecond(0)
                        .withNano(0)
                    val monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(
                        anchor.withDayOfMonth(1),
                        candidateMonth,
                    )
                    if (candidate.isAfter(after) && monthsBetween >= 0 && monthsBetween % descriptor.interval == 0L) {
                        return candidate.toInstant().toEpochMilli()
                    }
                    candidateMonth = candidateMonth.plusMonths(1)
                }
                null
            }

            RecurrenceMode.ADVANCED -> {
                var cursor = after.toLocalDate()
                repeat(730) {
                    val candidate = cursor.atTime(hour, minute).atZone(zoneId)
                    val monthsMatch = descriptor.months.isEmpty() || descriptor.months.contains(cursor.month)
                    val daysMatch = descriptor.daysOfWeek.isEmpty() || descriptor.daysOfWeek.contains(cursor.dayOfWeek)
                    val dayOfMonthMatch = descriptor.dayOfMonth == null || descriptor.dayOfMonth == cursor.dayOfMonth
                    val intervalMatch = java.time.temporal.ChronoUnit.DAYS.between(anchor.toLocalDate(), cursor) >= 0 &&
                        java.time.temporal.ChronoUnit.DAYS.between(anchor.toLocalDate(), cursor) % descriptor.interval == 0L
                    if (candidate.isAfter(after) && monthsMatch && daysMatch && dayOfMonthMatch && intervalMatch) {
                        return candidate.toInstant().toEpochMilli()
                    }
                    cursor = cursor.plusDays(1)
                }
                null
            }

            RecurrenceMode.NONE -> null
        }
    }

    fun buildSnapshotSummaries(
        accounts: List<AccountEntity>,
        snapshots: List<SnapshotWithDetails>,
    ): List<SnapshotSummary> {
        val includedAccountMap = accounts.associateBy { it.id }
        val chronologic = snapshots.sortedBy { it.snapshot.snapshotDate }
        val totalsById = mutableMapOf<Long, Long>()
        chronologic.forEach { details ->
            totalsById[details.snapshot.id] = details.balances
                .filter { includedAccountMap[it.accountId]?.includeInNetWorth == true }
                .sumOf { it.amountInCents }
        }
        val previousById = chronologic.zipWithNext().associate { (previous, current) ->
            current.snapshot.id to (totalsById[current.snapshot.id] ?: 0L) - (totalsById[previous.snapshot.id] ?: 0L)
        }
        val previousBalances = mutableMapOf<Long, Map<Long, Long>>()
        chronologic.forEachIndexed { index, details ->
            val prev = chronologic.getOrNull(index - 1)?.balances?.associate { it.accountId to it.amountInCents }.orEmpty()
            previousBalances[details.snapshot.id] = prev
        }
        return chronologic.reversed().map { details ->
            val total = totalsById[details.snapshot.id] ?: 0L
            val previous = previousBalances[details.snapshot.id].orEmpty()
            SnapshotSummary(
                id = details.snapshot.id,
                snapshotDate = details.snapshot.snapshotDate,
                totalInCents = total,
                changeFromPreviousInCents = previousById[details.snapshot.id],
                targetTotalInCents = details.snapshot.targetTotalInCents,
                goalReached = isGoalReached(total, details.snapshot.targetTotalInCents),
                debtLabel = details.snapshot.debtLabel,
                debtAmountInCents = details.snapshot.debtAmountInCents,
                nextRecordAt = details.snapshot.nextRecordAt,
                note = details.snapshot.note,
                balances = details.balances.map { balance ->
                    AccountBalanceSummary(
                        accountId = balance.accountId,
                        accountName = includedAccountMap[balance.accountId]?.name ?: "账户 ${balance.accountId}",
                        amountInCents = balance.amountInCents,
                        deltaFromPreviousInCents = previous[balance.accountId]?.let { balance.amountInCents - it },
                    )
                }.sortedBy { it.accountName },
                expenses = details.expenses.map { ExpenseSummary(it.categoryName, it.amountInCents) }
                    .sortedByDescending { it.amountInCents },
            )
        }
    }

    fun mapPeople(people: List<PersonWithGifts>): List<PersonLedgerSummary> {
        return people.map { personWithGifts ->
            PersonLedgerSummary(
                id = personWithGifts.person.id,
                name = personWithGifts.person.name,
                birthdayMonth = personWithGifts.person.birthdayMonth,
                birthdayDay = personWithGifts.person.birthdayDay,
                relation = personWithGifts.person.relation,
                note = personWithGifts.person.note,
                gifts = personWithGifts.gifts.sortedByDescending { it.date }.map {
                    GiftSummary(
                        id = it.id,
                        personId = it.personId,
                        date = it.date,
                        directionLabel = if (it.direction.name == "SENT") "我送出" else "对方送我",
                        giftName = it.giftName,
                        priceInCents = it.priceInCents,
                        note = it.note,
                    )
                },
            )
        }
    }

    fun mapTodos(todos: List<TodoWithRule>): List<TodoSummary> {
        return todos.map {
            TodoSummary(
                id = it.todo.id,
                title = it.todo.title,
                description = it.todo.description,
                isCompleted = it.todo.isCompleted,
                dueAt = it.todo.dueAt,
                reminderAt = it.todo.reminderAt,
                nextTriggerAt = it.todo.nextTriggerAt,
                completedAt = it.todo.completedAt,
                lastCompletedAt = it.todo.lastCompletedAt,
                recurrence = recurrenceFromEntity(it.rule),
                sourceType = it.todo.sourceType,
            )
        }
    }
}
