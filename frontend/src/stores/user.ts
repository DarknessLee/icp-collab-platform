import { defineStore } from 'pinia'
import { loginApi, logoutApi } from '@/api/auth'
import { getFallbackMenus } from '@/mock/menus'
import type { LoginPayload, LoginResult, MenuItem, UserInfo } from '@/types'
import { clearAuth, loadAuth, saveAuth } from '@/utils/auth'
import { buildMenuTree, collectMenuPaths, filterVisibleMenus, normalizePath } from '@/utils/menu'
import { hasPermission, hasRole } from '@/utils/permission'
import { displayRoleName } from '@/utils/roles'

interface UserState {
  token: string
  refreshToken: string
  user: UserInfo | null
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
}

function menuSignature(menus: MenuItem[]): string {
  return flattenPaths(menus).sort().join('|')
}

function flattenPaths(menus: MenuItem[]): string[] {
  const list: string[] = []
  const walk = (nodes: MenuItem[]) => {
    nodes.forEach((node) => {
      const path = normalizePath(node.path)
      if (path) list.push(path)
      if (node.children && node.children.length) walk(node.children)
    })
  }
  walk(menus)
  return list
}

export const useUserStore = defineStore('user', {
  state: (): UserState => {
    const stored = loadAuth()
    return {
      token: stored.token,
      refreshToken: stored.refreshToken,
      user: stored.user,
      roles: stored.roles,
      permissions: stored.permissions,
      menus: stored.menus
    }
  },

  getters: {
    isLoggedIn: (state): boolean => Boolean(state.token),
    displayName: (state): string => state.user?.realName || state.user?.username || '未登录',
    primaryRole: (state): string => displayRoleName(state.roles[0] || ''),
    menuTree: (state): MenuItem[] => filterVisibleMenus(buildMenuTree(state.menus)),
    menuPaths: (state): Set<string> => collectMenuPaths(buildMenuTree(state.menus))
  },

  actions: {
    async login(payload: LoginPayload): Promise<void> {
      const result: LoginResult = await loginApi(payload)

      if (!result || !result.accessToken) {
        throw new Error('登录响应缺少访问令牌')
      }

      this.token = result.accessToken
      this.refreshToken = result.refreshToken || ''
      this.user = result.user || null
      this.roles = Array.isArray(result.roles) ? result.roles : []
      this.permissions = Array.isArray(result.permissions) ? result.permissions : []

      const backendMenus = Array.isArray(result.menus) ? result.menus : []
      // 菜单为空时按角色使用内置兜底菜单，避免登录后白屏
      this.menus = backendMenus.length > 0 ? backendMenus : getFallbackMenus(this.roles)

      saveAuth(
        {
          accessToken: this.token,
          refreshToken: this.refreshToken,
          user: this.user as UserInfo,
          roles: this.roles,
          permissions: this.permissions,
          menus: this.menus
        }
      )
    },

    hasPermission(required?: string | string[]): boolean {
      return hasPermission(this.permissions, required)
    },

    hasRole(required?: string | string[]): boolean {
      return hasRole(this.roles, required)
    },

    /** 菜单是否发生变化（登录态变化时用于重算动态路由） */
    menusDiffer(menus: MenuItem[]): boolean {
      return menuSignature(menus) !== menuSignature(this.menuTree)
    },

    /** 个人中心更新资料后同步本地用户信息 */
    applyUserProfile(user: UserInfo): void {
      this.user = user
      saveAuth(
        {
          accessToken: this.token,
          refreshToken: this.refreshToken,
          user,
          roles: this.roles,
          permissions: this.permissions,
          menus: this.menus
        }
      )
    },

    async logout(): Promise<void> {
      try {
        await logoutApi()
      } catch {
        // 后端退出接口失败不阻塞前端退出
      }
      this.reset()
    },

    reset(): void {
      this.token = ''
      this.refreshToken = ''
      this.user = null
      this.roles = []
      this.permissions = []
      this.menus = []
      clearAuth()
    }
  }
})
