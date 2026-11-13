#!/usr/bin/env node
// AllCLI Agent 接入示例（Node 18+，零依赖，原生 fetch）。
//
// 用法:
//   node allcli_demo.mjs [gatewayUrl] [toolId]
//   node allcli_demo.mjs http://localhost:8088 listPets
//
// 流程（工具消费方视角）:
//   1. 目录检索    GET  /api/v1/tools?q=
//   2. 质量自检    POST /api/v1/tools/{id}/quality    （advisory）
//   3. 沙箱试运行  POST /api/v1/tools/{id}/sandbox    （mock，不触达真实系统）
//   4. 真实调用    POST /api/v1/invoke                （需订阅已批准）

const BASE = process.argv[2] ?? "http://localhost:8088";
const TOOL = process.argv[3] ?? "listPets";
const TOKEN = "agent_demo"; // 一期 token 形态：agent_<agentId>；生产换 SSO/JWT

async function call(method, path, body) {
  const resp = await fetch(BASE + path, {
    method,
    headers: { Authorization: `Bearer ${TOKEN}`, "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return resp.json();
}

function pretty(title, payload) {
  console.log(`\n=== ${title} ===`);
  console.log(JSON.stringify(payload, null, 2).slice(0, 1200));
}

const listing = await call("GET", `/api/v1/tools?q=${TOOL}`);
pretty("1. 目录检索", listing);
if (listing.code !== "OK" || listing.data.total < 1) {
  console.log("工具不存在，先运行转化引擎 + publish");
  process.exit(0);
}

pretty("2. 质量自检", await call("POST", `/api/v1/tools/${TOOL}/quality`));
pretty("3. 沙箱试运行(mock)", await call("POST", `/api/v1/tools/${TOOL}/sandbox`, { arguments: { limit: 3 } }));

const invoke = await call("POST", "/api/v1/invoke", { toolName: TOOL, arguments: { limit: 3 } });
pretty("4. 真实调用", invoke);
if (invoke.code === "AUTH_DENIED") {
  console.log("\n提示: 先创建订阅并审批 (POST /api/v1/subscriptions + /api/v1/subscriptions/{id}/approve)");
}
