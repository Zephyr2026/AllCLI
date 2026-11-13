<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMetrics } from '@/api/admin'
import StatusTag from '@/components/StatusTag.vue'
import type { MetricsResponse } from '@/types/api'
import { ms, pct, toastError } from '@/utils/format'

const router = useRouter()
const metrics = ref<MetricsResponse | null>(null)
const loading = ref(false)
const autoRefresh = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try {
    metrics.value = await getMetrics()
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

function toggleAuto(v: boolean | string | number) {
  if (timer) clearInterval(timer)
  timer = null
  if (v) timer = setInterval(load, 30_000)
}

onMounted(load)
onUnmounted(() => toggleAuto(false))
</script>

<template>
  <div v-loading="loading">
    <div class="toolbar">
      <el-switch v-model="autoRefresh" active-text="30s 自动刷新" @change="toggleAuto" />
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <template v-if="metrics">
      <el-row :gutter="14">
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">接入系统</div><div class="v">{{ metrics.systemCount }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">工具总数</div><div class="v">{{ metrics.tools.total }}</div>
          <div class="sub">已发布 {{ metrics.tools.published }} · 灰度 {{ metrics.tools.gray }} · 草案 {{ metrics.tools.draft }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">今日调用</div><div class="v">{{ metrics.invocations.today.total ?? '—' }}</div>
          <div class="sub">成功 {{ metrics.invocations.today.success ?? 0 }} · 失败 {{ metrics.invocations.today.fail ?? 0 }} · 待审批 {{ metrics.invocations.today.pendingApproval ?? 0 }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">HITL 拦截率(今日)</div><div class="v">{{ pct(metrics.invocations.today.hitlRate) }}</div>
          <div class="sub">近7日 {{ pct(metrics.invocations.last7d.hitlRate) }}</div></el-card></el-col>
      </el-row>
      <el-row :gutter="14" class="row2">
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">平均耗时(今日)</div><div class="v">{{ ms(metrics.invocations.today.avgDurationMs) }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">复用率</div><div class="v">{{ pct(metrics.reuse.reuseRate) }}</div>
          <div class="sub">复用工具 {{ metrics.reuse.reusedTools }} / 已使用 {{ metrics.reuse.usedTools }} / 总数 {{ metrics.reuse.totalTools }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">近 1 分钟 QPS</div><div class="v">{{ metrics.invocations.last1m.qps }}</div>
          <div class="sub">计数 {{ metrics.invocations.last1m.count }}</div></el-card></el-col>
        <el-col :span="6"><el-card shadow="never" class="tile"><div class="k">近 7 日调用</div><div class="v">{{ metrics.invocations.last7d.total ?? '—' }}</div>
          <div class="sub">成功 {{ metrics.invocations.last7d.success ?? 0 }} · 失败 {{ metrics.invocations.last7d.fail ?? 0 }}</div></el-card></el-col>
      </el-row>

      <el-card shadow="never" class="block">
        <template #header>工具状态分布</template>
        <div class="status-row">
          <template v-for="s in ['draft', 'reviewing', 'published', 'gray', 'deprecated']" :key="s">
            <StatusTag kind="tool" :value="s" />
            <span class="num">{{ metrics.tools[s as keyof typeof metrics.tools] }}</span>
          </template>
        </div>
      </el-card>

      <el-card shadow="never" class="block">
        <template #header>高复用工具</template>
        <template v-if="metrics.reuse.reusedToolIds.length">
          <el-tag v-for="id in metrics.reuse.reusedToolIds" :key="id" class="tool-tag" type="success" effect="plain"
                  style="cursor: pointer" @click="router.push(`/tools/${id}`)">{{ id }}</el-tag>
        </template>
        <span v-else class="dim">暂无被多个 Agent 复用的工具</span>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; gap: 12px; margin-bottom: 12px; align-items: center; }
.tile .k { font-size: 13px; color: var(--el-text-color-secondary); }
.tile .v { font-size: 28px; font-weight: 600; margin: 6px 0 2px; }
.tile .sub { font-size: 12px; color: var(--el-text-color-secondary); }
.row2 { margin-top: 14px; }
.block { margin-top: 14px; }
.status-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.status-row .num { font-size: 18px; font-weight: 600; margin-right: 14px; }
.tool-tag { margin: 0 8px 8px 0; }
.dim { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
