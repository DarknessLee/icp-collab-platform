/** M2c 流程中心类型（依据 docs/03 §5 / §5.1 M2c 实现口径、docs/01 U01–U12） */

export type WorkflowStatus = 'RUNNING' | 'COMPLETED' | 'REJECTED' | string

/** 流程定义（GET /workflow/processes） */
export interface ProcessDef {
  processKey: string
  name: string
  version: number | string
  status?: number | string
  description?: string
  category?: string
  deployedTime?: string
}

/** 待办/已办/我发起行；后端字段可能存在 processKey/key、processName 等别名，api 层归一化 */
export interface WorkflowTaskRow {
  taskId: string
  processInstanceId?: string
  processKey: string
  processName?: string
  title: string
  businessNo?: string
  businessId?: string | number
  nodeName?: string
  initiatorName?: string
  createTime?: string
  dueTime?: string
  overdue?: boolean
  status?: WorkflowStatus
  /** 已办：处理结果与时间 */
  action?: string
  comment?: string
  handleTime?: string
  endTime?: string
}

/** 我发起行 */
export interface WorkflowInstanceRow {
  processInstanceId: string
  processKey: string
  processName?: string
  title: string
  businessNo?: string
  status?: WorkflowStatus
  statusText?: string
  nodeName?: string
  initiatorName?: string
  startTime?: string
  createTime?: string
  endTime?: string
  duration?: string
  overdue?: boolean
  businessId?: string | number
}

/** 表单字段（wf_form 渲染只读表单） */
export interface WfFormField {
  key: string
  label: string
  type: string
  value?: unknown
  options?: { label: string; value: string | number }[]
  required?: boolean
  placeholder?: string
}

export interface WfHistoryItem {
  activityId?: string
  nodeName: string
  assignee?: string
  assigneeName?: string
  action: string
  comment?: string
  startTime?: string
  endTime?: string
  attachmentIds?: (string | number)[]
}

export interface WfDiagramHighlight {
  activityId?: string
  finishedActivityIds: string[]
}

/** 任务详情（GET /workflow/tasks/{taskId}） */
export interface WorkflowTaskDetail {
  taskId: string
  taskName?: string
  nodeName?: string
  processInstanceId: string
  processKey: string
  processName?: string
  title: string
  businessNo?: string
  initiatorName?: string
  createTime?: string
  formSchema: WfFormField[]
  formData: Record<string, unknown>
  businessData?: Record<string, unknown>
  actions?: string[]
}

/** 实例详情（GET /workflow/instances/{id}） */
export interface WorkflowInstanceDetail {
  processInstanceId: string
  processKey: string
  processName?: string
  title: string
  businessNo?: string
  status?: WorkflowStatus
  statusText?: string
  nodeName?: string
  initiatorName?: string
  startTime?: string
  endTime?: string
  overdue?: boolean
  formSchema: WfFormField[]
  formData: Record<string, unknown>
  businessData?: Record<string, unknown>
}

/** 监控行（GET /workflow/monitor） */
export interface WorkflowMonitorRow {
  processInstanceId: string
  processKey: string
  processName?: string
  title: string
  businessNo?: string
  initiatorName?: string
  nodeName?: string
  startTime?: string
  duration?: string
  status?: WorkflowStatus
  statusText?: string
  overdue?: boolean
}

export interface WorkflowMonitorStats {
  running: number
  completed: number
  rejected: number
  overdue: number
}

export interface WorkflowMonitorResult {
  list: WorkflowMonitorRow[]
  total: number
  stats: WorkflowMonitorStats
}

export interface CreateInstancePayload {
  processKey: string
  businessId?: string | number
  businessType?: string
  formData: Record<string, unknown>
}

export interface CreateInstanceResult {
  processInstanceId?: string
  taskId?: string
  businessNo?: string
  pendingWorkflow?: boolean
}

export interface ApprovePayload {
  comment?: string
  formData?: Record<string, unknown>
  attachmentIds?: (string | number)[]
  ccUserIds?: (string | number)[]
}

export interface RejectPayload {
  comment: string
  ccUserIds?: (string | number)[]
}

export interface UrgePayload {
  content: string
}

/** 采购申请业务单（wf_purchase；状态 0 草稿 / 1 审批中 / 2 已通过 / 3 已驳回 / 4 已完成） */
export interface PurchaseRow {
  id: string | number
  applyNo?: string
  title: string
  deptId?: string | number
  deptName?: string
  applicantId?: string | number
  applicantName?: string
  amount?: number
  budgetAmount?: number
  supplier?: string
  purchaseType?: number | string
  purpose?: string
  applyDate?: string
  status?: number | string
  remark?: string
  processInstanceId?: string
}

export interface PurchasePayload {
  title: string
  deptId?: string | number
  amount: number
  budgetAmount?: number
  supplier?: string
  purchaseType?: number
  purpose?: string
  applyDate?: string
  /** 创建后立即提交审批 */
  submit?: boolean
}

/** 用印申请（wf_seal；状态同采购） */
export interface SealRow {
  id: string | number
  applyNo?: string
  sealType?: number | string
  reason?: string
  useDate?: string
  fileId?: string | number
  applicantId?: string | number
  applicantName?: string
  deptId?: string | number
  deptName?: string
  status?: number | string
  remark?: string
  processInstanceId?: string
}

export interface SealPayload {
  sealType: number
  reason: string
  useDate?: string
  fileId?: string | number
  deptId?: string | number
  submit?: boolean
}

/** 文档借阅申请（coll_doc_borrow；状态 0 申请中 / 1 已通过 / 2 已驳回 / 3 已归还） */
export interface BorrowRow {
  id: string | number
  docId?: string | number
  borrowerId?: string | number
  borrowerName?: string
  reason?: string
  startDate?: string
  endDate?: string
  status?: number | string
  remark?: string
  processInstanceId?: string
}

export interface BorrowPayload {
  docId: string | number
  reason: string
  startDate?: string
  endDate?: string
}
