# 测试与验收记录

记录日期：2026-08-22，Windows 11 + Docker Desktop；后端使用 IntelliJ IDEA 2026.2.1 自带 JBR 25 运行，Maven 编译目标为 Java 21。

## 自动化命令

```powershell
# 后端
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\jbr'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' test

# 前端
Set-Location frontend
pnpm.cmd lint
pnpm.cmd test --run
pnpm.cmd build

# Compose 配置
Set-Location ..
docker compose config --quiet
```

后端覆盖上下文、统一错误、请求摘要、Excel 安全写入、计数超时映射、订单 SQL/record 构造映射、JOIN 列限定、Attempt 不可变对象插入与查询。前端覆盖导航、跨页选择、进度事件合并和 SSE 三次失败降级/30 秒恢复。

最终结果：后端 10 tests 全部通过；前端 4 个测试文件、6 tests 全部通过；TypeScript 检查、Vite 生产构建和 `docker compose config --quiet` 均退出码 0。

## 端到端结果

实际启动：Docker 中运行 MySQL 8.4、RabbitMQ 4.2、Redis 8.2；Spring Boot 使用 IDEA JBR 在 18080 启动；Vite 因本机 5173 已占用而在 5174 启动。

| 验收项 | 结果 |
|---|---|
| 基础设施健康 | MySQL、RabbitMQ、Redis 均 healthy |
| 订单分页/统计 | 100,000 条与 1,000,000 条均可查询；9 组筛选表单正常 |
| 跨页选择 | 第 1、2 页各选 1 条，累计显示 2，创建任务成功 |
| 幂等 | 同 Key/同内容返回任务 1；同 Key/不同内容返回 409；不同 Key 创建任务 3 |
| Outbox | 应用重启后待发布事件继续发布，RabbitMQ 消费恢复 |
| 自动重试 | 注入的 SQL 失败真实生成 3 个 Attempt，最终 FAILED |
| 手动重试 | 失败任务 ID 保持不变，依次创建 Run 1/Run 2；旧 Run 只读，达到上限后 `canManualRetry=false` |
| 下载 | 5 行数据 + 1 行表头，XLSX ZIP 结构有效，下载次数递增 |
| SSE | 收到 connected、heartbeat、5%–95% 进度、99% MOVING、100% succeeded |
| 浏览器 | 订单页、任务页、详情抽屉实测；干净会话 0 errors / 0 warnings |

## 规模测试

### 100,000 行

- 预计/实际：100,000 / 100,000
- 耗时：约 10 秒
- 文件大小：7,505,449 字节
- 进度：0 → 5 → 14 → 23 → … → 95 → 99 → 100
- Attempt：1 次，状态 SUCCESS

### 1,000,000 行

- 预计/实际：1,000,000 / 1,000,000
- 从创建到成功：约 66 秒
- 文件大小：74,758,615 字节（约 71.3 MB）
- 工作簿：单 Sheet，流式 XML 精确统计 1,000,001 行（表头 + 100 万数据），校验通过
- Attempt：1 次，状态 SUCCESS，无 OOM
- 完成后的 JVM G1 堆：reserved 2,058,240 KB，committed 266,240 KB，used 127,902 KB
- 进程采样：Working Set 约 62.9 MB，Private Memory 约 559.6 MB

这些是本机单次学习环境数据，不应作为生产 SLA；数据库、磁盘、JVM 参数和并发数都会影响结果。

## Docker 构建说明

`docker compose up -d --build` 的项目配置已完成；本机尝试构建时，Docker Hub 鉴权地址走 IPv6 并超时，导致 `eclipse-temurin`、`node`、`nginx` 基础镜像元数据无法拉取。该失败发生在 Dockerfile 执行前，不是代码编译错误。随后使用本机已有镜像启动三个中间件，并用 IDEA JDK/Vite 完成了全部真实联调。

网络恢复后执行以下命令即可验证完整镜像构建：

```powershell
docker compose up -d --build
docker compose ps
```

## 核心可靠性回归

以下约束属于任务主链路的固定验收项：

- Task、Run、Attempt 的执行状态在同一事务中更新。Task fencing 未命中表示旧 Worker 已失效；Task 命中后 Run 或 Attempt 未命中必须回滚。
- Redis/SSE 事件只在数据库事务提交后发布，并携带 `version`。前端只接受更大的版本号，SSE 健康时仍以 15 秒（有活动任务）或 60 秒（无活动任务）进行 REST 对账。
- `app.export.heartbeat-interval` 同时控制进度持久化节流和独立执行心跳；独立心跳必须覆盖 Excel 最终写入和文件移动阶段。
- 创建任务和手动重试的幂等写入在独立事务完成后处理唯一键冲突；并发相同 Key 只产生一份 Task/Run/Outbox 数据。
- 文件缺失、文件过期状态原子更新 Task/Run。最终目录仅清理超过两小时且 Task/Run 均无引用的 `.xlsx`，存储路径拒绝目录越界和符号链接。
- Outbox 消息包含 `schemaVersion` 和 `requestId`；只有 Rabbit confirm ACK 且没有 returned message 才能标记发布成功。
- `app.export.count-timeout-seconds` 同时控制 MyBatis 查询超时和 API 提示。页码范围为 1..10000，分页大小只允许 20、50、100，参数类型转换失败返回 400。

定向回归命令：

```powershell
# 后端核心事务、事件和幂等测试
mvn '-Dtest=CoreReliabilityServicesTest,TaskEventPublisherTest,RetryServiceTest' test

# 前端版本合并和 SSE/REST 对账测试
Set-Location frontend
pnpm.cmd test --run src/pages/tasks/taskPresentation.test.ts src/pages/tasks/useTaskEvents.test.tsx
```
