export type OrderStatus = 'PENDING_PAYMENT' | 'PENDING_SHIPMENT' | 'SHIPPED' | 'COMPLETED' | 'CLOSED'
export type PaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED'
export type PaymentMethod = 'ALIPAY' | 'WECHAT' | 'BANK_CARD'
export type OrderSource = 'WEB' | 'APP' | 'MINI_PROGRAM'
export type ExportType = 'SELECTED' | 'FILTER'
export type TaskStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'EXPIRED'

export interface Order {
  id: number
  orderNo: string
  customerName: string
  customerPhone: string
  orderStatus: OrderStatus
  paymentStatus: PaymentStatus
  paymentMethod: PaymentMethod
  orderSource: OrderSource
  itemCount: number
  totalAmount: number
  shippingProvince: string
  createdAt: string
  paidAt?: string
  updatedAt: string
}

export interface OrderFilters {
  orderNo?: string
  customerName?: string
  customerPhone?: string
  orderStatuses?: OrderStatus[]
  paymentStatuses?: PaymentStatus[]
  paymentMethods?: PaymentMethod[]
  orderSources?: OrderSource[]
  minAmount?: number
  maxAmount?: number
  createdFrom?: string
  createdTo?: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface ExportCount { count: number; limitExceeded: boolean }

export interface CreateTaskRequest {
  exportType: ExportType
  selectedOrderIds?: number[]
  filters?: OrderFilters
}

export interface CreatedTask {
  id: number
  taskNo: string
  exportType: ExportType
  status: TaskStatus
  expectedCount: number
  progress: number
  currentRunNo: number
  createdAt: string
  idempotentReplay: boolean
}

export interface TaskSummary {
  id: number
  taskNo: string
  exportType: ExportType
  status: TaskStatus
  stage: string
  expectedCount: number
  exportedCount: number
  progress: number
  fileSize?: number
  fileExpireAt?: string
  autoAttemptCount: number
  currentRunNo: number
  manualRetryCount: number
  manualRetryLimit: number
  canManualRetry: boolean
  retryable: boolean
  failureCode?: string
  failureMessage?: string
  createdAt: string
  startedAt?: string
  completedAt?: string
}

export interface TaskAttempt {
  attemptNo: number
  executionTokenShort?: string
  workerId: string
  status: string
  startedAt: string
  finishedAt?: string
  failureCode?: string
  failureMessage?: string
}

export interface TaskRun {
  id: number
  runNo: number
  triggerType: 'INITIAL' | 'MANUAL_RETRY' | 'RECOVERY'
  status: TaskStatus
  stage: string
  expectedCount: number
  exportedCount: number
  progress: number
  autoAttemptCount: number
  retryable: boolean
  failureCode?: string
  failureMessage?: string
  fileSize?: number
  fileExpireAt?: string
  createdAt: string
  startedAt?: string
  completedAt?: string
  attempts: TaskAttempt[]
}

export interface TaskDetail {
  task: TaskSummary
  filterSnapshotJson?: string
  snapshotMaxId?: number
  snapshotTime?: string
  selectedCount: number
  downloadCount: number
  lastDownloadedAt?: string
  runs: TaskRun[]
}

export interface TaskFilters {
  taskNo?: string
  exportType?: ExportType
  status?: TaskStatus
  createdFrom?: string
  createdTo?: string
}

export interface TaskProgressEvent {
  eventType: string
  taskId: number
  status: TaskStatus
  stage: string
  currentRunNo: number
  progress: number
  expectedCount: number
  exportedCount: number
  fileSize?: number
  fileExpireAt?: string
  updatedAt: string
}

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  requestId: string
  timestamp: string
}

export type ApiErrorBody = ApiResponse<Record<string, unknown> | null>
