import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'
import { clearAuth, getToken } from '@/utils/auth'

/**
 * 全局 axios 实例：
 * - baseURL 来自 VITE_API_BASE（默认 /api/v1，开发环境经 Vite 代理转发到 8080）
 * - 请求拦截注入 Authorization: Bearer <token>
 * - 响应拦截统一解包 { code, message, data, traceId }，code=0 返回 data
 * - HTTP 401 清理登录态跳登录，403 跳无权限页
 */

let redirecting = false

function goLogin(): void {
  if (redirecting || window.location.pathname === '/login') return
  redirecting = true
  clearAuth()
  window.location.replace(`/login?redirect=${encodeURIComponent(window.location.pathname)}`)
}

function goForbidden(): void {
  if (redirecting || window.location.pathname === '/403') return
  redirecting = true
  window.location.replace('/403')
}

/**
 * 登录/退出接口自身返回 401 属于业务校验结果（如 40101 用户名或密码错误），
 * 不应触发「登录已失效」的全局清 token + 跳转逻辑。
 */
function isAuthEndpoint(error: AxiosError): boolean {
  const url = error.config?.url || ''
  return url.indexOf('/auth/login') >= 0 || url.indexOf('/auth/logout') >= 0
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

/**
 * 判断「后端服务不可达」：
 * - 请求没有拿到任何响应（网络错误、代理无法连接）
 * - 开发环境 Vite 代理在后端未启动时会返回 500 + 空响应体
 * 超时不视为不可达，避免把后端繁忙误判为演示模式。
 */
function isBackendUnavailable(error: AxiosError): boolean {
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return false
  const response = error.response
  if (!response) return true
  if (response.status >= 500) {
    const data = response.data
    return (
      data === undefined ||
      data === null ||
      (typeof data === 'string' && data.trim() === '')
    )
  }
  return false
}

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown> | undefined

    // 文件下载等非 JSON 响应直接透传
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response
    }
    if (!body || typeof body !== 'object' || typeof body.code !== 'number') {
      return response.data
    }
    if (body.code === 0) {
      return body.data
    }

    const message = body.message || '请求处理失败'
    ElMessage.error(message)
    const error = new Error(message) as Error & { code?: number; traceId?: string }
    error.code = body.code
    error.traceId = body.traceId
    return Promise.reject(error)
  },
  (error: AxiosError) => {
    const status = error.response?.status
    const unavailable = isBackendUnavailable(error)
    if (unavailable) {
      ;(error as AxiosError & { backendUnavailable?: boolean }).backendUnavailable = true
    }
    if (status === 401 && isAuthEndpoint(error)) {
      // 登录接口的 401 透传后端 message（如 40101 用户名或密码错误）
      const body = error.response?.data as
        | { code?: number; message?: string; traceId?: string }
        | string
        | undefined
      const payload = typeof body === 'string' ? undefined : body
      const message = payload?.message || '用户名或密码错误'
      ElMessage.error(message)
      const bizError = new Error(message) as Error & { code?: number; traceId?: string }
      bizError.code = payload?.code
      bizError.traceId = payload?.traceId
      ;(bizError as Error & { backendUnavailable?: boolean }).backendUnavailable = false
      return Promise.reject(bizError)
    }
    if (status === 401) {
      ElMessage.error('登录已失效，请重新登录')
      goLogin()
    } else if (status === 403) {
      ElMessage.error('无权访问该资源')
      goForbidden()
    } else if (status === 404) {
      ElMessage.error('请求的接口不存在')
    } else if (unavailable) {
      // 后端未启动/代理不可达：由调用方决定是否进入演示模式，这里不弹窗
    } else if (status && status >= 500) {
      ElMessage.error('服务暂不可用，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error((error.response?.data as { message?: string } | undefined)?.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

/** 类型化请求：返回解包后的 data */
export async function http<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return request(config) as unknown as Promise<T>
}

export default request
