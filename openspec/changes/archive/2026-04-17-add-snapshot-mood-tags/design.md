## 上下文

- 资产快照当前是"写入即冻结"的数据：一旦保存就只能删除、无法编辑。这保证了金额类数据的可追溯性，但也意味着任何"事后回顾"信息（心情、关键词、反思）一次没写好就永远没机会补。
- 现有 `SnapshotEntity.note` 字段已落库，但 `SnapshotCard` 未渲染 → 等同于"假字段"。`AddSnapshotDialog` 中的备注输入框用户也感受不到反馈。
- `AccountEntity` / `ExpenseCategoryEntity` 已经建立了"独立表 + `archived: Boolean` 软删 + 同名冲突检测"的可复用范式；标签系统直接复刻这套语义。
- 前轮变更 `polish-core-flows` 刚把数据库 version 提升到 4，本次再 +1 到 5，仍使用 `fallbackToDestructiveMigration()`（产品当前阶段允许破坏性迁移）。
- 导出/导入契约 `schemaVersion` 目前为 3，本次升到 4：旧文件仍能读入（新字段按默认值处理），新文件不保证能被旧版本读入。

## 目标 / 非目标

**目标：**

1. 快照支持三项**事后可编辑**的批注：`mood`（情绪 1..5）、`tags`（多对多）、`note`（已有字段，改为可后期修改 + 显示出来）。
2. 情绪使用 **Emoji 五档**（😢 😕 😐 🙂 🤩）作为 UI 皮肤，底层存 `Int?`，保留未来做"情绪曲线"等数值化分析的可能。
3. 标签使用**独立 `tags` 表 + `snapshot_tags` 关联表**，参考 `expense_categories` 的软删/唯一/改名级联模式。
4. `SnapshotCard` 渲染 mood + tags + note；提供"编辑批注"入口。
5. 快照列表支持**按标签多选筛选**（OR 语义；空选=不过滤）。
6. 新增**标签管理**子入口（在 `AssetsScreen`），提供重命名、归档（不硬删）。
7. 导入/导出字段扩展到 `tags` / `snapshot_tags` / `snapshot.mood`，`schemaVersion` 4。

**非目标：**

- ❌ 允许修改快照的余额 / 目标 / 负债 / 花销（保持"一次写入"原则）。
- ❌ 情绪/标签的**派生可视化**（心情 × 资产曲线、标签聚合统计）。数据结构留好，视图放下期。
- ❌ 情绪二维化（心情×动荡）、标签颜色自定义。
- ❌ 跨快照的标签批量操作（批量打标）。
- ❌ 历史数据库迁移脚本——延续 `fallbackToDestructiveMigration`，现有数据在 Debug 环境下会被清空，用户需手动通过 JSON 导出备份。

## 决策

### 决策 1 — 快照"批注"概念单独建模，不破坏"不可编辑"原则

**选择**：在 `SnapshotEntity` 上就地添加 `mood: Int?`，`note` 字段保留不动；`tags` 通过关联表挂载。然后在 Repository 层提供**专用方法** `updateSnapshotAnnotation(snapshotId, mood, note, tagIds)`，该方法只允许写这三类字段。

**替代方案**：

- A. 把整个快照变"可编辑" → 破坏数据可信度，被 `polish-core-flows` 明确拒绝过。
- B. 新建 `SnapshotAnnotationEntity`（1:1 挂在快照下） → 过度建模，mood/note 本来就是快照的一部分属性。
- C. 把 mood/note 也视为"保存时写入"（不可改） → 和"情绪/心得是事后回忆"的天性冲突。

**理由**：A 风险过大、C 体验差；B 带来额外表但不解决问题。走最轻量的就地字段 + 受控方法。

### 决策 2 — 情绪用 `Int?` 存储，UI 层映射 Emoji

**存储**：`SnapshotEntity.mood: Int?`，合法值 1..5，null 表示未填。

**UI 映射**（在 `LedgerLogic` 提供纯函数 `moodEmoji(mood: Int?)`）：

```
  1 → 😢 （糟糕）
  2 → 😕 （一般）
  3 → 😐 （平稳）
  4 → 🙂 （顺利）
  5 → 🤩 （兴奋）
  null → 无 / "-"
```

**替代方案**：

- A. 直接存 emoji 字符串 → 数值分析/筛选/校验成本高，字符串长度、字符版本漂移风险。
- B. 存枚举名称字符串（如 `"GREAT"`） → 比 Int 稍可读但同样失去"数值"优势。

**理由**：1..5 保留数字属性；后续若要做心情曲线不用改 schema。Emoji 只是视觉皮。

### 决策 3 — 标签使用独立表 + 软删 + 唯一名称（复刻分类表模式）

**表结构**：

```sql
tags(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,            -- UNIQUE(name COLLATE NOCASE) via Index
  archived INTEGER NOT NULL DEFAULT 0,
  sortOrder INTEGER NOT NULL DEFAULT 0
)
snapshot_tags(
  snapshotId INTEGER NOT NULL,
  tagId INTEGER NOT NULL,
  PRIMARY KEY(snapshotId, tagId),
  FOREIGN KEY(snapshotId) REFERENCES snapshots(id) ON DELETE CASCADE,
  FOREIGN KEY(tagId)      REFERENCES tags(id)      ON DELETE RESTRICT
)
```

- `ON DELETE CASCADE` 对 snapshotId：快照硬删时连带清掉其标签关联（与已有 `snapshot_balances` / `snapshot_expenses` 一致）。
- `ON DELETE RESTRICT` 对 tagId：不允许硬删仍被使用的标签（参考 `expense_categories` 对 `snapshot_expenses` 的 RESTRICT）。历史快照上仍挂该标签则只能**归档**（`archived=1`）而非删除。

**重用策略**：

- 如果用户新建了同名已归档标签，自动解档（`archived=0`）并复用 id。这与账户 / 分类的"同名恢复"流程一致。
- 大小写不敏感唯一（`LOWER(name)`）。

**替代方案**：

- A. `snapshots.tags` 列里直接存 `"tag1,tag2"` 字符串 → 改名、统计、筛选都要全表扫描。
- B. 不支持归档，只允许硬删 → 历史快照会失标签，数据割裂。

### 决策 4 — 批注编辑入口 vs 完整快照编辑

**选择**：在 `SnapshotCard` 右侧（展开/删除按钮旁）增加一个 ✏️ **"编辑批注"** 图标按钮；点击打开 `EditSnapshotAnnotationDialog`，仅有三项：情绪五档 + 标签多选/创建 + 心得文本。

UI 不提供"编辑快照金额"的任何入口，以最大程度避免混淆"金额不可改"这条规则。

### 决策 5 — 标签输入体验：Chip + 下拉建议 + 新建

**AddSnapshotDialog** 与 **EditSnapshotAnnotationDialog** 共用同一个 `TagInputField` 组件：

```
  [#年终奖]  [#理财通涨了]  [×]  ┌───────────────────────┐
                                  │ 输入或选择标签...     │
                                  │ ╭─ 候选 ─────────╮    │
                                  │ │ 年终奖 ✓       │    │
                                  │ │ 买房           │    │
                                  │ │ 搬家           │    │
                                  │ │ 失业           │    │
                                  │ │ + 新建 "搬家2" │    │
                                  │ ╰────────────────╯    │
                                  └───────────────────────┘
```

- 已选标签显示为 `FilterChip` 集合，× 可移除。
- 输入框带历史候选下拉（过滤活跃 + 匹配前缀或子串）。
- "+ 新建 'X'" 显示在没有完全匹配时；确认后立即落 `tags` 表并加入已选集合。
- 批注保存时统一调 `updateSnapshotAnnotation`，该方法内部 diff 新旧集合、增删关联行。

### 决策 6 — 筛选条：标签多选（OR 语义）+ 其他维度保留扩展位

**UI**：

```
  ┌─ 资产快照 ─────────────────────────────────────────┐
  │ [全部标签 ▾]  [情绪 ▾]  [重置]                      │
  │                                                    │
  │ 选中标签：[#年终奖] [#买房]  (OR 匹配)              │
  └────────────────────────────────────────────────────┘

  列表：命中任一选中标签的快照显示，其他按时间倒序保留。
  情绪筛选先不做 UI，仅保留预留位；首版只做标签筛选。
```

**SQL 语义**：

```sql
SELECT s.* FROM snapshots s
WHERE EXISTS (
  SELECT 1 FROM snapshot_tags st
  WHERE st.snapshotId = s.id AND st.tagId IN (:selectedTagIds)
)
ORDER BY s.snapshotDate DESC
```

- 空 `selectedTagIds` → 不过滤。
- 使用 EXISTS 而非 JOIN 防重复行。
- 筛选状态保存在 `LedgerViewModel`（不持久化；跨冷启动重置）。

### 决策 7 — 导入/导出 schemaVersion 跃迁到 4

```jsonc
{
  "schemaVersion": 4,
  "tags": [
    { "id": 1, "name": "年终奖", "archived": false, "sortOrder": 0 }
  ],
  "snapshots": [
    {
      "id": 9,
      "snapshotDate": 1604361600000,
      "mood": 4,
      "note": "奖金到账，转一部分到定期。",
      ...
    }
  ],
  "snapshotTags": [
    { "snapshotId": 9, "tagId": 1 }
  ]
}
```

- 旧 `schemaVersion=3` 文件导入时：`mood` 默认 null，`tags`/`snapshotTags` 视为空数组；不报错。
- 新文件在旧版本 App 上会因 schemaVersion 检查而拒绝导入（既有机制）。

### 决策 8 — 标签管理放在 `AssetsScreen`，与账户/分类并列

保持"一切与快照相关的元数据都在资产 Tab 管"的一致性（账户、花销分类、现在再加标签）。管理操作：

- 重命名（与分类/账户同逻辑；级联生效于所有历史快照，因为引用是 id）。
- 归档/解档（软删）。
- 被历史引用时硬删被禁止，只能归档。
- 添加上限：**不设硬上限**（标签天生比分类高频、语义发散），但 UI 默认仅展示最近活跃前 N 个，超出折叠。

### 决策 9 — `note` 字段不变，只是"解锁"它

- 不改字段类型 / 长度（已是 `String`）。
- `SnapshotCard` 以折叠行渲染（`note.isNotBlank()` 时才显示"心得"行）。
- `EditSnapshotAnnotationDialog` 允许清空（改为 `""`）。
- 导出导入中 `note` 早已存在，零改动。

## 风险 / 权衡

- **风险**：既有 polish-core-flows 的"快照不可编辑"规则被破坏 → **缓解**：仅豁免 `mood`/`note`/`tags` 三项；Repository 专用方法名（`updateSnapshotAnnotation`）和 UI 标题（"编辑批注"）与"编辑快照"做语言上的区分；具体规范项在 spec 中加一条明确的 SHALL NOT。
- **风险**：标签随时间爆炸、输入下拉变慢 → **缓解**：下拉时只查询 `archived = 0` 的活跃标签；过滤在内存里按前缀/子串做；超过 200 条时只展示前 30 + 搜索匹配。
- **风险**：用户把"情绪/心得"误认为可以改账户金额 → **缓解**：编辑对话框顶部明示"仅可修改情绪 / 标签 / 心得"。
- **权衡**：不做心情曲线/标签聚合 → 让首版可落地；数值化 mood 保证后续零迁移能接分析视图。
- **风险**：数据库 destructive migration 清空数据 → **缓解**：前置提示用户导出 JSON 备份；JSON 导入支持携带新字段，回迁路径完整。
- **风险**：同名标签大小写冲突（"年终奖" vs "年终奖 "带空格） → **缓解**：保存前统一 `trim()` + 大小写不敏感比对；空字符串被拒绝。

## 迁移计划

1. DB version `4 → 5`，`fallbackToDestructiveMigration()` 继续启用。
2. 用户升级后首次启动：老 DB 直接清空。发布前在 `CHANGELOG` 明确警告；UI 内无新增迁移对话框。
3. JSON 导入：
   - 若文件 `schemaVersion >= 4` 且携带 `tags` / `snapshotTags` / `mood`，正常落库；对应标签会被创建/解档。
   - 若 `schemaVersion == 3`，忽略新字段，按旧路径导入，不报错。
   - 若 `schemaVersion > 4`，照搬现有"文件版本过高"错误处理。
4. 回滚：用户可通过旧版 App 的 JSON 导出文件（v3）恢复到旧版；新版 App 的 v4 文件在旧版上不可导入（既有约束）。

## 未决问题

- **是否允许标签按下拉顺序拖拽排序**？首版按 `name ASC` 排列即可，后续若有"置顶常用标签"诉求再加 `sortOrder` UI（`sortOrder` 字段已在表里预留）。
- **"情绪曲线"叠在资产折线图上**是否在下一期立即做？取决于用户数据积累速度，首版不动。
- **批注的"最后修改时间"**是否显示？暂不显示；如有需要再给 `SnapshotEntity` 加 `annotationUpdatedAt: Long?`。
