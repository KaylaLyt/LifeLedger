package com.codex.offlineledger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.GiftDirection
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.domain.LedgerLogic
import com.codex.offlineledger.domain.PersonLedgerSummary
import com.codex.offlineledger.domain.RecurrenceDescriptor
import com.codex.offlineledger.domain.SnapshotSummary
import com.codex.offlineledger.domain.TodoSummary
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId

@Composable
fun LedgerApp(
    state: LedgerUiState,
    snackbarHostState: SnackbarHostState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    viewModel: LedgerViewModel,
    context: Context,
) {
    if (!state.unlocked) {
        LockScreen(
            passwordSet = state.passwordSet,
            failedAttempts = state.failedAttempts,
            onUnlock = viewModel::unlock,
            onCreatePassword = viewModel::setPassword,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                LedgerTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(tab.shortLabel) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (state.activeTab) {
            LedgerTab.Assets -> AssetsScreen(
                modifier = Modifier.padding(innerPadding),
                accounts = state.accounts,
                snapshots = state.snapshots,
                onSaveAccount = viewModel::saveAccount,
                onSaveSnapshot = viewModel::saveSnapshot,
                context = context,
            )

            LedgerTab.Gifts -> GiftsScreen(
                modifier = Modifier.padding(innerPadding),
                people = state.people,
                onSavePerson = viewModel::savePerson,
                onSaveGift = viewModel::saveGift,
            )

            LedgerTab.Todos -> TodosScreen(
                modifier = Modifier.padding(innerPadding),
                todos = state.todos,
                onSaveTodo = viewModel::saveTodo,
                onCompleteTodo = viewModel::completeTodo,
                onReopenTodo = viewModel::reopenTodo,
            )

            LedgerTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                failedAttempts = state.failedAttempts,
                onExport = onExport,
                onImport = onImport,
                onChangePassword = viewModel::changePassword,
            )
        }
    }
}

private val LedgerTab.label: String
    get() = when (this) {
        LedgerTab.Assets -> "资产"
        LedgerTab.Gifts -> "人情"
        LedgerTab.Todos -> "Todo"
        LedgerTab.Settings -> "设置"
    }

private val LedgerTab.shortLabel: String
    get() = when (this) {
        LedgerTab.Assets -> "资"
        LedgerTab.Gifts -> "礼"
        LedgerTab.Todos -> "办"
        LedgerTab.Settings -> "设"
    }

@Composable
private fun LockScreen(
    passwordSet: Boolean,
    failedAttempts: Int,
    onUnlock: (String) -> Unit,
    onCreatePassword: (String, String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (passwordSet) "输入密码解锁" else "初始化应用密码",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = if (passwordSet) {
                        "连续输错 5 次会清空应用内本地数据，已导出的 JSON 文件不受影响。当前失败次数：$failedAttempts"
                    } else {
                        "首次使用请设置一个本地密码。"
                    },
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (passwordSet) "密码" else "新密码") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!passwordSet) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        if (passwordSet) onUnlock(password) else onCreatePassword(password, confirmPassword)
                        password = ""
                        confirmPassword = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (passwordSet) "解锁" else "设置并进入")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = LedgerLogic.parseOptionalDate(value) ?: System.currentTimeMillis(),
    )

    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onValueChange(LedgerLogic.formatDate(it))
                    }
                    showDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val initialMillis = LedgerLogic.parseOptionalDateTime(value) ?: System.currentTimeMillis()
    val initialDateTime = Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = initialDateTime.hour,
        initialMinute = initialDateTime.minute,
    )

    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDatePicker = true },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    val dateTime = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onValueChange(LedgerLogic.formatDateTime(dateTime))
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            },
            title = { Text("选择时间") },
            text = {
                TimePicker(state = timePickerState)
            },
        )
    }
}

@Composable
private fun AssetsScreen(
    modifier: Modifier,
    accounts: List<AccountEntity>,
    snapshots: List<SnapshotSummary>,
    onSaveAccount: (String, String, String, String, Boolean) -> Unit,
    onSaveSnapshot: (String, String, String, String, String, String, List<SnapshotBalanceDraft>, List<SnapshotExpenseDraft>) -> Unit,
    context: Context,
) {
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showSnapshotDialog by rememberSaveable { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(null) }
    val latest = snapshots.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "资产总览", subtitle = "以快照日期排序，支持历史补录") {
            SummaryValue("当前总资产", LedgerLogic.formatCurrency(latest?.totalInCents))
            SummaryValue("最近记录", LedgerLogic.formatDate(latest?.snapshotDate))
            SummaryValue(
                "相对上次变化",
                latest?.changeFromPreviousInCents?.let {
                    val prefix = if (it >= 0) "+" else ""
                    "$prefix${LedgerLogic.formatCurrency(it)}"
                } ?: "-",
            )
            SummaryValue(
                "目标状态",
                when (latest?.goalReached) {
                    true -> "已达成"
                    false -> "未达成"
                    null -> "未设置"
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showAccountDialog = true }) {
                    Text("新增账户")
                }
                OutlinedButton(onClick = { showSnapshotDialog = true }) {
                    Text("新增快照")
                }
            }
        }

        SectionCard(title = "账户", subtitle = "列表默认遮挡号码，详情中可复制") {
            if (accounts.isEmpty()) {
                EmptyState("还没有账户，先创建银行卡、支付宝或现金账户。")
            } else {
                accounts.forEach { account ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAccount = account },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            Text("${account.type} · ${LedgerLogic.maskAccountNumber(account.accountNumber)}")
                            Text(if (account.includeInNetWorth) "计入总资产" else "不计入总资产")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        SectionCard(title = "历史快照", subtitle = "主视图展示总资产变化，卡片内可查看账户对比") {
            if (snapshots.isEmpty()) {
                EmptyState("还没有快照。你可以补录最近三年的资产历史。")
            } else {
                snapshots.forEach { snapshot ->
                    SnapshotCard(snapshot = snapshot)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    if (showAccountDialog) {
        AddAccountDialog(
            onDismiss = { showAccountDialog = false },
            onConfirm = { name, type, number, note, include ->
                onSaveAccount(name, type, number, note, include)
                showAccountDialog = false
            },
        )
    }

    if (showSnapshotDialog) {
        AddSnapshotDialog(
            accounts = accounts,
            onDismiss = { showSnapshotDialog = false },
            onConfirm = { date, nextRecord, target, debtLabel, debtAmount, note, balances, expenses ->
                onSaveSnapshot(date, nextRecord, target, debtLabel, debtAmount, note, balances, expenses)
                showSnapshotDialog = false
            },
        )
    }

    selectedAccount?.let { account ->
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AlertDialog(
            onDismissRequest = { selectedAccount = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("accountNumber", account.accountNumber))
                        selectedAccount = null
                    },
                ) {
                    Text("复制号码")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAccount = null }) {
                    Text("关闭")
                }
            },
            title = { Text(account.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("类型：${account.type}")
                    Text("完整号码：${account.accountNumber}")
                    Text("备注：${account.note.ifBlank { "-" }}")
                }
            },
        )
    }
}

@Composable
private fun SnapshotCard(snapshot: SnapshotSummary) {
    var expanded by rememberSaveable(snapshot.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(LedgerLogic.formatDate(snapshot.snapshotDate), fontWeight = FontWeight.Bold)
                    Text("总资产 ${LedgerLogic.formatCurrency(snapshot.totalInCents)}")
                }
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "收起账户对比" else "查看账户对比") },
                )
            }
            Text(
                "变化 ${snapshot.changeFromPreviousInCents?.let {
                    val prefix = if (it >= 0) "+" else ""
                    "$prefix${LedgerLogic.formatCurrency(it)}"
                } ?: "-"}",
            )
            Text("下次记录 ${LedgerLogic.formatDate(snapshot.nextRecordAt)}")
            if (snapshot.targetTotalInCents != null) {
                Text(
                    "目标 ${LedgerLogic.formatCurrency(snapshot.targetTotalInCents)} · ${
                        if (snapshot.goalReached == true) "已达成" else "未达成"
                    }",
                )
            }
            if (snapshot.debtAmountInCents != null || snapshot.debtLabel.isNotBlank()) {
                Text("负债 ${snapshot.debtLabel.ifBlank { "未命名" }} · ${LedgerLogic.formatCurrency(snapshot.debtAmountInCents)}")
            }
            if (snapshot.expenses.isNotEmpty()) {
                Text("本期花销")
                snapshot.expenses.forEach {
                    Text("• ${it.categoryName}: ${LedgerLogic.formatCurrency(it.amountInCents)}")
                }
            }
            if (expanded) {
                HorizontalDivider()
                snapshot.balances.forEach {
                    Text(
                        "• ${it.accountName}: ${LedgerLogic.formatCurrency(it.amountInCents)}" +
                            (it.deltaFromPreviousInCents?.let { delta ->
                                "（${if (delta >= 0) "+" else ""}${LedgerLogic.formatCurrency(delta)}）"
                            } ?: ""),
                    )
                }
            }
        }
    }
}

@Composable
private fun GiftsScreen(
    modifier: Modifier,
    people: List<PersonLedgerSummary>,
    onSavePerson: (String, String, String, String, String) -> Unit,
    onSaveGift: (Long, String, GiftDirection, String, String, String) -> Unit,
) {
    var showPersonDialog by rememberSaveable { mutableStateOf(false) }
    var showGiftDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "人情往来", subtitle = "生日提前两周自动生成准备礼物 Todo") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showPersonDialog = true }) {
                    Text("新增联系人")
                }
                OutlinedButton(onClick = { showGiftDialog = true }) {
                    Text("新增礼物记录")
                }
            }
        }

        if (people.isEmpty()) {
            SectionCard(title = "联系人", subtitle = "还没有数据") {
                EmptyState("先录入联系人和生日，再补送礼台账。")
            }
        } else {
            people.forEach { person ->
                SectionCard(
                    title = person.name,
                    subtitle = "生日 ${person.birthdayMonth}月${person.birthdayDay}日 · ${person.relation.ifBlank { "关系未填" }}",
                ) {
                    if (person.gifts.isEmpty()) {
                        EmptyState("暂时还没有礼物记录")
                    } else {
                        person.gifts.forEach { gift ->
                            Text(
                                "• ${LedgerLogic.formatDate(gift.date)} · ${gift.directionLabel} · ${gift.giftName} · ${LedgerLogic.formatCurrency(gift.priceInCents)}",
                            )
                            if (gift.note.isNotBlank()) {
                                Text("  ${gift.note}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPersonDialog) {
        AddPersonDialog(
            onDismiss = { showPersonDialog = false },
            onConfirm = { name, month, day, relation, note ->
                onSavePerson(name, month, day, relation, note)
                showPersonDialog = false
            },
        )
    }

    if (showGiftDialog) {
        AddGiftDialog(
            people = people,
            onDismiss = { showGiftDialog = false },
            onConfirm = { personId, date, direction, giftName, price, note ->
                onSaveGift(personId, date, direction, giftName, price, note)
                showGiftDialog = false
            },
        )
    }
}

@Composable
private fun TodosScreen(
    modifier: Modifier,
    todos: List<TodoSummary>,
    onSaveTodo: (String, String, String, RecurrenceDraft) -> Unit,
    onCompleteTodo: (Long) -> Unit,
    onReopenTodo: (Long) -> Unit,
) {
    var showTodoDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "轻量 Todo", subtitle = "支持单次、常见周期和高级表单规则") {
            Button(onClick = { showTodoDialog = true }) {
                Text("新增 Todo")
            }
        }
        if (todos.isEmpty()) {
            SectionCard(title = "待办列表", subtitle = "暂无待办") {
                EmptyState("生日前两周生成的准备礼物任务也会出现在这里。")
            }
        } else {
            todos.forEach { todo ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(todo.title, fontWeight = FontWeight.Bold)
                        if (todo.description.isNotBlank()) {
                            Text(todo.description)
                        }
                        Text("状态：${if (todo.isCompleted) "已完成" else "进行中"}")
                        Text("下次提醒：${LedgerLogic.formatDateTime(todo.nextTriggerAt ?: todo.reminderAt ?: todo.dueAt)}")
                        todo.lastCompletedAt?.let {
                            Text("最近完成：${LedgerLogic.formatDateTime(it)}")
                        }
                        todo.recurrence?.let {
                            Text("规则：${recurrenceSummary(it)}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (todo.isCompleted) {
                                OutlinedButton(onClick = { onReopenTodo(todo.id) }) {
                                    Text("重新打开")
                                }
                            } else {
                                Button(onClick = { onCompleteTodo(todo.id) }) {
                                    Text("标记完成")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showTodoDialog) {
        AddTodoDialog(
            onDismiss = { showTodoDialog = false },
            onConfirm = { title, description, reminderText, recurrence ->
                onSaveTodo(title, description, reminderText, recurrence)
                showTodoDialog = false
            },
        )
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    failedAttempts: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChangePassword: (String, String, String) -> Unit,
) {
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "数据与安全", subtitle = "已导出的 JSON 不会因为密码输错而被删除") {
            SummaryValue("当前失败次数", failedAttempts.toString())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Text("导出数据")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("导入数据")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("修改密码")
            }
        }
        SectionCard(title = "录入说明", subtitle = "关键输入格式") {
            Text("• 日期与时间：点击对应输入框使用选择器")
            Text("• 金额：支持 123 或 123.45")
            Text("• 高级 Todo 通过表单生成规则，不需要手写 cron")
        }
    }
    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { current, next, confirm ->
                onChangePassword(current, next, confirm)
                showPasswordDialog = false
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            content()
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("银行卡") }
    var number by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var include by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(name, type, number, note, include) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("新增账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") })
                OutlinedTextField(type, { type = it }, label = { Text("类型") })
                OutlinedTextField(number, { number = it }, label = { Text("完整号码") })
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("计入总资产")
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = include, onCheckedChange = { include = it })
                }
            }
        },
    )
}

@Composable
private fun AddSnapshotDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, List<SnapshotBalanceDraft>, List<SnapshotExpenseDraft>) -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf("") }
    var nextRecordText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    var debtLabel by rememberSaveable { mutableStateOf("") }
    var debtAmountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val balances = remember(accounts) {
        mutableStateListOf<SnapshotBalanceDraft>().apply {
            addAll(accounts.map { SnapshotBalanceDraft(accountId = it.id, amountText = "") })
        }
    }
    val expenses = remember {
        mutableStateListOf<SnapshotExpenseDraft>()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        dateText,
                        nextRecordText,
                        targetText,
                        debtLabel,
                        debtAmountText,
                        note,
                        balances.toList(),
                        expenses.toList(),
                    )
                },
            ) {
                Text("保存快照")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        title = { Text("新增资产快照") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("日期留空则使用当前时间。")
                DatePickerField(label = "快照日期", value = dateText, onValueChange = { dateText = it })
                DatePickerField(label = "下次记录", value = nextRecordText, onValueChange = { nextRecordText = it })
                OutlinedTextField(targetText, { targetText = it }, label = { Text("目标总资产") })
                OutlinedTextField(debtLabel, { debtLabel = it }, label = { Text("负债说明") })
                OutlinedTextField(debtAmountText, { debtAmountText = it }, label = { Text("负债金额") })
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
                Text("账户余额")
                accounts.forEachIndexed { index, account ->
                    OutlinedTextField(
                        value = balances.getOrNull(index)?.amountText.orEmpty(),
                        onValueChange = { balances[index] = balances[index].copy(amountText = it) },
                        label = { Text(account.name) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("花销分类（最多 10 个）")
                    OutlinedButton(
                        onClick = {
                            if (expenses.size < 10) expenses.add(SnapshotExpenseDraft("", ""))
                        },
                    ) {
                        Text("新增分类")
                    }
                }
                expenses.forEachIndexed { index, expense ->
                    OutlinedTextField(
                        value = expense.categoryName,
                        onValueChange = { expenses[index] = expenses[index].copy(categoryName = it) },
                        label = { Text("分类名称") },
                    )
                    OutlinedTextField(
                        value = expense.amountText,
                        onValueChange = { expenses[index] = expenses[index].copy(amountText = it) },
                        label = { Text("分类金额") },
                    )
                }
            }
        },
    )
}

@Composable
private fun AddPersonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf("") }
    var relation by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(name, month, day, relation, note) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("新增联系人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var monthMenuExpanded by remember { mutableStateOf(false) }
                    var dayMenuExpanded by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { monthMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (month.isEmpty()) "生日月" else "${month}月")
                        }
                        DropdownMenu(expanded = monthMenuExpanded, onDismissRequest = { monthMenuExpanded = false }) {
                            (1..12).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m}月") },
                                    onClick = {
                                        month = m.toString()
                                        monthMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { dayMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (day.isEmpty()) "生日日" else "${day}日")
                        }
                        DropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                            (1..31).forEach { d ->
                                DropdownMenuItem(
                                    text = { Text("${d}日") },
                                    onClick = {
                                        day = d.toString()
                                        dayMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(relation, { relation = it }, label = { Text("关系") })
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
            }
        },
    )
}

@Composable
private fun AddGiftDialog(
    people: List<PersonLedgerSummary>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, GiftDirection, String, String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedPersonId by rememberSaveable { mutableStateOf(people.firstOrNull()?.id ?: 0L) }
    var dateText by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf(GiftDirection.SENT) }
    var giftName by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(selectedPersonId, dateText, direction, giftName, priceText, note) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("新增礼物记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(people.firstOrNull { it.id == selectedPersonId }?.name ?: "选择联系人")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        people.forEach { person ->
                            DropdownMenuItem(
                                text = { Text(person.name) },
                                onClick = {
                                    selectedPersonId = person.id
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                DatePickerField(label = "日期", value = dateText, onValueChange = { dateText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == GiftDirection.SENT,
                        onClick = { direction = GiftDirection.SENT },
                        label = { Text("我送出") },
                    )
                    FilterChip(
                        selected = direction == GiftDirection.RECEIVED,
                        onClick = { direction = GiftDirection.RECEIVED },
                        label = { Text("对方送我") },
                    )
                }
                OutlinedTextField(giftName, { giftName = it }, label = { Text("礼物名称") })
                OutlinedTextField(priceText, { priceText = it }, label = { Text("价格") })
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, RecurrenceDraft) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var reminderText by rememberSaveable { mutableStateOf("") }
    var recurrenceMode by rememberSaveable { mutableStateOf(RecurrenceMode.NONE) }
    var intervalText by rememberSaveable { mutableStateOf("1") }
    val weekdays = remember { mutableStateListOf<Int>() }
    var dayOfMonthText by rememberSaveable { mutableStateOf("") }
    val months = remember { mutableStateListOf<Int>() }
    var hourText by rememberSaveable { mutableStateOf("") }
    var minuteText by rememberSaveable { mutableStateOf("") }

    val preview = remember(
        reminderText,
        recurrenceMode,
        intervalText,
        weekdays.toList(),
        dayOfMonthText,
        months.toList(),
        hourText,
        minuteText,
    ) {
        val anchor = LedgerLogic.parseOptionalDateTime(reminderText) ?: System.currentTimeMillis()
        val descriptor = RecurrenceDraft(
            mode = recurrenceMode,
            intervalText = intervalText,
            daysOfWeek = weekdays.toSet(),
            dayOfMonthText = dayOfMonthText,
            months = months.toSet(),
            hourText = hourText,
            minuteText = minuteText,
        ).let {
            if (it.mode == RecurrenceMode.NONE) {
                null
            } else {
                RecurrenceDescriptor(
                    mode = it.mode,
                    interval = it.intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    daysOfWeek = it.daysOfWeek.map(DayOfWeek::of).toSet(),
                    dayOfMonth = it.dayOfMonthText.toIntOrNull(),
                    months = it.months.map(Month::of).toSet(),
                    hour = it.hourText.toIntOrNull(),
                    minute = it.minuteText.toIntOrNull(),
                )
            }
        }
        LedgerLogic.computeNextOccurrence(descriptor, anchorMillis = anchor, afterMillis = System.currentTimeMillis() - 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title,
                        description,
                        reminderText,
                        RecurrenceDraft(
                            mode = recurrenceMode,
                            intervalText = intervalText,
                            daysOfWeek = weekdays.toSet(),
                            dayOfMonthText = dayOfMonthText,
                            months = months.toSet(),
                            hourText = hourText,
                            minuteText = minuteText,
                        ),
                    )
                },
            ) {
                Text("保存 Todo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("新增 Todo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") })
                OutlinedTextField(description, { description = it }, label = { Text("说明") })
                DateTimePickerField(label = "提醒时间", value = reminderText, onValueChange = { reminderText = it })
                Text("循环规则")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RecurrenceMode.NONE to "单次",
                        RecurrenceMode.DAILY to "每天",
                        RecurrenceMode.WEEKLY to "每周",
                        RecurrenceMode.MONTHLY to "每月",
                        RecurrenceMode.ADVANCED to "高级",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = recurrenceMode == mode,
                            onClick = { recurrenceMode = mode },
                            label = { Text(label) },
                        )
                    }
                }
                if (recurrenceMode != RecurrenceMode.NONE) {
                    OutlinedTextField(intervalText, { intervalText = it }, label = { Text("间隔") })
                }
                if (recurrenceMode == RecurrenceMode.WEEKLY || recurrenceMode == RecurrenceMode.ADVANCED) {
                    Text("星期")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "一", 2 to "二", 3 to "三", 4 to "四", 5 to "五", 6 to "六", 7 to "日").forEach { (value, label) ->
                            FilterChip(
                                selected = weekdays.contains(value),
                                onClick = {
                                    if (weekdays.contains(value)) weekdays.remove(value) else weekdays.add(value)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                if (recurrenceMode == RecurrenceMode.MONTHLY || recurrenceMode == RecurrenceMode.ADVANCED) {
                    OutlinedTextField(dayOfMonthText, { dayOfMonthText = it }, label = { Text("每月第几日") })
                }
                if (recurrenceMode == RecurrenceMode.ADVANCED) {
                    Text("月份过滤")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..12).forEach { month ->
                            FilterChip(
                                selected = months.contains(month),
                                onClick = {
                                    if (months.contains(month)) months.remove(month) else months.add(month)
                                },
                                label = { Text(month.toString()) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(hourText, { hourText = it }, label = { Text("时") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(minuteText, { minuteText = it }, label = { Text("分") }, modifier = Modifier.weight(1f))
                    }
                }
                Text("下一次触发预览：${LedgerLogic.formatDateTime(preview)}")
            }
        },
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(current, next, confirm) }) {
                Text("更新密码")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(current, { current = it }, label = { Text("当前密码") })
                OutlinedTextField(next, { next = it }, label = { Text("新密码") })
                OutlinedTextField(confirm, { confirm = it }, label = { Text("确认新密码") })
            }
        },
    )
}

private fun recurrenceSummary(descriptor: RecurrenceDescriptor): String {
    return when (descriptor.mode) {
        RecurrenceMode.DAILY -> "每 ${descriptor.interval} 天"
        RecurrenceMode.WEEKLY -> "每 ${descriptor.interval} 周 · ${descriptor.daysOfWeek.joinToString { it.name.take(3) }}"
        RecurrenceMode.MONTHLY -> "每 ${descriptor.interval} 月 · ${descriptor.dayOfMonth ?: "同日"} 日"
        RecurrenceMode.ADVANCED -> "高级规则 · 间隔 ${descriptor.interval}"
        RecurrenceMode.NONE -> "单次"
    }
}
