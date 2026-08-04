---
title: 回看决策记录器 MVP 原型对齐与实现拆解规格
version: 1.0
date_created: 2026-08-04
last_updated: 2026-08-04
owner: 个人项目
tags: `design`, `android`, `compose`, `decision-journal`, `prototype-alignment`, `mvp`
---

# Introduction

本规格将 [回看 MVP 产品规格](./spec-product-decision-journal-mvp.md) 的业务要求与 `design/mobile-prototype-v5-ios-complete.png` 的页面结构、视觉语言和交互流程合并为一份可执行拆解。目标是使实现结果同时满足业务验收和原型级 UI 对齐，而不是仅完成可运行的功能骨架。

本规格是实现阶段的设计与交互补充规范。若本规格与基础产品规格存在冲突，数据安全、离线、本地优先和原始决策内容保留要求优先；本规格新增的视觉和记录字段用于完整呈现原型，不得破坏既有 MVP 闭环。

## 1. Purpose & Scope

### 1.1 Purpose

- 将原型中的页面、组件、文案、状态、导航和动效转化为明确的 Android Compose 实现要求。
- 将当前“基础功能型页面”拆解为可逐项开发、测试和验收的工作包。
- 为后续实现提供稳定的路由、数据和 UI 状态契约。

### 1.2 In Scope

- 首页、创建决策向导、候选项记录、写给未来的自己、决策详情、我的决定六类页面。
- 三项底部导航：今天、决定、我的。
- 原型中的最近决策卡片、时间线、统计卡片、候选项分组、未来自我寄语和复盘入口。
- 决策编辑、候选项编辑、复盘日期、复盘记录、本地提醒和离线持久化。
- 浅色 Apple 风格视觉系统、响应式竖屏布局、空状态、错误状态和保存反馈。

### 1.3 Out of Scope

- 云同步、账号、社交分享、AI 分析、自动决策和远程服务。
- 深色模式、平板横屏专属布局和复杂数据图表。
- 用户未主动触发的导出、分享或远程备份。

### 1.4 Assumptions

- 首要设备为 Android 手机竖屏，最小支持宽度为 360dp。
- 目标设备支持 Android API 26 或以上。
- 原型图中的示例数据仅作为视觉参考，不得写入生产数据库作为默认数据。
- 所有用户内容均可在无网络状态下创建、查看、编辑和复盘。

## 2. Definitions

- **决策（Decision）**：用户在某个时间点记录的问题、背景、候选选项和最终选择。
- **候选选项（Choice）**：一个决策下可被比较和选择的方案。
- **利好项（Benefit）**：支持某个候选选项的理由或预期收益。
- **担忧项（Concern）**：某个候选选项的风险、代价或不确定性。
- **未来寄语（Future Note）**：用户写给未来自己的可选文字，不得被 AI 修改或分析。
- **复盘（Review）**：用户在复盘日期之后记录的实际结果和满意度。
- **原型对齐（Prototype Alignment）**：页面信息结构、组件层级、主要交互、视觉 tokens 和状态与指定原型保持一致；不要求使用相同图片像素或设备外框。
- **MVP**：最小可行产品（Minimum Viable Product）。
- **CTA**：主要行动按钮（Call To Action）。

## 3. Requirements, Constraints & Guidelines

### 3.1 Information Architecture

- **REQ-001**：应用必须提供三项底部导航：`今天`、`决定`、`我的`。
- **REQ-002**：`今天`对应首页，展示问候、最近或待复盘决策和创建入口。
- **REQ-003**：`决定`对应全部决策列表，按最近修改时间倒序排列。
- **REQ-004**：`我的`对应个人决策概览，展示已完成数量、最常关注主题、未发生变化的决策数量和决策时间线。
- **REQ-005**：详情、创建和复盘页面必须隐藏底部导航，返回后恢复原导航选中状态。
- **REQ-006**：所有页面必须支持系统返回手势或返回按钮，返回不得丢失已保存数据。

### 3.2 Home Screen

- **REQ-010**：首页顶部显示品牌名“回看”、当前日期和低打扰副标题，例如“给自己一点时间”。
- **REQ-011**：存在待复盘决策时，首页必须优先显示待复盘卡片；卡片包含问题、复盘日期、状态和进入详情的操作。
- **REQ-012**：不存在待复盘决策但存在历史记录时，首页必须显示最近决策卡片，包含问题、记录日期和当前状态。
- **REQ-013**：不存在任何记录时，首页必须显示明确空状态和“记录一个决定”主按钮。
- **REQ-014**：首页主 CTA 固定在内容底部安全区域上方，单手可触达，点击后进入创建向导第一步。
- **REQ-015**：首页卡片必须使用圆角、浅色背景、轻微阴影或边框和足够留白，不得退化为纯文本列表。

### 3.3 Create Decision Wizard

- **REQ-020**：创建流程必须拆分为以下步骤：
  1. 记录问题和背景；
  2. 记录候选选项及其利好项、担忧项；
  3. 写给未来的自己并设置复盘日期；
  4. 保存并进入详情页。
- **REQ-021**：第一步标题为“新的决定”，提供问题输入框和可选背景输入框。
- **REQ-022**：问题去除首尾空白后必须至少包含一个字符；无效时下一步/保存按钮禁用并显示校验提示。
- **REQ-023**：第二步至少需要一个候选选项；每个候选选项必须支持编辑、删除和设为最终选择。
- **REQ-024**：候选选项可以记录零条或多条利好项和担忧项；空行不得保存。
- **REQ-025**：最终选择最多一个；用户可以在保存前保持未选择状态。
- **REQ-026**：删除当前最终选择时，系统必须清空最终选择而不是自动选择其他选项。
- **REQ-027**：第三步标题为“写给未来的自己”，提供多行文本框、字符计数、复盘日期选择和清除日期操作。
- **REQ-028**：未来寄语为可选字段，最大长度为 500 个字符；超出上限时阻止继续。
- **REQ-029**：复盘日期可选择今天或未来日期；选择今天时保存后立即进入待复盘区域，但不得立即弹出通知。
- **REQ-030**：编辑已有决策时，向导必须预填全部原始字段，修改后保留 `createdAt`，更新 `updatedAt`。
- **REQ-031**：重复点击保存不得生成重复决策或重复复盘提醒；保存期间按钮显示进行中状态并暂时禁用。

### 3.4 Decision Detail and Review

- **REQ-040**：详情页顶部显示返回、问题、记录日期和状态。
- **REQ-041**：详情页必须展示“当时的决定”区域，包括问题、背景、候选项、最终选择、利好项、担忧项和未来寄语。
- **REQ-042**：详情页必须展示复盘日期、复盘记录数量和所有复盘记录；记录按创建时间倒序排列。
- **REQ-043**：详情页提供“编辑”“记录结果”“删除”三个明确操作。
- **REQ-044**：删除必须使用二次确认弹窗，取消后原数据不变，确认后删除决策及其所有关联数据和提醒。
- **REQ-045**：复盘页面必须包含结果描述、满意度 1～5 和保存按钮；结果描述去除空白后不得为空。
- **REQ-046**：保存复盘后保留原始决策内容，详情页新增复盘记录并显示保存成功反馈。
- **REQ-047**：复盘日期被修改或清除后，系统必须取消旧提醒；设置新未来日期后必须重新安排唯一提醒。

### 3.5 My Decisions Screen

- **REQ-050**：我的页面顶部显示三个概览卡片：已完成决策数量、最常在意主题、没有发生变化的决策数量。
- **REQ-051**：概览数据必须从本地决策和复盘记录计算，不得使用硬编码示例数字。
- **REQ-052**：页面显示“决策时间线”，每条记录包含问题、日期、状态和进入详情操作。
- **REQ-053**：无数据时显示空状态和创建入口；有数据时不得显示空状态。

### 3.6 Visual System

- **UI-001**：默认主题使用浅色 Apple 风格，背景接近 `#F5F5F7`，主要文字接近 `#1D1D1F`，主 CTA 使用低饱和蓝色，辅助卡片使用浅蓝、浅绿和浅沙色。
- **UI-002**：所有颜色、字号、间距、圆角、阴影、按钮高度和动画时长必须集中定义在主题或设计 token 文件中。
- **UI-003**：页面水平安全边距默认 20dp；卡片圆角 16～24dp；主 CTA 高度不低于 52dp。
- **UI-004**：页面标题、区块标题、正文、辅助说明和按钮文字必须使用统一的 Typography 层级，不得在页面中随意创建字号。
- **UI-005**：卡片必须支持点击态、按压态和禁用态；按钮必须支持正常、禁用、加载和成功后的短暂反馈态。
- **UI-006**：列表项必须使用一致的垂直间距和分隔规则；不可用连续裸 `Text` 代替原型中的卡片层级。
- **UI-007**：底部导航必须包含图标、文字和选中态颜色；导航栏不得遮挡底部 CTA 或输入框。
- **UI-008**：进入页面和保存成功使用轻量动效，时长建议 150～250ms；不得使用影响输入和阅读的持续动画。
- **UI-009**：所有输入框必须具备键盘类型、焦点态、错误态、占位提示和系统返回行为。
- **UI-010**：内容必须支持中文字体回退、系统字体缩放和至少 360dp 宽度；文字不得被裁切。
- **UI-011**：原型中的插画或装饰图只能作为非必需视觉层，必须提供无图片时的可用降级布局。

### 3.7 State, Privacy and Architecture

- **UI-020**：每个主页面必须定义加载、空、正常、错误和保存中状态；状态变化必须可观察且可测试。
- **SEC-001**：决策、候选项、利好项、担忧项、未来寄语和复盘内容不得上传远程服务器。
- **SEC-002**：无网络时必须完成创建、编辑、查看、删除和复盘。
- **CON-001**：继续使用 Kotlin、Jetpack Compose、Material 3、Room、Hilt、Navigation 和 WorkManager。
- **CON-002**：UI 不得直接访问 DAO 或数据库，所有业务操作必须经过 Repository 和 ViewModel。
- **CON-003**：删除只能由用户主动触发，后台提醒任务不得修改或删除业务数据。
- **GUD-001**：文案使用简短、自然、低打扰的中文，避免 AI、智能评分和替用户决策等表达。
- **GUD-002**：原始记录必须可完整回看；任何概览数据不得覆盖或改写原始问题、选项和复盘内容。

## 4. Interfaces & Data Contracts

### 4.1 Decision Contract

```text
Decision {
  id: Long,
  question: String,              // 必填，trim 后长度 >= 1
  context: String?,              // 可选背景
  benefits: List<String>,        // 可选，我在意的事
  concerns: List<String>,        // 可选，我担心的事
  futureNote: String?,           // 可选，trim 后长度 <= 500
  createdAt: Instant,
  updatedAt: Instant,
  reviewDate: LocalDate?,
  status: ACTIVE | REVIEWED | ARCHIVED,
  selectedChoiceId: Long?
}
```

### 4.2 Choice Contract

```text
Choice {
  id: Long,
  decisionId: Long,
  text: String,                  // 必填，trim 后长度 >= 1
  benefits: List<String>,        // 可选，该方案的收益
  concerns: List<String>,        // 可选，该方案的风险
  position: Int                  // 从 0 开始，连续递增
}
```

### 4.3 Review Contract

```text
Review {
  id: Long,
  decisionId: Long,
  createdAt: Instant,
  result: String,                // 必填，trim 后长度 >= 1
  satisfaction: Int?             // null 或 1..5
}
```

### 4.4 Navigation Contract

| Route | 输入 | 页面行为 |
|---|---|---|
| `home` | 无 | 首页、待复盘/最近决策卡片、创建入口 |
| `decisions` | 无 | 全部决策时间线 |
| `mine` | 无 | 概览卡片和决策时间线 |
| `create?decisionId={id}&step={n}` | 可选决策 ID、步骤 | 新建或编辑向导 |
| `decision/{id}` | 决策 ID | 详情、编辑、删除、复盘入口 |
| `review/{decisionId}` | 决策 ID | 创建复盘并返回详情 |

### 4.5 Repository Contract

```text
observeDueDecisions(): Flow<List<DecisionSummary>>
observeRecentDecision(): Flow<DecisionSummary?>
observeAllDecisions(): Flow<List<DecisionSummary>>
observeDecision(id: Long): Flow<DecisionDetail?>
observeDecisionStats(): Flow<DecisionStats>
saveDecision(input: DecisionInput): Result<Long>
deleteDecision(id: Long): Result<Unit>
saveReview(input: ReviewInput): Result<Long>
scheduleReviewReminder(id: Long, date: LocalDate?)
```

### 4.6 UI State Contract

```text
ScreenState<T> = Loading | Empty | Content(T) | Error(message)
SaveState = Idle | Saving | Success | Error(message)
```

页面不得根据数据库是否返回数据以外的隐含条件猜测状态；ViewModel 必须显式暴露状态。

## 5. Acceptance Criteria

### 5.1 Functional Acceptance

- **AC-001**：Given 应用没有任何数据，When 打开首页，Then 显示品牌标题、空状态、创建 CTA 和三项底部导航。
- **AC-002**：Given 用户只填写空格问题，When 进入下一步，Then 操作被阻止并显示问题必填提示。
- **AC-003**：Given 用户填写有效问题但没有候选选项，When 进入下一步，Then 操作被阻止并提示至少添加一个候选项。
- **AC-004**：Given 用户完成问题、候选项和可选背景，When 保存，Then Room 中生成一条决策并进入详情页。
- **AC-005**：Given 一个决策有多个候选选项，When 用户设置最终选择，Then 只有一个选项显示选中态。
- **AC-006**：Given 用户删除最终选择，When 保存，Then `selectedChoiceId` 为空且没有其他选项被自动选中。
- **AC-007**：Given 用户编辑已有决策，When 保存，Then 原 `createdAt` 不变，`updatedAt` 更新，原始关联数据不产生孤儿记录。
- **AC-008**：Given 用户设置未来复盘日期，When 保存，Then 设备本地存在一个对应的唯一提醒任务。
- **AC-009**：Given 用户清除或修改复盘日期，When 保存，Then 旧任务被取消，新日期存在时仅创建一个新任务。
- **AC-010**：Given 当前日期达到复盘日期，When 打开首页，Then 决策显示在待复盘区域。
- **AC-011**：Given 用户提交有效复盘，When 保存，Then 详情页保留原始决策并新增复盘记录。
- **AC-012**：Given 用户提交两条复盘，When 打开详情，Then 按 `createdAt` 倒序展示。
- **AC-013**：Given 用户点击删除但取消确认，When 返回详情，Then 决策、候选项、复盘和提醒仍存在。
- **AC-014**：Given 用户确认删除，When 返回列表，Then 决策和所有关联数据均不可见且提醒已取消。
- **AC-015**：Given 设备处于飞行模式，When 执行创建、编辑、查看、删除和复盘，Then 所有操作成功。
- **AC-016**：Given 用户重启应用，When 打开首页，Then 之前保存的数据和状态仍存在。
- **AC-017**：Given 满意度为 0 或 6，When 保存复盘，Then 保存被阻止并显示 1～5 范围提示。

### 5.2 Prototype Alignment Acceptance

- **AC-020**：首页具有原型的品牌标题、日期/问候、最近或待复盘大卡片、蓝色主 CTA 和三项底部导航层级。
- **AC-021**：创建第一步具有问题卡片、背景卡片、顶部返回/关闭和底部“继续” CTA。
- **AC-022**：创建第二步具有候选选项分组、利好/担忧层级、选中态、拖动或位置调整入口和底部“下一步” CTA。
- **AC-023**：创建第三步具有未来寄语文本卡片、字数计数、复盘日期行和底部“保存” CTA。
- **AC-024**：详情页具有“当时的决定”和“未来再看”两类信息层级，以及复盘结果入口。
- **AC-025**：我的页面具有三个动态概览卡片和决策时间线，卡片、状态点和日期层级与原型一致。
- **AC-026**：所有主页面在 360dp、411dp 和系统字体放大 1.3 倍下不出现文字裁切、按钮越界或底部导航遮挡。
- **AC-027**：UI 设计 token 集中定义；静态检查不得发现页面散落主色、字号和圆角常量。
- **AC-028**：空、加载、错误、保存中和保存成功状态均有可重复触发的 UI 验证用例。

## 6. Test Automation Strategy

- **Test Levels**：Repository/validation 单元测试、Room 内存数据库集成测试、ViewModel 状态测试、Compose UI 测试、连接模拟器端到端测试。
- **Unit Tests**：覆盖空白输入、候选项清洗、最终选择索引、满意度范围、未来寄语长度和重复保存锁定。
- **Room Tests**：覆盖新增、更新、候选项重排、级联删除、复盘排序和数据库重启后读取。
- **ViewModel Tests**：覆盖 Loading、Empty、Content、Error、Saving、Success 状态和导航回调只调用一次。
- **Compose UI Tests**：覆盖空首页、创建向导每一步、禁用 CTA、选项选择、日期清除、删除确认和复盘保存。
- **Screenshot Tests**：为首页、创建三步、详情和我的页面保存浅色基准图；基准图只验证布局和视觉 token，不验证系统状态栏时间。
- **Device Tests**：至少在一个 API 26+ 模拟器执行主流程、通知权限、Room AndroidTest 和应用重启恢复。
- **Test Data Management**：每个测试使用独立内存数据库或临时数据，测试结束清理；不得使用开发者真实数据库。
- **Coverage Requirements**：数据层和业务逻辑行覆盖率目标不低于 80%；原型关键页面每个主流程至少一个 Compose 测试。
- **Performance Testing**：插入 1,000 条决策后，首页和时间线首次展示保持可交互；列表查询不得在主线程执行。
- **CI/CD Integration**：执行 Debug 构建、单元测试、AndroidTest APK 编译和静态检查；有模拟器时执行 connected tests。

## 7. Rationale & Context

原型的核心不是装饰，而是通过“当时的决定—未来的回看—我的时间线”形成低打扰的回顾体验。当前功能骨架已经具备本地数据闭环，但页面信息密度、分组层级和三项导航不足以表达原型的产品结构，因此本规格将视觉要求和数据表现要求同时纳入验收。

候选项的利好项、担忧项和未来寄语属于原型明确呈现的用户内容。它们必须作为本地数据保存，而不是仅作为不可持久化的临时 UI 文本。统计卡片只允许读取和聚合这些原始数据，不得引入 AI 或远程分析。

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**：Android 操作系统 - 提供 Activity、通知权限、本地数据库文件和系统日期能力。
- **EXT-002**：Android Emulator 或实体设备 - 执行连接测试和手工验收。

### Third-Party Services

- **SVC-001**：无远程业务服务。MVP 不上传用户内容。

### Infrastructure Dependencies

- **INF-001**：SQLite/Room 本地数据库 - 持久化决策、候选项、利好项、担忧项、寄语和复盘。
- **INF-002**：WorkManager - 调度未来复盘提醒并在应用重启后恢复任务。
- **INF-003**：Android 本地通知系统 - 展示复盘提醒。

### Data Dependencies

- **DAT-001**：无外部业务数据依赖；统计和时间线全部由本地用户数据派生。

### Technology Platform Dependencies

- **PLT-001**：Android SDK 37，compileSdk/targetSdk 37。
- **PLT-002**：Kotlin、Jetpack Compose、Material 3、Room、Hilt、Navigation 和 WorkManager。
- **PLT-003**：API 26+ 设备支持 `java.time` 日期处理和本地通知能力。

### Compliance Dependencies

- **COM-001**：应用说明必须明确数据仅保存在设备本地。
- **COM-002**：应用不得在未获得用户主动操作时生成导出文件、分享内容或调用远程服务。

## 9. Examples & Edge Cases

### 9.1 Complete Decision Example

```json
{
  "question": "要不要接受那份工作？",
  "context": "薪资更高，但需要搬家。",
  "benefits": ["能否有成长和学习机会", "工作与生活的平衡"],
  "concerns": ["工作压力可能更大", "不确定是否喜欢这份工作"],
  "choices": [
    {
      "text": "接受",
      "benefits": ["进入更大的平台"],
      "concerns": ["通勤时间变长"]
    },
    {
      "text": "拒绝",
      "benefits": ["生活更稳定"],
      "concerns": ["可能错过机会"]
    }
  ],
  "selectedChoice": "接受",
  "futureNote": "希望我能做出不后悔的选择。",
  "reviewDate": "2026-08-15"
}
```

### 9.2 Edge Cases

- 用户只输入空格：按空字符串处理，不得保存。
- 用户删除唯一候选项：下一步和保存按钮禁用。
- 用户删除已选候选项：清空最终选择。
- 用户重复点击“继续”或“保存”：只执行一次写入和一次提醒调度。
- 用户返回上一步：已输入内容保留，返回应用后重建页面不得丢失未提交草稿；若进程被系统杀死，未保存草稿不要求恢复。
- 用户把复盘日期设为今天：进入待复盘区域，不立即弹通知。
- 用户取消通知权限：决策和复盘仍可正常使用，首页必须继续展示待复盘内容。
- 用户删除最后一条决策：`今天`和`我的`页面都显示空状态。
- 用户输入 500 个字符的未来寄语：允许保存；第 501 个字符不得被接受。
- 用户系统字体放大：按钮文字和输入框不得裁切。

## 10. Validation Criteria

实现完成前必须满足：

1. `assembleDebug` 成功并生成 APK。
2. `testDebugUnitTest` 全部通过。
3. `assembleDebugAndroidTest` 成功并生成测试 APK。
4. 已连接模拟器执行 Room AndroidTest，所有测试通过。
5. Compose UI 测试覆盖首页、创建向导、详情、复盘和删除确认。
6. 截图基准检查通过，核心页面与原型的信息层级和视觉 token 对齐。
7. 手工完成 AC-001 至 AC-017，包括离线、通知、重启和删除级联场景。
8. 手工完成 AC-020 至 AC-028，包括三项导航、原型页面层级、不同屏幕宽度和字体放大检查。
9. Room 数据库不存在孤儿候选项、孤儿复盘或重复提醒任务。
10. 源码中不包含未经用户操作的数据上传、AI 调用或自动内容分析。

## 11. Related Specifications / Further Reading

- [基础产品规格](./spec-product-decision-journal-mvp.md)
- [项目 README](../README.md)
- [UI 原型图](../design/mobile-prototype-v5-ios-complete.png)
- [Android App Architecture](https://developer.android.com/topic/architecture)
- [Android Room](https://developer.android.com/training/data-storage/room)
- [Android WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)
