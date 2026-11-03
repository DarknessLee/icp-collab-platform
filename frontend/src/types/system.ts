/** M2a 系统管理相关类型（依据 docs/03 §1.3 分页、§2 认证、§3 组织权限、§8 审计） */

export type Id = string | number

/** 统一分页响应 data 结构 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum?: number
  pageSize?: number
}

/** 分页查询通用参数（各接口按需扩展） */
export interface PageQuery {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number | ''
  [key: string]: unknown
}

export type EnabledStatus = 0 | 1

export interface DeptNode {
  id: Id
  parentId?: Id | null
  name: string
  code?: string
  leader?: string
  phone?: string
  email?: string
  sort?: number
  status?: EnabledStatus
  children?: DeptNode[]
}

export interface DeptPayload {
  name: string
  parentId?: Id | null
  leader?: string
  phone?: string
  email?: string
  sort?: number
  status?: EnabledStatus
}

export interface PostRow {
  id: Id
  code: string
  name: string
  sort?: number
  status?: EnabledStatus
  remark?: string
  createTime?: string
}

export interface PostPayload {
  code: string
  name: string
  sort?: number
  status?: EnabledStatus
  remark?: string
}

export interface UserRow {
  id: Id
  username: string
  realName: string
  deptId?: Id | null
  deptName?: string
  postId?: Id | null
  postName?: string
  roleIds?: Id[]
  roleNames?: string[]
  roles?: Array<string | { id?: Id; name?: string }>
  mobile?: string
  email?: string
  status?: EnabledStatus
  lastLoginTime?: string
  remark?: string
  createTime?: string
}

export interface UserPayload {
  username?: string
  realName: string
  deptId?: Id | null
  postId?: Id | null
  mobile?: string
  email?: string
  password?: string
  roleIds?: Id[]
  status?: EnabledStatus
  remark?: string
}

export interface RoleRow {
  id: Id
  name: string
  code: string
  dataScope?: number
  sort?: number
  status?: EnabledStatus
  builtin?: boolean
  remark?: string
  createTime?: string
}

export interface RolePayload {
  name: string
  code: string
  dataScope: number
  sort?: number
  status?: EnabledStatus
  remark?: string
}

export interface MenuNode {
  id: Id
  parentId?: Id | null
  name: string
  path?: string
  component?: string
  icon?: string
  type?: string
  perms?: string
  sort?: number
  visible?: number
  status?: EnabledStatus
  children?: MenuNode[]
}

export interface MenuPayload {
  parentId?: Id | null
  name: string
  type: string
  path?: string
  icon?: string
  perms?: string
  sort?: number
  visible?: number
  status?: EnabledStatus
}

export interface DictTypeRow {
  id: Id
  name: string
  type: string
  status?: EnabledStatus
  remark?: string
  createTime?: string
}

export interface DictItemRow {
  id: Id
  typeId?: Id
  type?: string
  label: string
  value: string
  sort?: number
  tagType?: string
  status?: EnabledStatus
  remark?: string
}

export interface DictItemPayload {
  typeId?: Id
  type?: string
  label: string
  value: string
  sort?: number
  tagType?: string
  status?: EnabledStatus
  remark?: string
}

export interface ConfigRow {
  id: Id
  key: string
  value: string
  name: string
  remark?: string
  builtin?: boolean
  updateTime?: string
}

export interface OperLogRow {
  id: Id
  operName?: string
  module?: string
  action?: string
  bizType?: string
  bizId?: Id
  status?: number
  errorMsg?: string
  params?: string
  result?: string
  traceId?: string
  duration?: number
  ip?: string
  createTime?: string
}

export interface LoginLogRow {
  id: Id
  username?: string
  ip?: string
  status?: number
  message?: string
  userAgent?: string
  loginTime?: string
}

export interface ChangeLogRow {
  id: Id
  operName?: string
  bizType?: string
  bizId?: Id
  before?: string
  after?: string
  traceId?: string
  createTime?: string
}

export interface JobRow {
  id: Id
  name: string
  group?: string
  cron?: string
  status?: EnabledStatus
  lastRunTime?: string
  nextRunTime?: string
  remark?: string
}

export interface OnlineSession {
  jti: string
  username: string
  ip?: string
  loginTime?: string
  lastActive?: string
}

export interface UploadResult {
  id: Id
  originalName: string
  size: number
  suffix?: string
  contentType?: string
}
