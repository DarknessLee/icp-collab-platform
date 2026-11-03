import { reactive } from 'vue'
import { dictApi } from '@/api/system'
import { useUserStore } from '@/stores/user'

export type DictTagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export interface DictOption {
  value: number
  label: string
  tagType: DictTagType
}

/**
 * 内控域枚举字典：优先取后端 /dicts/{type}/items，失败或缺失时用前端兜底常量。
 * 键名与 docs/04 §4 的 sys_dict_type 一致（icp_*）。
 */
const FALLBACK: Record<string, DictOption[]> = {
  icp_policy_status: [
    { value: 0, label: '草稿', tagType: 'info' },
    { value: 1, label: '审批中', tagType: 'warning' },
    { value: 2, label: '已生效', tagType: 'success' },
    { value: 3, label: '已废止', tagType: 'info' },
    { value: 4, label: '已过期', tagType: 'danger' }
  ],
  icp_risk_level: [
    { value: 1, label: '高', tagType: 'danger' },
    { value: 2, label: '中', tagType: 'warning' },
    { value: 3, label: '低', tagType: 'success' }
  ],
  icp_control_type: [
    { value: 1, label: '预防', tagType: 'primary' },
    { value: 2, label: '发现', tagType: 'warning' },
    { value: 3, label: '纠正', tagType: 'success' }
  ],
  icp_matrix_coverage: [
    { value: 1, label: '全覆盖', tagType: 'success' },
    { value: 2, label: '部分覆盖', tagType: 'warning' },
    { value: 3, label: '未覆盖', tagType: 'danger' }
  ],
  icp_eval_status: [
    { value: 0, label: '草稿', tagType: 'info' },
    { value: 1, label: '审批中', tagType: 'warning' },
    { value: 2, label: '进行中', tagType: 'primary' },
    { value: 3, label: '已完成', tagType: 'success' },
    { value: 4, label: '已关闭', tagType: 'info' }
  ],
  icp_eval_type: [
    { value: 1, label: '年度', tagType: 'primary' },
    { value: 2, label: '专项', tagType: 'warning' }
  ],
  icp_eval_task_status: [
    { value: 0, label: '待评价', tagType: 'info' },
    { value: 1, label: '进行中', tagType: 'primary' },
    { value: 2, label: '已完成', tagType: 'success' },
    { value: 3, label: '已逾期', tagType: 'danger' }
  ],
  icp_eval_conclusion: [
    { value: 1, label: '有效', tagType: 'success' },
    { value: 2, label: '部分有效', tagType: 'warning' },
    { value: 3, label: '无效', tagType: 'danger' }
  ],
  icp_workpaper_result: [
    { value: 1, label: '符合', tagType: 'success' },
    { value: 2, label: '部分符合', tagType: 'warning' },
    { value: 3, label: '不符合', tagType: 'danger' }
  ],
  icp_defect_severity: [
    { value: 1, label: '重大', tagType: 'danger' },
    { value: 2, label: '一般', tagType: 'warning' },
    { value: 3, label: '轻微', tagType: 'info' }
  ],
  icp_defect_source: [
    { value: 1, label: '评价', tagType: 'primary' },
    { value: 2, label: '审计', tagType: 'warning' },
    { value: 3, label: '自查', tagType: 'info' },
    { value: 4, label: '投诉', tagType: 'danger' },
    { value: 5, label: '举报', tagType: 'danger' }
  ],
  icp_defect_type: [
    { value: 1, label: '设计缺陷', tagType: 'warning' },
    { value: 2, label: '运行缺陷', tagType: 'danger' }
  ],
  icp_defect_status: [
    { value: 0, label: '已登记', tagType: 'info' },
    { value: 1, label: '整改中', tagType: 'primary' },
    { value: 2, label: '待复核', tagType: 'warning' },
    { value: 3, label: '已关闭', tagType: 'success' }
  ],
  icp_rectify_status: [
    { value: 0, label: '待整改', tagType: 'warning' },
    { value: 1, label: '待复核', tagType: 'primary' },
    { value: 2, label: '已通过', tagType: 'success' },
    { value: 3, label: '已驳回', tagType: 'danger' },
    { value: 4, label: '已关闭', tagType: 'info' }
  ]
}

const store = reactive<Record<string, DictOption[]>>({})
const requested = new Set<string>()
let loading: Promise<void> | null = null

export function dictOptions(type: string): DictOption[] {
  const remote = store[type]
  if (remote && remote.length) return remote
  return FALLBACK[type] || []
}

export function dictLabel(type: string, value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '-'
  const option = dictOptions(type).find((item) => String(item.value) === String(value))
  return option ? option.label : String(value)
}

export function dictTagType(type: string, value: number | string | null | undefined): DictTagType {
  const option = dictOptions(type).find((item) => String(item.value) === String(value))
  return option ? option.tagType : 'info'
}

/**
 * 加载全部内控字典（幂等；失败保留兜底，不阻塞页面）。
 * 字典接口受 system:dict:list 保护，非系统管理员角色（如内控管理员、审计员）无该权限，
 * 直接跳过远端加载，避免 403 触发全局无权限跳转；页面仍可使用 FALLBACK 标签与颜色。
 */
export function ensureIcpDicts(): Promise<void> {
  const userStore = useUserStore()
  if (!userStore.hasPermission('system:dict:list')) return Promise.resolve()
  if (loading) return loading
  loading = (async () => {
    await Promise.all(
      Object.keys(FALLBACK).map(async (type) => {
        if (requested.has(type)) return
        requested.add(type)
        try {
          const page = await dictApi.listItems(type)
          const list = Array.isArray(page) ? page : page.list
          if (!list || !list.length) return
          const fallbackOptions = FALLBACK[type] || []
          store[type] = list.map((item) => {
            const value = Number(item.value)
            const fallback = fallbackOptions.find((option) => String(option.value) === String(item.value))
            return {
              value,
              label: item.label,
              tagType: (item.tagType as DictTagType) || fallback?.tagType || 'info'
            }
          })
        } catch {
          // 字典缺失时使用前端兜底
        }
      })
    )
  })()
  return loading
}
