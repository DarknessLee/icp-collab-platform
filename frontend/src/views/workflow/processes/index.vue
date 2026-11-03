<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { processApi, workflowTypeName } from '@/api/workflow'
import WorkflowDiagram from '@/components/WorkflowDiagram.vue'
import type { ProcessDef } from '@/types/workflow'

const loading = ref(false)
const list = ref<ProcessDef[]>([])

const diagramVisible = ref(false)
const current = ref<ProcessDef | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = await processApi.list()
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    loading.value = false
  }
}

function openDiagram(row: ProcessDef): void {
  current.value = row
  diagramVisible.value = true
}

function statusText(status?: number | string): string {
  const value = String(status ?? '')
  if (value === '1' || value.toLowerCase() === 'active') return '已部署'
  if (value === '0' || value.toLowerCase() === 'suspended') return '已挂起'
  return value || '已部署'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">流程定义</span>
          <span class="page-toolbar-desc">已部署的 BPMN 流程定义；流程图只读查看，当前节点与已流转节点高亮展示</span>
        </div>
        <div class="page-toolbar-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="processKey" empty-text="暂无流程定义">
        <el-table-column prop="processKey" label="流程标识" width="200" />
        <el-table-column label="流程名称" min-width="200">
          <template #default="{ row }">{{ row.name || workflowTypeName(row.processKey) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" align="center" />
        <el-table-column prop="category" label="分类" width="140" />
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部署时间" width="170">
          <template #default="{ row }">{{ row.deployedTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDiagram(row)">查看流程图</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="diagramVisible" :title="`流程图 - ${current?.name || ''}`" width="960px" destroy-on-close>
      <WorkflowDiagram v-if="current" :process-key="current.processKey" :height="480" />
      <template #footer>
        <el-button @click="diagramVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
