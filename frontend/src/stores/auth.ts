import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import http, { ApiError } from '@/api/http'
import type { MetricsResponse } from '@/types/api'

const LS_ADMIN = 'allcli.adminToken'
const LS_AGENT = 'allcli.agentId'

export const useAuthStore = defineStore('auth', () => {
  const adminToken = ref<string>(localStorage.getItem(LS_ADMIN) ?? 'admin_m4-dev-admin')
  const agentId = ref<string>(localStorage.getItem(LS_AGENT) ?? 'alice')

  watch(adminToken, (v) => localStorage.setItem(LS_ADMIN, v))
  watch(agentId, (v) => localStorage.setItem(LS_AGENT, v))

  /** 用 GET /metrics 验证 admin token；失败返回错误对象供页面展示 message+traceId */
  async function validate(): Promise<true | ApiError> {
    try {
      await http.get<MetricsResponse>('/api/v1/metrics')
      return true
    } catch (e) {
      return e instanceof ApiError ? e : new ApiError('UNKNOWN', '验证失败')
    }
  }

  return { adminToken, agentId, validate }
})
