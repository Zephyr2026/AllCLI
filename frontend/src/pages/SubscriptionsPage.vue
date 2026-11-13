<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approveSubscription, createSubscription, listSubscriptions, rejectSubscription, revokeSubscription } from '@/api/subscriptions'
import { usePage } from '@/composables/usePage'
import StatusTag from '@/components/StatusTag.vue'
import type { SubscriptionResponse } from '@/types/api'
import { toastError } from '@/utils/format'

const filters = reactive({ status: '' })
const { items, total, loading, currentPage, pageSize, load, search } = usePage<SubscriptionResponse, { status?: string }>(
  (p) => listSubscriptions({ status: filters.status || undefined, page: p.page, size: p.size }).catch((e) => {
    toastError(e)
    throw e
  }),
  {},
)
onMounted(load)

async function approve(row: SubscriptionResponse) {
  try {
    await approveSubscription(row.subId)
    ElMessage.success('已批准')
    await load()
  } catch (e) {
    toastError(e)
  }
}

async function reject(row: SubscriptionResponse) {
  try {
    await rejectSubscription(row.subId)
    ElMessage.success('已拒绝')
    await load()
  } catch (e) {
    toastError(e)
  }
}

async function revoke(row: SubscriptionResponse) {
  try {
    await ElMessageBox.confirm(`确认吊销 ${row.agentId} 对 ${row.toolId} 的订阅？吊销后其调用立即被阻断。`, '吊销（Kill Switch）', { type: 'warning' })
  } catch {
    return
  }
  try {
    await revokeSubscription(row.subId)
    ElMessage.success('已吊销')
    await load()
  } catch (e) {
    toastError(e)
  }
}

// ===== 新建申请 =====
const createVisible = ref(false)
const createForm = reactive({ agentId: '', toolId: '', scope: '' })
const creating = ref(false)

async function doCreate() {
  if (!createForm.agentId || !createForm.toolId) {
    ElMessage.warning('agentId 与 toolId 必填')
    return
  }
  creating.value = true
  try {
    const resp = await createSubscription({
      agentId: createForm.agentId,
      toolId: createForm.toolId,
      scope: createForm.scope || undefined,
    })
    ElMessage.success(`申请已创建（${resp.subId}，状态 ${resp.status}）`)
    createVisible.value = false
    await load()
  } catch (e) {
    toastError(e)
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <div>
    <div class="toolbar">
      <el-select v-model="filters.status" clearable placeholder="全部状态" style="width: 140px" @change="search">
        <el-option v-for="s in ['pending', 'approved', 'rejected', 'revoked']" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button type="primary" @click="createVisible = true">新建订阅申请</el-button>
    </div>

    <el-table v-loading="loading" :data="items" border stripe>
      <el-table-column prop="subId" label="subId" min-width="180" show-overflow-tooltip />
      <el-table-column prop="agentId" label="Agent" width="130" />
      <el-table-column label="工具" min-width="120">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/tools/${row.toolId}`)">{{ row.toolId }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="scope" label="scope" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag kind="sub" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="approvedBy" label="审批人" width="110" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'pending'">
            <el-tooltip content="以默认 Agent 身份执行（一期无 RBAC）" placement="top">
              <el-button size="small" type="success" @click="approve(row)">批准</el-button>
            </el-tooltip>
            <el-button size="small" type="danger" plain @click="reject(row)">拒绝</el-button>
          </template>
          <el-button v-if="row.status === 'approved'" size="small" type="danger" @click="revoke(row)">吊销</el-button>
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

    <el-dialog v-model="createVisible" title="新建订阅申请" width="440px">
      <el-form label-width="80px">
        <el-form-item label="Agent ID" required>
          <el-input v-model="createForm.agentId" placeholder="如 agent_demo" />
        </el-form-item>
        <el-form-item label="toolId" required>
          <el-input v-model="createForm.toolId" placeholder="如 listPets" />
        </el-form-item>
        <el-form-item label="scope">
          <el-input v-model="createForm.scope" placeholder="授权范围（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; justify-content: space-between; margin-bottom: 12px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
