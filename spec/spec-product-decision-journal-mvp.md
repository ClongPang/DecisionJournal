---
title: 回看个人决策记录器 MVP 产品规格
version: 1.0
date_created: 2026-08-03
last_updated: 2026-08-03
owner: 个人项目
tags: `product`, `android`, `compose`, `room`, `decision-journal`, `mvp`
---

# Introduction

本规格定义“回看”个人决策记录器 Android MVP 的产品行为、数据结构、页面流程、隐私边界和验收标准。产品用于帮助单个用户记录重要决策、保存当时的思考和选择，并在未来复盘结果。MVP 必须支持完全离线使用，不依赖账号、服务器或人工智能服务。

## 1. Purpose & Scope

### 1.1 Purpose

为后续 UI、数据库、业务逻辑和测试实现提供唯一、明确、可执行的产品依据。

### 1.2 In Scope

- 创建、查看、编辑和删除决策。
- 为一个决策记录至少一个候选选项，并标记最终选择。
- 设置复盘日期。
- 记录一次或多次复盘结果。
- 在首页展示待复盘事项。
- 在“我的决策”页面查看历史记录。
- 本地通知提醒复盘。
- 全部数据保存在设备本地。

### 1.3 Out of Scope

- 用户注册、登录和云同步。
- 社交分享和多人协作。
- 自动替用户做决策。
- AI 总结、预测、评分或主动建议。
- 复杂统计、推荐系统和跨设备同步。

### 1.4 Assumptions

- MVP 首先适配 Android 手机竖屏。
- UI 遵循 `design/mobile-prototype-v5-ios-complete.png` 的 Apple 风格：克制、清晰、留白充足、强调层级和动效细节。
- 用户可以在创建决策时填写问题、背景、候选选项、最终选择和复盘日期。
- 一个决策可以有一个或多个候选选项；没有候选选项时不允许提交。
- 一个决策可以拥有多条复盘记录。

## 2. Definitions

- **决策（Decision）**：用户在某个时间点需要做出的选择及其背景记录。
- **候选选项（Choice）**：决策中可供选择的方案。
- **最终选择（Selected Choice）**：用户实际采用的候选选项。
- **复盘（Review）**：用户在决策执行一段时间后对结果和满意度的记录。
- **待复盘（Due）**：当前日期已达到或超过复盘日期，且尚未完成最近一次复盘的决策。
- **MVP**：最小可行产品（Minimum Viable Product）。
- **Room**：Android 本地 SQLite 数据库抽象层。
- **AI**：人工智能服务。本 MVP 不集成 AI。

## 3. Requirements, Constraints & Guidelines

### 3.1 Functional Requirements

- **REQ-001**：系统必须允许用户创建决策，至少填写决策问题和一个候选选项。
- **REQ-002**：系统必须支持为决策填写可选背景、创建时间和复盘日期。
- **REQ-003**：系统必须允许用户新增、编辑和删除候选选项。
- **REQ-004**：系统必须允许用户将一个候选选项标记为最终选择。
- **REQ-005**：一个决策最多只能有一个最终选择；提交完成前可以没有最终选择。
- **REQ-006**：系统必须将创建和修改后的数据持久化到本地数据库。
- **REQ-007**：首页必须展示待复盘决策；没有待复盘决策时展示明确的空状态和创建入口。
- **REQ-008**：系统必须提供决策列表，并按最近修改时间倒序排列。
- **REQ-009**：系统必须提供决策详情页，展示问题、背景、候选选项、最终选择、状态和复盘记录。
- **REQ-010**：系统必须允许用户删除决策，并在删除前进行二次确认。
- **REQ-011**：系统必须允许用户创建复盘记录，包含复盘时间、结果描述和满意度。
- **REQ-012**：满意度必须使用 1 至 5 的整数表示，可以不填写。
- **REQ-013**：系统必须支持一条决策拥有多条复盘记录，并按复盘时间倒序展示。
- **REQ-014**：系统必须在设置了未来复盘日期时安排本地提醒。
- **REQ-015**：用户修改或删除复盘日期后，系统必须取消或重新安排对应提醒。

### 3.2 Privacy and Security Requirements

- **SEC-001**：MVP 不得上传决策内容、复盘内容或设备标识到远程服务器。
- **SEC-002**：MVP 不得要求用户注册账号或提供手机号、邮箱等身份信息。
- **SEC-003**：应用必须在无网络连接时完成创建、查看、编辑和复盘操作。
- **SEC-004**：生物识别锁属于后续版本功能，不得阻塞 MVP 主流程。
- **SEC-005**：导出功能属于后续版本功能；MVP 不得在未明确用户操作时生成或分享数据文件。

### 3.3 UI/UX Requirements

- **UI-001**：主要操作必须在手机竖屏单手使用场景下可发现和可操作。
- **UI-002**：创建决策页面必须提供清晰的键盘输入、返回和保存行为。
- **UI-003**：保存按钮在必填内容不完整时必须禁用，或显示明确的校验提示。
- **UI-004**：删除操作必须使用确认弹窗，避免误删。
- **UI-005**：加载、空状态、错误和保存成功状态必须有明确反馈。
- **UI-006**：颜色、字号、间距、圆角和动效应集中定义，避免在页面中散落硬编码。
- **UI-007**：默认主题优先实现原型中的浅色 Apple 风格；深色模式不属于 MVP 必须项。

### 3.4 Constraints

- **CON-001**：客户端平台为 Android，使用现有 Kotlin、Jetpack Compose、Material 3、Room、Hilt、Navigation 和 WorkManager 技术栈。
- **CON-002**：应用必须支持 compileSdk 37 和 targetSdk 37。
- **CON-003**：数据访问必须通过 Repository 和 DAO，UI 不得直接访问数据库。
- **CON-004**：业务状态必须通过单向数据流暴露给 Compose UI。
- **CON-005**：删除数据必须是用户主动触发的可确认操作，不得通过后台任务自动删除。

### 3.5 Guidelines

- **GUD-001**：MVP 文案使用简短、自然、低打扰的中文，避免“AI 助手”“智能分析”等表达。
- **GUD-002**：AI 若在后续版本加入，只能作为用户主动调用的总结工具，不得替用户决定或修改原始记录。
- **GUD-003**：优先完成“创建 → 保存 → 首页展示 → 详情 → 复盘”的闭环，再实现统计和高级设置。

## 4. Interfaces & Data Contracts

### 4.1 Decision Data Contract

```text
Decision {
  id: Long,                 // 本地唯一标识
  question: String,         // 必填，去除首尾空白后长度 >= 1
  context: String?,         // 可选背景
  createdAt: Instant,
  updatedAt: Instant,
  reviewDate: LocalDate?,   // 可选复盘日期
  status: DecisionStatus,   // ACTIVE, REVIEWED, ARCHIVED
  selectedChoiceId: Long?   // 可选最终选择
}
```

### 4.2 Choice Data Contract

```text
Choice {
  id: Long,
  decisionId: Long,
  text: String,             // 必填，去除首尾空白后长度 >= 1
  position: Int             // 决策内排序，从 0 开始
}
```

### 4.3 Review Data Contract

```text
Review {
  id: Long,
  decisionId: Long,
  createdAt: Instant,
  result: String,           // 必填，去除首尾空白后长度 >= 1
  satisfaction: Int?        // 可选，范围 1..5
}
```

### 4.4 Navigation Contract

| Route | 输入 | 输出行为 |
|---|---|---|
| `home` | 无 | 展示待复盘决策和创建入口 |
| `create` | 可选 `decisionId` | 创建或编辑决策，保存后返回详情或首页 |
| `decisions` | 无 | 展示全部决策 |
| `decision/{id}` | 决策 ID | 展示详情、编辑、删除和复盘入口 |
| `review/{decisionId}` | 决策 ID | 创建复盘并返回详情 |

### 4.5 Repository Contract

Repository 至少提供以下行为：

```text
observeDueDecisions(): Flow<List<DecisionWithChoices>>
observeAllDecisions(): Flow<List<DecisionWithChoices>>
observeDecision(id: Long): Flow<DecisionDetail?>
saveDecision(input: DecisionInput): Result<Long>
deleteDecision(id: Long): Result<Unit>
saveReview(input: ReviewInput): Result<Long>
```

## 5. Acceptance Criteria

- **AC-001**：Given 用户打开空的应用，When 进入首页，Then 页面展示空状态和“记录一个决定”入口。
- **AC-002**：Given 用户未填写决策问题，When 查看保存按钮，Then 保存按钮不可用或显示必填提示。
- **AC-003**：Given 用户填写问题但没有候选选项，When 尝试保存，Then 系统阻止保存并提示至少添加一个选项。
- **AC-004**：Given 用户填写有效问题和候选选项，When 点击保存，Then 数据写入 Room，且在决策列表中可见。
- **AC-005**：Given 一个决策有多个候选选项，When 用户选择其中一个，Then 只有该选项显示为最终选择。
- **AC-006**：Given 用户设置未来复盘日期，When 保存决策，Then 系统安排对应的本地提醒。
- **AC-007**：Given 当前日期达到复盘日期，When 用户打开首页，Then 对应决策出现在待复盘区域。
- **AC-008**：Given 用户提交有效复盘结果，When 保存复盘，Then 详情页展示该复盘，并保留原始决策内容。
- **AC-009**：Given 用户存在多条复盘，When 打开详情页，Then 复盘按时间倒序展示。
- **AC-010**：Given 用户点击删除，When 未完成确认，Then 决策仍然存在；完成确认后，决策及其关联数据被删除。
- **AC-011**：Given 设备处于离线状态，When 用户执行创建、查看、编辑或复盘，Then 操作仍然成功。
- **AC-012**：Given 用户重新启动应用，When 打开首页，Then 之前保存的数据仍然存在。
- **AC-013**：Given 用户输入满意度 0 或 6，When 提交复盘，Then 系统拒绝该值并提示范围为 1 至 5。

## 6. Test Automation Strategy

- **Test Levels**：Repository/Use Case 单元测试、Room 数据库集成测试、ViewModel 测试、Compose UI 测试、模拟器端到端测试。
- **Frameworks**：JUnit、AndroidX Test、Room in-memory database、Compose UI Test、Turbine 或等效 Flow 测试工具。
- **Test Data Management**：每个测试使用独立内存数据库或临时数据集；测试结束后清理数据，不依赖开发者真实数据库。
- **CI/CD Integration**：后续接入 GitHub Actions，至少执行 debug 编译、单元测试和静态检查。
- **Coverage Requirements**：MVP 业务逻辑和数据层行覆盖率目标不低于 80%；UI 覆盖关键主流程即可。
- **Performance Testing**：本地数据库在 1,000 条决策记录规模下，列表首次加载和查询应保持可交互；MVP 不要求压力测试。

## 7. Rationale & Context

产品的核心价值是保留“当时为什么这样选”，而不是在事后替用户给出正确答案。因此 MVP 首先保证原始记录的完整性、可回看性和低打扰体验。

采用本地优先架构可以降低个人项目的开发复杂度，并保护敏感的个人决策内容。采用 Room 统一数据访问，便于后续加入复盘、标签、导出和数据库迁移。AI 被明确排除在 MVP 之外，以避免产品变成主动建议工具，也避免隐私和云端依赖过早进入核心流程。

## 8. Dependencies & External Integrations

### External Systems

- **EXT-001**：Android 操作系统 - 提供应用运行环境、本地存储、通知和可选生物识别能力。

### Third-Party Services

- **SVC-001**：无。MVP 不依赖远程第三方服务或云端 API。

### Infrastructure Dependencies

- **INF-001**：设备本地文件系统和 SQLite 数据库 - 持久化决策、选项和复盘记录。
- **INF-002**：Android 后台任务调度能力 - 触发复盘提醒。

### Data Dependencies

- **DAT-001**：无外部数据依赖。所有业务数据由用户在应用内创建。

### Technology Platform Dependencies

- **PLT-001**：Android SDK 37 - 编译和目标平台。
- **PLT-002**：Kotlin、Jetpack Compose、Material 3、Room、Hilt、Navigation 和 WorkManager - 应用架构基础能力。

### Compliance Dependencies

- **COM-001**：应用隐私说明必须明确数据仅保存在设备本地，除非未来版本新增并明确启用云端服务。

## 9. Examples & Edge Cases

### 9.1 Valid Decision

```json
{
  "question": "是否接受新的工作机会？",
  "context": "薪资更高，但需要搬家。",
  "choices": ["接受", "拒绝", "继续沟通后决定"],
  "selectedChoice": "继续沟通后决定",
  "reviewDate": "2026-09-03"
}
```

### 9.2 Edge Cases

- 用户只输入空格：按空字符串处理，不得保存。
- 用户删除唯一候选选项：如果没有其他选项，不得保存决策。
- 用户删除已被选中的候选选项：清空 `selectedChoiceId`，并要求用户重新选择或保持未选择状态。
- 用户把复盘日期改为今天：保存后立即将决策标记为待复盘，但不要求立即弹出通知。
- 用户重复提交保存按钮：必须避免生成重复决策。
- 用户删除决策：同时删除关联候选选项和复盘记录。
- 系统重启或应用被杀死：已安排的提醒应在系统允许的范围内恢复或重新安排。

## 10. Validation Criteria

规格实现完成前必须满足以下条件：

1. Android 项目可以使用 Gradle Wrapper 成功构建 debug APK。
2. Room 数据库能够保存、读取、更新和删除决策、候选选项与复盘记录。
3. 创建、列表、详情和复盘主流程可以在 Android 模拟器上完成。
4. 关键验收标准 AC-001 至 AC-013 均有自动化测试或可重复的手工验证记录。
5. 应用在无网络状态下仍可完成 MVP 全部核心操作。
6. 代码中不存在未处理的必填字段、非法满意度或关联数据孤儿记录。
7. MVP 不包含未经用户主动触发的 AI 调用、数据上传或内容分析。

## 11. Related Specifications / Further Reading

- [项目 README](/Users/pclong/projects_dev/vibe_projects/decision-journal/README.md)
- [Android Developers: App architecture](https://developer.android.com/topic/architecture)
- [Android Developers: Room](https://developer.android.com/training/data-storage/room)
- [Android Developers: WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [UI 原型图](/Users/pclong/projects_dev/vibe_projects/decision-journal/design/mobile-prototype-v5-ios-complete.png)
