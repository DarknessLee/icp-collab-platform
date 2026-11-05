/** M2d 协同运营模块类型定义（任务/文档/借阅/公告/消息/日程） */

export type CollabId = string | number

/** 任务状态：0 待处理 / 1 进行中 / 2 已完成 / 3 已取消 */
export type TaskStatus = 0 | 1 | 2 | 3

export interface TaskRow {
  id: CollabId
  taskNo?: string
  title: string
  /** 任务标题（后端 DDL 字段 task_title） */
  taskTitle?: string
  /** 1 计划 / 2 任务 / 3 子任务 */
  taskType?: number
  description?: string
  /** 1 高 / 2 中 / 3 低 */
  priority?: number
  status?: TaskStatus
  assigneeId?: CollabId
  assigneeName?: string
  creatorId?: CollabId
  creatorName?: string
  deptId?: CollabId
  deptName?: string
  planStart?: string
  planEnd?: string
  remindTime?: string
  finishTime?: string
  parentId?: CollabId
  progress?: number
  overdue?: boolean
  createTime?: string
  children?: TaskRow[]
}

export interface TaskPayload {
  title: string
  taskType?: number
  description?: string
  priority?: number
  assigneeId?: CollabId
  deptId?: CollabId
  planStart?: string
  planEnd?: string
  remindTime?: string
  parentId?: CollabId
}

export interface TaskComment {
  id: CollabId
  taskId?: CollabId
  userId?: CollabId
  userName?: string
  content: string
  createTime?: string
}

/** 文档状态：0 草稿 / 1 已发布 / 2 已归档 */
export type DocStatus = 0 | 1 | 2

export interface DocCategory {
  id: CollabId
  parentId?: CollabId
  categoryName?: string
  name?: string
  sort?: number
  children?: DocCategory[]
}

export interface DocCategoryPayload {
  categoryName: string
  parentId?: CollabId
  sort?: number
}

export interface DocRow {
  id: CollabId
  docNo?: string
  title: string
  categoryId?: CollabId
  categoryName?: string
  status?: DocStatus
  version?: string
  ownerId?: CollabId
  ownerName?: string
  deptId?: CollabId
  deptName?: string
  fileId?: CollabId
  fileName?: string
  fileSize?: number
  summary?: string
  description?: string
  downloadCount?: number
  publishTime?: string
  createTime?: string
  /** 当前用户是否可下载（后端可计算，前端按钮兜底用） */
  canDownload?: boolean
}

export interface DocPayload {
  title: string
  categoryId?: CollabId
  fileId?: CollabId
  summary?: string
  version?: string
  deptId?: CollabId
}

/** 文档权限目标类型：0 全员 / 1 角色 / 2 用户 / 3 部门（实际 DDL 以 docs/03 §6.1 为准） */
export type DocPermTargetType = 0 | 1 | 2 | 3

/** 文档权限位：1 查看 / 2 下载 / 3 编辑（可多行组合） */
export type DocPermRight = 1 | 2 | 3

/** 接口按原始三元组返回：{targetType,targetId,permType} */
export interface DocPermItem {
  id?: CollabId
  docId?: CollabId
  targetType: DocPermTargetType
  /** 全员约定 targetId=0 */
  targetId?: CollabId
  targetName?: string
  permType: DocPermRight
}

/** 借阅状态：0 申请中 / 1 已通过 / 2 已驳回 / 3 已归还 */
export type BorrowStatus = 0 | 1 | 2 | 3

export interface BorrowRow {
  id: CollabId
  borrowNo?: string
  docId: CollabId
  docName?: string
  borrowerId?: CollabId
  borrowerName?: string
  reason?: string
  startDate?: string
  endDate?: string
  status?: BorrowStatus
  processInstanceId?: string
  createTime?: string
}

export interface BorrowPayload {
  docId: CollabId
  reason: string
  startDate?: string
  endDate?: string
}

/** 公告状态：0 草稿 / 1 已发布 / 2 已关闭 */
export type NoticeStatus = 0 | 1 | 2

export interface NoticeRow {
  id: CollabId
  title: string
  content?: string
  /** 1 通知 / 2 公告 / 3 制度宣贯 */
  noticeType?: number
  /** 1 全员 / 2 部门 / 3 角色 */
  scope?: number
  scopeId?: CollabId
  scopeName?: string
  scopeDeptIds?: CollabId[]
  scopeRoles?: string[]
  status?: NoticeStatus
  publisherId?: CollabId
  publisherName?: string
  publishTime?: string
  read?: boolean
  createTime?: string
}

export interface NoticePayload {
  title: string
  content: string
  noticeType?: number
  scope?: number
  scopeId?: CollabId
  scopeDeptIds?: Array<string | number>
  scopeRoles?: string[]
}

export interface MessageRow {
  id: CollabId
  /** 1 系统 / 2 审批 / 3 任务 / 4 公告 / 5 预警 */
  type?: number
  /** 业务类型标识，如 REMIND */
  bizType?: string
  title: string
  content?: string
  read?: boolean
  bizId?: CollabId
  createTime?: string
}

export interface ScheduleRow {
  id: CollabId
  title: string
  description?: string
  /** 1 会议 / 2 提醒 / 3 个人 */
  type?: number
  startTime?: string
  endTime?: string
  location?: string
  /** 0 待开始 / 1 进行中 / 2 已结束 / 3 已取消 */
  status?: number
  ownerId?: CollabId
  ownerName?: string
  deptId?: CollabId
}

export interface SchedulePayload {
  title: string
  description?: string
  type?: number
  startTime?: string
  endTime?: string
  location?: string
}

export interface CalendarItem {
  id: CollabId
  title: string
  start: string
  end: string
  type?: number
  status?: number
}

/** 会议状态：0 待召开 / 1 已召开 / 2 已取消 */
export type MeetingStatus = 0 | 1 | 2

/** 会议类型字典 icp_meeting_type：0 周例会 / 1 专题会 / 2 评审会 / 3 培训会 */
export interface MeetingRow {
  id: CollabId
  meetingNo?: string
  title: string
  /** 0 周例会 / 1 专题会 / 2 评审会 / 3 培训会 */
  meetingType?: number
  startTime?: string
  endTime?: string
  location?: string
  organizerId?: CollabId
  organizerName?: string
  deptId?: CollabId
  deptName?: string
  /** 参会人中文名，逗号分隔（后端派生字段） */
  attendeeNames?: string
  attendeeCount?: number
  status?: MeetingStatus
  minutes?: string
  remark?: string
  createTime?: string
}

export interface MeetingPayload {
  title: string
  /** 后端实体为 VARCHAR，按字典值字符串传输（"0"~"3"） */
  meetingType?: number | string
  startTime?: string
  endTime?: string
  location?: string
  /** 参会人中文名，逗号分隔 */
  attendeeNames?: string
  remark?: string
}
