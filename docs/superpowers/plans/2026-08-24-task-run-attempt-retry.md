# 导出任务 Run/Attempt 重试模型实施计划

> **执行要求：** 使用 `superpowers:executing-plans` 按任务逐项执行；每一阶段先补测试，再实现，再运行对应测试。

**目标：** 将“手动重试创建新任务链”重构为“一个逻辑导出任务包含多个 Run，每个 Run 包含多个自动 Attempt”，使任务列表只展示一次、重试次数准确、旧执行不能形成分叉。

**架构：** `export_task` 保存不可变导出快照和当前 Run 的汇总状态；新增 `export_task_run` 保存首次执行及每次手动重新执行；`export_task_attempt` 归属具体 Run。手动重试锁定逻辑任务行，在同一事务中创建 Run、重置任务当前状态并写入 Outbox，不使用 Redis 分布式锁。

**技术栈：** Java 21、Spring Boot、MyBatis、Flyway、MySQL、RabbitMQ、React、TypeScript、TanStack Query、Vitest。

---

### 任务 1：建立 Run 持久化模型并迁移历史数据

**文件：**
- 新建：`backend/src/main/resources/db/migration/V2__task_run_retry_model.sql`
- 新建：`backend/src/main/java/com/example/exportflow/export/domain/ExportRunTrigger.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/domain/ExportTaskRun.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/ExportRunMapper.java`
- 新建：`backend/src/main/resources/mapper/ExportRunMapper.xml`
- 修改：`backend/src/main/java/com/example/exportflow/export/domain/ExportTask.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/domain/ExportTaskAttempt.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/ExportAttemptMapper.java`
- 修改：`backend/src/main/resources/mapper/ExportAttemptMapper.xml`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExportRunMapperIntegrationTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExportAttemptMapperIntegrationTest.java`

- [ ] 新增 `export_task_run`，包含 `task_id/run_no/trigger_type/idempotency_key/request_hash`、执行状态、进度、文件、故障及 worker 时间字段，建立 `(task_id, run_no)` 和 `idempotency_key` 唯一约束。
- [ ] 为 `export_task` 增加 `current_run_id/manual_retry_count/manual_retry_limit/archived`；为 `export_task_attempt` 增加 `run_id`，把唯一约束改为 `(run_id, attempt_no)`。
- [ ] 将现有任务链按根任务回填为 Run；根任务同步到最新 Run 状态，历史子任务标记为归档，避免列表重复和旧 MQ 消息继续抢占。
- [ ] 编写 Mapper 集成测试，验证 Run 顺序、幂等键查询，以及同一任务不同 Run 都能保存 `attempt_no=1`。
- [ ] 运行 `mvn -Dtest=ExportRunMapperIntegrationTest,ExportAttemptMapperIntegrationTest test`，预期全部通过。

### 任务 2：让任务创建和执行状态机同步维护当前 Run

**文件：**
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportTaskCreationService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/TaskClaimService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/TaskFailureService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/RecoveryService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/TaskSuccessService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportExecutionService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/ProgressService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/ExportTaskMapper.java`
- 修改：`backend/src/main/resources/mapper/ExportTaskMapper.xml`
- 测试：`backend/src/test/java/com/example/exportflow/export/TaskClaimServiceTest.java`

- [ ] 创建导出任务时同时插入 `INITIAL` Run 0，将配置中的人工重试上限快照到任务并设置 `current_run_id`。
- [ ] 抢占任务时同时抢占当前 Run，自动次数只在当前 Run 内累计，并将 Attempt 写到 `run_id` 下。
- [ ] 进度、成功、自动重试等待、最终失败和 worker 丢失恢复都在事务中同步更新任务汇总与当前 Run。
- [ ] 所有抢占 SQL 增加 `archived = FALSE` 和当前 Run 校验，确保历史子任务及过期消息不能执行。
- [ ] 运行任务状态机相关测试和 `mvn test`，预期全部通过。

### 任务 3：重写人工重试为同一任务的新 Run

**文件：**
- 修改：`backend/src/main/java/com/example/exportflow/export/application/RetryService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/web/dto/ExportTaskResponse.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/RetryServiceTest.java`

- [ ] 使用 `SELECT ... FOR UPDATE` 锁定逻辑任务；仅当最新状态为 `FAILED`、故障可重试且 `manual_retry_count < manual_retry_limit` 时创建下一 Run。
- [ ] 同一 `Idempotency-Key + taskId` 返回原 Run；同一 Key 用于不同任务返回 `IDEMPOTENCY_KEY_REUSED`。
- [ ] 创建 `MANUAL_RETRY` Run 后将任务重置为 `PENDING/QUEUED`、当前自动次数归零、清除旧文件和故障字段，再在同一事务写 Outbox。
- [ ] 测试两次重试上限、幂等回放、成功/处理中不可重试和并发下只产生连续唯一 Run。
- [ ] 运行 `mvn -Dtest=RetryServiceTest test`，预期全部通过。

### 任务 4：调整查询契约和实时事件

**文件：**
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportTaskQueryService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/web/dto/TaskSummaryResponse.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/web/dto/TaskDetailResponse.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/TaskProgressEvent.java`
- 修改：`backend/src/main/resources/mapper/ExportTaskMapper.xml`
- 修改：`docs/API.md`

- [ ] 列表只查询 `archived = FALSE` 的逻辑任务，响应增加 `currentRunNo/manualRetryCount/manualRetryLimit/canManualRetry`。
- [ ] 详情响应改为 `runs[]`，每个 Run 内含 `attempts[]`，保留快照和下载统计。
- [ ] SSE 事件携带 `currentRunNo`，让前端在新 Run 开始时允许进度从旧失败值重置。
- [ ] 更新 API 文档中的人工重试语义、响应字段和状态约束。
- [ ] 运行 `mvn test`，预期全部通过。

### 任务 5：更新任务页为单任务多 Run 展示

**文件：**
- 修改：`frontend/src/api/types.ts`
- 修改：`frontend/src/api/client.ts`
- 修改：`frontend/src/pages/tasks/TasksPage.tsx`
- 修改：`frontend/src/pages/tasks/TaskDetailDrawer.tsx`
- 修改：`frontend/src/pages/tasks/taskPresentation.ts`
- 修改：`frontend/src/pages/tasks/taskPresentation.test.ts`

- [ ] 重试按钮只读取后端 `canManualRetry`，删除前端硬编码的 `/2` 判断。
- [ ] 重试成功后保持同一个任务 ID，刷新列表和详情，并提示“已发起第 N 次手动重试”。
- [ ] 详情按 Run 分组展示首次执行、人工重试和每个 Run 下的自动 Attempt，不再显示任务链。
- [ ] 新 Run 的 SSE 事件到达时重置进度，当前 Run 内仍阻止乱序事件导致进度倒退。
- [ ] 运行 `pnpm.cmd test --run`、`pnpm.cmd lint` 和 `pnpm.cmd build`，预期全部通过。

### 任务 6：全量验证与文档收尾

**文件：**
- 修改：`README.md`
- 修改：`docs/TESTING.md`

- [ ] 更新架构说明和重试术语：Retry 表示自动重试，Manual Retry/Redrive 表示同一快照的新 Run，重新导出表示新任务。
- [ ] 执行后端全量测试、前端测试/静态检查/构建。
- [ ] 使用现有本地服务验证：任务失败后列表仍为一条、最多两次手动重试、旧 Run 只能查看、成功后立即出现下载入口。
