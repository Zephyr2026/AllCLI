// 银行 AI 原生改造路线图 PPT 生成器
// 运行: node generate.js   (需 npm install pptxgenjs)
const PptxGenJS = require('pptxgenjs');

const C = {
  navy: '0E2A47', navy2: '16467A', blue: '1F6FB2', blueL: '5DA9DA',
  gold: 'C8A04B', goldL: 'E6C875', light: 'F4F6F8', light2: 'E2E8F0',
  dark: '27374A', gray: '6B7A8C', white: 'FFFFFF',
  green: '1E8449', red: 'B03A2E', amber: 'D68910',
};
const F = '微软雅黑';

const pptx = new PptxGenJS();
pptx.defineLayout({ name: 'WW', width: 13.333, height: 7.5 });
pptx.layout = 'WW';
pptx.author = 'AllCLI';
pptx.title = '银行 AI 原生改造路线图';

// ---------- helpers ----------
function header(slide, title, kicker) {
  slide.addShape('rect', { x: 0, y: 0, w: 13.333, h: 1.12, fill: { color: C.navy } });
  slide.addShape('rect', { x: 0, y: 1.12, w: 13.333, h: 0.06, fill: { color: C.gold } });
  slide.addShape('rect', { x: 0.55, y: 0.36, w: 0.12, h: 0.5, fill: { color: C.gold } });
  if (kicker) slide.addText(kicker, { x: 0.8, y: 0.22, w: 11, h: 0.3, fontFace: F, color: C.goldL, fontSize: 11, bold: true });
  slide.addText(title, { x: 0.8, y: 0.45, w: 11.7, h: 0.6, fontFace: F, color: C.white, fontSize: 24, bold: true, valign: 'middle' });
}
function footer(slide, n) {
  slide.addShape('line', { x: 0.55, y: 7.05, w: 12.23, h: 0, line: { color: C.light2, width: 1 } });
  slide.addText('银行 AI 原生改造路线图', { x: 0.55, y: 7.08, w: 8, h: 0.3, fontFace: F, fontSize: 9, color: C.gray });
  slide.addText(String(n), { x: 12.2, y: 7.08, w: 0.6, h: 0.3, fontFace: F, fontSize: 9, color: C.gray, align: 'right' });
}
function bullet(slide, items, opt = {}) {
  const x = opt.x ?? 0.8, y = opt.y ?? 1.5, w = opt.w ?? 7.1, h = opt.h ?? 5.3, fs = opt.fs ?? 15;
  const arr = items.map((it) => {
    if (typeof it === 'string') return { text: it, options: { bullet: { code: '25CF' }, color: C.dark, fontSize: fs, fontFace: F, paraSpaceAfter: 8, lineSpacingMultiple: 1.2 } };
    return {
      text: it.t,
      options: {
        bullet: it.sub ? false : { code: '25CF' },
        color: it.c || C.dark, fontSize: it.fs || fs, fontFace: F, bold: !!it.b,
        paraSpaceAfter: it.sub ? 4 : 8, lineSpacingMultiple: 1.2, indentLevel: it.sub ? 1 : 0,
      },
    };
  });
  slide.addText(arr, { x, y, w, h, valign: 'top' });
}
function chip(slide, x, y, w, h, text, fill, tcolor, fs = 13) {
  slide.addShape('roundRect', { x, y, w, h, fill: { color: fill }, rectRadius: 0.06, line: { color: fill, width: 0 } });
  slide.addText(text, { x, y, w, h, fontFace: F, color: tcolor, fontSize: fs, bold: true, align: 'center', valign: 'middle' });
}

// ---------- 1. 封面 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.navy };
  s.addShape('rect', { x: 0, y: 0, w: 0.35, h: 7.5, fill: { color: C.gold } });
  s.addText('AI 原生银行', { x: 0.9, y: 2.35, w: 11.5, h: 1.2, fontFace: F, color: C.white, fontSize: 50, bold: true });
  s.addText('系统性改造路线图', { x: 0.9, y: 3.5, w: 11.5, h: 0.9, fontFace: F, color: C.goldL, fontSize: 32, bold: true });
  s.addShape('rect', { x: 0.95, y: 4.55, w: 2.2, h: 0.08, fill: { color: C.gold } });
  s.addText('从"流程驱动"到"智能驱动 · 能力可被 Agent 调用"的系统性重构', { x: 0.9, y: 4.75, w: 11.5, h: 0.55, fontFace: F, color: C.blueL, fontSize: 16 });
  s.addText('Banking AI-Native Transformation Roadmap', { x: 0.9, y: 5.35, w: 11.5, h: 0.4, fontFace: F, color: C.gray, fontSize: 13 });
  s.addText('汇报人:____      日期:2026 年 6 月', { x: 0.9, y: 6.5, w: 11.5, h: 0.4, fontFace: F, color: C.light2, fontSize: 13 });
}

// ---------- 2. 议程 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '汇报议程', 'AGENDA');
  const items = [
    ['01', '为什么是现在', '行业 · 监管 · 竞争三大驱动力'],
    ['02', '核心认知纠偏', 'AI 原生 ≠ 外挂一个对话框'],
    ['03', '七层改造蓝图', '从基础设施到体验的系统性重构'],
    ['04', 'L4 能力开放层(重点)', '从"人用 REST"到"Agent 用语义 API"'],
    ['05', '治理、安全与监管红线', '2026 年合规约束'],
    ['06', '落地路径与 Roadmap', '四步走 + 价值 + 风险'],
  ];
  items.forEach((it, i) => {
    const col = i < 3 ? 0 : 1, row = i % 3;
    const x = 0.8 + col * 6.1, y = 1.65 + row * 1.65;
    s.addShape('roundRect', { x, y, w: 5.7, h: 1.4, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addText(it[0], { x: x + 0.2, y, w: 1.1, h: 1.4, fontFace: F, color: C.gold, fontSize: 40, bold: true, align: 'center', valign: 'middle' });
    s.addText(it[1], { x: x + 1.35, y: y + 0.22, w: 4.2, h: 0.5, fontFace: F, color: C.navy, fontSize: 17, bold: true });
    s.addText(it[2], { x: x + 1.35, y: y + 0.72, w: 4.2, h: 0.5, fontFace: F, color: C.gray, fontSize: 12.5 });
  });
  footer(s, 2);
}

// ---------- 3. 为什么是现在 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '为什么是"现在":三大驱动力', '01 · 背景');
  const cards = [
    ['行业趋势', C.blue, ['Solaris 宣布打造"欧洲首家 AI 原生银行"(2026.03)', '工行"工银智涌"接入 DeepSeek,落地金融市场/风控/营销', '上海银行推出 AI 原生手机银行,底层到界面重构']],
    ['监管收紧', C.amber, ['2026 美国跨机构《模型风险管理》修订,首覆盖 AI/ML', '央行:安全稳妥推进大模型、加快智能化转型', '欧盟 AI Act、德国 BaFin、算法备案与信创要求']],
    ['竞争与客户', C.green, ['领先行已从"降本增效"深入风控/营销/交易核心', '客户期望对话式、即时、个性化服务', '不做 AI 原生 = 在下一代客户入口中缺席']],
  ];
  cards.forEach((c, i) => {
    const x = 0.8 + i * 4.05;
    s.addShape('roundRect', { x, y: 1.6, w: 3.8, h: 4.5, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('rect', { x, y: 1.6, w: 3.8, h: 0.7, fill: { color: c[1] } });
    s.addText(c[0], { x, y: 1.6, w: 3.8, h: 0.7, fontFace: F, color: C.white, fontSize: 17, bold: true, align: 'center', valign: 'middle' });
    s.addText(c[2].map((t) => ({ text: t, options: { bullet: { code: '25CF' }, color: C.dark, fontSize: 13, fontFace: F, paraSpaceAfter: 10, lineSpacingMultiple: 1.15 } })), { x: x + 0.25, y: 2.5, w: 3.3, h: 3.4, valign: 'top' });
  });
  s.addShape('roundRect', { x: 0.8, y: 6.25, w: 11.7, h: 0.6, fill: { color: C.navy } });
  s.addText('结论:不是"要不要做",而是"多快能安全地做"——晚一步即失去下一代入口', { x: 0.8, y: 6.25, w: 11.7, h: 0.6, fontFace: F, color: C.goldL, fontSize: 14, bold: true, align: 'center', valign: 'middle' });
  footer(s, 3);
}

// ---------- 4. 认知纠偏 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '核心认知纠偏:AI 原生 ≠ 外挂一个对话框', '02 · 认知');
  s.addText('传统银行系统 = 面向流程与事务(核心账务 + 渠道),AI 只是外挂', { x: 0.8, y: 1.5, w: 11.7, h: 0.5, fontFace: F, color: C.gray, fontSize: 14, italic: true });
  s.addText('AI 原生银行,要求系统原生具备三件事:', { x: 0.8, y: 2.05, w: 11.7, h: 0.5, fontFace: F, color: C.navy, fontSize: 16, bold: true });
  const pts = [
    ['①', '能力可被语义化调用', '每个银行业务能力 = 带描述 / 参数 schema / 副作用声明的"工具",供 Agent 编排'],
    ['②', '数据实时、统一、可检索', '模型的"燃料"必须随时可得:湖仓一体 + 实时管道 + 向量知识库'],
    ['③', '决策可解释、可追溯、可干预', '每个决策可回放、可解释、可人工介入——这是金融牌照的红线'],
  ];
  pts.forEach((p, i) => {
    const y = 2.75 + i * 1.25;
    s.addShape('roundRect', { x: 0.8, y, w: 11.7, h: 1.05, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('roundRect', { x: 0.95, y: y + 0.18, w: 0.7, h: 0.7, fill: { color: C.gold } });
    s.addText(p[0], { x: 0.95, y: y + 0.18, w: 0.7, h: 0.7, fontFace: F, color: C.white, fontSize: 22, bold: true, align: 'center', valign: 'middle' });
    s.addText(p[1], { x: 1.85, y: y + 0.12, w: 4, h: 0.5, fontFace: F, color: C.navy, fontSize: 17, bold: true });
    s.addText(p[2], { x: 1.85, y: y + 0.55, w: 10.4, h: 0.5, fontFace: F, color: C.dark, fontSize: 13.5 });
  });
  footer(s, 4);
}

// ---------- 5. 七层改造蓝图总览 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '改造蓝图总览:七层架构', '03 · 蓝图');
  // 左侧治理贯穿竖条
  s.addShape('roundRect', { x: 0.85, y: 1.65, w: 0.7, h: 5.05, fill: { color: C.gold } });
  s.addText('L6\n治理与安全\n(横向贯穿)', { x: 0.85, y: 1.65, w: 0.7, h: 5.05, fontFace: F, color: C.white, fontSize: 12, bold: true, align: 'center', valign: 'middle', lineSpacingMultiple: 1.1 });
  const layers = [
    ['L5', '体验层', '对私 AI 原生手机银行 · 对公财资/支付/证券 · 内部 Copilot', C.blueL],
    ['L4', '能力开放层 ★', '语义化 API · Function Calling / MCP · 工具网关 · 分级授权审计', C.gold],
    ['L3', '智能体编排层', '多 Agent 编排 · 记忆 · 人在回路卡点 · 会话管理', C.blue],
    ['L2', '模型层', '大模型 + 微模型协同 · MaaS 路由 · MLOps/LLMOps · 模型风险', C.navy2],
    ['L1', '数据层', '湖仓一体 · 实时管道 · 向量知识库 · 数据治理与隐私', C.navy],
    ['L0', '基础设施层', '异构算力池 · 弹性调度 · 信创/混合云 · 两地三中心', '2C3E50'],
  ];
  const x0 = 1.75, w = 9.4, h = 0.74, gap = 0.1, y0 = 1.7;
  layers.forEach((l, i) => {
    const y = y0 + i * (h + gap);
    const hl = (l[1].includes('★'));
    s.addShape('roundRect', { x: x0, y, w, h, fill: { color: l[3] }, line: hl ? { color: C.goldL, width: 2 } : { color: l[3], width: 0 } });
    s.addText(l[0], { x: x0 + 0.1, y, w: 0.9, h, fontFace: F, color: C.white, fontSize: 20, bold: true, align: 'center', valign: 'middle' });
    s.addText(l[1], { x: x0 + 1.05, y, w: 2.7, h, fontFace: F, color: C.white, fontSize: 15, bold: true, valign: 'middle' });
    s.addText(l[2], { x: x0 + 3.8, y, w: 5.4, h, fontFace: F, color: hl ? '3A2E00' : C.white, fontSize: 12.5, valign: 'middle' });
  });
  s.addText('★ 本行工作重点:把银行能力做成 Agent 可调用的工具(CLI / MCP)', { x: 1.75, y: 6.85, w: 10, h: 0.3, fontFace: F, color: C.gold, fontSize: 12, bold: true });
  footer(s, 5);
}

// ---------- 6. L0 + L1 地基层 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'L0–L1 地基:基础设施与数据(最易被低估)', '03 · 蓝图');
  s.addShape('roundRect', { x: 0.8, y: 1.5, w: 5.75, h: 5.2, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
  s.addShape('rect', { x: 0.8, y: 1.5, w: 5.75, h: 0.65, fill: { color: C.navy } });
  s.addText('L0 · 基础设施层', { x: 0.8, y: 1.5, w: 5.75, h: 0.65, fontFace: F, color: C.white, fontSize: 16, bold: true, align: 'center', valign: 'middle' });
  bullet(s, [
    '异构算力池:GPU 推理集群 + 弹性调度',
    '混合云:敏感负载私有化、弹性负载上云',
    '向量数据库 + 流式计算,支撑 RAG 与在线特征',
    '信创 / 国产化适配(国内刚需)',
    '两地三中心、网络隔离区(DMZ)',
  ], { x: 1.05, y: 2.3, w: 5.3, fs: 14 });
  s.addShape('roundRect', { x: 6.75, y: 1.5, w: 5.75, h: 5.2, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
  s.addShape('rect', { x: 6.75, y: 1.5, w: 5.75, h: 0.65, fill: { color: C.blue } });
  s.addText('L1 · 数据层(最易卡住)', { x: 6.75, y: 1.5, w: 5.75, h: 0.65, fontFace: F, color: C.white, fontSize: 16, bold: true, align: 'center', valign: 'middle' });
  bullet(s, [
    '统一数据底座(湖仓一体),打破核心/信贷/零售竖井,建客户 360',
    '实时数据管道:交易流、行为流实时入仓',
    '向量库 + 知识库:产品/规章/案例可检索(RAG)',
    '非结构化治理:合同、凭证、语音、影像结构化',
    '治理:血缘、质量、脱敏/差分隐私、数据不出域',
  ], { x: 7.0, y: 2.3, w: 5.3, fs: 14 });
  footer(s, 6);
}

// ---------- 7. L2 模型层 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'L2 模型层:大模型 + 微模型协同', '03 · 蓝图');
  bullet(s, [
    { t: '"大模型 + 微模型"协同体系(上海银行做法)', b: true, c: C.navy, fs: 16 },
    { t: '基座大模型(自研 / DeepSeek / Qwen)+ 垂域微调 + 小模型 / 规则兜底', sub: true, fs: 14 },
    { t: '模型即服务(MaaS)+ 模型路由', b: true, c: C.navy, fs: 16 },
    { t: '按 成本 / 延迟 / 敏感度 自动路由请求,推理成本可控', sub: true, fs: 14 },
    { t: 'MLOps / LLMOps 全生命周期', b: true, c: C.navy, fs: 16 },
    { t: '训练 → 评测 → 灰度 → 监控漂移 → 回滚', sub: true, fs: 14 },
    { t: '模型风险管理(MRM)', b: true, c: C.navy, fs: 16 },
    { t: '对接 2026 监管:清单、验证、可解释性、偏见监测', sub: true, fs: 14 },
    { t: '安全分级部署:高敏数据本地推理,通用能力走云', b: true, c: C.navy, fs: 16 },
  ], { x: 0.9, y: 1.6, w: 8, h: 5.2 });
  // 右侧示意:三层模型
  const bx = 9.2;
  [['基座大模型', C.navy2, '通用理解/生成'], ['垂域微调', C.blue, '金融语料/任务'], ['规则/小模型', C.blueL, '兜底·可解释']].forEach((m, i) => {
    const y = 1.8 + i * 1.35;
    s.addShape('roundRect', { x: bx, y, w: 3.3, h: 1.1, fill: { color: m[1] } });
    s.addText(m[0], { x: bx, y: y + 0.12, w: 3.3, h: 0.45, fontFace: F, color: C.white, fontSize: 15, bold: true, align: 'center' });
    s.addText(m[2], { x: bx, y: y + 0.58, w: 3.3, h: 0.4, fontFace: F, color: C.light2, fontSize: 12, align: 'center' });
  });
  footer(s, 7);
}

// ---------- 8. L3 智能体编排层 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'L3 智能体编排层:让 Agent 真正"干活"', '03 · 蓝图');
  const mods = [
    ['规划与反思', '拆解任务、自我纠错、多步推理', C.navy2],
    ['工具调用', '通过 L4 工具网关调用银行能力', C.blue],
    ['记忆管理', '短期会话 + 长期画像 + 知识检索', C.blueL],
    ['人在回路(HITL)', '资金/合规类高风险操作必须人工确认', C.gold],
    ['多 Agent 协作', '客服 / 风控 / 运营 Agent 编队协作', C.green],
    ['可观测与降级', '轨迹留痕、异常熔断、回退规则引擎', C.red],
  ];
  mods.forEach((m, i) => {
    const col = i % 3, row = Math.floor(i / 3);
    const x = 0.8 + col * 4.05, y = 1.65 + row * 2.55;
    s.addShape('roundRect', { x, y, w: 3.8, h: 2.25, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('roundRect', { x: x + 0.25, y: y + 0.25, w: 0.7, h: 0.7, fill: { color: m[2] } });
    s.addText(String(i + 1), { x: x + 0.25, y: y + 0.25, w: 0.7, h: 0.7, fontFace: F, color: C.white, fontSize: 22, bold: true, align: 'center', valign: 'middle' });
    s.addText(m[0], { x: x + 1.1, y: y + 0.3, w: 2.55, h: 0.6, fontFace: F, color: C.navy, fontSize: 15.5, bold: true, valign: 'middle' });
    s.addText(m[1], { x: x + 0.25, y: y + 1.1, w: 3.3, h: 1, fontFace: F, color: C.dark, fontSize: 13, lineSpacingMultiple: 1.15 });
  });
  footer(s, 8);
}

// ---------- 9. L4 能力开放层(重点) ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'L4 能力开放层 ★:从"人用 REST"到"Agent 用语义 API"', '04 · 重点');
  chip(s, 0.8, 1.45, 11.7, 0.5, '这是本行 AllCLI 工作的主战场 —— 把每个银行能力变成 Agent 可调用的"工具"', C.gold, '3A2E00', 14);
  bullet(s, [
    { t: '范式升级', b: true, c: C.navy, fs: 15 },
    { t: '面向人类 REST → 面向 Agent 的语义化 API:每个能力带描述、参数 schema、幂等键、副作用与可逆性声明', sub: true, fs: 13.5 },
    { t: '协议暴露', b: true, c: C.navy, fs: 15 },
    { t: '用 Function Calling / MCP Server 暴露银行能力(OpenAPI 操作 → 工具,如 restish-mcp 思路)', sub: true, fs: 13.5 },
    { t: '网关增强(Agent 防护)', b: true, c: C.navy, fs: 15 },
    { t: '意图校验、按 Agent 身份限流、全量审计、"可撤销/不可逆"操作分级', sub: true, fs: 13.5 },
    { t: '标准化沉淀', b: true, c: C.navy, fs: 15 },
    { t: '与开放银行 OpenAPI 融合,形成行内 Agent 协议规范与工具市场', sub: true, fs: 13.5 },
  ], { x: 0.9, y: 2.15, w: 7.4, h: 4.6 });
  // 右侧示意链
  const fx = 8.6;
  ['银行核心能力', '语义化工具封装', 'MCP / 工具网关', 'AI Agent 编排调用'].forEach((t, i) => {
    const y = 2.3 + i * 1.0;
    s.addShape('roundRect', { x: fx, y, w: 3.9, h: 0.78, fill: { color: i === 3 ? C.gold : C.blue } });
    s.addText(t, { x: fx, y, w: 3.9, h: 0.78, fontFace: F, color: i === 3 ? '3A2E00' : C.white, fontSize: 14, bold: true, align: 'center', valign: 'middle' });
    if (i < 3) s.addText('▼', { x: fx + 1.8, y: y + 0.78, w: 0.4, h: 0.22, fontFace: F, color: C.gray, fontSize: 12, align: 'center' });
  });
  footer(s, 9);
}

// ---------- 10. L5 体验层 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'L5 体验层:全渠道 AI 原生交互', '03 · 蓝图');
  const cols = [
    ['对私 / 零售', C.blue, ['AI 原生手机银行(对话/自然语言入口)', '千人千面:智能投顾、产品推荐、财务助手', '语音/多模态交互,降低使用门槛']],
    ['对公 / 机构', C.navy2, ['财资、支付、证券的 AI 原生产品', '自然语言查账、批量操作、智能报告', 'Deloitte 2026:机构银行最大增量来源']],
    ['内部 / 员工', C.green, ['客户经理 Copilot:客户洞察、话术、合规', '运营 Copilot:工单、审核、RPA 协同', '研发 Copilot:代码、测试、运维提效']],
  ];
  cols.forEach((c, i) => {
    const x = 0.8 + i * 4.05;
    s.addShape('roundRect', { x, y: 1.6, w: 3.8, h: 5.1, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('rect', { x, y: 1.6, w: 3.8, h: 0.75, fill: { color: c[1] } });
    s.addText(c[0], { x, y: 1.6, w: 3.8, h: 0.75, fontFace: F, color: C.white, fontSize: 17, bold: true, align: 'center', valign: 'middle' });
    s.addText(c[2].map((t) => ({ text: t, options: { bullet: { code: '25CF' }, color: C.dark, fontSize: 13.5, fontFace: F, paraSpaceAfter: 12, lineSpacingMultiple: 1.18 } })), { x: x + 0.25, y: 2.55, w: 3.3, h: 4, valign: 'top' });
  });
  footer(s, 10);
}

// ---------- 11. L6 治理与安全 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'L6 治理与安全:横向贯穿的红线工程', '05 · 治理');
  const g = [
    ['组织治理', 'AI 治理委员会 · 全行模型清单 · 责任到人', C.navy],
    ['模型风险', 'MRM 体系 · 验证 · 可解释性 · 漂移监控', C.blue],
    ['公平与偏见', '人群公平性监测 · 消费者保护合规', C.blueL],
    ['Agent 安全', '防 Prompt 注入 · 越权拦截 · 权限分级', C.gold],
    ['数据与隐私', '脱敏 · 隐私计算 · 数据不出域 · 留痕', C.green],
    ['可干预性', '人在回路 · Kill Switch · 降级回规则', C.red],
  ];
  g.forEach((it, i) => {
    const col = i % 3, row = Math.floor(i / 3);
    const x = 0.8 + col * 4.05, y = 1.6 + row * 2.55;
    s.addShape('roundRect', { x, y, w: 3.8, h: 2.25, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('rect', { x, y, w: 3.8, h: 0.7, fill: { color: it[2] } });
    s.addText(it[0], { x, y, w: 3.8, h: 0.7, fontFace: F, color: C.white, fontSize: 15.5, bold: true, align: 'center', valign: 'middle' });
    s.addText(it[1], { x: x + 0.25, y: y + 0.85, w: 3.3, h: 1.2, fontFace: F, color: C.dark, fontSize: 13.5, valign: 'top', lineSpacingMultiple: 1.2 });
  });
  footer(s, 11);
}

// ---------- 12. 监管红线 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '监管红线:2026 年必须卡死的合规约束', '05 · 治理');
  const rows = [
    [{ text: '地区/标准', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } },
     { text: '关键要求', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } }],
    ['美国 (OCC/Fed/FDIC)', '2026 修订《模型风险管理》指引,首次明确覆盖 AI/ML 模型'],
    ['欧盟', 'AI Act:金融服务相关场景属"高风险"类别'],
    ['德国 (BaFin)', '2025.12 发布 AI 相关 ICT 风险监管指引'],
    ['中国', '央行"安全稳妥推进大模型"+ 数据安全法 + 个保法 + 算法备案 + 信创'],
    ['通用框架', 'NIST AI RMF:被多国金融监管援引为基线'],
  ];
  const tb = rows.map((r) => r.map((cell) => (typeof cell === 'string'
    ? { text: cell, options: { color: C.dark, fontFace: F, fontSize: 13 } }
    : { ...cell, options: { ...cell.options, fontFace: F, fontSize: 13 } })));
  s.addTable(tb, { x: 0.8, y: 1.65, w: 11.7, colW: [3.2, 8.5], rowH: 0.75, border: { type: 'solid', pt: 0.5, color: C.light2 }, valign: 'middle' });
  s.addShape('roundRect', { x: 0.8, y: 6.3, w: 11.7, h: 0.6, fill: { color: C.amber } });
  s.addText('治理做不好,前面所有层都是空中楼阁——AI 落地的天花板是合规', { x: 0.8, y: 6.3, w: 11.7, h: 0.6, fontFace: F, color: C.white, fontSize: 14, bold: true, align: 'center', valign: 'middle' });
  footer(s, 12);
}

// ---------- 13. 横切改造对比表 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '横切改造点:传统 vs AI 原生', '03 · 蓝图');
  const data = [
    ['核心系统', '单体、紧耦合账务', '模块化解耦 · API-first · 事件驱动'],
    ['实时性', 'T+1 批处理为主', '实时流处理 · 在线风控即时决策'],
    ['接口范式', '面向人类的 REST', '面向 Agent 的语义化 / 可编排 / 可审计'],
    ['决策审计', '操作日志', '决策可追溯 · 可回放 · 可解释'],
    ['成本模型', '按交易', 'FinOps for AI · 推理成本可控'],
    ['组织人才', 'IT 部门交付', '业务 + 数据 + AI 融合的敏捷组'],
  ];
  const head = [{ text: '维度', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } },
    { text: '传统银行', options: { bold: true, color: C.white, fill: { color: C.gray }, align: 'center' } },
    { text: 'AI 原生银行', options: { bold: true, color: C.white, fill: { color: C.gold }, align: 'center' } }];
  const tb = [head, ...data.map((r) => r.map((cell, ci) => ({ text: cell, options: { color: ci === 0 ? C.navy : C.dark, bold: ci === 0, fontFace: F, fontSize: 13.5 } })))];
  s.addTable(tb, { x: 0.8, y: 1.7, w: 11.7, colW: [2.4, 4.4, 4.9], rowH: 0.72, border: { type: 'solid', pt: 0.5, color: C.light2 }, valign: 'middle' });
  footer(s, 13);
}

// ---------- 14. 落地路径四步走 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '落地路径:四步走(避免一上来动核心)', '06 · 路径');
  const steps = [
    ['第 1 步', '修地基', '统一数据 + 核心能力 API 化、语义化', C.navy, '0–6 月'],
    ['第 2 步', '低风险内部先行', '智能客服 / 知识检索 / 运营 / 研发 Copilot', C.blue, '6–12 月'],
    ['第 3 步', '深入核心业务', '风控 / 营销 / 金融市场交易,创造增量', C.gold, '12–18 月'],
    ['第 4 步', '对客 AI 原生体验', 'AI 原生手机银行 / 对公 AI 产品', C.green, '18 月+'],
  ];
  const bw = 2.85, gap = 0.18, x0 = 0.7, y = 2.2;
  steps.forEach((st, i) => {
    const x = x0 + i * (bw + gap);
    s.addShape('roundRect', { x, y, w: bw, h: 3.6, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('roundRect', { x, y, w: bw, h: 0.95, fill: { color: st[3] } });
    s.addText(st[0], { x, y: y + 0.1, w: bw, h: 0.4, fontFace: F, color: C.white, fontSize: 13, bold: true, align: 'center' });
    s.addText(st[1], { x, y: y + 0.45, w: bw, h: 0.45, fontFace: F, color: C.white, fontSize: 18, bold: true, align: 'center' });
    s.addText(st[4], { x, y: y + 1.05, w: bw, h: 0.4, fontFace: F, color: st[3], fontSize: 13, bold: true, align: 'center' });
    s.addText(st[2], { x: x + 0.2, y: y + 1.55, w: bw - 0.4, h: 1.8, fontFace: F, color: C.dark, fontSize: 13, align: 'center', valign: 'top', lineSpacingMultiple: 1.2 });
    if (i < 3) s.addText('▶', { x: x + bw - 0.02, y: y + 1.5, w: 0.25, h: 0.4, fontFace: F, color: C.gray, fontSize: 16, align: 'center' });
  });
  s.addShape('roundRect', { x: 0.8, y: 6.15, w: 11.7, h: 0.6, fill: { color: C.navy } });
  s.addText('原则:数据与 API 先行 → 内部验证 → 核心业务 → 对客体验;治理与人在回路必须先于高风险场景就位', { x: 0.8, y: 6.15, w: 11.7, h: 0.6, fontFace: F, color: C.goldL, fontSize: 13, bold: true, align: 'center', valign: 'middle' });
  footer(s, 14);
}

// ---------- 15. 预期价值 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '预期价值:从降本到增收的四维收益', '06 · 价值');
  const v = [
    ['降本增效', '客服 / 运营 / 研发自动化,人效显著提升', C.blue],
    ['创造增量', '精准营销、智能投顾、对公 AI 产品新收入', C.green],
    ['风控增强', '实时反欺诈、AML、信贷决策更准更快', C.red],
    ['体验升级', '对话式服务、千人千面、客户黏性提升', C.gold],
  ];
  v.forEach((it, i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = 0.8 + col * 6.1, y = 1.7 + row * 2.45;
    s.addShape('roundRect', { x, y, w: 5.7, h: 2.15, fill: { color: C.light }, line: { color: C.light2, width: 1 } });
    s.addShape('roundRect', { x: x + 0.25, y: y + 0.3, w: 1.1, h: 1.1, fill: { color: it[2] } });
    s.addText(['¥', '↑', '◆', '★'][i], { x: x + 0.25, y: y + 0.3, w: 1.1, h: 1.1, fontFace: F, color: C.white, fontSize: 30, bold: true, align: 'center', valign: 'middle' });
    s.addText(it[0], { x: x + 1.55, y: y + 0.4, w: 3.9, h: 0.5, fontFace: F, color: C.navy, fontSize: 18, bold: true });
    s.addText(it[1], { x: x + 1.55, y: y + 0.95, w: 3.9, h: 0.9, fontFace: F, color: C.dark, fontSize: 13.5, lineSpacingMultiple: 1.2 });
  });
  s.addText('注:具体量化指标需结合本行基线测算(客服解决率、单位获客成本、欺诈拦截率、AUM 增速等)', { x: 0.8, y: 6.65, w: 11.7, h: 0.3, fontFace: F, color: C.gray, fontSize: 11.5, italic: true });
  footer(s, 15);
}

// ---------- 16. 风险与应对 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, '风险与应对', '06 · 路径');
  const data = [
    ['合规', '模型不可解释、监管处罚', 'MRM 体系 · 可解释性 · 模型清单'],
    ['数据', '质量差、隐私泄露、数据孤岛', '统一治理 · 脱敏/隐私计算 · 不出域'],
    ['安全', 'Prompt 注入、Agent 越权', '意图校验 · 分级授权 · HITL · Kill Switch'],
    ['成本', 'LLM 推理成本失控', '模型路由 · 缓存 · FinOps 监控'],
    ['组织', '缺复合人才、落地慢', '内部平台 + 低代码 · 外部合作 · 培训'],
  ];
  const head = [{ text: '类别', options: { bold: true, color: C.white, fill: { color: C.red }, align: 'center' } },
    { text: '具体风险', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } },
    { text: '应对措施', options: { bold: true, color: C.white, fill: { color: C.green }, align: 'center' } }];
  const tb = [head, ...data.map((r) => r.map((cell, ci) => ({ text: cell, options: { color: ci === 0 ? C.red : C.dark, bold: ci === 0, fontFace: F, fontSize: 13.5 } })))];
  s.addTable(tb, { x: 0.8, y: 1.75, w: 11.7, colW: [1.8, 4.8, 5.1], rowH: 0.78, border: { type: 'solid', pt: 0.5, color: C.light2 }, valign: 'middle' });
  footer(s, 16);
}

// ---------- 17. Roadmap ----------
{
  const s = pptx.addSlide(); s.background = { color: C.white };
  header(s, 'Roadmap:12–18 个月分阶段里程碑', '06 · 路径');
  const data = [
    ['阶段一 · 地基', '0–6 月', '数据统一 + 核心 API 化 + AI 治理委员会', '客户 360、语义 API 规范、治理章程'],
    ['阶段二 · 内部', '6–12 月', '智能客服 / 知识检索 / 运营 / 研发 Copilot', 'MaaS 平台、3–5 场景上线'],
    ['阶段三 · 核心', '12–18 月', '风控 / 营销 / 金融市场交易', '大+微模型协同、产生增量收益'],
    ['阶段四 · 对客', '18 月+', 'AI 原生手机银行 / 对公 AI 产品', '全渠道、HITL 成熟、规模化'],
  ];
  const head = [{ text: '阶段', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } },
    { text: '时间', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } },
    { text: '重点', options: { bold: true, color: C.white, fill: { color: C.navy }, align: 'center' } },
    { text: '关键里程碑', options: { bold: true, color: C.white, fill: { color: C.gold }, align: 'center' } }];
  const tb = [head, ...data.map((r) => r.map((cell, ci) => ({ text: cell, options: { color: ci === 0 ? C.navy : C.dark, bold: ci === 0 || ci === 3, fontFace: F, fontSize: 13 } })))];
  s.addTable(tb, { x: 0.8, y: 1.7, w: 11.7, colW: [2.4, 1.7, 4.6, 3.0], rowH: 0.95, border: { type: 'solid', pt: 0.5, color: C.light2 }, valign: 'middle' });
  s.addShape('roundRect', { x: 0.8, y: 6.2, w: 11.7, h: 0.6, fill: { color: C.gold } });
  s.addText('下一步:启动 L4 工具网关 + MCP 化试点,以一个核心场景(如智能风控审批)跑通闭环', { x: 0.8, y: 6.2, w: 11.7, h: 0.6, fontFace: F, color: '3A2E00', fontSize: 13.5, bold: true, align: 'center', valign: 'middle' });
  footer(s, 17);
}

// ---------- 18. 封底 ----------
{
  const s = pptx.addSlide(); s.background = { color: C.navy };
  s.addShape('rect', { x: 0, y: 0, w: 0.35, h: 7.5, fill: { color: C.gold } });
  s.addText('让银行的每一种能力,', { x: 0.9, y: 2.7, w: 11.5, h: 0.9, fontFace: F, color: C.white, fontSize: 34, bold: true });
  s.addText('都能被 AI 安全、智能地调用。', { x: 0.9, y: 3.5, w: 11.5, h: 0.9, fontFace: F, color: C.goldL, fontSize: 34, bold: true });
  s.addShape('rect', { x: 0.95, y: 4.7, w: 2.2, h: 0.06, fill: { color: C.gold } });
  s.addText('Thanks · 欢迎讨论', { x: 0.9, y: 4.9, w: 11.5, h: 0.5, fontFace: F, color: C.blueL, fontSize: 18 });
  s.addText('AllCLI · AI-Native Banking Initiative   |   2026', { x: 0.9, y: 6.6, w: 11.5, h: 0.4, fontFace: F, color: C.gray, fontSize: 12 });
}

(async () => {
  const file = '银行AI原生改造路线图.pptx';
  await pptx.writeFile({ fileName: file });
  console.log('OK -> ' + file);
})();
