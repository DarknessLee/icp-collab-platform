import type { PageResult } from '@/types/system'

export type { PageResult }

/** 通用内控域字段（后端 icp_* 实体字段，统一在此声明） */
export interface IcpCategory {
  id: number
  parentId?: number
  categoryName: string
  orderNum?: number
  status?: number
  children?: IcpCategory[]
}

export interface PolicyCategoryPayload {
  id?: number
  parentId?: number
  categoryName: string
  orderNum?: number
  status?: number
}

export interface PolicyRow {
  id: number
  policyCode: string
  policyName: string
  categoryId?: number
  categoryName?: string
  version?: string
  effectiveDate?: string
  expireDate?: string
  status: number
  content?: string
  ownerDeptId?: number
  ownerDeptName?: string
  ownerUserId?: number
  keywords?: string
  publishTime?: string
  remark?: string
  createBy?: number
  createByName?: string
  createTime?: string
  updateTime?: string
  fileIds?: number[]
}

export type PolicyPayload = Omit<
  PolicyRow,
  'id' | 'policyCode' | 'status' | 'publishTime' | 'createTime' | 'updateTime'
>

export interface PolicyQuery {
  categoryId?: number
  status?: number
  keyword?: string
  effectiveFrom?: string
  effectiveTo?: string
}

export interface RiskRow {
  id: number
  riskCode: string
  riskName: string
  categoryId?: number
  categoryName?: string
  riskLevel?: number
  probability?: number
  impact?: number
  riskScore?: number
  ownerDeptId?: number
  ownerDeptName?: string
  ownerUserId?: number
  status?: number
  description?: string
  remark?: string
  createTime?: string
}

export type RiskPayload = Omit<RiskRow, 'id' | 'riskCode' | 'riskLevel' | 'riskScore' | 'createTime'>

export interface RiskQuery {
  categoryId?: number
  riskLevel?: number
  status?: number
  keyword?: string
}

export interface ControlRow {
  id: number
  controlCode: string
  controlName: string
  controlType?: number
  controlDesc?: string
  freq?: string
  ownerDeptId?: number
  ownerDeptName?: string
  ownerUserId?: number
  status?: number
  remark?: string
  createTime?: string
}

export type ControlPayload = Omit<ControlRow, 'id' | 'controlCode' | 'createTime'>

export interface ControlQuery {
  keyword?: string
  controlType?: number
  status?: number
}

export interface MatrixRow {
  id: number
  matrixCode: string
  riskId: number
  riskName?: string
  controlId: number
  controlName?: string
  policyId?: number
  policyName?: string
  coverage?: number
  evidenceDesc?: string
  status?: number
  ownerDeptId?: number
  ownerDeptName?: string
  remark?: string
  createTime?: string
}

export type MatrixPayload = Omit<MatrixRow, 'id' | 'matrixCode' | 'riskName' | 'controlName' | 'policyName' | 'ownerDeptName' | 'createTime'>

export interface MatrixQuery {
  keyword?: string
  coverage?: number
  riskId?: number
}

export interface MatrixCoverage {
  total?: number
  covered?: number
  uncovered?: number
  rate?: number
  byLevel?: Array<{ level: number | string; label?: string; count: number }>
}

export interface EvaluationRow {
  id: number
  planCode: string
  planName: string
  planYear?: number
  planType?: number
  startDate?: string
  endDate?: string
  deptScope?: string
  status: number
  ownerDeptId?: number
  ownerDeptName?: string
  description?: string
  remark?: string
  createTime?: string
}

export type EvaluationPayload = Omit<EvaluationRow, 'id' | 'planCode' | 'status' | 'createTime' | 'ownerDeptName'>

export interface EvaluationQuery {
  keyword?: string
  status?: number
  planYear?: number
}

export interface EvaluationTaskRow {
  id: number
  taskCode: string
  planId: number
  taskName: string
  riskId?: number
  controlId?: number
  evaluateDeptId?: number
  evaluateDeptName?: string
  evaluatorId?: number
  evaluatorName?: string
  startDate?: string
  dueDate?: string
  status: number
  score?: number | null
  conclusion?: number | null
  remark?: string
}

export interface WorkpaperRow {
  id?: number
  taskId?: number
  sampleDesc?: string
  testDesc?: string
  testResult?: number
  evidence?: string
  conclusion?: string
  workUserId?: number
  workUserName?: string
  createTime?: string
}

export interface EvaluationSubmitPayload {
  score: number
  conclusion: number
  workpaper: {
    sampleDesc?: string
    testDesc?: string
    testResult?: number
    evidence?: number[]
    conclusion?: string
  }
}

export interface DefectRow {
  id: number
  defectCode: string
  defectName: string
  sourceType?: number
  sourceId?: number
  severity?: number
  defectType?: number
  deptId?: number
  deptName?: string
  findDate?: string
  discoverUserId?: number
  discoverUserName?: string
  description?: string
  status: number
  ownerUserId?: number
  ownerUserName?: string
  closeTime?: string
  rectifyId?: number
  rectifyCode?: string
  remark?: string
  createTime?: string
}

export type DefectPayload = Omit<DefectRow, 'id' | 'defectCode' | 'status' | 'closeTime' | 'rectifyId' | 'rectifyCode' | 'createTime' | 'deptName' | 'discoverUserName' | 'ownerUserName'>

export interface DefectQuery {
  keyword?: string
  severity?: number
  deptId?: number
  status?: number
  sourceType?: number
}

export interface RectifyRow {
  id: number
  rectifyCode: string
  defectId: number
  defectCode?: string
  defectName?: string
  rectifyPlan?: string
  responsibleUserId?: number
  responsibleUserName?: string
  deptId?: number
  deptName?: string
  deadline?: string
  finishDate?: string
  evidence?: string
  status: number
  reviewUserId?: number
  reviewUserName?: string
  reviewOpinion?: string
  reviewTime?: string
  remark?: string
  createTime?: string
}

export type RectifyPayload = Pick<RectifyRow, 'rectifyPlan'> & {
  responsibleUserId?: number
  deadline?: string
  remark?: string
}

export interface RectifyQuery {
  keyword?: string
  status?: number
  deptId?: number
}

export interface RectifyCreatePayload {
  responsibleUserId?: number
  deadline?: string
  rectifyPlan: string
}

export interface RectifySubmitPayload {
  evidence: number[]
  remark?: string
  finishDate?: string
}

export interface RectifyRejectPayload {
  reviewOpinion: string
}
