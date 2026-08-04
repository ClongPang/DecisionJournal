# 异常场景检查与验收记录

本轮以“用户仍能完成核心目标、数据不悄悄丢失、系统能力失败不拖垮本地业务”为产品验收原则，覆盖创建、编辑、复盘、提醒、删除、导航、数据库和生命周期。产品侧确认：提醒是增强能力，不能阻断本地保存或删除；工程侧将校验和数据一致性放在 Repository/DAO，而不是只依赖界面禁用状态。

| 业务环节 | 异常场景 | 当前处理 | 验收证据 |
| --- | --- | --- | --- |
| 创建决定 | 问题为空、选项全为空格、选择索引越界 | 仓储层统一校验，文本保存前清洗 | `ValidationTest` |
| 创建决定 | 清洗空选项后最终选择错指向相邻选项 | 先验证原始选中项，再把索引映射到清洗后的列表，绝不静默改变选择 | `ValidationTest.selectingABlankChoice…`、`selectionIsRemapped…` |
| 创建/编辑 | 非法信心值、未来寄语超长、决定日期晚于今天、重复点击保存 | 业务校验；输入限制为 500 字；保存中禁用按钮；仓储层再次校验 | `ValidationTest`、UI 状态代码 |
| 创建/编辑 | 通知权限拒绝、系统通知总开关关闭、通知频道关闭 | 先校验表单再请求可选权限；决定先保存，提醒失败转为可见 warning，可稍后重试 | `CreateDecisionViewModel.validateBeforePermissionRequest`、`ReviewReminderScheduler` |
| 编辑 | 记录已被其他流程删除或 ID 无效 | 编辑页显式展示加载中或“找不到这条决定”，不再呈现可填写的空白编辑表单；DAO 仍拒绝孤儿写入 | `DecisionEditorState`、`DecisionDaoTest` |
| 编辑 | 删除/重排候选项后仍保留错误的最终选择 | DAO 以本次插入后的真实 ID 重映射选择 | `saveRewritesChoicesAndMapsSelectedChoice` |
| 复盘 | 决定已删除、结果为空白、满意度越界 | 页面禁用保存，仓储层拒绝无效数据 | `ValidationTest`、`DecisionDaoTest` |
| 复盘 | 下一次复盘日期早于今天 | 仓储/校验层拒绝，今天及未来日期可保存 | `ValidationTest.nextReviewDateCannotBeBeforeToday` |
| 复盘 | 复盘后不再提醒或继续提醒 | 事务内同时保存历史和更新决定状态/日期 | `DecisionDaoTest` |
| 提醒 Worker | 缺少决定 ID、权限在执行期间被撤回、通知关闭、记录在 Worker 执行期间已删除 | 无效输入失败；权限/关闭状态安全结束；发通知前再次查询本地记录；其他系统异常重试 | `ReviewReminderWorker` 防御式处理 |
| 删除 | 取消 WorkManager 失败 | 不阻断数据库级级联删除；同时在 `finally` 中尝试清除已展示通知 | `DecisionRepository.delete`、`ReviewReminderScheduler.cancel` |
| 删除/通知 | 记录已删除但旧通知被点击、不同 Long ID 的旧通知编号碰撞 | 编辑/删除清除已展示通知；使用稳定 tag 而非 `hashCode`；详情页仍保留失效 ID 保护状态 | `ReviewReminderSchedulerTest`、详情页空状态 |
| 数据库 | 迁移后索引、旧列表编码、损坏枚举值 | 保留 2→7 迁移；旧分隔符仍可读；未知枚举降级 | Android DAO 测试、`DecisionConvertersTest` |
| 生命周期 | 页面销毁/协程取消、首次启动 | 不吞取消信号；Debug/Release 都不再自动写入演示数据，避免空状态和真实数据验收失真 | `MainActivity`、模拟器空状态验收 |
| 性能 | 决定列表增长、重复计算统计、子表查询 | 时间线先展示 10 条、每次追加 20 条；统计结果记忆化；决定/子表索引 | `JournalStatsTest`、Room schema/index 检查 |

## 验收结果

- `./gradlew :app:testDebugUnitTest --no-daemon --no-build-cache`：通过。
- `./gradlew :app:lintDebug --no-daemon --no-build-cache`：通过，无 lint 错误。
- `./test-connected.sh`：已连接 Pixel_10a（API 36）9/9 通过。
- 新增测试覆盖：空选项导致的选择错位、未来决定日期、Long ID 通知碰撞。
- 清除 `com.example.decisionjournal` 模拟器应用数据并重新启动后，首页显示“今天没有待复盘的决定”和“记录一个决定”，确认首启未写入演示数据。

## 产品约定

提醒属于增强能力，不应阻断本地记录保存或删除；当权限、系统开关或频道不可用时，界面提示用户可重试，但不回滚已保存的决定/复盘内容。
