import type { TaskProgressEvent, TaskSummary } from '../../api/types'
import { mergeTaskEvent, taskReconciliationInterval } from './taskPresentation'

const task: TaskSummary = {
  id: 1, version: 10, taskNo: 'EXP1', exportType: 'FILTER', status: 'PROCESSING', stage: 'QUERYING_WRITING',
  expectedCount: 100, exportedCount: 20, progress: 23, autoAttemptCount: 1, currentRunNo: 0,
  manualRetryCount: 0, manualRetryLimit: 2, canManualRetry: false,
  retryable: false, createdAt: '2026-08-22T10:00:00',
}

test('ignores an older progress event by database version', () => {
  const event: TaskProgressEvent = { eventType: 'task.progress', taskId: 1, version: 9, status: 'PROCESSING', stage: 'QUERYING_WRITING', currentRunNo: 0, progress: 10, expectedCount: 100, exportedCount: 10, updatedAt: '' }
  expect(mergeTaskEvent(task, event).progress).toBe(23)
  expect(mergeTaskEvent(task, event).exportedCount).toBe(20)
})

test('ignores processing event older than a terminal state', () => {
  const event: TaskProgressEvent = { eventType: 'task.progress', taskId: 1, version: 9, status: 'PROCESSING', stage: 'QUERYING_WRITING', currentRunNo: 0, progress: 50, expectedCount: 100, exportedCount: 50, updatedAt: '' }
  expect(mergeTaskEvent({ ...task, status: 'SUCCESS', progress: 100 }, event).status).toBe('SUCCESS')
})

test('merges download metadata from a successful SSE event', () => {
  const event: TaskProgressEvent = {
    eventType: 'task.succeeded', taskId: 1, version: 11, status: 'SUCCESS', stage: 'COMPLETED', progress: 100,
    currentRunNo: 0,
    expectedCount: 100, exportedCount: 100, fileSize: 4096,
    fileExpireAt: '2026-08-24T22:00:00', updatedAt: '2026-08-23T22:00:00',
  }

  expect(mergeTaskEvent(task, event)).toMatchObject({
    status: 'SUCCESS', progress: 100, fileSize: 4096, fileExpireAt: '2026-08-24T22:00:00',
  })
})

test('resets progress when a newer manual run starts', () => {
  const event: TaskProgressEvent = {
    eventType: 'task.progress', taskId: 1, version: 11, status: 'PROCESSING', stage: 'PREPARING', currentRunNo: 1,
    progress: 1, expectedCount: 100, exportedCount: 0, updatedAt: '',
  }

  expect(mergeTaskEvent({ ...task, status: 'FAILED', progress: 80, exportedCount: 70 }, event)).toMatchObject({
    status: 'PROCESSING', currentRunNo: 1, progress: 1, exportedCount: 0, manualRetryCount: 1,
  })
})

test('accepts a progress reset for a newer automatic attempt in the same run', () => {
  const event: TaskProgressEvent = {
    eventType: 'task.retrying', taskId: 1, version: 11, status: 'PENDING', stage: 'RETRY_WAITING',
    currentRunNo: 0, progress: 0, expectedCount: 100, exportedCount: 0, updatedAt: '',
  }

  expect(mergeTaskEvent(task, event)).toMatchObject({ version: 11, progress: 0, exportedCount: 0 })
})

test('keeps low-frequency REST reconciliation while SSE is healthy', () => {
  expect(taskReconciliationInterval('sse', [task])).toBe(15_000)
  expect(taskReconciliationInterval('sse', [{ ...task, status: 'SUCCESS' }])).toBe(60_000)
  expect(taskReconciliationInterval('polling', [task])).toBe(2_000)
})
