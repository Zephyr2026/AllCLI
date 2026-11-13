<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { registerCredential, registerSystem } from '@/api/admin'
import type { CredentialResponse, SystemResponse } from '@/types/api'
import { toastError } from '@/utils/format'

const sysForm = reactive({
  systemId: '',
  name: '',
  baseUrl: '',
  authType: 'None' as 'OAuth2' | 'ApiKey' | 'Cookie' | 'None',
  owner: 'alice',
})
const sysResult = ref<SystemResponse | null>(null)
const sysSubmitting = ref(false)

async function submitSystem() {
  if (!sysForm.systemId || !sysForm.baseUrl) {
    ElMessage.warning('systemId 与 baseUrl 必填')
    return
  }
  sysSubmitting.value = true
  try {
    sysResult.value = await registerSystem({
      systemId: sysForm.systemId,
      name: sysForm.name || undefined,
      baseUrl: sysForm.baseUrl,
      authType: sysForm.authType,
      owner: sysForm.owner || undefined,
    })
    ElMessage.success('系统注册成功')
  } catch (e) {
    toastError(e)
  } finally {
    sysSubmitting.value = false
  }
}

const credForm = reactive({
  systemRef: '',
  type: 'Vault' as 'Vault' | 'KMS' | 'Opaque',
  secret: '',
  rotatePolicy: '',
})
const credResult = ref<CredentialResponse | null>(null)
const credSubmitting = ref(false)

async function submitCredential() {
  if (!credForm.systemRef || !credForm.secret) {
    ElMessage.warning('systemRef 与 secret 必填')
    return
  }
  credSubmitting.value = true
  try {
    credResult.value = await registerCredential({
      systemRef: credForm.systemRef,
      type: credForm.type,
      secret: credForm.secret,
      rotatePolicy: credForm.rotatePolicy || undefined,
    })
    ElMessage.success('凭证注册成功（已信封加密托管）')
    credForm.secret = ''
  } catch (e) {
    toastError(e)
  } finally {
    credSubmitting.value = false
  }
}
</script>

<template>
  <div>
    <el-alert type="info" :closable="false" show-icon class="mb"
              title="一期无列表接口：仅支持注册，重复 systemId / 未注册系统的凭证会以业务错误提示" />

    <el-row :gutter="14">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>注册存量系统</template>
          <el-form label-width="100px">
            <el-form-item label="systemId" required>
              <el-input v-model="sysForm.systemId" placeholder="如 petstore2" />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="sysForm.name" placeholder="显示名（可选）" />
            </el-form-item>
            <el-form-item label="baseUrl" required>
              <el-input v-model="sysForm.baseUrl" placeholder="http://127.0.0.1:18090" />
            </el-form-item>
            <el-form-item label="认证方式">
              <el-select v-model="sysForm.authType">
                <el-option v-for="t in ['None', 'OAuth2', 'ApiKey', 'Cookie']" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="Owner">
              <el-input v-model="sysForm.owner" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="sysSubmitting" @click="submitSystem">注册</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="sysResult" :column="1" border size="small">
            <el-descriptions-item label="systemId">{{ sysResult.systemId }}</el-descriptions-item>
            <el-descriptions-item label="baseUrl">{{ sysResult.baseUrl }}</el-descriptions-item>
            <el-descriptions-item label="authType">{{ sysResult.authType }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ sysResult.status }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>托管凭证（SM4 信封加密）</template>
          <el-form label-width="100px">
            <el-form-item label="目标系统" required>
              <el-input v-model="credForm.systemRef" placeholder="须为已注册的 systemId" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="credForm.type">
                <el-option v-for="t in ['Vault', 'KMS', 'Opaque']" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="secret" required>
              <el-input v-model="credForm.secret" type="password" show-password placeholder="密钥明文，仅本次提交可见" />
            </el-form-item>
            <el-form-item label="轮转策略">
              <el-input v-model="credForm.rotatePolicy" placeholder="如 30d（可选）" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="credSubmitting" @click="submitCredential">托管</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="credResult" :column="1" border size="small">
            <el-descriptions-item label="credId">{{ credResult.credId }}</el-descriptions-item>
            <el-descriptions-item label="systemRef">{{ credResult.systemRef }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ credResult.type }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ credResult.status }}</el-descriptions-item>
          </el-descriptions>
          <p class="hint">凭证经 SM4 信封加密落库，secret 不回显；网关转发时解密注入下游 Header，Agent 永不接触明文。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.mb { margin-bottom: 14px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); margin: 10px 0 0; line-height: 1.5; }
</style>
