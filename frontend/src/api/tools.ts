import http from '@/api/http'
import type {
  PageResponse,
  PublishRequest,
  PublishResponse,
  QualityReport,
  SandboxResponse,
  SemanticUpdateRequest,
  SemanticUpdateResponse,
  ToolDetail,
  ToolListFilters,
  ToolSummary,
} from '@/types/api'

export function listTools(params: ToolListFilters & { page: number; size: number }) {
  // 目录查询公开，无需 token
  return http.get<PageResponse<ToolSummary>>('/api/v1/tools', { params, authMode: 'none' })
}

export function getTool(toolId: string) {
  return http.get<ToolDetail>(`/api/v1/tools/${encodeURIComponent(toolId)}`, { authMode: 'none' })
}

export function publishTool(toolId: string, body: PublishRequest) {
  return http.post<PublishResponse>(`/api/v1/tools/${encodeURIComponent(toolId)}/publish`, body, { authMode: 'agent' })
}

export function deprecateTool(toolId: string) {
  return http.post<PublishResponse>(`/api/v1/tools/${encodeURIComponent(toolId)}/deprecate`, undefined, { authMode: 'agent' })
}

export function updateToolSemantic(toolId: string, body: SemanticUpdateRequest) {
  return http.put<SemanticUpdateResponse>(`/api/v1/tools/${encodeURIComponent(toolId)}`, body)
}

export function runQuality(toolId: string) {
  return http.post<QualityReport>(`/api/v1/tools/${encodeURIComponent(toolId)}/quality`, undefined, { authMode: 'agent' })
}

export function runSandbox(toolId: string, body?: { arguments?: Record<string, unknown> }) {
  return http.post<SandboxResponse>(`/api/v1/tools/${encodeURIComponent(toolId)}/sandbox`, body, { authMode: 'agent' })
}
