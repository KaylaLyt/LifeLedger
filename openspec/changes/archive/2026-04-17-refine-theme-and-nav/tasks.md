## 1. 主题色板重建

- [x] 1.1 重写 `ui/theme/Color.kt`，定义浅 / 深两套新色值命名，包含 `background` / `surface` / `surfaceVariant` / `outline` / `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer` / `onBackground` / `onSurface` / `onSurfaceVariant`
- [x] 1.2 重写 `ui/theme/Theme.kt` 的 `LightColors` 与 `DarkColors`，应用新色值，并将 `secondary` 系列设为 `primary` 同色占位
- [x] 1.3 移除旧色名 `Fern` / `Clay` / `Sand` / `Bark` / `Mist` / `Moss` 在 UI 代码中的直接引用，统一走 `MaterialTheme.colorScheme`

## 2. 统一强调色与表面层级

- [x] 2.1 全局搜索并替换 `MaterialTheme.colorScheme.secondary` / `onSecondary` / `secondaryContainer` / `onSecondaryContainer` 为 `primary` 对应字段或合适的 `surface` / `surfaceVariant`
- [x] 2.2 审查资产快照卡片、人情联系人卡片等嵌套结构，将内嵌子块背景从 `surface` 改为 `surfaceVariant`，形成三层分层
- [x] 2.3 审查 Vico 图表容器背景，显式传入 `surface` 或 `surfaceVariant`，避免与新色板冲突

## 3. 底部导航结构重构

- [x] 3.1 在 `LedgerApp.kt` 中将 `Scaffold` 的 `bottomBar` 从 `NavigationBar` 替换为 `BottomAppBar` + 居中 `FloatingActionButton`，开启 docked 切口
- [x] 3.2 FAB 使用 `Icons.Default.Settings` 图标、`primaryContainer` 背景、`onPrimaryContainer` 图标色，点击切换到设置 Tab，按下依赖 Material 3 state layer 自然加深
- [x] 3.3 四个常规 Tab（笔记 / Todo / 资产 / 人情）作为 `BottomAppBar` 的 action 按顺序均匀分布在两侧
- [x] 3.4 删除原 `NavigationBar` + `NavigationBarItem` + 圆形 icon Box 相关代码

## 4. Tab 高度恒定与选中动画

- [x] 4.1 将每个常规 Tab 实现为 `Column`：底部固定 y 坐标处放置 24dp icon，icon 上方预留 label slot，整体 Tab 高度从 bar 底部到 FAB 顶部（约 96dp）
- [x] 4.2 label 仅在选中状态下通过 `AnimatedVisibility` 以 180ms EaseOutCubic 浮现（透明度 0 → 1、位移 4dp → 0）
- [x] 4.3 选中 Tab 的 label 使用 `primary` 色，未选中 icon 使用 `onSurfaceVariant`，选中 icon 保持 `onSurfaceVariant` 不变色
- [x] 4.4 验证在任意 Tab 切换过程中，`BottomAppBar` 外轮廓、FAB 位置、所有 icon y 坐标均不变

## 5. 验证与清理

- [x] 5.1 在浅色与深色主题下分别手动走查：资产 / 人情 / 笔记 / Todo / 设置五个 Tab 的卡片与子块层次清晰、主按钮 / 输入框 / Checkbox 等强调元素颜色一致
- [x] 5.2 验证 Tab 切换无高度跳变、FAB 按下有加深反馈、设置页进入后 FAB 外观不变
- [x] 5.3 清理测试中发现的遗留 `secondary` 引用或旧色名引用
- [x] 5.4 更新 `LedgerLogicTest` 或其他测试中若硬编码的主题色断言

## 6. v2 迭代：Tab Bar 重做（推翻 FAB + 切口方案）

- [x] 6.1 在 `LedgerApp.kt` 中删除 `FloatingActionButton` 调用及其 import，删除 `BottomTabItem` 列表中间的 `Spacer` 占位
- [x] 6.2 删除 `BottomBarCutoutShape` 类以及 `BottomBarHeight` / `BottomBarFabSize` / `BottomBarCutoutPadding` / `BottomBarTotalHeight` 常量，相关 `Shape` / `Path` / `Outline` / `Rect` / `Size` / `Density` / `LayoutDirection` / `Dp` import 全部清理
- [x] 6.3 将 `BottomNavShell` 简化为固定 80dp 高度的 `Surface` + `Row`，内含 5 个等宽 `BottomTabItem`（顺序：笔记 / Todo / 设置 / 资产 / 人情），去掉 `Box` 叠层
- [x] 6.4 `LedgerTab.icon` 扩展属性改写：笔记 / Todo / 资产 / 人情 使用 `Icons.Outlined.*` 版本，设置使用 `Icons.Filled.Settings`；引入 `androidx.compose.material.icons.outlined.*` 相应 import
- [x] 6.5 `BottomTabItem` 改为 icon + label 在同一 `Box` 槽位内的 crossfade 互斥：icon 透明度 `if (selected) 0f else 1f` + `animateFloatAsState(tween(180, FastOutSlowInEasing))`，label 透明度取反，同样的 animate
- [x] 6.6 选中 label 使用 `MaterialTheme.colorScheme.primary` + `FontWeight.SemiBold`，`style = MaterialTheme.typography.labelSmall`
- [x] 6.7 icon 颜色按 Tab 区分：设置 Tab 使用 `MaterialTheme.colorScheme.primary`，其他 Tab 使用 `MaterialTheme.colorScheme.onSurfaceVariant`
- [x] 6.8 移除 `fadeIn` / `fadeOut` / `slideInVertically` / `FloatingActionButton` / `FloatingActionButtonDefaults` / `MutableInteractionSource` / `LocalDensity` / `Density` / `Dp` / `LayoutDirection` / `Rect` / `Size` / `Outline` / `Path` / `Shape` 等在 Phase 3-4 引入但 v2 不再需要的 import（`AnimatedVisibility` 仍在业务展开面板中使用，保留）；同时添加 `animateFloatAsState` / `graphicsLayer` / `Icons.Outlined.*` 的 import
- [x] 6.9 在浅 / 深主题下静态验证：5 Tab 等宽（`weight(1f)` × 5）、80dp 固定高度、icon 居中（`contentAlignment = Alignment.Center`）、选中 crossfade 无位移（同 `Box` 同一槽位、仅 `graphicsLayer.alpha`）、设置 filled primary 醒目 — 通过
