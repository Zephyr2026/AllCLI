<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listApprovals } from '@/api/admin'
import { decideHitl } from '@/api/invoke'
import { getTool } from '@/api/tools'
import { usePage } from '@/composables/usePage'
import JsonPretty from '@/components/JsonPretty.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { ApprovalEntry, InvokeData } from '@/types/api'
import { fmtTime, toastError } from '@/utils/format'

const { items, total, loading, currentPage, pageSize, load } = usePage<ApprovalEntry, object>(
  (p) => listApprovals({ decision: 'pending', ...p }).catch((e) => {
    toastError(e)
    throw e
  }),
  {},
)
onMounted(load)

// ===== 审批 dialog =====
const visible = ref(false)
const current = ref<ApprovalEntry | null>(null)
const form = reactive({ decision: 'approved' as 'approved' | 'rejected', reason: '', confirmIrreversible: false })
const irreversible = ref(false)
const deciding = ref(false)
const finalResult = ref<InvokeData | null>(null)

async function open(row: ApprovalEntry) {
  current.value = row
  form.decision = 'approved'
  form.reason = ''
  form.confirmIrreversible = false
  finalResult.value = null
  irreversible.value = false
  // 拉工具详情判断是否不可逆（决定是否出示二次确认）
  try {
    const t = await getTool(row.toolId)
    irreversible.value = t.reversibility === 'irreversible'
  } catch { /* 拉不到详情时保守出示确认框 */ }
  visible.value = true
}

async function decide() {
  const row = current.value!
  if (irreversible.value && form.decision === 'approved' && !form.confirmIrreversible) {
    ElMessage.warning('不可逆工具批准时必须勾选二次确认')
    return
  }
  deciding.value = true
  try {
    finalResult.value = await decideHitl(row.approvalId, {
      decision: form.decision,
      reason: form.reason || undefined,
      confirmIrreversible: form.decision === 'approved' ? form.confirmIrreversible : undefined,
    }, row.approverRef)
    ElMessage.success(`审批完成：${finalResult.value.status}`)
    await load()
  } catch (e) {
    toastError(e)
  } finally {
    deciding.value = false
  }
}
</script>

<template>
  <div>
    <el-table v-loading="loading" :data="items" border stripe>
      <el-table-column prop="approvalId" label="approvalId" min-width="180" show-overflow-tooltip />
      <el-table-column prop="invocationId" label="invocationId" min-width="170" show-overflow-tooltip />
      <el-table-column label="toolId" min-width="120">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/tools/${row.toolId}`)">{{ row.toolId }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="agentId" label="发起 Agent" width="130" />
      <el-table-column label="审批人" width="120">
        <template #default="{ row }">
          <el-tag size="small" type="warning">{{ row.approverRef }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发起时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="open(row)">审批</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="load"
    />

    <el-dialog v-model="visible" :title="`HITL 审批：${current?.approvalId ?? ''}`" width="480px">
      <el-alert type="info" :closable="false" class="mb" show-icon
                :title="`将以 agent_${current?.approverRef ?? ''} 身份提交（后端校验审批人与该单 approverRef 等值）`" />
      <el-descriptions v-if="current" :column="1" border size="small" class="mb">
        <el-descriptions-item label="工具">{{ current.toolId }}（发起：{{ current.agentId }}）</el-descriptions-item>
        <el-descriptions-item label="调用">{{ current.invocationId }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="决定">
          <el-radio-group v-model="form.decision">
            <el-radio value="approved">批准</el-radio>
            <el-radio value="rejected">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="form.reason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="form.decision === 'approved' && irreversible">
          <el-checkbox v-model="form.confirmIrreversible">我已知晓该操作<b>不可逆</b>（必勾）</el-checkbox>
        </el-form-item>
      </el-form>
      <template v-if="finalResult">
        <el-divider />
        <p class="sub">审批后终态：<StatusTag kind="audit" :value="finalResult.status" /></p>
        <JsonPretty :data="finalResult.result ?? '（无返回体）'" />
      </template>
      <template #footer>
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" :loading="deciding" :disabled="!!finalResult" @click="decide">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager { margin-top: 14px; justify-content: flex-end; }
.mb { margin-bottom: 12px; }
.sub { font-size: 13px; margin: 6px 0; }
</style>
