import { http } from './request'
import type { LoginPayload, LoginResult } from '@/types'

/** 登录：POST /api/v1/auth/login，响应 data 结构见 docs/03 */
export function loginApi(payload: LoginPayload): Promise<LoginResult> {
  return http<LoginResult>({
    url: '/auth/login',
    method: 'post',
    data: payload
  })
}

/** 当前登录用户：GET /api/v1/auth/me，data 结构与登录一致（供 M2 刷新用户/菜单使用） */
export function getMeApi(): Promise<LoginResult> {
  return http<LoginResult>({
    url: '/auth/me',
    method: 'get'
  })
}

/** 退出登录：POST /api/v1/auth/logout（失败不阻塞前端清理） */
export function logoutApi(): Promise<unknown> {
  return http<unknown>({
    url: '/auth/logout',
    method: 'post'
  })
}
