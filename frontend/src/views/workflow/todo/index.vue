<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { taskApi, WORKFLOW_TYPES, workflowTypeName } from '@/api/workflow'
import WorkflowTaskDetailDrawer from '@/components/WorkflowTaskDetailDrawer.vue'
import { usePageList } from '@/composables/usePageList'
import type { PageQuery } from '@/types/system'
import { useUserStore } from '@/stores/user'
import type { WorkflowTaskRow } from '@/types/workflow'

const router = useRouter()
const userStore = useUserStore()

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<WorkflowTaskRow>((params) => taskApi.todo(params as PageQuery), {
    keyword: '',
    processKey: ''
  })

const detailVisible = ref(false)
const activeTaskId = ref('')

const canStartPurchase = computed(() => userStore.hasPermission('workflow:purchase:add'))
const canStartSeal = computed(() => userStore.hasPermission('workflow:seal:add'))
const canStartBorrow = computed(() => userStore.hasPermission('workflow:borrow:add') || userStore.hasPermission('coll:doc:borrow'))
const canStartAny = computed(() => canStartPurchase.value || canStartSeal.value || canStartBorrow.value)

function openTask(row: WorkflowTaskRow): void {
  activeTaskId.value = row.taskId
  detailVisible.value = true
}

function handleHandled(): void {
  ElMessage.success('处理完成，待办列表已刷新')
  void load()
}

function start(type: 'purchase' | 'seal' | 'borrow'): void {
  void router.push({ path: `/workflow/${type}`, query: { new: '1' } })
}

function overdue(row: WorkflowTaskRow): boolean {
  if (!row.dueTime) return false
  const time = new Date(String(row.dueTime).replace(/-/g, '/')).getTime()
  return Number.isFinite(time) && time < Date.now()
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
          <span class="page-toolbar-heading">我的待办</span>
          <span class="page-toolbar-desc">当前由我审批的流程任务；超时任务以红色标签提醒，处理后可一键刷新</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select v-model="query.processKey" class="toolbar-select" placeholder="流程类型" clearable @change="search">
            <el-option v-for="item in WORKFLOW_TYPES" :key="item.key" :label="item.name" :value="item.key" />
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
          <el-dropdown v-if="canStartAny" trigger="click" @command="start">
            <el-button type="primary">
              发起申请<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="canStartPurchase" command="purchase">采购申请</el-dropdown-item>
                <el-dropdown-item v-if="canStartSeal" command="seal">用印申请</el-dropdown-item>
                <el-dropdown-item v-if="canStartBorrow" command="borrow">文档借阅</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="taskId" empty-text="暂无待办任务">
        <el-table-column label="标题" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openTask(row)">{{ row.title || '—' }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="流程类型" width="115">
          <template #default="{ row }">{{ row.processName || workflowTypeName(row.processKey) }}</template>
        </el-table-column>
        <el-table-column prop="businessNo" label="业务单号" width="130" />
        <el-table-column label="当前节点" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.nodeName || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="initiatorName" label="发起人" width="90" />
        <el-table-column prop="createTime" label="发起时间" width="165" />
        <el-table-column label="截止时间" width="175">
          <template #default="{ row }">
            <span>{{ row.dueTime || '—' }}</span>
            <el-tag v-if="overdue(row)" size="small" type="danger" class="ml-6">已超时</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTask(row)">处理</el-button>
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

    <WorkflowTaskDetailDrawer
      v-model="detailVisible"
      :task-id="activeTaskId"
      show-actions
      @handled="handleHandled"
    />
  </div>
</template>

<style scoped>
.ml-6 {
  margin-left: 6px;
}
</style>
