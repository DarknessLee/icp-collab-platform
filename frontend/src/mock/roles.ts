import type { RoleName } from '@/utils/roles'

/**
 * 内置菜单路径（按角色）。
 * 仅在后端登录响应未返回菜单树时作为兜底使用；正式权限以 docs/05 权限矩阵与后端返回为准。
 */

const SYSTEM_PATHS = [
  '/system/dept',
  '/system/post',
  '/system/user',
  '/system/role',
  '/system/menu',
  '/system/dict',
  '/system/config',
  '/system/log'
]
const ICP_PATHS = [
  '/icp/policy',
  '/icp/risk',
  '/icp/control',
  '/icp/matrix',
  '/icp/evaluation',
  '/icp/defect',
  '/icp/rectify'
]
const COLL_PATHS = [
  '/coll/task',
  '/coll/doc',
  '/coll/doc-borrow',
  '/coll/notice',
  '/coll/schedule',
  '/coll/message'
]
const WF_PATHS = [
  '/wf/form',
  '/wf/definition',
  '/wf/instance',
  '/wf/todo',
  '/wf/done',
  '/wf/purchase',
  '/wf/seal',
  '/wf/urge'
]
const REPORT_PATHS = ['/report/overview', '/report/policy', '/report/defect', '/report/task']
const DASHBOARD_PATHS = ['/dashboard/index', '/dashboard/workbench', '/dashboard/todo']

export const ROLE_MENU_PATHS: Record<RoleName, string[]> = {
  系统管理员: [...SYSTEM_PATHS, ...ICP_PATHS, ...COLL_PATHS, ...WF_PATHS, ...REPORT_PATHS, ...DASHBOARD_PATHS],
  内控管理员: [...ICP_PATHS, ...COLL_PATHS, ...WF_PATHS, ...REPORT_PATHS, ...DASHBOARD_PATHS],
  部门负责人: [...ICP_PATHS, ...COLL_PATHS, ...REPORT_PATHS, ...DASHBOARD_PATHS],
  普通员工: ['/icp/policy', '/icp/defect', ...COLL_PATHS, ...DASHBOARD_PATHS],
  审计员: [...ICP_PATHS, ...REPORT_PATHS, '/system/log', ...DASHBOARD_PATHS],
  管理层: [...ICP_PATHS, ...COLL_PATHS, ...REPORT_PATHS, ...DASHBOARD_PATHS]
}
