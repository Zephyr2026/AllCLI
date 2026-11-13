// ===== 后端契约类型（与 gateway DTO 一一对应，Jackson camelCase）=====

/** 统一响应信封：业务错误也返回 HTTP 200，以 code 区分 */
export interface Envelope<T = unknown> {
  code: string
  message?: string
  data?: T
  traceId: string
  category?: 'auth' | 'limit' | 'validation' | 'business' | 'upstream' | 'hitl'
  retriable?: boolean
}

/** 分页响应（请求参数是 page(0起)/size/sort，注意名称不同） */
export interface PageResponse<T> {
  page: number
  pageSize: number
  total: number
  items: T[]
}

// ===== 工具目录 =====

export type ToolStatus = 'draft' | 'reviewing' | 'published' | 'gray' | 'deprecated'
export type SideEffect = 'readonly' | 'write'
export type Reversibility = 'readonly' | 'reversible' | 'irreversible'

export interface ToolSummary {
  toolId: string
  name: string
  /** 语义化版本字符串，如 "1.0.0" */
  version: string
  status: ToolStatus
  systemRef: string
  description: string
  sideEffect: SideEffect
  reversibility?: Reversibility
  hitlEnabled: boolean
  owner?: string
}

export interface HitlPolicy {
  enabled: boolean
  approverType?: string
  approverRef?: string
  timeoutSec?: number
}

export interface ToolDetail extends ToolSummary {
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
  requiredPermission?: string[]
  idempotencyKey?: string
  hitl?: HitlPolicy
  examples?: Record<string, unknown>[]
}

export interface ToolListFilters {
  q?: string
  system?: string
  reversibility?: Reversibility
  status?: ToolStatus
}

export interface PublishRequest {
  target: 'published' | 'gray'
  grayPercent?: number
  changelog?: string
}

export interface PublishResponse {
  toolId: string
  version: string
  status: ToolStatus
  snapshotId?: number | null
}

export interface SemanticUpdateRequest {
  description?: string
  requiredPermission?: string[]
  sideEffect?: SideEffect
  reversibility?: Reversibility
  idempotencyKey?: string
  /** 出现即整体替换：4 个字段全部提交 */
  hitl?: HitlPolicy
}

export interface SemanticUpdateResponse {
  toolId: string
  version: string
  status: ToolStatus
  warnings: string[]
}

// ===== 质量 / 沙箱 =====

export interface QualityItem {
  item: 'description' | 'inputSchema' | 'examples' | 'writeGuard' | string
  status: 'pass' | 'warn' | 'fail'
  detail: string
}

export interface QualityReport {
  toolId: string
  overall: 'pass' | 'warn' | 'fail'
  items: QualityItem[]
}

export interface SandboxResponse {
  toolId: string
  version: string
  sideEffect: SideEffect
  status: string
  sandbox: boolean
  argSource: 'request' | 'example' | 'generated'
  arguments: Record<string, unknown>
  result: unknown
  notes: string[]
}

// ===== 调用 / HITL =====

export interface InvokeRequest {
  toolName: string
  version?: string
  arguments: Record<string, unknown>
  idempotencyKey?: string
}

export interface InvokeData {
  status: 'succeeded' | 'pending_approval' | 'replayed'
  result?: unknown
  invocationId?: string
  approvalId?: string
  expiresAt?: string
}

export interface HitlDecisionRequest {
  decision: 'approved' | 'rejected'
  reason?: string
  /** 不可逆工具批准时必须为 true */
  confirmIrreversible?: boolean
}

// ===== 订阅 =====

export type SubStatus = 'pending' | 'approved' | 'rejected' | 'revoked'

export interface SubscriptionResponse {
  subId: string
  agentId: string
  toolId: string
  scope?: string
  status: SubStatus
  approvedBy?: string
}

export interface SubscriptionCreateRequest {
  agentId: string
  toolId: string
  scope?: string
}

// ===== 导入 =====

export interface ImportRequest {
  sourceType: 'openapi'
  sourceUrl?: string
  sourceContent?: string
  systemRef: string
  credentialRef?: string
  owner?: string
  version?: string
  timeoutMs?: number
  includePaths?: string[]
  excludeRegex?: string
}

export interface ImportJobResponse {
  jobId: string
  status: 'running' | 'done' | 'failed'
  created?: string[]
  conflicts?: string[]
  errorCode?: string
  errorMessage?: string
  submittedAt: string
  finishedAt?: string
}

// ===== 审计 / 审批 =====

export type InvocationStatus = 'success' | 'fail' | 'hitl_pending' | 'timeout'

export interface AuditEntry {
  createdAt: string
  invocationId: string
  traceId: string
  agentId: string
  toolId: string
  status: InvocationStatus
  durationMs: number
  errorCode?: string
  approvalId?: string
}

export interface AuditFilters {
  agentId?: string
  toolId?: string
  from?: string
  to?: string
  reversibility?: Reversibility
}

export interface ApprovalEntry {
  approvalId: string
  invocationId: string
  toolId: string
  agentId: string
  approverRef: string
  reason?: string
  createdAt: string
}

// ===== 指标 =====

export interface InvocationMetrics {
  total?: number
  success?: number
  fail?: number
  pendingApproval?: number
  hitl?: number
  /** 0–1 小数 */
  hitlRate?: number
  avgDurationMs?: number | null
}

export interface MetricsResponse {
  systemCount: number
  tools: {
    total: number
    draft: number
    reviewing: number
    published: number
    gray: number
    deprecated: number
  }
  invocations: {
    today: InvocationMetrics
    last7d: InvocationMetrics
    last1m: { count: number; qps: number }
  }
  reuse: {
    totalTools: number
    usedTools: number
    reusedTools: number
    /** 0–1 小数 */
    reuseRate: number
    reusedToolIds: string[]
  }
}

// ===== 系统 / 凭证 =====

export interface SystemRegisterRequest {
  systemId: string
  name?: string
  baseUrl: string
  authType: 'OAuth2' | 'ApiKey' | 'Cookie' | 'None'
  owner?: string
}

export interface SystemResponse {
  systemId: string
  name?: string
  baseUrl: string
  authType: string
  owner?: string
  status: string
}

export interface CredentialRegisterRequest {
  systemRef: string
  type: 'Vault' | 'KMS' | 'Opaque'
  secret: string
  rotatePolicy?: string
}

export interface CredentialResponse {
  credId: string
  systemRef: string
  type: string
  rotatePolicy?: string
  status: string
}
