import type { TaskProgressEvent, TaskSummary } from '../../api/types'
import { mergeTaskEvent } from './taskPresentation'

const task: TaskSummary = {
  id: 1, taskNo: 'EXP1', exportType: 'FILTER', status: 'PROCESSING', stage: 'QUERYING_WRITING',
  expectedCount: 100, exportedCount: 20, progress: 23, autoAttemptCount: 1, manualRetryIndex: 0,
  retryable: false, createdAt: '2026-08-22T10:00:00',
}

test('merges progress event without moving progress backwards', () => {
  const event: TaskProgressEvent = { eventType: 'task.progress', taskId: 1, status: 'PROCESSING', stage: 'QUERYING_WRITING', progress: 10, expectedCount: 100, exportedCount: 10, updatedAt: '' }
  expect(mergeTaskEvent(task, event).progress).toBe(23)
  expect(mergeTaskEvent(task, event).exportedCount).toBe(20)
})

test('ignores processing event after terminal state', () => {
  const event: TaskProgressEvent = { eventType: 'task.progress', taskId: 1, status: 'PROCESSING', stage: 'QUERYING_WRITING', progress: 50, expectedCount: 100, exportedCount: 50, updatedAt: '' }
  expect(mergeTaskEvent({ ...task, status: 'SUCCESS', progress: 100 }, event).status).toBe('SUCCESS')
})

test('merges download metadata from a successful SSE event', () => {
  const event: TaskProgressEvent = {
    eventType: 'task.succeeded', taskId: 1, status: 'SUCCESS', stage: 'COMPLETED', progress: 100,
    expectedCount: 100, exportedCount: 100, fileSize: 4096,
    fileExpireAt: '2026-08-24T22:00:00', updatedAt: '2026-08-23T22:00:00',
  }

  expect(mergeTaskEvent(task, event)).toMatchObject({
    status: 'SUCCESS', progress: 100, fileSize: 4096, fileExpireAt: '2026-08-24T22:00:00',
  })
})
