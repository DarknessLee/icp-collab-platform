<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { taskApi, WORKFLOW_TYPES, workflowStatusText, workflowStatusType, workflowTypeName } from '@/api/workflow'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'
import { usePageList } from '@/composables/usePageList'
import type { PageQuery } from '@/types/system'
import type { WorkflowInstanceRow } from '@/types/workflow'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<WorkflowInstanceRow>((params) => taskApi.initiated(params as PageQuery), {
    keyword: '',
    processKey: '',
    status: ''
  })

const drawerVisible = ref(false)
const activeInstanceId = ref('')

function openDetail(row: WorkflowInstanceRow): void {
  activeInstanceId.value = row.processInstanceId
  drawerVisible.value = true
}

function isRejected(row: WorkflowInstanceRow): boolean {
  const status = String(row.status || '').toUpperCase()
  return status === 'REJECTED' || status === '2' || row.statusText === '已驳回'
}

function resubmitted(): void {
  void load()
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
          <span class="page-toolbar-heading">我发起的</span>
          <span class="page-toolbar-desc">我发起的流程实例，可跟踪审批进度；被驳回可修改后重新提交</span>
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

      <el-table v-loading="loading" :data="list" border stripe row-key="processInstanceId" empty-text="暂无发起记录">
        <el-table-column label="标题" min-width="165" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title || '—' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="流程类型" width="115">
          <template #default="{ row }">{{ row.processName || workflowTypeName(row.processKey) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" width="130" />
        <el-table-column label="流程状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="workflowStatusType(row.status)">
              {{ row.statusText || workflowStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="当前节点" width="115" show-overflow-tooltip />
        <el-table-column label="发起时间" width="165">
          <template #default="{ row }">{{ row.startTime || row.createTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="165">
          <template #default="{ row }">{{ row.endTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="isRejected(row)" link type="warning" @click="openDetail(row)">重新提交</el-button>
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

    <WorkflowInstanceDrawer
      v-model="drawerVisible"
      :instance-id="activeInstanceId"
      allow-resubmit
      @resubmitted="resubmitted"
    />
  </div>
</template>
