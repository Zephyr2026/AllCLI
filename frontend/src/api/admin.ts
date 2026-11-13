import http, { instance } from '@/api/http'
import type {
  ApprovalEntry,
  AuditFilters,
  AuditEntry,
  CredentialRegisterRequest,
  CredentialResponse,
  ImportJobResponse,
  ImportRequest,
  MetricsResponse,
  PageResponse,
  SystemRegisterRequest,
  SystemResponse,
} from '@/types/api'

export function getMetrics() {
  return http.get<MetricsResponse>('/api/v1/metrics')
}

export function listAudit(params: AuditFilters & { page: number; size: number }) {
  return http.get<PageResponse<AuditEntry>>('/api/v1/audit', { params })
}

/** CSV 导出：原始流非信封，须带 admin 头 → 用裸实例拿完整 AxiosResponse<Blob> 由调用方触发下载 */
export function exportAuditCsv(params: AuditFilters) {
  return instance.get<Blob>('/api/v1/audit/export.csv', { params, responseType: 'blob' })
}

export function listApprovals(params: { decision?: string; page: number; size: number }) {
  return http.get<PageResponse<ApprovalEntry>>('/api/v1/approvals', { params })
}

export function createImport(body: ImportRequest) {
  return http.post<ImportJobResponse>('/api/v1/imports', body)
}

export function getImportJob(jobId: string) {
  return http.get<ImportJobResponse>(`/api/v1/imports/${encodeURIComponent(jobId)}`)
}

export function registerSystem(body: SystemRegisterRequest) {
  return http.post<SystemResponse>('/api/v1/systems', body)
}

export function registerCredential(body: CredentialRegisterRequest) {
  return http.post<CredentialResponse>('/api/v1/credentials', body)
}
