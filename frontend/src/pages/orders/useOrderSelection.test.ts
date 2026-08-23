import { normalizeSelection } from './useOrderSelection'

test('normalizes cross-page selection', () => {
  expect(normalizeSelection([3, 1, 3, 2])).toEqual([3, 1, 2])
})

test('enforces selection limit', () => {
  expect(() => normalizeSelection([1, 2, 3], 2)).toThrow('最多选择 2 条订单')
})
