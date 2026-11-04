<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { alertApi } from '@/api/report'
import type { AlertCount, AlertRow, AlertType } from '@/types/report'

const router = useRouter()

const ALERT_TABS: Array<{ type: AlertType; label: string; hint: string; target: string }> = [
  { type: 'task_overdue', label: '任务逾期', hint: '计划结束日期早于今天且未完成', target: '/coll/task' },
  { type: 'policy_expiring', label: '制度到期', hint: '生效中且 30 天内到期', target: '/icp/policy' },
  { type: 'defect_high_risk', label: '高风险缺陷', hint: '严重程度 1（重大）且未关闭', target: '/icp/defect' },
  { type: 'process_overdue', label: '流程超时', hint: '运行中超过 48 小时未结束', target: '/wf/monitor' }
]

const activeType = ref<AlertType>('task_overdue')
const loading = ref(false)
const error = ref('')
const count = ref<AlertCount>()
const rows = ref<AlertRow[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10 })

const activeTab = computed(() => ALERT_TABS.find((item) => item.type === activeType.value))
const countOf = computed<Record<AlertType, number>>(() => ({
  task_overdue: count.value?.taskOverdue ?? 0,
  policy_expiring: count.value?.policyExpiring ?? 0,
  defect_high_risk: count.value?.defectHighRisk ?? 0,
  process_overdue: count.value?.processOverdue ?? 0
}))

const levelText = (level: number | string): string => {
  if (String(level) === '1') return '高'
  if (String(level) === '2') return '中'
  return '低'
}
const levelType = (level: number | string): 'danger' | 'warning' | 'info' => {
  if (String(level) === '1') return 'danger'
  if (String(level) === '2') return 'warning'
  return 'info'
}

async function loadCount(): Promise<void> {
  count.value = await alertApi.count()
}

async function loadList(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await alertApi.list({
      type: activeType.value,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = result.list
    total.value = result.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '预警列表加载失败'
  } finally {
    loading.value = false
  }
}

async function reload(): Promise<void> {
  await Promise.all([loadCount(), loadList()])
}

function switchTab(type: AlertType): void {
  if (activeType.value === type) return
  activeType.value = type
  query.pageNum = 1
  loadList()
}

function handlePageChange(page: number): void {
  query.pageNum = page
  loadList()
}

function handleSizeChange(size: number): void {
  query.pageSize = size
  query.pageNum = 1
  loadList()
}

function openAlert(row: AlertRow): void {
  const fallback = activeTab.value?.target ?? '/dashboard'
  if (row.bizType === 'icp_policy') {
    router.push('/icp/policy')
    return
  }
  if (row.bizType === 'icp_defect') {
    router.push('/icp/defect')
    return
  }
  if (row.bizType === 'wf_instance' || row.bizType === 'workflow') {
    router.push('/wf/monitor')
    return
  }
  if (row.bizType === 'coll_task') {
    router.push('/coll/task')
    return
  }
  router.push(fallback)
}

onMounted(reload)
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div>
        <div class="page-toolbar-heading">预警中心</div>
        <div class="page-toolbar-desc">任务逾期、制度到期、高风险缺陷、流程超时四类预警统一入口</div>
      </div>
      <div class="page-toolbar-actions">
        <el-button :loading="loading" @click="reload">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" :closable="false" class="mb-12">
      <template #title>
        {{ error }}
        <el-button link type="primary" @click="reload">重试</el-button>
      </template>
    </el-alert>

    <el-row :gutter="16" class="mb-12">
      <el-col v-for="tab in ALERT_TABS" :key="tab.type" :xs="12" :sm="6">
        <el-card
          shadow="never"
          :class="['alert-card', { 'is-active': tab.type === activeType }]"
          @click="switchTab(tab.type)"
        >
          <div class="alert-card__label">{{ tab.label }}</div>
          <div class="alert-card__value" :class="{ 'is-danger': countOf[tab.type] > 0 }">
            {{ countOf[tab.type] }}
          </div>
          <div class="alert-card__hint">{{ tab.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-tabs :model-value="activeType" @tab-change="(name: string | number) => switchTab(String(name) as AlertType)">
        <el-tab-pane v-for="tab in ALERT_TABS" :key="tab.type" :name="tab.type" :label="tab.label" />
      </el-tabs>

      <el-table v-loading="loading" :data="rows" empty-text="当前类别暂无预警" @row-click="openAlert">
        <el-table-column prop="title" label="预警内容" min-width="280" show-overflow-tooltip />
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="levelType(row.level)" effect="light" size="small">{{ levelText(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期/截止时间" width="170" align="center">
          <template #default="{ row }">{{ row.dueTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="剩余/逾期" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.overdueDays !== undefined" class="is-danger-text">逾期 {{ row.overdueDays }} 天</span>
            <span v-else-if="row.daysLeft !== undefined">剩余 {{ row.daysLeft }} 天</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openAlert(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.alert-card {
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.alert-card.is-active {
  border-color: #2f6fed;
  box-shadow: 0 0 0 1px #2f6fed inset;
}

.alert-card__label {
  font-size: 13px;
  color: #5c6b7a;
}

.alert-card__value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
  color: #2f6fed;
}

.alert-card__value.is-danger {
  color: #f56c6c;
}

.alert-card__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.is-danger-text {
  color: #f56c6c;
}
</style>
