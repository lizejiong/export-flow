import type { OrderSource, OrderStatus, PaymentMethod, PaymentStatus } from '../../api/types'

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待付款', PENDING_SHIPMENT: '待发货', SHIPPED: '已发货', COMPLETED: '已完成', CLOSED: '已关闭',
}
export const paymentStatusLabels: Record<PaymentStatus, string> = { UNPAID: '未支付', PAID: '已支付', REFUNDED: '已退款' }
export const paymentMethodLabels: Record<PaymentMethod, string> = { ALIPAY: '支付宝', WECHAT: '微信', BANK_CARD: '银行卡' }
export const orderSourceLabels: Record<OrderSource, string> = { WEB: 'Web', APP: 'App', MINI_PROGRAM: '小程序' }

export const options = <T extends string>(labels: Record<T, string>) =>
  Object.entries(labels).map(([value, label]) => ({ value, label: String(label) }))

