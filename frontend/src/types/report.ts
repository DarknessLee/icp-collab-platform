/** M2e 报表与看板类型（按 docs/03 §7.1 冻结口径） */

export interface DashboardPolicies {
  total: number
  effective: number
}

export interface DashboardRisks {
  total: number
  high: number
}

export interface DashboardCoverage {
  totalRisks: number
  coveredRisks: number
  coverageRate: number
}

export interface DashboardDefects {
  total: number
  unclosed: number
}

export interface DashboardRectify {
  total: number
  approved: number
  rate: number
}

export interface DashboardWorkflow {
  todo: number
  overdue: number
}

export interface DashboardTasks {
  overdue: number
}

export interface DashboardMessages {
  unread: number
}

export interface DashboardSummary {
  policies: DashboardPolicies
  risks: DashboardRisks
  coverage: DashboardCoverage
  defects: DashboardDefects
  rectify: DashboardRectify
  workflow: DashboardWorkflow
  tasks: DashboardTasks
  messages: DashboardMessages
}

export interface DashboardTrend {
  labels: string[]
  newPolicies: number[]
  newDefects: number[]
  rectifyClosed: number[]
  avgProcessHours: number[]
}

export interface CoverageDeptRow {
  deptId?: number | string
  deptName: string
  total: number
  covered: number
  rate: number
}

export interface CoverageCategoryRow {
  categoryId?: number | string
  categoryName: string
  total: number
  covered: number
  rate: number
}

export interface CoverageReport {
  totalRisks: number
  coveredRisks: number
  coverageRate: number
  byDept: CoverageDeptRow[]
  byCategory: CoverageCategoryRow[]
}

export type RiskDim = 'level' | 'category' | 'dept'

export interface NamedValue {
  name: string
  value: number
}

export interface RiskDistribution {
  dim: RiskDim
  items: NamedValue[]
  total: number
}

export interface RectifyDeptRow {
  deptId?: number | string
  deptName: string
  total: number
  approved: number
  rate: number
}

export interface RectifyMonthRow {
  month: string
  total: number
  approved: number
  rate: number
}

export interface RectifyRateReport {
  total: number
  approved: number
  rate: number
  byDept: RectifyDeptRow[]
  byMonth: RectifyMonthRow[]
}

export interface ProcessDurationItem {
  processKey: string
  processName: string
  total: number
  avgHours: number
  p95Hours: number
}

export interface ProcessMonthRow {
  month: string
  avgHours: number
  count: number
}

export interface ProcessDurationReport {
  items: ProcessDurationItem[]
  byMonth: ProcessMonthRow[]
}

export interface DefectStatItem {
  severity: number | string
  status: number | string
  count: number
}

export interface DefectStatReport {
  items: DefectStatItem[]
  total: number
}

export type AlertType = 'task_overdue' | 'policy_expiring' | 'defect_high_risk' | 'process_overdue'

export interface AlertRow {
  id: number | string
  type: AlertType
  level: number | string
  title: string
  bizType?: string
  bizId?: number | string
  dueTime?: string
  daysLeft?: number
  overdueDays?: number
}

export interface AlertListResult {
  list: AlertRow[]
  total: number
  pageNum: number
  pageSize: number
}

export interface AlertCount {
  taskOverdue: number
  policyExpiring: number
  defectHighRisk: number
  processOverdue: number
  total: number
}

export interface TaskStatItem {
  status: number | string
  count: number
}

export interface TaskStatReport {
  items: TaskStatItem[]
  total: number
  overdue?: number
}
