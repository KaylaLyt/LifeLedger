## 1. 实体 / DAO / DB 版本

- [x] 1.1 在 `data/entity/Entities.kt` 的 `SnapshotEntity` 新增 `val mood: Int? = null`
- [x] 1.2 新增 `TagEntity(id, name, archived=false, sortOrder=0)`，加 `Index(value=["name"], unique=true)`
- [x] 1.3 新增 `SnapshotTagCrossRef(snapshotId, tagId)`，复合主键；`ForeignKey(snapshots, onDelete=CASCADE)` 与 `ForeignKey(tags, onDelete=RESTRICT)`
- [x] 1.4 在 `AppDatabase` 的 `entities` 列表注册 `TagEntity`、`SnapshotTagCrossRef`；`version` 由 4 递增到 5；保留 `fallbackToDestructiveMigration()`
- [x] 1.5 在 `LedgerDao`：新增 `observeActiveTags()` / `observeAllTags()` / `getTagByNameIgnoreCase(name)` / `insertTag` / `updateTag` / `setTagArchived(id, archived)` / `deleteTagById(id)` + 引用计数 `countSnapshotTagsByTag`
- [x] 1.6 在 `LedgerDao`：新增 `observeTagsForSnapshot(snapshotId)`、`insertSnapshotTagRefs(refs)`、`deleteSnapshotTagRefs(snapshotId, tagIds)`、`clearSnapshotTagRefs(snapshotId)`、`observeSnapshotTagJoin()`（供 VM 层组装 tagsBySnapshot）
- [x] 1.7 在 `LedgerDao`：新增 `updateSnapshotMoodAndNote(snapshotId, mood, note)` 专用 `@Query UPDATE`
- [x] 1.8 标签过滤改为在 VM 层按 `selectedTagFilterIds` 过滤 `SnapshotSummary`（Room 对空 `IN ()` 不友好，且 VM 已持有 tagsBySnapshot，内存过滤更简单、语义等价于 `EXISTS` OR 匹配）

## 2. 导入 / 导出模型

- [x] 2.1 在 `data/model/ExportModels.kt` 新增 `ExportTag(id, name, archived, sortOrder)` 与 `ExportSnapshotTag(snapshotId, tagId)`
- [x] 2.2 `ExportSnapshot` 新增 `val mood: Int? = null`
- [x] 2.3 `ExportBundle` 新增 `val tags: List<ExportTag> = emptyList()`、`val snapshotTags: List<ExportSnapshotTag> = emptyList()`
- [x] 2.4 `LedgerRepository.exportJson()`：`schemaVersion` 升到 `4`；写入 `mood`、`tags`、`snapshotTags`
- [x] 2.5 `LedgerRepository.importJson()`：兼容 v3（缺失字段按默认处理，kotlinx.serialization 默认值）；v4 时把标签/关联落库，归一化 mood ∉ 1..5 为 null
- [x] 2.6 `exportFileName()` 不包含 schemaVersion，无需更新

## 3. 领域模型 / 工具

- [x] 3.1 在 `domain/LedgerModels.kt` 的 `SnapshotSummary` 新增 `mood: Int?`、`tags: List<TagSummary>`（`TagSummary(id, name, archived)`）
- [x] 3.2 在 `domain/LedgerLogic.kt` 新增 `moodEmoji(mood: Int?): String?` 与 `moodLabel(mood: Int?): String?` + `normalizeMood`
- [x] 3.3 `buildSnapshotSummaries(...)` 新增 `tagsBySnapshot: Map<Long, List<TagEntity>>` 参数，组装到 `SnapshotSummary.tags`

## 4. Repository 批注与标签 CRUD

- [x] 4.1 `LedgerRepository`：新增 `allTags: Flow<List<TagEntity>>`、`activeTags: Flow<List<TagEntity>>`、`snapshotTagJoin`
- [x] 4.2 新增 `TagNameStatus` sealed class + `checkTagNameAvailability(name)`
- [x] 4.3 新增 `saveTag(name): Result<Long>`：trim + 大小写不敏感；已归档同名自动解档并复用 id
- [x] 4.4 新增 `renameTag(id, newName)`：校验唯一并保留原 id
- [x] 4.5 新增 `archiveTag(id)`、`unarchiveTag(id)`、`deleteTagIfUnused(id)` + `TagInUseException`
- [x] 4.6 新增 `updateSnapshotAnnotation(snapshotId, mood, note, tagIds)`：事务内 update + diff
- [x] 4.7 `saveSnapshot(...)` 扩展 `mood: Int? = null` 与 `tagIds: List<Long> = emptyList()`

## 5. ViewModel

- [x] 5.1 `LedgerUiState` 新增 `tags`、`allTags`、`selectedTagFilterIds`
- [x] 5.2 `LedgerBaseState` / `baseState` 合入 `activeTags`/`allTags`/`tagsBySnapshot`
- [x] 5.3 新增 `selectedTagFilterIds: MutableStateFlow<Set<Long>>` 并通过 `auxiliaryState` 纳入 `uiState` combine；VM 层按集合过滤 summaries
- [x] 5.4 新增方法 `toggleTagFilter(tagId)`、`clearTagFilter()`
- [x] 5.5 `saveSnapshot(...)` 扩展 `mood` 与 `tagIds` 参数
- [x] 5.6 新增 `editSnapshotAnnotation(snapshotId, mood, note, tagIds)`
- [x] 5.7 新增标签 CRUD 方法：`saveTag`、`renameTag`、`archiveTag`、`unarchiveTag`、`deleteTagIfUnused`
- [x] 5.8 `buildSnapshotSummaries` 调用处传入 `tagsBySnapshot`

## 6. UI — 新建快照对话框

- [x] 6.1 在 `AddSnapshotDialog` 增加情绪选择器（`MoodSelector`，5 个 Emoji FilterChip，可再次点击取消）
- [x] 6.2 增加 `TagInputField` 组件：已选标签以 `FilterChip` + 点击取消；下方 `AssistChip` 候选（仅活跃）；输入作为过滤器
- [x] 6.3 `onConfirm` 签名扩展为携带 `mood: Int?` 与 `tagIds: List<Long>`；ViewModel 调用链打通
- [x] 6.4 "备注" 文本框改名为"心得 / 备注"，卡片会渲染

## 7. UI — SnapshotCard 渲染 + 批注编辑

- [x] 7.1 在 `SnapshotCard` 标题行右侧增加 ✏️「编辑批注」IconButton
- [x] 7.2 在标题行显示 `moodEmoji(snapshot.mood)`（null 时不渲染）
- [x] 7.3 渲染 `snapshot.tags`（非空时一行只读 `FilterChip`）
- [x] 7.4 渲染 `snapshot.note`（非空时以独立段落显示，前缀"心得："）
- [x] 7.5 新增 `EditSnapshotAnnotationDialog`：仅含情绪选择器、`TagInputField`、心得 `OutlinedTextField`；顶部提示"仅可修改情绪 / 标签 / 心得"
- [x] 7.6 对话框"保存"调用 `viewModel.editSnapshotAnnotation(...)`

## 8. UI — 筛选栏 & 标签管理

- [x] 8.1 `AssetsScreen` 在历史快照之上新增「按标签筛选」区（标签胶囊 + 重置），仅有标签时显示
- [x] 8.2 选中标签以 `FilterChip(selected=true)` 显示；点击切换；显示 "OR 匹配" 说明
- [x] 8.3 新增「标签管理」入口 `TagManagerDialog`：列出活跃与归档标签，重命名 / 归档 / 恢复；已归档可删除
- [x] 8.4 硬删通过失败提示反馈"被 N 条快照引用，只能归档"（`TagInUseException.message`）

## 9. 测试

- [x] 9.1 为 `LedgerLogic.moodEmoji` 写单元测试：1..5 映射 + null + 越界（0/-1/6）全部归一化
- [x] 9.2 为 `buildSnapshotSummaries` 写测试：`tagsBySnapshot` 正确组装，空 map 时 `SnapshotSummary.tags` 为空
- [ ] 9.3 Repository/Dao 集成测试（`saveTag` / `updateSnapshotAnnotation` / 标签过滤 / 导入导出）——当前代码库无 Robolectric / AndroidX 测试基础设施，延后至下一个变更统一补齐；纯单测覆盖 `LedgerLogic` 侧语义
- [ ] 9.4 同 9.3（延后）
- [ ] 9.5 同 9.3（延后）
- [ ] 9.6 同 9.3（延后）

## 10. 验证与走查

- [x] 10.1 `openspec-cn validate add-snapshot-mood-tags --strict` 通过
- [ ] 10.2 手动走查：新建带情绪/标签/心得的快照，卡片正确显示三项（需真机运行）
- [ ] 10.3 手动走查：对已保存快照做批注编辑，金额/花销/目标/负债不变（需真机运行）
- [ ] 10.4 手动走查：标签多选筛选 OR 语义与「重置」工作正常（需真机运行）
- [ ] 10.5 手动走查：标签重命名级联、归档从候选隐去、历史快照仍显示（需真机运行）
- [ ] 10.6 手动走查：导出 v4 后重置数据，再导入文件——mood / tags / snapshotTags 全部还原（需真机运行）
