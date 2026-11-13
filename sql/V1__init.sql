-- =====================================================================
-- AllCLI 元数据库 V1 初始化
-- 基于：《AllCLI_PRD详细设计》§2 数据模型
-- DB: allcli  (MySQL 8.0, utf8mb4)
-- 命名：Flyway 风格 V1__init.sql，便于控制面用 Flyway 管理
-- =====================================================================

SET NAMES utf8mb4;

-- -------------------------------------------------------------------
-- 1. 存量系统 System
-- -------------------------------------------------------------------
CREATE TABLE sys_system (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  system_id    VARCHAR(64)  NOT NULL COMMENT '业务ID，如 itsm-prod',
  name         VARCHAR(128) NOT NULL,
  base_url     VARCHAR(512) NOT NULL,
  auth_type    VARCHAR(32)  NOT NULL COMMENT 'OAuth2|ApiKey|Cookie|None',
  owner        VARCHAR(64)  NOT NULL,
  status       VARCHAR(16)  NOT NULL DEFAULT 'active',
  extra        JSON         NULL COMMENT '认证细节（非密钥）',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_system_id (system_id),
  KEY idx_system_owner (owner)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='存量系统';

-- -------------------------------------------------------------------
-- 2. 凭证 Credential（密文引用，明文永不入库）
-- -------------------------------------------------------------------
CREATE TABLE sys_credential (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  cred_id       VARCHAR(64)  NOT NULL,
  system_id     VARCHAR(64)  NOT NULL,
  type          VARCHAR(32)  NOT NULL COMMENT 'Vault|KMS|Opaque',
  secret_ref    VARCHAR(512) NOT NULL COMMENT '密文引用，如 vault://secret/itsm 或 KMS keyId',
  rotate_policy VARCHAR(64)  NULL COMMENT '轮转策略，如 every-90d',
  status        VARCHAR(16)  NOT NULL DEFAULT 'active',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_cred_id (cred_id),
  KEY idx_cred_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='凭证（密文引用）';

-- -------------------------------------------------------------------
-- 3. 工具定义 ToolDefinition（核心实体）
-- -------------------------------------------------------------------
CREATE TABLE sys_tool_definition (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tool_id             VARCHAR(128) NOT NULL COMMENT '工具标识，如 create_ticket',
  name                VARCHAR(128) NOT NULL,
  version             VARCHAR(32)  NOT NULL DEFAULT '1.0.0' COMMENT '当前版本',
  status              VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft|reviewing|published|gray|deprecated',
  system_id           VARCHAR(64)  NOT NULL,
  credential_id       VARCHAR(64)  NULL,
  description         TEXT         NULL,
  endpoint            JSON         NOT NULL COMMENT '{method,path,timeoutMs}',
  input_schema        JSON         NULL COMMENT 'JSON Schema',
  output_schema       JSON         NULL COMMENT 'JSON Schema',
  side_effect         VARCHAR(16)  NOT NULL DEFAULT 'readonly' COMMENT 'readonly|write',
  reversibility       VARCHAR(16)  NOT NULL DEFAULT 'readonly' COMMENT 'readonly|reversible|irreversible',
  idempotency_key     VARCHAR(64)  NULL,
  required_permission JSON         NULL COMMENT '["itsm:ticket:create"]',
  hitl                JSON         NULL COMMENT '{enabled,approverType,approverRef,timeoutSec}',
  examples            JSON         NULL,
  owner               VARCHAR(64)  NOT NULL,
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_id (tool_id),
  KEY idx_tool_system (system_id),
  KEY idx_tool_status (status),
  KEY idx_tool_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具定义（核心）';

-- -------------------------------------------------------------------
-- 4. 工具版本快照 ToolVersion
-- -------------------------------------------------------------------
CREATE TABLE sys_tool_version (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  tool_id             VARCHAR(128) NOT NULL,
  version             VARCHAR(32)  NOT NULL,
  changelog           TEXT         NULL,
  definition_snapshot JSON         NOT NULL COMMENT '发布时完整定义快照',
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_version (tool_id, version),
  KEY idx_tversion_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具版本快照';

-- -------------------------------------------------------------------
-- 5. 订阅 Subscription（Agent ↔ Tool）
-- -------------------------------------------------------------------
CREATE TABLE sys_subscription (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  sub_id      VARCHAR(64)  NOT NULL,
  agent_id    VARCHAR(64)  NOT NULL,
  tool_id     VARCHAR(128) NOT NULL,
  scope       VARCHAR(128) NULL COMMENT '数据域/系统范围',
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected|revoked',
  approved_by VARCHAR(64)  NULL,
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sub_id (sub_id),
  KEY idx_sub_agent (agent_id),
  KEY idx_sub_tool (tool_id),
  KEY idx_sub_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 订阅';

-- -------------------------------------------------------------------
-- 6. 调用记录 Invocation（元信息；明细审计入 ClickHouse）
-- -------------------------------------------------------------------
CREATE TABLE sys_invocation (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  invocation_id VARCHAR(64)  NOT NULL,
  trace_id      VARCHAR(64)  NULL,
  agent_id      VARCHAR(64)  NOT NULL,
  tool_id       VARCHAR(128) NOT NULL,
  input_digest  VARCHAR(128) NULL COMMENT '输入 SHA256',
  output_digest VARCHAR(128) NULL,
  status        VARCHAR(16)  NOT NULL COMMENT 'success|fail|hitl_pending|timeout',
  duration_ms   INT          NULL,
  approval_id   VARCHAR(64)  NULL,
  error_code    VARCHAR(32)  NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_invocation_id (invocation_id),
  KEY idx_inv_trace (trace_id),
  KEY idx_inv_tool (tool_id),
  KEY idx_inv_agent (agent_id),
  KEY idx_inv_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调用记录（元信息）';

-- -------------------------------------------------------------------
-- 7. HITL 审批 Approval
-- -------------------------------------------------------------------
CREATE TABLE sys_approval (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  approval_id   VARCHAR(64)  NOT NULL,
  invocation_id VARCHAR(64)  NOT NULL,
  approver_ref  VARCHAR(64)  NOT NULL,
  decision      VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected|timeout',
  reason        VARCHAR(512) NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  decided_at    DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_approval_id (approval_id),
  KEY idx_appr_inv (invocation_id),
  KEY idx_appr_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HITL 审批';
