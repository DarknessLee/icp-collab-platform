/**
 * M2c 流程中心 API（docs/03 §5 / §5.1）
 *
 * 后端字段可能存在别名（taskId/taskName、processKey/key、nodeName/name 等），
 * 统一在本层归一化为前端视图模型，页面只使用前端语义字段。
 * 接口未就绪时页面为加载失败/空态，不做假数据填充。
 */
import { http } from './request'
import { formatTime, toPage } from './system'
import type { PageQuery, PageResult } from '@/types/system'
import type {
  ApprovePayload,
  BorrowPayload,
  BorrowRow,
  CreateInstancePayload,
  CreateInstanceResult,
  ProcessDef,
  PurchasePayload,
  PurchaseRow,
  RejectPayload,
  SealPayload,
  SealRow,
  UrgePayload,
  WfDiagramHighlight,
  WfFormField,
  WfHistoryItem,
  WorkflowInstanceDetail,
  WorkflowInstanceRow,
  WorkflowMonitorResult,
  WorkflowMonitorRow,
  WorkflowTaskDetail,
  WorkflowTaskRow
} from '@/types/workflow'

type Raw = Record<string, unknown>

function str(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') return undefined
  return String(value)
}

function num(value: unknown): number | undefined {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

function bool(value: unknown): boolean | undefined {
  if (value === null || value === undefined) return undefined
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value === 1
  const s = String(value).toLowerCase()
  if (['true', '1', 'yes', 'y'].includes(s)) return true
  if (['false', '0', 'no', 'n'].includes(s)) return false
  return undefined
}

function raw(value: unknown): Raw {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value as Raw
  return {}
}

function parseMaybeJson(value: unknown): unknown {
  if (typeof value !== 'string') return value
  const trimmed = value.trim()
  if (!trimmed) return undefined
  if (!/^[[{]/.test(trimmed)) return value
  try {
    return JSON.parse(trimmed)
  } catch {
    return value
  }
}

function asList(data: unknown): Raw[] {
  const parsed = parseMaybeJson(data)
  if (Array.isArray(parsed)) return parsed.map((item) => raw(item))
  const obj = raw(parsed)
  const list = obj.list ?? obj.rows ?? obj.records ?? obj.items
  return Array.isArray(list) ? list.map((item) => raw(item)) : []
}

function pick(...values: unknown[]): unknown {
  for (const value of values) {
    if (value !== null && value !== undefined && value !== '') return value
  }
  return undefined
}

function idempotencyKey(): string {
  const c = globalThis.crypto as Crypto | undefined
  if (c && typeof c.randomUUID === 'function') return c.randomUUID()
  return `wf-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 统一状态字典（后端可能返回 RUNNING/COMPLETED/REJECTED 或数字） */
const STATUS_TEXT: Record<string, string> = {
  RUNNING: '运行中',
  COMPLETED: '已完成',
  REJECTED: '已驳回',
  CANCELED: '已取消',
  CANCELLED: '已取消',
  '0': '运行中',
  '1': '已完成',
  '2': '已驳回',
  '3': '已取消'
}

export function workflowStatusText(status: unknown): string {
  const key = str(status)?.toUpperCase()
  if (!key) return '—'
  return STATUS_TEXT[key] || key
}

export function workflowStatusType(status: unknown): 'primary' | 'success' | 'danger' | 'info' {
  const key = str(status)?.toUpperCase()
  if (key === 'COMPLETED' || key === '1') return 'success'
  if (key === 'REJECTED' || key === '2') return 'danger'
  if (key === 'CANCELED' || key === 'CANCELLED' || key === '3') return 'info'
  return 'primary'
}

function mapFieldOptions(value: unknown): { label: string; value: string | number }[] {
  const parsed = parseMaybeJson(value)
  if (Array.isArray(parsed)) {
    return parsed.map((item) => {
      if (typeof item === 'object' && item) {
        const o = item as Raw
        return {
          label: String(pick(o.label, o.name, o.dictLabel, o.text) ?? ''),
          value: (pick(o.value, o.dictValue, o.code, o.id) ?? '') as string | number
        }
      }
      return { label: String(item), value: String(item) }
    })
  }
  const obj = raw(parsed)
  return Object.entries(obj).map(([key, label]) => ({ label: String(label), value: key }))
}

function mapFormSchema(value: unknown): WfFormField[] {
  const parsed = parseMaybeJson(value)
  const source = Array.isArray(parsed)
    ? parsed
    : (raw(parsed).fields ?? raw(parsed).list ?? raw(parsed).items ?? raw(parsed).formSchema)
  if (!Array.isArray(source)) return []
  return source.map((item) => {
    const o = raw(item)
    const key = String(pick(o.key, o.fieldKey, o.name, o.prop, o.fieldName) ?? '')
    return {
      key,
      label: String(pick(o.label, o.title, o.fieldLabel, o.name) ?? key),
      type: String(pick(o.type, o.fieldType, o.component, o.inputType) ?? 'text'),
      value: pick(o.value, o.defaultValue),
      options: mapFieldOptions(pick(o.options, o.dictOptions, o.dictType)),
      required: bool(pick(o.required, o.requiredFlag, o.must)) ?? false,
      placeholder: str(o.placeholder)
    }
  })
}

function mapFormData(value: unknown): Record<string, unknown> {
  const parsed = parseMaybeJson(value)
  return raw(parsed)
}

function mapHistory(data: unknown): WfHistoryItem[] {
  return asList(data).map((o) => ({
    activityId: str(pick(o.activityId, o.actId, o.nodeId)),
    nodeName: String(pick(o.nodeName, o.activityName, o.taskName, o.name) ?? '—'),
    assignee: str(pick(o.assignee, o.assigneeId, o.userId)),
    assigneeName: str(pick(o.assigneeName, o.userName, o.handlerName)),
    action: String(pick(o.action, o.actionName, o.type, o.commentType) ?? '审批'),
    comment: str(pick(o.comment, o.message, o.opinion, o.fullMessage)),
    startTime: formatTime(pick(o.startTime, o.createTime)),
    endTime: formatTime(pick(o.endTime, o.completeTime)),
    attachmentIds: Array.isArray(o.attachmentIds) ? (o.attachmentIds as (string | number)[]) : undefined
  }))
}

function mapHighlight(data: unknown): WfDiagramHighlight {
  const o = raw(parseMaybeJson(data))
  const finished = pick(o.finishedActivityIds, o.finishedIds, o.highlightedActivityIds) as unknown
  return {
    activityId: str(pick(o.activityId, o.currentActivityId, o.currentNodeId)),
    finishedActivityIds: Array.isArray(finished) ? finished.map((item) => String(item)) : []
  }
}

function mapProcess(o: Raw): ProcessDef {
  return {
    processKey: String(pick(o.processKey, o.key, o.definitionKey, o.processDefKey) ?? ''),
    name: String(pick(o.name, o.processName, o.definitionName, o.title) ?? ''),
    version: (pick(o.version, o.versionTag, o.processVersion) as number | string) ?? '',
    status: (o.status as number | string) ?? undefined,
    description: str(pick(o.description, o.remark)),
    category: str(o.category),
    deployedTime: formatTime(pick(o.deployedTime, o.deployTime, o.createTime))
  }
}

function mapTaskRow(o: Raw): WorkflowTaskRow {
  const dueRaw = pick(o.dueTime, o.dueDate, o.deadline)
  return {
    taskId: String(pick(o.taskId, o.id, o.taskKey) ?? ''),
    processInstanceId: str(pick(o.processInstanceId, o.instanceId, o.procInstId)),
    processKey: String(pick(o.processKey, o.key, o.processDefinitionKey, o.processDefKey) ?? ''),
    processName: str(pick(o.processName, o.processDefName, o.category)),
    title: String(pick(o.title, o.processTitle, o.businessTitle, o.subject, o.name) ?? '未命名流程'),
    businessNo: str(pick(o.businessNo, o.bizNo, o.businessCode)),
    businessId: (pick(o.businessId, o.bizId) as string | number) ?? undefined,
    nodeName: str(pick(o.nodeName, o.taskName, o.name, o.activityName)),
    initiatorName: str(pick(o.initiatorName, o.startUserName, o.creatorName, o.applyUserName)),
    createTime: formatTime(pick(o.createTime, o.startTime)),
    dueTime: formatTime(dueRaw),
    overdue: bool(o.overdue),
    status: (o.status as string) ?? undefined,
    action: str(pick(o.action, o.result)),
    comment: str(pick(o.comment, o.opinion)),
    handleTime: formatTime(pick(o.handleTime, o.endTime)),
    endTime: formatTime(o.endTime)
  }
}

function mapInstanceRow(o: Raw): WorkflowInstanceRow {
  const start = formatTime(pick(o.startTime, o.createTime))
  return {
    processInstanceId: String(pick(o.processInstanceId, o.instanceId, o.id) ?? ''),
    processKey: String(pick(o.processKey, o.key, o.definitionKey) ?? ''),
    processName: str(pick(o.processName, o.processDefName)),
    title: String(pick(o.title, o.processTitle, o.businessTitle, o.name) ?? '未命名流程'),
    businessNo: str(pick(o.businessNo, o.bizNo)),
    status: (o.status as string) ?? undefined,
    statusText: str(pick(o.statusText, o.statusLabel)),
    nodeName: str(pick(o.nodeName, o.currentNodeName, o.currentNode, o.currentActivityName)),
    initiatorName: str(pick(o.initiatorName, o.startUserName, o.creatorName)),
    startTime: start,
    createTime: start,
    endTime: formatTime(o.endTime),
    duration: str(pick(o.duration, o.durationText)),
    overdue: bool(o.overdue),
    businessId: (pick(o.businessId, o.bizId) as string | number) ?? undefined
  }
}

function mapTaskDetail(data: unknown): WorkflowTaskDetail {
  const o = raw(data)
  return {
    taskId: String(pick(o.taskId, o.id) ?? ''),
    taskName: str(pick(o.taskName, o.name)),
    nodeName: str(pick(o.nodeName, o.taskName, o.activityName, o.name)),
    processInstanceId: String(pick(o.processInstanceId, o.instanceId) ?? ''),
    processKey: String(pick(o.processKey, o.key) ?? ''),
    processName: str(o.processName),
    title: String(pick(o.title, o.processTitle, o.businessTitle) ?? '流程任务'),
    businessNo: str(pick(o.businessNo, o.bizNo)),
    initiatorName: str(pick(o.initiatorName, o.startUserName)),
    createTime: formatTime(pick(o.createTime, o.startTime)),
    formSchema: mapFormSchema(pick(o.formSchema, o.schema, o.formFields)),
    formData: mapFormData(pick(o.formData, o.formValues, o.variables)),
    businessData: raw(pick(o.businessData, o.business)),
    actions: Array.isArray(o.actions) ? (o.actions as string[]) : undefined
  }
}

function mapInstanceDetail(data: unknown): WorkflowInstanceDetail {
  const o = raw(data)
  return {
    processInstanceId: String(pick(o.processInstanceId, o.instanceId, o.id) ?? ''),
    processKey: String(pick(o.processKey, o.key) ?? ''),
    processName: str(o.processName),
    title: String(pick(o.title, o.processTitle, o.businessTitle) ?? '流程实例'),
    businessNo: str(pick(o.businessNo, o.bizNo)),
    status: (o.status as string) ?? undefined,
    statusText: str(o.statusText),
    nodeName: str(pick(o.nodeName, o.currentNodeName)),
    initiatorName: str(pick(o.initiatorName, o.startUserName)),
    startTime: formatTime(pick(o.startTime, o.createTime)),
    endTime: formatTime(o.endTime),
    overdue: bool(o.overdue),
    formSchema: mapFormSchema(pick(o.formSchema, o.schema, o.formFields)),
    formData: mapFormData(pick(o.formData, o.formValues, o.variables)),
    businessData: raw(pick(o.businessData, o.business))
  }
}

function mapMonitorRow(o: Raw): WorkflowMonitorRow {
  const startRaw = pick(o.startTime, o.createTime)
  const endRaw = pick(o.endTime, o.finishTime)
  let seconds = num(pick(o.durationSeconds, o.costSeconds))
  if (seconds === undefined && startRaw && endRaw) {
    const started = new Date(String(startRaw)).getTime()
    const ended = new Date(String(endRaw)).getTime()
    if (Number.isFinite(started) && Number.isFinite(ended) && ended >= started) {
      seconds = Math.round((ended - started) / 1000)
    }
  }
  if (seconds === undefined && startRaw && !endRaw) {
    const started = new Date(String(startRaw)).getTime()
    if (Number.isFinite(started)) seconds = Math.max(0, Math.round((Date.now() - started) / 1000))
  }
  let duration = str(pick(o.duration, o.durationText))
  if (!duration && seconds !== undefined) {
    if (seconds < 60) duration = `${seconds} 秒`
    else if (seconds < 3600) duration = `${Math.round(seconds / 60)} 分钟`
    else duration = `${(seconds / 3600).toFixed(1)} 小时`
  }
  return {
    processInstanceId: String(pick(o.processInstanceId, o.instanceId, o.id) ?? ''),
    processKey: String(pick(o.processKey, o.key) ?? ''),
    processName: str(o.processName),
    title: String(pick(o.title, o.processTitle, o.businessTitle, o.name) ?? '流程实例'),
    businessNo: str(pick(o.businessNo, o.bizNo)),
    initiatorName: str(pick(o.initiatorName, o.startUserName)),
    nodeName: str(pick(o.nodeName, o.currentNodeName, o.currentNode)),
    startTime: formatTime(pick(o.startTime, o.createTime)),
    duration,
    status: (pick(o.status, o.statusLabel) as string) ?? undefined,
    statusText: str(pick(o.statusLabel, o.statusText, o.statusName)),
    overdue: bool(o.overdue)
  }
}

function mapMonitor(data: unknown): WorkflowMonitorResult {
  const o = raw(parseMaybeJson(data))
  const statsRaw = raw(pick(o.stats, o.statistics, o.summary))
  const statsSource = Object.keys(statsRaw).length ? statsRaw : o
  const list = asList(pick(o.list, o.rows, data))
  const pickNum = (key: string, alias: string[]): number => {
    for (const name of [key, ...alias]) {
      const v = num(statsSource[name])
      if (v !== undefined) return v
    }
    return 0
  }
  return {
    list: list.map(mapMonitorRow),
    total: num(pick(o.total, list.length)) ?? list.length,
    stats: {
      running: pickNum('running', ['runningCount', 'runningTotal']),
      completed: pickNum('completed', ['completedCount', 'finished']),
      rejected: pickNum('rejected', ['rejectedCount']),
      overdue: pickNum('overdue', ['overdueCount', 'timeout', 'timeoutCount'])
    }
  }
}

function mapPurchase(o: Raw): PurchaseRow {
  return {
    id: (pick(o.id, o.purchaseId) ?? '') as string | number,
    applyNo: str(pick(o.applyNo, o.purchaseNo, o.businessNo)),
    title: String(pick(o.title, o.purchaseName) ?? '采购申请'),
    deptId: (pick(o.deptId) as string | number) ?? undefined,
    deptName: str(o.deptName),
    applicantId: (pick(o.applicantId, o.createBy) as string | number) ?? undefined,
    applicantName: str(pick(o.applicantName, o.applyUserName, o.creatorName)),
    amount: num(pick(o.amount, o.totalAmount)),
    budgetAmount: num(o.budgetAmount),
    supplier: str(o.supplier),
    purchaseType: (pick(o.purchaseType) as number | string) ?? undefined,
    purpose: str(pick(o.purpose, o.reason)),
    applyDate: str(pick(o.applyDate, o.createTime)),
    status: (o.status as number | string) ?? undefined,
    remark: str(o.remark),
    processInstanceId: str(pick(o.processInstanceId, o.flowInstanceId, o.instanceId))
  }
}

function mapSeal(o: Raw): SealRow {
  return {
    id: (pick(o.id, o.sealId) ?? '') as string | number,
    applyNo: str(pick(o.applyNo, o.sealNo, o.businessNo)),
    sealType: (pick(o.sealType) as number | string) ?? undefined,
    reason: str(pick(o.reason, o.sealReason, o.applyReason)),
    useDate: str(pick(o.useDate, o.sealDate)),
    fileId: (pick(o.fileId) as string | number) ?? undefined,
    applicantId: (pick(o.applicantId, o.createBy) as string | number) ?? undefined,
    applicantName: str(pick(o.applicantName, o.applyUserName)),
    deptId: (pick(o.deptId) as string | number) ?? undefined,
    deptName: str(o.deptName),
    status: (o.status as number | string) ?? undefined,
    remark: str(o.remark),
    processInstanceId: str(pick(o.processInstanceId, o.flowInstanceId, o.instanceId))
  }
}

function mapBorrow(o: Raw): BorrowRow {
  return {
    id: (pick(o.id, o.borrowId) ?? '') as string | number,
    docId: (pick(o.docId, o.documentId) as string | number) ?? undefined,
    borrowerId: (pick(o.borrowerId) as string | number) ?? undefined,
    borrowerName: str(pick(o.borrowerName, o.applicantName)),
    reason: str(pick(o.reason, o.borrowReason)),
    startDate: str(o.startDate),
    endDate: str(o.endDate),
    status: (o.status as number | string) ?? undefined,
    remark: str(o.remark),
    processInstanceId: str(pick(o.processInstanceId, o.flowInstanceId, o.instanceId))
  }
}

/** 业务单创建响应：单据字段 + workflow{processInstanceId,taskId} */
function mapCreated(data: unknown): CreateInstanceResult {
  const o = raw(data)
  const workflow = raw(pick(o.workflow, o.instance))
  return {
    processInstanceId: str(pick(workflow.processInstanceId, o.processInstanceId)),
    taskId: str(pick(workflow.taskId, o.taskId)),
    businessNo: str(pick(o.applyNo, o.businessNo)),
    pendingWorkflow: bool(o.pendingWorkflow)
  }
}

/** 发起实例（幂等头）。业务单接口若已返回 processInstanceId 则不再二次发起。 */
async function startInstance(payload: CreateInstancePayload): Promise<CreateInstanceResult> {
  return http<CreateInstanceResult>({
    url: '/workflow/instances',
    method: 'post',
    data: payload,
    headers: { 'Idempotency-Key': idempotencyKey() }
  }).then((data) => raw(data) as CreateInstanceResult)
}

export const processApi = {
  list(): Promise<ProcessDef[]> {
    return http<unknown>({ url: '/workflow/processes', method: 'get' }).then((data) => asList(data).map(mapProcess))
  },
  /** 返回 BPMN XML（后端可能直接返回 XML 字符串，或 {bpmnXml} / {xml} 包装） */
  diagram(processKey: string): Promise<string> {
    return http<unknown>({ url: `/workflow/processes/${processKey}/diagram`, method: 'get' }).then((data) => {
      if (typeof data === 'string') {
        const parsed = parseMaybeJson(data)
        if (typeof parsed === 'string') return parsed
        return str(raw(parsed).bpmnXml ?? raw(parsed).xml ?? raw(parsed).content) ?? ''
      }
      const o = raw(data)
      return str(o.bpmnXml ?? o.xml ?? o.content) ?? ''
    })
  }
}

export const instanceApi = {
  create: startInstance,
  detail(id: string): Promise<WorkflowInstanceDetail> {
    return http<unknown>({ url: `/workflow/instances/${id}`, method: 'get' }).then(mapInstanceDetail)
  },
  history(id: string): Promise<WfHistoryItem[]> {
    return http<unknown>({ url: `/workflow/instances/${id}/history`, method: 'get' }).then(mapHistory)
  },
  highlight(id: string): Promise<WfDiagramHighlight> {
    return http<unknown>({ url: `/workflow/instances/${id}/diagram-highlight`, method: 'get' }).then(mapHighlight)
  },
  resubmit(id: string): Promise<CreateInstanceResult> {
    return http<unknown>({ url: `/workflow/instances/${id}/resubmit`, method: 'post' }).then(
      (data) => raw(data) as CreateInstanceResult
    )
  }
}

export const taskApi = {
  todo(params: PageQuery): Promise<PageResult<WorkflowTaskRow>> {
    return http<unknown>({ url: '/workflow/todo', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map(mapTaskRow) }
    })
  },
  done(params: PageQuery): Promise<PageResult<WorkflowTaskRow>> {
    return http<unknown>({ url: '/workflow/done', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map(mapTaskRow) }
    })
  },
  initiated(params: PageQuery): Promise<PageResult<WorkflowInstanceRow>> {
    return http<unknown>({ url: '/workflow/initiated', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map(mapInstanceRow) }
    })
  },
  detail(taskId: string): Promise<WorkflowTaskDetail> {
    return http<unknown>({ url: `/workflow/tasks/${taskId}`, method: 'get' }).then(mapTaskDetail)
  },
  approve(taskId: string, payload: ApprovePayload): Promise<void> {
    return http<void>({ url: `/workflow/tasks/${taskId}/approve`, method: 'post', data: payload })
  },
  reject(taskId: string, payload: RejectPayload): Promise<void> {
    return http<void>({ url: `/workflow/tasks/${taskId}/reject`, method: 'post', data: payload })
  },
  urge(taskId: string, payload: UrgePayload): Promise<void> {
    return http<void>({ url: `/workflow/tasks/${taskId}/urge`, method: 'post', data: payload })
  }
}

export const monitorApi = {
  list(params: PageQuery): Promise<WorkflowMonitorResult> {
    return http<unknown>({ url: '/workflow/monitor', method: 'get', params }).then(mapMonitor)
  }
}

export const purchaseApi = {
  list(params: PageQuery): Promise<PageResult<PurchaseRow>> {
    return http<unknown>({ url: '/workflow/purchase', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map(mapPurchase) }
    })
  },
  detail(id: string | number): Promise<PurchaseRow> {
    return http<unknown>({ url: `/workflow/purchase/${id}`, method: 'get' }).then((data) => mapPurchase(raw(data)))
  },
  /** 创建采购单；submit=true 时后端同步发起 purchase_apply 审批并返回 workflow 信息 */
  async create(payload: PurchasePayload): Promise<CreateInstanceResult> {
    const data = await http<unknown>({ url: '/workflow/purchase', method: 'post', data: payload })
    return mapCreated(data)
  }
}

export const sealApi = {
  list(params: PageQuery): Promise<PageResult<SealRow>> {
    return http<unknown>({ url: '/workflow/seal', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map(mapSeal) }
    })
  },
  detail(id: string | number): Promise<SealRow> {
    return http<unknown>({ url: `/workflow/seal/${id}`, method: 'get' }).then((data) => mapSeal(raw(data)))
  },
  async create(payload: SealPayload): Promise<CreateInstanceResult> {
    const data = await http<unknown>({ url: '/workflow/seal', method: 'post', data: payload })
    return mapCreated(data)
  }
}

export const borrowApi = {
  list(params: PageQuery): Promise<PageResult<BorrowRow>> {
    return http<unknown>({ url: '/workflow/borrows', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map(mapBorrow) }
    })
  },
  async create(payload: BorrowPayload): Promise<CreateInstanceResult> {
    const data = await http<unknown>({ url: '/workflow/borrows', method: 'post', data: payload })
    return mapCreated(data)
  }
}

/** 流程类型下拉（6 条内置流程，接口未就绪时使用） */
export const WORKFLOW_TYPES: { key: string; name: string }[] = [
  { key: 'policy_publish', name: '制度发布审批' },
  { key: 'defect_rectify', name: '缺陷整改审批' },
  { key: 'evaluation_plan', name: '评价计划审批' },
  { key: 'doc_borrow', name: '文档借阅审批' },
  { key: 'purchase_apply', name: '采购申请审批' },
  { key: 'seal_apply', name: '用印申请审批' }
]

export function workflowTypeName(key?: string): string {
  if (!key) return '—'
  return WORKFLOW_TYPES.find((item) => item.key === key)?.name || key
}
