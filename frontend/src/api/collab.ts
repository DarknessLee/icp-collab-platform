import { http } from '@/api/request'
import { formatTime, toArray, toPage } from '@/api/system'
import type { PageQuery, PageResult } from '@/types/system'
import type {
  BorrowPayload,
  BorrowRow,
  CalendarItem,
  DocCategory,
  DocCategoryPayload,
  DocPayload,
  DocPermItem,
  DocRow,
  MessageRow,
  NoticePayload,
  NoticeRow,
  SchedulePayload,
  ScheduleRow,
  TaskComment,
  TaskPayload,
  TaskRow
} from '@/types/collab'

/** 字符串化（兼容数字 ID） */
function str(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') return undefined
  return String(value)
}

function num(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

function bool(value: unknown): boolean | undefined {
  if (value === null || value === undefined || value === '') return undefined
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value === 1
  const text = String(value).toLowerCase()
  return text === 'true' || text === '1' || text === 'yes'
}

function raw(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

/** 后端分页字段兼容：list/records/rows */
function page<T>(data: unknown, mapper: (item: Record<string, unknown>) => T): PageResult<T> {
  const normalized = toPage<Record<string, unknown>>(data)
  return { ...normalized, list: (normalized.list || []).map((item) => mapper(raw(item))) }
}

function mapTask(o: Record<string, unknown>): TaskRow {
  return {
    id: (o.id ?? o.taskId) as TaskRow['id'],
    taskNo: str(o.taskNo ?? o.taskCode),
    title: String(o.taskTitle ?? o.title ?? o.taskName ?? ''),
    taskTitle: str(o.taskTitle ?? o.title),
    taskType: num(o.taskType),
    description: str(o.description ?? o.remark),
    priority: num(o.priority),
    status: num(o.status) as TaskRow['status'],
    assigneeId: str(o.assigneeId),
    assigneeName: str(o.assigneeName),
    creatorId: str(o.creatorId),
    creatorName: str(o.creatorName),
    deptId: str(o.deptId),
    deptName: str(o.deptName),
    planStart: formatTime(o.planStart),
    planEnd: formatTime(o.planEnd),
    remindTime: formatTime(o.remindTime),
    finishTime: formatTime(o.finishTime ?? o.completeTime),
    parentId: str(o.parentId),
    progress: num(o.progress),
    overdue: bool(o.overdue),
    createTime: formatTime(o.createTime),
    children: Array.isArray(o.children)
      ? (o.children as Record<string, unknown>[]).map((child) => mapTask(raw(child)))
      : undefined
  }
}

function mapComment(o: Record<string, unknown>): TaskComment {
  return {
    id: (o.id ?? o.commentId) as TaskComment['id'],
    taskId: str(o.taskId),
    userId: str(o.userId),
    userName: str(o.userName ?? o.createByName),
    content: String(o.content ?? o.comment ?? ''),
    createTime: formatTime(o.createTime)
  }
}

function mapCategory(o: Record<string, unknown>): DocCategory {
  return {
    id: (o.id ?? o.categoryId) as DocCategory['id'],
    parentId: str(o.parentId),
    categoryName: str(o.categoryName ?? o.name),
    name: str(o.categoryName ?? o.name),
    sort: num(o.sort ?? o.orderNum),
    children: Array.isArray(o.children)
      ? (o.children as Record<string, unknown>[]).map((child) => mapCategory(raw(child)))
      : undefined
  }
}

function mapDoc(o: Record<string, unknown>): DocRow {
  return {
    id: (o.id ?? o.docId) as DocRow['id'],
    docNo: str(o.docNo ?? o.docCode),
    title: String(o.docName ?? o.title ?? o.fileName ?? ''),
    categoryId: str(o.categoryId),
    categoryName: str(o.categoryName),
    status: num(o.status) as DocRow['status'],
    version: str(o.version),
    ownerId: str(o.ownerId),
    ownerName: str(o.ownerName ?? o.createByName),
    deptId: str(o.deptId),
    deptName: str(o.deptName),
    fileId: str(o.fileId),
    fileName: str(o.fileName ?? o.originalName),
    fileSize: num(o.fileSize ?? o.size),
    summary: str(o.summary ?? o.description),
    description: str(o.description),
    downloadCount: num(o.downloadCount),
    publishTime: formatTime(o.publishTime),
    createTime: formatTime(o.createTime),
    canDownload: bool(o.canDownload)
  }
}

function mapPerm(o: Record<string, unknown>): DocPermItem {
  return {
    id: str(o.id),
    docId: str(o.docId),
    targetType: (num(o.targetType) ?? 0) as DocPermItem['targetType'],
    targetId: str(o.targetId),
    targetName: str(o.targetName),
    permType: (num(o.permType) ?? 1) as DocPermItem['permType']
  }
}

function mapBorrow(o: Record<string, unknown>): BorrowRow {
  return {
    id: (o.id ?? o.borrowId) as BorrowRow['id'],
    borrowNo: str(o.borrowNo ?? o.applyNo ?? o.borrowCode) || (o.id ? `JY-${o.id}` : ''),
    docId: (o.docId ?? o.fileId) as BorrowRow['docId'],
    docName: str(o.docName ?? o.fileName),
    borrowerId: str(o.borrowerId),
    borrowerName: str(o.borrowerName),
    reason: str(o.reason),
    startDate: formatTime(o.startDate),
    endDate: formatTime(o.endDate),
    status: num(o.status) as BorrowRow['status'],
    processInstanceId: str(o.processInstanceId),
    createTime: formatTime(o.createTime)
  }
}

function mapNotice(o: Record<string, unknown>): NoticeRow {
  return {
    id: (o.id ?? o.noticeId) as NoticeRow['id'],
    title: String(o.noticeTitle ?? o.title ?? ''),
    content: str(o.content),
    noticeType: num(o.noticeType),
    scope: num(o.scope),
    scopeId: str(o.scopeId),
    scopeName: str(o.scopeName),
    scopeDeptIds: toArray(o.scopeDeptIds ?? o.deptIds) as NoticeRow['scopeDeptIds'],
    scopeRoles: toArray(o.scopeRoles ?? o.roleCodes) as NoticeRow['scopeRoles'],
    status: num(o.status) as NoticeRow['status'],
    publisherId: str(o.publisherId),
    publisherName: str(o.publisherName ?? o.createByName),
    publishTime: formatTime(o.publishTime),
    read: bool(o.read ?? o.readFlag),
    createTime: formatTime(o.createTime)
  }
}

function mapMessage(o: Record<string, unknown>): MessageRow {
  return {
    id: (o.id ?? o.messageId) as MessageRow['id'],
    type: num(o.messageType ?? o.type),
    title: String(o.title ?? ''),
    content: str(o.content),
    bizType: str(o.bizType),
    read: bool(o.read ?? o.isRead ?? o.readFlag) ?? false,
    bizId: str(o.bizId),
    createTime: formatTime(o.createTime ?? o.sendTime)
  }
}

function mapSchedule(o: Record<string, unknown>): ScheduleRow {
  return {
    id: (o.id ?? o.scheduleId) as ScheduleRow['id'],
    title: String(o.scheduleTitle ?? o.title ?? ''),
    description: str(o.description ?? o.content),
    type: num(o.scheduleType ?? o.type),
    startTime: formatTime(o.startTime ?? o.start),
    endTime: formatTime(o.endTime ?? o.end),
    location: str(o.location),
    status: num(o.status),
    ownerId: str(o.ownerId),
    ownerName: str(o.ownerName ?? o.createByName),
    deptId: str(o.deptId)
  }
}

function mapCalendar(o: Record<string, unknown>): CalendarItem {
  return {
    id: (o.id ?? o.scheduleId) as CalendarItem['id'],
    title: String(o.title ?? o.scheduleTitle ?? ''),
    start: String(o.start ?? o.startTime ?? ''),
    end: String(o.end ?? o.endTime ?? ''),
    type: num(o.type),
    status: num(o.status)
  }
}

function pageQuery(params: Record<string, unknown>): PageQuery {
  return params as PageQuery
}

/** 任务写入字段映射：前端 title → 后端 taskTitle */
function taskPayload(data: Partial<TaskPayload>): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  if (data.title !== undefined) payload.taskTitle = data.title
  if (data.taskType !== undefined) payload.taskType = data.taskType
  if (data.description !== undefined) payload.description = data.description
  if (data.priority !== undefined) payload.priority = data.priority
  if (data.assigneeId !== undefined) payload.assigneeId = data.assigneeId
  if (data.deptId !== undefined) payload.deptId = data.deptId
  if (data.planStart !== undefined) payload.planStart = data.planStart
  if (data.planEnd !== undefined) payload.planEnd = data.planEnd
  if (data.remindTime !== undefined) payload.remindTime = data.remindTime
  if (data.parentId !== undefined) payload.parentId = data.parentId
  return payload
}

/** 文档写入字段映射：title → docName，summary → description */
function docPayload(data: Partial<DocPayload>): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  if (data.title !== undefined) payload.docName = data.title
  if (data.categoryId !== undefined) payload.categoryId = data.categoryId
  if (data.fileId !== undefined) payload.fileId = data.fileId
  if (data.version !== undefined) payload.version = data.version
  if (data.deptId !== undefined) payload.deptId = data.deptId
  if (data.summary !== undefined) payload.description = data.summary
  return payload
}

/** 公告写入字段映射：title → noticeTitle（scope 系列原样传递） */
function noticePayload(data: Partial<NoticePayload>): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  if (data.title !== undefined) payload.noticeTitle = data.title
  if (data.content !== undefined) payload.content = data.content
  if (data.noticeType !== undefined) payload.noticeType = data.noticeType
  if (data.scope !== undefined) payload.scope = data.scope
  if (data.scopeDeptIds !== undefined) payload.scopeDeptIds = data.scopeDeptIds
  if (data.scopeRoles !== undefined) payload.scopeRoles = data.scopeRoles
  return payload
}

/** 日程写入字段映射：title → scheduleTitle，type → scheduleType */
function schedulePayload(data: Partial<SchedulePayload>): Record<string, unknown> {
  const payload: Record<string, unknown> = {}
  if (data.title !== undefined) payload.scheduleTitle = data.title
  if (data.type !== undefined) payload.scheduleType = data.type
  if (data.description !== undefined) payload.description = data.description
  if (data.startTime !== undefined) payload.startTime = data.startTime
  if (data.endTime !== undefined) payload.endTime = data.endTime
  if (data.location !== undefined) payload.location = data.location
  return payload
}

export const taskApi = {
  list: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/tasks', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapTask)
    ),
  my: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/tasks/my', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapTask)
    ),
  overdue: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/tasks/overdue', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapTask)
    ),
  detail: (id: TaskRow['id']) =>
    http<unknown>({ url: `/tasks/${id}`, method: 'get' }).then((data) => mapTask(raw(data))),
  create: (data: TaskPayload) =>
    http<unknown>({ url: '/tasks', method: 'post', data: taskPayload(data) }),
  update: (id: TaskRow['id'], data: Partial<TaskPayload>) =>
    http<unknown>({ url: `/tasks/${id}`, method: 'put', data: taskPayload(data) }),
  remove: (id: TaskRow['id']) => http<void>({ url: `/tasks/${id}`, method: 'delete' }),
  start: (id: TaskRow['id']) => http<void>({ url: `/tasks/${id}/start`, method: 'post' }),
  complete: (id: TaskRow['id'], data?: { comment?: string }) =>
    http<void>({ url: `/tasks/${id}/complete`, method: 'post', data }),
  cancel: (id: TaskRow['id'], data?: { reason?: string }) =>
    http<void>({ url: `/tasks/${id}/cancel`, method: 'post', data }),
  remind: (id: TaskRow['id'], data?: { content?: string }) =>
    http<void>({ url: `/tasks/${id}/remind`, method: 'post', data }),
  comments: (id: TaskRow['id']) =>
    http<unknown>({ url: `/tasks/${id}/comments`, method: 'get' }).then((data) =>
      toArray<Record<string, unknown>>(data).map((item) => mapComment(raw(item)))
    ),
  addComment: (id: TaskRow['id'], content: string) =>
    http<void>({ url: `/tasks/${id}/comments`, method: 'post', data: { content } })
}

export const docCategoryApi = {
  tree: () =>
    http<unknown>({ url: '/doc-categories/tree', method: 'get' }).then((data) =>
      toArray<Record<string, unknown>>(data).map((item) => mapCategory(raw(item)))
    ),
  create: (data: DocCategoryPayload) =>
    http<unknown>({ url: '/doc-categories', method: 'post', data }),
  update: (id: DocCategory['id'], data: DocCategoryPayload) =>
    http<unknown>({ url: `/doc-categories/${id}`, method: 'put', data }),
  remove: (id: DocCategory['id']) =>
    http<void>({ url: `/doc-categories/${id}`, method: 'delete' })
}

export const docApi = {
  list: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/docs', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapDoc)
    ),
  detail: (id: DocRow['id']) =>
    http<unknown>({ url: `/docs/${id}`, method: 'get' }).then((data) => mapDoc(raw(data))),
  create: (data: DocPayload) =>
    http<unknown>({ url: '/docs', method: 'post', data: docPayload(data) }),
  update: (id: DocRow['id'], data: Partial<DocPayload>) =>
    http<unknown>({ url: `/docs/${id}`, method: 'put', data: docPayload(data) }),
  remove: (id: DocRow['id']) => http<void>({ url: `/docs/${id}`, method: 'delete' }),
  publish: (id: DocRow['id']) => http<void>({ url: `/docs/${id}/publish`, method: 'post' }),
  archive: (id: DocRow['id']) => http<void>({ url: `/docs/${id}/archive`, method: 'post' }),
  download: (id: DocRow['id']) =>
    http<Blob>({ url: `/docs/${id}/download`, method: 'get', responseType: 'blob' }),
  getPerms: (id: DocRow['id']) =>
    http<unknown>({ url: `/docs/${id}/perms`, method: 'get' }).then((data) =>
      toArray<Record<string, unknown>>(data).map((item) => mapPerm(raw(item)))
    ),
  savePerms: (id: DocRow['id'], perms: DocPermItem[]) =>
    http<void>({
      url: `/docs/${id}/perms`,
      method: 'put',
      data: {
        perms: perms.map((item) => ({
          targetType: item.targetType,
          targetId: item.targetType === 0 ? 0 : Number(item.targetId),
          permType: item.permType
        }))
      }
    })
}

export const borrowApi = {
  list: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/borrows', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapBorrow)
    ),
  detail: (id: BorrowRow['id']) =>
    http<unknown>({ url: `/borrows/${id}`, method: 'get' }).then((data) => mapBorrow(raw(data))),
  create: (data: BorrowPayload) => http<unknown>({ url: '/borrows', method: 'post', data }),
  returnDoc: (id: BorrowRow['id']) =>
    http<void>({ url: `/borrows/${id}/return`, method: 'post' })
}

export const noticeApi = {
  list: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/notices', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapNotice)
    ),
  detail: (id: NoticeRow['id']) =>
    http<unknown>({ url: `/notices/${id}`, method: 'get' }).then((data) => mapNotice(raw(data))),
  create: (data: NoticePayload) =>
    http<unknown>({ url: '/notices', method: 'post', data: noticePayload(data) }),
  update: (id: NoticeRow['id'], data: Partial<NoticePayload>) =>
    http<unknown>({ url: `/notices/${id}`, method: 'put', data: noticePayload(data) }),
  remove: (id: NoticeRow['id']) => http<void>({ url: `/notices/${id}`, method: 'delete' }),
  publish: (id: NoticeRow['id']) => http<void>({ url: `/notices/${id}/publish`, method: 'post' }),
  close: (id: NoticeRow['id']) => http<void>({ url: `/notices/${id}/close`, method: 'post' }),
  read: (id: NoticeRow['id']) => http<void>({ url: `/notices/${id}/read`, method: 'post' }),
  unreadCount: () =>
    http<unknown>({ url: '/notices/unread-count', method: 'get' }).then((data) => {
      const o = raw(data)
      return num(o.count ?? o.unread ?? o.total ?? data) ?? 0
    })
}

export const messageApi = {
  list: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/messages', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapMessage)
    ),
  read: (ids: Array<string | number>) =>
    http<void>({ url: '/messages/read', method: 'post', data: { ids } }),
  readAll: async () => {
    // 后端 /messages/read 要求 ids 非空（List<Long>），先取全部未读再批量已读
    const data = await http<unknown>({
      url: '/messages',
      method: 'get',
      params: { pageNum: 1, pageSize: 500, readFlag: 0 }
    })
    const list = Array.isArray(raw(data).list) ? (raw(data).list as Array<Record<string, unknown>>) : []
    const ids = list.map((item) => Number(item.id)).filter((id) => Number.isFinite(id))
    if (!ids.length) return
    await http<void>({ url: '/messages/read', method: 'post', data: { ids } })
  },
  unreadCount: () =>
    http<unknown>({ url: '/messages/unread-count', method: 'get' }).then((data) => {
      const o = raw(data)
      return num(o.count ?? o.unread ?? o.total ?? data) ?? 0
    }),
  create: (data: { title: string; content?: string; type?: number; userIds?: Array<string | number> }) =>
    http<void>({ url: '/messages', method: 'post', data })
}

/** SSE 地址：EventSource 无法携带自定义头，使用 access_token query（日志侧需脱敏） */
export function messageStreamUrl(token: string): string {
  const base = import.meta.env.VITE_API_BASE || '/api/v1'
  return `${base}/messages/stream?access_token=${encodeURIComponent(token)}`
}

export const scheduleApi = {
  list: (params: Record<string, unknown>) =>
    http<unknown>({ url: '/schedules', method: 'get', params: pageQuery(params) }).then((data) =>
      page(data, mapSchedule)
    ),
  calendar: (month: string) =>
    http<unknown>({
      url: '/schedules/calendar',
      method: 'get',
      params: { month }
    }).then((data) => {
      const o = raw(data)
      const items = o.items ?? o.list ?? data
      return toArray<Record<string, unknown>>(items).map((item) => mapCalendar(raw(item)))
    }),
  create: (data: SchedulePayload) =>
    http<unknown>({ url: '/schedules', method: 'post', data: schedulePayload(data) }),
  update: (id: ScheduleRow['id'], data: Partial<SchedulePayload>) =>
    http<unknown>({ url: `/schedules/${id}`, method: 'put', data: schedulePayload(data) }),
  remove: (id: ScheduleRow['id']) => http<void>({ url: `/schedules/${id}`, method: 'delete' }),
  cancel: (id: ScheduleRow['id']) =>
    http<void>({ url: `/schedules/${id}/cancel`, method: 'post' })
}

