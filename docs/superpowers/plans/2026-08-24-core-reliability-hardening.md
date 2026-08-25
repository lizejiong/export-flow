# 导出核心链路可靠性加固实施计划

> **给 agentic workers：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。

**目标：** 修复导出任务从创建、投递、执行、恢复、文件发布到 SSE 展示之间的状态一致性、并发幂等、故障恢复、异常安全和验证缺口。

**架构：** MySQL 继续作为唯一事实源，Task/Run/Attempt 状态变化保持同事务提交；事务内只发布轻量领域事件，Redis/SSE 在 `AFTER_COMMIT` 阶段读取数据库最新状态并广播。执行期间增加独立心跳，文件系统通过临时文件、最终文件年龄阈值和数据库引用对账处理不可事务化窗口。trace 使用现有 Outbox JSON 和 Rabbit 消息传播，不新增数据库列。

**技术栈：** Java 21、Spring Boot、Spring Transaction、MyBatis、RabbitMQ、Redis、Apache POI、React、TanStack Query、Vitest、Maven。

---

### 任务 1：建立提交后任务事件模型

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/TaskChangedEvent.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/TaskEventPublisher.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/TaskProgressEvent.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/ProgressService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportTaskCreationService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/TaskSuccessService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/TaskFailureService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/RecoveryService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/RetryService.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/TaskEventPublisherTest.java`

- [ ] **步骤 1：编写提交后事件失败测试**

覆盖事务回滚不广播、提交后从 Mapper 重读最新 `version/status/stage`、Redis 异常不影响数据库提交三个场景。事件对象固定为：

```java
public record TaskChangedEvent(long taskId, String eventType, String requestId) {
}
```

- [ ] **步骤 2：运行定向测试，确认当前缺少实现**

Run: `mvn -Dtest=TaskEventPublisherTest test`

预期：编译失败，提示 `TaskChangedEvent` 或 `TaskEventPublisher` 不存在。

- [ ] **步骤 3：实现 AFTER_COMMIT Redis 广播**

`TaskEventPublisher` 使用同步事务监听器：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void publish(TaskChangedEvent changed) {
    ExportTask task = taskMapper.findById(changed.taskId());
    if (task == null || task.isArchived()) return;
    redis.convertAndSend(ProgressService.CHANNEL,
            objectMapper.writeValueAsString(TaskProgressEvent.from(task, changed.eventType(), changed.requestId())));
}
```

`TaskProgressEvent` 增加 `version` 和 `requestId`；所有持久状态变化只在事务内调用 `ApplicationEventPublisher.publishEvent(...)`，不直接操作 Redis。

- [ ] **步骤 4：移除事务提交前和未持久化进度广播**

`ProgressService.persist` 完成 Task/Run/Attempt 更新后发布 `task.progress` 领域事件。删除 `publishOnly` 路径，最多按持久化节流间隔广播一次，保证 SSE 不领先于 MySQL。

- [ ] **步骤 5：补齐全部状态事件**

创建使用 `task.created`；手动重试、自动重试等待和恢复使用 `task.retrying`；成功、失败、文件缺失、过期分别使用 `task.succeeded`、`task.failed`、`task.failed`、`task.expired`。

- [ ] **步骤 6：运行事件测试**

Run: `mvn -Dtest=TaskEventPublisherTest test`

预期：全部通过，回滚事务没有 Redis 消息，提交事务消息版本等于数据库版本。

### 任务 2：收紧 Task/Run/Attempt 状态原子性并增加独立心跳

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/TaskHeartbeatService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/ExecutionHeartbeat.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportExecutionService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/TaskSuccessService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/TaskFailureService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/RecoveryService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/ExportRunMapper.java`
- 修改：`backend/src/main/resources/mapper/ExportRunMapper.xml`
- 测试：`backend/src/test/java/com/example/exportflow/export/TaskStateConsistencyTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExecutionHeartbeatTest.java`

- [ ] **步骤 1：编写失败测试**

验证 Task CAS 返回 0 时视为旧执行被替代且不修改 Run/Attempt；Task 更新成功但 Run 或 Attempt 更新数不是 1 时事务回滚；长时间没有批次回调时独立心跳仍持续更新三层记录。

- [ ] **步骤 2：运行测试并确认失败**

Run: `mvn -Dtest=TaskStateConsistencyTest,ExecutionHeartbeatTest test`

- [ ] **步骤 3：统一更新顺序**

每个执行状态事务使用以下顺序：

```java
int taskUpdated = taskMapper.transition(...);
if (taskUpdated == 0) return false;
requireOne(runMapper.transition(...), "current run");
requireOne(attemptMapper.transition(...), "current attempt");
events.publishEvent(...);
return true;
```

Task CAS=0 是正常 fencing 结果；Task 成功而 Run/Attempt 失败才是数据不一致并抛异常。

- [ ] **步骤 4：实现共享独立心跳**

`ExecutionHeartbeat` 使用一个 Spring 管理的具名调度线程池，每 `properties.heartbeatInterval()` 调用 `TaskHeartbeatService.heartbeat`。心跳事务同时校验 Task token、Run token 和 Attempt token；执行结束必须取消 future。

- [ ] **步骤 5：让执行器覆盖 workbook.write 和文件移动阶段**

在 `ExportExecutionService.execute` 开始后立即启动心跳，在 `finally` 中关闭；批次进度仍负责业务进度，独立心跳只维护 lease/fencing。

- [ ] **步骤 6：运行状态与心跳测试**

Run: `mvn -Dtest=TaskStateConsistencyTest,ExecutionHeartbeatTest test`

预期：全部通过，无 Task/Run/Attempt 分裂状态。

### 任务 3：修复并发重试幂等窗口

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/RetryTransactionService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/RetryService.java`
- 修改：`backend/src/test/java/com/example/exportflow/export/RetryServiceTest.java`
- 新建：`backend/src/test/java/com/example/exportflow/export/RetryConcurrencyIntegrationTest.java`

- [ ] **步骤 1：增加并发相同 key 测试**

两个线程同时对同一失败 Task 使用同一 `Idempotency-Key`；断言只新增一个 Run，两个响应指向同一 Task，其中一个标记为 replay。

- [ ] **步骤 2：运行测试确认当前行为失败**

Run: `mvn -Dtest=RetryServiceTest,RetryConcurrencyIntegrationTest test`

- [ ] **步骤 3：分离事务执行与冲突回读**

`RetryTransactionService` 在事务内锁 Task、再次查询 key、校验状态并插入 Run/Outbox。外层 `RetryService` 捕获 `DuplicateKeyException` 时，确保原事务已经回滚，再回读：

```java
try {
    return transactionService.createRun(taskId, key, requestHash);
} catch (DuplicateKeyException duplicate) {
    ExportTaskRun winner = runMapper.findByIdempotencyKey(key);
    if (winner == null) throw duplicate;
    return replayOrConflict(winner, taskId, requestHash);
}
```

- [ ] **步骤 4：运行重试测试**

Run: `mvn -Dtest=RetryServiceTest,RetryConcurrencyIntegrationTest test`

预期：并发请求只创建一个 Run 和一个 Outbox 事件。

### 任务 4：修复文件状态事务、路径安全和孤儿文件

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/FileLifecycleService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/DownloadService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/FileCleanupScheduler.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/storage/LocalFileStorage.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/ExportTaskMapper.java`
- 修改：`backend/src/main/resources/mapper/ExportTaskMapper.xml`
- 修改：`backend/src/main/resources/mapper/ExportRunMapper.xml`
- 测试：`backend/src/test/java/com/example/exportflow/export/FileLifecycleServiceTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/LocalFileStorageTest.java`

- [ ] **步骤 1：编写文件缺失提交和孤儿清理失败测试**

验证下载发现缺失后 Task/Run 确实提交为 FAILED；Task 更新需 `version + 1`；最终文件只有在超过宽限期且 Task/Run 均无引用时才能删除；符号链接或越界路径必须拒绝。

- [ ] **步骤 2：运行测试确认失败**

Run: `mvn -Dtest=FileLifecycleServiceTest,LocalFileStorageTest test`

- [ ] **步骤 3：拆出可提交的文件状态事务**

移除 `DownloadService.get` 外层事务。文件不存在时调用独立 `@Transactional` 的 `FileLifecycleService.markMissing(...)`，正常返回后再抛 404，避免 RuntimeException 回滚修复状态。

- [ ] **步骤 4：使过期状态原子更新**

清理器负责物理删除；`FileLifecycleService.markExpired` 在一个事务内校验 Task/Run 更新数并发布 `task.expired`。任何 CAS=0 表示状态已经变化，不覆盖新状态。

- [ ] **步骤 5：增加最终文件对账**

Mapper 增加 Task 与 Run 的总引用计数：

```sql
SELECT
  (SELECT COUNT(*) FROM export_task WHERE file_path = #{filePath}) +
  (SELECT COUNT(*) FROM export_task_run WHERE file_path = #{filePath})
```

扫描 `files` 目录中超过两小时的普通 `.xlsx`；引用数为 0 才删除。

- [ ] **步骤 6：强化路径解析**

根目录初始化后保存 `toRealPath()`；解析、读取、删除和扫描使用 `NOFOLLOW_LINKS`，逐段拒绝符号链接，并继续保留目录边界检查。

- [ ] **步骤 7：运行文件测试**

Run: `mvn -Dtest=FileLifecycleServiceTest,LocalFileStorageTest test`

### 任务 5：安全异常分类、日志与跨异步 trace

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/common/api/RequestIdContext.java`
- 修改：`backend/src/main/java/com/example/exportflow/common/api/RequestIdFilter.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/ExportTaskMessage.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/ExportTaskListener.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/OutboxPublisher.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportFailureClassifier.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportExecutionService.java`
- 修改：所有创建 Outbox payload 的 application service
- 修改：`backend/src/main/resources/application.yml`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExportFailureClassifierTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/MessageTracePropagationTest.java`

- [ ] **步骤 1：编写安全异常与 trace 失败测试**

断言 SQL/路径等原始异常信息不会进入用户可见 failure message；日志调用保留异常对象；HTTP requestId 经 Outbox JSON、Rabbit message、Listener MDC 到状态事件保持一致；旧消息缺少 schema/requestId 时安全兼容。

- [ ] **步骤 2：运行测试确认失败**

Run: `mvn -Dtest=ExportFailureClassifierTest,MessageTracePropagationTest test`

- [ ] **步骤 3：收敛异常边界**

执行器改为 `catch (Exception exception)`；分类器对白名单业务错误、数据错误、IO/磁盘错误和未知错误返回固定安全文案，`TaskFailureService` 返回是否成功落库，执行器使用：

```java
log.error("export_failed requestId={} taskId={} token={}",
        RequestIdContext.currentOrCreate(), task.getId(), task.getExecutionToken(), exception);
```

- [ ] **步骤 4：扩展兼容消息契约**

```java
public record ExportTaskMessage(
        Integer schemaVersion,
        String eventId,
        long taskId,
        String eventType,
        String requestId) {
    public boolean isSupported() {
        return (schemaVersion == null || schemaVersion == 1) && taskId > 0 && eventId != null;
    }
}
```

新消息写 `schemaVersion=1`；Listener 对不支持消息 reject 且不 requeue，对合法消息在 try-with-resources MDC scope 中执行。

- [ ] **步骤 5：强化 Outbox confirm**

启用 Rabbit mandatory；只有 publisher confirm ACK 且 `correlation.getReturned() == null` 才标记已发布。延迟路由使用明确 event type switch，不使用字符串 `contains`。

- [ ] **步骤 6：运行异常与消息测试**

Run: `mvn -Dtest=ExportFailureClassifierTest,MessageTracePropagationTest test`

### 任务 6：统一 Clock、分页和生效配置

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/common/config/TimeConfiguration.java`
- 修改：所有直接使用 `LocalDateTime.now(ZoneId)` 的核心导出服务
- 修改：`backend/src/main/java/com/example/exportflow/order/application/CountQuerySupport.java`
- 修改：`backend/src/main/java/com/example/exportflow/order/application/OrderService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportTaskQueryService.java`
- 修改：`backend/src/main/java/com/example/exportflow/common/error/GlobalExceptionHandler.java`
- 修改：`backend/src/main/resources/application.yml`
- 修改：`backend/src/main/resources/mapper/OrderMapper.xml`
- 修改：Mapper offset 参数类型
- 测试：相关 service 与 exception handler 测试

- [ ] **步骤 1：增加固定时钟、分页边界和类型错误测试**

覆盖固定 Clock 下的创建/过期/恢复时间；page=0、page=10001、非法 pageSize 和非法枚举均返回 400；offset 使用 long 不溢出。

- [ ] **步骤 2：运行测试确认失败**

Run: `mvn test -Dtest=OrderServiceTest,GlobalExceptionHandlerTest,RetryServiceTest`

- [ ] **步骤 3：注入行为保持一致的 Clock**

```java
@Bean
Clock applicationClock() {
    return Clock.system(ZoneId.of("Asia/Shanghai"));
}
```

核心服务改用 `LocalDateTime.now(clock)`，测试注入 `Clock.fixed(...)`；不改变现有 DATETIME 和 API 时区语义。

- [ ] **步骤 4：让 count timeout 和 heartbeat 配置真正生效**

在 MyBatis variables 中暴露 `countTimeoutSeconds`，Mapper 使用 `timeout="${countTimeoutSeconds}"`；`CountQuerySupport.map` 接收秒数生成文案；进度节流和独立心跳统一使用 `properties.heartbeatInterval()`。

- [ ] **步骤 5：严格分页与类型错误映射**

页码只允许 1..10000，pageSize 只允许 20/50/100，非法值抛 `VALIDATION_ERROR`；offset 改为 long；异常处理器显式处理 `MethodArgumentTypeMismatchException` 和缺失参数异常为 400。

- [ ] **步骤 6：运行配置与边界测试**

Run: `mvn test -Dtest=OrderServiceTest,GlobalExceptionHandlerTest,RetryServiceTest`

### 任务 7：前端 version 合并和低频 REST 对账

**文件：**
- 修改：`frontend/src/api/types.ts`
- 修改：`frontend/src/pages/tasks/taskPresentation.ts`
- 修改：`frontend/src/pages/tasks/TasksPage.tsx`
- 修改：`frontend/src/pages/tasks/useTaskEvents.ts`
- 修改：`frontend/src/pages/tasks/taskPresentation.test.ts`
- 修改：`frontend/src/pages/tasks/useTaskEvents.test.tsx`

- [ ] **步骤 1：增加乱序、重试进度回退和 SSE 丢事件测试**

旧 version 事件不得覆盖新缓存；同一 Run 自动重试的新 version 可以把进度从较大值重置；SSE 正常时仍定期 REST 对账；Redis 无事件但连接 heartbeat 正常时最终能刷新终态。

- [ ] **步骤 2：运行测试确认失败**

Run: `pnpm test --run src/pages/tasks/taskPresentation.test.ts src/pages/tasks/useTaskEvents.test.tsx`

- [ ] **步骤 3：按数据库 version 合并**

TaskSummary 与 TaskProgressEvent 增加 `version`。合并规则：

```ts
if (task.id !== event.taskId || event.version <= task.version) return task
return { ...task, ...eventFields, version: event.version }
```

不再使用 `Math.max(progress)`，让新 attempt 的进度可以合法归零。

- [ ] **步骤 4：增加 REST 权威对账**

轮询降级使用 2 秒；SSE 正常且页面存在活动任务时使用 15 秒；其余状态使用 60 秒低频校准。收到 created/retrying/terminal 事件立即 invalidate 列表，详情按 taskId invalidate。

- [ ] **步骤 5：运行前端定向测试**

Run: `pnpm test --run src/pages/tasks/taskPresentation.test.ts src/pages/tasks/useTaskEvents.test.tsx`

### 任务 8：全链路回归验证

**文件：**
- 修改：`docs/TESTING.md`
- 修改：`README.md`（仅在运行方式或配置名变化时）

- [ ] **步骤 1：运行后端全部测试**

Run: `mvn test`

预期：全部通过，无失败或跳过的核心可靠性测试。

- [ ] **步骤 2：运行前端全部验证**

Run: `pnpm lint`

Run: `pnpm test --run`

Run: `pnpm build`

预期：类型检查、测试和生产构建全部通过。

- [ ] **步骤 3：运行容器化冒烟验证**

Run: `docker compose up -d --build`

验证创建任务、Rabbit 消费、成功下载、模拟失败、手动重试、Redis 暂停后的 REST 对账、文件过期与孤儿文件清理。

- [ ] **步骤 4：更新测试文档**

只记录本项目新增的可靠性语义、配置项和验证命令；不描述外部项目或比较过程。

---

本计划执行期间不自动创建 Git commit，保留工作树供用户统一审阅。
