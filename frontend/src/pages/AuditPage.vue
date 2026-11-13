<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { exportAuditCsv, listAudit } from '@/api/admin'
import { usePage } from '@/composables/usePage'
import StatusTag from '@/components/StatusTag.vue'
import type { AuditEntry, Reversibility } from '@/types/api'
import { downloadBlob, fmtTime, toastError, toLocalIso } from '@/utils/format'

const filters = reactive({
  agentId: '',
  toolId: '',
  reversibility: '' as '' | Reversibility,
  range: null as [Date, Date] | null,
})

function queryParams() {
  const [from, to] = filters.range ?? [null, null]
  return {
    agentId: filters.agentId || undefined,
    toolId: filters.toolId || undefined,
    reversibility: filters.reversibility || undefined,
    // LocalDateTime 绑定：本地 ISO，不带时区后缀
    from: toLocalIso(from),
    to: toLocalIso(to),
  }
}

const { items, total, loading, currentPage, pageSize, load, search } = usePage<AuditEntry, ReturnType<typeof queryParams>>(
  (p) => listAudit({ ...queryParams(), page: p.page, size: p.size }).catch((e) => {
    toastError(e)
    throw e
  }),
  queryParams(),
)
onMounted(load)

const exporting = ref(false)

function copyTrace(t: string) {
  void navigator.clipboard.writeText(t)
  ElMessage.success('traceId 已复制')
}

async function exportCsv() {
  exporting.value = true
  try {
    const resp = await exportAuditCsv(queryParams())
    downloadBlob(resp.data, `audit-${Date.now()}.csv`)
    ElMessage.success('已导出（上限 1000 行）')
  } catch (e) {
    toastError(e)
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div>
    <el-form inline @submit.prevent="search">
      <el-form-item label="Agent">
        <el-input v-model="filters.agentId" placeholder="agentId" clearable style="width: 150px" @keyup.enter="search" @clear="search" />
      </el-form-item>
      <el-form-item label="工具">
        <el-input v-model="filters.toolId" placeholder="toolId" clearable style="width: 130px" @keyup.enter="search" @clear="search" />
      </el-form-item>
      <el-form-item label="可逆性">
        <el-select v-model="filters.reversibility" clearable placeholder="全部" style="width: 130px" @change="search">
          <el-option v-for="s in ['readonly', 'reversible', 'irreversible']" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker v-model="filters.range" type="datetimerange" range-separator="至"
                        start-placeholder="开始" end-placeholder="结束" style="width: 360px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button :loading="exporting" @click="exportCsv">导出 CSV</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="items" border stripe>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="invocationId" label="invocationId" min-width="170" show-overflow-tooltip />
      <el-table-column label="traceId" min-width="180">
        <template #default="{ row }">
          <el-tooltip :content="row.traceId" placement="top">
            <el-link style="font-family: monospace; font-size: 12px" @click="copyTrace(row.traceId)">{{ row.traceId.slice(0, 16) }}…</el-link>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="agentId" label="Agent" width="120" />
      <el-table-column prop="toolId" label="工具" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><StatusTag kind="audit" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时(ms)" width="90" />
      <el-table-column prop="errorCode" label="错误码" width="140" show-overflow-tooltip />
      <el-table-column prop="approvalId" label="审批单" min-width="160" show-overflow-tooltip />
    </el-table>

    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      class="pager"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[20, 50, 100]"
      :total="total"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
