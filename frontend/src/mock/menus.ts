import type { MenuItem } from '@/types'
import { ALL_MENUS } from '@/config/modules'
import { normalizePath } from '@/utils/menu'
import { resolveRoleName, type RoleName } from '@/utils/roles'
import { ROLE_MENU_PATHS } from './roles'

function filterTreeByPaths(tree: MenuItem[], allowed: Set<string>): MenuItem[] {
  const result: MenuItem[] = []
  tree.forEach((item) => {
    const children = filterTreeByPaths(item.children || [], allowed)
    const path = normalizePath(item.path)
    if (children.length > 0) {
      result.push({ ...item, children })
      return
    }
    if (path && allowed.has(path)) {
      result.push({ ...item, children: [] })
    }
  })
  return result
}

/** 按角色返回内置兜底菜单；未匹配到角色时按普通员工处理 */
export function getFallbackMenus(roles: string[]): MenuItem[] {
  const matched = new Set<RoleName>()
  ;(roles || []).forEach((role) => {
    const name = resolveRoleName(role)
    if (name) matched.add(name)
  })
  if (matched.size === 0) matched.add('普通员工')

  const allowed = new Set<string>()
  matched.forEach((name) => {
    ROLE_MENU_PATHS[name].forEach((path) => allowed.add(normalizePath(path)))
  })
  return filterTreeByPaths(ALL_MENUS, allowed)
}
