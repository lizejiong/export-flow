import { CloudDownloadOutlined, EyeOutlined, ReloadOutlined, RetweetOutlined, WifiOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Col, DatePicker, Form, Input, Progress, Row, Select, Space, Table, Tag, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, api, newIdempotencyKey } from '../../api/client'
import type { PageResponse, TaskFilters, TaskProgressEvent, TaskSummary } from '../../api/types'
import TaskDetailDrawer from './TaskDetailDrawer'
import { formatBytes, mergeTaskEvent, stageLabels, statusColors, taskReconciliationInterval, taskStatusLabels } from './taskPresentation'
import { useTaskEvents } from './useTaskEvents'

interface FilterValues extends Omit<TaskFilters, 'createdFrom' | 'createdTo'> { createdRange?: [Dayjs, Dayjs] }

export default function TasksPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const highlightedId = Number(searchParams.get('highlight')) || undefined
  const [filters, setFilters] = useState<TaskFilters>({})
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [detailId, setDetailId] = useState<number | undefined>()

  const onEvent = useCallback((event: TaskProgressEvent) => {
    queryClient.setQueriesData<PageResponse<TaskSummary>>({ queryKey: ['tasks'] }, (current) => current ? ({ ...current, items: current.items.map((task) => mergeTaskEvent(task, event)) }) : current)
    queryClient.invalidateQueries({ queryKey: ['task-detail', event.taskId] })
    if (['task.created', 'task.retrying', 'task.succeeded', 'task.failed', 'task.expired'].includes(event.eventType)) {
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
    }
  }, [queryClient])
  const eventMode = useTaskEvents(onEvent)

  const tasks = useQuery({
    queryKey: ['tasks', filters, page, pageSize], queryFn: () => api.tasks(filters, page, pageSize),
    refetchInterval(query) {
      if (eventMode !== 'sse') return 2_000
      const data = query.state.data as PageResponse<TaskSummary> | undefined
      return taskReconciliationInterval(eventMode, data?.items)
    },
  })

  useEffect(() => { if (highlightedId) setDetailId(highlightedId) }, [highlightedId])

  const retry = useMutation({
    mutationFn: (id: number) => api.retryTask(id, newIdempotencyKey()),
    onSuccess(task) {
      message.success(`已发起第 ${task.currentRunNo} 次手动重试`)
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: ['task-detail', task.id] })
      setDetailId(task.id)
    },
    onError(error) { message.error(error instanceof ApiError ? error.body.message : '重试失败') },
  })

  const search = (values: FilterValues) => {
    const { createdRange, ...rest } = values
    setFilters({ ...rest, createdFrom: createdRange?.[0].startOf('day').format('YYYY-MM-DDTHH:mm:ss'), createdTo: createdRange?.[1].endOf('day').format('YYYY-MM-DDTHH:mm:ss') })
    setPage(1)
  }

  const columns: TableColumnsType<TaskSummary> = [
    { title: '任务编号', dataIndex: 'taskNo', fixed: 'left', width: 230, render: (value) => <Typography.Text copyable>{value}</Typography.Text> },
    { title: '方式', dataIndex: 'exportType', width: 100, render: (value) => value === 'SELECTED' ? '已选订单' : '条件导出' },
    { title: '状态', dataIndex: 'status', width: 125, render: (value) => <span><span className="status-dot" style={{ background: statusColors[value as keyof typeof statusColors] }} />{taskStatusLabels[value as keyof typeof taskStatusLabels]}</span> },
    { title: '阶段', dataIndex: 'stage', width: 145, render: (value) => <Tag>{stageLabels[value] ?? value}</Tag> },
    { title: '进度', width: 210, render: (_, task) => <div><Progress percent={task.progress} size="small" status={task.status === 'FAILED' ? 'exception' : task.status === 'SUCCESS' ? 'success' : 'active'} /><span className="muted">{task.exportedCount.toLocaleString()} / {task.expectedCount.toLocaleString()}</span></div> },
    { title: '文件大小', dataIndex: 'fileSize', width: 100, render: formatBytes },
    { title: '尝试', width: 110, render: (_, task) => `${task.autoAttemptCount}/3 · ${task.manualRetryCount}/${task.manualRetryLimit}` },
    { title: '创建时间', dataIndex: 'createdAt', width: 170, render: (value) => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
    { title: '操作', fixed: 'right', width: 220, render: (_, task) => <Space>
      <Button size="small" icon={<EyeOutlined />} onClick={() => setDetailId(task.id)}>详情</Button>
      {task.status === 'SUCCESS' && task.fileExpireAt && dayjs(task.fileExpireAt).isAfter(dayjs()) && <Button size="small" type="link" icon={<CloudDownloadOutlined />} onClick={() => api.downloadTask(task.id, `${task.taskNo}.xlsx`).catch((error) => message.error(error instanceof ApiError ? error.body.message : '下载失败'))}>下载</Button>}
      {task.canManualRetry && <Button size="small" type="link" icon={<RetweetOutlined />} loading={retry.isPending} onClick={() => modal.confirm({ title: '手动重试？', content: `系统会复用原任务快照，在同一任务下发起 Run ${task.currentRunNo + 1}。`, onOk: () => retry.mutate(task.id) })}>重试</Button>}
    </Space> },
  ]

  return <>
    <div className="page-heading">
      <div><Typography.Title level={2}>导出任务</Typography.Title><p>实时查看队列、生成进度，以及按 Run / Attempt 分层的执行历史。</p></div>
      <Space><Tag color={eventMode === 'sse' ? 'green' : eventMode === 'polling' ? 'orange' : 'default'} icon={<WifiOutlined />}>{eventMode === 'sse' ? 'SSE 实时' : eventMode === 'polling' ? '轮询降级' : '正在连接'}</Tag><Button icon={<ReloadOutlined />} onClick={() => tasks.refetch()}>刷新</Button></Space>
    </div>
    <div className="panel filter-panel">
      <Form layout="vertical" onFinish={search}>
        <Row gutter={16}>
          <Col xs={24} md={8} xl={6}><Form.Item name="taskNo" label="任务编号"><Input allowClear /></Form.Item></Col>
          <Col xs={12} md={6} xl={4}><Form.Item name="exportType" label="导出方式"><Select allowClear options={[{ value: 'SELECTED', label: '已选订单' }, { value: 'FILTER', label: '条件导出' }]} /></Form.Item></Col>
          <Col xs={12} md={6} xl={4}><Form.Item name="status" label="任务状态"><Select allowClear options={Object.entries(taskStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item></Col>
          <Col xs={24} md={10} xl={7}><Form.Item name="createdRange" label="创建时间"><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item></Col>
          <Col flex="auto" style={{ display: 'flex', alignItems: 'end', justifyContent: 'flex-end', paddingBottom: 24 }}><Button type="primary" htmlType="submit">查询任务</Button></Col>
        </Row>
      </Form>
    </div>
    <div className="panel table-panel">
      <Table<TaskSummary>
        rowKey="id" columns={columns} dataSource={tasks.data?.items ?? []} loading={tasks.isLoading}
        scroll={{ x: 1400 }} rowClassName={(record) => record.id === highlightedId ? 'highlight-row' : ''}
        pagination={{ current: page, pageSize, total: tasks.data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showTotal: (total) => `共 ${total.toLocaleString()} 条`, onChange(next, size) { setPage(next); setPageSize(size) } }}
      />
    </div>
    <TaskDetailDrawer taskId={detailId} onClose={() => setDetailId(undefined)} />
  </>
}
