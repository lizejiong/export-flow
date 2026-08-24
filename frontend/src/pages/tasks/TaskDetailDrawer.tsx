import { Collapse, Descriptions, Drawer, Empty, Progress, Space, Tag, Timeline, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { api } from '../../api/client'
import type { TaskRun } from '../../api/types'
import { formatBytes, stageLabels, statusColors, taskStatusLabels } from './taskPresentation'

const triggerLabels: Record<TaskRun['triggerType'], string> = {
  INITIAL: '首次执行',
  MANUAL_RETRY: '手动重试',
  RECOVERY: '故障恢复',
}

export default function TaskDetailDrawer({ taskId, onClose }: { taskId?: number; onClose: () => void }) {
  const detail = useQuery({ queryKey: ['task-detail', taskId], queryFn: () => api.taskDetail(taskId!), enabled: taskId != null })
  const data = detail.data
  const task = data?.task
  const runItems = data?.runs.slice().reverse().map((run) => ({
    key: run.id,
    label: <Space>
      <strong>Run {run.runNo}</strong>
      <Tag>{triggerLabels[run.triggerType]}</Tag>
      <span><span className="status-dot" style={{ background: statusColors[run.status] }} />{taskStatusLabels[run.status]}</span>
      <span className="muted">{dayjs(run.createdAt).format('YYYY-MM-DD HH:mm:ss')}</span>
    </Space>,
    children: <Space orientation="vertical" size={16} style={{ width: '100%' }}>
      <Progress percent={run.progress} size="small" status={run.status === 'FAILED' ? 'exception' : run.status === 'SUCCESS' ? 'success' : 'active'} />
      <Descriptions column={2} size="small" bordered>
        <Descriptions.Item label="阶段">{stageLabels[run.stage] ?? run.stage}</Descriptions.Item>
        <Descriptions.Item label="自动尝试">{run.autoAttemptCount} / 3</Descriptions.Item>
        <Descriptions.Item label="导出数量">{run.exportedCount.toLocaleString()} / {run.expectedCount.toLocaleString()}</Descriptions.Item>
        <Descriptions.Item label="文件大小">{formatBytes(run.fileSize)}</Descriptions.Item>
        {run.failureMessage && <Descriptions.Item label="失败原因" span={2}><Typography.Text type="danger">[{run.failureCode}] {run.failureMessage}</Typography.Text></Descriptions.Item>}
      </Descriptions>
      <div>
        <Typography.Title level={5}>自动执行记录</Typography.Title>
        {run.attempts.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未开始执行" /> : <Timeline items={run.attempts.map((attempt) => ({
          color: attempt.status === 'SUCCESS' ? 'green' : attempt.status === 'PROCESSING' ? 'blue' : 'red',
          content: <div><strong>第 {attempt.attemptNo} 次 · {attempt.status}</strong><div className="muted">{dayjs(attempt.startedAt).format('YYYY-MM-DD HH:mm:ss')} · {attempt.workerId} · token {attempt.executionTokenShort}</div>{attempt.failureMessage && <Typography.Text type="danger">[{attempt.failureCode}] {attempt.failureMessage}</Typography.Text>}</div>,
        }))} />}
      </div>
    </Space>,
  })) ?? []

  return (
    <Drawer title="任务详情" size="large" open={taskId != null} onClose={onClose} loading={detail.isLoading}>
      {!task || !data ? <Empty description="暂无任务信息" /> : <Space orientation="vertical" size={24} style={{ width: '100%' }}>
        <div>
          <Space><span className="status-dot" style={{ background: statusColors[task.status] }} /><Typography.Title level={4} style={{ margin: 0 }}>{taskStatusLabels[task.status]}</Typography.Title><Tag>{stageLabels[task.stage] ?? task.stage}</Tag></Space>
          <Progress percent={task.progress} status={task.status === 'FAILED' ? 'exception' : task.status === 'SUCCESS' ? 'success' : 'active'} />
        </div>
        <Descriptions column={2} size="small" bordered>
          <Descriptions.Item label="任务编号" span={2}>{task.taskNo}</Descriptions.Item>
          <Descriptions.Item label="导出方式">{task.exportType === 'SELECTED' ? '已选订单' : '条件导出'}</Descriptions.Item>
          <Descriptions.Item label="预计/实际">{task.expectedCount.toLocaleString()} / {task.exportedCount.toLocaleString()}</Descriptions.Item>
          <Descriptions.Item label="快照时间">{data.snapshotTime ? dayjs(data.snapshotTime).format('YYYY-MM-DD HH:mm:ss') : '—'}</Descriptions.Item>
          <Descriptions.Item label="快照最大 ID">{data.snapshotMaxId ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="文件大小">{formatBytes(task.fileSize)}</Descriptions.Item>
          <Descriptions.Item label="下载次数">{data.downloadCount}</Descriptions.Item>
          <Descriptions.Item label="当前 Run">Run {task.currentRunNo}</Descriptions.Item>
          <Descriptions.Item label="手动重试">{task.manualRetryCount} / {task.manualRetryLimit}</Descriptions.Item>
          {data.filterSnapshotJson && <Descriptions.Item label="筛选快照" span={2}><Typography.Paragraph code copyable style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.filterSnapshotJson}</Typography.Paragraph></Descriptions.Item>}
          {task.failureMessage && <Descriptions.Item label="失败原因" span={2}><Typography.Text type="danger">[{task.failureCode}] {task.failureMessage}</Typography.Text></Descriptions.Item>}
        </Descriptions>
        <div>
          <Typography.Title level={5}>Run 历史</Typography.Title>
          <Collapse defaultActiveKey={task.currentRunNo === 0 ? runItems[0]?.key : data.runs.find((run) => run.runNo === task.currentRunNo)?.id} items={runItems} />
        </div>
      </Space>}
    </Drawer>
  )
}
