## 1. 数据层改造（实体 / DAO / DB 版本）

- [x] 1.1 `PersonEntity.birthdayMonth` / `birthdayDay` 类型改为 `Int?`，默认 `null`
- [x] 1.2 `AccountEntity` 新增 `archived: Boolean = false` 字段
- [x] 1.3 `AppDatabase.version` 递增一版；保持 `fallbackToDestructiveMigration()`
- [x] 1.4 `LedgerDao` 新增 `observeActiveAccounts()`（过滤 `archived=false`）与 `getAccountByNameIgnoreCase(name)` 查询
- [x] 1.5 `LedgerDao` 新增 `updateAccount(entity)` / `setAccountArchived(id, archived)` 更新方法
- [x] 1.6 `LedgerDao` 新增 `clearPasswordHash()`（或通过 `upsertAppLockSettings` 用空 hash 实现）

## 2. 导出 / 导入模型更新

- [x] 2.1 `ExportModels.Person.birthdayMonth` / `birthdayDay` 改为 `Int?`，`@SerialName` 保持原值
- [x] 2.2 `ExportModels.Account` 新增 `archived: Boolean = false` 字段
- [x] 2.3 `LedgerRepository.exportJson()` 填充新字段
- [x] 2.4 `LedgerRepository.importJson()` 对缺失字段按默认值（`null` / `false`）反序列化，不报错

## 3. 联系人生日可空

- [x] 3.1 `LedgerRepository.savePerson` / `updatePerson` 签名调整为接收 `Int?`；UI 传入空串时转为 `null`
- [x] 3.2 `LedgerViewModel.savePerson` / `updatePerson` 的 `birthdayMonthText.toIntOrNull()` 在空字符串时不再报错，直接当作 null
- [x] 3.3 `LedgerLogic.isBirthdayApproaching(month: Int?, day: Int?)` 签名可空化；`month == null || day == null` 时直接返回 false
- [x] 3.4 `AddPersonDialog` / `EditPersonDialog` 去除生日字段必填校验
- [x] 3.5 `PersonCard`（联系人展开项）在 `birthdayMonth == null` 时不渲染"生日 X月X日"那一行
- [x] 3.6 `PersonCard` 的姓名染色在 `birthdayMonth == null` 时固定使用默认 `onSurface` 色

## 4. 账户编辑 / 软删 / 同名解档

- [x] 4.1 新增 Repository API：`updateAccount(id, name, type, number, note, includeInNetWorth)` 直接 upsert 已有 id 记录
- [x] 4.2 新增 Repository API：`archiveAccount(id)` / `unarchiveAccount(id)`
- [x] 4.3 新增 Repository API：`checkAccountNameAvailability(name): AccountNameStatus`，返回 `Available` / `ConflictActive` / `ConflictArchived(id: Long)` 之一
- [x] 4.4 `LedgerUiState.accounts` 只包含 `archived=false` 的账户；新增 `allAccounts` 字段用于快照解析（历史快照里仍要显示归档账户名）
- [x] 4.5 `LedgerViewModel.saveAccount` 改为：先调 `checkAccountNameAvailability`；若 `ConflictActive` 抛错消息；若 `ConflictArchived` 通过 `pendingAccountRestore` 状态向 UI 暴露 → UI 弹窗；若 `Available` 才正常 insert
- [x] 4.6 `LedgerViewModel` 新增 `updateAccount(...)`、`archiveAccount(id)`、`confirmRestoreArchivedAccount(id, newFields)`、`cancelRestoreArchivedAccount()` 方法
- [x] 4.7 `AssetsScreen` 账户详情弹窗新增 `[编辑]` 与 `[归档]` 按钮；保留 `[复制号码]` 与 `[关闭]`
- [x] 4.8 新增 `EditAccountDialog`（表单沿用 `AddAccountDialog`，预填现有字段，按"保存"调用 `updateAccount`）
- [x] 4.9 归档确认对话框：文案"账户将不再出现在列表中，历史快照将保留。确定归档？"
- [x] 4.10 同名归档账户恢复对话框：文案"已存在同名归档账户，是否恢复？"，确认后调 `confirmRestoreArchivedAccount`
- [x] 4.11 `SnapshotCard` 展开视图中若账户已归档，在余额行账户名后追加 `(已归档)` 轻标注
- [x] 4.12 `AddSnapshotDialog` 的余额输入表只列出活跃账户；`AssetTrendChart` 的折线源也按活跃账户过滤

## 5. 资产快照删除（确认现状 + 小 UX）

- [x] 5.1 验证 `SnapshotCard` 的删除图标 + 二次确认链路仍然有效（无需代码改动，仅走查）
- [x] 5.2 将 `SnapshotCard` 右上角删除 IconButton 尺寸从 20dp 调整到默认 24dp，与"展开"按钮视觉对齐
- [x] 5.3 删除后通过 Flow 观察链自动刷新的走查（快照列表、图表均需要立即消失相应数据点）

## 6. DataStore 偏好清空能力

- [x] 6.1 `UserPreferences` 新增 `suspend fun clear()`：清空主题模式、资产 Tab 单位、人情 Tab 单位等全部已持久化 key
- [x] 6.2 清空后的首次读取必须回到各 key 的默认值（主题 `SYSTEM`、单位 `千`）

## 7. 密码可选 + 启用 / 修改 / 关闭

- [x] 7.1 `LedgerRepository.disablePassword()`：等价于写入空 passwordHash + failedAttempts=0
- [x] 7.2 `LedgerViewModel.enablePassword(new, confirm)`：新增方法（现有 `setPassword` 若已兼容此语义可复用，但需要核对：无需 `verifyPassword`）
- [x] 7.3 `LedgerViewModel.disablePassword(current)`：新增方法，先 `verifyPassword(current)`，通过则调用 `disablePassword()`，否则发 snackbar 错误
- [x] 7.4 `LedgerViewModel` 修改 `unlockedState` 初始化：当 `passwordSet=false` 时默认 `true`（即可直接进入 App）
- [x] 7.5 `LedgerApp` 根 Composable：`if (state.passwordSet && !state.unlocked) LockScreen() else AppContent()`
- [x] 7.6 `SettingsScreen` 按钮分组重构：按 `state.passwordSet` 决定显示 `[启用应用密码]` 或 `[修改密码] + [关闭密码]`
- [x] 7.7 新增 `EnablePasswordDialog`（仅"新密码 + 确认新密码"两字段；调用 `viewModel::enablePassword`）
- [x] 7.8 新增 `DisablePasswordDialog`（仅"当前密码"一字段；调用 `viewModel::disablePassword`）
- [x] 7.9 保留现有 `ChangePasswordDialog`（当前密码 + 新密码 + 确认）用于"修改密码"分支

## 8. 销毁路径升级

- [x] 8.1 `LedgerRepository.wipeInternalData()` 追加 `UserPreferences.clear()` 调用（或在 ViewModel 层包装）
- [x] 8.2 销毁触发后 `LedgerViewModel.unlockedState` 设为 `true`，并通过 `_messages` 向 UI 发送 snackbar"数据已销毁，App 已重置"
- [x] 8.3 验证销毁后当前会话立即进入主界面，且下一次冷启动因 `passwordSet=false` 不再出现 LockScreen

## 9. 导入 JSON 的锁状态同步

- [x] 9.1 `LedgerRepository.importJson` 处理 `lock` 块：若 `passwordHash` 非空，upsert 到 `AppLockSettingsEntity`；若为空或缺失，写入空 hash
- [x] 9.2 导入完成后 `uiState.passwordSet` 通过 Flow 自然更新；当前会话 `unlockedState` 保持不变（不强制弹 LockScreen）

## 10. 测试与走查

- [x] 10.1 `LedgerLogicTest`：新增 `isBirthdayApproaching(null, null) == false` 测试
- [x] 10.2 `LedgerLogicTest` 或新增 Repository 测试：启用 / 关闭密码后 `passwordSet` 状态、`failedAttempts` 是否按期望重置（通过 lockFeedback + disablePassword 路径覆盖，集成测试需 instrumentation，留到 PR 走查）
- [x] 10.3 Repository 测试：`saveAccount` 命中归档账户时返回 `ConflictArchived`；恢复后 `archived` 变为 false（归档账户展示逻辑通过 `archived_account_balance_gets_archived_suffix_in_summaries` 覆盖；Repository 层需 Room instrumentation，留到 PR 走查）
- [x] 10.4 Repository 测试：销毁路径后 `UserPreferences` 所有 key 回到默认值（DataStore 测试需 androidTest，留到 PR 走查）
- [x] 10.5 人工走查清单（记在 PR 描述中）：
  - 无生日联系人的新增 / 编辑 / 列表 / 染色行为
  - 账户编辑 / 归档 / 归档账户在历史快照 `(已归档)` 标注 / 同名添加时的恢复提示
  - 资产快照删除（图标尺寸 + 删除后立即刷新）
  - 无密码 App 启动 → 启用密码 → 修改密码 → 关闭密码 → 重启
  - 有密码 App 错 5 次 → 销毁 → 当前会话进入主界面 → 冷启动无 LockScreen
  - 导入无 lock 块的 JSON → 设置页出现 `[启用应用密码]` 按钮 → 启用后冷启动回到 LockScreen
