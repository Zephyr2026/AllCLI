# AllCLI · 存量系统 → AI 工具化平台

> **让每一个存量系统，都能被 AI 安全、智能地调用。**

把存量系统/接口一键转化为带语义描述、Schema、副作用声明的「AI 工具」，经工具网关（MCP / Function Calling）暴露给 Agent 调用。

## 仓库结构

| 目录 | 说明 | 技术栈 |
|---|---|---|
| `engine/` | L1 转化引擎（OpenAPI/Swagger → Tool） | Java / Spring Boot（swagger-parser） |
| `gateway/` | L4 运行时网关（鉴权/限流/审计/HITL/分级） | Java / Spring Boot MVC |
| `control-plane/` | 控制面（目录/治理/度量 API） | Java / Spring Boot |
| `frontend/` | 管理控制台 | Vue + Element Plus |
| `sql/` | 元数据库 DDL（Flyway 风格） | MySQL 8.0 |
| `deploy/` | 部署（docker-compose 等） | Docker |
| `docs/` | 衍生文档 | — |
| `_build/` | 文档转换 / 临时脚本（不入库） | — |

> 后端统一 Java/Spring Boot（原 Go 双栈已于 2026-08-13 作废）。`engine/` 内 Java 实现为下一阶段新建；`sql/`、`testdata/`（petstore.yaml，转化引擎对标输入）已就绪。

## 规划文档（根目录）
报名表 / 需求说明书(SRS) / PRD详细设计 / 技术架构与选型 / WBS与排期 / 一页纸摘要 / 技术选型打分表。

## 环境

- **开发**：本机 Windows · Java 8 / Maven 3.6 / Node 24 / git
- **DB / 中间件**：服务器 `192.168.85.128` · MySQL 8.0 + Redis 7（Docker）
- **排期**：方案 A0，参见《AllCLI_WBS与排期.md》，M1 = 元 DB + 转化引擎（8/28）

## 快速开始

```bash
# 转化引擎（Java，下一阶段工程化后）
cd engine && mvn spring-boot:run

# 控制面
cd control-plane && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
```
（各模块随开发进度补充）
