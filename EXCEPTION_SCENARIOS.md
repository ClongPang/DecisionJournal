# 异常场景检查与验收记录

本轮以“用户仍能完成核心目标、数据不悄悄丢失、系统能力失败不拖垮本地业务”为产品验收原则，覆盖创建、编辑、复盘、提醒、删除、导航、数据库和生命周期。

| 业务环节 | 异常场景 | 当前处理 | 验收证据 |
| --- | --- | --- | --- |
| 创建决定 | 问题为空、选项全为空格、选择索引越界 | 仓储层统一校验，文本保存前清洗 | `ValidationTest` |
| 创建/编辑 | 非法信心值、未来笔记超长、重复点击保存 | 业务校验；保存中禁用按钮；仓储层再次校验 | `ValidationTest`、UI 状态代码 |
| 创建/编辑 | 通知权限拒绝、系统通知总开关关闭、通知频道关闭 | 决定先保存，提醒失败转为可见 warning，可稍后重试 | 模拟器启动与提醒路径、`ReviewReminderScheduler` |
| 编辑 | 记录已被其他流程删除或 ID 无效 | 编辑页不写入孤儿数据，显示不存在错误 | `DecisionDaoTest` |
| 编辑 | 删除/重排候选项后仍保留错误的最终选择 | DAO 以本次插入后的真实 ID 重映射选择 | `saveRewritesChoicesAndMapsSelectedChoice` |
| 复盘 | 决定已删除、结果为空白、满意度越界 | 页面禁用保存，仓储层拒绝无效数据 | `ValidationTest`、`DecisionDaoTest` |
| 复盘 | 下一次复盘日期早于今天 | 仓储/校验层拒绝，今天及未来日期可保存 | `ValidationTest.nextReviewDateCannotBeBeforeToday` |
| 复盘 | 复盘后不再提醒或继续提醒 | 事务内同时保存历史和更新决定状态/日期 | `DecisionDaoTest` |
| 提醒 Worker | 缺少决定 ID、权限在执行期间被撤回、通知关闭 | 无效输入失败；权限/关闭状态安全结束；其他系统异常重试 | `ReviewReminderWorker` 防御式处理 |
| 删除 | 取消 WorkManager 失败 | 不阻断数据库级级联删除；详情页仍可安全返回 | `DecisionRepository.delete` |
| 删除/通知 | 记录已删除但旧通知被点击 | 导航 ID 必须为正数，详情页显示记录不存在保护状态 | 模拟器 deep-link 验收、详情页空状态 |
| 数据库 | 迁移后索引、旧列表编码、损坏枚举值 | 保留 2→7 迁移；旧分隔符仍可读；未知枚举降级 | Android DAO 测试、`DecisionConvertersTest` |
| 生命周期 | 页面销毁/协程取消、调试预置数据通知失败 | 不吞取消信号；调试预置数据异常不影响 App 启动 | 静态检查、`MainActivity`/Repository |
| 性能 | 决定列表增长、重复计算统计、子表查询 | 列表分页、统计结果记忆化、决定/子表索引 | `JournalStatsTest`、Room schema/index 检查 |

## 验收结果

- `./gradlew :app:testDebugUnitTest --no-daemon --no-build-cache`：通过。
- `./gradlew :app:lintDebug --no-daemon --no-build-cache`：通过，无 lint 错误。
- `./test-connected.sh`：Pixel_10a 模拟器 9/9 通过。
- 已验证提醒通知点击进入对应决定详情；无效/已删除 ID 进入保护页，不触发崩溃。

## 产品约定

提醒属于增强能力，不应阻断本地记录保存或删除；当权限、系统开关或频道不可用时，界面提示用户可重试，但不回滚已保存的决定/复盘内容。
