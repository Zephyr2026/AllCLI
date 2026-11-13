<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  DataBoard, FolderOpened, CaretRight, Stamp, Connection, Document, Upload, Setting, Fold, Menu as IconMenu,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useHealthStore } from '@/stores/health'

const route = useRoute()
const auth = useAuthStore()
const health = useHealthStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/tools')) return '/tools'
  return route.path
})
const pageTitle = computed(() => (route.meta.title as string) ?? '')

const menus = [
  { path: '/', title: '看板', icon: DataBoard },
  { path: '/tools', title: '工具目录', icon: FolderOpened },
  { path: '/playground', title: '调用测试', icon: CaretRight },
  { path: '/approvals', title: 'HITL 审批', icon: Stamp },
  { path: '/subscriptions', title: '订阅治理', icon: Connection },
  { path: '/audit', title: '审计', icon: Document },
  { path: '/imports', title: 'OpenAPI 导入', icon: Upload },
  { path: '/systems', title: '系统与凭证', icon: Setting },
]

const healthColor = computed(() => ({ up: '#67c23a', down: '#f56c6c', unknown: '#909399' })[health.status])
const healthText = computed(() => ({ up: '后端正常', down: '后端不可达', unknown: '检测中…' })[health.status])

onMounted(() => health.start())
onUnmounted(() => health.stop())
</script>

<template>
  <el-container class="console">
    <el-aside width="220px" class="aside">
      <div class="brand">AllCLI 控制台</div>
      <el-menu router :default-active="activeMenu" class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header" height="52px">
        <div class="left">
          <el-icon :size="16"><IconMenu /></el-icon>
          <span class="title">{{ pageTitle }}</span>
        </div>
        <div class="right">
          <el-tooltip :content="healthText" placement="bottom">
            <span class="dot" :style="{ background: healthColor }" @click="health.poll()" />
          </el-tooltip>
          <el-tag size="small" type="info">管理员已设置</el-tag>
          <el-tag size="small">默认 Agent: {{ auth.agentId || '未设置' }}</el-tag>
          <el-button size="small" text :icon="Fold" @click="$router.push('/login')">设置</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.console { height: 100vh; }
.aside { border-right: 1px solid var(--el-border-color-light); display: flex; flex-direction: column; }
.brand { height: 52px; line-height: 52px; text-align: center; font-weight: 600; font-size: 15px; border-bottom: 1px solid var(--el-border-color-light); }
.menu { border-right: none; flex: 1; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--el-border-color-light); }
.header .left { display: flex; align-items: center; gap: 6px; }
.header .title { font-size: 14px; color: var(--el-text-color-primary); }
.header .right { display: flex; align-items: center; gap: 10px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; cursor: pointer; }
.main { background: var(--el-fill-color-blank); }
</style>
