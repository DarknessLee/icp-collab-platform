import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { KNOWN_MENU_PATHS } from '@/config/modules'
import { normalizePath } from '@/utils/menu'
import type { MenuItem } from '@/types'

const Layout = () => import('@/layout/index.vue')

/**
 * M2a 系统管理与个人中心页面：静态注册（登录可见，按 meta.perm / meta.permAny 做权限拦截），
 * 侧栏仍由后端 menus 渲染；动态菜单注册遇到这些路径时跳过，避免重复路由。
 */
const staticPageRoutes: RouteRecordRaw[] = [
  {
    path: 'profile',
    name: 'Profile',
    component: () => import('@/views/profile/index.vue'),
    meta: { title: '个人中心', hidden: true }
  },
  {
    path: 'system/dept',
    name: 'SystemDept',
    component: () => import('@/views/system/dept/index.vue'),
    meta: { title: '部门管理', perm: 'system:dept:list' }
  },
  {
    path: 'system/post',
    name: 'SystemPost',
    component: () => import('@/views/system/post/index.vue'),
    meta: { title: '岗位管理', perm: 'system:post:list' }
  },
  {
    path: 'system/user',
    name: 'SystemUser',
    component: () => import('@/views/system/user/index.vue'),
    meta: { title: '用户管理', perm: 'system:user:list' }
  },
  {
    path: 'system/role',
    name: 'SystemRole',
    component: () => import('@/views/system/role/index.vue'),
    meta: { title: '角色管理', perm: 'system:role:list' }
  },
  {
    path: 'system/menu',
    name: 'SystemMenu',
    component: () => import('@/views/system/menu/index.vue'),
    meta: { title: '菜单管理', perm: 'system:menu:list' }
  },
  {
    path: 'system/dict',
    name: 'SystemDict',
    component: () => import('@/views/system/dict/index.vue'),
    meta: { title: '字典管理', perm: 'system:dict:list' }
  },
  {
    path: 'system/config',
    name: 'SystemConfig',
    component: () => import('@/views/system/config/index.vue'),
    meta: { title: '参数管理', perm: 'system:config:list' }
  },
  {
    path: 'system/log',
    name: 'SystemLog',
    component: () => import('@/views/system/log/index.vue'),
    meta: { title: '日志审计', permAny: ['system:log:oper', 'audit:log:view'] }
  },
  {
    path: 'system/job',
    name: 'SystemJob',
    component: () => import('@/views/system/job/index.vue'),
    meta: { title: '定时任务', perm: 'system:job:list' }
  },
  {
    path: 'icp/policy',
    name: 'IcpPolicy',
    component: () => import('@/views/icp/policy/index.vue'),
    meta: { title: '制度库', perm: 'icp:policy:list' }
  },
  {
    path: 'icp/risk',
    name: 'IcpRisk',
    component: () => import('@/views/icp/risk/index.vue'),
    meta: { title: '风险库', perm: 'icp:risk:list' }
  },
  {
    path: 'icp/control',
    name: 'IcpControl',
    component: () => import('@/views/icp/control/index.vue'),
    meta: { title: '控制点', perm: 'icp:control:list' }
  },
  {
    path: 'icp/matrix',
    name: 'IcpMatrix',
    component: () => import('@/views/icp/matrix/index.vue'),
    meta: { title: '控制矩阵', perm: 'icp:matrix:list' }
  },
  {
    path: 'icp/evaluation',
    name: 'IcpEvaluation',
    component: () => import('@/views/icp/evaluation/index.vue'),
    meta: { title: '评价计划', perm: 'icp:evaluation:list' }
  },
  {
    path: 'icp/defect',
    name: 'IcpDefect',
    component: () => import('@/views/icp/defect/index.vue'),
    meta: { title: '缺陷管理', perm: 'icp:defect:list' }
  },
  {
    path: 'icp/rectify',
    name: 'IcpRectify',
    component: () => import('@/views/icp/rectify/index.vue'),
    meta: { title: '整改管理', perm: 'icp:rectify:list' }
  },
  {
    path: 'wf/todo',
    name: 'WorkflowTodo',
    component: () => import('@/views/workflow/todo/index.vue'),
    meta: { title: '我的待办', perm: 'workflow:todo' }
  },
  {
    path: 'wf/done',
    name: 'WorkflowDone',
    component: () => import('@/views/workflow/done/index.vue'),
    meta: { title: '我的已办', perm: 'workflow:done' }
  },
  {
    path: 'wf/initiated',
    name: 'WorkflowInitiated',
    component: () => import('@/views/workflow/initiated/index.vue'),
    meta: { title: '我发起的', perm: 'workflow:initiated' }
  },
  {
    path: 'wf/monitor',
    name: 'WorkflowMonitor',
    component: () => import('@/views/workflow/monitor/index.vue'),
    meta: { title: '流程监控', perm: 'workflow:monitor:view' }
  },
  {
    path: 'wf/definition',
    name: 'WorkflowDefinition',
    component: () => import('@/views/workflow/processes/index.vue'),
    meta: { title: '流程定义', permAny: ['workflow:definition:list', 'workflow:form:list'] }
  },
  {
    path: 'wf/instance',
    name: 'WorkflowInstance',
    component: () => import('@/views/workflow/instance/index.vue'),
    meta: { title: '流程实例', permAny: ['workflow:instance:list', 'workflow:monitor:view'] }
  },
  {
    path: 'wf/purchase',
    name: 'WorkflowPurchase',
    component: () => import('@/views/workflow/purchase/index.vue'),
    meta: { title: '采购申请', permAny: ['workflow:purchase:list', 'workflow:purchase:add'] }
  },
  {
    path: 'wf/seal',
    name: 'WorkflowSeal',
    component: () => import('@/views/workflow/seal/index.vue'),
    meta: { title: '用印申请', permAny: ['workflow:seal:list', 'workflow:seal:add'] }
  },
  {
    path: 'wf/borrow',
    name: 'WorkflowBorrow',
    component: () => import('@/views/workflow/borrow/index.vue'),
    meta: { title: '文档借阅', permAny: ['workflow:borrow:list', 'workflow:borrow:add', 'coll:doc:borrow'] }
  },
  {
    path: 'coll/task',
    name: 'CollabTask',
    component: () => import('@/views/collab/task/index.vue'),
    meta: { title: '任务管理', permAny: ['collab:task:list', 'collab:task:get'] }
  },
  {
    path: 'coll/doc',
    name: 'CollabDoc',
    component: () => import('@/views/collab/doc/index.vue'),
    meta: { title: '文档中心', permAny: ['collab:doc:list', 'collab:doc:get'] }
  },
  {
    path: 'coll/doc-borrow',
    name: 'CollabDocBorrow',
    component: () => import('@/views/collab/doc/borrow.vue'),
    meta: { title: '文档借阅', permAny: ['collab:borrow:list', 'collab:borrow:get'] }
  },
  {
    path: 'coll/notice',
    name: 'CollabNotice',
    component: () => import('@/views/collab/notice/index.vue'),
    meta: { title: '公告通知', permAny: ['collab:notice:list', 'collab:notice:get'] }
  },
  {
    path: 'coll/schedule',
    name: 'CollabSchedule',
    component: () => import('@/views/collab/schedule/index.vue'),
    meta: { title: '日程会议', permAny: ['collab:schedule:list', 'collab:schedule:get'] }
  },
  {
    path: 'coll/message',
    name: 'CollabMessage',
    component: () => import('@/views/collab/message/index.vue'),
    meta: { title: '消息中心', permAny: ['collab:message:list', 'collab:message:get'] }
  },
  {
    path: 'report/overview',
    name: 'ReportOverview',
    component: () => import('@/views/report/overview.vue'),
    meta: { title: '内控概览', permAny: ['report:icp:view', 'report:dashboard:view'] }
  },
  {
    path: 'report/policy',
    name: 'ReportPolicy',
    component: () => import('@/views/report/policy.vue'),
    meta: { title: '制度与风险统计', permAny: ['report:risk:view'] }
  },
  {
    path: 'report/defect',
    name: 'ReportDefect',
    component: () => import('@/views/report/defect.vue'),
    meta: { title: '缺陷统计', permAny: ['report:rectify:view'] }
  },
  {
    path: 'report/task',
    name: 'ReportTask',
    component: () => import('@/views/report/task.vue'),
    meta: { title: '任务与流程统计', permAny: ['report:process:view'] }
  },
  {
    path: 'report/alert',
    name: 'ReportAlert',
    component: () => import('@/views/report/alert.vue'),
    meta: { title: '预警中心', permAny: ['report:alert:view'] }
  }
]

/**
 * 开发态自测页：仅 DEV 注册并挂到 Layout 下，生产构建时该分支被剔除。
 */
if (import.meta.env.DEV) {
  staticPageRoutes.push({
    path: 'dev/upload',
    name: 'DevUpload',
    component: () => import('@/views/dev/upload.vue'),
    meta: { title: '附件上传自测', hidden: true }
  })
}

/** 静态页面路径集合：动态菜单注册时跳过，避免同一路径注册两次 */
const staticPagePaths = new Set<string>(
  staticPageRoutes.map((route) => normalizePath(route.path))
)

/** 静态路由：工作台 + M2a 系统管理/个人中心，其余模块路由按后端菜单动态注册 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true, hidden: true }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权访问', public: true, hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true, hidden: true }
  },
  {
    path: '/',
    name: 'Root',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', affix: true }
      },
      ...staticPageRoutes
    ]
  }
]

/** 兜底路由：必须最后注册（动态路由匹配优先级更高）。注意不能标记 public，否则守卫无法做 403/404 分流 */
export const catchAllRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'CatchAll',
  component: () => import('@/views/error/404.vue'),
  meta: { title: '页面不存在', hidden: true }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [...constantRoutes, catchAllRoute],
  scrollBehavior: () => ({ top: 0 })
})

const APP_TITLE = '企业内控与协同运营管理平台'

/** 取菜单树中第一个可跳转的叶子路径（目录直接访问时重定向用） */
function firstLeafPath(menu: MenuItem): string {
  for (const child of menu.children || []) {
    if (child.children && child.children.length > 0) {
      const nested = firstLeafPath(child)
      if (nested) return nested
      continue
    }
    const path = normalizePath(child.path)
    if (path) return path
  }
  return ''
}

/** 依据菜单树动态注册占位路由（M1 骨架；M2 替换为真实页面组件） */
export function registerMenuRoutes(menus: MenuItem[]): number {
  let count = 0
  menus.forEach((menu) => {
    if (menu.children && menu.children.length > 0) {
      // 目录节点本身不可路由，注册为到第一个叶子菜单的重定向，避免直接输入目录地址时落到 404
      const dirPath = normalizePath(menu.path)
      const target = firstLeafPath(menu)
      if (dirPath && dirPath !== '/dashboard' && target && !router.hasRoute(`Menu:dir:${dirPath}`)) {
        router.addRoute('Root', {
          path: dirPath.replace(/^\//, ''),
          name: `Menu:dir:${dirPath}`,
          redirect: target,
          meta: { title: menu.name, menuPath: dirPath }
        })
      }
      count += registerMenuRoutes(menu.children)
      return
    }
    const path = normalizePath(menu.path)
    if (!path) return
    // M2a 页面已静态注册（含权限 meta），不再重复注册动态路由
    if (staticPagePaths.has(path)) return
    const routeName = `Menu:${path}`
    if (router.hasRoute(routeName)) return

    // 管理驾驶舱下的子菜单（后端路径 /dashboard/*）统一收敛到工作台，避免 M1 出现无意义占位页
    if (path === '/dashboard' || path.startsWith('/dashboard/')) {
      router.addRoute('Root', {
        path: path.replace(/^\//, ''),
        name: routeName,
        redirect: '/dashboard',
        meta: { title: menu.name, menuPath: path }
      })
      count += 1
      return
    }

    router.addRoute('Root', {
      path: path.replace(/^\//, ''),
      name: routeName,
      component: () => import('@/views/placeholder/index.vue'),
      meta: { title: menu.name, menuPath: path }
    })
    count += 1
  })
  return count
}

/** 移除已注册的动态菜单路由（退出登录/切换账号时调用） */
export function resetMenuRoutes(): void {
  router.getRoutes().forEach((route) => {
    const name = typeof route.name === 'string' ? route.name : ''
    if (name.startsWith('Menu:')) {
      router.removeRoute(route.name as string)
    }
  })
}

/** 已尝试补注册动态路由的路径（避免守卫重试死循环） */
const retriedPaths = new Set<string>()

router.beforeEach((to) => {
  const userStore = useUserStore()
  const loggedIn = userStore.isLoggedIn

  if (to.meta.public) {
    if (to.path === '/login' && loggedIn) {
      return { path: '/dashboard', replace: true }
    }
    return true
  }

  if (!loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath }, replace: true }
  }

  // 已登录但菜单缺失（刷新时本地缓存被清理等异常）时回登录重建
  if (!userStore.menus.length) {
    userStore.reset()
    return { path: '/login', replace: true }
  }

  // M2a：静态页面按 meta 权限拦截（无权限直接 403，覆盖直接输入地址场景）
  const requiredPerm = to.meta.perm as string | undefined
  if (requiredPerm && !userStore.hasPermission(requiredPerm)) {
    return { path: '/403', replace: true }
  }
  const requiredPermAny = to.meta.permAny as string[] | undefined
  if (requiredPermAny && !requiredPermAny.some((perm) => userStore.hasPermission(perm))) {
    return { path: '/403', replace: true }
  }

  if (to.name === 'CatchAll') {
    const targetPath = normalizePath(to.path)
    // 首屏直达动态菜单地址时，路由可能尚未注册完成：补注册后重试一次
    if (userStore.menuPaths.has(targetPath) && !retriedPaths.has(targetPath)) {
      retriedPaths.add(targetPath)
      registerMenuRoutes(userStore.menuTree)
      if (router.resolve(to.fullPath).name !== 'CatchAll') {
        return { path: to.fullPath, replace: true }
      }
    }
    // 平台已定义的模块但当前角色不在菜单内 -> 无权限；完全未知的地址 -> 404
    if (KNOWN_MENU_PATHS.has(targetPath) && !userStore.menuPaths.has(targetPath)) {
      return { path: '/403', replace: true }
    }
    return { path: '/404', replace: true }
  }

  return true
})

router.afterEach((to) => {
  const title = (to.meta.title as string | undefined) || ''
  document.title = title ? `${title} - ${APP_TITLE}` : APP_TITLE
})

export default router
