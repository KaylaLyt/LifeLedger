package com.codex.offlineledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.offlineledger.data.AppDatabase
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.GiftDirection
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.repo.LedgerRepository
import com.codex.offlineledger.domain.LedgerLogic
import com.codex.offlineledger.domain.LedgerLogic.buildSnapshotSummaries
import com.codex.offlineledger.domain.LedgerLogic.mapPeople
import com.codex.offlineledger.domain.LedgerLogic.mapTodos
import com.codex.offlineledger.domain.LockAction
import com.codex.offlineledger.domain.PersonLedgerSummary
import com.codex.offlineledger.domain.RecurrenceDescriptor
import com.codex.offlineledger.domain.SnapshotSummary
import com.codex.offlineledger.domain.TodoSummary
import com.codex.offlineledger.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Month

enum class LedgerTab {
    Assets,
    Gifts,
    Todos,
    Settings,
}

data class LedgerUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val snapshots: List<SnapshotSummary> = emptyList(),
    val people: List<PersonLedgerSummary> = emptyList(),
    val todos: List<TodoSummary> = emptyList(),
    val passwordSet: Boolean = false,
    val failedAttempts: Int = 0,
    val unlocked: Boolean = false,
    val activeTab: LedgerTab = LedgerTab.Assets,
)

data class SnapshotBalanceDraft(
    val accountId: Long,
    val amountText: String,
)

data class SnapshotExpenseDraft(
    val categoryName: String,
    val amountText: String,
)

data class RecurrenceDraft(
    val mode: RecurrenceMode = RecurrenceMode.NONE,
    val intervalText: String = "1",
    val daysOfWeek: Set<Int> = emptySet(),
    val dayOfMonthText: String = "",
    val months: Set<Int> = emptySet(),
    val hourText: String = "",
    val minuteText: String = "",
)

private data class LedgerBaseState(
    val accounts: List<AccountEntity>,
    val snapshots: List<com.codex.offlineledger.data.model.SnapshotWithDetails>,
    val people: List<com.codex.offlineledger.data.model.PersonWithGifts>,
    val todos: List<com.codex.offlineledger.data.model.TodoWithRule>,
    val lock: com.codex.offlineledger.data.entity.AppLockSettingsEntity?,
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(AppDatabase.getInstance(application))
    private val selectedTab = MutableStateFlow(LedgerTab.Assets)
    private val unlockedState = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages

    private val baseState = combine(
        repository.accounts,
        repository.snapshots,
        repository.people,
        repository.todos,
        repository.lockSettings,
    ) { accounts, snapshots, people, todos, lock ->
        LedgerBaseState(
            accounts = accounts,
            snapshots = snapshots,
            people = people,
            todos = todos,
            lock = lock,
        )
    }

    val uiState: StateFlow<LedgerUiState> = combine(
        baseState,
        selectedTab,
        unlockedState,
    ) { base, tab, unlocked ->
        LedgerUiState(
            accounts = base.accounts,
            snapshots = buildSnapshotSummaries(base.accounts, base.snapshots),
            people = mapPeople(base.people),
            todos = mapTodos(base.todos),
            passwordSet = !base.lock?.passwordHash.isNullOrBlank(),
            failedAttempts = base.lock?.failedAttempts ?: 0,
            unlocked = unlocked || base.lock?.passwordHash.isNullOrBlank(),
            activeTab = tab,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun selectTab(tab: LedgerTab) {
        selectedTab.value = tab
    }

    fun saveAccount(
        name: String,
        type: String,
        accountNumber: String,
        note: String,
        includeInNetWorth: Boolean,
    ) {
        viewModelScope.launch {
            if (name.isBlank() || type.isBlank() || accountNumber.isBlank()) {
                _messages.emit("账户名称、类型和号码不能为空")
                return@launch
            }
            repository.saveAccount(name, type, accountNumber, note, includeInNetWorth)
            _messages.emit("账户已保存")
        }
    }

    fun saveSnapshot(
        snapshotDateText: String,
        nextRecordText: String,
        targetText: String,
        debtLabel: String,
        debtAmountText: String,
        note: String,
        balances: List<SnapshotBalanceDraft>,
        expenses: List<SnapshotExpenseDraft>,
    ) {
        viewModelScope.launch {
            if (expenses.size > 10) {
                _messages.emit("花销分类最多 10 个")
                return@launch
            }
            val parsedBalances = balances.mapNotNull { draft ->
                val amount = LedgerLogic.parseCurrencyToCents(draft.amountText)
                if (amount == null) {
                    null
                } else {
                    draft.accountId to amount
                }
            }
            val invalidBalance = balances.any { LedgerLogic.parseCurrencyToCents(it.amountText) == null }
            val invalidExpense = expenses.any { LedgerLogic.parseCurrencyToCents(it.amountText) == null || it.categoryName.isBlank() }
            if (invalidBalance || invalidExpense) {
                _messages.emit("请检查金额格式，分类名称不能为空")
                return@launch
            }
            repository.saveSnapshot(
                snapshotDate = LedgerLogic.parseDateOrNow(snapshotDateText),
                nextRecordAt = LedgerLogic.parseOptionalDate(nextRecordText),
                targetTotalInCents = targetText.takeIf { it.isNotBlank() }?.let(LedgerLogic::parseCurrencyToCents),
                debtLabel = debtLabel,
                debtAmountInCents = debtAmountText.takeIf { it.isNotBlank() }?.let(LedgerLogic::parseCurrencyToCents),
                note = note,
                balances = parsedBalances,
                expenses = expenses.map { it.categoryName to (LedgerLogic.parseCurrencyToCents(it.amountText) ?: 0L) },
            )
            _messages.emit("资产快照已保存")
        }
    }

    fun savePerson(
        name: String,
        birthdayMonthText: String,
        birthdayDayText: String,
        relation: String,
        note: String,
    ) {
        viewModelScope.launch {
            val month = birthdayMonthText.toIntOrNull()
            val day = birthdayDayText.toIntOrNull()
            if (name.isBlank() || month == null || day == null || month !in 1..12 || day !in 1..31) {
                _messages.emit("请填写有效的姓名和生日")
                return@launch
            }
            repository.savePerson(name, month, day, relation, note)
            _messages.emit("联系人已保存")
        }
    }

    fun saveGift(
        personId: Long,
        dateText: String,
        direction: GiftDirection,
        giftName: String,
        priceText: String,
        note: String,
    ) {
        viewModelScope.launch {
            val amount = LedgerLogic.parseCurrencyToCents(priceText)
            if (personId <= 0 || giftName.isBlank() || priceText.isBlank() || amount == null) {
                _messages.emit("请填写有效的礼物信息")
                return@launch
            }
            repository.saveGiftRecord(
                personId = personId,
                date = LedgerLogic.parseDateOrNow(dateText),
                direction = direction,
                giftName = giftName,
                priceInCents = amount,
                note = note,
            )
            _messages.emit("送礼记录已保存")
        }
    }

    fun saveTodo(
        title: String,
        description: String,
        reminderText: String,
        recurrenceDraft: RecurrenceDraft,
    ) {
        viewModelScope.launch {
            if (title.isBlank()) {
                _messages.emit("Todo 标题不能为空")
                return@launch
            }
            val reminderAt = LedgerLogic.parseOptionalDateTime(reminderText)
            if (reminderText.isNotBlank() && reminderAt == null) {
                _messages.emit("提醒时间格式应为 yyyy-MM-dd HH:mm")
                return@launch
            }
            val recurrence = recurrenceDraft.toDescriptor(reminderAt)
            repository.saveTodo(
                title = title,
                description = description,
                dueAt = reminderAt,
                reminderAt = reminderAt,
                recurrence = recurrence,
            )
            ReminderScheduler.schedule(getApplication())
            _messages.emit("Todo 已保存")
        }
    }

    fun completeTodo(todoId: Long) {
        viewModelScope.launch {
            repository.completeTodo(todoId)
            _messages.emit("Todo 状态已更新")
        }
    }

    fun reopenTodo(todoId: Long) {
        viewModelScope.launch {
            repository.reopenTodo(todoId)
            _messages.emit("Todo 已重新打开")
        }
    }

    fun setPassword(password: String, confirmPassword: String) {
        viewModelScope.launch {
            if (password.length < 4) {
                _messages.emit("密码至少 4 位")
                return@launch
            }
            if (password != confirmPassword) {
                _messages.emit("两次密码输入不一致")
                return@launch
            }
            repository.setPassword(password)
            unlockedState.value = true
            _messages.emit("密码已设置")
        }
    }

    fun changePassword(current: String, next: String, confirm: String) {
        viewModelScope.launch {
            if (!repository.verifyPassword(current)) {
                _messages.emit("当前密码不正确")
                return@launch
            }
            if (next.length < 4 || next != confirm) {
                _messages.emit("新密码不合法或两次输入不一致")
                return@launch
            }
            repository.setPassword(next)
            _messages.emit("密码已更新")
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            val passwordSet = uiState.value.passwordSet
            if (!passwordSet) {
                unlockedState.value = true
                return@launch
            }
            if (repository.verifyPassword(password)) {
                repository.resetFailedUnlocks()
                unlockedState.value = true
                _messages.emit("解锁成功")
            } else {
                val feedback = repository.recordFailedUnlock()
                when (feedback.action) {
                    LockAction.KEEP_LOCKED -> _messages.emit("密码错误，还剩 ${feedback.attemptsRemaining} 次机会")
                    LockAction.WARN_FINAL_ATTEMPT -> _messages.emit("再失败 1 次将销毁本地数据")
                    LockAction.WIPE_DATA -> {
                        repository.wipeInternalData()
                        unlockedState.value = false
                        _messages.emit("已连续 5 次输错密码，本地数据已销毁")
                    }
                }
            }
        }
    }

    fun exportFileName(): String = "offline-ledger-${LedgerLogic.formatDate(System.currentTimeMillis())}.json"

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                repository.exportJson(output)
            }
            _messages.emit("JSON 导出完成")
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.importFromUri(getApplication(), uri.toString())
                _messages.emit("JSON 导入成功")
            } catch (e: Exception) {
                _messages.emit("导入失败: ${e.message}")
            }
        }
    }

    private fun RecurrenceDraft.toDescriptor(anchorMillis: Long?): RecurrenceDescriptor? {
        if (mode == RecurrenceMode.NONE) return null
        val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val dayOfMonth = dayOfMonthText.toIntOrNull()
        val hour = hourText.toIntOrNull() ?: anchorMillis?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).hour
        }
        val minute = minuteText.toIntOrNull() ?: anchorMillis?.let {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).minute
        }
        return RecurrenceDescriptor(
            mode = mode,
            interval = interval,
            daysOfWeek = daysOfWeek.map(DayOfWeek::of).toSet(),
            dayOfMonth = dayOfMonth,
            months = months.map(Month::of).toSet(),
            hour = hour,
            minute = minute,
        )
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return LedgerViewModel(application) as T
                }
            }
        }
    }
}
