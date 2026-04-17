package com.codex.offlineledger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.codex.offlineledger.data.ThemeMode
import com.codex.offlineledger.data.entity.AccountEntity
import com.codex.offlineledger.data.entity.ExpenseCategoryEntity
import com.codex.offlineledger.data.entity.GiftDirection
import com.codex.offlineledger.data.entity.RecurrenceMode
import com.codex.offlineledger.data.entity.TagEntity
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.Dp
import com.codex.offlineledger.domain.CurrencyUnit
import com.codex.offlineledger.domain.LedgerLogic
import com.codex.offlineledger.domain.PersonLedgerSummary
import com.codex.offlineledger.domain.RecurrenceDescriptor
import com.codex.offlineledger.domain.SnapshotSummary
import com.codex.offlineledger.domain.TodoSummary
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
    if (state.passwordSet && !state.unlocked) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            LockScreen(
                modifier = Modifier.padding(innerPadding),
                passwordSet = state.passwordSet,
                failedAttempts = state.failedAttempts,
                onUnlock = viewModel::unlock,
                onCreatePassword = viewModel::setPassword,
            )
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomNavShell(
                activeTab = state.activeTab,
                onTabSelect = viewModel::selectTab,
            )
        },
    ) { innerPadding ->
        when (state.activeTab) {
            LedgerTab.Notes -> NotesScreen(
                modifier = Modifier.padding(innerPadding),
                notes = state.notes,
                onSaveNote = viewModel::saveNote,
                onUpdateNote = viewModel::updateNote,
                onDeleteNote = viewModel::deleteNote,
            )

            LedgerTab.Todos -> TodosScreen(
                modifier = Modifier.padding(innerPadding),
                todos = state.todos,
                onSaveTodo = viewModel::saveTodo,
                onCompleteTodo = viewModel::completeTodo,
                onReopenTodo = viewModel::reopenTodo,
                onDeleteTodo = viewModel::deleteTodo,
            )

            LedgerTab.Settings -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                failedAttempts = state.failedAttempts,
                passwordSet = state.passwordSet,
                themeMode = state.themeMode,
                onExport = onExport,
                onImport = onImport,
                onEnablePassword = viewModel::enablePassword,
                onChangePassword = viewModel::changePassword,
                onDisablePassword = viewModel::disablePassword,
                onSetTheme = viewModel::setThemeMode,
            )

            LedgerTab.Assets -> AssetsScreen(
                modifier = Modifier.padding(innerPadding),
                accounts = state.accounts,
                snapshots = state.snapshots,
                categories = state.expenseCategories,
                unit = state.assetsUnit,
                tags = state.tags,
                allTags = state.allTags,
                selectedTagFilterIds = state.selectedTagFilterIds,
                onSaveAccount = viewModel::saveAccount,
                onUpdateAccount = viewModel::updateAccount,
                onArchiveAccount = viewModel::archiveAccount,
                onSaveSnapshot = viewModel::saveSnapshot,
                onDeleteSnapshot = viewModel::deleteSnapshot,
                onEditSnapshotAnnotation = viewModel::editSnapshotAnnotation,
                onSaveCategory = viewModel::saveExpenseCategory,
                onRenameCategory = viewModel::renameExpenseCategory,
                onArchiveCategory = viewModel::archiveExpenseCategory,
                onSetUnit = viewModel::setAssetsUnit,
                onSaveTag = viewModel::saveTag,
                onRenameTag = viewModel::renameTag,
                onArchiveTag = viewModel::archiveTag,
                onUnarchiveTag = viewModel::unarchiveTag,
                onDeleteTag = viewModel::deleteTagIfUnused,
                onToggleTagFilter = viewModel::toggleTagFilter,
                onClearTagFilter = viewModel::clearTagFilter,
                context = context,
            )

            LedgerTab.Gifts -> GiftsScreen(
                modifier = Modifier.padding(innerPadding),
                people = state.people,
                unit = state.giftsUnit,
                onSavePerson = viewModel::savePerson,
                onUpdatePerson = viewModel::updatePerson,
                onDeletePerson = viewModel::deletePerson,
                onReorderPeople = viewModel::reorderPeople,
                onSaveGift = viewModel::saveGift,
                onSetUnit = viewModel::setGiftsUnit,
            )
        }
    }

    state.pendingAccountRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestoreArchivedAccount,
            confirmButton = {
                Button(onClick = viewModel::confirmRestoreArchivedAccount) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestoreArchivedAccount) { Text("取消") }
            },
            title = { Text("发现同名归档账户") },
            text = {
                Text("已存在同名归档账户 \"${pending.archivedName}\"，是否恢复并更新为当前填写的内容？")
            },
        )
    }
}

private val LedgerTab.label: String
    get() = when (this) {
        LedgerTab.Notes -> "笔记"
        LedgerTab.Todos -> "Todo"
        LedgerTab.Settings -> "设置"
        LedgerTab.Assets -> "资产"
        LedgerTab.Gifts -> "人情"
    }

private val LedgerTab.icon: ImageVector
    get() = when (this) {
        LedgerTab.Notes -> Icons.Outlined.Create
        LedgerTab.Todos -> Icons.Outlined.CheckCircle
        LedgerTab.Settings -> Icons.Default.Settings
        LedgerTab.Assets -> Icons.Outlined.Home
        LedgerTab.Gifts -> Icons.Outlined.Favorite
    }

// --- Bottom Navigation Shell (v2: flat 80dp NavBar, icon/label crossfade) ---

@Composable
private fun BottomNavShell(
    activeTab: LedgerTab,
    onTabSelect: (LedgerTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LedgerTab.entries.forEach { tab ->
                BottomTabItem(
                    tab = tab,
                    selected = activeTab == tab,
                    onClick = onTabSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: LedgerTab,
    selected: Boolean,
    onClick: (LedgerTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tabIconAlpha",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tabLabelAlpha",
    )
    val iconTint = if (tab == LedgerTab.Settings) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { alpha = iconAlpha },
        )
        Text(
            text = tab.label,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
    }
}

// --- Lock Screen ---

@Composable
private fun LockScreen(
    modifier: Modifier = Modifier,
    passwordSet: Boolean,
    failedAttempts: Int,
    onUnlock: (String) -> Unit,
    onCreatePassword: (String, String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    Box(
        modifier = modifier
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
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                if (!passwordSet) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
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

// --- Picker Helpers ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = LedgerLogic.parseOptionalDate(value) ?: System.currentTimeMillis(),
    )
    OutlinedTextField(
        value = value, onValueChange = {}, label = { Text(label) }, readOnly = true,
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
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
                    datePickerState.selectedDateMillis?.let { onValueChange(LedgerLogic.formatDate(it)) }
                    showDialog = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } },
        ) { DatePicker(state = datePickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val initialMillis = LedgerLogic.parseOptionalDateTime(value) ?: System.currentTimeMillis()
    val initialDateTime = Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val timePickerState = rememberTimePickerState(initialHour = initialDateTime.hour, initialMinute = initialDateTime.minute)

    OutlinedTextField(
        value = value, onValueChange = {}, label = { Text(label) }, readOnly = true,
        modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
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
            confirmButton = { TextButton(onClick = { showDatePicker = false; showTimePicker = true }) { Text("下一步") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = datePickerState) }
    }
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onValueChange(LedgerLogic.formatDateTime(date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
            title = { Text("选择时间") },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

@Composable
private fun CurrencyUnitSelector(unit: CurrencyUnit, onUnitChange: (CurrencyUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(unit.label) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyUnit.entries.forEach { u ->
                DropdownMenuItem(text = { Text(u.label) }, onClick = { onUnitChange(u); expanded = false })
            }
        }
    }
}

// --- Notes Screen ---

@Composable
private fun NotesScreen(
    modifier: Modifier,
    notes: List<com.codex.offlineledger.data.entity.NoteEntity>,
    onSaveNote: (String, String) -> Unit,
    onUpdateNote: (Long, String, String) -> Unit,
    onDeleteNote: (Long) -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<com.codex.offlineledger.data.entity.NoteEntity?>(null) }
    var deletingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var expandedNoteId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "笔记", subtitle = "点击展开查看正文") {
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新增笔记")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(notes, key = { it.id }) { note ->
                val isExpanded = expandedNoteId == note.id
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        expandedNoteId = if (isExpanded) null else note.id
                    },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(note.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                        Text(
                            "创建 ${LedgerLogic.formatDateTime(note.createdAt)} · 更新 ${LedgerLogic.formatDateTime(note.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider()
                                Text(note.body.ifBlank { "（无正文）" })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { editingNote = note }) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("编辑")
                                    }
                                    OutlinedButton(onClick = { deletingNoteId = note.id }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (notes.isEmpty()) {
                item { EmptyState("还没有笔记，点击上方按钮新增。") }
            }
        }
    }

    if (showAddDialog) {
        NoteDialog(
            title = "新增笔记",
            initialTitle = "",
            initialBody = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { t, b -> onSaveNote(t, b); showAddDialog = false },
        )
    }
    editingNote?.let { note ->
        NoteDialog(
            title = "编辑笔记",
            initialTitle = note.title,
            initialBody = note.body,
            onDismiss = { editingNote = null },
            onConfirm = { t, b -> onUpdateNote(note.id, t, b); editingNote = null },
        )
    }
    deletingNoteId?.let { id ->
        ConfirmDeleteDialog(
            message = "确定要删除这条笔记吗？此操作不可撤销。",
            onDismiss = { deletingNoteId = null },
            onConfirm = { onDeleteNote(id); deletingNoteId = null },
        )
    }
}

@Composable
private fun NoteDialog(
    title: String,
    initialTitle: String,
    initialBody: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var noteTitle by rememberSaveable { mutableStateOf(initialTitle) }
    var noteBody by rememberSaveable { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(noteTitle, noteBody) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(noteTitle, { noteTitle = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    noteBody, { noteBody = it }, label = { Text("正文") },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    maxLines = 10,
                )
            }
        },
    )
}

// --- Assets Screen ---

@Composable
private fun AssetsScreen(
    modifier: Modifier,
    accounts: List<AccountEntity>,
    snapshots: List<SnapshotSummary>,
    categories: List<ExpenseCategoryEntity>,
    unit: CurrencyUnit,
    tags: List<TagEntity>,
    allTags: List<TagEntity>,
    selectedTagFilterIds: Set<Long>,
    onSaveAccount: (String, String, String, String, Boolean) -> Unit,
    onUpdateAccount: (Long, String, String, String, String, Boolean) -> Unit,
    onArchiveAccount: (Long) -> Unit,
    onSaveSnapshot: (String, String, String, String, String, String, List<SnapshotBalanceDraft>, List<SnapshotExpenseDraft>, CurrencyUnit, Int?, List<Long>) -> Unit,
    onDeleteSnapshot: (Long) -> Unit,
    onEditSnapshotAnnotation: (Long, Int?, String, List<Long>) -> Unit,
    onSaveCategory: (String) -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onArchiveCategory: (Long) -> Unit,
    onSetUnit: (CurrencyUnit) -> Unit,
    onSaveTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onArchiveTag: (Long) -> Unit,
    onUnarchiveTag: (Long) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onToggleTagFilter: (Long) -> Unit,
    onClearTagFilter: () -> Unit,
    context: Context,
) {
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showSnapshotDialog by rememberSaveable { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var pendingArchiveAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showTagManager by rememberSaveable { mutableStateOf(false) }
    var editingAnnotationSnapshot by remember { mutableStateOf<SnapshotSummary?>(null) }
    val latest = snapshots.firstOrNull()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "资产总览", subtitle = "以快照日期排序，支持历史补录") {
            SummaryValue("当前总资产", LedgerLogic.formatCurrencySmart(latest?.total))
            SummaryValue("最近记录", LedgerLogic.formatDate(latest?.snapshotDate))
            SummaryValue(
                "相对上次变化",
                latest?.changeFromPrevious?.let {
                    "${if (it >= 0) "+" else ""}${LedgerLogic.formatCurrencySmart(it)}"
                } ?: "-",
            )
            SummaryValue(
                "目标状态",
                when (latest?.goalReached) { true -> "已达成"; false -> "未达成"; null -> "未设置" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("金额单位")
                CurrencyUnitSelector(unit = unit, onUnitChange = onSetUnit)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showAccountDialog = true }) { Text("新增账户") }
                OutlinedButton(onClick = { showSnapshotDialog = true }) { Text("新增快照") }
            }
        }

        SectionCard(title = "花销分类", subtitle = "配置后可在快照中复用（最多 10 个）") {
            categories.forEach { cat ->
                var showRename by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cat.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onArchiveCategory(cat.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "归档", modifier = Modifier.size(18.dp))
                    }
                }
                if (showRename) {
                    var newName by rememberSaveable { mutableStateOf(cat.name) }
                    AlertDialog(
                        onDismissRequest = { showRename = false },
                        confirmButton = { Button(onClick = { onRenameCategory(cat.id, newName); showRename = false }) { Text("确定") } },
                        dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } },
                        title = { Text("重命名分类") },
                        text = { OutlinedTextField(newName, { newName = it }, label = { Text("新名称") }) },
                    )
                }
            }
            Button(onClick = { showAddCategoryDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新增分类")
            }
        }

        SectionCard(title = "账户", subtitle = "列表默认遮挡号码，详情中可复制") {
            if (accounts.isEmpty()) {
                EmptyState("还没有账户，先创建银行卡、支付宝或现金账户。")
            } else {
                accounts.forEach { account ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().clickable { selectedAccount = account },
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

        if (snapshots.size >= 1) {
            SectionCard(title = "资产趋势", subtitle = "总资产及各账户余额走势") {
                AssetTrendChart(snapshots = snapshots, accounts = accounts)
            }
        }

        SectionCard(title = "标签管理", subtitle = "重命名、归档或删除标签") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showTagManager = true }) { Text("管理标签") }
            }
        }

        if (tags.isNotEmpty() || selectedTagFilterIds.isNotEmpty()) {
            SectionCard(title = "按标签筛选", subtitle = "OR 匹配：选中其一即纳入") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRowSimple(
                        spacing = 6.dp,
                    ) {
                        tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTagFilterIds,
                                onClick = { onToggleTagFilter(tag.id) },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                    if (selectedTagFilterIds.isNotEmpty()) {
                        TextButton(onClick = onClearTagFilter) { Text("重置筛选") }
                    }
                }
            }
        }

        SectionCard(title = "历史快照", subtitle = "点击展开查看账户对比") {
            if (snapshots.isEmpty()) {
                EmptyState(
                    if (selectedTagFilterIds.isEmpty()) "还没有快照。你可以补录最近三年的资产历史。"
                    else "没有匹配当前标签筛选的快照。",
                )
            } else {
                snapshots.forEach { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        onDelete = onDeleteSnapshot,
                        onEditAnnotation = { editingAnnotationSnapshot = snapshot },
                    )
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
            categories = categories,
            unit = unit,
            tags = tags,
            onDismiss = { showSnapshotDialog = false },
            onConfirm = { date, nextRecord, target, debtLabel, debtAmount, note, balances, expenses, mood, tagIds ->
                onSaveSnapshot(date, nextRecord, target, debtLabel, debtAmount, note, balances, expenses, unit, mood, tagIds)
                showSnapshotDialog = false
            },
        )
    }
    editingAnnotationSnapshot?.let { snap ->
        EditSnapshotAnnotationDialog(
            snapshot = snap,
            tags = tags,
            onDismiss = { editingAnnotationSnapshot = null },
            onConfirm = { mood, note, tagIds ->
                onEditSnapshotAnnotation(snap.id, mood, note, tagIds)
                editingAnnotationSnapshot = null
            },
        )
    }
    if (showTagManager) {
        TagManagerDialog(
            allTags = allTags,
            onDismiss = { showTagManager = false },
            onAdd = onSaveTag,
            onRename = onRenameTag,
            onArchive = onArchiveTag,
            onUnarchive = onUnarchiveTag,
            onDelete = onDeleteTag,
        )
    }
    if (showAddCategoryDialog) {
        var catName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            confirmButton = { Button(onClick = { onSaveCategory(catName); showAddCategoryDialog = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showAddCategoryDialog = false }) { Text("取消") } },
            title = { Text("新增花销分类") },
            text = { OutlinedTextField(catName, { catName = it }, label = { Text("分类名称") }) },
        )
    }
    selectedAccount?.let { account ->
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AlertDialog(
            onDismissRequest = { selectedAccount = null },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("accountNumber", account.accountNumber))
                    selectedAccount = null
                }) { Text("复制号码") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        editingAccount = account
                        selectedAccount = null
                    }) { Text("编辑") }
                    TextButton(onClick = {
                        pendingArchiveAccount = account
                        selectedAccount = null
                    }) { Text("归档") }
                    TextButton(onClick = { selectedAccount = null }) { Text("关闭") }
                }
            },
            title = { Text(account.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("类型：${account.type}")
                    Text("完整号码：${account.accountNumber}")
                    Text("备注：${account.note.ifBlank { "-" }}")
                    Text(if (account.includeInNetWorth) "计入总资产" else "不计入总资产")
                }
            },
        )
    }
    editingAccount?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onConfirm = { name, type, number, note, include ->
                onUpdateAccount(account.id, name, type, number, note, include)
                editingAccount = null
            },
        )
    }
    pendingArchiveAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingArchiveAccount = null },
            confirmButton = {
                Button(onClick = {
                    onArchiveAccount(account.id)
                    pendingArchiveAccount = null
                }) { Text("归档") }
            },
            dismissButton = { TextButton(onClick = { pendingArchiveAccount = null }) { Text("取消") } },
            title = { Text("归档账户") },
            text = { Text("账户将不再出现在列表中，历史快照将保留。确定归档？") },
        )
    }
}

@Composable
private fun AssetTrendChart(snapshots: List<SnapshotSummary>, accounts: List<AccountEntity>) {
    var showTotal by rememberSaveable { mutableStateOf(true) }
    val chrono = remember(snapshots) { snapshots.reversed() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(chrono, showTotal) {
        if (chrono.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                if (showTotal) {
                    series(chrono.map { it.total.toDouble() })
                } else {
                    val accountIds = accounts.map { it.id }
                    accountIds.forEach { accountId ->
                        series(chrono.map { snapshot ->
                            snapshot.balances.firstOrNull { it.accountId == accountId }?.amount?.toDouble() ?: 0.0
                        })
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showTotal, onClick = { showTotal = true }, label = { Text("总资产") })
            FilterChip(selected = !showTotal, onClick = { showTotal = false }, label = { Text("各账户") })
        }
        CartesianChartHost(
            rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer,
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(8.dp),
            rememberVicoScrollState(scrollEnabled = false),
        )
    }
}

@Composable
private fun SnapshotCard(
    snapshot: SnapshotSummary,
    onDelete: (Long) -> Unit,
    onEditAnnotation: () -> Unit,
) {
    var expanded by rememberSaveable(snapshot.id) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val moodEmoji = LedgerLogic.moodEmoji(snapshot.mood)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(LedgerLogic.formatDate(snapshot.snapshotDate), fontWeight = FontWeight.Bold)
                        if (moodEmoji != null) {
                            Text(moodEmoji)
                        }
                    }
                    Text("总资产 ${LedgerLogic.formatCurrencySmart(snapshot.total)}")
                }
                Row {
                    IconButton(onClick = onEditAnnotation) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑批注")
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "收起" else "展开",
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }
            if (snapshot.tags.isNotEmpty()) {
                FlowRowSimple(spacing = 4.dp) {
                    snapshot.tags.forEach { tag ->
                        FilterChip(
                            selected = !tag.archived,
                            onClick = {},
                            enabled = false,
                            label = { Text(tag.name) },
                        )
                    }
                }
            }
            Text(
                "变化 ${snapshot.changeFromPrevious?.let {
                    "${if (it >= 0) "+" else ""}${LedgerLogic.formatCurrencySmart(it)}"
                } ?: "-"}",
            )
            Text("下次记录 ${LedgerLogic.formatDate(snapshot.nextRecordAt)}")
            if (snapshot.targetTotal != null) {
                Text("目标 ${LedgerLogic.formatCurrencySmart(snapshot.targetTotal)} · ${if (snapshot.goalReached == true) "已达成" else "未达成"}")
            }
            if (snapshot.debtAmount != null || snapshot.debtLabel.isNotBlank()) {
                Text("负债 ${snapshot.debtLabel.ifBlank { "未命名" }} · ${LedgerLogic.formatCurrencySmart(snapshot.debtAmount)}")
            }
            if (snapshot.note.isNotBlank()) {
                Text(
                    "心得：${snapshot.note}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (snapshot.expenses.isNotEmpty()) {
                Text("本期花销")
                snapshot.expenses.forEach {
                    Text("• ${it.categoryName}: ${LedgerLogic.formatCurrencySmart(it.amount)}")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    snapshot.balances.forEach {
                        Text(
                            "• ${it.accountName}: ${LedgerLogic.formatCurrencySmart(it.amount)}" +
                                (it.deltaFromPrevious?.let { delta ->
                                    "（${if (delta >= 0) "+" else ""}${LedgerLogic.formatCurrencySmart(delta)}）"
                                } ?: ""),
                        )
                    }
                }
            }
        }
    }
    if (showDeleteConfirm) {
        ConfirmDeleteDialog(
            message = "确定要删除这条快照吗？关联的余额和花销记录也会被删除。",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = { onDelete(snapshot.id); showDeleteConfirm = false },
        )
    }
}

// --- Gifts Screen ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GiftsScreen(
    modifier: Modifier,
    people: List<PersonLedgerSummary>,
    unit: CurrencyUnit,
    onSavePerson: (String, String, String, String, String) -> Unit,
    onUpdatePerson: (Long, String, String, String, String, String) -> Unit,
    onDeletePerson: (Long) -> Unit,
    onReorderPeople: (List<Long>) -> Unit,
    onSaveGift: (Long, String, GiftDirection, String, String, String, CurrencyUnit) -> Unit,
    onSetUnit: (CurrencyUnit) -> Unit,
) {
    var showPersonDialog by rememberSaveable { mutableStateOf(false) }
    var showGiftDialog by rememberSaveable { mutableStateOf(false) }
    var expandedPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingPerson by remember { mutableStateOf<PersonLedgerSummary?>(null) }
    var deletingPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    var contextMenuPersonId by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = remember { LocalDate.now() }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "人情往来", subtitle = "生日临近时联系人会有颜色提示") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("金额单位")
                CurrencyUnitSelector(unit = unit, onUnitChange = onSetUnit)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showPersonDialog = true }) { Text("新增联系人") }
                OutlinedButton(onClick = { showGiftDialog = true }) { Text("新增礼物记录") }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (people.isEmpty()) {
                item { EmptyState("先录入联系人（生日可选），再补送礼台账。") }
            }
            items(people, key = { it.id }) { person ->
                val isExpanded = expandedPersonId == person.id
                val birthdaySoon = LedgerLogic.isBirthdayApproaching(today, person.birthdayMonth, person.birthdayDay)
                val nameColor = if (birthdaySoon) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                val showContextMenu = contextMenuPersonId == person.id

                Card(
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { expandedPersonId = if (isExpanded) null else person.id },
                        onLongClick = { contextMenuPersonId = person.id },
                    ),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.name, fontWeight = FontWeight.Bold, color = nameColor)
                                val birthdayText = if (person.birthdayMonth != null && person.birthdayDay != null) {
                                    "生日 ${person.birthdayMonth}月${person.birthdayDay}日 · "
                                } else {
                                    ""
                                }
                                Text(
                                    "$birthdayText${person.relation.ifBlank { "关系未填" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Box {
                                Row {
                                    IconButton(onClick = {
                                        val ids = people.map { it.id }.toMutableList()
                                        val idx = ids.indexOf(person.id)
                                        if (idx > 0) { ids.removeAt(idx); ids.add(idx - 1, person.id); onReorderPeople(ids) }
                                    }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(20.dp)) }
                                    IconButton(onClick = {
                                        val ids = people.map { it.id }.toMutableList()
                                        val idx = ids.indexOf(person.id)
                                        if (idx < ids.size - 1) { ids.removeAt(idx); ids.add(idx + 1, person.id); onReorderPeople(ids) }
                                    }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(20.dp)) }
                                    IconButton(onClick = { contextMenuPersonId = person.id }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(20.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = showContextMenu,
                                    onDismissRequest = { contextMenuPersonId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("编辑") },
                                        onClick = { editingPerson = person; contextMenuPersonId = null },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = { deletingPersonId = person.id; contextMenuPersonId = null },
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                HorizontalDivider()
                                if (person.gifts.isEmpty()) {
                                    Text("暂时还没有礼物记录", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                } else {
                                    person.gifts.forEach { gift ->
                                        val bgColor = if (gift.directionLabel == "我送出") {
                                            Color(0xFFFFF3E0)
                                        } else {
                                            Color(0xFFE8F5E9)
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(bgColor, RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                        ) {
                                            Text(
                                                "${LedgerLogic.formatDate(gift.date)} · ${gift.directionLabel} · ${gift.giftName} · ${LedgerLogic.formatCurrencySmart(gift.price)}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        if (gift.note.isNotBlank()) {
                                            Text("  ${gift.note}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
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
            people = people, unit = unit,
            onDismiss = { showGiftDialog = false },
            onConfirm = { personId, date, direction, giftName, price, note ->
                onSaveGift(personId, date, direction, giftName, price, note, unit)
                showGiftDialog = false
            },
        )
    }
    editingPerson?.let { person ->
        EditPersonDialog(
            person = person,
            onDismiss = { editingPerson = null },
            onConfirm = { name, month, day, relation, note ->
                onUpdatePerson(person.id, name, month, day, relation, note)
                editingPerson = null
            },
        )
    }
    deletingPersonId?.let { id ->
        ConfirmDeleteDialog(
            message = "确定要删除这位联系人吗？关联的所有礼物记录也会被删除。",
            onDismiss = { deletingPersonId = null },
            onConfirm = { onDeletePerson(id); deletingPersonId = null },
        )
    }
}

// --- Todos Screen ---

@Composable
private fun TodosScreen(
    modifier: Modifier,
    todos: List<TodoSummary>,
    onSaveTodo: (String, String, String, RecurrenceDraft) -> Unit,
    onCompleteTodo: (Long) -> Unit,
    onReopenTodo: (Long) -> Unit,
    onDeleteTodo: (Long) -> Unit,
) {
    var showTodoDialog by rememberSaveable { mutableStateOf(false) }
    var deletingTodoId by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "轻量 Todo", subtitle = "支持单次、常见周期和高级表单规则") {
            Button(onClick = { showTodoDialog = true }) { Text("新增 Todo") }
        }
        if (todos.isEmpty()) {
            SectionCard(title = "待办列表", subtitle = "暂无待办") {
                EmptyState("还没有待办事项。")
            }
        } else {
            todos.forEach { todo ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(todo.title, fontWeight = FontWeight.Bold)
                        if (todo.description.isNotBlank()) Text(todo.description)
                        Text("状态：${if (todo.isCompleted) "已完成" else "进行中"}")
                        Text("下次提醒：${LedgerLogic.formatDateTime(todo.nextTriggerAt ?: todo.reminderAt ?: todo.dueAt)}")
                        todo.lastCompletedAt?.let { Text("最近完成：${LedgerLogic.formatDateTime(it)}") }
                        todo.recurrence?.let { Text("规则：${recurrenceSummary(it)}") }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (todo.isCompleted) {
                                OutlinedButton(onClick = { onReopenTodo(todo.id) }) { Text("重新打开") }
                            } else {
                                Button(onClick = { onCompleteTodo(todo.id) }) { Text("标记完成") }
                            }
                            OutlinedButton(onClick = { deletingTodoId = todo.id }) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("删除")
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
    deletingTodoId?.let { id ->
        ConfirmDeleteDialog(
            message = "确定要删除这条 Todo 吗？循环规则也会一起删除。",
            onDismiss = { deletingTodoId = null },
            onConfirm = { onDeleteTodo(id); deletingTodoId = null },
        )
    }
}

// --- Settings Screen ---

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    failedAttempts: Int,
    passwordSet: Boolean,
    themeMode: ThemeMode,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onEnablePassword: (String, String) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onDisablePassword: (String) -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
) {
    var showEnableDialog by rememberSaveable { mutableStateOf(false) }
    var showChangeDialog by rememberSaveable { mutableStateOf(false) }
    var showDisableDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "外观", subtitle = "选择应用主题") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to "跟随系统",
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onSetTheme(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }

        SectionCard(title = "数据与安全", subtitle = "已导出的 JSON 不会因为密码输错而被删除") {
            SummaryValue("当前失败次数", failedAttempts.toString())
            SummaryValue("应用密码", if (passwordSet) "已启用" else "未启用")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出数据") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入数据") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (passwordSet) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showChangeDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("修改密码")
                    }
                    OutlinedButton(onClick = { showDisableDialog = true }, modifier = Modifier.weight(1f)) {
                        Text("关闭密码")
                    }
                }
            } else {
                Button(onClick = { showEnableDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("启用应用密码")
                }
            }
        }

        SectionCard(title = "录入说明", subtitle = "关键输入格式") {
            Text("• 日期与时间：点击对应输入框使用选择器")
            Text("• 金额：整数输入，单位可选（元/千/万）")
            Text("• 高级 Todo 通过表单生成规则，不需要手写 cron")
        }
    }

    if (showEnableDialog) {
        EnablePasswordDialog(
            onDismiss = { showEnableDialog = false },
            onConfirm = { new, confirm ->
                onEnablePassword(new, confirm)
                showEnableDialog = false
            },
        )
    }
    if (showChangeDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangeDialog = false },
            onConfirm = { current, next, confirm ->
                onChangePassword(current, next, confirm)
                showChangeDialog = false
            },
        )
    }
    if (showDisableDialog) {
        DisablePasswordDialog(
            onDismiss = { showDisableDialog = false },
            onConfirm = { current ->
                onDisablePassword(current)
                showDisableDialog = false
            },
        )
    }
}

// --- Utility Composables ---

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) { Text(text) }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConfirmDeleteDialog(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text("确定删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("确认删除") },
        text = { Text(message) },
    )
}

// --- Dialogs ---

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit,
) {
    AccountFormDialog(
        title = "新增账户",
        initialName = "",
        initialType = "银行卡",
        initialNumber = "",
        initialNote = "",
        initialInclude = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit,
) {
    AccountFormDialog(
        title = "编辑账户",
        initialName = account.name,
        initialType = account.type,
        initialNumber = account.accountNumber,
        initialNote = account.note,
        initialInclude = account.includeInNetWorth,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun AccountFormDialog(
    title: String,
    initialName: String,
    initialType: String,
    initialNumber: String,
    initialNote: String,
    initialInclude: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var include by rememberSaveable { mutableStateOf(initialInclude) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(name, type, number, note, include) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
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
    categories: List<ExpenseCategoryEntity>,
    unit: CurrencyUnit,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, List<SnapshotBalanceDraft>, List<SnapshotExpenseDraft>, Int?, List<Long>) -> Unit,
) {
    var dateText by rememberSaveable { mutableStateOf("") }
    var nextRecordText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    var debtLabel by rememberSaveable { mutableStateOf("") }
    var debtAmountText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedTagIds = remember { mutableStateListOf<Long>() }
    val balances = remember(accounts) {
        mutableStateListOf<SnapshotBalanceDraft>().apply {
            addAll(accounts.map { SnapshotBalanceDraft(accountId = it.id, amountText = "") })
        }
    }
    val expenses = remember(categories) {
        mutableStateListOf<SnapshotExpenseDraft>().apply {
            addAll(categories.map { SnapshotExpenseDraft(categoryId = it.id, categoryName = it.name, amountText = "") })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    dateText, nextRecordText, targetText, debtLabel, debtAmountText, note,
                    balances.toList(), expenses.toList(), mood, selectedTagIds.toList(),
                )
            }) {
                Text("保存快照")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("新增资产快照（单位: ${unit.label}）") },
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
                Text("心情", fontWeight = FontWeight.SemiBold)
                MoodSelector(mood = mood, onMoodChange = { mood = it })
                Text("标签", fontWeight = FontWeight.SemiBold)
                TagInputField(
                    allTags = tags,
                    selectedTagIds = selectedTagIds,
                )
                OutlinedTextField(note, { note = it }, label = { Text("心得 / 备注") }, modifier = Modifier.fillMaxWidth())
                Text("账户余额", fontWeight = FontWeight.SemiBold)
                accounts.forEachIndexed { index, account ->
                    OutlinedTextField(
                        value = balances.getOrNull(index)?.amountText.orEmpty(),
                        onValueChange = { balances[index] = balances[index].copy(amountText = it) },
                        label = { Text(account.name) },
                    )
                }
                Text("花销分类", fontWeight = FontWeight.SemiBold)
                expenses.forEachIndexed { index, expense ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(expense.categoryName, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = expense.amountText,
                            onValueChange = { expenses[index] = expenses[index].copy(amountText = it) },
                            label = { Text("金额") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                var newCatName by rememberSaveable { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newCatName, { newCatName = it },
                        label = { Text("临时新增分类") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        if (newCatName.isNotBlank()) {
                            expenses.add(SnapshotExpenseDraft(categoryId = 0, categoryName = newCatName.trim(), amountText = ""))
                            newCatName = ""
                        }
                    }) { Text("添加") }
                }
            }
        },
    )
}

@Composable
private fun MoodSelector(mood: Int?, onMoodChange: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { level ->
            val emoji = LedgerLogic.moodEmoji(level) ?: ""
            FilterChip(
                selected = mood == level,
                onClick = { onMoodChange(if (mood == level) null else level) },
                label = { Text(emoji) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

@Composable
private fun TagInputField(
    allTags: List<TagEntity>,
    selectedTagIds: MutableList<Long>,
) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (selectedTagIds.isNotEmpty()) {
            FlowRowSimple(spacing = 4.dp) {
                selectedTagIds.toList().forEach { id ->
                    val tag = allTags.firstOrNull { it.id == id }
                    FilterChip(
                        selected = true,
                        onClick = { selectedTagIds.remove(id) },
                        label = { Text(tag?.name ?: "#$id") },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("输入标签名筛选或新建") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        val filtered = allTags
            .filter { !it.archived && it.id !in selectedTagIds }
            .filter { input.isBlank() || it.name.contains(input.trim(), ignoreCase = true) }
            .take(8)
        if (filtered.isNotEmpty()) {
            FlowRowSimple(spacing = 4.dp) {
                filtered.forEach { tag ->
                    AssistChip(
                        onClick = {
                            if (tag.id !in selectedTagIds) selectedTagIds.add(tag.id)
                            input = ""
                        },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSnapshotAnnotationDialog(
    snapshot: SnapshotSummary,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Int?, String, List<Long>) -> Unit,
) {
    var mood by rememberSaveable(snapshot.id) { mutableStateOf(snapshot.mood) }
    var note by rememberSaveable(snapshot.id) { mutableStateOf(snapshot.note) }
    val selectedTagIds = remember(snapshot.id) {
        mutableStateListOf<Long>().apply { addAll(snapshot.tags.map { it.id }) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onConfirm(mood, note, selectedTagIds.toList()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("编辑批注") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "仅可修改情绪 / 标签 / 心得；金额、花销等不可修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("心情", fontWeight = FontWeight.SemiBold)
                MoodSelector(mood = mood, onMoodChange = { mood = it })
                Text("标签", fontWeight = FontWeight.SemiBold)
                TagInputField(allTags = tags, selectedTagIds = selectedTagIds)
                OutlinedTextField(
                    note,
                    { note = it },
                    label = { Text("心得") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun TagManagerDialog(
    allTags: List<TagEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onArchive: (Long) -> Unit,
    onUnarchive: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var renamingTag by remember { mutableStateOf<TagEntity?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text("标签管理") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新标签") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedButton(onClick = {
                        if (newName.isNotBlank()) {
                            onAdd(newName)
                            newName = ""
                        }
                    }) { Text("添加") }
                }
                HorizontalDivider()
                if (allTags.isEmpty()) {
                    Text("还没有标签。", style = MaterialTheme.typography.bodySmall)
                }
                allTags.forEach { tag ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (tag.archived) "(已归档) " else "") + tag.name,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { renamingTag = tag }) {
                            Icon(Icons.Default.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
                        }
                        if (tag.archived) {
                            TextButton(onClick = { onUnarchive(tag.id) }) { Text("恢复") }
                            IconButton(onClick = { onDelete(tag.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                            }
                        } else {
                            TextButton(onClick = { onArchive(tag.id) }) { Text("归档") }
                        }
                    }
                }
            }
        },
    )
    renamingTag?.let { tag ->
        var text by rememberSaveable(tag.id) { mutableStateOf(tag.name) }
        AlertDialog(
            onDismissRequest = { renamingTag = null },
            confirmButton = {
                Button(onClick = {
                    onRename(tag.id, text)
                    renamingTag = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamingTag = null }) { Text("取消") } },
            title = { Text("重命名标签") },
            text = { OutlinedTextField(text, { text = it }, label = { Text("新名称") }) },
        )
    }
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
        confirmButton = { Button(onClick = { onConfirm(name, month, day, relation, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("新增联系人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名") })
                BirthdayPicker(month = month, day = day, onMonthChange = { month = it }, onDayChange = { day = it })
                OutlinedTextField(relation, { relation = it }, label = { Text("关系") })
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
            }
        },
    )
}

@Composable
private fun EditPersonDialog(
    person: PersonLedgerSummary,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(person.name) }
    var month by rememberSaveable { mutableStateOf(person.birthdayMonth?.toString().orEmpty()) }
    var day by rememberSaveable { mutableStateOf(person.birthdayDay?.toString().orEmpty()) }
    var relation by rememberSaveable { mutableStateOf(person.relation) }
    var note by rememberSaveable { mutableStateOf(person.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(name, month, day, relation, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("编辑联系人") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名") })
                BirthdayPicker(month = month, day = day, onMonthChange = { month = it }, onDayChange = { day = it })
                OutlinedTextField(relation, { relation = it }, label = { Text("关系") })
                OutlinedTextField(note, { note = it }, label = { Text("备注") })
            }
        },
    )
}

@Composable
private fun BirthdayPicker(month: String, day: String, onMonthChange: (String) -> Unit, onDayChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        var monthMenuExpanded by remember { mutableStateOf(false) }
        var dayMenuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { monthMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (month.isEmpty()) "生日月（可空）" else "${month}月")
            }
            DropdownMenu(expanded = monthMenuExpanded, onDismissRequest = { monthMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("不填") },
                    onClick = { onMonthChange(""); onDayChange(""); monthMenuExpanded = false },
                )
                (1..12).forEach { m ->
                    DropdownMenuItem(text = { Text("${m}月") }, onClick = { onMonthChange(m.toString()); monthMenuExpanded = false })
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { dayMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (day.isEmpty()) "生日日（可空）" else "${day}日")
            }
            DropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("不填") },
                    onClick = { onDayChange(""); dayMenuExpanded = false },
                )
                (1..31).forEach { d ->
                    DropdownMenuItem(text = { Text("${d}日") }, onClick = { onDayChange(d.toString()); dayMenuExpanded = false })
                }
            }
        }
    }
}

@Composable
private fun AddGiftDialog(
    people: List<PersonLedgerSummary>,
    unit: CurrencyUnit,
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
        confirmButton = { Button(onClick = { onConfirm(selectedPersonId, dateText, direction, giftName, priceText, note) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("新增礼物记录（单位: ${unit.label}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(people.firstOrNull { it.id == selectedPersonId }?.name ?: "选择联系人")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        people.forEach { person ->
                            DropdownMenuItem(text = { Text(person.name) }, onClick = { selectedPersonId = person.id; expanded = false })
                        }
                    }
                }
                DatePickerField(label = "日期", value = dateText, onValueChange = { dateText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == GiftDirection.SENT, onClick = { direction = GiftDirection.SENT }, label = { Text("我送出") })
                    FilterChip(selected = direction == GiftDirection.RECEIVED, onClick = { direction = GiftDirection.RECEIVED }, label = { Text("对方送我") })
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

    val preview = remember(reminderText, recurrenceMode, intervalText, weekdays.toList(), dayOfMonthText, months.toList(), hourText, minuteText) {
        val anchor = LedgerLogic.parseOptionalDateTime(reminderText) ?: System.currentTimeMillis()
        val descriptor = RecurrenceDraft(
            mode = recurrenceMode, intervalText = intervalText, daysOfWeek = weekdays.toSet(),
            dayOfMonthText = dayOfMonthText, months = months.toSet(), hourText = hourText, minuteText = minuteText,
        ).let {
            if (it.mode == RecurrenceMode.NONE) null
            else RecurrenceDescriptor(
                mode = it.mode, interval = it.intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                daysOfWeek = it.daysOfWeek.map(DayOfWeek::of).toSet(), dayOfMonth = it.dayOfMonthText.toIntOrNull(),
                months = it.months.map(Month::of).toSet(), hour = it.hourText.toIntOrNull(), minute = it.minuteText.toIntOrNull(),
            )
        }
        LedgerLogic.computeNextOccurrence(descriptor, anchorMillis = anchor, afterMillis = System.currentTimeMillis() - 1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm(title, description, reminderText, RecurrenceDraft(
                    mode = recurrenceMode, intervalText = intervalText, daysOfWeek = weekdays.toSet(),
                    dayOfMonthText = dayOfMonthText, months = months.toSet(), hourText = hourText, minuteText = minuteText,
                ))
            }) { Text("保存 Todo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
                        RecurrenceMode.NONE to "单次", RecurrenceMode.DAILY to "每天", RecurrenceMode.WEEKLY to "每周",
                        RecurrenceMode.MONTHLY to "每月", RecurrenceMode.ADVANCED to "高级",
                    ).forEach { (mode, label) ->
                        FilterChip(selected = recurrenceMode == mode, onClick = { recurrenceMode = mode }, label = { Text(label) })
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
                                onClick = { if (weekdays.contains(value)) weekdays.remove(value) else weekdays.add(value) },
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
                                onClick = { if (months.contains(month)) months.remove(month) else months.add(month) },
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
        confirmButton = { Button(onClick = { onConfirm(current, next, confirm) }) { Text("更新密码") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "当前密码")
                PasswordField(next, { next = it }, "新密码")
                PasswordField(confirm, { confirm = it }, "确认新密码")
            }
        },
    )
}

@Composable
private fun EnablePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(next, confirm) }) { Text("启用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("启用应用密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(next, { next = it }, "新密码（至少 4 位）")
                PasswordField(confirm, { confirm = it }, "确认新密码")
            }
        },
    )
}

@Composable
private fun DisablePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = { onConfirm(current) }) { Text("关闭密码") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text("关闭应用密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "当前密码")
                Text(
                    "关闭后下次启动将直接进入主界面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
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
