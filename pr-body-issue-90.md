## Background

Closes #90.

Issue #90 asks for the first unified task status and event experience, limited to literature search tasks and paper polish tasks.

## Changes

- Added shared `AgentTaskStatus` and `AgentTaskEventTypes` constants for the issue #90 task paths.
- Extended `GET /api/v1/tasks/{taskId}/status` with `errorCode` and latest event fields.
- Kept existing status values and reused `agent_tasks`, `agent_task_events`, `paper_tasks`, and `literature_search_tasks`.
- Propagated paper cancel reasons into the unified `agent_tasks.cancellation_reason` mirror.
- Exposed paper artifact `artifactStatus` in tool output and only marks completed artifacts as downloadable.
- Marks literature search results as partial when results exist with recorded source failures.
- Updated frontend API typings only; no UI redesign.
- Added targeted tests and progress notes for the literature search and paper polish loops.

## Non-goals

- No Project task center changes.
- No sandbox or full Agent task system rewrite.
- No UI redesign.
- No new database migration.

## User-visible Changes

- Unified status responses now include recent task event context and `errorCode`.
- Cancelled paper tasks can surface the cancellation reason through the unified status path.
- Partial paper artifacts are no longer presented as downloadable final artifacts by tool output.
- Literature search tool/status output can distinguish partial results caused by source failures.

## Tests

```powershell
mvn -pl yanban-api -am "-Dtest=AgentTaskRegistryTest,AgentTaskEventRecorderTest,LiteratureSearchTaskServiceTest,LiteratureSearchTaskUnifiedTaskMirrorTest,AgentTaskServiceTest,TaskControlServiceTest,AgentTaskEventControllerTest,TaskControlControllerIntegrationTest,LiteratureSearchTaskToolExecutorTest,PaperTaskToolExecutorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: passed. 44 tests, 0 failures, 0 errors, 0 skipped.

## Risk and Rollback

- Public API response shape changed for the unified task status endpoint.
- Task status machine constants are now centralized, while stored status strings stay unchanged.
- No database migration was added; rollback is reverting this commit.
