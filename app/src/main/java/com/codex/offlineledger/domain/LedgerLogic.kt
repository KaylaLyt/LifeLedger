package com.codex.offlineledger.domain

import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.ExpenseCategoryEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.RecurrenceRuleEntity
import com.codex.offlineledger.data.entity.TagEntity
import com.codex.offlineledger.data.model.PersonWithGifts
import com.codex.offlineledger.data.model.SnapshotWithDetails
import com.codex.offlineledger.data.model.TodoWithRule
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

enum class CurrencyUnit(val multiplier: Long, val label: String) {
    YUAN(1, "元"),
    THOUSAND(1_000, "千"),
    TEN_THOUSAND(10_000, "万"),
}

object LedgerLogic {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun parseCurrency(text: String, unit: CurrencyUnit = CurrencyUnit.YUAN): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        val value = trimmed.toLongOrNull() ?: return null
        return value * unit.multiplier
    }

    fun formatCurrencySmart(yuan: Long?): String {
        if (yuan == null) return "-"
        if (yuan == 0L) return "0"
        val abs = kotlin.math.abs(yuan)
        val prefix = if (yuan < 0) "-" else ""
        return when {
            abs % 10_000 == 0L -> "$prefix${abs / 10_000}万"
            abs % 1_000 == 0L -> "$prefix${abs / 1_000}千"
            else -> "$prefix${NumberFormat.getNumberInstance(Locale.getDefault()).format(abs)}元"
        }
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

    fun isGoalReached(total: Long, target: Long?): Boolean? {
        return target?.let { total >= it }
    }

    fun validateExpenseCategoryCount(count: Int): Boolean = count <= 10

    fun normalizeMood(mood: Int?): Int? = mood?.takeIf { it in 1..5 }

    fun moodEmoji(mood: Int?): String? = when (normalizeMood(mood)) {
        1 -> "😢"
        2 -> "😕"
        3 -> "😐"
        4 -> "🙂"
        5 -> "🤩"
        else -> null
    }

    fun moodLabel(mood: Int?): String? = when (normalizeMood(mood)) {
        1 -> "糟糕"
        2 -> "一般"
        3 -> "平稳"
        4 -> "顺利"
        5 -> "兴奋"
        else -> null
    }

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

    fun isBirthdayApproaching(
        today: LocalDate,
        birthdayMonth: Int?,
        birthdayDay: Int?,
        daysAhead: Int = 30,
    ): Boolean {
        if (birthdayMonth == null || birthdayDay == null) return false
        val thisYear = runCatching { LocalDate.of(today.year, birthdayMonth, birthdayDay) }.getOrNull()
        val nextYear = runCatching { LocalDate.of(today.year + 1, birthdayMonth, birthdayDay) }.getOrNull()
        val upcoming = when {
            thisYear != null && !thisYear.isBefore(today) -> thisYear
            nextYear != null -> nextYear
            else -> return false
        }
        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, upcoming)
        return daysUntil in 0..daysAhead
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
        categories: List<ExpenseCategoryEntity>,
        tagsBySnapshot: Map<Long, List<TagEntity>> = emptyMap(),
    ): List<SnapshotSummary> {
        val includedAccountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val chronologic = snapshots.sortedBy { it.snapshot.snapshotDate }
        val totalsById = mutableMapOf<Long, Long>()
        chronologic.forEach { details ->
            totalsById[details.snapshot.id] = details.balances
                .filter { includedAccountMap[it.accountId]?.includeInNetWorth == true }
                .sumOf { it.amount }
        }
        val previousById = chronologic.zipWithNext().associate { (previous, current) ->
            current.snapshot.id to (totalsById[current.snapshot.id] ?: 0L) - (totalsById[previous.snapshot.id] ?: 0L)
        }
        val previousBalances = mutableMapOf<Long, Map<Long, Long>>()
        chronologic.forEachIndexed { index, details ->
            val prev = chronologic.getOrNull(index - 1)?.balances?.associate { it.accountId to it.amount }.orEmpty()
            previousBalances[details.snapshot.id] = prev
        }
        return chronologic.reversed().map { details ->
            val total = totalsById[details.snapshot.id] ?: 0L
            val previous = previousBalances[details.snapshot.id].orEmpty()
            SnapshotSummary(
                id = details.snapshot.id,
                snapshotDate = details.snapshot.snapshotDate,
                total = total,
                changeFromPrevious = previousById[details.snapshot.id],
                targetTotal = details.snapshot.targetTotal,
                goalReached = isGoalReached(total, details.snapshot.targetTotal),
                debtLabel = details.snapshot.debtLabel,
                debtAmount = details.snapshot.debtAmount,
                nextRecordAt = details.snapshot.nextRecordAt,
                note = details.snapshot.note,
                mood = normalizeMood(details.snapshot.mood),
                tags = tagsBySnapshot[details.snapshot.id].orEmpty().map {
                    TagSummary(id = it.id, name = it.name, archived = it.archived)
                },
                balances = details.balances.map { balance ->
                    val account = includedAccountMap[balance.accountId]
                    val baseName = account?.name ?: "账户 ${balance.accountId}"
                    val name = if (account?.archived == true) "$baseName (已归档)" else baseName
                    AccountBalanceSummary(
                        accountId = balance.accountId,
                        accountName = name,
                        amount = balance.amount,
                        deltaFromPrevious = previous[balance.accountId]?.let { balance.amount - it },
                    )
                }.sortedBy { it.accountName },
                expenses = details.expenses.map { expense ->
                    ExpenseSummary(
                        categoryId = expense.categoryId,
                        categoryName = categoryMap[expense.categoryId]?.name ?: "已删除",
                        amount = expense.amount,
                    )
                }.sortedByDescending { it.amount },
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
                sortOrder = personWithGifts.person.sortOrder,
                gifts = personWithGifts.gifts.sortedByDescending { it.date }.map {
                    GiftSummary(
                        id = it.id,
                        personId = it.personId,
                        date = it.date,
                        directionLabel = if (it.direction.name == "SENT") "我送出" else "对方送我",
                        giftName = it.giftName,
                        price = it.price,
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
            )
        }
    }
}
