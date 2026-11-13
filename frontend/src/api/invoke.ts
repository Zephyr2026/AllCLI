import http from '@/api/http'
import type { HitlDecisionRequest, InvokeData, InvokeRequest } from '@/types/api'

export function invoke(body: InvokeRequest) {
  return http.post<InvokeData>('/api/v1/invoke', body, { authMode: 'agent' })
}

/**
 * HITL 审批：后端强校验 token 的 agentId == 该单 approverRef（HitlService），
 * 故必须用 agentIdOverride 指定审批人身份，而非默认 agent。
 */
export function decideHitl(approvalId: string, body: HitlDecisionRequest, approverRef: string) {
  return http.post<InvokeData>(`/api/v1/hitl/${encodeURIComponent(approvalId)}/decision`, body, {
    authMode: 'agent',
    agentIdOverride: approverRef,
  })
}
