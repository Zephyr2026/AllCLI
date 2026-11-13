// UI 冒烟：登录 → 看板 → 工具列表 → 详情 → Playground 调用。用本机 Edge 无头跑。
// 用法：node scripts/ui-smoke.mjs  （前置：gateway 8088、vite 5173 已启动）
import { chromium } from 'playwright-core'
import { mkdirSync } from 'node:fs'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'http://localhost:5173'
const SHOTS = 'e2e-shots'
mkdirSync(SHOTS, { recursive: true })

const errors = []
const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
// 订阅批给了 agent_demo：默认 Agent 身份设为它，才能 invoke
await context.addInitScript(() => localStorage.setItem('allcli.agentId', 'agent_demo'))
const page = await context.newPage()
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push(String(e)))

const shot = (n) => page.screenshot({ path: `${SHOTS}/${n}.png`, fullPage: false })

// 1. 登录页（store 预填默认 token 时守卫不拦截，故直达 /login 走校验流程）
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await page.waitForSelector('text=AllCLI 控制台', { timeout: 15000 })
await shot('01-login')
await page.click('button:has-text("保存并进入")')
await page.waitForSelector('.tile', { timeout: 15000 })
console.log('[1] 登录校验+看板 OK')

// 2. 工具列表
await page.waitForSelector('.tile', { timeout: 15000 })
await shot('02-dashboard')
await page.click('.el-menu-item:has-text("工具目录")')
await page.waitForSelector('.el-table__row', { timeout: 15000 })
await page.waitForTimeout(600)
const rows = await page.locator('.el-table__row').count()
console.log(`[2] 工具列表行数 = ${rows}`)
await shot('03-tools')

// 3. listPets 详情（schema 渲染）
await page.click('.el-table__row:has-text("listPets") a:has-text("listPets")')
await page.waitForSelector('text=inputSchema', { timeout: 15000 })
console.log('[3] 详情 schema OK')
await shot('04-detail')

// 4. Playground 调用 listPets（readonly 直放）
await page.click('.el-menu-item:has-text("调用测试")')
await page.waitForSelector('.el-select', { timeout: 15000 })
await page.click('.el-select')
await page.waitForSelector('.el-select-dropdown__item', { timeout: 10000 })
await page.click('.el-select-dropdown__item:has-text("listPets")')
await page.waitForSelector('text=工具信息', { timeout: 10000 })
await page.click('button:has-text("调用")')
await page.waitForSelector('text=调用结果', { timeout: 20000 })
await page.waitForTimeout(500)
console.log('[4] Playground 调用 OK')
await shot('05-invoke')

// 5. HITL 审批中心 & 审计页能打开
await page.click('.el-menu-item:has-text("HITL 审批")')
await page.waitForTimeout(800)
await shot('06-approvals')
await page.click('.el-menu-item:has-text("审计")')
await page.waitForTimeout(800)
await shot('07-audit')
console.log('[5] 审批/审计页 OK')

await browser.close()
if (errors.length) {
  console.log('CONSOLE ERRORS:')
  for (const e of errors) console.log('  -', e.slice(0, 300))
  process.exit(1)
}
console.log('UI SMOKE PASS')
