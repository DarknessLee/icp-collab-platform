import { reactive, ref, type Ref } from 'vue'
import type { PageResult } from '@/types/system'

/** 查询状态：pageNum/pageSize 强类型，其余筛选条件按模块扩展 */
export type PageQueryState = {
  pageNum: number
  pageSize: number
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any
}

/**
 * 分页列表通用逻辑：查询条件、列表、总数、加载态、搜索/重置。
 * 后端返回数组或分页对象均已在 api 层归一化为 PageResult。
 */
export function usePageList<T>(
  fetcher: (params: Record<string, unknown>) => Promise<PageResult<T>>,
  defaults: Record<string, unknown> = {}
) {
  const initial: PageQueryState = { pageNum: 1, pageSize: 10, ...defaults }
  const query = reactive<PageQueryState>({ ...initial })
  const list = ref([]) as Ref<T[]>
  const total = ref(0)
  const loading = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      const result = await fetcher({ ...query })
      list.value = result.list || []
      total.value = result.total || 0
    } catch {
      // 失败原因已由 request.ts 统一提示；保留当前列表，避免页面抛未处理异常
    } finally {
      loading.value = false
    }
  }

  function search(): void {
    query.pageNum = 1
    void load()
  }

  function reset(): void {
    Object.keys(query).forEach((key) => {
      // eslint-disable-next-line @typescript-eslint/no-dynamic-delete
      delete query[key]
    })
    Object.assign(query, initial)
    void load()
  }

  function handlePageChange(page: number): void {
    query.pageNum = page
    void load()
  }

  function handleSizeChange(size: number): void {
    query.pageSize = size
    query.pageNum = 1
    void load()
  }

  return { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange }
}
