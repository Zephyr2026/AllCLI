import http from '@/api/http'
import type { PageResponse, SubscriptionCreateRequest, SubscriptionResponse } from '@/types/api'

export function listSubscriptions(params: { status?: string; page: number; size: number }) {
  return http.get<PageResponse<SubscriptionResponse>>('/api/v1/subscriptions', { params })
}

export function createSubscription(body: SubscriptionCreateRequest) {
  return http.post<SubscriptionResponse>('/api/v1/subscriptions', body, { authMode: 'none' })
}

/** 批准/拒绝：后端不校验身份（一期无 RBAC），以默认 agent 身份执行 */
export function approveSubscription(subId: string) {
  return http.post<SubscriptionResponse>(`/api/v1/subscriptions/${encodeURIComponent(subId)}/approve`, undefined, { authMode: 'agent' })
}

export function rejectSubscription(subId: string) {
  return http.post<SubscriptionResponse>(`/api/v1/subscriptions/${encodeURIComponent(subId)}/reject`, undefined, { authMode: 'agent' })
}

export function revokeSubscription(subId: string) {
  return http.post<SubscriptionResponse>(`/api/v1/subscriptions/${encodeURIComponent(subId)}/revoke`, undefined)
}
