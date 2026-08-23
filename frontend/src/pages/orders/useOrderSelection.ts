import { useMemo, useState } from 'react'

export function normalizeSelection(ids: readonly number[], limit = 5000): number[] {
  const normalized = [...new Set(ids.filter((id) => Number.isInteger(id) && id > 0))]
  if (normalized.length > limit) throw new Error(`最多选择 ${limit} 条订单`)
  return normalized
}

export function useOrderSelection(limit = 5000) {
  const [ids, setIds] = useState<number[]>([])
  return useMemo(() => ({
    ids,
    count: ids.length,
    replace(next: readonly number[]) { setIds(normalizeSelection(next, limit)) },
    clear() { setIds([]) },
  }), [ids, limit])
}

