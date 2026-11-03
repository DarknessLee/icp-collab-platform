import { http } from './request'
import type {
  ChangeLogRow,
  ConfigRow,
  DeptNode,
  DeptPayload,
  DictItemPayload,
  DictItemRow,
  DictTypeRow,
  JobRow,
  LoginLogRow,
  MenuNode,
  MenuPayload,
  OnlineSession,
  OperLogRow,
  PageResult,
  PostPayload,
  PostRow,
  RolePayload,
  RoleRow,
  UploadResult,
  UserPayload,
  UserRow
} from '@/types/system'

/**
 * M2a 系统管理接口（docs/03 §2/§3/§8）。
 * 后端实体字段（deptName/menuName/roleCode 等）与前端视图模型字段（name/path/type）不同，
 * 统一在本层做双向映射，页面只使用前端语义字段。
 */

type Raw = Record<string, unknown>

/** 兼容后端返回数组或分页对象两种形态 */
export function toPage<T>(data: unknown): PageResult<T> {
  if (Array.isArray(data)) {
    return { list: data as T[], total: (data as unknown[]).length }
  }
  const obj = (data || {}) as { list?: T[]; rows?: T[]; total?: number; pageNum?: number; pageSize?: number }
  const list = Array.isArray(obj.list) ? obj.list : Array.isArray(obj.rows) ? obj.rows : []
  return {
    list,
    total: typeof obj.total === 'number' ? obj.total : list.length,
    pageNum: obj.pageNum,
    pageSize: obj.pageSize
  }
}

/** 兼容后端返回数组或 { list } 两种形态 */
export function toArray<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[]
  const obj = (data || {}) as { list?: T[] }
  return Array.isArray(obj.list) ? obj.list : []
}

function str(value: unknown): string | undefined {
  if (value === null || value === undefined) return undefined
  return String(value)
}

function num(value: unknown, fallback?: number): number | undefined {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

/** ISO 时间（2026-11-03T12:21:39）→ 表格展示格式（2026-11-03 12:21:39） */
export function formatTime(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value) return undefined
  return value.replace('T', ' ').slice(0, 19)
}

function pretty(value: unknown): string | undefined {
  if (value === null || value === undefined || value === '') return undefined
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return JSON.stringify(parsed, null, 2)
  } catch {
    return typeof value === 'string' ? value : undefined
  }
}

function parseJson(value: unknown): Raw | null {
  if (typeof value !== 'string' || !value) return null
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? (parsed as Raw) : null
  } catch {
    return null
  }
}

/* ------------------------------ 部门 ------------------------------ */
function mapDept(raw: Raw): DeptNode {
  const children = Array.isArray(raw.children) ? (raw.children as Raw[]) : []
  return {
    id: raw.id as DeptNode['id'],
    parentId: (raw.parentId as DeptNode['parentId']) ?? null,
    name: str(raw.deptName) || str(raw.name) || '',
    code: str(raw.deptCode),
    leader: str(raw.leaderName) || str(raw.leader),
    phone: str(raw.phone),
    email: str(raw.email),
    sort: num(raw.orderNum ?? raw.sort),
    status: (num(raw.status, 1) as DeptNode['status']) ?? 1,
    children: children.map(mapDept)
  }
}

function mapDeptPayload(payload: DeptPayload): Raw {
  return {
    parentId: payload.parentId ?? 0,
    deptName: payload.name,
    orderNum: payload.sort ?? 1,
    phone: payload.phone,
    email: payload.email,
    status: payload.status ?? 1
  }
}

export const deptApi = {
  tree: (params?: Record<string, unknown>) =>
    http<Raw[]>({ url: '/depts/tree', method: 'get', params }).then((list) =>
      toArray<Raw>(list).map((item) => mapDept(item as Raw))
    ),
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/depts', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapDept(item as Raw)) }
    }),
  create: (data: DeptPayload) => http({ url: '/depts', method: 'post', data: mapDeptPayload(data) }),
  update: (id: string | number, data: DeptPayload) =>
    http({ url: `/depts/${id}`, method: 'put', data: mapDeptPayload(data) }),
  remove: (id: string | number) => http({ url: `/depts/${id}`, method: 'delete' })
}

/* ------------------------------ 岗位 ------------------------------ */
function mapPost(raw: Raw): PostRow {
  return {
    id: raw.id as PostRow['id'],
    code: str(raw.postCode) || str(raw.code) || '',
    name: str(raw.postName) || str(raw.name) || '',
    sort: num(raw.orderNum ?? raw.sort),
    status: (num(raw.status, 1) as PostRow['status']) ?? 1,
    remark: str(raw.remark),
    createTime: formatTime(raw.createTime)
  }
}

function mapPostPayload(payload: PostPayload): Raw {
  return {
    postCode: payload.code,
    postName: payload.name,
    orderNum: payload.sort ?? 1,
    status: payload.status ?? 1,
    remark: payload.remark
  }
}

export const postApi = {
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/posts', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapPost(item as Raw)) }
    }),
  create: (data: PostPayload) => http({ url: '/posts', method: 'post', data: mapPostPayload(data) }),
  update: (id: string | number, data: PostPayload) =>
    http({ url: `/posts/${id}`, method: 'put', data: mapPostPayload(data) }),
  remove: (id: string | number) => http({ url: `/posts/${id}`, method: 'delete' })
}

/* ------------------------------ 用户 ------------------------------ */
function mapUser(raw: Raw): UserRow {
  const roleIds = Array.isArray(raw.roleIds) ? (raw.roleIds as UserRow['roleIds']) : undefined
  const roleNames = Array.isArray(raw.roleNames) ? (raw.roleNames as string[]) : undefined
  return {
    id: raw.id as UserRow['id'],
    username: str(raw.username) || '',
    realName: str(raw.realName) || '',
    deptId: (raw.deptId as UserRow['deptId']) ?? null,
    deptName: str(raw.deptName),
    postId: (raw.postId as UserRow['postId']) ?? null,
    postName: str(raw.postName),
    roleIds,
    roleNames,
    roles: roleNames,
    mobile: str(raw.mobile),
    email: str(raw.email),
    status: (num(raw.status, 1) as UserRow['status']) ?? 1,
    lastLoginTime: formatTime(raw.loginTime ?? raw.lastLoginTime),
    remark: str(raw.remark),
    createTime: formatTime(raw.createTime)
  }
}

function mapUserPayload(payload: UserPayload): Raw {
  return {
    username: payload.username,
    realName: payload.realName,
    deptId: payload.deptId ?? undefined,
    postId: payload.postId ?? undefined,
    mobile: payload.mobile,
    email: payload.email,
    password: payload.password,
    roleIds: payload.roleIds,
    status: payload.status ?? 1,
    remark: payload.remark
  }
}

export const userApi = {
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/users', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapUser(item as Raw)) }
    }),
  detail: (id: string | number) =>
    http<Raw>({ url: `/users/${id}`, method: 'get' }).then((data) => mapUser(data as Raw)),
  create: (data: UserPayload) => http({ url: '/users', method: 'post', data: mapUserPayload(data) }),
  update: (id: string | number, data: UserPayload) =>
    http({ url: `/users/${id}`, method: 'put', data: mapUserPayload(data) }),
  remove: (id: string | number) => http({ url: `/users/${id}`, method: 'delete' }),
  updateStatus: (id: string | number, status: number) =>
    http({ url: `/users/${id}/status`, method: 'put', data: { status } }),
  resetPassword: (id: string | number, password: string) =>
    http({ url: `/users/${id}/reset-password`, method: 'put', data: { password } }),
  assignRoles: (id: string | number, roleIds: Array<string | number>) =>
    http({ url: `/users/${id}/roles`, method: 'put', data: { roleIds } })
}

/* ------------------------------ 角色 ------------------------------ */
function mapRole(raw: Raw): RoleRow {
  return {
    id: raw.id as RoleRow['id'],
    name: str(raw.roleName) || str(raw.name) || '',
    code: str(raw.roleCode) || str(raw.code) || '',
    dataScope: num(raw.dataScope, 4),
    sort: num(raw.roleSort ?? raw.sort),
    status: (num(raw.status, 1) as RoleRow['status']) ?? 1,
    builtin: Boolean(raw.builtin) || ['ADMIN', 'ICP_MANAGER', 'DEPT_MANAGER', 'EMPLOYEE', 'AUDITOR', 'MANAGEMENT'].includes(str(raw.roleCode) || ''),
    remark: str(raw.remark),
    createTime: formatTime(raw.createTime)
  }
}

function mapRolePayload(payload: RolePayload): Raw {
  return {
    roleCode: payload.code,
    roleName: payload.name,
    roleSort: payload.sort ?? 1,
    dataScope: payload.dataScope,
    status: payload.status ?? 1,
    remark: payload.remark
  }
}

export const roleApi = {
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/roles', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapRole(item as Raw)) }
    }),
  create: (data: RolePayload) => http({ url: '/roles', method: 'post', data: mapRolePayload(data) }),
  update: (id: string | number, data: RolePayload) =>
    http({ url: `/roles/${id}`, method: 'put', data: mapRolePayload(data) }),
  remove: (id: string | number) => http({ url: `/roles/${id}`, method: 'delete' }),
  getMenus: (id: string | number) => http<unknown>({ url: `/roles/${id}/menus`, method: 'get' }),
  assignMenus: (id: string | number, menuIds: Array<string | number>) =>
    http({ url: `/roles/${id}/menus`, method: 'put', data: { menuIds } })
}

/* ------------------------------ 菜单 ------------------------------ */
function mapMenu(raw: Raw): MenuNode {
  const children = Array.isArray(raw.children) ? (raw.children as Raw[]) : []
  return {
    id: raw.id as MenuNode['id'],
    parentId: (raw.parentId as MenuNode['parentId']) ?? null,
    name: str(raw.menuName) || str(raw.name) || '',
    path: str(raw.path),
    component: str(raw.component),
    icon: str(raw.icon),
    type: str(raw.menuType) || str(raw.type) || 'C',
    perms: str(raw.perms),
    sort: num(raw.orderNum ?? raw.sort),
    visible: (num(raw.visible, 1) as number) ?? 1,
    status: (num(raw.status, 1) as MenuNode['status']) ?? 1,
    children: children.map(mapMenu)
  }
}

function mapMenuPayload(payload: MenuPayload): Raw {
  return {
    parentId: payload.parentId ?? 0,
    menuName: payload.name,
    menuType: payload.type,
    path: payload.path,
    component: payload.path ? String(payload.path).replace(/^\//, '') + '/index' : '',
    icon: payload.icon,
    perms: payload.perms,
    orderNum: payload.sort ?? 1,
    visible: payload.visible ?? 1,
    status: payload.status ?? 1
  }
}

export const menuApi = {
  tree: () =>
    http<Raw[]>({ url: '/menus/tree', method: 'get' }).then((list) => toArray<Raw>(list).map((item) => mapMenu(item as Raw))),
  create: (data: MenuPayload) => http({ url: '/menus', method: 'post', data: mapMenuPayload(data) }),
  update: (id: string | number, data: MenuPayload) =>
    http({ url: `/menus/${id}`, method: 'put', data: mapMenuPayload(data) }),
  remove: (id: string | number) => http({ url: `/menus/${id}`, method: 'delete' })
}

/* ------------------------------ 字典 ------------------------------ */
function mapDictType(raw: Raw): DictTypeRow {
  return {
    id: raw.id as DictTypeRow['id'],
    name: str(raw.dictName) || str(raw.name) || '',
    type: str(raw.dictType) || str(raw.type) || '',
    status: (num(raw.status, 1) as DictTypeRow['status']) ?? 1,
    remark: str(raw.remark),
    createTime: formatTime(raw.createTime)
  }
}

function mapDictTypePayload(payload: Partial<DictTypeRow>): Raw {
  return {
    dictName: payload.name,
    dictType: payload.type,
    status: payload.status ?? 1,
    remark: payload.remark
  }
}

function mapDictItem(raw: Raw): DictItemRow {
  return {
    id: raw.id as DictItemRow['id'],
    type: str(raw.dictType) || str(raw.type),
    label: str(raw.dictLabel) || str(raw.label) || '',
    value: str(raw.dictValue) || str(raw.value) || '',
    sort: num(raw.dictSort ?? raw.sort),
    tagType: str(raw.tagType),
    status: (num(raw.status, 1) as DictItemRow['status']) ?? 1,
    remark: str(raw.remark)
  }
}

function mapDictItemPayload(payload: DictItemPayload): Raw {
  return {
    dictType: payload.type ?? (typeof payload.typeId === 'string' ? payload.typeId : undefined),
    dictLabel: payload.label,
    dictValue: payload.value,
    dictSort: payload.sort ?? 1,
    tagType: payload.tagType,
    status: payload.status ?? 1,
    remark: payload.remark
  }
}

export const dictApi = {
  listTypes: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/dicts/types', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapDictType(item as Raw)) }
    }),
  createType: (data: Partial<DictTypeRow>) =>
    http({ url: '/dicts/types', method: 'post', data: mapDictTypePayload(data) }),
  updateType: (id: string | number, data: Partial<DictTypeRow>) =>
    http({ url: `/dicts/types/${id}`, method: 'put', data: mapDictTypePayload(data) }),
  listItems: (type: string, params?: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({
      url: `/dicts/${encodeURIComponent(type)}/items`,
      method: 'get',
      params
    }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapDictItem(item as Raw)) }
    }),
  createItem: (data: DictItemPayload) =>
    http({ url: '/dicts/items', method: 'post', data: mapDictItemPayload(data) }),
  updateItem: (id: string | number, data: DictItemPayload) =>
    http({ url: `/dicts/items/${id}`, method: 'put', data: mapDictItemPayload(data) }),
  removeItem: (id: string | number) => http({ url: `/dicts/items/${id}`, method: 'delete' })
}

/* ------------------------------ 参数 ------------------------------ */
function mapConfig(raw: Raw): ConfigRow {
  return {
    id: raw.id as ConfigRow['id'],
    key: str(raw.configKey) || str(raw.key) || '',
    value: str(raw.configValue) ?? str(raw.value) ?? '',
    name: str(raw.configName) || str(raw.name) || '',
    remark: str(raw.remark),
    builtin: String(raw.configType ?? raw.builtin ?? '').toUpperCase() === 'Y',
    updateTime: formatTime(raw.updateTime ?? raw.createTime)
  }
}

export const configApi = {
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/configs', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapConfig(item as Raw)) }
    }),
  update: (id: string | number, value: string) =>
    http({ url: `/configs/${id}`, method: 'put', data: { configValue: value } })
}

/* ------------------------------ 定时任务 ------------------------------ */
function mapJob(raw: Raw): JobRow {
  return {
    id: raw.id as JobRow['id'],
    name: str(raw.jobName) || str(raw.name) || '',
    group: str(raw.jobGroup),
    cron: str(raw.cronExpression) || str(raw.cron),
    status: (num(raw.status, 1) as JobRow['status']) ?? 1,
    lastRunTime: formatTime(raw.lastRunTime),
    nextRunTime: formatTime(raw.nextRunTime),
    remark: str(raw.remark)
  }
}

export const jobApi = {
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/jobs', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapJob(item as Raw)) }
    }),
  updateStatus: (id: string | number, status: number) =>
    http({ url: `/jobs/${id}/status`, method: 'put', data: { status } }),
  run: (id: string | number) => http({ url: `/jobs/${id}/run`, method: 'post' })
}

/* ------------------------------ 日志 ------------------------------ */
/** 操作日志：后端 status=0 表示成功，前端表格按 1=成功展示 */
function mapOperLog(raw: Raw): OperLogRow {
  const param = parseJson(raw.requestParam)
  const params = param && Array.isArray(param.params) ? param.params : undefined
  const resultRaw = param ? param.result : undefined
  return {
    id: raw.id as OperLogRow['id'],
    operName: str(raw.operName),
    module: str(raw.title),
    action: [str(raw.requestMethod), str(raw.requestUri)].filter(Boolean).join(' ') || str(raw.businessType),
    bizType: str(raw.businessType),
    bizId: (param?.bizId as OperLogRow['bizId']) ?? undefined,
    status: Number(raw.status) === 0 ? 1 : 0,
    errorMsg: str(raw.errorMsg),
    params: params ? JSON.stringify(params, null, 2) : str(raw.requestParam),
    result: pretty(resultRaw),
    traceId: str(raw.traceId),
    duration: num(raw.costMs ?? raw.duration),
    ip: str(raw.operIp),
    createTime: formatTime(raw.operTime ?? raw.createTime)
  }
}

function mapLoginLog(raw: Raw): LoginLogRow {
  return {
    id: raw.id as LoginLogRow['id'],
    username: str(raw.username),
    ip: str(raw.ip),
    status: Number(raw.status) === 1 ? 1 : 0,
    message: str(raw.msg) || str(raw.message),
    userAgent: str(raw.userAgent),
    loginTime: formatTime(raw.loginTime ?? raw.createTime)
  }
}

function mapChangeLog(raw: Raw): ChangeLogRow {
  const param = parseJson(raw.requestParam)
  return {
    id: raw.id as ChangeLogRow['id'],
    operName: str(raw.operName),
    bizType: str(raw.businessType),
    bizId: (param?.bizId as ChangeLogRow['bizId']) ?? undefined,
    before: pretty(param?.before),
    after: pretty(param?.after),
    traceId: str(raw.traceId),
    createTime: formatTime(raw.operTime ?? raw.createTime)
  }
}

export const logApi = {
  oper: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/logs/oper', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapOperLog(item as Raw)) }
    }),
  login: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/logs/login', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapLoginLog(item as Raw)) }
    }),
  changes: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/logs/changes', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapChangeLog(item as Raw)) }
    }),
  trace: (traceId: string) => http<unknown>({ url: `/logs/trace/${encodeURIComponent(traceId)}`, method: 'get' })
}

/* ------------------------------ 在线会话 ------------------------------ */
function mapOnline(raw: Raw): OnlineSession {
  return {
    jti: String(raw.jti ?? ''),
    username: str(raw.username) || '',
    ip: str(raw.ip),
    loginTime: formatTime(raw.loginTime),
    lastActive: formatTime(raw.lastActiveTime ?? raw.lastActive)
  }
}

export const onlineApi = {
  list: (params: Record<string, unknown>) =>
    http<Raw[] | PageResult<Raw>>({ url: '/auth/online', method: 'get', params }).then((data) => {
      const page = toPage<Raw>(data)
      return { ...page, list: page.list.map((item) => mapOnline(item as Raw)) }
    }),
  kick: (jti: string) => http({ url: `/auth/online/${encodeURIComponent(jti)}`, method: 'delete' })
}

/* ------------------------------ 附件 ------------------------------ */
function mapUpload(raw: Raw): UploadResult {
  return {
    id: (raw.id as UploadResult['id']) ?? (raw.fileId as UploadResult['id']),
    originalName: str(raw.originalName) || str(raw.fileName) || '',
    size: Number(raw.size ?? 0),
    suffix: str(raw.suffix),
    contentType: str(raw.contentType)
  }
}

export function uploadFile(
  file: File,
  onProgress?: (percent: number) => void
): Promise<UploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  return http<Raw>({
    url: '/files',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded * 100) / event.total))
    }
  }).then((data) => mapUpload((data || {}) as Raw))
}

/* ------------------------------ 个人中心 ------------------------------ */
export const accountApi = {
  changePassword: (oldPassword: string, newPassword: string) =>
    http({ url: '/auth/password', method: 'put', data: { oldPassword, newPassword } }),
  updateProfile: (data: { realName?: string; mobile?: string; email?: string }) =>
    http({ url: '/users/me', method: 'put', data })
}
