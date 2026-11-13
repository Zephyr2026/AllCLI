<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deprecateTool, getTool, publishTool, runQuality, runSandbox, updateToolSemantic } from '@/api/tools'
import JsonPretty from '@/components/JsonPretty.vue'
import StatusTag from '@/components/StatusTag.vue'
import type { QualityReport, SandboxResponse, SemanticUpdateRequest, ToolDetail } from '@/types/api'
import { toastError } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const toolId = route.params.toolId as string

const tool = ref<ToolDetail | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    tool.value = await getTool(toolId)
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const canPublish = computed(() => ['draft', 'reviewing'].includes(tool.value?.status ?? ''))
const isDeprecated = computed(() => tool.value?.status === 'deprecated')
const isLive = computed(() => ['published', 'gray'].includes(tool.value?.status ?? ''))
const isIrreversible = computed(() => tool.value?.reversibility === 'irreversible')

// ===== 发布 =====
const publishVisible = ref(false)
const publishForm = reactive({ target: 'published' as 'published' | 'gray', grayPercent: 50, changelog: '' })

function openPublish() {
  publishForm.target = 'published'
  publishForm.grayPercent = 50
  publishForm.changelog = ''
  publishVisible.value = true
}

async function doPublish() {
  if (publishForm.target === 'gray' && (publishForm.grayPercent < 1 || publishForm.grayPercent > 100)) {
    ElMessage.warning('灰度比例须为 1-100')
    return
  }
  try {
    const resp = await publishTool(toolId, {
      target: publishForm.target,
      grayPercent: publishForm.target === 'gray' ? publishForm.grayPercent : undefined,
      changelog: publishForm.changelog || undefined,
    })
    ElMessage.success(`已发布：v${resp.version} ${resp.status}${resp.snapshotId != null ? `（快照 ${resp.snapshotId}）` : ''}`)
    publishVisible.value = false
    await load()
  } catch (e) {
    toastError(e)
  }
}

async function doDeprecate() {
  try {
    await ElMessageBox.confirm(`确认下线工具 ${toolId}？下线为终态，不可再编辑/发布。`, '下线确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deprecateTool(toolId)
    ElMessage.success('已下线')
    await load()
  } catch (e) {
    toastError(e)
  }
}

// ===== 语义编辑 =====
const WARNING_TEXT: Record<string, string> = {
  HITL_CLEARED_FOR_READONLY: '只读工具已自动清除 HITL 配置',
  WRITE_MISSING_REVERSIBILITY: '写操作缺少可逆性声明（缺 reversibility 时无法发布）',
  WRITE_WITHOUT_HITL: '写操作未开启 HITL 人工确认',
  IRREVERSIBLE_WITHOUT_HITL: '不可逆操作未开启 HITL，风险较高',
  TOOL_REVERTED_TO_DRAFT: '治理字段变更，工具已回退为 draft 并自动升版',
  VERSION_NOT_AUTO_BUMPED: '版本未自动递增',
}

const editVisible = ref(false)
const editForm = reactive({
  description: '',
  requiredPermission: [] as string[],
  sideEffect: '' as '',
  reversibility: '' as '',
  idempotencyKey: '',
  hitlEnabled: false,
  approverType: '',
  approverRef: '',
  timeoutSec: 300,
})
const editWarnings = ref<string[]>([])

function openEdit() {
  const t = tool.value!
  editForm.description = t.description ?? ''
  editForm.requiredPermission = t.requiredPermission ? [...t.requiredPermission] : []
  editForm.sideEffect = (t.sideEffect ?? '') as never
  editForm.reversibility = (t.reversibility ?? '') as never
  editForm.idempotencyKey = t.idempotencyKey ?? ''
  editForm.hitlEnabled = t.hitl?.enabled ?? false
  editForm.approverType = t.hitl?.approverType ?? ''
  editForm.approverRef = t.hitl?.approverRef ?? ''
  editForm.timeoutSec = t.hitl?.timeoutSec ?? 300
  editWarnings.value = []
  editVisible.value = true
}

async function doEdit() {
  const t = tool.value!
  const body: SemanticUpdateRequest = { description: editForm.description || undefined }
  // published/gray 仅允许改 description（后端规则：改治理字段会回退 draft）
  if (!isLive.value) {
    body.sideEffect = (editForm.sideEffect || undefined) as never
    body.reversibility = (editForm.reversibility || undefined) as never
    body.requiredPermission = editForm.requiredPermission.length ? editForm.requiredPermission : undefined
    body.idempotencyKey = editForm.idempotencyKey || undefined
    if (editForm.hitlEnabled) {
      body.hitl = {
        enabled: true,
        approverType: editForm.approverType || undefined,
        approverRef: editForm.approverRef || undefined,
        timeoutSec: editForm.timeoutSec,
      }
    }
  }
  try {
    const resp = await updateToolSemantic(toolId, body)
    editWarnings.value = resp.warnings ?? []
    ElMessage.success(`已保存（v${resp.version} ${resp.status}）`)
    await load()
    if (!editWarnings.value.length) editVisible.value = false
  } catch (e) {
    toastError(e)
  }
}

// ===== 质量自检 =====
const quality = ref<QualityReport | null>(null)
const qualityLoading = ref(false)

async function doQuality() {
  qualityLoading.value = true
  try {
    quality.value = await runQuality(toolId)
  } catch (e) {
    toastError(e)
  } finally {
    qualityLoading.value = false
  }
}

// ===== 沙箱试运行 =====
const sandboxVisible = ref(false)
const sandboxArgs = ref('')
const sandboxResult = ref<SandboxResponse | null>(null)
const sandboxLoading = ref(false)
const argsError = ref('')

function validateArgs() {
  argsError.value = ''
  if (!sandboxArgs.value.trim()) return
  try {
    const parsed: unknown = JSON.parse(sandboxArgs.value)
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      argsError.value = 'arguments 须为 JSON 对象 {}'
    }
  } catch {
    argsError.value = 'JSON 格式错误'
  }
}

function openSandbox() {
  sandboxArgs.value = ''
  sandboxResult.value = null
  sandboxVisible.value = true
}

async function doSandbox() {
  validateArgs()
  if (argsError.value) return
  sandboxLoading.value = true
  try {
    const body = sandboxArgs.value.trim()
      ? { arguments: JSON.parse(sandboxArgs.value) as Record<string, unknown> }
      : undefined
    sandboxResult.value = await runSandbox(toolId, body)
  } catch (e) {
    toastError(e)
  } finally {
    sandboxLoading.value = false
  }
}
</script>

<template>
  <div v-loading="loading">
    <template v-if="tool">
      <div class="head">
        <div class="info">
          <h2 class="name">{{ tool.name }}</h2>
          <div class="meta">
            <code class="tool-id">{{ tool.toolId }}</code>
            <StatusTag kind="tool" :value="tool.status" />
            <span>v{{ tool.version }}</span>
            <el-divider direction="vertical" />
            <span>系统：{{ tool.systemRef }}</span>
            <span>Owner：{{ tool.owner || '—' }}</span>
            <StatusTag kind="tool" :value="tool.sideEffect" />
            <StatusTag v-if="tool.reversibility" kind="tool" :value="tool.reversibility" />
            <el-tag v-if="tool.hitlEnabled" size="small" type="warning">HITL</el-tag>
          </div>
          <p class="desc">{{ tool.description || '（无描述）' }}</p>
        </div>
        <div class="actions">
          <el-button @click="openEdit" :disabled="isDeprecated">编辑语义</el-button>
          <el-button type="primary" :disabled="!canPublish" @click="openPublish">发布</el-button>
          <el-button :disabled="isDeprecated" @click="doDeprecate">下线</el-button>
          <el-divider direction="vertical" />
          <el-button :loading="qualityLoading" @click="doQuality">质量自检</el-button>
          <el-button @click="openSandbox">沙箱试运行</el-button>
          <el-button type="success" plain @click="router.push(`/playground?toolId=${tool.toolId}`)">去调用</el-button>
        </div>
      </div>

      <el-alert v-if="isDeprecated" type="warning" :closable="false" show-icon class="block"
                title="该工具已下线（终态）：不可编辑、不可发布" />

      <el-card class="block" shadow="never">
        <template #header>输入 / 输出 Schema</template>
        <el-collapse>
          <el-collapse-item title="inputSchema" name="in">
            <JsonPretty :data="tool.inputSchema" />
          </el-collapse-item>
          <el-collapse-item title="outputSchema" name="out">
            <JsonPretty :data="tool.outputSchema" />
          </el-collapse-item>
          <el-collapse-item :title="`examples（${tool.examples?.length ?? 0}）`" name="ex">
            <JsonPretty :data="tool.examples" />
          </el-collapse-item>
          <el-collapse-item v-if="tool.hitl" title="HITL 策略" name="hitl">
            <JsonPretty :data="tool.hitl" />
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <el-card v-if="quality" class="block" shadow="never">
        <template #header>
          质量自检：
          <StatusTag kind="quality" :value="quality.overall" />
        </template>
        <el-table :data="quality.items" border size="small">
          <el-table-column prop="item" label="检查项" width="140" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }"><StatusTag kind="quality" :value="row.status" /></template>
          </el-table-column>
          <el-table-column prop="detail" label="说明" />
        </el-table>
      </el-card>
    </template>

    <!-- 发布 dialog -->
    <el-dialog v-model="publishVisible" title="发布工具" width="460px">
      <el-form label-width="90px">
        <el-form-item label="目标">
          <el-radio-group v-model="publishForm.target">
            <el-radio value="published">正式发布</el-radio>
            <el-radio value="gray">灰度发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="publishForm.target === 'gray'" label="灰度比例" required>
          <el-input-number v-model="publishForm.grayPercent" :min="1" :max="100" /> %
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="publishForm.changelog" type="textarea" :rows="2" placeholder="本次发布变更内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" @click="doPublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 语义编辑 dialog -->
    <el-dialog v-model="editVisible" title="编辑语义" width="620px">
      <el-alert v-if="isLive" type="info" :closable="false" class="mb"
                title="published / gray 状态仅可修改描述；修改治理字段会使工具回退为 draft（后端规则）" show-icon />
      <el-form label-width="120px">
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <template v-if="!isLive">
          <el-form-item label="副作用">
            <el-radio-group v-model="editForm.sideEffect">
              <el-radio value="readonly">readonly（只读）</el-radio>
              <el-radio value="write">write（写）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="可逆性">
            <el-radio-group v-model="editForm.reversibility">
              <el-radio value="readonly">readonly</el-radio>
              <el-radio value="reversible">reversible</el-radio>
              <el-radio value="irreversible">irreversible</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="所需权限">
            <el-select v-model="editForm.requiredPermission" multiple filterable allow-create default-first-option
                       placeholder="可输入自定义权限标识" style="width: 100%" />
          </el-form-item>
          <el-form-item label="幂等键">
            <el-input v-model="editForm.idempotencyKey" placeholder="如入参中的业务主键字段（无法显式清空）" />
          </el-form-item>
          <el-form-item label="HITL 人工确认">
            <el-switch v-model="editForm.hitlEnabled" />
            <span class="hint">（提交即整体替换，请填写完整）</span>
          </el-form-item>
          <template v-if="editForm.hitlEnabled">
            <el-form-item label="审批人类型">
              <el-input v-model="editForm.approverType" placeholder="如 role / user" />
            </el-form-item>
            <el-form-item label="审批人">
              <el-input v-model="editForm.approverRef" placeholder="如 alice（审批时 agent 身份须等于此值）" />
            </el-form-item>
            <el-form-item label="超时(秒)">
              <el-input-number v-model="editForm.timeoutSec" :min="30" :max="86400" />
            </el-form-item>
          </template>
        </template>
      </el-form>
      <el-alert v-for="w in editWarnings" :key="w" type="warning" :closable="false" class="mb"
                :title="`${WARNING_TEXT[w] ?? w} [${w}]`" show-icon />
      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button type="primary" @click="doEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 沙箱 dialog -->
    <el-dialog v-model="sandboxVisible" title="沙箱试运行" width="680px">
      <el-alert type="info" :closable="false" class="mb"
                title="沙箱结果由 outputSchema 生成 mock 数据，不会调用真实下游系统" show-icon />
      <el-form label-width="90px">
        <el-form-item label="arguments">
          <el-input v-model="sandboxArgs" type="textarea" :rows="5" placeholder='留空则按 examples[0] → 由 inputSchema 生成' @input="validateArgs" />
          <div v-if="argsError" class="err">{{ argsError }}</div>
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="sandboxLoading" :disabled="!!argsError" @click="doSandbox">运行</el-button>
      <template v-if="sandboxResult">
        <el-divider />
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="argSource">{{ sandboxResult.argSource }}</el-descriptions-item>
          <el-descriptions-item label="sideEffect">{{ sandboxResult.sideEffect }}</el-descriptions-item>
        </el-descriptions>
        <p class="sub">实际参数：</p>
        <JsonPretty :data="sandboxResult.arguments" />
        <p class="sub">mock 结果：</p>
        <JsonPretty :data="sandboxResult.result" />
        <ul v-if="sandboxResult.notes.length" class="notes">
          <li v-for="(n, i) in sandboxResult.notes" :key="i">{{ n }}</li>
        </ul>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; flex-wrap: wrap; }
.name { margin: 0 0 6px; }
.meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--el-text-color-secondary); font-size: 13px; }
.tool-id { background: var(--el-fill-color); padding: 1px 6px; border-radius: 3px; }
.desc { color: var(--el-text-color-regular); max-width: 760px; }
.actions { display: flex; align-items: center; flex-wrap: wrap; gap: 0; }
.block { margin-top: 16px; }
.mb { margin-bottom: 12px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); margin-left: 6px; }
.err { color: var(--el-color-danger); font-size: 12px; }
.sub { margin: 10px 0 6px; font-size: 13px; color: var(--el-text-color-secondary); }
.notes { font-size: 12px; color: var(--el-text-color-secondary); padding-left: 18px; }
</style>
