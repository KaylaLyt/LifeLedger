## 上下文

`enhance-offline-ledger` 铺完基础账本功能并上线使用后，四个缺口浮现出来：联系人生日强制必填、账户一旦建错只能删库重建、资产快照删除入口不易察觉、应用密码在 `passwordSet=false` 态下无法通过现有 UI 重新设置（从而在导入无 lock 表的 JSON 后形成死锁）。它们共享同一个特点：均为核心流程的"卡点"，单独拎出来做变更过于琐碎，打包成一个统一的"打磨变更" (`polish-core-flows`) 更合适。

数据库此前沿用 `fallbackToDestructiveMigration()`、不维护历史迁移脚本（见 `AppDatabase.kt`），本变更延续这一策略。导出 / 导入 JSON 的兼容策略也延续"新字段缺省"——旧文件缺失字段按 default 值反序列化。

## 目标 / 非目标

**目标：**
- 联系人生日成为可选字段，不打扰只记关系 / 备注的用户。
- 账户具备"编辑 / 软删 / 同名解档"完整 CRUD，在不破坏历史快照的前提下让账户信息保持正确。
- 资产快照删除维持真删（CASCADE 清关联余额与花销），行为与用户一次性主动删除操作预期一致。
- 应用密码重做为"可选"模型：无密码可直接进入 App；有密码时支持"修改 + 关闭"；销毁后自动恢复到无密码的初始态，保证 App 始终可用。
- 不引入任何额外的一次性校验 / 迁移流程；用户无需手动"修复"状态。

**非目标：**
- 不做账户的排序、多币种或多子账户结构。
- 不引入生物识别（指纹 / FaceID）解锁；本次仍然只维护本地密码哈希。
- 不变更 `fallbackToDestructiveMigration()` 策略（接受破坏性迁移，不写 Migration 脚本）。
- 不调整 `app-visual-shell` 的主题色板、底部导航样式等视觉规范。
- 不改变 DataStore 偏好的加密 / 签名策略（仍用默认明文 DataStore）。

## 决策

### 1. 生日字段用 `Int?` 而非空字符串 / 零值约定

- 选择原因：语义最清晰，`null` 即"未填"，不会和月 / 日 = 0 这种无效日期混淆。Room 对可空 `Int` 原生支持；`kotlinx.serialization` 默认把 null 序列化为 JSON `null`；UI 层从 `Int?` 转 TextField 的空字符串也只是一行 `?.toString().orEmpty()`。
- 备选方案：
  - 用 `Int = 0` 配合"0 代表未填"的约定 → 容易把真实的 0 日期与"未填"混淆，且 Room 没法用 `IS NULL` 判断。
  - 用单字段 `birthday: String = ""` 存 `"MM-DD"` → 改动面更大，要写解析函数并失去类型安全。
- 不选原因：约定式空值在跨场景（导出 / 测试 / 日志）里极易踩坑；字符串方案得不偿失。

### 2. 卡片行显示策略：无生日时整行隐藏

- 选择原因：用户明确要求"展示的时候直接显示空的"。整行隐藏让视觉最安静，也避免出现"生日：" 后面跟空白的尴尬排版。
- 备选方案：
  - 显示"生日：未填" → 对只记关系的联系人反而是视觉负担。
  - 显示"生日：--" → 占位但信息量为 0。
- 不选原因：与用户原话"直接显示空的"最贴近的即整行隐藏。

### 3. 账户软删字段命名为 `archived`，与花销分类对齐

- 选择原因：与 `ExpenseCategoryEntity.archived` 完全同名，内部同名归档 / 解档的规则也可复用心智模型（同名再添加 = 解档）。
- 备选方案：
  - 字段名 `deleted: Boolean` / `isDeleted` → 语义过强，用户其实没"删"，只是"隐藏"。
  - 新增 `accountStatus: enum { ACTIVE, ARCHIVED }` → 过度设计，只有两态。
- 不选原因：与现有分类字段不一致会让代码阅读者疑惑"为什么账户和分类命名不同"。

### 4. 归档账户的可见性规则

```
资产 Tab 账户列表         仅 archived=false
新增快照的余额输入表格    仅 archived=false
新增快照的花销（无关）     仍然按花销分类处理
历史快照内部展开的余额    全量 join（允许展示归档账户名）
图表 AssetTrendChart      仅 archived=false 的账户作为折线源
账户详情弹窗              archived=true 的账户在历史快照中不可点击（无入口打开详情）
```

- 选择原因：满足用户"归档的账户不再出现在列表"诉求，同时"历史快照不丢数据"。图表不显示归档账户线是出于"关注当前活跃资产"的理由。
- 备选方案：历史快照也把归档账户的条目隐藏 → 会让过去时点的总资产看起来缺一块，与快照本身"时点全量"语义冲突。

### 5. 同名账户添加时自动提示解档（与分类一致）

- 规则：`saveAccount(name, ...)` 时先查 `archived=true AND name=<name>` 是否存在。
  - 若存在 → 弹出确认对话框 `"已存在同名归档账户，是否恢复？"` → 确认则 `unarchiveAccount(id)` + 用新输入覆盖其它字段；取消则不保存。
  - 若不存在 → 正常 insert。
- 选择原因：与 `enhance-offline-ledger` 里花销分类的同名解档规则一致，避免双模型。
- 备选方案：
  - 无交互直接复活（静默 unarchive）→ 用户可能不知道账户里藏着一条归档，静默覆盖字段有风险。
  - 阻止同名添加 → 强迫用户到归档列表里找，入口繁琐，违背"不复杂化操作"原则。

### 6. 资产快照保持真删 + CASCADE（不引入软删）

- 选择原因：快照本身是"时点数据"，删除一条等同于用户主动撤回一次记录；历史上已有二次确认；与"账户可恢复"不同，快照不需要复活能力。
- 备选方案：快照也引入 archived → 把软删概念扩散到时序对象上，语义奇怪（"归档某一天的资产"）。
- UI 侧不做大改，仅把右上角的删除 IconButton 尺寸从 `20dp` 对齐到其他 IconButton 默认 24dp 视觉更协调——这条作为次要 UX 改进包含在任务里，不单列需求。

### 7. 密码模型：`passwordSet` 是 App 解锁流程的唯一开关

新状态机：

```
App 启动
  │
  ▼
 ┌────────────────────────┐
 │ 读取 lock.passwordHash │
 └────────┬───────────────┘
          │
  passwordSet = passwordHash != null && isNotBlank
          │
          ▼
 ┌────────────────────────┐
 │  passwordSet == false  │──────► 直接渲染主界面（unlocked=true）
 └────────┬───────────────┘
          │
          ▼
 ┌────────────────────────┐
 │  passwordSet == true   │──────► 渲染 LockScreen
 └────────┬───────────────┘
          │ 正确      │ 错
          ▼           ▼
      进入主界面     failedAttempts++
          ▲              │
          │              ▼
          │      attempts >= 5 ?
          │        │        │
          │       否        是
          │        │        │
          │        ▼        ▼
          │    停留 LockScreen   销毁路径
          │                       │
          │    ┌──────────────────┘
          │    ▼
          │  数据销毁（见决策 10）
          │    │
          │    ▼
          │  passwordSet → false（因为 lock 表也被清空）
          │    │
          └────┘
```

- `ViewModel` 的 `unlockedState: MutableStateFlow<Boolean>`：当 `passwordSet=false` 时，它的值由业务层**主动设为 true**（可以理解为"没密码就不锁"）；当 `passwordSet=true` 时，由"解锁成功 / 销毁"切换。
- `LedgerApp` 根 Composable：`if (state.passwordSet && !state.unlocked) LockScreen() else AppContent()`。

### 8. 设置页的密码按钮按 `passwordSet` 切换（B2 方案）

```
passwordSet == false：
  ┌───────────────────────┐
  │ [启用应用密码]         │
  └───────────────────────┘

passwordSet == true：
  ┌───────────────────────┐
  │ [修改密码]            │
  │ [关闭密码]            │
  └───────────────────────┘
```

- 选择原因：相较于"单按钮智能切换对话框内容"，两按钮形态让每个对话框责任单一（启用 / 修改 / 关闭），校验规则也清楚：
  - 启用：`newPassword.length >= 4 && newPassword == confirm`，无需当前密码。
  - 修改：沿用现状。
  - 关闭：`verifyPassword(current)` → ok 则 `dao.upsertAppLockSettings(AppLockSettingsEntity(passwordHash = "", failedAttempts = 0))`。
- 备选方案：单按钮 `[管理密码]` 打开万能对话框 → 对话框内部分支逻辑复杂，反而违背"不复杂化操作"。

### 9. 关闭密码不加二次确认

- 选择原因：关闭密码这一动作需要先输入当前密码，输密码本身就是确认。再追加"你确定吗"弹窗反而繁琐。
- 备选方案：弹轻量确认 → 两次确认在"用户已经敲进 6 位密码"这个成本上是过度保护。
- 对比：归档账户前 **加** 一次轻量确认（只需一次点击就能触发的动作，二次确认合理）；销毁则不在 UI 上走，完全由错 5 次自动触发。

### 10. 销毁路径：Room 全表清空 + DataStore `clear()`

- 销毁流程：
  1. `database.clearAllTables()` —— 清空全部 Room 表（已存在）。
  2. `UserPreferences.clear()` —— 新增；清空主题模式、各 Tab 输入单位偏好。
  3. `unlockedState.value = true` —— 因为没密码了，自动视为解锁（下次启动时 `passwordSet=false` 自动走决策 7 的"直接进入"）。
  4. 向用户发送一条 snackbar："数据已销毁，App 已重置"。
- 选择原因：用户说"密码和所有设置都清空" → 销毁后 App 等同首次启动，一切偏好归零。DataStore 保留会让下次启动"数据空但主题还停留在深色"这种割裂状态。
- 备选方案：只清 Room 不清 DataStore → 与用户原意不符；且"单位偏好"若指向被清除的账户 / 分类会是无意义残留。

### 11. 导入 JSON 时的锁状态处理

- 规则：
  - 导入 JSON 的 `lock` 块若 `passwordHash` 为 null / 空字符串 → `passwordSet=false`；按决策 7，应用在导入成功后**保持已解锁**。
  - 导入 JSON 的 `lock` 块若有 `passwordHash` → 导入后的 `passwordSet=true`；是否继续保持 `unlockedState=true` 需要讨论：
    - **决策**：保持 `unlockedState=true`（因为用户已经在操作当前会话）；下一次 App 冷启动时回归正常 LockScreen 流程。
- 选择原因：避免用户在"导入中途"被弹回 LockScreen 的体验断层；冷启动时再走正常流程即可。

### 12. 测试策略

- 纯逻辑测试 (`LedgerLogicTest`)：覆盖 `isBirthdayApproaching(null, null) == false`。
- Repository 级测试（新增或扩展）：
  - 启用 / 关闭密码：状态过渡与 `passwordHash` / `failedAttempts` 字段。
  - `saveAccount` 同名归档账户 → 返回 "需要确认解档" 的信号（Repository 层返回带 discriminator 的结果类型，UI 层再弹窗）。
  - `archiveAccount` / `unarchiveAccount` 过滤行为。
  - 销毁路径：`wipeInternalData()` 后 DataStore 也被清空，再次观测时回到默认值。
- UI 层本次不新增 Compose 测试，以现有人工走查为准。

## 风险 / 权衡

- **风险：Room 版本递增 + 破坏性迁移会清空现有数据** → 与一贯策略一致；项目尚未发布到用户量大的场景，风险可接受。若未来有实际用户数据需要保护，再单独出 Migration 变更。
- **风险：归档账户在历史快照里仍显示账户名，可能让用户以为"账户没真删"** → UI 上在历史快照的归档账户名旁加 `(已归档)` 轻标注（此为次要改进，在任务中实现）。
- **风险：销毁时连 DataStore 一起清，用户原先的主题选择被重置为 "跟随系统"** → 这是"销毁"语义本身的应有之义，文档中向用户说明即可。
- **风险：`saveAccount` 同名解档需要 UI 层弹窗，Repository 层不能一次完成写入** → 采用"两步走"API：`checkAccountNameAvailability(name)` 返回 `Available | ConflictWithActive | ConflictWithArchived(id)`；UI 层根据结果决定是直接保存、阻止、还是弹"是否恢复"对话框。
- **权衡：关闭密码不二次确认** → 换取"不复杂化操作"；用户如果误关，也可以立即再启用（数据未受影响）。
- **权衡：统一变更 (`polish-core-flows`) 打包四件事** → 任务量中等 (~15-20 个任务)，实施期间需要分阶段执行；好处是可以一次性跑完"验证 + 归档"。

## Migration Plan

1. **数据层先行**：修改 `PersonEntity` / `AccountEntity`、递增 `AppDatabase.version`、保持 `fallbackToDestructiveMigration()`；更新 DAO 接口（`updateAccount` / `archiveAccount` / `unarchiveAccount` / `disablePassword` / `checkAccountNameAvailability` 相关查询）。
2. **导出 / 导入模型**：扩展 `ExportModels` 的 `Person` / `Account` 字段；`LedgerRepository.importJson` 对缺失字段兜底（`archived=false`、`birthdayMonth=null` 等）。
3. **Repository 与 ViewModel**：实现决策 5 / 7 / 10 / 11 的业务分支；新增 `UserPreferences.clear()`。
4. **UI 调整**：
   - 生日输入 / 展示路径的 nullable 改造。
   - `AssetsScreen` 账户详情弹窗 + `EditAccountDialog` + 归档确认 + 恢复归档确认 + 同名解档流程。
   - `SettingsScreen` 按钮重组 + `EnablePasswordDialog` / `DisablePasswordDialog`。
   - `LedgerApp` 根 Composable 改走决策 7 的开关逻辑。
5. **测试**：补齐决策 12 列出的覆盖点。
6. **人工走查**：
   - 无生日联系人（新增 / 编辑 / 列表展示）。
   - 账户编辑 / 归档 / 归档后历史快照显示 / 同名解档。
   - 无密码启动 → 设置密码 → 关闭密码 → 再次启动。
   - 错 5 次 → 销毁 → 重启 → 无阻碍进入 App。
   - 导入无 lock 表 JSON → 设置页"启用应用密码" → 重启验证 LockScreen 出现。

## Open Questions

- 归档账户在历史快照中的 `(已归档)` 轻标注是用**颜色**（灰字）还是**后缀文字**？此为纯视觉，可在实施时就近决定；不阻塞提案。
- 销毁后要不要弹一次带"已销毁"文案的 AlertDialog（而非 snackbar）让用户更清楚？当前决定走 snackbar（更轻），如果实测不够醒目再升级为 AlertDialog。
