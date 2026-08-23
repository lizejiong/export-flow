import { ClearOutlined, ExportOutlined, FileExcelOutlined, ReloadOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { App, Button, Progress, Space, Table, Tag, Typography } from 'antd'
import type { TableColumnsType } from 'antd'
import dayjs from 'dayjs'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, newIdempotencyKey } from '../../api/client'
import type { CreateTaskRequest, Order, OrderFilters } from '../../api/types'
import OrderFilterForm from './OrderFilterForm'
import { orderSourceLabels, orderStatusLabels, paymentMethodLabels, paymentStatusLabels } from './orderPresentation'
import { useOrderSelection } from './useOrderSelection'

const hasFilters = (filters: OrderFilters) => Object.values(filters).some((value) => Array.isArray(value) ? value.length > 0 : value !== undefined && value !== '')

export default function OrdersPage() {
  const { message, modal, notification } = App.useApp()
  const navigate = useNavigate()
  const [filters, setFilters] = useState<OrderFilters>({})
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const selection = useOrderSelection()

  const orders = useQuery({
    queryKey: ['orders', filters, page, pageSize],
    queryFn: () => api.orders(filters, page, pageSize),
  })

  const createTask = useMutation({
    mutationFn: ({ payload, key }: { payload: CreateTaskRequest; key: string; clearSelection: boolean }) => api.createTask(payload, key),
    onSuccess(task, variables) {
      if (variables.clearSelection) selection.clear()
      notification.success({
        title: '导出任务已创建',
        description: `任务编号：${task.taskNo}，预计 ${task.expectedCount.toLocaleString()} 条`,
        actions: <Button type="link" onClick={() => navigate(`/tasks?highlight=${task.id}`)}>查看任务</Button>,
      })
    },
    onError(error) { message.error(error instanceof ApiError ? error.body.message : '创建导出任务失败') },
  })

  const applyFilters = (next: OrderFilters) => {
    const apply = () => { selection.clear(); setFilters(next); setPage(1) }
    if (selection.count === 0) return apply()
    modal.confirm({ title: '修改筛选条件？', content: '修改筛选条件将清空已选择的订单。', onOk: apply })
  }

  const exportSelected = () => {
    modal.confirm({
      title: '导出已选订单',
      content: `将创建包含 ${selection.count.toLocaleString()} 条已选订单的异步任务。`,
      okText: '创建任务',
      onOk: () => createTask.mutate({
        payload: { exportType: 'SELECTED', selectedOrderIds: selection.ids },
        key: newIdempotencyKey(), clearSelection: true,
      }),
    })
  }

  const exportFiltered = async () => {
    try {
      const count = await api.exportCount(filters)
      if (count.count === 0) return message.warning('当前条件没有可导出的订单')
      if (count.limitExceeded) return message.warning('预计数据超过 100 万条，请缩小筛选范围')
      modal.confirm({
        title: hasFilters(filters) ? '按当前条件导出' : '确认导出全部订单？',
        content: <div><p>预计导出 <strong>{count.count.toLocaleString()}</strong> 条订单。</p>{!hasFilters(filters) && <Typography.Text type="warning">当前没有设置任何筛选条件。</Typography.Text>}</div>,
        okText: '创建任务',
        onOk: () => createTask.mutate({ payload: { exportType: 'FILTER', filters }, key: newIdempotencyKey(), clearSelection: false }),
      })
    } catch (error) { message.error(error instanceof ApiError ? error.body.message : '统计导出数量失败') }
  }

  const columns: TableColumnsType<Order> = [
    { title: '订单号', dataIndex: 'orderNo', fixed: 'left', width: 175, render: (value) => <Typography.Text copyable>{value}</Typography.Text> },
    { title: '客户', dataIndex: 'customerName', width: 110 },
    { title: '手机号', dataIndex: 'customerPhone', width: 135 },
    { title: '订单状态', dataIndex: 'orderStatus', width: 110, render: (value) => <Tag color={value === 'COMPLETED' ? 'green' : value === 'CLOSED' ? 'default' : 'blue'}>{orderStatusLabels[value as keyof typeof orderStatusLabels]}</Tag> },
    { title: '支付状态', dataIndex: 'paymentStatus', width: 100, render: (value) => paymentStatusLabels[value as keyof typeof paymentStatusLabels] },
    { title: '支付方式', dataIndex: 'paymentMethod', width: 100, render: (value) => paymentMethodLabels[value as keyof typeof paymentMethodLabels] },
    { title: '来源', dataIndex: 'orderSource', width: 90, render: (value) => orderSourceLabels[value as keyof typeof orderSourceLabels] },
    { title: '商品数', dataIndex: 'itemCount', width: 80 },
    { title: '订单金额', dataIndex: 'totalAmount', align: 'right', width: 110, render: (value) => `¥${Number(value).toFixed(2)}` },
    { title: '省份', dataIndex: 'shippingProvince', width: 90 },
    { title: '下单时间', dataIndex: 'createdAt', width: 170, render: (value) => dayjs(value).format('YYYY-MM-DD HH:mm:ss') },
  ]

  return <>
    <div className="page-heading">
      <div><Typography.Title level={2}>订单中心</Typography.Title><p>筛选订单、跨页选择，然后将大数据导出交给后台队列处理。</p></div>
      <Button icon={<ReloadOutlined />} onClick={() => orders.refetch()}>刷新</Button>
    </div>
    <div className="panel filter-panel"><OrderFilterForm onSearch={applyFilters} /></div>
    <div className="panel table-panel">
      <div className="selection-bar">
        <Space><Typography.Text>已选择 <strong>{selection.count.toLocaleString()}</strong> 条</Typography.Text>{selection.count > 0 && <Button type="link" icon={<ClearOutlined />} onClick={selection.clear}>清空</Button>}</Space>
        <Space>
          <Button icon={<ExportOutlined />} disabled={selection.count === 0} loading={createTask.isPending} onClick={exportSelected}>导出已选</Button>
          <Button type="primary" icon={<FileExcelOutlined />} loading={createTask.isPending} onClick={exportFiltered}>按条件导出</Button>
        </Space>
      </div>
      {orders.isFetching && orders.data && <Progress percent={100} showInfo={false} status="active" size="small" />}
      <Table<Order>
        rowKey="id" columns={columns} dataSource={orders.data?.items ?? []} loading={orders.isLoading}
        scroll={{ x: 1250 }}
        rowSelection={{
          selectedRowKeys: selection.ids,
          preserveSelectedRowKeys: true,
          onChange(keys) {
            try { selection.replace(keys.map(Number)) } catch (error) { message.warning((error as Error).message) }
          },
        }}
        pagination={{ current: page, pageSize, total: orders.data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [20, 50, 100], showTotal: (total) => `共 ${total.toLocaleString()} 条`, onChange(next, size) { setPage(next); setPageSize(size) } }}
      />
    </div>
  </>
}
