<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTools } from '@/api/tools'
import { usePage } from '@/composables/usePage'
import StatusTag from '@/components/StatusTag.vue'
import type { ToolListFilters, ToolSummary } from '@/types/api'
import { toastError } from '@/utils/format'

const router = useRouter()

// 过滤字段界面态为 string（空串=不过滤），发请求时映射为后端枚举/undefined
interface ListFilters {
  q: string
  system: string
  status: string
  reversibility: string
}

const { items, total, loading, currentPage, pageSize, filters, load, search } = usePage<ToolSummary, ListFilters>(
  (p) => listTools({
    q: p.q || undefined,
    system: p.system || undefined,
    status: (p.status || undefined) as ToolListFilters['status'],
    reversibility: (p.reversibility || undefined) as ToolListFilters['reversibility'],
    page: p.page,
    size: p.size,
  }).catch((e) => {
    toastError(e)
    throw e
  }),
  { q: '', system: '', status: '', reversibility: '' },
)

onMounted(load)
</script>

<template>
  <div>
    <el-form inline class="filters" @submit.prevent="search">
      <el-form-item label="关键词">
        <el-input v-model="filters.q" placeholder="toolId / 名称 / 描述" clearable style="width: 200px" @keyup.enter="search" @clear="search" />
      </el-form-item>
      <el-form-item label="系统">
        <el-input v-model="filters.system" placeholder="如 petstore" clearable style="width: 140px" @keyup.enter="search" @clear="search" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" clearable placeholder="全部" style="width: 130px" @change="search">
          <el-option v-for="s in ['draft', 'reviewing', 'published', 'gray', 'deprecated']" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="可逆性">
        <el-select v-model="filters.reversibility" clearable placeholder="全部" style="width: 130px" @change="search">
          <el-option v-for="s in ['readonly', 'reversible', 'irreversible']" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="items" border stripe>
      <el-table-column label="toolId" min-width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="router.push(`/tools/${row.toolId}`)">{{ row.toolId }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusTag kind="tool" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="systemRef" label="系统" width="110" />
      <el-table-column label="副作用" width="90">
        <template #default="{ row }"><StatusTag kind="tool" :value="row.sideEffect" /></template>
      </el-table-column>
      <el-table-column prop="reversibility" label="可逆性" width="110" />
      <el-table-column label="HITL" width="70">
        <template #default="{ row }">{{ row.hitlEnabled ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="owner" label="Owner" width="100" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="router.push(`/tools/${row.toolId}`)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      class="pager"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50]"
      :total="total"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<style scoped>
.filters { margin-bottom: 4px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
