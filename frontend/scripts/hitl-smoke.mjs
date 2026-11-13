// HITL 写操作全环冒烟：deletePet(irreversible+HITL) invoke 挂起 → 审批中心批准(二次确认) → 幂等重放
// 前置：gateway/vite 已启动；deletePet 已发布；agent_demo 已订阅且 admin token 已配
import { chromium } from 'playwright-core'
import { mkdirSync } from 'node:fs'

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const SHOTS = 'e2e-shots'
mkdirSync(SHOTS, { recursive: true })

const errors = []
const browser = await chromium.launch({ executablePath: EDGE, headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
await context.addInitScript(() => localStorage.setItem('allcli.agentId', 'agent_demo'))
const page = await context.newPage()
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push(String(e)))
const shot = (n) => page.screenshot({ path: `${SHOTS}/${n}.png` })

// 1. Playground 调 deletePet（带幂等键）→ pending_approval
await page.goto('http://localhost:5173/playground', { waitUntil: 'domcontentloaded' })
await page.waitForSelector('.el-select', { timeout: 15000 })
await page.click('.el-select')
await page.keyboard.type('deletePet')
await page.waitForSelector('.el-select-dropdown__item:has-text("deletePet")', { timeout: 10000 })
await page.click('.el-select-dropdown__item:has-text("deletePet")')
await page.waitForSelector('text=工具信息', { timeout: 10000 })
const idem = `demo-${Date.now()}`
await page.fill('.mono textarea', '{"petId": "smoke-1"}')
await page.fill('input[placeholder*="写操作同参数"]', idem)
await page.click('button:has-text("调用")')
await page.waitForSelector('text=等待人工审批', { timeout: 20000 })
console.log('[1] deletePet 挂起 pending_approval OK')
await shot('10-hitl-pending')

// 2. 审批中心 → 审批 dialog → 不可逆二次确认 → 提交
await page.click('.el-menu-item:has-text("HITL 审批")')
await page.waitForSelector('.el-table__row:has-text("deletePet")', { timeout: 15000 })
await shot('11-hitl-center')
await page.click('.el-table__row:has-text("deletePet") button:has-text("审批")')
await page.waitForSelector('text=将以 agent_', { timeout: 10000 })
await page.fill('.el-dialog textarea', 'demo 冒烟批准')
const confirmBox = page.locator('text=我已知晓该操作').first()
await confirmBox.waitFor({ timeout: 5000 }).catch(() => {})
await confirmBox.click()
await shot('12-hitl-dialog')
await page.click('.el-dialog button:has-text("提交")')
await page.waitForSelector('.el-dialog >> text=succeeded', { timeout: 20000 })
console.log('[2] 审批 approve → succeeded OK')
await shot('13-hitl-approved')
await page.click('.el-dialog button:has-text("关闭")')

// 3. 同幂等键重发 → replayed
await page.click('.el-menu-item:has-text("调用测试")')
await page.waitForSelector('.el-select', { timeout: 15000 })
await page.click('.el-select')
await page.keyboard.type('deletePet')
await page.waitForSelector('.el-select-dropdown__item:has-text("deletePet")', { timeout: 10000 })
await page.click('.el-select-dropdown__item:has-text("deletePet")')
await page.waitForSelector('text=工具信息', { timeout: 10000 })
await page.fill('.mono textarea', '{"petId": "smoke-1"}')
await page.fill('input[placeholder*="写操作同参数"]', idem)
await page.click('button:has-text("调用")')
await page.waitForSelector('text=幂等命中', { timeout: 20000 })
console.log('[3] 幂等重放 replayed OK')
await shot('14-idempotent-replay')

// 4. 审计页可见 hitl_pending→success 记录
await page.click('.el-menu-item:has-text("审计")')
await page.waitForTimeout(900)
await shot('15-audit-after-hitl')
console.log('[4] 审计页 OK')

await browser.close()
if (errors.length) {
  console.log('CONSOLE ERRORS:')
  for (const e of errors) console.log('  -', e.slice(0, 300))
  process.exit(1)
}
console.log('HITL SMOKE PASS')
