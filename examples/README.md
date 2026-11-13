# AllCLI 接入示例（FR-AGENT-03）

工具消费方（Agent/脚本）视角的最小接入演示，**零第三方依赖**：

| 示例 | 运行要求 | 运行方式 |
|---|---|---|
| `python/allcli_demo.py` | Python 3.8+（标准库） | `python python/allcli_demo.py [gatewayUrl] [toolId]` |
| `node/allcli_demo.mjs` | Node 18+（原生 fetch） | `node node/allcli_demo.mjs [gatewayUrl] [toolId]` |

演示流程（默认 `http://localhost:8088` + `listPets`）：

1. **目录检索** `GET /api/v1/tools?q=` — 按关键字找工具（脱敏摘要）
2. **质量自检** `POST /api/v1/tools/{id}/quality` — description/inputSchema/examples/writeGuard checklist（advisory）
3. **沙箱试运行** `POST /api/v1/tools/{id}/sandbox` — mock 凭证 + outputSchema 生成 mock 响应，不触达真实系统
4. **真实调用** `POST /api/v1/invoke` — 走 10 步流水线（需订阅已批准，否则提示 AUTH_DENIED）

token 一期为 `Bearer agent_<agentId>`（如 `agent_demo`），生产换 SSO/JWT。
