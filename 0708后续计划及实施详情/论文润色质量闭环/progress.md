# 论文润色质量闭环 progress

## 记录规则

每次推进都追加一条记录，格式如下：

```text
## YYYY-MM-DD HH:mm

目标：
- ...

本次完成：
- ...

修改文件：
- ...

测试：
- 已执行：...
- 结果：...
- 未执行：...
- 原因：...

风险：
- ...

下一步：
- ...
```

## 2026-07-09 初始调研

目标：

- 阅读当前项目与 `0708后续计划及实施详情` 中的计划文件。
- 明确普通论文润色质量闭环缺什么、如何补、与文献检索质量闭环是否冲突。

本次完成：

- 确认项目是 Spring Boot 多模块科研 Agent 平台，论文相关核心模块在 `yanban-paper`，API 聚合在 `yanban-api`，前端在 `frontend`。
- 阅读：
  - `0708后续计划及实施详情/后续计划及实施详情.md`
  - `0708后续计划及实施详情/多对话并行协作流程.md`
- 检查论文润色主链路：
  - `PaperTaskService`
  - `PaperOrchestrator`
  - `PaperSectionPolishService`
  - `PaperAssembleService`
  - `PaperTask`
  - `PaperSection`
  - `PaperTaskResponse`
  - `PaperProcessRequest`
  - `PaperPage.vue`
- 确认当前最大缺口：
  - 前端和接口已传 `scoreThreshold / maxRounds / innerMaxAttempts`。
  - `PaperTask` 未持久化这三个参数。
  - `PaperOrchestrator` 仍硬编码 `0.7, 1` 调用分章润色。
  - 当前评分尺度存在 0-100 与 0-1 混用风险。
  - 章节状态、review、diff 已有基础，但失败原因协议和用户可读 diff 不完整。
  - suggestion accept/reject 目前主要是状态变更，尚未形成重新生成产物闭环。

修改文件：

- 新增 `0708后续计划及实施详情/论文润色质量闭环/implementation.md`
- 新增 `0708后续计划及实施详情/论文润色质量闭环/progress.md`

测试：

- 已执行：未执行代码测试。
- 结果：本次仅为文档沉淀和方案设计。
- 未执行：Maven 测试、前端 build、论文润色 eval。
- 原因：尚未修改代码。

风险：

- 当前工作区位于 `main` 且已有大量未提交改动，后续实现前需要先确认分支和改动归属。
- 文献检索质量闭环已有文件夹和疑似相关未提交改动，涉及 suggestions、literature、assemble 的任务需要避免并行冲突。

下一步：

- 建议先开独立 issue：`论文润色参数真实生效`。
- 从最新可控分支开始实现。
- 优先修复参数持久化、评分尺度统一、编排器传参和幂等 key。

## 2026-07-09 issue #94 evaluation baseline

Goal:
- Establish the first repeatable baseline for paper polish must-not-regress checks.

Completed:
- Added `PaperPolishBaselineEvaluationTest`.
- Reused existing fixed LaTeX samples under `yanban-paper/src/test/resources/paper-quality-samples/`.
- Generated paper polish baseline reports under `yanban-paper/target/paper-polish-baseline-eval/`.
- Added the combined runner `docs/evaluation/run-paper-quality-baseline-eval.ps1`.
- Added the dated report `docs/evaluation/reports/paper-quality-baseline-eval-20260709.md`.

Baseline cases:
- `POLISH-EN-001`: English section preserves citation, reference, and section structure.
- `POLISH-ZH-001`: Chinese sample preserves citation, figure, label, ref, graphics, and math spans.
- `POLISH-NEG-001`: unsafe output that drops protected placeholders is rejected and keeps the original text.

Evaluation:
- Command: `docs/evaluation/run-paper-quality-baseline-eval.ps1`
- Paper polish report output:
  - `yanban-paper/target/paper-polish-baseline-eval/report.json`
  - `yanban-paper/target/paper-polish-baseline-eval/report.md`

Scope boundary:
- This baseline validates guardrails and reportability with a deterministic model stub.
- It does not judge full academic writing quality or replace manual review.
## 2026-07-09 Issue #90 统一任务状态和事件体验

目标：
- 将论文润色任务接入第一版统一任务状态和事件约定。

本次完成：
- 复用现有 `paper_tasks`、`paper_task_artifacts`、`agent_tasks`、`agent_task_events`，未新增数据库迁移。
- 论文任务状态常量收口到统一 `AgentTaskStatus`，事件类型收口到 `AgentTaskEventTypes`。
- 统一状态接口新增最近事件字段，并继续返回失败原因、取消原因、partial artifact 计数。
- 论文取消的 `cancelReason` 写入统一 `agent_tasks.cancellation_reason` 镜像，不新增 `paper_tasks` 字段。
- 论文任务工具输出 artifact 的 `artifactStatus`，且 `PARTIAL` artifact 不再标记为 downloadable。

测试：
- 已执行统一任务/事件目标测试命令。
- 结果：通过，44 tests, 0 failures, 0 errors。

风险：
- 本次改变了公共状态响应字段和任务状态机常量引用，但未改变已有状态值。
- 取消原因通过统一镜像表保存，不扩展 `paper_tasks` 表结构。

下一步：
- 后续 UI 可按 `artifactStatus` 和最近事件字段优化展示；本 issue 不改 UI。
