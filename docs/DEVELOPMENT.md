# Offline Ledger 开发说明

## 环境

- Android Studio Koala 或更高版本
- Android Studio 自带 JBR 或任意可用的 JDK 17+
- Android SDK Platform 36
- Android SDK Build-Tools 36.1.0
- Android Emulator 或一台 Android 8.0+ 真机

## 首次打开

1. 用 Android Studio 打开仓库根目录 `/Users/liyutong/Documents/Other/TestSpec`
2. 仓库已经包含 Gradle Wrapper，首次同步时允许 Android Studio 自动下载对应 Gradle 发行版
3. 确认 `local.properties` 指向你的 SDK，例如：

```properties
sdk.dir=/Users/<your-user>/Library/Android/sdk
```

## 调试

- 默认启动入口：`app/src/main/java/com/codex/offlineledger/MainActivity.kt`
- 应用类：`app/src/main/java/com/codex/offlineledger/OfflineLedgerApplication.kt`
- 数据库：`app/src/main/java/com/codex/offlineledger/data/AppDatabase.kt`
- 核心业务规则：`app/src/main/java/com/codex/offlineledger/domain/LedgerLogic.kt`
- 后台提醒：`app/src/main/java/com/codex/offlineledger/work/ReminderWorker.kt`

建议的断点位置：

- 解锁与输错 5 次销毁：`LedgerViewModel.unlock`
- 快照保存：`LedgerViewModel.saveSnapshot`
- 生日自动生成 Todo：`LedgerRepository.generateBirthdayTodos`
- 循环规则计算：`LedgerLogic.computeNextOccurrence`
- JSON 导出：`LedgerRepository.exportJson`

## 本地运行

- Android Studio 里直接运行 `app`
- 命令行也可以直接执行：

```bash
./gradlew assembleDebug
```

如果你想在终端里复用 Android Studio 自带 JBR，可以这样执行：

```bash
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
  ./gradlew test assembleDebug
```

## Release 包

### 1. 生成签名

如果你还没有 keystore：

```bash
keytool -genkeypair -v \
  -keystore offline-ledger-release.jks \
  -alias offline-ledger \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

### 2. 配置签名

在 `app/build.gradle.kts` 里增加 `signingConfigs` 和 `release` 绑定，或者在 Android Studio 的 `Build > Generate Signed Bundle / APK` 里交互式配置。

### 3. 打包

- Android Studio:
  `Build > Generate Signed Bundle / APK`
- 命令行:

```bash
./gradlew assembleRelease
```

当前工程在未配置正式签名前，命令行产物位于：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

配置签名后，再通过 Android Studio 的签名向导或自定义 `signingConfigs` 生成可安装的正式包。

如果要发布 AAB：

```bash
./gradlew bundleRelease
```

产物位于：

```text
app/build/outputs/bundle/release/app-release.aab
```

## 说明

- 当前实现以 Room + JSON 导出为主，不包含 JSON 回导功能
- 当前已验证 `./gradlew test assembleDebug` 与 `./gradlew assembleRelease` 可以在本机环境下成功执行
