## 为什么

目前资产快照只能记录金额维度的数据（账户余额、目标、负债、花销），缺少"当时的上下文与心情"。用户多年手写的纸面记录（例如 `【2020.11.3】 总xx k` + 账户流水）同样完全没有情境信息，回头复盘时只能看到一串冷数字，难以回忆"那时候发生了什么、我感觉如何"。

另外，快照的 `note` 字段虽已落库，但 `SnapshotCard` 从未渲染，等于写了也看不到。

现在我们希望让快照长出"叙事"一层：情绪 + 标签 + 心得，并在卡片上可视化，支持按标签筛选历史。这样快照从"账单截面"升级为"带有背景的财务日记"。

## 变更内容

- 新增**快照情绪**字段：`mood`（1..5 整数，可空；UI 上用 😢 😕 😐 🙂 🤩 映射）。
- 新增**标签系统**：独立 `tags` 表（软删/唯一性参考现有 `expense_categories`）+ `snapshot_tags` 多对多关联表。自由输入 + 历史标签下拉提示。
- **快照批注可编辑**：对既有"快照不可编辑"的规则开一个精准豁免——仅允许修改 `mood` / 关联 `tags` / `note`，账户余额与花销仍然一次写入不可改。
- `SnapshotCard` 显示 `mood` 图标、`tags` 胶囊、`note` 文本；提供"编辑批注"入口打开批注编辑对话框。
- `AddSnapshotDialog` 增加情绪选择与标签输入；新建标签会落入 `tags` 表。
- **快照列表按标签筛选**：筛选条（多选标签 OR 语义；可再叠加情绪区间）。
- `AssetsScreen` 新增"标签管理"子入口（重命名、归档），与账户 / 花销分类管理风格一致。
- **导出/导入**：`ExportBundle` 增加 `tags`、`snapshotTags`、并在 `ExportSnapshot` 上增加 `mood`；`schemaVersion` 升到 4。
- 数据库版本递增，延续 `fallbackToDestructiveMigration()` 策略（无历史迁移）。

## 功能 (Capabilities)

### 新增功能
（无）

### 修改功能
- `ledger-core`: 扩展快照数据模型（mood、note 渲染、标签关联与筛选）、新增独立的标签管理需求、在既有"快照不可编辑"原则上精确豁免批注字段，并同步更新导入导出契约。

## 影响

- **代码**：
  - `data/entity/Entities.kt`（`SnapshotEntity.mood`、新 `TagEntity` / `SnapshotTagCrossRef`）
  - `data/dao/LedgerDao.kt`（tags CRUD、snapshot 批注更新、按标签查询）
  - `data/AppDatabase.kt`（版本 +1、destructive migration）
  - `data/model/ExportModels.kt` + `LedgerRepository`（schemaVersion=4，导入导出字段）
  - `domain/LedgerModels.kt` + `domain/LedgerLogic.kt`（`SnapshotSummary` 扩展、mood 映射工具）
  - `ui/LedgerViewModel.kt`（批注编辑、标签筛选状态、标签 CRUD 方法）
  - `ui/LedgerApp.kt`（`SnapshotCard` 渲染、批注编辑对话框、`AddSnapshotDialog` 扩展、筛选栏、标签管理 UI）
- **数据**：新增 `tags`、`snapshot_tags` 两张表；`snapshots` 增加 `mood` 列。
- **导出文件**：老版本 JSON 在导入时将 `mood` 视为 null，`tags/snapshotTags` 视为空；新导出文件 `schemaVersion=4`。
- **依赖**：无新三方依赖。
- **无破坏性 API 改动**（App 内部私有调用），但 JSON `schemaVersion` 往前一步。
