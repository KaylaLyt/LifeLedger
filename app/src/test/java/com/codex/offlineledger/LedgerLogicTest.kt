package com.codex.offlineledger

import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.domain.LedgerLogic
import com.codex.offlineledger.domain.LockAction
import com.codex.offlineledger.domain.RecurrenceDescriptor
import com.codex.offlineledger.data.model.SnapshotWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

class LedgerLogicTest {
    @Test
    fun `snapshot summaries are sorted by snapshot date and recomputed for backfill`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "银行卡", type = "bank", accountNumber = "1234", includeInNetWorth = true),
            AccountEntity(id = 2, name = "支付宝", type = "wallet", accountNumber = "5678", includeInNetWorth = true),
        )
        val oldSnapshot = SnapshotWithDetails(
            snapshot = SnapshotEntity(id = 1, snapshotDate = millisOf(2024, 1, 1), createdAt = millisOf(2026, 1, 1)),
            balances = listOf(
                SnapshotBalanceEntity(snapshotId = 1, accountId = 1, amountInCents = 100_00),
                SnapshotBalanceEntity(snapshotId = 1, accountId = 2, amountInCents = 200_00),
            ),
            expenses = emptyList(),
        )
        val newSnapshot = SnapshotWithDetails(
            snapshot = SnapshotEntity(id = 2, snapshotDate = millisOf(2024, 6, 1), createdAt = millisOf(2026, 1, 1)),
            balances = listOf(
                SnapshotBalanceEntity(snapshotId = 2, accountId = 1, amountInCents = 150_00),
                SnapshotBalanceEntity(snapshotId = 2, accountId = 2, amountInCents = 250_00),
            ),
            expenses = emptyList(),
        )

        val summaries = LedgerLogic.buildSnapshotSummaries(accounts, listOf(newSnapshot, oldSnapshot))

        assertEquals(2, summaries.size)
        assertEquals(millisOf(2024, 6, 1), summaries.first().snapshotDate)
        assertEquals(100_00L, summaries.first().changeFromPreviousInCents)
    }

    @Test
    fun `goal is reached when total is greater than or equal to target`() {
        assertTrue(LedgerLogic.isGoalReached(totalInCents = 100_00, targetInCents = 100_00) == true)
        assertFalse(LedgerLogic.isGoalReached(totalInCents = 99_99, targetInCents = 100_00) == true)
    }

    @Test
    fun `expense category count is limited to ten`() {
        assertTrue(LedgerLogic.validateExpenseCategoryCount(10))
        assertFalse(LedgerLogic.validateExpenseCategoryCount(11))
    }

    @Test
    fun `birthday todo is generated exactly two weeks before birthday`() {
        val today = LocalDate.of(2026, 4, 9)
        assertTrue(LedgerLogic.shouldGenerateBirthdayTodo(today, 4, 23))
        assertFalse(LedgerLogic.shouldGenerateBirthdayTodo(today, 4, 24))
    }

    @Test
    fun `lock feedback wipes data on fifth failure`() {
        val feedback = LedgerLogic.lockFeedback(5)
        assertEquals(LockAction.WIPE_DATA, feedback.action)
        assertEquals(0, feedback.attemptsRemaining)
    }

    @Test
    fun `advanced recurrence computes a next occurrence`() {
        val anchor = millisOf(2026, 4, 9)
        val next = LedgerLogic.computeNextOccurrence(
            descriptor = RecurrenceDescriptor(
                mode = RecurrenceMode.ADVANCED,
                interval = 1,
                daysOfWeek = setOf(DayOfWeek.FRIDAY),
                months = setOf(Month.APRIL),
                hour = 9,
                minute = 0,
            ),
            anchorMillis = anchor,
            afterMillis = anchor,
        )
        assertNotNull(next)
    }

    private fun millisOf(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
