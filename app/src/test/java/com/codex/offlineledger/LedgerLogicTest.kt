package com.codex.offlineledger

import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.SnapshotBalanceEntity
import com.codex.offlineledger.data.entity.SnapshotEntity
import com.codex.offlineledger.data.entity.TagEntity
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
                SnapshotBalanceEntity(snapshotId = 1, accountId = 1, amount = 10000),
                SnapshotBalanceEntity(snapshotId = 1, accountId = 2, amount = 20000),
            ),
            expenses = emptyList(),
        )
        val newSnapshot = SnapshotWithDetails(
            snapshot = SnapshotEntity(id = 2, snapshotDate = millisOf(2024, 6, 1), createdAt = millisOf(2026, 1, 1)),
            balances = listOf(
                SnapshotBalanceEntity(snapshotId = 2, accountId = 1, amount = 15000),
                SnapshotBalanceEntity(snapshotId = 2, accountId = 2, amount = 25000),
            ),
            expenses = emptyList(),
        )

        val summaries = LedgerLogic.buildSnapshotSummaries(accounts, listOf(newSnapshot, oldSnapshot), emptyList())

        assertEquals(2, summaries.size)
        assertEquals(millisOf(2024, 6, 1), summaries.first().snapshotDate)
        assertEquals(10000L, summaries.first().changeFromPrevious)
    }

    @Test
    fun `goal is reached when total is greater than or equal to target`() {
        assertTrue(LedgerLogic.isGoalReached(total = 10000, target = 10000) == true)
        assertFalse(LedgerLogic.isGoalReached(total = 9999, target = 10000) == true)
    }

    @Test
    fun `expense category count is limited to ten`() {
        assertTrue(LedgerLogic.validateExpenseCategoryCount(10))
        assertFalse(LedgerLogic.validateExpenseCategoryCount(11))
    }

    @Test
    fun `birthday approaching within 30 days`() {
        val today = LocalDate.of(2026, 4, 9)
        assertTrue(LedgerLogic.isBirthdayApproaching(today, 4, 23))
        assertTrue(LedgerLogic.isBirthdayApproaching(today, 5, 8))
        assertFalse(LedgerLogic.isBirthdayApproaching(today, 6, 1))
    }

    @Test
    fun `birthday approaching returns false when month or day is null`() {
        val today = LocalDate.of(2026, 4, 9)
        assertFalse(LedgerLogic.isBirthdayApproaching(today, null, null))
        assertFalse(LedgerLogic.isBirthdayApproaching(today, 4, null))
        assertFalse(LedgerLogic.isBirthdayApproaching(today, null, 23))
    }

    @Test
    fun `archived account balance gets archived suffix in summaries`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "旧卡", type = "bank", accountNumber = "0000", includeInNetWorth = true, archived = true),
            AccountEntity(id = 2, name = "在用卡", type = "bank", accountNumber = "1111", includeInNetWorth = true, archived = false),
        )
        val snapshot = SnapshotWithDetails(
            snapshot = SnapshotEntity(id = 1, snapshotDate = millisOf(2024, 1, 1), createdAt = millisOf(2024, 1, 1)),
            balances = listOf(
                SnapshotBalanceEntity(snapshotId = 1, accountId = 1, amount = 500),
                SnapshotBalanceEntity(snapshotId = 1, accountId = 2, amount = 800),
            ),
            expenses = emptyList(),
        )

        val summaries = LedgerLogic.buildSnapshotSummaries(accounts, listOf(snapshot), emptyList())

        val archivedBalance = summaries.first().balances.first { it.accountId == 1L }
        assertTrue(archivedBalance.accountName.contains("(已归档)"))
        val activeBalance = summaries.first().balances.first { it.accountId == 2L }
        assertFalse(activeBalance.accountName.contains("(已归档)"))
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

    @Test
    fun `smart currency format`() {
        assertEquals("15万", LedgerLogic.formatCurrencySmart(150000))
        assertEquals("15千", LedgerLogic.formatCurrencySmart(15000))
        assertEquals("12,345元", LedgerLogic.formatCurrencySmart(12345))
        assertEquals("0", LedgerLogic.formatCurrencySmart(0))
        assertEquals("-", LedgerLogic.formatCurrencySmart(null))
    }

    @Test
    fun `parse currency with unit`() {
        assertEquals(15000L, LedgerLogic.parseCurrency("15", com.codex.offlineledger.domain.CurrencyUnit.THOUSAND))
        assertEquals(150000L, LedgerLogic.parseCurrency("15", com.codex.offlineledger.domain.CurrencyUnit.TEN_THOUSAND))
        assertEquals(15L, LedgerLogic.parseCurrency("15", com.codex.offlineledger.domain.CurrencyUnit.YUAN))
    }

    @Test
    fun `mood emoji maps 1 to 5 and returns null outside`() {
        assertEquals("😢", LedgerLogic.moodEmoji(1))
        assertEquals("😕", LedgerLogic.moodEmoji(2))
        assertEquals("😐", LedgerLogic.moodEmoji(3))
        assertEquals("🙂", LedgerLogic.moodEmoji(4))
        assertEquals("🤩", LedgerLogic.moodEmoji(5))
        assertEquals(null, LedgerLogic.moodEmoji(null))
        assertEquals(null, LedgerLogic.moodEmoji(0))
        assertEquals(null, LedgerLogic.moodEmoji(6))
        assertEquals(null, LedgerLogic.moodEmoji(-1))
    }

    @Test
    fun `buildSnapshotSummaries attaches mood and tags`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "卡", type = "bank", accountNumber = "0", includeInNetWorth = true),
        )
        val snap = SnapshotWithDetails(
            snapshot = SnapshotEntity(
                id = 42,
                snapshotDate = millisOf(2024, 5, 1),
                createdAt = millisOf(2024, 5, 1),
                mood = 4,
                note = "顺利",
            ),
            balances = listOf(SnapshotBalanceEntity(snapshotId = 42, accountId = 1, amount = 100)),
            expenses = emptyList(),
        )
        val tagsBySnapshot = mapOf(
            42L to listOf(
                TagEntity(id = 7, name = "里程碑"),
                TagEntity(id = 8, name = "旅行"),
            ),
        )
        val summaries = LedgerLogic.buildSnapshotSummaries(accounts, listOf(snap), emptyList(), tagsBySnapshot)
        val summary = summaries.first()
        assertEquals(4, summary.mood)
        assertEquals(listOf(7L, 8L), summary.tags.map { it.id })
        assertEquals("顺利", summary.note)
    }

    @Test
    fun `buildSnapshotSummaries yields empty tags when map has no entry`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "卡", type = "bank", accountNumber = "0", includeInNetWorth = true),
        )
        val snap = SnapshotWithDetails(
            snapshot = SnapshotEntity(id = 1, snapshotDate = millisOf(2024, 5, 1), createdAt = millisOf(2024, 5, 1)),
            balances = listOf(SnapshotBalanceEntity(snapshotId = 1, accountId = 1, amount = 100)),
            expenses = emptyList(),
        )
        val summary = LedgerLogic.buildSnapshotSummaries(accounts, listOf(snap), emptyList()).first()
        assertEquals(emptyList<Long>(), summary.tags.map { it.id })
        assertEquals(null, summary.mood)
    }

    private fun millisOf(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
