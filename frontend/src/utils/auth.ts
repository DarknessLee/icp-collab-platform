import type { MenuItem, UserInfo } from '@/types'

const ACCESS_TOKEN_KEY = 'icp_access_token'
const REFRESH_TOKEN_KEY = 'icp_refresh_token'
const USER_KEY = 'icp_user'
const ROLES_KEY = 'icp_roles'
const PERMISSIONS_KEY = 'icp_permissions'
const MENUS_KEY = 'icp_menus'
const MOCK_MODE_KEY = 'icp_mock_mode'

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return fallback
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

export interface StoredAuth {
  token: string
  refreshToken: string
  user: UserInfo | null
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
  mockMode: boolean
}

export function getToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function loadAuth(): StoredAuth {
  return {
    token: getToken(),
    refreshToken: getRefreshToken(),
    user: readJson<UserInfo | null>(USER_KEY, null),
    roles: readJson<string[]>(ROLES_KEY, []),
    permissions: readJson<string[]>(PERMISSIONS_KEY, []),
    menus: readJson<MenuItem[]>(MENUS_KEY, []),
    mockMode: localStorage.getItem(MOCK_MODE_KEY) === '1'
  }
}

export interface AuthPayload {
  accessToken: string
  refreshToken: string
  user: UserInfo
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
}

export function saveAuth(payload: AuthPayload, mockMode: boolean): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, payload.refreshToken || '')
  localStorage.setItem(USER_KEY, JSON.stringify(payload.user))
  localStorage.setItem(ROLES_KEY, JSON.stringify(payload.roles || []))
  localStorage.setItem(PERMISSIONS_KEY, JSON.stringify(payload.permissions || []))
  localStorage.setItem(MENUS_KEY, JSON.stringify(payload.menus || []))
  localStorage.setItem(MOCK_MODE_KEY, mockMode ? '1' : '0')
}

export function clearAuth(): void {
  ;[
    ACCESS_TOKEN_KEY,
    REFRESH_TOKEN_KEY,
    USER_KEY,
    ROLES_KEY,
    PERMISSIONS_KEY,
    MENUS_KEY,
    MOCK_MODE_KEY
  ].forEach((key) => localStorage.removeItem(key))
}
