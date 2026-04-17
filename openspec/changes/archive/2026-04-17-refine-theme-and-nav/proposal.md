## 为什么

当前浅色主题以米黄 + 陶土橘为主，深色主题以近黑 + 深咖啡为底、米白为强调，两套配色各自饱和度偏高，长时间使用容易视觉疲劳；并且浅色与深色的气质脱节，在切换时观感跳跃。

底部导航栏同时存在"设置 Tab 特殊凸起"和"选中 Tab 才显示标签"两个规则，但它们没有被统一在一个稳定的高度基线上，导致在不同 Tab 之间切换时整条导航栏高度抖动，影响整体协调感。

## 变更内容

- 重做浅色与深色主题的调色板，采用低饱和、同家族的"大地米调"系，日间偏雾橄榄绿、夜间偏暖燕麦；两套配色是同一气质的明 / 暗两面，切换时不再断裂。
- 去掉 `secondary` 强调色的使用，统一通过单一 `primary` 承载强调语义，整体更安静干净。
- 引入 `surfaceVariant` 作为第三层表面色，用于区分"背景 / 卡片 / 卡片内嵌子块"的层次。
- 用 Material 3 的 `BottomAppBar` + 居中 `FloatingActionButton` 重构底部导航：设置以悬浮 FAB 形式常驻中心（带切口），其他四个 Tab 作为 `BottomAppBar` 的 action 均匀分布在两侧。
- 导航栏整体高度恒定，选中 Tab 通过在图标正上方浮现标签文字实现"视觉抬升"；图标位置与导航栏外轮廓始终不变，不再出现切换时的高度跳变。
- 选中 Tab 的标签使用 `primary` 色，图标保持 `onSurfaceVariant` 的默认中性色。
- 设置 FAB 使用 `primaryContainer` 作为背景色，按下瞬间通过 state layer 自然加深反馈，松手恢复。

## 功能 (Capabilities)

### 新增功能
- `app-visual-shell`: 统一的应用外壳视觉规范，包含浅 / 深主题色板与底部导航结构、Tab 高度与选中态、设置 FAB 交互。

### 修改功能

## 影响

- 需要重写 `ui/theme/Color.kt`、`ui/theme/Theme.kt` 两个主题文件，调整色值命名、启用 `surfaceVariant`，并移除 `secondary` 在 UI 中的引用点。
- 需要将 `LedgerApp.kt` 中基于 `NavigationBar` 的底部导航改造为 `BottomAppBar` + 居中 FAB；同时改写 Tab 项的 label 显示策略，保证高度恒定。
- 涉及到所有引用 `MaterialTheme.colorScheme.secondary` / `onSecondary` / 旧色值命名的组件需要改为 `primary` / `surfaceVariant` 等新语义。
- 不改变数据层、业务逻辑、导出格式或任何已存的偏好字段；主题模式（System / Light / Dark）仍沿用当前 DataStore 存储。

## 迭代修正 (v2)

Phase 1-5 实装后，真机走查时发现 `BottomAppBar` + docked FAB 方案在视觉上存在两处问题：

1. **FAB 凸起 + 切口** 与应用整体低饱和大地色调不匹配，切口边缘让底部导航看起来像"被咬掉一块"，观感违和。
2. 虽然 bar 的 **外轮廓高度恒定**，但选中 Tab 的 label 在图标上方浮现时，视觉重心仍然上抬，配合未选中态没有 label 的空白感，Tab 之间看起来仍然"有的高有的矮"。

本轮提出以下修正（推翻原决策 4、5、6、8，保留决策 1、2、3、7）：

- 底部导航回到 Material 3 标准 `NavigationBar` 结构，整体高度统一为 **80dp**，不再出现任何凸起元素（无 FAB、无切口）。
- 5 个 Tab 完全等宽均分，图标严格居中放置，视觉重量一致。
- 选中态改为 **icon 与 label 的 crossfade 互斥**：未选中只显示图标，选中只显示图标位置的文字（同位置、同尺寸槽位），通过 180ms 的透明度交叉过渡完成切换，彻底消除高度错觉。
- 选中 Tab 的 label 使用 `primary` 色 + `FontWeight.SemiBold`。
- "设置"Tab 的特殊存在感改为 **视觉权重差异**：设置始终使用 `Icons.Filled.Settings` + `primary` 色，其余四个 Tab 使用 `Icons.Outlined.*` + `onSurfaceVariant` 灰色；设置 Tab 选中时也走统一的 icon→文字 crossfade，文字同样 SemiBold + primary。
- 删除原 FAB 按下 state layer 反馈相关需求与设计；设置 Tab 的反馈回到标准 ripple。

这次修正不涉及色板、`surfaceVariant` 层级或 `secondary` 的使用策略。
