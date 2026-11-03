/**
 * 角色代码与中文名归一化（兼容后端 ROLE_XXX / 下划线代码 / 中文名）。
 */
export const ROLE_NAMES = [
  '系统管理员',
  '内控管理员',
  '部门负责人',
  '普通员工',
  '审计员',
  '管理层'
] as const

export type RoleName = (typeof ROLE_NAMES)[number]

const ROLE_ALIASES: Record<RoleName, string[]> = {
  系统管理员: ['admin', 'sysadmin', 'system_admin', 'super_admin', 'sys_admin'],
  内控管理员: ['icp_admin', 'icp_manager', 'internal', 'internal_admin', 'internal_control_admin'],
  部门负责人: ['dept_leader', 'dept_manager', 'dept_head', 'leader'],
  普通员工: ['employee', 'staff', 'user', 'general_user'],
  审计员: ['auditor', 'audit', 'audit_admin'],
  管理层: ['manager', 'management', 'executive', 'management_viewer']
}

/** 后端角色代码 -> 中文角色名；无法识别时返回 null */
export function resolveRoleName(role: string): RoleName | null {
  const raw = String(role || '').trim()
  if (!raw) return null
  if ((ROLE_NAMES as readonly string[]).includes(raw)) return raw as RoleName
  const token = raw.toLowerCase().replace(/^role[:_-]/, '').trim()
  for (const name of ROLE_NAMES) {
    if (ROLE_ALIASES[name].includes(token)) return name
  }
  return null
}

/** 展示用角色名：识别不了时回退原始代码 */
export function displayRoleName(role: string): string {
  return resolveRoleName(role) || role || '未分配角色'
}
