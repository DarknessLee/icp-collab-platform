<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { logApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import type { ChangeLogRow, LoginLogRow, OperLogRow } from '@/types/system'

type TabKey = 'oper' | 'login' | 'change'

const userStore = useUserStore()
function can(perm: string): boolean {
  return userStore.hasPermission(perm)
}

const activeTab = ref<TabKey>('oper')
const filters = reactive({
  keyword: '',
  dateRange: [] as string[],
  status: '' as number | '',
  bizType: ''
})

const operState = usePageList<OperLogRow>(logApi.oper, { operName: '', status: '', bizType: '' })
const loginState = usePageList<LoginLogRow>(logApi.login, { username: '', status: '' })
const changeState = usePageList<ChangeLogRow>(logApi.changes, { bizType: '' })

const tabVisible = computed(() => {
  const auditOnly = can('audit:log:view') && !can('system:log:oper')
  return {
    oper: auditOnly || can('system:log:oper'),
    login: auditOnly || can('system:log:login'),
    change: auditOnly || can('system:log:change')
  }
})

function fillQuery(): void {
  const [startDate = '', endDate = ''] = filters.dateRange || []
  const keyword = filters.keyword.trim()
  const status = filters.status === '' ? undefined : filters.status
  const bizType = filters.bizType.trim() || undefined
  if (activeTab.value === 'oper') {
    // 契约 docs/03 §8：操作日志筛选参数为 operName/module/status/startDate/endDate
    operState.query.operName = keyword || undefined
    operState.query.status = status
    operState.query.bizType = bizType
    operState.query.startDate = startDate || undefined
    operState.query.endDate = endDate || undefined
  } else if (activeTab.value === 'login') {
    // 契约 docs/03 §8：登录日志筛选参数为 username/status/startDate/endDate
    loginState.query.username = keyword || undefined
    loginState.query.status = status
    loginState.query.startDate = startDate || undefined
    loginState.query.endDate = endDate || undefined
  } else {
    // 契约 docs/03 §8：变更日志筛选参数为 bizType/bizId/startDate/endDate
    changeState.query.bizType = bizType
    changeState.query.startDate = startDate || undefined
    changeState.query.endDate = endDate || undefined
  }
}

function currentState() {
  if (activeTab.value === 'oper') return operState
  if (activeTab.value === 'login') return loginState
  return changeState
}

function search(): void {
  const state = currentState()
  fillQuery()
  state.query.pageNum = 1
  void state.load()
}

function reset(): void {
  filters.keyword = ''
  filters.dateRange = []
  filters.status = ''
  filters.bizType = ''
  const state = currentState()
  state.reset()
}

function handleTabChange(tab: string | number): void {
  activeTab.value = tab as TabKey
  search()
}

/* ------------------------------ 详情抽屉 ------------------------------ */
const detailVisible = ref(false)
const operDetail = ref<OperLogRow | null>(null)
const changeDetail = ref<ChangeLogRow | null>(null)

function openOperDetail(row: OperLogRow): void {
  operDetail.value = row
  changeDetail.value = null
  detailVisible.value = true
}

function openChangeDetail(row: ChangeLogRow): void {
  changeDetail.value = row
  operDetail.value = null
  detailVisible.value = true
}

function prettyJson(raw?: string): string {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

async function copyText(text?: string): Promise<void> {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning(`复制失败，请手工复制：${text}`)
  }
}

function operStatusTag(status?: number): 'success' | 'danger' | 'info' {
  if (status === 1) return 'success'
  if (status === 0) return 'danger'
  return 'info'
}

watch(activeTab, () => {
  operDetail.value = null
  changeDetail.value = null
})

onMounted(() => {
  const first = (['oper', 'login', 'change'] as TabKey[]).find((key) => tabVisible.value[key])
  activeTab.value = first || 'oper'
  search()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">审计日志</span>
          <span class="page-toolbar-desc">操作日志、登录日志与数据变更日志全程留痕，审计员仅可查看</span>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane v-if="tabVisible.oper" label="操作日志" name="oper" />
        <el-tab-pane v-if="tabVisible.login" label="登录日志" name="login" />
        <el-tab-pane v-if="tabVisible.change" label="变更日志" name="change" />
      </el-tabs>

      <div class="filter-bar">
        <el-input
          v-if="activeTab !== 'change'"
          v-model="filters.keyword"
          class="filter-input"
          :placeholder="activeTab === 'login' ? '用户名' : '操作人'"
          clearable
          @keyup.enter="search"
        />
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          class="filter-date"
        />
        <el-select v-if="activeTab !== 'change'" v-model="filters.status" class="filter-select" placeholder="执行状态" clearable>
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-input
          v-if="activeTab !== 'login'"
          v-model="filters.bizType"
          class="filter-input"
          placeholder="业务类型"
          clearable
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table
        v-if="activeTab === 'oper'"
        v-loading="operState.loading.value"
        :data="operState.list.value"
        border
        stripe
        empty-text="暂无操作日志"
      >
        <el-table-column prop="operName" label="操作人" width="110" />
        <el-table-column prop="module" label="模块" min-width="110" show-overflow-tooltip />
        <el-table-column prop="action" label="动作" min-width="120" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="operStatusTag(row.status)" size="small">
              {{ row.status === 1 ? '成功' : row.status === 0 ? '失败' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="90" align="right">
          <template #default="{ row }">{{ row.duration != null ? `${row.duration} ms` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="createTime" label="操作时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openOperDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
        v-else-if="activeTab === 'login'"
        v-loading="loginState.loading.value"
        :data="loginState.list.value"
        border
        stripe
        empty-text="暂无登录日志"
      >
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="ip" label="IP 地址" width="140" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="operStatusTag(row.status)" size="small">
              {{ row.status === 1 ? '成功' : row.status === 0 ? '失败' : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column prop="userAgent" label="浏览器 / 终端" min-width="220" show-overflow-tooltip />
        <el-table-column prop="loginTime" label="登录时间" width="170" />
      </el-table>

      <el-table
        v-else
        v-loading="changeState.loading.value"
        :data="changeState.list.value"
        border
        stripe
        empty-text="暂无变更日志"
      >
        <el-table-column prop="operName" label="操作人" width="110" />
        <el-table-column prop="bizType" label="业务类型" min-width="120" show-overflow-tooltip />
        <el-table-column prop="bizId" label="业务 ID" width="110" />
        <el-table-column prop="traceId" label="TraceId" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createTime" label="变更时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openChangeDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="currentState().query.pageNum"
          :page-size="currentState().query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="currentState().total.value"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="currentState().handlePageChange"
          @size-change="currentState().handleSizeChange"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" :title="operDetail ? '操作日志详情' : '数据变更详情'" size="560px">
      <template v-if="operDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="操作人">{{ operDetail.operName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">
            <el-tag :type="operStatusTag(operDetail.status)" size="small">
              {{ operDetail.status === 1 ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="模块">{{ operDetail.module || '-' }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ operDetail.action || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ operDetail.bizType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务 ID">{{ operDetail.bizId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ operDetail.ip || '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ operDetail.duration != null ? `${operDetail.duration} ms` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作时间" :span="2">{{ operDetail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="2">
            <span class="mono">{{ operDetail.traceId || '-' }}</span>
            <el-button v-if="operDetail.traceId" link type="primary" @click="copyText(operDetail.traceId)">
              复制
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item v-if="operDetail.errorMsg" label="错误信息" :span="2">
            <span class="text-danger">{{ operDetail.errorMsg }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-block">
          <div class="detail-label">请求参数</div>
          <pre class="json-block">{{ prettyJson(operDetail.params) }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-label">响应结果</div>
          <pre class="json-block">{{ prettyJson(operDetail.result) }}</pre>
        </div>
      </template>

      <template v-else-if="changeDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="操作人">{{ changeDetail.operName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ changeDetail.bizType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务 ID">{{ changeDetail.bizId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="变更时间">{{ changeDetail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="2">
            <span class="mono">{{ changeDetail.traceId || '-' }}</span>
            <el-button v-if="changeDetail.traceId" link type="primary" @click="copyText(changeDetail.traceId)">
              复制
            </el-button>
          </el-descriptions-item>
        </el-descriptions>
        <el-collapse class="change-collapse">
          <el-collapse-item title="变更前数据" name="before">
            <pre class="json-block">{{ prettyJson(changeDetail.before) }}</pre>
          </el-collapse-item>
          <el-collapse-item title="变更后数据" name="after">
            <pre class="json-block">{{ prettyJson(changeDetail.after) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 6px;
}

.page-toolbar-heading {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.page-toolbar-desc {
  font-size: 12px;
  color: #8a94a6;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 3px 0 14px;
}

.filter-input {
  width: 180px;
}

.filter-date {
  width: 260px;
}

.filter-select {
  width: 130px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.mono {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  color: #4a5568;
}

.text-danger {
  color: #e45656;
}

.detail-block {
  margin-top: 16px;
}

.detail-label {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2d3d;
}

.json-block {
  max-height: 240px;
  margin: 0;
  padding: 10px 12px;
  overflow: auto;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #2d3748;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f7f9fc;
  border: 1px solid #e8edf5;
  border-radius: 6px;
}

.change-collapse {
  margin-top: 16px;
}
</style>
