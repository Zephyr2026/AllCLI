#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AllCLI Agent 接入示例（Python 标准库，零依赖，Python 3.8+）。

用法:
    python allcli_demo.py [gateway_url] [tool_id]
    python allcli_demo.py http://localhost:8088 listPets

流程（工具消费方视角）:
    1. 目录检索        GET  /api/v1/tools?q=
    2. 质量自检        POST /api/v1/tools/{id}/quality      （advisory）
    3. 沙箱试运行      POST /api/v1/tools/{id}/sandbox      （mock，不触达真实系统）
    4. 真实调用        POST /api/v1/invoke                  （需订阅已批准）
"""

import json
import sys
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8088"
TOOL = sys.argv[2] if len(sys.argv) > 2 else "listPets"
TOKEN = "agent_demo"  # 一期 token 形态：agent_<agentId>；生产换 SSO/JWT


def call(method: str, path: str, body=None) -> dict:
    req = urllib.request.Request(
        BASE + path,
        method=method,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def pretty(title: str, payload: dict) -> None:
    print(f"\n=== {title} ===")
    print(json.dumps(payload, ensure_ascii=False, indent=2)[:1200])


def main() -> None:
    print(f"AllCLI gateway: {BASE}  tool: {TOOL}")

    # 1. 目录检索
    listing = call("GET", f"/api/v1/tools?q={TOOL}")
    pretty("1. 目录检索", listing)
    if listing.get("code") != "OK" or listing["data"]["total"] < 1:
        print("工具不存在，先运行转化引擎 + publish")
        return

    # 2. 质量自检（advisory：description/inputSchema/examples/writeGuard）
    quality = call("POST", f"/api/v1/tools/{TOOL}/quality")
    pretty("2. 质量自检", quality)

    # 3. 沙箱试运行（入参可省略：回退 examples 或按 inputSchema 生成）
    sandbox = call("POST", f"/api/v1/tools/{TOOL}/sandbox",
                   {"arguments": {"limit": 3}})
    pretty("3. 沙箱试运行(mock)", sandbox)

    # 4. 真实调用（需 Subscription approved，否则 AUTH_DENIED）
    invoke = call("POST", "/api/v1/invoke",
                  {"toolName": TOOL, "arguments": {"limit": 3}})
    pretty("4. 真实调用", invoke)
    if invoke.get("code") == "AUTH_DENIED":
        print("\n提示: 先创建订阅并审批 "
              f"(POST /api/v1/subscriptions + /api/v1/subscriptions/{{id}}/approve)")


if __name__ == "__main__":
    main()
