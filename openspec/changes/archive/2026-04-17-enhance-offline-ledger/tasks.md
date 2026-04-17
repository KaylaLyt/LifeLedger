## 1. 数据模型重构

- [x] 1.1 金额存储从分改元：所有 Entity 中 `*InCents` 字段改为整数元（Long），去掉 InCents 后缀；同步更新 `LedgerLogic.parseCurrencyToCents` → `parseCurrency`、`formatCurrency` → 智能单位展示
- [x] 1.2 新增 `ExpenseCategoryEntity` 表（id, name UNIQUE, sortOrder, archived, createdAt），`snapshot_expenses` 的 `categoryName` 改为 `categoryId` FK（onDelete = RESTRICT）
- [x] 1.3 简化 `NoteEntity`：加 `title` 字段，去掉 `categoryId` 及外键；删除 `NoteCategoryEntity` 及 `note_categories` 表
- [x] 1.4 `PersonEntity` 加 `sortOrder` 字段（Int，新联系人取 MIN(sortOrder)-1 排最前）
- [x] 1.5 `TodoEntity` 清除 `sourceType`、`sourceRefId`、`sourceCycleKey` 字段
- [x] 1.6 更新 `ExportModels`：金额字段去 InCents 后缀，新增 `ExportExpenseCategory`，`ExportSnapshotExpense` 用 categoryId 替代 categoryName，`ExportNote` 加 title 去 categoryId，`ExportTodo` 去 source 字段，去 `ExportNoteCategory`
- [x] 1.7 更新 `LedgerModels`（域模型）：所有 *InCents 改名，`ExpenseSummary` 改为用 categoryId+categoryName，`TodoSummary` 去掉 sourceType，`PersonLedgerSummary` 不变
- [x] 1.8 更新 `Relations.kt`/`AppDatabase`：数据库 entities 列表加 ExpenseCategoryEntity，去 NoteCategoryEntity

## 2. DAO + Repository 层

- [x] 2.1 花销分类 DAO：observeActiveCategories（archived=false）、getAllCategories、insertCategory、updateCategoryName、archiveCategory、unarchiveByName、countActiveCategories、countExpensesByCategory
- [x] 2.2 笔记 DAO 简化：去掉所有 noteCategory 相关方法（7个），保留并简化笔记 CRUD，加 deleteNote(id)
- [x] 2.3 新增 DAO 方法：deleteSnapshot(id)、deleteTodo(id)、deletePerson(id)
- [x] 2.4 联系人编辑：Repository.updatePerson（带 id 的 upsert）、Repository.reorderPeople（批量更新 sortOrder）
- [x] 2.5 Repository 花销分类：saveExpenseCategory、renameExpenseCategory、archiveExpenseCategory（检查关联记录数）、保存快照时自动写入新分类
- [x] 2.6 Repository 删除方法：deleteSnapshot、deleteTodo、deletePerson
- [x] 2.7 Repository 笔记 CRUD：saveNote、updateNote、deleteNote
- [x] 2.8 移除生日 Todo 生成：删除 generateBirthdayTodos、shouldGenerateBirthdayTodo、hasGeneratedTodo、getPeopleByBirthday；ReminderWorker 去掉调用；Channel 描述改为"Todo 提醒"
- [x] 2.9 Repository 导入/导出更新：导出加 expenseCategories，去 noteCategories，snapshot expenses 用 categoryId，notes 加 title 去 categoryId，todos 去 source 字段

## 3. ViewModel + DataStore 层

- [x] 3.1 新增 LedgerTab.Notes，Tab 枚举顺序改为 Notes/Todos/Settings/Assets/Gifts
- [x] 3.2 LedgerUiState 加 expenseCategories 列表、notes 列表
- [x] 3.3 ViewModel 花销分类方法：saveExpenseCategory、renameExpenseCategory、archiveExpenseCategory
- [x] 3.4 ViewModel 删除方法：deleteSnapshot、deleteTodo、deletePerson
- [x] 3.5 ViewModel 联系人编辑和排序：updatePerson、reorderPeople
- [x] 3.6 ViewModel 笔记方法：saveNote、updateNote、deleteNote
- [x] 3.7 DataStore 主题偏好：themeMode（system/light/dark），暴露为 StateFlow
- [x] 3.8 DataStore 金额单位偏好：unit_assets、unit_gifts，暴露为 StateFlow
- [x] 3.9 ViewModel saveSnapshot 更新：接收 categoryId 列表而非 categoryName，保存时检查临时新增分类并写入主表

## 4. UI 层

- [x] 4.1 Tab 重构：5 个 Tab（笔记/Todo/设置/资产/人情），每个 Tab 用特征 icon，未选中只显示 icon、选中显示 icon+文字，设置 Tab 略突出特殊样式
- [x] 4.2 资产 Tab 花销分类管理区：SectionCard 列出活跃分类，点击编辑名称，滑动/长按归档，新增分类按钮，10 条上限提示
- [x] 4.3 资产 Tab 快照卡片：展开按钮改为 ExpandMore/ExpandLess 图标，新增删除按钮+二次确认对话框
- [x] 4.4 资产 Tab AddSnapshotDialog 改造：花销分类从主表列出（archived=false），逐行填金额，支持临时新增分类（同名恢复），金额输入框改为整数+单位选择器
- [x] 4.5 折线图：引入 Vico 依赖，资产 Tab 新增「资产趋势」SectionCard，总资产+单账户切换，1 个数据点也正常显示，Y 轴智能单位
- [x] 4.6 人情 Tab 联系人交互：点击联系人名字展开/收起礼物记录，长按弹出菜单（编辑/删除），删除二次确认提示礼物记录丢失
- [x] 4.7 人情 Tab 礼物展示：送出/收到不同浅色背景，按赠送时间倒序
- [x] 4.8 人情 Tab 联系人排序：LazyColumn + 拖拽排序库，持久化 sortOrder
- [x] 4.9 人情 Tab 生日视觉提示：生日前一个月联系人名字颜色轻微变化（暖色），subtitle 改为"生日临近时联系人会有颜色提示"
- [x] 4.10 笔记 Tab：列表默认只显示标题+创建/更新时间，点击展开正文，展开后显示编辑/删除按钮，新增笔记按钮，编辑用 Dialog（标题+正文），删除二次确认
- [x] 4.11 Todo Tab：每条 Todo 卡片加删除按钮，二次确认对话框
- [x] 4.12 设置 Tab：新增「外观」SectionCard，三挡切换（跟随系统/浅色/深色）
- [x] 4.13 所有金额输入框改造：整数输入 + 单位 Dropdown（元/千/万），默认千，各 Tab 独立记忆上次单位
- [x] 4.14 金额展示智能格式化：整除万→X万，整除千→X千，否则→X,XXX元

## 5. 主题系统

- [x] 5.1 OfflineLedgerTheme 接入 darkTheme 参数，根据参数选择 LightColors/DarkColors
- [x] 5.2 MainActivity 读取 DataStore 主题偏好，计算 darkTheme 值传入 OfflineLedgerTheme
