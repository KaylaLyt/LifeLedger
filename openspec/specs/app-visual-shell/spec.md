# app-visual-shell 规范

## 目的
待定 - 由归档变更 refine-theme-and-nav 创建。归档后请更新目的。
## 需求
### 需求:系统必须提供同家族的浅 / 深主题色板

系统必须提供浅色与深色两套 Material 3 `ColorScheme`，两套色板属于同一"大地米调"家族：浅色以雾橄榄绿为 `primary`、米白系为表面族；深色以暖燕麦为 `primary`、深咖系为表面族。两套色板的饱和度均控制在低区间，切换主题时不得出现气质断裂。

#### 场景:浅色主题使用雾调大地色板
- **当** 用户将主题模式切换为"浅色"或在"跟随系统"下系统为浅色
- **那么** 系统必须使用米白 `#FAF7F2` 作为 `background`、浅米 `#F1ECE3` 作为 `surface`、雾橄榄绿 `#8FA085` 作为 `primary`

#### 场景:深色主题使用暖杏燕麦色板
- **当** 用户将主题模式切换为"深色"或在"跟随系统"下系统为深色
- **那么** 系统必须使用深咖 `#1E1A17` 作为 `background`、深暖咖 `#2A2522` 作为 `surface`、暖燕麦 `#D4BB94` 作为 `primary`

#### 场景:主题切换时气质连贯
- **当** 用户在浅色与深色之间切换
- **那么** 两套主题的 `primary` 必须落在"暖米大地"色域内，不得出现跨色相的跳变

### 需求:系统必须只使用 `primary` 作为可见强调色

系统必须将所有按钮、FAB、选中态、输入框高亮、Checkbox / Switch 选中色等强调语义统一指向 Material 3 `ColorScheme` 的 `primary` 系列；`secondary` 系列在自写 UI 代码中不得被引用。

#### 场景:任意强调元素使用 primary
- **当** 系统渲染一个需要视觉强调的元素（主按钮、FAB、选中 Tab 标签、Checkbox 选中状态等）
- **那么** 该元素的强调色必须来自 `primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer`

#### 场景:secondary 不出现在自写 UI 中
- **当** 开发者在 Composable 代码中书写颜色引用
- **那么** 不得出现 `MaterialTheme.colorScheme.secondary` / `onSecondary` / `secondaryContainer` / `onSecondaryContainer`

### 需求:系统必须使用三层表面色区分嵌套结构

系统必须在"页面背景 → 卡片表面 → 卡片内嵌子块"三层结构中分别使用 `background`、`surface`、`surfaceVariant`，让嵌套结构在视觉上可区分。

#### 场景:资产快照卡片内部账户列表分层
- **当** 系统渲染资产快照卡片
- **那么** 卡片自身必须使用 `surface`，卡片内部的账户余额行等子块必须使用 `surfaceVariant`

#### 场景:页面背景与卡片可区分
- **当** 系统渲染任意包含卡片的页面
- **那么** 页面 `background` 与卡片 `surface` 必须使用不同色值，使卡片边缘可见

### 需求:系统必须使用等宽 5 Tab 的标准 80dp 底部导航

系统必须使用固定 80dp 高度的底部导航栏，内含 5 个等宽均分的 Tab，按顺序为"笔记 / Todo / 设置 / 资产 / 人情"。导航栏整体高度在任意 Tab 切换期间必须保持不变，任何 Tab 不得在几何上凸起、放大或改变槽位尺寸。

#### 场景:导航结构
- **当** 用户位于任意 Tab
- **那么** 底部导航必须呈现"笔记 · Todo · 设置 · 资产 · 人情"的 5 格等宽布局

#### 场景:导航栏高度恒定为 80dp
- **当** 系统渲染底部导航
- **那么** 导航栏高度必须为 80dp，且不得出现任何向上凸出的子元素（无 FAB、无切口）

#### 场景:切换 Tab 不抖动
- **当** 用户在任意两个 Tab 之间切换
- **那么** 导航栏自身高度、所有 Tab 槽位的位置与尺寸必须保持不变

### 需求:选中 Tab 必须通过 icon 与 label 的 crossfade 互斥显示

系统必须在 Tab 选中态与未选中态之间通过 icon 与 label 的透明度交叉过渡（crossfade）切换显示：未选中态仅显示 icon，选中态仅显示 label，两者在 Tab 槽位内占用同一居中位置。过渡使用 180ms 的 EaseOutCubic 透明度动画，icon 与 label 的渐出渐入同步进行，同位置槽位不得出现任何位移或尺寸变化。

#### 场景:未选中 Tab 仅显示 icon
- **当** 某个 Tab 未被选中
- **那么** 该 Tab 槽位必须仅显示图标，不得显示任何标签文字

#### 场景:选中 Tab 仅显示 label
- **当** 某个 Tab 被选中
- **那么** 该 Tab 槽位必须仅显示 label 文字（图标透明度为 0）

#### 场景:切换使用 crossfade
- **当** 某个 Tab 在选中 / 未选中之间切换
- **那么** icon 的透明度（1↔0）与 label 的透明度（0↔1）必须以 180ms EaseOutCubic 同步过渡，不得出现位移

#### 场景:选中 label 使用 primary + SemiBold
- **当** 某个 Tab 处于选中态
- **那么** 该 Tab 的 label 文字必须使用 `primary` 色与 `FontWeight.SemiBold` 重量

### 需求:设置 Tab 必须通过 Filled 图标与 primary 色实现视觉特异化

系统必须让"设置" Tab 在未选中状态下使用 `Icons.Filled.Settings` 图标并以 `primary` 色显示，与其他 4 个 Tab 的 `Icons.Outlined.*` + `onSurfaceVariant` 灰色图标形成视觉区分。设置 Tab 在选中态下的 label 行为与其他 Tab 一致，不得获得任何额外的几何凸起、背景块或尺寸放大。

#### 场景:设置未选中时 filled primary
- **当** 用户未处于设置 Tab
- **那么** 设置 Tab 必须显示 `Icons.Filled.Settings` 图标，颜色为 `primary`

#### 场景:其他 Tab 未选中时 outlined 灰
- **当** 用户未处于"笔记 / Todo / 资产 / 人情"中的某个 Tab
- **那么** 对应 Tab 必须显示 `Icons.Outlined.*` 图标，颜色为 `onSurfaceVariant`

#### 场景:设置选中时走统一 crossfade
- **当** 用户切换到设置 Tab
- **那么** 设置 Tab 的 filled icon 必须通过 crossfade 过渡到 "设置" label，label 样式与其他选中 Tab 一致（primary + SemiBold）

#### 场景:设置 Tab 无额外几何特异化
- **当** 系统渲染设置 Tab
- **那么** 该 Tab 的槽位宽度、高度、背景、内边距必须与其他 4 个 Tab 完全一致

