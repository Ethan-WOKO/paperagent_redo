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
