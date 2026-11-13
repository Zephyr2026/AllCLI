<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createImport, getImportJob } from '@/api/admin'
import StatusTag from '@/components/StatusTag.vue'
import type { ImportJobResponse } from '@/types/api'
import { fmtTime, toastError } from '@/utils/format'

const step = ref(0)
const sourceMode = ref<'url' | 'content'>('url')
const submitting = ref(false)
const job = ref<ImportJobResponse | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  sourceUrl: '',
  sourceContent: '',
  systemRef: '',
  credentialRef: '',
  owner: 'alice',
  includePaths: [] as string[],
  excludeRegex: '',
})

async function submit() {
  if (!form.systemRef.trim()) {
    ElMessage.warning('systemRef 必填（须已在「系统与凭证」页注册）')
    return
  }
  if (sourceMode.value === 'url' && !form.sourceUrl.trim()) {
    ElMessage.warning('请填写 OpenAPI 文档 URL 或切换为粘贴内容')
    return
  }
  if (sourceMode.value === 'content' && !form.sourceContent.trim()) {
    ElMessage.warning('请粘贴 OpenAPI 文档内容')
    return
  }
  submitting.value = true
  try {
    job.value = await createImport({
      sourceType: 'openapi',
      sourceUrl: sourceMode.value === 'url' ? form.sourceUrl.trim() : undefined,
      sourceContent: sourceMode.value === 'content' ? form.sourceContent : undefined,
      systemRef: form.systemRef.trim(),
      credentialRef: form.credentialRef.trim() || undefined,
      owner: form.owner.trim() || undefined,
      includePaths: form.includePaths.length ? form.includePaths : undefined,
      excludeRegex: form.excludeRegex.trim() || undefined,
    })
    step.value = 1
    startPolling()
  } catch (e) {
    toastError(e)
  } finally {
    submitting.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    if (!job.value) return
    try {
      const j = await getImportJob(job.value.jobId)
      job.value = j
      if (j.status !== 'running') {
        stopPolling()
        step.value = 2
        if (j.status === 'done') ElMessage.success('导入完成')
      }
    } catch (e) {
      // job 为内存态：后端重启后 404，容忍并停止轮询
      stopPolling()
      step.value = 2
      toastError(e)
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}
onUnmounted(stopPolling)

function reset() {
  stopPolling()
  job.value = null
  step.value = 0
}
</script>

<template>
  <div class="wizard">
    <el-card shadow="never">
      <template #header>OpenAPI 一键导入 → 工具草案</template>
      <el-steps :active="step" align-center finish-status="success" class="steps">
        <el-step title="填写来源" />
        <el-step title="转化中" />
        <el-step title="结果" />
      </el-steps>

      <!-- 第 1 步：表单 -->
      <div v-if="step === 0">
        <el-form label-width="120px">
          <el-form-item label="来源方式">
            <el-radio-group v-model="sourceMode">
              <el-radio value="url">URL 拉取</el-radio>
              <el-radio value="content">粘贴内容</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="sourceMode === 'url'" label="文档 URL">
            <el-input v-model="form.sourceUrl" placeholder="http://host/openapi.json（5s 超时 / 5MB 上限）" style="width: 480px" />
          </el-form-item>
          <el-form-item v-else label="文档内容">
            <el-input v-model="form.sourceContent" type="textarea" :rows="10" class="mono"
                      placeholder="直接粘贴 OpenAPI YAML/JSON（如 testdata/petstore.yaml）" />
          </el-form-item>
          <el-form-item label="目标系统" required>
            <el-input v-model="form.systemRef" placeholder="如 petstore（须已注册）" style="width: 240px" />
            <span class="hint">未注册？先去 <el-link type="primary" @click="$router.push('/systems')">系统与凭证</el-link> 页注册</span>
          </el-form-item>
          <el-form-item label="凭证">
            <el-input v-model="form.credentialRef" placeholder="credId（可选）" style="width: 240px" />
          </el-form-item>
          <el-form-item label="Owner">
            <el-input v-model="form.owner" style="width: 240px" />
          </el-form-item>
          <el-form-item label="路径过滤">
            <el-select v-model="form.includePaths" multiple filterable allow-create default-first-option
                       placeholder="仅保留这些路径（可输入如 /pets 回车）" style="width: 480px" />
          </el-form-item>
          <el-form-item label="排除正则">
            <el-input v-model="form.excludeRegex" placeholder="如 ^/internal/.*（可选）" style="width: 480px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">开始导入</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 第 2 步：轮询中 -->
      <div v-else-if="step === 1" v-loading="true" element-loading-text="转化任务运行中…" class="center">
        <p>jobId：<code>{{ job?.jobId }}</code></p>
        <p class="hint">任务为内存态：后端重启会丢失任务记录</p>
      </div>

      <!-- 第 3 步：结果 -->
      <div v-else-if="job">
        <el-result v-if="job.status === 'done'" icon="success" title="导入完成">
          <template #sub-title>
            <div>新建 {{ job.created?.length ?? 0 }} 个工具草案 · 冲突 {{ job.conflicts?.length ?? 0 }}（同名不覆盖）</div>
          </template>
          <template #extra>
            <div class="tags">
              <el-tag v-for="id in job.created ?? []" :key="id" type="success" effect="plain" class="tag"
                      style="cursor: pointer" @click="$router.push(`/tools/${id}`)">＋ {{ id }}</el-tag>
              <el-tag v-for="id in job.conflicts ?? []" :key="id" type="warning" effect="plain" class="tag"
                      style="cursor: pointer" @click="$router.push(`/tools/${id}`)">⚠ {{ id }}（已存在）</el-tag>
            </div>
            <el-button @click="reset">再导一次</el-button>
            <el-button type="primary" @click="$router.push('/tools')">去工具列表</el-button>
          </template>
        </el-result>
        <el-result v-else icon="error" :title="`导入失败：${job.errorCode ?? ''}`" :sub-title="job.errorMessage ?? ''">
          <template #extra>
            <el-button type="primary" @click="reset">返回修改</el-button>
          </template>
        </el-result>
        <el-descriptions v-if="job.submittedAt" :column="2" border size="small" class="mt">
          <el-descriptions-item label="jobId">{{ job.jobId }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag kind="job" :value="job.status" /></el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmtTime(job.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ fmtTime(job.finishedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.wizard { max-width: 860px; }
.steps { margin-bottom: 24px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); margin-left: 8px; }
.mono :deep(textarea) { font-family: ui-monospace, Consolas, monospace; font-size: 12px; }
.center { text-align: center; padding: 30px 0; }
.tags { margin-bottom: 16px; }
.tag { margin: 0 6px 6px 0; }
.mt { margin-top: 16px; }
</style>
