## 上下文

当前应用已经具备资产快照、送礼台账、Todo、笔记和设置共五个底部 Tab，并通过 DataStore 支持"系统 / 浅色 / 深色"三挡主题切换。随着 `enhance-offline-ledger` 变更将"设置"Tab 放置到中间位置并要求特殊样式，以及后续要求的高度恒定诉求，目前通过 `NavigationBar` 加圆形 icon Box 的实现已经不能同时满足"设置凸起"和"高度恒定"。

同时，浅 / 深色主题的饱和度偏高、两套气质脱节的问题长期被掩盖在其他功能开发之下，现在需要一次性梳理。两个问题都聚焦在"应用外壳（shell）的视觉规范"，因此合入同一个变更处理。

## 目标 / 非目标

**目标：**
- 提供一套低饱和、家族式的浅 / 深主题色板，两者在切换时不出现气质断裂。
- 取消 `secondary` 作为可见强调色的角色，全局只用一个 `primary`。
- 引入 `surfaceVariant` 作为第三层表面色，用于卡片内嵌子块的分层。
- 提供一个高度恒定、视觉统一的底部导航结构：设置以居中 FAB 的形式常驻凸起，其他四个 Tab 均匀分布在两侧。
- 选中 Tab 通过在图标正上方浮现标签实现"视觉抬升"，而非改变整条导航栏的实际高度。
- 设置 FAB 按下瞬间颜色自然加深，松手恢复，提供即时触感反馈。

**非目标：**
- 不在此变更中扩展主题挡位（仍只保留 System / Light / Dark 三挡）。
- 不引入动态取色（Material You 壁纸取色）或自定义主题。
- 不改变 Tab 的顺序、数量或路由（仍为笔记 · Todo · 设置 · 资产 · 人情）。
- 不修改任何数据模型、业务逻辑或导出 / 导入字段。
- 不调整字体排印系统（`Type.kt` 维持现状）。

## 决策

### 1. 浅 / 深主题采用"大地米调"家族，日雾绿、夜暖燕麦

- 选择原因：用户希望柔和且连贯，两套色板在色相上处于同一"暖米大地"象限，日间偏绿冷静、夜间偏暖温润，在明 / 暗切换时像"同一张纸在不同光线下"。
- 备选方案：两套都采用 Notion 近灰极简风；或两套都使用 Material You 柔蓝绿原生风。
- 不选原因：近灰极简失去账本气质；柔蓝绿不符合"纸质账本"的气氛偏好。

具体色板：

**浅色（雾调大地）**
```
background          #FAF7F2   米白
surface             #F1ECE3   浅米（卡片）
surfaceVariant      #E8E2D5   更浅米（卡片内嵌子块）
outline             #D4CCBC   浅米灰（分割线 / 描边）
primary             #8FA085   雾橄榄绿
onPrimary           #FFFFFF   白
primaryContainer    #C8D1BC   浅雾绿（FAB 底）
onPrimaryContainer  #2E3A28   深橄榄
onBackground        #3A3A36   深灰（主文字）
onSurface           #3A3A36   深灰
onSurfaceVariant    #6E6E68   中灰（次文字 / 默认 icon）
```

**深色（暖杏燕麦）**
```
background          #1E1A17   深咖
surface             #2A2522   深暖咖（卡片）
surfaceVariant      #352F2B   中深咖（卡片内嵌子块）
outline             #4A4340   深暖灰
primary             #D4BB94   暖燕麦
onPrimary           #3A2E20   深咖
primaryContainer    #5C4A35   深燕麦（FAB 底）
onPrimaryContainer  #F0DDC2   浅燕麦
onBackground        #EFE4D6   暖米（主文字）
onSurface           #EFE4D6   暖米
onSurfaceVariant    #A89F93   中暖灰（次文字 / 默认 icon）
```

### 2. 不启用 `secondary`，全局只使用一个 `primary` 强调

- 选择原因：两种强调色并列使用容易让界面失焦，而账本应用的交互核心已经足够聚焦（单一主按钮、单一 FAB、单一选中色），统一用 `primary` 能减少噪声。
- 备选方案：保留 `secondary` 用于某些次要强调，例如 Chip、次要按钮。
- 不选原因：当前 UI 中没有足够多的次要强调场景需要第二种色相，保留反而会导致"什么时候用哪种"的不确定性。

具体实施：Material 3 的 `ColorScheme` 仍然需要给 `secondary` / `onSecondary` / `secondaryContainer` / `onSecondaryContainer` 提供色值（否则默认黑），但这些色值仅作为"框架兜底"存在，在所有自写 Composable 中不允许引用。采用的策略是：将 `secondary` 系列设为 `primary` 系列的同色，作为"无意义但合法"的占位。

### 3. 启用 `surfaceVariant` 做三层分层

- 选择原因：资产快照卡片、人情联系人卡片都存在"卡片 → 内部条目列表"的嵌套结构，三层分层能让子块从卡片里微微凸显出来，阅读层次更清晰。
- 备选方案：所有内嵌块沿用 `surface` 同色，或用轻描边区分。
- 不选原因：同色过于扁平，长条目一片糊；描边在低饱和大地色下会显得"脏"。

### 4. ~~底部导航改为 `BottomAppBar` + 居中 FAB~~ → **v2 推翻，改为标准 80dp `NavigationBar` 等分 5 Tab**

> **状态：v1 决策已作废。** 实装后视觉违和，改用 Material 3 标准 `NavigationBar` 样式（见 v2 细化）。

- v2 选择原因：FAB + 切口在低饱和大地色板下与整体气质不协调；切口轮廓破坏 bar 的连续感；凸起元素让"稳定账本"的氛围失衡。采用 Material 3 标准 `NavigationBar` 风格（高度 80dp、5 个等宽 Tab）能给出最简洁干净的底栏。
- v2 备选方案：保留 FAB 但去掉切口；或让 FAB 完全融入 bar（背景同色 + 无凸起）。
- v2 不选原因：前者仍保留视觉凸起；后者与普通 Tab 区分不开，等同于放弃设置特异化。

Tab 顺序与路由映射保持不变，但布局不再有凸起：

```
┌───────┬───────┬───────┬───────┬───────┐
│ 笔记  │ Todo  │ 设置  │ 资产  │ 人情  │   5 Tab 等宽，整体 80dp
└───────┴───────┴───────┴───────┴───────┘
```

### 5. ~~设置以 FAB 形式悬浮居中，有 docked 切口~~ → **v2 整节作废**

> **状态：整节作废。** 无 FAB、无切口、无向上凸起。设置 Tab 的特异化改由新决策 9（视觉权重差异）承担。

### 6. ~~Tab 高度恒定通过 label 上方浮现实现~~ → **v2 改为 icon / label crossfade 互斥**

- v2 选择原因：label 在图标上方浮现虽然在几何上是恒定高度，但视觉上选中 Tab 仍然"比别的 Tab 多占了一截"，感知抖动没有真的消除。改为 icon 和 label 在**同一位置**通过 crossfade 互斥过渡，不增加任何纵向视觉重量，才是真正意义的"无抖"。
- v2 备选方案：保留浮现但加强"未选中 Tab 留白"以对齐高度感；或完全去掉 label 只靠颜色表达选中。
- v2 不选原因：前者让未选中 Tab 出现无意义空白；后者缺少文字对"在哪个 Tab"的直接说明。

v2 导航结构关键参数：

```
NavigationBar 高度            80dp（Material 3 标准）
Tab 数量 / 宽度               5 个等宽均分

Tab 单元槽位（所有 Tab 一致）
┌────────────┐
│            │
│   ┌────┐   │    24dp icon 居中（未选中）
│   │icon│   │    OR
│   │or  │   │    labelSmall 文本居中（选中）
│   │label│  │
│   └────┘   │
│            │
└────────────┘

切换动画时长                  180ms EaseOutCubic
  · icon 透明度                1 → 0
  · label 透明度               0 → 1
  · 两者同时进行，同一槽位，无位移
```

### 7. 选中 Tab 文字使用 `primary + SemiBold`，未选中图标保持 `onSurfaceVariant`

> **v2 微调：** 此决策从 v1 延续，但细节对齐到新动画。

- 选择原因：在 crossfade 方案下，选中态由"文字"承担全部视觉指示；文字用 `primary` 提供色彩区分，`FontWeight.SemiBold` 提供重量区分，组合起来在快速扫视中足够醒目又不喧宾夺主。常规 Tab 未选中时的图标保持 `onSurfaceVariant` 中性灰。
- 备选方案：文字用 `Bold` 更重；或文字不加粗仅靠颜色。
- 不选原因：Bold 在 `labelSmall` 尺寸下过粗显得笨重；不加粗的 primary 文字与未选中的灰 icon 对比度偏弱。

### 8. ~~FAB 按下瞬间加深~~ → **v2 整节作废**

> **状态：整节作废。** 无 FAB，设置 Tab 按下反馈走标准 `NavigationBarItem` ripple，无须特别设计。

### 9. 设置 Tab 视觉特异化：始终 `Icons.Filled.Settings` + `primary`，其他 Tab 用 `Icons.Outlined.*` + `onSurfaceVariant`

- 选择原因：取消 FAB 后仍需保留"设置与其他功能性 Tab 地位不同"的暗示。通过**图标风格差异（Filled vs Outlined）+ 颜色差异（primary vs 灰）**的组合，设置图标在一排 Tab 中可一眼识别，但不破坏 bar 的几何等宽整齐。Filled 图标在视觉上比 Outlined 更"重"，自然成为视觉锚点。
- 备选方案：所有 Tab 用 Outlined，设置仅靠颜色区分（C1 方案）；或给设置单独加 `primaryContainer` 圆角背景块；或把设置做成比其他 Tab 稍大的尺寸。
- 不选原因：纯颜色差异在小尺寸图标上识别度不足；背景块重新引入"设置格子比别人大"的违和感；尺寸差异破坏等宽整齐。

Tab 图标映射（v2）：

| Tab | 未选中 icon | 未选中色 | 选中态 |
|-----|-------------|----------|--------|
| 笔记 | `Icons.Outlined.Create` | `onSurfaceVariant` | crossfade → "笔记" primary SemiBold |
| Todo | `Icons.Outlined.CheckCircle` | `onSurfaceVariant` | crossfade → "Todo" primary SemiBold |
| 设置 | `Icons.Filled.Settings` | `primary` | crossfade → "设置" primary SemiBold |
| 资产 | `Icons.Outlined.Home` | `onSurfaceVariant` | crossfade → "资产" primary SemiBold |
| 人情 | `Icons.Outlined.Favorite` | `onSurfaceVariant` | crossfade → "人情" primary SemiBold |

### 10. Icon 与 Label 使用 Crossfade 互斥

- 选择原因：选中态不改变 Tab 几何（位置、尺寸、占用宽度），只交换内容。透明度交叉 180ms 的节奏与主题色切换动画时长对齐，整体感觉一致；EaseOutCubic 的减速让最终状态更稳。两者 **同时进行** 而非串联，避免中间出现"空槽"的空白帧。
- 备选方案：瞬时硬切（无动画）；icon 淡出先完成、再淡入 label（串联）；加 scale/position 的复合过渡。
- 不选原因：硬切显得廉价；串联让切换感觉"迟钝一倍"；复合过渡重新引入位移感，违背本次"无抖"目标。

具体过渡实现：

```
未选中 → 选中
  · icon 透明度:  1.0 → 0.0  (tween 180ms, EaseOutCubic)
  · label 透明度: 0.0 → 1.0  (tween 180ms, EaseOutCubic)
  · 两者重叠在同一槽位（同 Box，label 与 icon 同大小的居中放置）

选中 → 未选中
  · 对称反向过渡，同样 180ms EaseOutCubic
```

## 风险 / 权衡

- [新色板与既有截图 / 素材的视觉连贯性] → 接受本次整体换新带来的视觉断代，旧截图属于迭代记录，不作为视觉约束。
- [`BottomAppBar` 配合切口 FAB 在较老 Material 3 版本中可能布局异常] → 使用项目现有 Compose BOM 中稳定支持的 `BottomAppBar(floatingActionButton = ...)` API，并在开发时在浅 / 深两种主题下进行真机布局验证。
- [Tab 内的 label 浮现动画在低性能设备上可能掉帧] → 动画只在当前选中 Tab 的 label 上运行，幅度小（4dp 位移 + 透明度），预期代价可忽略；若实际表现不佳，可降级为无动画的瞬时显隐。
- [`surfaceVariant` 在内嵌子块中使用可能与部分第三方组件（如 Vico 图表）的默认背景冲突] → 对图表容器显式传入 `surface` 或 `surfaceVariant` 背景，不依赖第三方组件的默认推断。
- [取消 `secondary` 后，未来若需要第二种强调色需要回溯修改] → 接受此风险，将"是否启用 secondary"明确记录为设计决策，未来若出现第二强调需求再单独提出变更。

## Migration Plan

1. 重写 `ui/theme/Color.kt`：引入上文定义的浅 / 深两套色板所有色值命名，移除旧色名 `Fern` / `Clay` / `Sand` / `Bark` / `Mist` / `Moss` 的 UI 引用，必要时以别名保留过渡。
2. 重写 `ui/theme/Theme.kt`：`LightColors` / `DarkColors` 使用新色值；`secondary` 系列设置为 `primary` 的占位同色。
3. 全局搜索 `MaterialTheme.colorScheme.secondary` / `onSecondary` / `secondaryContainer` / `onSecondaryContainer`，统一替换为 `primary` 对应字段或 `surface` / `surfaceVariant`。
4. 在 `LedgerApp.kt` 中将 `Scaffold` 的 `bottomBar` 替换为 `BottomAppBar` + 居中 `FloatingActionButton` 实现；按设计决策 6 的参数实现 Tab 项的图标 / 标签布局与动画。
5. 删除原 `NavigationBar` + `NavigationBarItem` + 圆形 icon Box 的相关代码。
6. 在浅 / 深两种主题下手动验证：Tab 切换无高度跳变，FAB 按下有加深反馈，选中 Tab 仅 label 变色，三层表面有可辨识的对比。
7. 若存在未覆盖到的组件（例如 Chip、Switch）的 `secondary` 引用，在验证阶段统一清理。

## Open Questions

- 无阻塞提案落地的开放问题；所有强调色、FAB 切口、选中样式、标签显示策略、FAB 按下反馈均已在本文件中锁定。
