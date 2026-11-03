import type { RoleName } from '@/utils/roles'

/**
 * 演示角色定义（仅用于后端未启动时的前端 Mock 登录，不参与真实鉴权）。
 * 正式权限以 docs/05 权限矩阵与后端返回为准。
 */

const ICP_MODULES = ['policy', 'risk', 'matrix', 'evaluation', 'defect']
const ICP_ALL_ACTIONS = ICP_MODULES.flatMap((module) => [
  `icp:${module}:list`,
  `icp:${module}:add`,
  `icp:${module}:edit`,
  `icp:${module}:export`
])

/** 演示菜单范围（按模块路径，与后端菜单树路径保持一致） */
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

/** 演示按钮权限（v-perm 指令使用；'*' 表示全部权限） */
export const ROLE_PERMISSIONS: Record<RoleName, string[]> = {
  系统管理员: ['*'],
  内控管理员: [
    ...ICP_ALL_ACTIONS,
    'workflow:approval:list',
    'workflow:approval:approve',
    'workflow:approval:reject',
    'workflow:approval:urge',
    'report:dashboard:export',
    'report:warning:handle',
    'collab:task:add',
    'collab:document:upload'
  ],
  部门负责人: [
    'icp:evaluation:list',
    'icp:evaluation:add',
    'icp:evaluation:export',
    'icp:defect:list',
    'icp:defect:add',
    'icp:defect:edit',
    'workflow:approval:approve',
    'workflow:approval:reject',
    'workflow:approval:urge',
    'report:dashboard:export',
    'report:warning:handle',
    'collab:task:add',
    'collab:document:upload'
  ],
  普通员工: [
    'collab:task:add',
    'collab:document:upload',
    'collab:schedule:add',
    'collab:notice:read'
  ],
  审计员: ['system:audit-log:export', 'system:login-log:export', 'report:dashboard:export'],
  管理层: ['report:dashboard:export']
}
