import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import type { Envelope } from '@/types/api'
import { useAuthStore } from '@/stores/auth'

/** 统一业务错误：信封 code !== 'OK'，或框架层 HTTP 错误归一化后的形态 */
export class ApiError extends Error {
  code: string
  category?: string
  retriable?: boolean
  traceId?: string

  constructor(code: string, message: string, opts: { category?: string; retriable?: boolean; traceId?: string } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.category = opts.category
    this.retriable = opts.retriable
    this.traceId = opts.traceId
  }
}

export const instance = axios.create({
  // 相对路径，经 Vite proxy 转发到 localhost:8088（后端无 CORS，禁止硬编码地址）
  baseURL: '',
  timeout: 15000,
})

instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const auth = useAuthStore()
  const mode = config.authMode ?? 'admin'
  if (mode === 'admin' && auth.adminToken) {
    config.headers.set('Authorization', `Bearer ${auth.adminToken}`)
  } else if (mode === 'agent') {
    const agentId = config.agentIdOverride ?? auth.agentId
    if (agentId) config.headers.set('Authorization', `Bearer agent_${agentId}`)
  }
  return config
})

instance.interceptors.response.use(
  (resp: AxiosResponse) => {
    // CSV 等二进制直通（export.csv 非 JSON 信封），完整 AxiosResponse 返回
    if (resp.config.responseType === 'blob') return resp
    const body = resp.data as Envelope
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 'OK') return body.data as never // 解包：调用方直接拿 data
      throw new ApiError(body.code, body.message ?? `业务错误 ${body.code}`, {
        category: body.category,
        retriable: body.retriable,
        traceId: body.traceId,
      })
    }
    // /actuator/health 等非信封 JSON 原样返回
    return body as never
  },
  (error: AxiosError<Envelope & { status?: number; error?: string; message?: string }>) => {
    // 框架层错误：真实非 200（404/400/500）或网络断连
    if (error.response) {
      const b = error.response.data
      if (b && typeof b === 'object' && 'code' in b && b.code) {
        // 极少数路径异常可能仍带信封
        return Promise.reject(new ApiError(b.code, b.message ?? `HTTP ${error.response.status}`, {
          category: b.category,
          retriable: b.retriable,
          traceId: b.traceId,
        }))
      }
      return Promise.reject(
        new ApiError(`HTTP_${error.response.status}`, (b && b.message) || `HTTP ${error.response.status} ${error.response.statusText || ''}`.trim()),
      )
    }
    return Promise.reject(new ApiError('NETWORK_ERROR', '无法连接后端（请确认 gateway 已启动，localhost:8088）'))
  },
)

/**
 * 类型化帮助函数：拦截器已把信封解包为 data，
 * 这里的泛型直接标注解包后的业务类型（axios 默认类型不会感知拦截器改写）。
 */
async function unwrap<T>(p: Promise<AxiosResponse<T>>): Promise<T> {
  return (await p) as unknown as T
}

export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) => unwrap<T>(instance.get<T>(url, config)),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(instance.post<T>(url, body, config)),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) => unwrap<T>(instance.put<T>(url, body, config)),
  delete: <T>(url: string, config?: AxiosRequestConfig) => unwrap<T>(instance.delete<T>(url, config)),
}

export default http
