import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { KNOWN_MENU_PATHS } from '@/config/modules'
import { normalizePath } from '@/utils/menu'
import type { MenuItem } from '@/types'

const Layout = () => import('@/layout/index.vue')

/** 静态路由：M1 只静态注册工作台，其余模块路由按后端菜单动态注册 */
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
      }
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
