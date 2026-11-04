/** M2e 报表与看板 API：统一走 http<T>，返回前做防御性归一化（空值不产生 NaN） */
import { http } from '@/api/request'
import type {
  AlertCount,
  AlertListResult,
  AlertRow,
  AlertType,
  CoverageCategoryRow,
  CoverageDeptRow,
  CoverageReport,
  DashboardSummary,
  DashboardTrend,
  DefectStatItem,
  DefectStatReport,
  NamedValue,
  ProcessDurationItem,
  ProcessDurationReport,
  ProcessMonthRow,
  RectifyDeptRow,
  RectifyMonthRow,
  RectifyRateReport,
  RiskDim,
  RiskDistribution,
  TaskStatItem,
  TaskStatReport
} from '@/types/report'

function raw(data: unknown): Record<string, unknown> {
  return data && typeof data === 'object' ? (data as Record<string, unknown>) : {}
}

function num(value: unknown, fallback = 0): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function str(value: unknown, fallback = ''): string {
  return value === null || value === undefined ? fallback : String(value)
}

function arr<T = unknown>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : []
}

function rate(value: unknown): number {
  const n = num(value)
  if (n > 1 && n <= 100) return n / 100
  return Math.min(1, Math.max(0, n))
}

function numList(value: unknown): number[] {
  return arr<unknown>(value).map((item) => num(item))
}

function mapCoverage(data: unknown): CoverageReport {
  const o = raw(data)
  return {
    totalRisks: num(o.totalRisks),
    coveredRisks: num(o.coveredRisks),
    coverageRate: rate(o.coverageRate),
    byDept: arr<Record<string, unknown>>(o.byDept).map<CoverageDeptRow>((item) => ({
      deptId: item.deptId as number | string | undefined,
      deptName: str(item.deptName, '未分配部门'),
      total: num(item.total),
      covered: num(item.covered),
      rate: rate(item.rate)
    })),
    byCategory: arr<Record<string, unknown>>(o.byCategory).map<CoverageCategoryRow>((item) => ({
      categoryId: item.categoryId as number | string | undefined,
      categoryName: str(item.categoryName, '未分类'),
      total: num(item.total),
      covered: num(item.covered),
      rate: rate(item.rate)
    }))
  }
}

function mapRisk(data: unknown, dim: RiskDim): RiskDistribution {
  const o = raw(data)
  return {
    dim: (str(o.dim, dim) as RiskDim) || dim,
    total: num(o.total),
    items: arr<Record<string, unknown>>(o.items).map<NamedValue>((item) => ({
      name: str(item.name, '未知'),
      value: num(item.value)
    }))
  }
}

function mapRectify(data: unknown): RectifyRateReport {
  const o = raw(data)
  return {
    total: num(o.total),
    approved: num(o.approved),
    rate: rate(o.rate),
    byDept: arr<Record<string, unknown>>(o.byDept).map<RectifyDeptRow>((item) => ({
      deptId: item.deptId as number | string | undefined,
      deptName: str(item.deptName, '未分配部门'),
      total: num(item.total),
      approved: num(item.approved),
      rate: rate(item.rate)
    })),
    byMonth: arr<Record<string, unknown>>(o.byMonth).map<RectifyMonthRow>((item) => ({
      month: str(item.month),
      total: num(item.total),
      approved: num(item.approved),
      rate: rate(item.rate)
    }))
  }
}

function mapProcess(data: unknown): ProcessDurationReport {
  const o = raw(data)
  return {
    items: arr<Record<string, unknown>>(o.items).map<ProcessDurationItem>((item) => ({
      processKey: str(item.processKey),
      processName: str(item.processName, str(item.processKey, '未命名流程')),
      total: num(item.total),
      avgHours: num(item.avgHours),
      p95Hours: num(item.p95Hours)
    })),
    byMonth: arr<Record<string, unknown>>(o.byMonth).map<ProcessMonthRow>((item) => ({
      month: str(item.month),
      avgHours: num(item.avgHours),
      count: num(item.count)
    }))
  }
}

function mapDefects(data: unknown): DefectStatReport {
  const o = raw(data)
  const items = arr<Record<string, unknown>>(o.items).map<DefectStatItem>((item) => ({
    severity: (item.severity ?? 0) as number | string,
    status: (item.status ?? 0) as number | string,
    count: num(item.count)
  }))
  return {
    items,
    total: num(o.total, items.reduce((sum, item) => sum + item.count, 0))
  }
}

function mapAlert(data: unknown): AlertRow {
  const o = raw(data)
  return {
    id: (o.id ?? '') as number | string,
    type: (str(o.type) as AlertType) || 'task_overdue',
    level: (o.level ?? 0) as number | string,
    title: str(o.title, '未命名预警'),
    bizType: str(o.bizType) || undefined,
    bizId: (o.bizId ?? undefined) as number | string | undefined,
    dueTime: str(o.dueTime) || undefined,
    daysLeft: o.daysLeft === undefined || o.daysLeft === null ? undefined : num(o.daysLeft),
    overdueDays:
      o.overdueDays === undefined || o.overdueDays === null ? undefined : num(o.overdueDays)
  }
}

function mapAlerts(data: unknown, params: Record<string, unknown>): AlertListResult {
  const o = raw(data)
  const list = arr<Record<string, unknown>>(o.list ?? o.items ?? data).map(mapAlert)
  return {
    list,
    total: num(o.total, list.length),
    pageNum: num(o.pageNum, num(params.pageNum, 1)),
    pageSize: num(o.pageSize, num(params.pageSize, 10))
  }
}

function mapSummary(data: unknown): DashboardSummary {
  const o = raw(data)
  const policies = raw(o.policies)
  const risks = raw(o.risks)
  const coverage = raw(o.coverage)
  const defects = raw(o.defects)
  const rectify = raw(o.rectify)
  const workflow = raw(o.workflow)
  const tasks = raw(o.tasks)
  const messages = raw(o.messages)
  return {
    policies: { total: num(policies.total), effective: num(policies.effective) },
    risks: { total: num(risks.total), high: num(risks.high) },
    coverage: {
      totalRisks: num(coverage.totalRisks),
      coveredRisks: num(coverage.coveredRisks),
      coverageRate: rate(coverage.coverageRate)
    },
    defects: { total: num(defects.total), unclosed: num(defects.unclosed) },
    rectify: { total: num(rectify.total), approved: num(rectify.approved), rate: rate(rectify.rate) },
    workflow: { todo: num(workflow.todo), overdue: num(workflow.overdue) },
    tasks: { overdue: num(tasks.overdue) },
    messages: { unread: num(messages.unread) }
  }
}

function mapTrend(data: unknown): DashboardTrend {
  const o = raw(data)
  return {
    labels: arr<unknown>(o.labels).map((item) => str(item)),
    newPolicies: numList(o.newPolicies),
    newDefects: numList(o.newDefects),
    rectifyClosed: numList(o.rectifyClosed),
    avgProcessHours: numList(o.avgProcessHours)
  }
}

function mapAlertCount(data: unknown): AlertCount {
  const o = raw(data)
  return {
    taskOverdue: num(o.taskOverdue),
    policyExpiring: num(o.policyExpiring),
    defectHighRisk: num(o.defectHighRisk),
    processOverdue: num(o.processOverdue),
    total: num(o.total, num(o.taskOverdue) + num(o.policyExpiring) + num(o.defectHighRisk) + num(o.processOverdue))
  }
}

function mapTasks(data: unknown): TaskStatReport {
  const o = raw(data)
  const items = arr<Record<string, unknown>>(o.items).map<TaskStatItem>((item) => ({
    status: (item.status ?? 0) as number | string,
    count: num(item.count)
  }))
  return {
    items,
    total: num(o.total, items.reduce((sum, item) => sum + item.count, 0)),
    overdue: o.overdue === undefined || o.overdue === null ? undefined : num(o.overdue)
  }
}

export const dashboardApi = {
  summary: () =>
    http<unknown>({ url: '/dashboard/summary', method: 'get' }).then(mapSummary),
  trend: (months = 6) =>
    http<unknown>({ url: '/dashboard/trend', method: 'get', params: { months } }).then(mapTrend)
}

export const reportApi = {
  coverage: (params: { deptId?: number | string; year?: number | string } = {}) =>
    http<unknown>({ url: '/reports/icp-coverage', method: 'get', params }).then(mapCoverage),
  riskDistribution: (dim: RiskDim) =>
    http<unknown>({ url: '/reports/risk-distribution', method: 'get', params: { dim } }).then((data) =>
      mapRisk(data, dim)
    ),
  rectifyRate: (params: { year?: number | string; deptId?: number | string } = {}) =>
    http<unknown>({ url: '/reports/rectify-rate', method: 'get', params }).then(mapRectify),
  processDuration: (params: { processKey?: string; months?: number } = {}) =>
    http<unknown>({ url: '/reports/process-duration', method: 'get', params }).then(mapProcess),
  defects: (params: { severity?: number | string; status?: number | string } = {}) =>
    http<unknown>({ url: '/reports/defects', method: 'get', params }).then(mapDefects),
  tasks: () => http<unknown>({ url: '/reports/tasks', method: 'get' }).then(mapTasks)
}

export const alertApi = {
  list: (params: { type?: AlertType | ''; pageNum?: number; pageSize?: number } = {}) =>
    http<unknown>({ url: '/alerts', method: 'get', params }).then((data) => mapAlerts(data, params)),
  count: () => http<unknown>({ url: '/alerts/count', method: 'get' }).then(mapAlertCount)
}

export function alertListOf(data: unknown): AlertRow[] {
  return arr<Record<string, unknown>>(raw(data).list).map(mapAlert)
}
