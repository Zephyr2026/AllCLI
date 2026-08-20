# AllCLI · 存量系统 → AI 工具化平台

> **让每一个存量系统，都能被 AI 安全、智能地调用。**

把存量系统/接口一键转化为带语义描述、Schema、副作用声明的「AI 工具」，经工具网关（MCP / Function Calling）暴露给 Agent 调用。

## 仓库结构

| 目录 | 说明 | 状态 |
|---|---|---|
| `engine/` | L1 转化引擎（OpenAPI/Swagger → Tool 定义 + 持久化） | ✅ M1 |
| `gateway/` | L4 运行时网关：10 步 invoke 流水线 + L3 目录 + MCP Server（同进程） | ✅ M2/M3 |
| `sql/` | 元数据库 DDL（V1 七表） | ✅ |
| `testdata/` | petstore.yaml（转化引擎对标输入） | ✅ |
| `scripts/` | E2E 辅助（MockPetstore mock 下游） | ✅ |
| `control-plane/` | 控制面管理 UI/看板 | M4 规划 |
| `frontend/` | 管理控制台 | M4 规划 |

> 后端统一 Java/Spring Boot（原 Go 双栈已于 2026-08-13 作废）。

## 里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 转化引擎：OpenAPI → 工具定义（语义/Schema/副作用/HITL 推断），`--persist` 入库 | ✅ 8/14 |
| M2 | 网关：invoke 十步流水线（鉴权/校验/限流/分级/HITL/幂等/凭证加密/脱敏/审计） | ✅ 8/15 |
| M3 | 平台闭环：L3 目录（注册/发现/订阅/发布）+ MCP Server（Streamable HTTP，同进程复用全部策略） | ✅ 8/19 |

排期：方案 A0，参见《AllCLI_WBS与排期.md》。

## 规划文档（根目录）

报名表 / 需求说明书(SRS) / PRD详细设计 / 技术架构与选型 / WBS与排期 / 一页纸摘要 / 技术选型打分表。

## 环境

- **本机**：Windows · JDK 17（`D:/work/software/jdk-17.0.15_windows-x64_bin`）· Maven 3.6（本地仓库 `D:\repository`）
- **DB / 中间件**：服务器 `192.168.85.128` · MySQL 8.0 + Redis 7（Docker）；库名 `allcli`
- **技术基线**：Spring Boot 3.5 + Spring AI 1.1（MCP java-sdk 0.18，Streamable HTTP `/mcp`）

## 快速开始

```bash
export JAVA_HOME=D:/work/software/jdk-17.0.15_windows-x64_bin

# 全量测试（engine 29 + gateway 38）
mvn test

# 启动网关（端口 8088，含 MCP Server）
mvn -pl gateway spring-boot:run
```

## E2E 全链路（M3 验证记录，2026-08-19 实测通过）

前置：MySQL/Redis 就绪、`sql/` DDL 已建库、网关启动。

```bash
# 0. mock 下游（petstore 存量系统替身，GET/POST /pets、DELETE /pets/{id}）
javac -encoding UTF-8 -d scripts scripts/MockPetstore.java && java -cp scripts MockPetstore 18090
#    并插入系统行（baseUrl 指向 mock）：
#    INSERT INTO sys_system(system_id,name,base_url,auth_type,owner,status,extra)
#      VALUES ('petstore','Petstore','http://127.0.0.1:18090','None','alice','active','{}');

# 1. 转化引擎：petstore.yaml → 3 个工具定义入库（draft）
java -jar engine/target/allcli-engine-0.1.0-SNAPSHOT.jar \
  --input=testdata/petstore.yaml --system=petstore --owner=alice --persist

# 2. 目录检索（脱敏：无 endpoint/credentialRef）
curl "http://localhost:8088/api/v1/tools?system=petstore"

# 3. 发布（写 sys_tool_version 快照；MCP tools/list 即时热更新，无需重启）
curl -X POST http://localhost:8088/api/v1/tools/listPets/publish \
  -H "Authorization: Bearer agent_alice" -H "Content-Type: application/json" \
  -d '{"target":"published","changelog":"first release"}'
curl -X POST http://localhost:8088/api/v1/tools/createPet/publish \
  -H "Authorization: Bearer agent_alice" -H "Content-Type: application/json" -d '{"target":"published"}'

# 4. 订阅 + 审批（tools/call 时 AuthService 强制校验订阅）
curl -X POST http://localhost:8088/api/v1/subscriptions \
  -H "Authorization: Bearer agent_demo" -H "Content-Type: application/json" \
  -d '{"agentId":"demo","toolId":"listPets"}'
#   → sub_id；再 POST /api/v1/subscriptions/{subId}/approve（agent_alice）

# 5. MCP 调用（初始化 → 列表 → 只读调用 → 写调用挂起 HITL → 审批放行）
curl -i -X POST http://localhost:8088/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"e2e","version":"1.0"}}}'
#   → 记住 Mcp-Session-Id，后续请求带上；notifications/initialized 后 tools/list / tools/call
curl -X POST http://localhost:8088/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" -H "Mcp-Session-Id: <sid>" \
  -H "Authorization: Bearer agent_demo" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"listPets","arguments":{"limit":2}}}'
#   写操作返回 pending_approval + approvalId：
curl -X POST http://localhost:8088/api/v1/hitl/{approvalId}/decision \
  -H "Authorization: Bearer agent_alice" -H "Content-Type: application/json" \
  -d '{"decision":"approved"}'
```

### Claude Code 接入

```bash
claude mcp add --transport http allcli http://localhost:8088/mcp \
  --header "Authorization: Bearer agent_demo"
```

会话内即可看到 `listPets`/`createPet` 等工具（含 `readOnlyHint`/`destructiveHint` 注解与 `_meta` 治理字段），调用全部经网关流水线（鉴权/限流/HITL/幂等/凭证/脱敏/审计零旁路）。

## 关键决策记录

- **D12**：MCP Server 与网关同进程，`tools/call` 转内部 invoke 链路（零旁路复用治理）
- **Streamable HTTP**：`spring.ai.mcp.server.protocol=STREAMABLE`（缺省 SSE），端点 `/mcp`
- **tools/list 暴露全部 published 工具**，订阅控制在 `tools/call` 强制（一期不做按会话过滤）
- **HITL 语义**：`pending_approval` 作为正常 tool result 返回（非错误），agent 持 approvalId 转述/轮询审批
- **JSON Schema 校验**：复用 MCP SDK 自带 validator（弃 networknt，规避 1.x/2.x 冲突）

## 已知限制（一期）

- 管理端点（publish/订阅审批）无 RBAC，任意 agent token 可操作（安全项待办）
- MCP 目录热更新为单节点内存态，多实例需重启或引入广播
- HITL 审批结果不回推 MCP 会话（agent 轮询/转述），后续可加 invocation 查询端点
- agent token 为无签名的 `agent_<id>` 演示形态，生产需换 OAuth2/API Key 对接
