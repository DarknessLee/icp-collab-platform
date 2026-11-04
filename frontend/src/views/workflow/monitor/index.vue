<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { monitorApi, WORKFLOW_TYPES, workflowStatusText, workflowStatusType, workflowTypeName } from '@/api/workflow'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'
import type { WorkflowMonitorRow, WorkflowMonitorStats } from '@/types/workflow'

const loading = ref(false)
const list = ref<WorkflowMonitorRow[]>([])
const total = ref(0)
const stats = ref<WorkflowMonitorStats>({ running: 0, completed: 0, rejected: 0, overdue: 0 })
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; processKey: string; overdue: string }>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  processKey: '',
  overdue: ''
})

const drawerVisible = ref(false)
const activeInstanceId = ref('')

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await monitorApi.list({ ...query })
    list.value = result.list
    total.value = result.total
    stats.value = result.stats
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
  query.overdue = ''
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
    <div class="wf-monitor__stats">
      <div class="wf-monitor__stat">
        <div class="wf-monitor__stat-label">运行中</div>
        <div class="wf-monitor__stat-value wf-monitor__stat-value--primary">{{ stats.running }}</div>
      </div>
      <div class="wf-monitor__stat">
        <div class="wf-monitor__stat-label">已完成</div>
        <div class="wf-monitor__stat-value wf-monitor__stat-value--success">{{ stats.completed }}</div>
      </div>
      <div class="wf-monitor__stat">
        <div class="wf-monitor__stat-label">已驳回</div>
        <div class="wf-monitor__stat-value wf-monitor__stat-value--danger">{{ stats.rejected }}</div>
      </div>
      <div class="wf-monitor__stat">
        <div class="wf-monitor__stat-label">超时（超过 48 小时）</div>
        <div class="wf-monitor__stat-value wf-monitor__stat-value--warning">{{ stats.overdue }}</div>
      </div>
    </div>

    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">流程监控</span>
          <span class="page-toolbar-desc">全量流程实例运行情况；可按超时筛选，便于督办与干预</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select v-model="query.processKey" class="toolbar-select" placeholder="流程类型" clearable @change="search">
            <el-option v-for="item in WORKFLOW_TYPES" :key="item.key" :label="item.name" :value="item.key" />
          </el-select>
          <el-select v-model="query.overdue" class="toolbar-select" placeholder="超时状态" clearable @change="search">
            <el-option label="仅超时" value="true" />
            <el-option label="未超时" value="false" />
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

      <el-table v-loading="loading" :data="list" border stripe row-key="processInstanceId" empty-text="暂无流程实例">
        <el-table-column label="标题" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title || '—' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="流程类型" width="120">
          <template #default="{ row }">{{ row.processName || workflowTypeName(row.processKey) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" width="135" />
        <el-table-column prop="initiatorName" label="发起人" width="90" />
        <el-table-column prop="nodeName" label="当前节点" width="120" show-overflow-tooltip />
        <el-table-column label="发起时间" width="160">
          <template #default="{ row }">{{ row.startTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="已耗时" width="120" align="center">
          <template #default="{ row }">
            <span>{{ row.duration || '—' }}</span>
            <el-tag v-if="row.overdue" size="small" type="danger" class="ml-6">超时</el-tag>
          </template>
        </el-table-column>
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

<style scoped>
.wf-monitor__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.wf-monitor__stat {
  background: #ffffff;
  border-radius: 8px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(16, 42, 82, 0.06);
}

.wf-monitor__stat-label {
  font-size: 13px;
  color: #8a94a6;
}

.wf-monitor__stat-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.wf-monitor__stat-value--primary {
  color: #2f6fed;
}

.wf-monitor__stat-value--success {
  color: #17a673;
}

.wf-monitor__stat-value--danger {
  color: #d64545;
}

.wf-monitor__stat-value--warning {
  color: #e6a23c;
}

.ml-6 {
  margin-left: 6px;
}
</style>
