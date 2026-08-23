import type {
  ApiErrorBody, ApiResponse, CreateTaskRequest, CreatedTask, ExportCount, Order, OrderFilters,
  PageResponse, TaskDetail, TaskFilters, TaskSummary,
} from './types'

export class ApiError extends Error {
  constructor(public status: number, public body: ApiErrorBody) {
    super(body.message)
  }
}

function queryString(values: object): string {
  const params = new URLSearchParams()
  Object.entries(values as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    if (Array.isArray(value)) value.forEach((item) => params.append(key, String(item)))
    else params.set(key, String(value))
  })
  const value = params.toString()
  return value ? `?${value}` : ''
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  let envelope: ApiResponse<T> | undefined
  try { envelope = await response.json() as ApiResponse<T> } catch { /* handled below */ }
  if (!response.ok || !envelope || envelope.code !== 'SUCCESS') {
    const fallback: ApiErrorBody = {
      code: 'HTTP_ERROR',
      message: `请求失败（${response.status}）`,
      data: null,
      requestId: response.headers.get('X-Request-Id') ?? 'unknown',
      timestamp: new Date().toISOString(),
    }
    throw new ApiError(response.status, (envelope as ApiErrorBody | undefined) ?? fallback)
  }
  return envelope.data
}

export const newIdempotencyKey = () => crypto.randomUUID()

export const api = {
  orders(filters: OrderFilters, page: number, pageSize: number) {
    return request<PageResponse<Order>>(`/api/orders${queryString({ ...filters, page, pageSize })}`)
  },
  exportCount(filters: OrderFilters) {
    return request<ExportCount>(`/api/orders/export-count${queryString(filters)}`)
  },
  createTask(payload: CreateTaskRequest, key: string) {
    return request<CreatedTask>('/api/export-tasks', {
      method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify(payload),
    })
  },
  tasks(filters: TaskFilters, page: number, pageSize: number) {
    return request<PageResponse<TaskSummary>>(`/api/export-tasks${queryString({ ...filters, page, pageSize })}`)
  },
  taskDetail(id: number) { return request<TaskDetail>(`/api/export-tasks/${id}`) },
  retryTask(id: number, key: string) {
    return request<CreatedTask>(`/api/export-tasks/${id}/retry`, {
      method: 'POST', headers: { 'Idempotency-Key': key },
    })
  },
  async downloadTask(id: number, fallbackName: string) {
    const response = await fetch(`/api/export-tasks/${id}/download`)
    if (!response.ok) {
      let body: ApiErrorBody = {
        code: 'HTTP_ERROR', message: '下载失败', data: null,
        requestId: response.headers.get('X-Request-Id') ?? 'unknown', timestamp: new Date().toISOString(),
      }
      try { body = await response.json() as ApiErrorBody } catch { /* keep fallback */ }
      throw new ApiError(response.status, body)
    }
    const blob = await response.blob()
    const disposition = response.headers.get('Content-Disposition')
    const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/)?.[1]
    const fileName = encoded ? decodeURIComponent(encoded) : fallbackName
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    anchor.click()
    URL.revokeObjectURL(url)
  },
}
