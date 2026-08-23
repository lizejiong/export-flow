import { useEffect, useState } from 'react'
import type { TaskProgressEvent } from '../../api/types'

const EVENT_NAMES = ['task.created', 'task.progress', 'task.retrying', 'task.succeeded', 'task.failed', 'task.expired']

export type TaskEventMode = 'connecting' | 'sse' | 'polling'

export function useTaskEvents(onEvent: (event: TaskProgressEvent) => void) {
  const [mode, setMode] = useState<TaskEventMode>('connecting')

  useEffect(() => {
    let source: EventSource | undefined
    let stopped = false
    let failures = 0
    let fallback = false
    let timer: number | undefined

    const connect = () => {
      if (stopped) return
      const current = new EventSource('/api/export-tasks/events')
      source = current
      current.onopen = () => {
        failures = 0
        fallback = false
        setMode('sse')
      }
      const handler = (raw: Event) => {
        try { onEvent(JSON.parse((raw as MessageEvent<string>).data) as TaskProgressEvent) } catch { /* next REST refresh repairs state */ }
      }
      EVENT_NAMES.forEach((name) => current.addEventListener(name, handler))
      current.onerror = () => {
        current.close()
        failures++
        if (failures >= 3 || fallback) {
          fallback = true
          setMode('polling')
          failures = 0
          timer = window.setTimeout(connect, 5_000)
        } else {
          setMode('connecting')
          timer = window.setTimeout(connect, 1_000)
        }
      }
    }
    connect()
    return () => { stopped = true; source?.close(); if (timer) window.clearTimeout(timer) }
  }, [onEvent])

  return mode
}
