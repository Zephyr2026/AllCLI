import { reactive, ref, type Ref } from 'vue'
import type { PageResponse } from '@/types/api'

/**
 * 分页列表通用逻辑：把 Element Plus 的 1 基页码转换为后端的 0 基 page 参数。
 * fetchFn 只管发请求，过滤参数由 filters 承载（同名透传）。
 */
export function usePage<T, F extends object>(
  fetchFn: (p: { page: number; size: number } & F) => Promise<PageResponse<T>>,
  defaultFilters: F,
) {
  const items: Ref<T[]> = ref([])
  const total = ref(0)
  const loading = ref(false)
  const currentPage = ref(1) // 1 基，界面用
  const pageSize = ref(20)
  const filters = reactive({ ...defaultFilters })

  async function load() {
    loading.value = true
    try {
      const resp = await fetchFn({
        page: currentPage.value - 1, // → 0 基
        size: Math.min(pageSize.value, 100),
        ...filters,
      } as { page: number; size: number } & F)
      items.value = resp.items
      total.value = resp.total
      currentPage.value = resp.page + 1 // → 1 基
    } finally {
      loading.value = false
    }
  }

  function search() {
    currentPage.value = 1
    void load()
  }

  return { items, total, loading, currentPage, pageSize, filters, load, search }
}
