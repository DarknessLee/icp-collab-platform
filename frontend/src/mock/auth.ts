import type { LoginPayload, LoginResult } from '@/types'
import { getFallbackMenus } from './menus'
import { ROLE_PERMISSIONS } from './roles'
import type { RoleName } from '@/utils/roles'

interface MockAccount {
  username: string
  password: string
  realName: string
  deptName: string
  deptId: number
  role: RoleName
}

/**
 * 演示账号：仅在后端不可达（网络错误）且 VITE_ENABLE_MOCK=true 时生效。
 * 真实环境请使用 docs/05 权限矩阵中的种子账号。
 */
export const MOCK_ACCOUNTS: MockAccount[] = [
  { username: 'admin', password: 'Admin@123', realName: '张明', deptName: '信息技术部', deptId: 1, role: '系统管理员' },
  { username: 'internal', password: 'Internal@123', realName: '李静', deptName: '内控与审计部', deptId: 2, role: '内控管理员' },
  { username: 'deptlead', password: 'Dept@123', realName: '王强', deptName: '财务部', deptId: 3, role: '部门负责人' },
  { username: 'employee', password: 'User@123', realName: '赵敏', deptName: '综合管理部', deptId: 4, role: '普通员工' },
  { username: 'auditor', password: 'Audit@123', realName: '陈涛', deptName: '审计部', deptId: 5, role: '审计员' },
  { username: 'manager', password: 'Manager@123', realName: '刘洋', deptName: '总经理办公室', deptId: 6, role: '管理层' }
]

/** 登录页展示的演示账号（只展示两个常用角色，避免喧宾夺主） */
export const DEMO_ACCOUNT_HINTS = [
  { username: 'admin', password: 'Admin@123', role: '系统管理员' },
  { username: 'employee', password: 'User@123', role: '普通员工' }
]

/** 校验演示账号并生成与后端一致结构的登录结果；失败返回 null */
export function mockLogin(payload: LoginPayload): LoginResult | null {
  const username = String(payload.username || '').trim()
  const account = MOCK_ACCOUNTS.find(
    (item) => item.username === username && item.password === payload.password
  )
  if (!account) return null

  const roles: string[] = [account.role]
  const timestamp = Date.now()
  return {
    accessToken: `mock-access-token-${account.username}-${timestamp}`,
    refreshToken: `mock-refresh-token-${account.username}-${timestamp}`,
    expiresIn: 7200,
    user: {
      id: MOCK_ACCOUNTS.indexOf(account) + 1,
      username: account.username,
      realName: account.realName,
      deptId: account.deptId,
      deptName: account.deptName
    },
    roles,
    permissions: ROLE_PERMISSIONS[account.role] || [],
    menus: getFallbackMenus(roles)
  }
}
