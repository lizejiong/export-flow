import { Descriptions, Drawer, Empty, Progress, Space, Table, Tag, Timeline, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import dayjs from 'dayjs'
import { api } from '../../api/client'
import { formatBytes, stageLabels, statusColors, taskStatusLabels } from './taskPresentation'

export default function TaskDetailDrawer({ taskId, onClose }: { taskId?: number; onClose: () => void }) {
  const detail = useQuery({ queryKey: ['task-detail', taskId], queryFn: () => api.taskDetail(taskId!), enabled: taskId != null })
  const data = detail.data
  const task = data?.task
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
          <Descriptions.Item label="手动重试">{task.manualRetryIndex} / 2</Descriptions.Item>
          <Descriptions.Item label="自动执行">{task.autoAttemptCount} / 3</Descriptions.Item>
          {data.filterSnapshotJson && <Descriptions.Item label="筛选快照" span={2}><Typography.Paragraph code copyable style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.filterSnapshotJson}</Typography.Paragraph></Descriptions.Item>}
          {task.failureMessage && <Descriptions.Item label="失败原因" span={2}><Typography.Text type="danger">[{task.failureCode}] {task.failureMessage}</Typography.Text></Descriptions.Item>}
        </Descriptions>
        <div>
          <Typography.Title level={5}>执行记录</Typography.Title>
          <Timeline items={data.attempts.map((attempt) => ({
            color: attempt.status === 'SUCCESS' ? 'green' : attempt.status === 'PROCESSING' ? 'blue' : 'red',
            content: <div><strong>第 {attempt.attemptNo} 次 · {attempt.status}</strong><div className="muted">{dayjs(attempt.startedAt).format('YYYY-MM-DD HH:mm:ss')} · {attempt.workerId} · token {attempt.executionTokenShort}</div>{attempt.failureMessage && <Typography.Text type="danger">[{attempt.failureCode}] {attempt.failureMessage}</Typography.Text>}</div>,
          }))} />
        </div>
        <div>
          <Typography.Title level={5}>重试链</Typography.Title>
          <Table size="small" pagination={false} rowKey="id" dataSource={data.retryChain} columns={[
            { title: '任务编号', dataIndex: 'taskNo' },
            { title: '手动次数', dataIndex: 'manualRetryIndex' },
            { title: '状态', dataIndex: 'status', render: (value) => taskStatusLabels[value as keyof typeof taskStatusLabels] },
          ]} />
        </div>
      </Space>}
    </Drawer>
  )
}
