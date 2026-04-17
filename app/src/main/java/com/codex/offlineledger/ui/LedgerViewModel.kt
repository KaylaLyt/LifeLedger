package com.codex.offlineledger.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codex.offlineledger.data.AppDatabase
import com.codex.offlineledger.data.ThemeMode
import com.codex.offlineledger.data.UserPreferences
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.ExpenseCategoryEntity
import com.codex.offlineledger.data.entity.GiftDirection
import com.codex.offlineledger.data.entity.NoteEntity
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.TagEntity
import com.codex.offlineledger.data.repo.LedgerRepository
import com.codex.offlineledger.domain.CurrencyUnit
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
    Notes,
    Todos,
    Settings,
    Assets,
    Gifts,
}

data class LedgerUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val allAccounts: List<AccountEntity> = emptyList(),
    val snapshots: List<SnapshotSummary> = emptyList(),
    val people: List<PersonLedgerSummary> = emptyList(),
    val todos: List<TodoSummary> = emptyList(),
    val expenseCategories: List<ExpenseCategoryEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val selectedTagFilterIds: Set<Long> = emptySet(),
    val passwordSet: Boolean = false,
    val failedAttempts: Int = 0,
    val unlocked: Boolean = false,
    val activeTab: LedgerTab = LedgerTab.Assets,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val assetsUnit: CurrencyUnit = CurrencyUnit.THOUSAND,
    val giftsUnit: CurrencyUnit = CurrencyUnit.THOUSAND,
    val pendingAccountRestore: PendingAccountRestore? = null,
)

data class PendingAccountRestore(
    val accountId: Long,
    val archivedName: String,
    val pendingName: String,
    val type: String,
    val accountNumber: String,
    val note: String,
    val includeInNetWorth: Boolean,
)

data class SnapshotBalanceDraft(
    val accountId: Long,
    val amountText: String,
)

data class SnapshotExpenseDraft(
    val categoryId: Long,
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
    val allAccounts: List<AccountEntity>,
    val snapshots: List<com.codex.offlineledger.data.model.SnapshotWithDetails>,
    val people: List<com.codex.offlineledger.data.model.PersonWithGifts>,
    val todos: List<com.codex.offlineledger.data.model.TodoWithRule>,
    val lock: com.codex.offlineledger.data.entity.AppLockSettingsEntity?,
    val expenseCategories: List<ExpenseCategoryEntity>,
    val notes: List<NoteEntity>,
    val activeTags: List<TagEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val tagsBySnapshot: Map<Long, List<TagEntity>> = emptyMap(),
)

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LedgerRepository(AppDatabase.getInstance(application))
    private val prefs = UserPreferences(application)
    private val selectedTab = MutableStateFlow(LedgerTab.Assets)
    private val unlockedState = MutableStateFlow(false)
    private val pendingAccountRestoreState = MutableStateFlow<PendingAccountRestore?>(null)
    private val selectedTagFilterIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages

    private val accountsCombined = combine(
        repository.accounts,
        repository.allAccounts,
    ) { active, all -> active to all }

    private val baseState = combine(
        accountsCombined,
        repository.snapshots,
        repository.people,
        repository.todos,
        repository.lockSettings,
    ) { accounts, snapshots, people, todos, lock ->
        LedgerBaseState(
            accounts = accounts.first,
            allAccounts = accounts.second,
            snapshots = snapshots,
            people = people,
            todos = todos,
            lock = lock,
            expenseCategories = emptyList(),
            notes = emptyList(),
        )
    }

    private val tagsCombined = combine(
        repository.activeTags,
        repository.allTags,
        repository.snapshotTagJoin,
    ) { active, all, join ->
        val byId: Map<Long, TagEntity> = all.associateBy { it.id }
        val bySnapshot: Map<Long, List<TagEntity>> = join
            .groupBy { it.snapshotId }
            .mapValues { (_, rows) ->
                rows.mapNotNull { byId[it.id] }.sortedBy { it.name }
            }
        Triple(active, all, bySnapshot)
    }

    private val extendedState = combine(
        baseState,
        repository.activeCategories,
        repository.notes,
        tagsCombined,
    ) { base, categories, notes, tags ->
        base.copy(
            expenseCategories = categories,
            notes = notes,
            activeTags = tags.first,
            allTags = tags.second,
            tagsBySnapshot = tags.third,
        )
    }

    private val prefsState = combine(
        prefs.themeMode,
        prefs.assetsUnit,
        prefs.giftsUnit,
    ) { theme, assetsUnit, giftsUnit ->
        Triple(theme, assetsUnit, giftsUnit)
    }

    private val auxiliaryState = combine(
        pendingAccountRestoreState,
        selectedTagFilterIds,
    ) { pending, tagFilter -> pending to tagFilter }

    val uiState: StateFlow<LedgerUiState> = combine(
        extendedState,
        selectedTab,
        unlockedState,
        prefsState,
        auxiliaryState,
    ) { base, tab, unlocked, prefs, aux ->
        val (pendingRestore, tagFilterIds) = aux
        val summaries = buildSnapshotSummaries(
            base.allAccounts,
            base.snapshots,
            base.expenseCategories,
            base.tagsBySnapshot,
        )
        val filteredSummaries = if (tagFilterIds.isEmpty()) {
            summaries
        } else {
            summaries.filter { summary ->
                summary.tags.any { it.id in tagFilterIds }
            }
        }
        LedgerUiState(
            accounts = base.accounts,
            allAccounts = base.allAccounts,
            snapshots = filteredSummaries,
            people = mapPeople(base.people),
            todos = mapTodos(base.todos),
            expenseCategories = base.expenseCategories,
            notes = base.notes,
            tags = base.activeTags,
            allTags = base.allTags,
            selectedTagFilterIds = tagFilterIds,
            passwordSet = !base.lock?.passwordHash.isNullOrBlank(),
            failedAttempts = base.lock?.failedAttempts ?: 0,
            unlocked = unlocked || base.lock?.passwordHash.isNullOrBlank(),
            activeTab = tab,
            themeMode = prefs.first,
            assetsUnit = prefs.second,
            giftsUnit = prefs.third,
            pendingAccountRestore = pendingRestore,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState(),
    )

    fun selectTab(tab: LedgerTab) {
        selectedTab.value = tab
    }

    // --- Accounts ---

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
                .onSuccess {
                    _messages.emit("账户已保存")
                }.onFailure { err ->
                    if (err is LedgerRepository.AccountArchivedConflict) {
                        pendingAccountRestoreState.value = PendingAccountRestore(
                            accountId = err.id,
                            archivedName = err.name,
                            pendingName = name.trim(),
                            type = type.trim(),
                            accountNumber = accountNumber.trim(),
                            note = note.trim(),
                            includeInNetWorth = includeInNetWorth,
                        )
                    } else {
                        _messages.emit(err.message ?: "账户保存失败")
                    }
                }
        }
    }

    fun updateAccount(
        id: Long,
        name: String,
        type: String,
        accountNumber: String,
        note: String,
        includeInNetWorth: Boolean,
    ) {
        viewModelScope.launch {
            repository.updateAccount(id, name, type, accountNumber, note, includeInNetWorth)
                .onSuccess { _messages.emit("账户已更新") }
                .onFailure { _messages.emit(it.message ?: "账户更新失败") }
        }
    }

    fun archiveAccount(id: Long) {
        viewModelScope.launch {
            repository.archiveAccount(id)
            _messages.emit("账户已归档，历史快照保留")
        }
    }

    fun confirmRestoreArchivedAccount() {
        val pending = pendingAccountRestoreState.value ?: return
        viewModelScope.launch {
            repository.restoreAndUpdateAccount(
                id = pending.accountId,
                name = pending.pendingName,
                type = pending.type,
                accountNumber = pending.accountNumber,
                note = pending.note,
                includeInNetWorth = pending.includeInNetWorth,
            ).onSuccess {
                pendingAccountRestoreState.value = null
                _messages.emit("已恢复并更新归档账户")
            }.onFailure {
                _messages.emit(it.message ?: "恢复账户失败")
            }
        }
    }

    fun cancelRestoreArchivedAccount() {
        pendingAccountRestoreState.value = null
    }

    // --- Expense Categories ---

    fun saveExpenseCategory(name: String) {
        viewModelScope.launch {
            repository.saveExpenseCategory(name).onFailure {
                _messages.emit(it.message ?: "保存失败")
            }.onSuccess {
                _messages.emit("分类已保存")
            }
        }
    }

    fun renameExpenseCategory(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameExpenseCategory(id, newName).onFailure {
                _messages.emit(it.message ?: "重命名失败")
            }.onSuccess {
                _messages.emit("分类已重命名")
            }
        }
    }

    fun archiveExpenseCategory(id: Long) {
        viewModelScope.launch {
            repository.archiveExpenseCategory(id)
            _messages.emit("分类已归档")
        }
    }

    // --- Snapshots ---

    fun saveSnapshot(
        snapshotDateText: String,
        nextRecordText: String,
        targetText: String,
        debtLabel: String,
        debtAmountText: String,
        note: String,
        balances: List<SnapshotBalanceDraft>,
        expenses: List<SnapshotExpenseDraft>,
        unit: CurrencyUnit,
        mood: Int? = null,
        tagIds: List<Long> = emptyList(),
    ) {
        viewModelScope.launch {
            val parsedBalances = balances.mapNotNull { draft ->
                val amount = LedgerLogic.parseCurrency(draft.amountText, unit)
                if (amount == null) null else draft.accountId to amount
            }
            val invalidBalance = balances.any { LedgerLogic.parseCurrency(it.amountText, unit) == null }
            val invalidExpense = expenses.any { LedgerLogic.parseCurrency(it.amountText, unit) == null }
            if (invalidBalance || invalidExpense) {
                _messages.emit("请检查金额格式")
                return@launch
            }
            val parsedExpenses = expenses.mapNotNull { draft ->
                val amount = LedgerLogic.parseCurrency(draft.amountText, unit) ?: return@mapNotNull null
                if (amount == 0L) return@mapNotNull null
                var catId = draft.categoryId
                if (catId == 0L && draft.categoryName.isNotBlank()) {
                    val result = repository.saveExpenseCategory(draft.categoryName)
                    catId = result.getOrElse {
                        _messages.emit(it.message ?: "分类保存失败")
                        return@launch
                    }
                }
                catId to amount
            }
            repository.saveSnapshot(
                snapshotDate = LedgerLogic.parseDateOrNow(snapshotDateText),
                nextRecordAt = LedgerLogic.parseOptionalDate(nextRecordText),
                targetTotal = targetText.takeIf { it.isNotBlank() }?.let { LedgerLogic.parseCurrency(it, unit) },
                debtLabel = debtLabel,
                debtAmount = debtAmountText.takeIf { it.isNotBlank() }?.let { LedgerLogic.parseCurrency(it, unit) },
                note = note,
                balances = parsedBalances,
                expenses = parsedExpenses,
                mood = mood,
                tagIds = tagIds,
            )
            _messages.emit("资产快照已保存")
        }
    }

    fun deleteSnapshot(id: Long) {
        viewModelScope.launch {
            repository.deleteSnapshot(id)
            _messages.emit("快照已删除")
        }
    }

    fun editSnapshotAnnotation(
        snapshotId: Long,
        mood: Int?,
        note: String,
        tagIds: List<Long>,
    ) {
        viewModelScope.launch {
            repository.updateSnapshotAnnotation(snapshotId, mood, note, tagIds)
                .onSuccess { _messages.emit("批注已更新") }
                .onFailure { _messages.emit(it.message ?: "批注更新失败") }
        }
    }

    // --- Tag filter ---

    fun toggleTagFilter(tagId: Long) {
        val current = selectedTagFilterIds.value
        selectedTagFilterIds.value = if (tagId in current) current - tagId else current + tagId
    }

    fun clearTagFilter() {
        selectedTagFilterIds.value = emptySet()
    }

    // --- Tags ---

    fun saveTag(name: String) {
        viewModelScope.launch {
            repository.saveTag(name)
                .onSuccess { _messages.emit("标签已保存") }
                .onFailure { _messages.emit(it.message ?: "标签保存失败") }
        }
    }

    fun renameTag(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameTag(id, newName)
                .onSuccess { _messages.emit("标签已重命名") }
                .onFailure { _messages.emit(it.message ?: "标签重命名失败") }
        }
    }

    fun archiveTag(id: Long) {
        viewModelScope.launch {
            repository.archiveTag(id)
            _messages.emit("标签已归档")
        }
    }

    fun unarchiveTag(id: Long) {
        viewModelScope.launch {
            repository.unarchiveTag(id)
            _messages.emit("标签已恢复")
        }
    }

    fun deleteTagIfUnused(id: Long) {
        viewModelScope.launch {
            repository.deleteTagIfUnused(id)
                .onSuccess { _messages.emit("标签已删除") }
                .onFailure { _messages.emit(it.message ?: "标签无法删除") }
        }
    }

    // --- People ---

    fun savePerson(
        name: String,
        birthdayMonthText: String,
        birthdayDayText: String,
        relation: String,
        note: String,
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _messages.emit("请填写姓名")
                return@launch
            }
            val (month, day, birthdayOk) = parseOptionalBirthday(birthdayMonthText, birthdayDayText)
            if (!birthdayOk) {
                _messages.emit("生日格式不正确，或留空")
                return@launch
            }
            repository.savePerson(name, month, day, relation, note)
            _messages.emit("联系人已保存")
        }
    }

    fun updatePerson(
        id: Long,
        name: String,
        birthdayMonthText: String,
        birthdayDayText: String,
        relation: String,
        note: String,
    ) {
        viewModelScope.launch {
            if (name.isBlank()) {
                _messages.emit("请填写姓名")
                return@launch
            }
            val (month, day, birthdayOk) = parseOptionalBirthday(birthdayMonthText, birthdayDayText)
            if (!birthdayOk) {
                _messages.emit("生日格式不正确，或留空")
                return@launch
            }
            repository.updatePerson(id, name, month, day, relation, note)
            _messages.emit("联系人已更新")
        }
    }

    private data class OptionalBirthday(
        val month: Int?,
        val day: Int?,
        val ok: Boolean,
    )

    private fun parseOptionalBirthday(monthText: String, dayText: String): OptionalBirthday {
        val monthBlank = monthText.isBlank()
        val dayBlank = dayText.isBlank()
        if (monthBlank && dayBlank) return OptionalBirthday(null, null, true)
        val month = monthText.toIntOrNull()
        val day = dayText.toIntOrNull()
        val valid = month != null && day != null && month in 1..12 && day in 1..31
        return OptionalBirthday(month, day, valid)
    }

    fun deletePerson(id: Long) {
        viewModelScope.launch {
            repository.deletePerson(id)
            _messages.emit("联系人已删除")
        }
    }

    fun reorderPeople(orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderPeople(orderedIds)
        }
    }

    fun saveGift(
        personId: Long,
        dateText: String,
        direction: GiftDirection,
        giftName: String,
        priceText: String,
        note: String,
        unit: CurrencyUnit,
    ) {
        viewModelScope.launch {
            val amount = LedgerLogic.parseCurrency(priceText, unit)
            if (personId <= 0 || giftName.isBlank() || priceText.isBlank() || amount == null) {
                _messages.emit("请填写有效的礼物信息")
                return@launch
            }
            repository.saveGiftRecord(
                personId = personId,
                date = LedgerLogic.parseDateOrNow(dateText),
                direction = direction,
                giftName = giftName,
                price = amount,
                note = note,
            )
            _messages.emit("送礼记录已保存")
        }
    }

    // --- Notes ---

    fun saveNote(title: String, body: String) {
        viewModelScope.launch {
            if (title.isBlank()) {
                _messages.emit("笔记标题不能为空")
                return@launch
            }
            repository.saveNote(title, body)
            _messages.emit("笔记已保存")
        }
    }

    fun updateNote(id: Long, title: String, body: String) {
        viewModelScope.launch {
            if (title.isBlank()) {
                _messages.emit("笔记标题不能为空")
                return@launch
            }
            repository.updateNote(id, title, body)
            _messages.emit("笔记已更新")
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
            _messages.emit("笔记已删除")
        }
    }

    // --- Todos ---

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

    fun deleteTodo(todoId: Long) {
        viewModelScope.launch {
            repository.deleteTodo(todoId)
            _messages.emit("Todo 已删除")
        }
    }

    // --- Preferences ---

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setAssetsUnit(unit: CurrencyUnit) {
        viewModelScope.launch { prefs.setAssetsUnit(unit) }
    }

    fun setGiftsUnit(unit: CurrencyUnit) {
        viewModelScope.launch { prefs.setGiftsUnit(unit) }
    }

    // --- Password ---

    fun setPassword(password: String, confirmPassword: String) {
        enablePassword(password, confirmPassword)
    }

    fun enablePassword(password: String, confirmPassword: String) {
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
            _messages.emit("密码已启用")
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

    fun disablePassword(current: String) {
        viewModelScope.launch {
            if (!repository.verifyPassword(current)) {
                _messages.emit("当前密码不正确")
                return@launch
            }
            repository.disablePassword()
            unlockedState.value = true
            _messages.emit("应用密码已关闭")
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
                        runCatching { repository.wipeInternalData() }
                        runCatching { prefs.clear() }
                        pendingAccountRestoreState.value = null
                        unlockedState.value = true
                        _messages.emit("已连续 5 次输错密码，数据已销毁，App 已重置")
                    }
                }
            }
        }
    }

    // --- Export / Import ---

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
