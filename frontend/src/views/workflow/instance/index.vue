<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { monitorApi, WORKFLOW_TYPES, workflowStatusText, workflowStatusType, workflowTypeName } from '@/api/workflow'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'
import type { PageQuery } from '@/types/system'
import type { WorkflowMonitorRow } from '@/types/workflow'

const loading = ref(false)
const list = ref<WorkflowMonitorRow[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; processKey: string; status: string }>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  processKey: '',
  status: ''
})

const drawerVisible = ref(false)
const activeInstanceId = ref('')

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await monitorApi.list({ ...query } as PageQuery)
    list.value = result.list
    total.value = result.total
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNum = 1
  void load()
}

function reset(): void {
  query.keyword = ''
  query.processKey = ''
  query.status = ''
  search()
}

function handlePageChange(page: number): void {
  query.pageNum = page
  void load()
}

function handleSizeChange(size: number): void {
  query.pageSize = size
  query.pageNum = 1
  void load()
}

function openDetail(row: WorkflowMonitorRow): void {
  activeInstanceId.value = row.processInstanceId
  drawerVisible.value = true
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
          <span class="page-toolbar-heading">流程实例</span>
          <span class="page-toolbar-desc">全量流程实例列表；可按流程类型、运行状态与关键字检索，点击标题查看表单、流转历史与流程图</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select v-model="query.processKey" class="toolbar-select" placeholder="流程类型" clearable @change="search">
            <el-option v-for="item in WORKFLOW_TYPES" :key="item.key" :label="item.name" :value="item.key" />
          </el-select>
          <el-select v-model="query.status" class="toolbar-select" placeholder="运行状态" clearable @change="search">
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="标题 / 业务单号 / 发起人"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="processInstanceId" empty-text="暂无流程实例">
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title || '—' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="流程类型" width="115">
          <template #default="{ row }">{{ row.processName || workflowTypeName(row.processKey) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" width="130" />
        <el-table-column prop="initiatorName" label="发起人" width="85" />
        <el-table-column prop="nodeName" label="当前节点" width="115" show-overflow-tooltip />
        <el-table-column label="发起时间" width="165">
          <template #default="{ row }">{{ row.startTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="duration" label="已耗时" width="100" align="center" />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="workflowStatusType(row.status)">
              {{ row.statusText || workflowStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
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

    <WorkflowInstanceDrawer v-model="drawerVisible" :instance-id="activeInstanceId" />
  </div>
</template>
