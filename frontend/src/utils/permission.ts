/**
 * 按钮权限判断：支持 'system:user:add'、'system:user:*'、'*' 通配。
 */
export function hasPermission(
  permissions: string[],
  required?: string | string[]
): boolean {
  if (!required) return true
  const requiredList = (Array.isArray(required) ? required : [required]).filter(Boolean)
  if (requiredList.length === 0) return true
  if (!permissions || permissions.length === 0) return false
  if (permissions.includes('*')) return true

  return requiredList.every((item) => {
    if (permissions.includes(item)) return true
    const prefix = item.slice(0, item.lastIndexOf(':'))
    if (!prefix) return false
    return permissions.includes(`${prefix}:*`)
  })
}

/** 角色判断（保留给后续页面复用） */
export function hasRole(roles: string[], required?: string | string[]): boolean {
  if (!required) return true
  const list = (Array.isArray(required) ? required : [required]).filter(Boolean)
  return list.some((role) => roles.includes(role))
}
