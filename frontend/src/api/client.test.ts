import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

describe('api client response envelope', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('unwraps successful business data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'SUCCESS',
      message: '操作成功',
      data: { items: [], page: 1, pageSize: 20, total: 0 },
      requestId: 'request-123',
      timestamp: '2026-08-23T22:00:00+08:00',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(api.orders({}, 1, 20)).resolves.toEqual({
      items: [], page: 1, pageSize: 20, total: 0,
    })
  })

  it('keeps the unified error envelope', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'NO_EXPORT_DATA',
      message: '没有可导出的订单',
      data: { filter: 'empty' },
      requestId: 'request-456',
      timestamp: '2026-08-23T22:00:00+08:00',
    }), { status: 422, headers: { 'Content-Type': 'application/json' } })))

    const error = await api.orders({}, 1, 20).catch((reason: unknown) => reason)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).body).toMatchObject({
      code: 'NO_EXPORT_DATA', requestId: 'request-456', data: { filter: 'empty' },
    })
  })
})
