import { DatabaseOutlined, FileExcelOutlined } from '@ant-design/icons'
import { Layout, Menu, Typography } from 'antd'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import OrdersPage from './pages/orders/OrdersPage'
import TasksPage from './pages/tasks/TasksPage'

function Shell() {
  const location = useLocation()
  const navigate = useNavigate()
  return (
    <Layout className="app-shell">
      <Layout.Sider width={244} className="app-sider" breakpoint="lg" collapsedWidth="0">
        <div className="brand">
          <div className="brand-mark">EF</div>
          <div><Typography.Title level={4}>Export Flow</Typography.Title><span>异步导出实验室</span></div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname.startsWith('/tasks') ? '/tasks' : '/orders']}
          onClick={({ key }) => navigate(key)}
          items={[
            { key: '/orders', icon: <DatabaseOutlined />, label: '订单中心' },
            { key: '/tasks', icon: <FileExcelOutlined />, label: '导出任务' },
          ]}
        />
      </Layout.Sider>
      <Layout.Content className="app-content">
        <Routes>
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/tasks" element={<TasksPage />} />
          <Route path="*" element={<Navigate to="/orders" replace />} />
        </Routes>
      </Layout.Content>
    </Layout>
  )
}

export default function App() {
  return <BrowserRouter><Shell /></BrowserRouter>
}

