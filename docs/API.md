# API 文档

默认 Base URL：`http://localhost:8080`。所有时间使用 ISO-8601，本项目按 `Asia/Shanghai` 展示。

所有业务 JSON 响应统一使用以下信封：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": {},
  "requestId": "request-123",
  "timestamp": "2026-08-23T22:00:00+08:00"
}
```

分页数据位于 `data` 中：

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": { "items": [], "page": 1, "pageSize": 20, "total": 100000 },
  "requestId": "request-123",
  "timestamp": "2026-08-23T22:00:00+08:00"
}
```

错误响应统一为：

```json
{
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "同一 Idempotency-Key 不能用于不同请求",
  "data": {},
  "requestId": "...",
  "timestamp": "2026-08-22T23:00:00+08:00"
}
```

客户端可传 `X-Request-Id`；未传时后端自动生成并通过响应头 `X-Request-Id` 返回。
SSE、Excel 下载、Actuator 和 Swagger 不使用该 JSON 信封，保持各自标准协议。下文未展示完整信封的 JSON 片段均表示 `data` 字段内容。

## 订单

### `GET /api/orders`

查询参数：`orderNo`、`customerName`、`customerPhone`、`orderStatuses`、`paymentStatuses`、`paymentMethods`、`orderSources`、`minAmount`、`maxAmount`、`createdFrom`、`createdTo`、`page`、`pageSize`。数组参数可重复出现；多组条件为 AND，同组多选为 IN。

### `GET /api/orders/export-count`

参数与订单查询相同。返回：

```json
{ "count": 19942, "limitExceeded": false }
```

查询最长 5 秒；超时返回业务错误且不创建任务。

## 创建任务

### `POST /api/export-tasks`

请求头必须包含 `Idempotency-Key`。

勾选导出：

```json
{
  "exportType": "SELECTED",
  "selectedOrderIds": [100000, 99980]
}
```

条件导出：

```json
{
  "exportType": "FILTER",
  "filters": {
    "orderStatuses": ["COMPLETED"],
    "minAmount": 100,
    "createdFrom": "2026-01-01T00:00:00"
  }
}
```

首次创建返回 201；相同 Key + 相同规范化请求返回 200 且 `idempotentReplay=true`；相同 Key + 不同内容返回 409。

```json
{
  "id": 9,
  "taskNo": "EXP20260822233646653E7D90",
  "exportType": "FILTER",
  "status": "PENDING",
  "expectedCount": 1000000,
  "progress": 0,
  "idempotentReplay": false
}
```

## 任务查询

### `GET /api/export-tasks`

参数：`taskNo`、`exportType`、`status`、`createdFrom`、`createdTo`、`page`、`pageSize`。

状态：`PENDING`、`PROCESSING`、`SUCCESS`、`FAILED`、`EXPIRED`。

### `GET /api/export-tasks/{taskId}`

返回任务、逻辑快照、筛选 JSON、已选数量、下载次数、Attempt 列表和整条手动重试链。

### `POST /api/export-tasks/{taskId}/retry`

请求头必须包含新的 `Idempotency-Key`。只允许最终失败且可重试的任务；创建一条新任务并复用原快照/Item。整条根链最多两次手动重试。

### `GET /api/export-tasks/{taskId}/download`

仅未过期 `SUCCESS` 任务可下载。响应类型为 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，文件名通过 UTF-8 `Content-Disposition` 返回。

## SSE

### `GET /api/export-tasks/events`

响应类型：`text/event-stream`。连接后收到 `connected`，每 15 秒收到 `heartbeat`。业务事件包括：

- `task.progress`
- `task.succeeded`
- `task.failed`
- `task.retrying`
- `task.expired`

示例：

```text
event:task.progress
data:{"eventType":"task.progress","taskId":9,"status":"PROCESSING","stage":"QUERYING_WRITING","progress":64,"expectedCount":1000000,"exportedCount":660000,"fileSize":null,"fileExpireAt":null,"updatedAt":"2026-08-22T23:37:24"}
```

`task.succeeded` 会同时携带 `fileSize` 和 `fileExpireAt`，前端可立即展示下载按钮。SSE 负责实时界面更新；终态到达后前端会自动发起一次 REST 校准，最终状态始终以 MySQL 为准。

## 主要业务错误

| HTTP | code | 含义 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 参数格式、范围或必填项错误 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同一个 Key 被用于不同请求 |
| 409 | `TASK_NOT_DOWNLOADABLE` | 任务当前不可下载 |
| 410 | `FILE_EXPIRED` | 文件已过期 |
| 404 | `FILE_MISSING` | 文件记录存在但物理文件缺失 |
| 422 | `NO_EXPORT_DATA` | 没有可导出的订单 |
| 422 | `EXPORT_LIMIT_EXCEEDED` | 超过勾选或条件导出上限 |
| 500 | `INTERNAL_ERROR` | 未分类内部错误，详细堆栈仅写服务端日志 |
