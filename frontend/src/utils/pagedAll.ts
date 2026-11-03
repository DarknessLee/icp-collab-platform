import type { PageResult } from '@/types/system'

/** 拉取全部筛选结果（导出/下拉选项用），最多 10 页避免异常数据死循环 */
export async function fetchAllPages<T>(
  fetcher: (params: Record<string, unknown>) => Promise<PageResult<T>>,
  params: Record<string, unknown> = {},
  pageSize = 200
): Promise<T[]> {
  const rows: T[] = []
  for (let pageNum = 1; pageNum <= 10; pageNum += 1) {
    const page = await fetcher({ ...params, pageNum, pageSize })
    rows.push(...page.list)
    if (page.list.length < pageSize || rows.length >= (page.total || 0)) break
  }
  return rows
}
