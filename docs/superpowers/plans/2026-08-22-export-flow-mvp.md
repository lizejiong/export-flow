# Export Flow MVP 实施计划

> **给 agentic workers：** 必需子技能：使用 `superpowers:executing-plans` 按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪状态。

**目标：** 构建一个可本地运行的异步订单导出系统，支持订单筛选与跨页选择、RabbitMQ 异步生成 XLSX、Outbox、真实进度、SSE、下载、自动恢复和手动重试。

**架构：** React 单页应用调用单体 Spring Boot API。Spring Boot 同时承载 HTTP、RabbitMQ Listener 和 `@Scheduled` 调度，但各自使用独立线程池；MySQL 保存最终状态，Redis 保存高频进度并用 Pub/Sub 转发 SSE，RabbitMQ 承担任务投递，本地卷保存导出文件。

**技术栈：** Java 21、Spring Boot 3.5.16、MyBatis Starter 3.0.5、Apache POI 5.5.1、MySQL 8.4、RabbitMQ 4.2、Redis 8.2、React 19、TypeScript、Vite、Ant Design、TanStack Query、Vitest、Docker Compose。

---

## 文件结构

```text
export-flow/
├─ backend/
│  ├─ pom.xml
│  ├─ Dockerfile
│  └─ src/
│     ├─ main/java/com/example/exportflow/
│     │  ├─ ExportFlowApplication.java
│     │  ├─ common/                 API 响应、错误、时钟与配置
│     │  ├─ order/                  订单领域、筛选、分页和开发数据生成
│     │  ├─ export/domain/          任务、执行记录和状态机
│     │  ├─ export/application/     创建、查询、重试、下载和导出编排
│     │  ├─ export/infrastructure/  MyBatis、文件、Excel、Redis、MQ、Outbox
│     │  └─ export/web/             REST 与 SSE Controller
│     └─ main/resources/
│        ├─ application.yml
│        ├─ db/migration/
│        └─ mapper/
├─ frontend/
│  ├─ package.json
│  ├─ vite.config.ts
│  └─ src/
│     ├─ api/                       HTTP、SSE、类型和错误归一化
│     ├─ pages/orders/              订单页与跨页选择
│     ├─ pages/tasks/               任务列表、详情、下载和重试
│     └─ test/                      Vitest 环境
├─ compose.yaml
├─ .env.example
├─ .gitignore
└─ README.md
```

### 任务 1：仓库、基础设施与后端启动骨架

**文件：**
- 新建：`.gitignore`
- 新建：`.env.example`
- 新建：`compose.yaml`
- 新建：`backend/pom.xml`
- 新建：`backend/Dockerfile`
- 新建：`backend/src/main/java/com/example/exportflow/ExportFlowApplication.java`
- 新建：`backend/src/main/resources/application.yml`
- 测试：`backend/src/test/java/com/example/exportflow/ExportFlowApplicationTest.java`

- [x] **步骤 1：编写 Spring 上下文失败测试**

```java
@SpringBootTest
class ExportFlowApplicationTest {
    @Test
    void contextLoads() {}
}
```

- [x] **步骤 2：添加 Maven 依赖和应用入口**

`pom.xml` 使用 Spring Boot 3.5.16，包含 Web、Validation、AMQP、Redis、JDBC、MyBatis 3.0.5、Flyway、MySQL、POI 5.5.1、Actuator 和 Testcontainers 测试依赖；Java 版本固定为 21。

```java
@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.exportflow")
public class ExportFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExportFlowApplication.class, args);
    }
}
```

- [x] **步骤 3：添加 MySQL、RabbitMQ、Redis 与应用 Compose 服务**

基础设施暴露 `3306`、`5672/15672`、`6379`，应用暴露 `8080`；MySQL、RabbitMQ、Redis 使用健康检查，应用等待三者健康后启动，`./data/exports` 挂载为文件卷。

- [x] **步骤 4：运行骨架测试**

Run：

```powershell
docker run --rm -v "${PWD}/backend:/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -q test
```

预期：测试通过，退出码 0。

### 任务 2：数据库迁移、领域枚举与公共错误模型

**文件：**
- 新建：`backend/src/main/resources/db/migration/V1__init_schema.sql`
- 新建：`backend/src/main/java/com/example/exportflow/common/api/ApiError.java`
- 新建：`backend/src/main/java/com/example/exportflow/common/api/PageResponse.java`
- 新建：`backend/src/main/java/com/example/exportflow/common/error/BusinessException.java`
- 新建：`backend/src/main/java/com/example/exportflow/common/error/GlobalExceptionHandler.java`
- 新建：`backend/src/main/java/com/example/exportflow/order/domain/OrderEnums.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/domain/ExportEnums.java`
- 测试：`backend/src/test/java/com/example/exportflow/common/error/GlobalExceptionHandlerTest.java`

- [x] **步骤 1：编写错误响应测试**

使用 `MockMvc` 调用一个抛出 `BusinessException("NO_EXPORT_DATA", HttpStatus.UNPROCESSABLE_ENTITY, "没有可导出的订单")` 的测试端点，断言状态为 422，JSON 包含 `code`、`message`、`requestId`。

- [x] **步骤 2：建立五张核心表**

迁移脚本创建：

```sql
orders
export_task
export_task_item
export_task_attempt
outbox_event
```

字段和索引严格按 `docs/PRD.md` 第 22 节实现；`export_task.idempotency_key`、`task_no` 唯一，`export_task_item` 主键为 `(task_id, order_id)`，`outbox_event.event_id` 唯一。

- [x] **步骤 3：定义稳定枚举代码**

```java
public enum ExportTaskStatus { PENDING, PROCESSING, SUCCESS, FAILED, EXPIRED }
public enum ExportStage { QUEUED, RETRY_WAITING, PREPARING, QUERYING_WRITING, FINALIZING, MOVING, RECOVERING, COMPLETED }
public enum ExportType { SELECTED, FILTER }
```

订单状态、支付状态、支付方式和来源使用 PRD 第 7.1 节代码。

- [x] **步骤 4：实现统一错误响应并运行测试**

Run：`mvn -q -Dtest=GlobalExceptionHandlerTest test`

预期：测试通过；校验错误返回 400，业务错误使用自身 HTTP 状态，未知错误返回 500 且不输出堆栈给客户端。

### 任务 3：订单分页、九组筛选与模拟数据

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/order/domain/Order.java`
- 新建：`backend/src/main/java/com/example/exportflow/order/application/OrderFilter.java`
- 新建：`backend/src/main/java/com/example/exportflow/order/application/OrderService.java`
- 新建：`backend/src/main/java/com/example/exportflow/order/infrastructure/OrderMapper.java`
- 新建：`backend/src/main/resources/mapper/OrderMapper.xml`
- 新建：`backend/src/main/java/com/example/exportflow/order/web/OrderController.java`
- 新建：`backend/src/main/java/com/example/exportflow/order/dev/OrderDataSeeder.java`
- 测试：`backend/src/test/java/com/example/exportflow/order/OrderMapperIT.java`
- 测试：`backend/src/test/java/com/example/exportflow/order/OrderControllerTest.java`

- [x] **步骤 1：编写筛选集成测试**

插入覆盖全部枚举的订单，分别验证订单号、姓名、手机号、订单状态、支付状态、支付方式、来源、金额范围、创建时间范围，并验证多条件 AND 与多选 IN。

- [x] **步骤 2：实现 MyBatis 动态 SQL 与游标读取**

Mapper 提供：

```java
PageSlice<Order> findPage(OrderFilter filter, int offset, int pageSize);
long countForExport(OrderFilter filter, long snapshotMaxId, int timeoutSeconds);
long findMaxId();
List<Order> findExportBatch(OrderFilter filter, long snapshotMaxId, long afterId, int limit);
List<Order> findSelectedBatch(long taskId, long afterOrderId, int limit);
```

XML 中所有用户值使用 `#{}` 参数绑定；客户姓名使用转义后的 LIKE 参数；导出批次固定 `ORDER BY id ASC LIMIT #{limit}`。

- [x] **步骤 3：实现 REST 接口**

```text
GET /api/orders
GET /api/orders/export-count
```

分页接口默认 20 条、最大 100 条；统计接口设置 5 秒语句超时并返回 `count`、`limitExceeded`。

- [x] **步骤 4：实现开发数据生成器**

应用参数 `--app.seed.orders=100000` 或 `1000000` 时分批生成稳定分布的模拟订单；只有 `dev` Profile 生效。每批 2,000 条，订单号唯一，不提供 HTTP 清空接口。

- [x] **步骤 5：运行订单测试**

Run：`mvn -q -Dtest=OrderMapperIT,OrderControllerTest test`

预期：九组筛选、分页、统计超时映射和参数校验全部通过。

### 任务 4：请求幂等、任务创建与 Transactional Outbox

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/domain/ExportTask.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/CreateExportTaskCommand.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/ExportTaskCreationService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/ExportTaskMapper.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/OutboxMapper.java`
- 新建：`backend/src/main/resources/mapper/ExportTaskMapper.xml`
- 新建：`backend/src/main/resources/mapper/OutboxMapper.xml`
- 新建：`backend/src/main/java/com/example/exportflow/export/web/ExportTaskController.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/web/dto/ExportTaskDtos.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExportTaskCreationServiceIT.java`

- [x] **步骤 1：编写幂等与创建事务测试**

覆盖：

```text
相同 Key + 相同规范化请求 -> 返回原任务
相同 Key + 不同请求 -> 409 IDEMPOTENCY_KEY_REUSED
不同 Key + 相同条件 -> 创建两条任务
FILTER 为 0 -> 422 且无 Task/Outbox
FILTER 超过 1,000,000 -> 422 且无 Task/Outbox
SELECTED 去重且最多 5,000 -> Task、Items、Outbox 同事务存在
```

- [x] **步骤 2：实现规范化与 SHA-256 请求摘要**

筛选数组排序、ID 排序去重、金额和时间使用稳定格式，序列化后计算 SHA-256；摘要逻辑写为纯函数并进行单元测试。

- [x] **步骤 3：实现任务创建事务**

条件任务先读取 `snapshotMaxId` 并同步统计；勾选任务查询存在 ID。事务内创建 `export_task`、可选 `export_task_item` 和 `outbox_event`。并发唯一键冲突时读取旧任务并比较 `request_hash`。

- [x] **步骤 4：实现创建端点**

`POST /api/export-tasks` 要求 `Idempotency-Key`；首次创建返回 201，幂等重放返回 200 和 `idempotentReplay=true`。

- [x] **步骤 5：运行任务创建测试**

Run：`mvn -q -Dtest=ExportTaskCreationServiceIT test`

预期：事务、快照、上限和幂等测试全部通过。

### 任务 5：RabbitMQ 拓扑、Outbox 发布与消费抢占

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/RabbitTopologyConfig.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/ExportTaskMessage.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/OutboxPublisher.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/messaging/ExportTaskListener.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/TaskClaimService.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/OutboxPublisherIT.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/TaskClaimServiceIT.java`

- [x] **步骤 1：编写发布与抢占测试**

验证 Publisher Confirm 后 Outbox 变为 `PUBLISHED`；发布异常保留 `PENDING` 并增加次数；两个线程同时抢占时只有一个数据库更新成功；终态任务的重复消息被忽略。

- [x] **步骤 2：声明 RabbitMQ 资源**

使用 PRD 固定命名；主队列 prefetch 1、默认并发 2；重试队列 TTL 默认 15 秒并死信回主交换机；不可解析消息路由到死信队列。

- [x] **步骤 3：实现 Outbox 发布器**

每秒读取最多 100 条到期事件，发布带 `eventId`、`taskId`、`eventType` 的 JSON；Confirm 成功标记已发布，失败指数退避且不删除事件。

- [x] **步骤 4：实现数据库 CAS 抢占**

生成 `workerId` 与 `executionToken`，原子把 `PENDING` 更新为 `PROCESSING/PREPARING` 并创建 `export_task_attempt`；失败时返回重复消息结果。

- [x] **步骤 5：运行 MQ 与抢占测试**

Run：`mvn -q -Dtest=OutboxPublisherIT,TaskClaimServiceIT test`

预期：消息最终发布、重复消息安全、抢占只有一个赢家。

### 任务 6：流式 Excel、真实进度与本地文件落盘

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/ExportExecutionService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/excel/OrderExcelWriter.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/storage/LocalFileStorage.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/ProgressStore.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/RedisProgressStore.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/DatabaseProgressStore.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/OrderExcelWriterTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExportExecutionServiceIT.java`

- [x] **步骤 1：编写 Excel 测试**

生成包含 14 列、空支付时间、中文状态、手机号和 `=1+1` 客户名的文件；用 POI 重新打开并断言表头、格式、公式转义、冻结行和数据数量。

- [x] **步骤 2：实现 SXSSF 流式写入**

行窗口 500，数据批次 1,000；按 ID 游标读取；文本公式前缀写成文本安全值；所有 Workbook、输出流和 SXSSF 临时资源可靠关闭。

- [x] **步骤 3：实现 token 隔离的本地存储**

临时文件名包含任务编号和 `executionToken`；正式文件名符合 PRD；移动前再次校验 token，使用同文件系统原子移动；CAS 失败时删除本 Attempt 文件。

- [x] **步骤 4：实现进度更新**

Redis 每 1,000 条更新，MySQL 每 10,000 条或 5 秒更新；Redis 异常只记录警告，不能中止导出；最终成功先写 MySQL 再发布 Redis 事件。

- [x] **步骤 5：运行 Excel 与执行测试**

Run：`mvn -q -Dtest=OrderExcelWriterTest,ExportExecutionServiceIT test`

预期：文件可打开、行列正确、进度单调、弱快照数量差异仍成功、旧 token 无法完成任务。

### 任务 7：自动重试、心跳恢复、手动重试与文件清理

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/ExportFailureClassifier.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/RetryService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/RecoveryScheduler.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/FileCleanupScheduler.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/RetryServiceIT.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/RecoverySchedulerIT.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/FileCleanupSchedulerIT.java`

- [x] **步骤 1：编写重试链测试**

验证单任务总共最多 3 个 Attempt；最终失败后手动重试创建新 Task；新任务复制条件或 Item；根链最多两次；原任务保持 `FAILED`；重试端点同样支持请求幂等。

- [x] **步骤 2：编写失联恢复测试**

把 `PROCESSING` 任务心跳改为 61 秒前，运行调度器；断言旧 token 失效、旧 Attempt 失败、任务回到 `PENDING/RECOVERING`、Outbox 新增；第三次失联转 `FAILED`。

- [x] **步骤 3：实现错误分类和自动重试**

临时 DB/I/O/未知系统异常可重试；校验、超限、目录非法、磁盘阈值不足和数据映射错误不可自动重试。可重试异常通过事务写 Attempt、重置 Task 和插入重试 Outbox，成功后 Listener ACK 当前消息。

- [x] **步骤 4：实现手动重试事务**

`POST /api/export-tasks/{id}/retry` 创建新任务，复制快照与 Item，设置 `source_task_id`、`root_task_id`、`manual_retry_index`，同时插入 Outbox。

- [x] **步骤 5：实现恢复与文件清理**

恢复扫描 30 秒、失联阈值 60 秒；文件每小时清理、应用启动补扫；删除成功后状态变 `EXPIRED`，删除失败保留 `SUCCESS`；清理旧临时文件和孤儿文件。

- [x] **步骤 6：运行恢复测试**

Run：`mvn -q -Dtest=RetryServiceIT,RecoverySchedulerIT,FileCleanupSchedulerIT test`

预期：自动三次、手动两次、token 失效、过期清理均符合 PRD。

### 任务 8：任务查询、下载与 SSE

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/export/application/ExportTaskQueryService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/application/DownloadService.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/TaskEventPublisher.java`
- 新建：`backend/src/main/java/com/example/exportflow/export/web/TaskEventController.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/ExportTaskQueryControllerTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/DownloadServiceTest.java`
- 测试：`backend/src/test/java/com/example/exportflow/export/TaskEventControllerTest.java`

- [x] **步骤 1：编写查询和下载测试**

覆盖任务编号、类型、状态、日期分页；详情包含 Attempt 与重试链；只有未过期 `SUCCESS` 可下载；过期返回 410；丢失文件返回 404 并把任务修正为不可重试 `FAILED`。

- [x] **步骤 2：实现任务 REST API**

```text
GET /api/export-tasks
GET /api/export-tasks/{taskId}
GET /api/export-tasks/{taskId}/download
```

下载流式响应，设置 XLSX Content-Type、UTF-8 Content-Disposition，路径必须由任务记录解析且位于正式目录内。

- [x] **步骤 3：实现全局 SSE**

`GET /api/export-tasks/events` 返回 `text/event-stream`；SseEmitter 超时和完成后移除；每 15 秒心跳；Redis Pub/Sub 消息转成 PRD 第 21 节事件；Redis 异常不影响 REST。

- [x] **步骤 4：运行 API 测试**

Run：`mvn -q -Dtest=ExportTaskQueryControllerTest,DownloadServiceTest,TaskEventControllerTest test`

预期：查询、详情、下载安全和 SSE 生命周期测试通过。

### 任务 9：React 基础、API 客户端与公共布局

**文件：**
- 新建：`frontend/package.json`
- 新建：`frontend/tsconfig.json`
- 新建：`frontend/vite.config.ts`
- 新建：`frontend/vitest.config.ts`
- 新建：`frontend/index.html`
- 新建：`frontend/src/main.tsx`
- 新建：`frontend/src/App.tsx`
- 新建：`frontend/src/api/client.ts`
- 新建：`frontend/src/api/types.ts`
- 新建：`frontend/src/api/sse.ts`
- 新建：`frontend/src/styles.css`
- 测试：`frontend/src/App.test.tsx`

- [x] **步骤 1：初始化依赖并编写导航测试**

测试 `/orders` 和 `/tasks` 两个导航入口可见，未知路径重定向 `/orders`。

- [x] **步骤 2：实现应用基础**

使用 React Router、Ant Design `App`、TanStack QueryProvider；中文 Locale 与 `Asia/Shanghai` 展示格式；Vite `/api` 代理到 `http://localhost:8080`。

- [x] **步骤 3：实现类型化 API 客户端**

统一解析 `ApiError`；创建/重试方法生成并复用 `Idempotency-Key`；下载使用浏览器 Blob；SSE 客户端连续失败 3 次后触发轮询状态。

- [x] **步骤 4：运行前端基础测试**

Run：`pnpm.cmd --dir frontend test --run`

预期：导航、错误映射与 SSE 降级单元测试通过。

### 任务 10：订单页与跨分页选择

**文件：**
- 新建：`frontend/src/pages/orders/orderFilters.ts`
- 新建：`frontend/src/pages/orders/useOrderSelection.ts`
- 新建：`frontend/src/pages/orders/OrderFilterForm.tsx`
- 新建：`frontend/src/pages/orders/ExportConfirmModal.tsx`
- 新建：`frontend/src/pages/orders/OrdersPage.tsx`
- 测试：`frontend/src/pages/orders/useOrderSelection.test.ts`
- 测试：`frontend/src/pages/orders/OrdersPage.test.tsx`

- [x] **步骤 1：编写选择状态测试**

验证翻页保留、当前页全选、清空、5,000 上限、修改筛选前提示、刷新不持久化、成功后清空、失败后保留。

- [x] **步骤 2：实现九组筛选和分页表格**

默认创建时间倒序；支持 20/50/100；表格横向滚动；查询和重置回到第 1 页；金额、日期和枚举在前端先校验。

- [x] **步骤 3：实现两种导出流程**

已选导出展示有效数量；条件导出先调用预览统计；无条件时二次确认；提交期间禁用按钮；成功停留订单页并展示任务编号和“查看任务”。

- [x] **步骤 4：运行订单页测试**

Run：`pnpm.cmd --dir frontend test --run src/pages/orders`

预期：筛选、分页、跨页选择、上限、确认和幂等重试交互通过。

### 任务 11：任务页、实时进度、详情、下载与重试

**文件：**
- 新建：`frontend/src/pages/tasks/taskPresentation.ts`
- 新建：`frontend/src/pages/tasks/useTaskEvents.ts`
- 新建：`frontend/src/pages/tasks/TaskDetailDrawer.tsx`
- 新建：`frontend/src/pages/tasks/TasksPage.tsx`
- 测试：`frontend/src/pages/tasks/useTaskEvents.test.ts`
- 测试：`frontend/src/pages/tasks/TasksPage.test.tsx`

- [x] **步骤 1：编写进度合并与降级测试**

验证 SSE 事件只更新目标任务、进度不倒退、终态优先、三次失败后启用 2 秒轮询、30 秒尝试恢复、恢复后刷新 REST。

- [x] **步骤 2：实现任务筛选与列表**

按编号、类型、状态、创建时间查询；创建时间倒序；状态中文映射；进度条、已处理/预计数、文件大小、时间和操作按钮符合 PRD 表格。

- [x] **步骤 3：实现详情抽屉**

展示快照、筛选摘要、Attempt、token 短值、错误、文件、重试父子链和状态时间线。

- [x] **步骤 4：实现下载与手动重试**

仅有效成功任务显示下载；仅可重试且未达上限任务显示重试；手动重试使用新幂等键，成功后新任务置顶高亮。

- [x] **步骤 5：运行任务页测试**

Run：`pnpm.cmd --dir frontend test --run src/pages/tasks`

预期：列表、SSE、降级、详情、下载和重试交互通过。

### 任务 12：端到端验证、文档与交付

**文件：**
- 新建：`README.md`
- 新建：`docs/API.md`
- 新建：`docs/DEMO.md`
- 新建：`docs/TESTING.md`
- 修改：`compose.yaml`

- [x] **步骤 1：运行全部静态与单元测试**

```powershell
docker run --rm -v "${PWD}/backend:/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn verify
pnpm.cmd --dir frontend lint
pnpm.cmd --dir frontend test --run
pnpm.cmd --dir frontend build
```

预期：全部退出码为 0。

- [x] **步骤 2：启动完整环境并检查健康状态**

Run：`docker compose up -d --build`

预期：MySQL、RabbitMQ、Redis、Backend 健康；前端可访问；Flyway 完成迁移。

- [x] **步骤 3：执行核心端到端验收**

生成 10 万订单，验证九组筛选、跨页选择、条件导出、SSE、下载、幂等重放；暂停 RabbitMQ 验证 Outbox；执行中重启应用验证恢复；停止 Redis 验证轮询；把文件过期时间改为过去验证清理。

- [x] **步骤 4：执行 100 万行测试**

生成 100 万订单，创建全量任务，记录耗时、最大 JVM 堆占用和文件大小；确认单 Sheet、100 万数据行、无 OOM、任务进度最终 100%。

- [x] **步骤 5：完善交付文档**

README 写明前置条件、启动、开发命令、端口、数据生成和常见故障；API 文档覆盖 REST/SSE；DEMO 写明 Outbox、恢复、自动/手动重试演示；TESTING 记录命令与实测结果。

---

## 自检结论

- PRD 的订单页、任务页、筛选、快照、Excel、状态机、Outbox、MQ、Redis/SSE、自动恢复、手动重试、下载和清理均有对应任务；
- 不实现登录、取消、删除、业务条件去重、Excel 分片、XXL-JOB、Redis 锁和指标看板；
- 后端所有关键状态变化以 MySQL CAS 和事务为准；
- 前端的 `Idempotency-Key` 生命周期与后端唯一约束一致；
- 自动执行“总共 3 次”和手动重试“根链 2 次”在任务 7 中统一实现；
- 本计划无待定占位项，未实现内容均明确列入非目标。
