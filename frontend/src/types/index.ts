/**
 * 与后端契约 docs/03 对齐的基础类型（M1）。
 * 统一响应：{ code, message, data, traceId }，code = 0 表示成功。
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface UserInfo {
  id: number | string
  username: string
  realName: string
  deptId?: number | string | null
  deptName?: string | null
  mobile?: string
  email?: string
  postName?: string
  lastLoginTime?: string
}

/**
 * 菜单项：type 约定 0=目录、1=菜单、2=按钮（兼容后端返回字符串枚举）。
 * 目录可无 path，按钮不参与侧栏与路由注册。
 */
export interface MenuItem {
  id: number | string
  parentId?: number | string | null
  name: string
  path: string
  icon?: string
  type?: number | string
  /** 0=隐藏（后端菜单 visible 字段），隐藏项不参与侧栏与路由注册 */
  visible?: number | string
  children?: MenuItem[]
}

export interface LoginPayload {
  username: string
  password: string
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
}

export interface TagView {
  path: string
  title: string
  affix?: boolean
}
