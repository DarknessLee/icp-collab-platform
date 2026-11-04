import type { MenuItem } from '@/types'

/** 统一路径格式：保证以 / 开头、去掉末尾 /（根路径除外） */
export function normalizePath(path?: string | null): string {
  if (!path) return ''
  let value = String(path).trim()
  if (!value) return ''
  if (!value.startsWith('/')) value = `/${value}`
  if (value.length > 1) value = value.replace(/\/+$/, '')
  return value
}

/** type 约定：0=目录、1=菜单、2=按钮（兼容字符串枚举） */
export function isButtonMenu(item: MenuItem): boolean {
  const type = item.type
  if (type === 2 || type === '2') return true
  const text = String(type ?? '').toUpperCase()
  return text === 'BUTTON' || text === 'F' || text === 'BTN'
}

/**
 * 种子中 visible=0 的隐藏菜单兜底：登录响应目前未透出 visible 字段，
 * 按契约路径兜底隐藏（40=表单管理 /wf/form，47=催办管理 /wf/urge）。
 */
const HIDDEN_MENU_PATHS = new Set(['/wf/form', '/wf/urge'])

function isDirectoryType(type: MenuItem['type']): boolean {
  const text = String(type ?? '').toUpperCase()
  return type === 0 || type === '0' || text === 'DIR' || text === 'M' || text === 'DIRECTORY'
}

/** 把后端可能返回的「扁平 parentId 列表」或「嵌套树」统一整理为树 */
export function buildMenuTree(list: MenuItem[]): MenuItem[] {
  if (!Array.isArray(list) || list.length === 0) return []
  const hasNested = list.some((item) => Array.isArray(item.children) && item.children.length > 0)
  if (hasNested) {
    // 后端菜单为嵌套树（真实响应），此处统一把 path 归一化为以 / 开头
    return list.map((item) => ({
      ...item,
      path: normalizePath(item.path),
      children: buildMenuTree(item.children || [])
    }))
  }

  const nodes: MenuItem[] = list.map((item) => ({
    ...item,
    path: normalizePath(item.path),
    children: []
  }))
  const nodeMap = new Map<string, MenuItem>()
  nodes.forEach((node) => nodeMap.set(String(node.id), node))

  const roots: MenuItem[] = []
  nodes.forEach((node) => {
    const parentId = node.parentId
    const isRoot =
      parentId === null ||
      parentId === undefined ||
      parentId === '' ||
      String(parentId) === '0'
    if (isRoot) {
      roots.push(node)
      return
    }
    const parent = nodeMap.get(String(parentId))
    if (parent) {
      if (!parent.children) parent.children = []
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

/** 过滤按钮、隐藏菜单（visible=0）与空目录，得到侧栏可渲染的菜单树 */
export function filterVisibleMenus(tree: MenuItem[]): MenuItem[] {
  const result: MenuItem[] = []
  tree.forEach((item) => {
    if (isButtonMenu(item)) return
    if (item.visible === 0 || item.visible === '0') return
    if (HIDDEN_MENU_PATHS.has(normalizePath(item.path))) return
    const children = filterVisibleMenus(item.children || [])
    if (!normalizePath(item.path) && children.length === 0) return
    result.push({ ...item, children })
  })
  return result
}

/** 收集菜单中可访问的路径（用于路由守卫权限判断） */
export function collectMenuPaths(tree: MenuItem[]): Set<string> {
  const paths = new Set<string>()
  const walk = (nodes: MenuItem[]) => {
    nodes.forEach((node) => {
      if (isButtonMenu(node)) return
      const path = normalizePath(node.path)
      if (path) paths.add(path)
      if (node.children && node.children.length) walk(node.children)
    })
  }
  walk(tree)
  return paths
}

export interface MenuLeaf {
  menu: MenuItem
  group?: string
}

/** 提取所有可注册路由的叶子菜单（目录节点不单独注册页面） */
export function flattenMenuLeaves(tree: MenuItem[], parentName?: string): MenuLeaf[] {
  const leaves: MenuLeaf[] = []
  tree.forEach((item) => {
    if (isButtonMenu(item)) return
    const children = (item.children || []).filter((child) => !isButtonMenu(child))
    if (children.length > 0) {
      leaves.push(...flattenMenuLeaves(children, item.name))
      return
    }
    const path = normalizePath(item.path)
    if (!path) return
    leaves.push({ menu: item, group: parentName })
  })
  return leaves
}

/** 根据菜单路径推导按钮权限前缀，例如 /system/user -> system:user */
export function permPrefixFromPath(path: string): string {
  return normalizePath(path)
    .split('/')
    .filter(Boolean)
    .join(':')
}

/** 目录判定（导出给侧栏渲染使用） */
export function isDirectoryMenu(item: MenuItem): boolean {
  if (item.children && item.children.length > 0) return true
  return isDirectoryType(item.type)
}

/** 查找菜单路径的父级链（用于面包屑） */
export function findMenuChain(tree: MenuItem[], path: string): MenuItem[] {
  const target = normalizePath(path)
  if (!target) return []
  for (const item of tree) {
    if (normalizePath(item.path) === target) return [item]
    if (item.children && item.children.length > 0) {
      const chain = findMenuChain(item.children, target)
      if (chain.length > 0) return [item, ...chain]
    }
  }
  return []
}
