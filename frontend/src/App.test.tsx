import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

vi.mock('./pages/orders/OrdersPage', () => ({ default: () => <div>订单页面内容</div> }))
vi.mock('./pages/tasks/TasksPage', () => ({ default: () => <div>任务页面内容</div> }))

test('renders product navigation and redirects to orders', async () => {
  window.history.pushState({}, '', '/')
  render(<QueryClientProvider client={new QueryClient()}><App /></QueryClientProvider>)
  expect(screen.getByText('订单中心')).toBeInTheDocument()
  expect(screen.getByText('导出任务')).toBeInTheDocument()
  expect(await screen.findByText('订单页面内容')).toBeInTheDocument()
})
