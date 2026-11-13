<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ kind: 'tool' | 'sub' | 'audit' | 'quality' | 'job'; value: string }>()

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const COLOR: Record<string, TagType> = {
  // 工具状态
  draft: 'info', reviewing: 'warning', published: 'success', gray: 'primary', deprecated: 'danger',
  // 订阅状态
  pending: 'warning', approved: 'success', rejected: 'info', revoked: 'danger',
  // 调用状态
  success: 'success', fail: 'danger', hitl_pending: 'warning', timeout: 'warning',
  succeeded: 'success', replayed: 'primary', pending_approval: 'warning',
  // 质量 / 任务
  pass: 'success', warn: 'warning', failed: 'danger',
  done: 'success', running: 'warning',
  // 副作用
  readonly: 'success', write: 'warning',
}

const type = computed<TagType>(() => COLOR[props.value] ?? 'info')
</script>

<template>
  <el-tag :type="type" size="small" disable-transitions>{{ value }}</el-tag>
</template>
