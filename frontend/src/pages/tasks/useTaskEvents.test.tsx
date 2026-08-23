import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useTaskEvents } from './useTaskEvents'

class FakeEventSource {
  static instances: FakeEventSource[] = []
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  close = vi.fn()

  constructor(public readonly url: string) {
    FakeEventSource.instances.push(this)
  }

  addEventListener() {}
}

describe('useTaskEvents', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('reports the SSE connection lifecycle', () => {
    const { result } = renderHook(() => useTaskEvents(vi.fn()))

    expect(result.current).toBe('connecting')
    act(() => FakeEventSource.instances[0].onopen?.())
    expect(result.current).toBe('sse')
  })

  it('falls back after three SSE failures and recovers after five seconds', () => {
    const { result } = renderHook(() => useTaskEvents(vi.fn()))

    act(() => FakeEventSource.instances[0].onerror?.())
    act(() => vi.advanceTimersByTime(1_000))
    act(() => FakeEventSource.instances[1].onerror?.())
    act(() => vi.advanceTimersByTime(1_000))
    act(() => FakeEventSource.instances[2].onerror?.())

    expect(result.current).toBe('polling')

    act(() => vi.advanceTimersByTime(5_000))
    act(() => FakeEventSource.instances[3].onopen?.())
    expect(result.current).toBe('sse')
  })
})
