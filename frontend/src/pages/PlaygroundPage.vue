<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTool, listTools } from '@/api/tools'
import { decideHitl, invoke } from '@/api/invoke'
import { ApiError } from '@/api/http'
import JsonPretty from '@/components/JsonPretty.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { InvokeData, ToolDetail, ToolSummary } from '@/types/api'
import { toastError } from '@/utils/format'

const route = useRoute()

const toolId = ref<string>((route.query.toolId as string) ?? '')
const options = ref<ToolSummary[]>([])
const searching = ref(false)
const tool = ref<ToolDetail | null>(null)

const version = ref<string>('')
const argsText = ref('{}')
const argsError = ref('')
const idempotencyKey = ref('')
const invoking = ref(false)
const result = ref<InvokeData | null>(null)

async function searchTools(q: string) {
  searching.value = true
  try {
    const resp = await listTools({ q: q || undefined, page: 0, size: 20 })
    options.value = resp.items
  } catch (e) {
    toastError(e)
  } finally {
    searching.value = false
  }
}

async function onToolChange(id: string) {
  result.value = null
  argsError.value = ''
  tool.value = null
  if (!id) return
  try {
    tool.value = await getTool(id)
    version.value = ''
    // 预填第一个示例参数
    argsText.value = tool.value.examples?.length
      ? JSON.stringify(tool.value.examples[0], null, 2)
      : '{}'
  } catch (e) {
    toastError(e)
  }
}

const parsedArgs = computed<Record<string, unknown> | null>(() => {
  if (!argsText.value.trim()) return {}
  try {
    const v: unknown = JSON.parse(argsText.value)
    if (typeof v !== 'object' || v === null || Array.isArray(v)) return null
    return v as Record<string, unknown>
  } catch {
    return null
  }
})

function validateArgs() {
  argsError.value = parsedArgs.value == null ? (argsText.value.trim() ? 'JSON 格式错误或不是对象' : '') : ''
}

async function doInvoke() {
  validateArgs()
  if (argsError.value) {
    ElMessage.warning('请先修正 arguments')
    return
  }
  invoking.value = true
  try {
    result.value = await invoke({
      toolName: toolId.value,
      version: version.value || undefined,
      arguments: parsedArgs.value!,
      idempotencyKey: idempotencyKey.value || undefined,
    })
    if (result.value.status === 'succeeded') ElMessage.success('调用成功')
  } catch (e) {
    if (e instanceof ApiError && e.code === 'IDEMPOTENT_REPLAY') {
      // 幂等命中以错误信封返回：转成重放结果展示
      const m = /invocationId=([A-Za-z0-9_]+)/.exec(e.message)
      result.value = { status: 'replayed', invocationId: m?.[1] }
      ElMessage.info('幂等命中：5 分钟窗口内同参数写操作已重放首次结果')
    } else {
      toastError(e)
    }
  } finally {
    invoking.value = false
  }
}

// ===== 内联 HITL 审批 =====
const decisionVisible = ref(false)
const decisionForm = reactive({
  decision: 'approved' as 'approved' | 'rejected',
  reason: '',
  confirmIrreversible: false,
})
const deciding = ref(false)

function openDecision() {
  decisionForm.decision = 'approved'
  decisionForm.reason = ''
  decisionForm.confirmIrreversible = false
  decisionVisible.value = true
}

async function doDecide() {
  const r = result.value!
  if (tool.value?.reversibility === 'irreversible' && decisionForm.decision === 'approved'
      && !decisionForm.confirmIrreversible) {
    ElMessage.warning('不可逆工具批准时必须勾选二次确认')
    return
  }
  deciding.value = true
  try {
    // 后端校验审批人 == approverRef，须以该身份提交
    const final = await decideHitl(r.approvalId!, {
      decision: decisionForm.decision,
      reason: decisionForm.reason || undefined,
      confirmIrreversible: decisionForm.decision === 'approved' ? decisionForm.confirmIrreversible : undefined,
    }, tool.value?.hitl?.approverRef ?? '')
    ElMessage.success(`审批完成：${final.status}`)
    decisionVisible.value = false
    result.value = final
  } catch (e) {
    toastError(e)
  } finally {
    deciding.value = false
  }
}

onMounted(() => {
  void searchTools('')
  if (toolId.value) void onToolChange(toolId.value)
})
</script>

<template>
  <div class="pg">
    <el-card shadow="never">
      <template #header>调用测试（Playground）</template>
      <el-form label-width="110px">
        <el-form-item label="选择工具">
          <el-select v-model="toolId" filterable remote :remote-method="searchTools" :loading="searching"
                     placeholder="搜索 toolId / 名称" style="width: 360px" @change="onToolChange">
            <el-option v-for="t in options" :key="t.toolId" :value="t.toolId" :label="`${t.toolId}（${t.name}）`" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="tool" label="工具信息">
          <StatusTag kind="tool" :value="tool.status" />
          <StatusTag kind="tool" :value="tool.sideEffect" />
          <StatusTag v-if="tool.reversibility" kind="tool" :value="tool.reversibility" />
          <el-tag v-if="tool.hitlEnabled" size="small" type="warning">HITL：{{ tool.hitl?.approverRef || '未指定审批人' }}</el-tag>
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="version" placeholder="留空=最新（如 1.0.0）" style="width: 180px" />
        </el-form-item>
        <el-form-item label="arguments">
          <el-input v-model="argsText" type="textarea" :rows="8" class="mono" @input="validateArgs" />
          <div v-if="argsError" class="err">{{ argsError }}</div>
        </el-form-item>
        <el-form-item label="幂等键">
          <el-input v-model="idempotencyKey" placeholder="可选；写操作同参数 5 分钟窗口内重放" style="width: 280px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="invoking" :disabled="!toolId" @click="doInvoke">调用</el-button>
        </el-form-item>
      </el-form>

      <el-collapse v-if="tool?.inputSchema" class="schema">
        <el-collapse-item title="inputSchema 参考">
          <JsonPretty :data="tool.inputSchema" />
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-card v-if="result" shadow="never" class="result">
      <template #header>
        调用结果：
        <StatusTag kind="audit" :value="result.status" />
      </template>
      <el-descriptions v-if="result.invocationId" :column="2" border size="small" class="mb">
        <el-descriptions-item label="invocationId">{{ result.invocationId }}</el-descriptions-item>
        <el-descriptions-item v-if="result.approvalId" label="approvalId">{{ result.approvalId }}</el-descriptions-item>
        <el-descriptions-item v-if="result.expiresAt" label="审批截止">{{ result.expiresAt }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="result.status === 'pending_approval'">
        <el-alert type="warning" :closable="false" show-icon class="mb"
                  title="该操作为写操作，已挂起等待人工审批（HITL）" />
        <el-button type="warning" @click="openDecision"> inline 审批</el-button>
      </template>
      <template v-else-if="result.status === 'replayed'">
        <el-alert type="info" :closable="false" show-icon class="mb"
                  title="幂等命中：5 分钟窗口内同幂等键调用已重放既有结果" />
      </template>
      <p class="sub">result：</p>
      <JsonPretty :data="result.result ?? '（无）'" />
    </el-card>

    <!-- 内联 HITL 审批 -->
    <el-dialog v-model="decisionVisible" title="HITL 审批" width="460px">
      <el-alert v-if="tool" type="info" :closable="false" class="mb"
                :title="`将以 agent_${tool.hitl?.approverRef ?? ''} 身份提交（后端校验审批人等值）`" show-icon />
      <el-form label-width="110px">
        <el-form-item label="决定">
          <el-radio-group v-model="decisionForm.decision">
            <el-radio value="approved">批准</el-radio>
            <el-radio value="rejected">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="decisionForm.reason" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-if="decisionForm.decision === 'approved' && tool?.reversibility === 'irreversible'">
          <el-checkbox v-model="decisionForm.confirmIrreversible">我已知晓该操作不可逆（必勾）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button type="primary" :loading="deciding" @click="doDecide">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pg { max-width: 900px; }
.result { margin-top: 14px; }
.mono :deep(textarea) { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.hint { margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary); }
.err { color: var(--el-color-danger); font-size: 12px; }
.schema { margin-bottom: 8px; }
.mb { margin-bottom: 12px; }
.sub { margin: 10px 0 6px; font-size: 13px; color: var(--el-text-color-secondary); }
</style>
