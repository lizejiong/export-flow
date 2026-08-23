import { Button, Col, DatePicker, Form, Input, InputNumber, Row, Select, Space } from 'antd'
import type { Dayjs } from 'dayjs'
import type { OrderFilters } from '../../api/types'
import { options, orderSourceLabels, orderStatusLabels, paymentMethodLabels, paymentStatusLabels } from './orderPresentation'

interface Values extends Omit<OrderFilters, 'createdFrom' | 'createdTo'> { createdRange?: [Dayjs, Dayjs] }

export default function OrderFilterForm({ onSearch }: { onSearch: (filters: OrderFilters) => void }) {
  const [form] = Form.useForm<Values>()
  const submit = (values: Values) => {
    const { createdRange, ...rest } = values
    onSearch({
      ...rest,
      createdFrom: createdRange?.[0].startOf('day').format('YYYY-MM-DDTHH:mm:ss'),
      createdTo: createdRange?.[1].endOf('day').format('YYYY-MM-DDTHH:mm:ss'),
    })
  }
  return (
    <Form form={form} layout="vertical" onFinish={submit}>
      <Row gutter={16}>
        <Col xs={24} md={8} xl={6}><Form.Item name="orderNo" label="订单号"><Input allowClear placeholder="精确查询" /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}><Form.Item name="customerName" label="客户姓名"><Input allowClear placeholder="支持模糊查询" /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}><Form.Item name="customerPhone" label="客户手机号"><Input allowClear placeholder="精确查询" /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}><Form.Item name="orderStatuses" label="订单状态"><Select allowClear mode="multiple" maxTagCount="responsive" options={options(orderStatusLabels)} /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}><Form.Item name="paymentStatuses" label="支付状态"><Select allowClear mode="multiple" options={options(paymentStatusLabels)} /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}><Form.Item name="paymentMethods" label="支付方式"><Select allowClear mode="multiple" options={options(paymentMethodLabels)} /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}><Form.Item name="orderSources" label="订单来源"><Select allowClear mode="multiple" options={options(orderSourceLabels)} /></Form.Item></Col>
        <Col xs={24} md={8} xl={6}>
          <Form.Item label="订单金额">
            <Space.Compact block>
              <Form.Item name="minAmount" noStyle><InputNumber min={0} precision={2} placeholder="最低" style={{ width: '50%' }} /></Form.Item>
              <Form.Item name="maxAmount" noStyle dependencies={['minAmount']} rules={[({ getFieldValue }) => ({ validator(_, value) { return value == null || getFieldValue('minAmount') == null || value >= getFieldValue('minAmount') ? Promise.resolve() : Promise.reject(new Error('最高金额不能小于最低金额')) } })]}><InputNumber min={0} precision={2} placeholder="最高" style={{ width: '50%' }} /></Form.Item>
            </Space.Compact>
          </Form.Item>
        </Col>
        <Col xs={24} md={12} xl={8}><Form.Item name="createdRange" label="下单时间"><DatePicker.RangePicker style={{ width: '100%' }} /></Form.Item></Col>
        <Col flex="auto" style={{ display: 'flex', alignItems: 'end', justifyContent: 'flex-end', paddingBottom: 24 }}>
          <Space>
            <Button onClick={() => { form.resetFields(); onSearch({}) }}>重置</Button>
            <Button type="primary" htmlType="submit">查询订单</Button>
          </Space>
        </Col>
      </Row>
    </Form>
  )
}

