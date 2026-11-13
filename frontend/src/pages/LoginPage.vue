<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useHealthStore } from '@/stores/health'

const router = useRouter()
const auth = useAuthStore()
const health = useHealthStore()

const adminToken = ref(auth.adminToken)
const agentId = ref(auth.agentId)
const validating = ref(false)
const error = ref('')

async function save() {
  if (!adminToken.value.trim()) {
    error.value = '请填写管理员 token'
    return
  }
  validating.value = true
  error.value = ''
  // 先写入 store 让拦截器用新 token 发验证请求
  auth.adminToken = adminToken.value.trim()
  auth.agentId = agentId.value.trim()
  const result = await auth.validate()
  validating.value = false
  if (result === true) {
    ElMessage.success('验证通过')
    void router.replace('/')
  } else {
    error.value = `验证失败：${result.message}（${result.code}${result.traceId ? ` traceId: ${result.traceId}` : ''}）`
  }
}

void health.poll()
</script>

<template>
  <div class="login">
    <el-card class="card">
      <h2 class="title">AllCLI 控制台</h2>
      <p class="sub">存量系统 → AI 工具 · 转化 / 目录 / 网关 / 治理</p>
      <el-form label-width="110px" @submit.prevent>
        <el-form-item label="管理员 token">
          <el-input v-model="adminToken" type="password" show-password placeholder="admin_m4-dev-admin" />
        </el-form-item>
        <el-form-item label="默认 Agent ID">
          <el-input v-model="agentId" placeholder="alice" />
          <div class="hint">用于发布 / 调用 / 沙箱 / 订阅审批等 agent 面操作</div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="validating" @click="save">保存并进入</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
      <div class="health">后端状态：{{ { up: '正常', down: '不可达', unknown: '检测中…' }[health.status] }}</div>
    </el-card>
  </div>
</template>

<style scoped>
.login { height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--el-fill-color-light); }
.card { width: 440px; }
.title { margin: 0 0 4px; text-align: center; }
.sub { margin: 0 0 20px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.4; margin-top: 2px; }
.health { margin-top: 14px; text-align: center; font-size: 12px; color: var(--el-text-color-secondary); }
</style>
