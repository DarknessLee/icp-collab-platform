<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { taskApi, WORKFLOW_TYPES, workflowStatusText, workflowStatusType, workflowTypeName } from '@/api/workflow'
import WorkflowTaskDetailDrawer from '@/components/WorkflowTaskDetailDrawer.vue'
import { usePageList } from '@/composables/usePageList'
import type { PageQuery } from '@/types/system'
import type { WorkflowTaskRow } from '@/types/workflow'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<WorkflowTaskRow>((params) => taskApi.done(params as PageQuery), {
    keyword: '',
    processKey: '',
    status: ''
  })

const detailVisible = ref(false)
const activeTaskId = ref('')

function openDetail(row: WorkflowTaskRow): void {
  activeTaskId.value = row.taskId
  detailVisible.value = true
}

function actionTagType(action?: string): 'success' | 'danger' | 'warning' | 'info' {
  const key = (action || '').toLowerCase()
  if (key.includes('reject') || key.includes('驳回')) return 'danger'
  if (key.includes('approve') || key.includes('同意') || key.includes('通过')) return 'success'
  return 'info'
}

function actionText(action?: string): string {
  const key = (action || '').toLowerCase()
  if (key.includes('reject') || key.includes('驳回')) return '驳回'
  if (key.includes('approve') || key.includes('同意') || key.includes('通过')) return '同意'
  return action || '已处理'
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
          <span class="page-toolbar-heading">我的已办</span>
          <span class="page-toolbar-desc">我已处理过的流程任务，可回看表单、流转历史与流程图</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select v-model="query.processKey" class="toolbar-select" placeholder="流程类型" clearable @change="search">
            <el-option v-for="item in WORKFLOW_TYPES" :key="item.key" :label="item.name" :value="item.key" />
          </el-select>
          <el-select v-model="query.status" class="toolbar-select" placeholder="流程状态" clearable @change="search">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="标题 / 业务单号"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="taskId" empty-text="暂无已办任务">
        <el-table-column label="标题" min-width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title || '—' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="流程类型" width="115">
          <template #default="{ row }">{{ row.processName || workflowTypeName(row.processKey) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" width="130" />
        <el-table-column prop="nodeName" label="处理节点" width="115" show-overflow-tooltip />
        <el-table-column label="处理结果" width="85" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTagType(row.action)">{{ actionText(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="initiatorName" label="发起人" width="85" />
        <el-table-column label="处理时间" width="165">
          <template #default="{ row }">{{ row.handleTime || row.endTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="流程状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="workflowStatusType(row.status)">
              {{ row.statusText || workflowStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="85" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
        <el-button text @click="reset">重置筛选</el-button>
      </div>
    </div>

    <WorkflowTaskDetailDrawer v-model="detailVisible" :task-id="activeTaskId" :show-actions="false" />
  </div>
</template>
