<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'

const props = defineProps<{ data: unknown; collapsed?: boolean }>()

const text = computed(() => {
  if (props.data == null) return '（空）'
  try {
    return JSON.stringify(props.data, null, 2)
  } catch {
    return String(props.data)
  }
})

async function copy() {
  await navigator.clipboard.writeText(text.value)
  ElMessage.success('已复制')
}
</script>

<template>
  <div class="json-pretty">
    <el-button class="copy-btn" size="small" text :icon="CopyDocument" @click="copy">复制</el-button>
    <el-scrollbar max-height="320px">
      <pre>{{ text }}</pre>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.json-pretty {
  position: relative;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-light);
}
.copy-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  z-index: 1;
}
pre {
  margin: 0;
  padding: 8px 10px;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
