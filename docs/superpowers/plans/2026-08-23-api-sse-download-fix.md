# API 统一响应与 SSE 完成态下载修复实施计划

> **给 agentic workers：** 使用 `superpowers:executing-plans` 按任务逐项执行本计划，步骤使用 checkbox（`- [ ]`）跟踪状态。

**目标：** 让所有业务 JSON 接口使用同一响应信封，任务页以 SSE 为主并在临时断连后快速恢复，同时让任务成功事件立即带出下载所需元数据，无需用户手动刷新。

**架构：** Spring MVC 通过 `ResponseBodyAdvice` 只包装 Jackson JSON 响应，SSE、Excel 文件、Actuator 与 Swagger 保持原协议。任务成功事务落库后发布包含 `fileSize`、`fileExpireAt` 的 SSE 事件；React Query 先合并事件实现即时 UI，再对终态做一次后台校准请求。SSE 连接使用显式的 `connecting/sse/polling` 状态，轮询只作为降级路径。

**技术栈：** Spring Boot 3.5、Spring MVC、JUnit 5、React 19、TanStack Query、EventSource、Vitest。

---

### 任务 1：统一业务 JSON 响应信封

**文件：**
- 新建：`backend/src/main/java/com/example/exportflow/common/api/ApiResponse.java`
- 新建：`backend/src/main/java/com/example/exportflow/common/api/ApiResponseBodyAdvice.java`
- 修改：`backend/src/main/java/com/example/exportflow/common/error/GlobalExceptionHandler.java`
- 删除：`backend/src/main/java/com/example/exportflow/common/api/ApiError.java`
- 新建：`backend/src/test/java/com/example/exportflow/common/api/ApiResponseBodyAdviceTest.java`
- 修改：`backend/src/test/java/com/example/exportflow/common/error/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：编写成功与失败信封测试**

```java
mvc.perform(get("/test/success").header("X-Request-Id", "request-123"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.code").value("SUCCESS"))
    .andExpect(jsonPath("$.message").value("操作成功"))
    .andExpect(jsonPath("$.data.value").value("ok"))
    .andExpect(jsonPath("$.requestId").value("request-123"));

mvc.perform(get("/test/fail").header("X-Request-Id", "request-123"))
    .andExpect(status().isUnprocessableEntity())
    .andExpect(jsonPath("$.code").value("NO_EXPORT_DATA"))
    .andExpect(jsonPath("$.data").isMap());
```

- [ ] **步骤 2：运行定向测试并确认旧实现失败**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ApiResponseBodyAdviceTest,GlobalExceptionHandlerTest test`

预期：成功接口缺少统一信封，错误接口仍使用旧 `ApiError.details`。

- [ ] **步骤 3：实现统一信封和仅 JSON 包装策略**

```java
public record ApiResponse<T>(
        String code, String message, T data, String requestId, OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("SUCCESS", "操作成功", data, requestId, now());
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data, String requestId) {
        return new ApiResponse<>(code, message, data, requestId, now());
    }
}
```

`ApiResponseBodyAdvice.supports` 仅接受 `MappingJackson2HttpMessageConverter`；`beforeBodyWrite` 对已经是 `ApiResponse` 的对象原样返回，其余业务 JSON 用 `ApiResponse.success` 包装。异常处理器直接返回 `ApiResponse<Map<String,Object>>`，避免二次包装。

- [ ] **步骤 4：运行后端定向测试**

Run: `mvn.cmd -f backend/pom.xml -Dtest=ApiResponseBodyAdviceTest,GlobalExceptionHandlerTest test`

预期：全部通过；下载与 SSE 不进入 Jackson 信封处理。

- [ ] **步骤 5：形成提交候选**

本轮不自动执行 `git commit`；验证通过后将这些文件列为一个独立提交候选：`feat: unify business api responses`。

### 任务 2：让前端解包统一响应

**文件：**
- 修改：`frontend/src/api/types.ts`
- 修改：`frontend/src/api/client.ts`
- 新建：`frontend/src/api/client.test.ts`

- [ ] **步骤 1：编写响应解包和错误解析测试**

```ts
vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
  code: 'SUCCESS', message: '操作成功', data: { items: [], page: 1, pageSize: 20, total: 0 },
  requestId: 'request-123', timestamp: '2026-08-23T22:00:00+08:00',
}), { status: 200, headers: { 'Content-Type': 'application/json' } })))
await expect(api.orders({}, 1, 20)).resolves.toEqual({ items: [], page: 1, pageSize: 20, total: 0 })
```

错误测试使用 HTTP 422 信封并断言 `ApiError.body.code`、`message`、`requestId` 和 `data`。

- [ ] **步骤 2：运行测试并确认旧客户端把整个信封当业务数据**

Run: `pnpm.cmd --dir frontend test --run src/api/client.test.ts`

预期：解包断言失败。

- [ ] **步骤 3：实现通用 `ApiResponse<T>` 类型与解包**

```ts
export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId: string
  timestamp: string
}

const envelope = await response.json() as ApiResponse<T>
if (!response.ok || envelope.code !== 'SUCCESS') throw new ApiError(response.status, envelope)
return envelope.data
```

文件下载成功响应继续读取 Blob；只有下载失败 JSON 才按统一信封解析。

- [ ] **步骤 4：运行前端定向测试**

Run: `pnpm.cmd --dir frontend test --run src/api/client.test.ts`

预期：全部通过。

- [ ] **步骤 5：形成提交候选**

提交候选：`feat: unwrap unified api envelope in frontend`。

### 任务 3：补全 SSE 终态数据并立即展示下载

**文件：**
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/TaskProgressEvent.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/infrastructure/progress/ProgressService.java`
- 修改：`backend/src/main/java/com/example/exportflow/export/application/ExportExecutionService.java`
- 修改：`frontend/src/api/types.ts`
- 修改：`frontend/src/pages/tasks/taskPresentation.ts`
- 修改：`frontend/src/pages/tasks/TasksPage.tsx`
- 修改：`frontend/src/pages/tasks/taskPresentation.test.ts`

- [ ] **步骤 1：编写成功事件合并测试**

```ts
const event: TaskProgressEvent = {
  eventType: 'task.succeeded', taskId: 1, status: 'SUCCESS', stage: 'COMPLETED',
  progress: 100, expectedCount: 100, exportedCount: 100,
  fileSize: 4096, fileExpireAt: '2026-08-24T22:00:00', updatedAt: '2026-08-23T22:00:00',
}
expect(mergeTaskEvent(task, event)).toMatchObject({
  status: 'SUCCESS', fileSize: 4096, fileExpireAt: '2026-08-24T22:00:00',
})
```

- [ ] **步骤 2：运行测试并确认旧事件类型/合并逻辑失败**

Run: `pnpm.cmd --dir frontend test --run src/pages/tasks/taskPresentation.test.ts`

预期：事件类型不接受文件字段或合并结果缺少下载元数据。

- [ ] **步骤 3：后端成功落库后发布完整终态事件**

`TaskProgressEvent` 增加可空字段 `Long fileSize`、`LocalDateTime fileExpireAt`。`ExportExecutionService` 在 `markSuccess` 成功后把 `stored.size()` 与同一个 `expireAt` 写回任务对象，再调用 `ProgressService.publish`；普通进度事件字段为 `null`。

- [ ] **步骤 4：前端合并终态元数据并后台校准**

```ts
const merged = {
  ...task,
  status: event.status,
  stage: event.stage,
  fileSize: event.fileSize ?? task.fileSize,
  fileExpireAt: event.fileExpireAt ?? task.fileExpireAt,
}
if (['task.succeeded', 'task.failed', 'task.expired'].includes(event.eventType)) {
  void queryClient.invalidateQueries({ queryKey: ['tasks'] })
}
```

这样下载按钮由成功事件立即出现，随后的一次 REST 请求只负责校准 `completedAt` 等非关键字段。

- [ ] **步骤 5：运行后端和前端相关测试**

Run: `mvn.cmd -f backend/pom.xml test`

Run: `pnpm.cmd --dir frontend test --run`

预期：全部通过。

- [ ] **步骤 6：形成提交候选**

提交候选：`fix: expose download as soon as export succeeds`。

### 任务 4：明确 SSE 主通道与快速降级恢复

**文件：**
- 修改：`frontend/src/pages/tasks/useTaskEvents.ts`
- 修改：`frontend/src/pages/tasks/useTaskEvents.test.tsx`
- 修改：`frontend/src/pages/tasks/TasksPage.tsx`

- [ ] **步骤 1：编写连接模式测试**

测试初始状态为 `connecting`，`onopen` 后为 `sse`，连续三次失败后为 `polling`，5 秒重连成功后立即恢复 `sse`。

- [ ] **步骤 2：运行定向测试确认当前 boolean 状态不满足契约**

Run: `pnpm.cmd --dir frontend test --run src/pages/tasks/useTaskEvents.test.tsx`

预期：连接模式与 5 秒恢复断言失败。

- [ ] **步骤 3：实现显式连接状态**

```ts
export type TaskEventMode = 'connecting' | 'sse' | 'polling'
const [mode, setMode] = useState<TaskEventMode>('connecting')
source.onopen = () => { failures = 0; setMode('sse') }
// 三次连续错误后 setMode('polling')，每 5 秒尝试重连；连接成功立即停止轮询。
```

任务查询仅在 `mode === 'polling'` 时设置 `refetchInterval: 2000`；标签分别显示“正在连接 / SSE 实时 / 轮询降级”。

- [ ] **步骤 4：运行全部前端测试、类型检查和构建**

Run: `pnpm.cmd --dir frontend test --run`

Run: `pnpm.cmd --dir frontend lint`

Run: `pnpm.cmd --dir frontend build`

预期：全部通过。

- [ ] **步骤 5：形成提交候选**

提交候选：`fix: make sse connection state explicit`。

### 任务 5：重启并完成端到端验收

**文件：**
- 修改：`README.md`（记录业务响应信封，以及 SSE/下载/Actuator 的协议例外）

- [ ] **步骤 1：打包后端并重启本地 Java 进程**

Run: `mvn.cmd -f backend/pom.xml clean package`

预期：生成 `backend/target/export-flow-backend-0.0.1-SNAPSHOT.jar`。

- [ ] **步骤 2：验证 API 协议**

Run: `curl.exe http://127.0.0.1:8080/api/orders?page=1^&pageSize=1`

预期：HTTP 200，JSON 顶层为 `code/message/data/requestId/timestamp`。

Run: `curl.exe -N http://127.0.0.1:8080/api/export-tasks/events`

预期：`Content-Type: text/event-stream`，收到 `connected` 与心跳事件，不被 JSON 信封包裹。

- [ ] **步骤 3：通过网页创建小型导出任务**

在订单页勾选少量订单创建导出，进入任务页，确认标签为“SSE 实时”、进度通过事件更新、成功后无需点击刷新即出现“下载”。

- [ ] **步骤 4：验证下载响应**

点击“下载”，预期浏览器收到 `.xlsx` 文件，响应仍为 Excel MIME 类型而不是 JSON 信封。

- [ ] **步骤 5：更新 README 并保留运行服务**

README 明确：统一信封只适用于业务 JSON；SSE、Excel 下载、Actuator 和 Swagger 保持各自标准协议。完成后保留 `127.0.0.1:5174` 与 `127.0.0.1:8080` 供用户继续使用。

## 自检结果

- 需求覆盖：统一响应、SSE 主通道、轮询降级、终态即时下载均有对应任务。
- 协议边界：下载与 SSE 明确排除统一 JSON 包装，避免破坏流式响应。
- 类型一致性：后端 `fileSize/fileExpireAt` 与前端 `TaskProgressEvent` 同名且均为可空/可选字段。
- 占位符扫描：无 TBD、TODO 或未定义实现步骤。
