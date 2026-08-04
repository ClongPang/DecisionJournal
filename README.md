# 回看 · Decision Journal

记录决策，总结决策，复盘决策，甚至是辅助决策。

Android 原生项目骨架，基于 `Kotlin + Jetpack Compose + Material 3 + Room`，当前使用 Android SDK 37、AGP 9.3.0、Gradle 9.6.1 和 AGP 内置 Kotlin。

## 当前包含

- Compose 主题与 iOS 风格的浅色视觉基线
- 首页、创建决定、我的决定三个基础页面
- Compose Navigation 页面路由
- Room `Decision` / `Choice` 数据模型与 DAO
- Hilt 数据库依赖注入
- DataStore、WorkManager 依赖预留
- Debug 构建首次启动自动加载完整体验预热数据；Release 构建保持空数据

### Debug 数据预热

Debug 构建首次启动时会在本地生成覆盖以下场景的示例数据：已回看、多次复盘、未来待复盘、已到期待复盘、无复盘日期、未选择最终方案和统计洞察。预热器只在数据库为空时执行，不会覆盖已有用户数据。

如需重新查看预热数据，请清除应用数据后重新启动 Debug APK；该操作会删除设备上的本地决策记录。

## 打开项目

使用 Android Studio 打开本目录，等待 Gradle Sync 完成后运行 `app` 配置。

项目可以通过 Android Studio 或项目内的本地构建脚本完成 Debug 构建。

## 构建 Debug APK

如果本机已经解压了 Gradle Wrapper 所需版本，可以执行：

```bash
./build-debug.sh
```

脚本会优先使用 `GRADLE_USER_HOME`（默认是 `~/.gradle`）下已存在的 Gradle 版本，避免重复下载发行版；实际构建缓存和 Android 用户目录放在项目内的 `.gradle-user-home`、`.android-user-home`，以避免系统缓存目录权限或原生库加载问题。找不到本地发行版时才回退到 `./gradlew`。

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

依赖版本已锁定。首次修改版本后需要联网执行：

```bash
./gradlew dependencies --write-locks
```

日常构建和测试使用：

```bash
./build-debug.sh
./test-connected.sh
```

不要单独升级 AGP、Kotlin、KSP 或 Hilt；请按兼容版本组一起升级，并重新执行构建与测试。
