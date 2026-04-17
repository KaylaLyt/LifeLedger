## 为什么

日常使用中暴露出四个影响基础体验的缺口：

1. **联系人生日强制必填** 让"只想记关系 / 备注，没打算记生日"的联系人无法录入。
2. **账户信息不可编辑** —— 一旦拼错银行名称或号码，就只能删掉重建，历史快照又会连带丢失。
3. **资产快照删除入口隐蔽**，在"我想删一条错误快照"的场景下不容易被发现（功能其实已存在，需要确认行为 + 提升可见性）。
4. **应用密码流程存在无法自救的死锁**：导入无 `lock` 表的 JSON 后，`passwordSet=false` 但"修改密码"要求先验证旧密码，导致**永远无法在 App 内补设首密**；此外当前流程强制每位用户必须设置一个本地密码，体验偏重。

这四个问题都属于核心流程的"卡点打磨"，放在同一个统一变更里一次性修掉，命名为 `polish-core-flows`。

## 变更内容

- **联系人生日改为可选**：`PersonEntity.birthdayMonth` / `birthdayDay` 变为 `Int?`；UI 留空即保存为 null；卡片列表在无生日时**不展示**生日那一行；生日临近染色逻辑对无生日人员自动跳过。
- **账户 CRUD 完整化**：
  - 账户详情弹窗新增 `[编辑]` 按钮 → 打开表单对话框进行修改（与新增账户同表单）。
  - **新增 `archived: Boolean` 字段**，通过详情弹窗里的 `[归档]` 按钮进行软删（轻量二次确认"账户将不再出现在列表中，历史快照保留"）。
  - 归档账户**不出现**在资产 Tab 的账户列表 / 新增快照的余额输入表；但历史快照中通过 id join 仍能正常显示该账户的名称与余额。
  - 归档账户"复活" —— 用户新增同名账户时，若命中已归档记录，弹提示提供"恢复归档"操作（与花销分类一致）。
- **资产快照删除语义确认**：保持真删（`clearAllTables` 级 CASCADE 清理 `snapshot_balances` 与 `snapshot_expenses`），二次确认沿用现状。
- **BREAKING：重做应用密码流程**：
  - **密码可选**：`passwordSet=false` 时 App 启动后**不再展示 LockScreen**，直接进入主界面。
  - **启用密码**（`passwordSet=false` 态）：设置页出现 `[启用应用密码]` 按钮 → 弹出"新密码 + 确认"对话框，无需"当前密码"。
  - **修改密码**（`passwordSet=true` 态）：设置页出现 `[修改密码]` 按钮 → 沿用"当前密码 + 新密码 + 确认"流程。
  - **关闭密码**（`passwordSet=true` 态）：设置页额外出现 `[关闭密码]` 按钮 → 输入当前密码校验通过后清空 `passwordHash` 与 `failedAttempts`，不要求二次确认（输密码本身即确认）。
  - **销毁后自恢复**：错 5 次触发销毁时，除原有 `clearAllTables()` 外**额外清空 DataStore**（主题模式、各 Tab 的输入单位偏好），令 App 回到首次启动的全新状态；下次启动因 `passwordSet=false` 自动进入主界面，不再卡死。

## 功能 (Capabilities)

### 新增功能

- `ledger-core`: 账本核心数据管理规范，覆盖联系人（含可选生日）、账户（含编辑 / 软删 / 同名解档）、资产快照（含删除）等核心 CRUD 与业务规则。
- `app-lock`: 应用密码与锁屏能力规范，覆盖"密码可选"形态、启用 / 修改 / 关闭三种操作、失败计数与销毁自恢复机制。

### 修改功能

- 无（主规范库中 `app-visual-shell` 与本次变更不相关）

## 影响

- **数据层**：
  - `PersonEntity` 两个字段类型变更为 `Int?`（生日月 / 日）。
  - `AccountEntity` 新增 `archived: Boolean = false` 字段。
  - 因沿用 `fallbackToDestructiveMigration()`、无历史数据迁移包袱，直接递增数据库 version 并让 Room 执行破坏性迁移即可（与本项目一贯策略一致）。
- **导出 / 导入 JSON**：
  - `Person` 的 `birthdayMonth` / `birthdayDay` 支持 `null`。
  - `Account` 新增 `archived` 字段（缺省为 false）。
  - 导入旧 JSON 时缺字段按缺省值处理，不报错。
- **业务层**：
  - `LedgerRepository` 新增 `updateAccount` / `archiveAccount` / `unarchiveAccount` / `disablePassword` 四个方法；`saveAccount` 支持"同名命中已归档 → 解档"分支。
  - `LedgerViewModel` 对应新增方法；`uiState` 按 `archived=false` 过滤展示中的账户列表，但历史快照的解析仍然使用全量账户 map。
  - 销毁路径追加 DataStore `clear()`。
- **UI 层**：
  - `EditPersonDialog` / `AddPersonDialog`：生日两个输入框可留空；`PersonCard` 的生日行在无数据时隐藏。
  - `AssetsScreen`：账户卡片点击后的详情弹窗增加 `[编辑]` / `[归档]` 按钮；新增 `EditAccountDialog`；新增账户命中归档记录时的恢复提示；列表按 `archived=false` 过滤。
  - `LedgerApp` 根 Composable：`passwordSet=false` 时跳过 `LockScreen` 直接呈现主界面。
  - `SettingsScreen`：将单按钮"修改密码"重做为按 `passwordSet` 切换的 `[启用应用密码]` / `[修改密码] + [关闭密码]` 组合；引入 `EnablePasswordDialog` / `DisablePasswordDialog`（修改密码弹窗沿用现有实现）。
- **偏好 / 设置**：`UserPreferences` 新增 `clear()` 能力供销毁路径调用。
- **测试**：`LedgerLogicTest` 需新增对 `isBirthdayApproaching(null, null)` 与账户 archived 过滤的覆盖；`LedgerRepository` 层关键路径（启用 / 关闭密码、归档 / 解档账户、销毁清理）建议补单元或集成测试。
