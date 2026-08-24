import type { TaskProgressEvent, TaskStatus, TaskSummary } from '../../api/types'

export const taskStatusLabels: Record<TaskStatus, string> = {
  PENDING: '等待处理', PROCESSING: '正在导出', SUCCESS: '导出成功', FAILED: '导出失败', EXPIRED: '文件已过期',
}

export const stageLabels: Record<string, string> = {
  QUEUED: '等待处理', RETRY_WAITING: '等待自动重试', PREPARING: '正在准备文件',
  QUERYING_WRITING: '正在生成 Excel', FINALIZING: '正在校验文件', MOVING: '正在保存文件',
  RECOVERING: '正在恢复任务', COMPLETED: '已完成',
}

export const statusColors: Record<TaskStatus, string> = {
  PENDING: '#d89614', PROCESSING: '#3155d9', SUCCESS: '#2a9d67', FAILED: '#d84a4a', EXPIRED: '#8a92a6',
}

export function mergeTaskEvent(task: TaskSummary, event: TaskProgressEvent): TaskSummary {
  if (task.id !== event.taskId) return task
  const newRun = event.currentRunNo > task.currentRunNo
  const terminal = ['SUCCESS', 'FAILED', 'EXPIRED'].includes(task.status)
  if (!newRun && terminal && event.status === 'PROCESSING') return task
  return {
    ...task,
    status: event.status,
    stage: event.stage,
    currentRunNo: event.currentRunNo,
    manualRetryCount: Math.max(task.manualRetryCount, event.currentRunNo),
    progress: newRun ? event.progress : Math.max(task.progress, event.progress),
    expectedCount: event.expectedCount,
    exportedCount: newRun ? event.exportedCount : Math.max(task.exportedCount, event.exportedCount),
    fileSize: event.fileSize ?? task.fileSize,
    fileExpireAt: event.fileExpireAt ?? task.fileExpireAt,
  }
}

export function formatBytes(value?: number) {
  if (value == null) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 ** 2) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 ** 2).toFixed(1)} MB`
}
