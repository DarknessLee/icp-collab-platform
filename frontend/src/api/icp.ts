import { http } from './request'
import { toPage } from './system'
import type { PageResult } from '@/types/system'
import type {
  ControlPayload,  ControlQuery,
  ControlRow,
  DefectPayload,
  DefectQuery,
  DefectRow,
  EvaluationPayload,
  EvaluationQuery,
  EvaluationRow,
  EvaluationSubmitPayload,
  EvaluationTaskRow,
  EvaluationQuery as _EvaluationQuery,
  IcpCategory,
  MatrixCoverage,
  MatrixPayload,
  MatrixQuery,
  MatrixRow,
  PolicyCategoryPayload,
  PolicyPayload,
  PolicyQuery,
  PolicyRow,
  RectifyCreatePayload,
  RectifyPayload,
  RectifyQuery,
  RectifyRejectPayload,
  RectifyRow,
  RectifySubmitPayload,
  RiskPayload,
  RiskQuery,
  RiskRow,
  WorkpaperRow
} from '@/types/icp'

/** 制度/评价提交审批后可能返回流程实例，前端据此提示「可在流程中心跟踪」，不再二次触发发布 */
export interface WorkflowStartResult {
  processInstanceId?: string
  pendingWorkflow?: boolean
}

/**
 * M2b 内控域接口（docs/03 §4 + docs/04 §3）。
 * 后端未就绪时页面按契约开发；字段以后端实体 camelCase 为准，联调期若有不一致在本层适配。
 */

type PageParams = Record<string, unknown>

function page<T>(url: string, params?: PageParams): Promise<PageResult<T>> {
  return http<unknown>({ url, method: 'get', params }).then((data) => toPage<T>(data))
}

/** 分类接口兼容嵌套树与扁平列表两种返回 */
function toTree<T extends { id: number; parentId?: number; children?: T[] }>(data: unknown): T[] {
  const list = Array.isArray(data) ? (data as T[]) : []
  if (list.some((item) => Array.isArray(item.children) && item.children.length > 0)) return list
  const nodes = list.map((item) => ({ ...item, children: [] as T[] }))
  const map = new Map<number, T>()
  nodes.forEach((node) => map.set(node.id, node))
  const roots: T[] = []
  nodes.forEach((node) => {
    const parentId = Number(node.parentId ?? 0)
    if (parentId && map.has(parentId)) {
      const parent = map.get(parentId) as T
      parent.children = parent.children || []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

/* ------------------------------- 制度 ------------------------------- */
export const policyApi = {
  categoryTree: () =>
    http<unknown>({ url: '/policy-categories/tree', method: 'get' }).then((data) => toTree<IcpCategory>(data)),
  createCategory: (data: PolicyCategoryPayload) => http<void>({ url: '/policy-categories', method: 'post', data }),
  updateCategory: (id: number, data: PolicyCategoryPayload) =>
    http<void>({ url: `/policy-categories/${id}`, method: 'put', data }),
  removeCategory: (id: number) => http<void>({ url: `/policy-categories/${id}`, method: 'delete' }),

  list: (params: PolicyQuery & PageParams) => page<PolicyRow>('/policies', params),
  detail: (id: number) => http<PolicyRow>({ url: `/policies/${id}`, method: 'get' }),
  create: (data: PolicyPayload) => http<PolicyRow>({ url: '/policies', method: 'post', data }),
  update: (id: number, data: PolicyPayload) => http<PolicyRow>({ url: `/policies/${id}`, method: 'put', data }),
  remove: (id: number) => http<void>({ url: `/policies/${id}`, method: 'delete' }),
  submit: (id: number) => http<WorkflowStartResult>({ url: `/policies/${id}/submit`, method: 'post' }),
  publish: (id: number) => http<void>({ url: `/policies/${id}/publish`, method: 'post' }),
  abolish: (id: number) => http<void>({ url: `/policies/${id}/abolish`, method: 'post' })
}

/* ------------------------------- 风险 ------------------------------- */
export const riskApi = {
  categoryTree: () =>
    http<unknown>({ url: '/risk-categories/tree', method: 'get' }).then((data) => toTree<IcpCategory>(data)),
  createCategory: (data: PolicyCategoryPayload) => http<void>({ url: '/risk-categories', method: 'post', data }),
  updateCategory: (id: number, data: PolicyCategoryPayload) =>
    http<void>({ url: `/risk-categories/${id}`, method: 'put', data }),
  removeCategory: (id: number) => http<void>({ url: `/risk-categories/${id}`, method: 'delete' }),

  list: (params: RiskQuery & PageParams) => page<RiskRow>('/risks', params),
  detail: (id: number) => http<RiskRow>({ url: `/risks/${id}`, method: 'get' }),
  create: (data: RiskPayload) => http<RiskRow>({ url: '/risks', method: 'post', data }),
  update: (id: number, data: RiskPayload) => http<RiskRow>({ url: `/risks/${id}`, method: 'put', data }),
  remove: (id: number) => http<void>({ url: `/risks/${id}`, method: 'delete' }),
  updateStatus: (id: number, status: number) =>
    http<void>({ url: `/risks/${id}/status`, method: 'put', data: { status } })
}

/* ------------------------------ 控制点 ------------------------------ */
export const controlApi = {
  list: (params: ControlQuery & PageParams) => page<ControlRow>('/controls', params),
  detail: (id: number) => http<ControlRow>({ url: `/controls/${id}`, method: 'get' }),
  create: (data: ControlPayload) => http<ControlRow>({ url: '/controls', method: 'post', data }),
  update: (id: number, data: ControlPayload) => http<ControlRow>({ url: `/controls/${id}`, method: 'put', data }),
  remove: (id: number) => http<void>({ url: `/controls/${id}`, method: 'delete' })
}

/* ------------------------------ 控制矩阵 ---------------------------- */
/** 后端 /matrices/coverage 返回 {totalRisks,coveredRisks,uncoveredRisks,coverageRate,items}，此处归一化为页面模型 */
function mapCoverage(data: unknown): MatrixCoverage {
  const raw = (data || {}) as Record<string, unknown>
  const total = Number(raw.totalRisks ?? raw.total ?? 0)
  const covered = Number(raw.coveredRisks ?? raw.covered ?? 0)
  const uncovered = Number(raw.uncoveredRisks ?? raw.uncovered ?? Math.max(0, total - covered))
  const rate = Number(raw.coverageRate ?? raw.rate ?? (total ? (covered / total) * 100 : 0))
  return {
    total,
    covered,
    uncovered,
    rate,
    byLevel: [
      { level: 1, label: '已覆盖', count: covered },
      { level: 3, label: '未覆盖', count: uncovered }
    ]
  }
}

export const matrixApi = {
  list: (params: MatrixQuery & PageParams) => page<MatrixRow>('/matrices', params),
  detail: (id: number) => http<MatrixRow>({ url: `/matrices/${id}`, method: 'get' }),
  create: (data: MatrixPayload) => http<MatrixRow>({ url: '/matrices', method: 'post', data }),
  update: (id: number, data: MatrixPayload) => http<MatrixRow>({ url: `/matrices/${id}`, method: 'put', data }),
  remove: (id: number) => http<void>({ url: `/matrices/${id}`, method: 'delete' }),
  coverage: () => http<unknown>({ url: '/matrices/coverage', method: 'get' }).then((data) => mapCoverage(data))
}

/* ------------------------------ 评价计划 ---------------------------- */
export const evaluationApi = {
  list: (params: EvaluationQuery & PageParams) => page<EvaluationRow>('/evaluations', params),
  detail: (id: number) => http<EvaluationRow>({ url: `/evaluations/${id}`, method: 'get' }),
  create: (data: EvaluationPayload) => http<EvaluationRow>({ url: '/evaluations', method: 'post', data }),
  update: (id: number, data: EvaluationPayload) =>
    http<EvaluationRow>({ url: `/evaluations/${id}`, method: 'put', data }),
  remove: (id: number) => http<void>({ url: `/evaluations/${id}`, method: 'delete' }),
  submit: (id: number) => http<WorkflowStartResult>({ url: `/evaluations/${id}/submit`, method: 'post' }),
  start: (id: number) => http<void>({ url: `/evaluations/${id}/start`, method: 'post' }),
  finish: (id: number) => http<void>({ url: `/evaluations/${id}/finish`, method: 'post' }),
  close: (id: number) => http<void>({ url: `/evaluations/${id}/close`, method: 'post' })
}

export const evaluationTaskApi = {
  list: (params: { planId?: number; evaluatorId?: number; status?: number } & PageParams) =>
    page<EvaluationTaskRow>('/evaluation-tasks', params),
  detail: (id: number) => http<EvaluationTaskRow>({ url: `/evaluation-tasks/${id}`, method: 'get' }),
  update: (id: number, data: Partial<EvaluationTaskRow>) =>
    http<void>({ url: `/evaluation-tasks/${id}`, method: 'put', data }),
  start: (id: number) => http<void>({ url: `/evaluation-tasks/${id}/start`, method: 'post' }),
  submit: (id: number, data: EvaluationSubmitPayload) =>
    http<void>({ url: `/evaluation-tasks/${id}/submit`, method: 'post', data }),
  workpapers: (id: number) =>
    http<WorkpaperRow[] | { list: WorkpaperRow[] }>({ url: `/evaluation-tasks/${id}/workpapers`, method: 'get' }),
  addWorkpaper: (id: number, data: WorkpaperRow) =>
    http<void>({ url: `/evaluation-tasks/${id}/workpapers`, method: 'post', data })
}

/* ------------------------------- 缺陷 ------------------------------- */
export const defectApi = {
  list: (params: DefectQuery & PageParams) => page<DefectRow>('/defects', params),
  detail: (id: number) => http<DefectRow>({ url: `/defects/${id}`, method: 'get' }),
  create: (data: DefectPayload) => http<DefectRow>({ url: '/defects', method: 'post', data }),
  update: (id: number, data: DefectPayload) => http<DefectRow>({ url: `/defects/${id}`, method: 'put', data }),
  rectify: (id: number, data: RectifyCreatePayload) =>
    http<void>({ url: `/defects/${id}/rectify`, method: 'post', data }),
  close: (id: number) => http<void>({ url: `/defects/${id}/close`, method: 'post' })
}

/* ------------------------------- 整改 ------------------------------- */
export const rectifyApi = {
  list: (params: RectifyQuery & PageParams) => page<RectifyRow>('/rectifies', params),
  detail: (id: number) => http<RectifyRow>({ url: `/rectifies/${id}`, method: 'get' }),
  update: (id: number, data: RectifyPayload) => http<RectifyRow>({ url: `/rectifies/${id}`, method: 'put', data }),
  submit: (id: number, data: RectifySubmitPayload) =>
    // 后端 RectifySubmit.evidence 为 String（逗号分隔的附件 id），前端视图层仍用 number[]
    http<void>({
      url: `/rectifies/${id}/submit`,
      method: 'post',
      data: { ...data, evidence: (data.evidence || []).join(',') }
    }),
  approve: (id: number) => http<void>({ url: `/rectifies/${id}/approve`, method: 'post' }),
  reject: (id: number, data: RectifyRejectPayload) =>
    http<void>({ url: `/rectifies/${id}/reject`, method: 'post', data }),
  close: (id: number) => http<void>({ url: `/rectifies/${id}/close`, method: 'post' })
}

export type { PageResult }
