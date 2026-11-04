<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Bell,
  DataAnalysis,
  DocumentChecked,
  FirstAidKit,
  Odometer,
  Tickets,
  TrendCharts,
  WarningFilled
} from '@element-plus/icons-vue'
import { dashboardApi } from '@/api/report'
import type { DashboardSummary, DashboardTrend } from '@/types/report'
import { useUserStore } from '@/stores/user'
import { resolveRoleName } from '@/utils/roles'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const error = ref('')
const summary = ref<DashboardSummary>()
const trend = ref<DashboardTrend>()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const roleName = computed(() => {
  for (const role of userStore.roles) {
    const matched = resolveRoleName(role)
    if (matched) return matched
  }
  return '普通员工'
})

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 星期${week}`
})

const percent = (value?: number): string => `${((value ?? 0) * 100).toFixed(1)}%`

interface StatCard {
  key: string
  title: string
  value: string
  hint: string
  icon: unknown
  color: string
  path?: string
  warn?: boolean
}

const cards = computed<StatCard[]>(() => {
  const s = summary.value
  if (!s) return []
  return [
    {
      key: 'policy',
      title: '制度总数',
      value: String(s.policies.total),
      hint: `有效制度 ${s.policies.effective}`,
      icon: DocumentChecked,
      color: '#2f6fed',
      path: '/icp/policy'
    },
    {
      key: 'risk',
      title: '风险总数',
      value: String(s.risks.total),
      hint: `高风险 ${s.risks.high}`,
      icon: WarningFilled,
      color: '#e6a23c',
      path: '/icp/risk'
    },
    {
      key: 'coverage',
      title: '控制矩阵覆盖率',
      value: percent(s.coverage.coverageRate),
      hint: `${s.coverage.coveredRisks} / ${s.coverage.totalRisks} 项风险已覆盖`,
      icon: Odometer,
      color: '#16a6a6',
      path: '/icp/matrix'
    },
    {
      key: 'defect',
      title: '缺陷总数',
      value: String(s.defects.total),
      hint: `未关闭 ${s.defects.unclosed}`,
      icon: FirstAidKit,
      color: '#f56c6c',
      path: '/icp/defect'
    },
    {
      key: 'rectify',
      title: '整改完成率',
      value: percent(s.rectify.rate),
      hint: `已通过 ${s.rectify.approved} / ${s.rectify.total}`,
      icon: TrendCharts,
      color: '#6f5bd1',
      path: '/icp/rectify'
    },
    {
      key: 'todo',
      title: '待办审批',
      value: String(s.workflow.todo),
      hint: `超时流程 ${s.workflow.overdue}`,
      icon: Tickets,
      color: '#409eff',
      path: '/wf/todo'
    },
    {
      key: 'task',
      title: '超期任务',
      value: String(s.tasks.overdue),
      hint: '协同任务逾期未完成',
      icon: DataAnalysis,
      color: '#e6a23c',
      path: '/coll/task',
      warn: s.tasks.overdue > 0
    },
    {
      key: 'message',
      title: '未读消息',
      value: String(s.messages.unread),
      hint: '流程提醒与协作通知',
      icon: Bell,
      color: '#67c23a',
      path: '/coll/message',
      warn: s.messages.unread > 0
    }
  ]
})

const quickActions = computed(() => {
  const list: Array<{ title: string; path: string; type: 'primary' | 'warning' | 'default' }> = []
  if (userStore.hasPermission('workflow:todo')) {
    list.push({ title: '查看待办审批', path: '/wf/todo', type: 'primary' })
  }
  if (userStore.hasPermission('collab:message:list')) {
    list.push({ title: '消息中心', path: '/coll/message', type: 'default' })
  }
  if (userStore.hasPermission('report:alert:view')) {
    list.push({ title: '预警中心', path: '/report/alert', type: 'warning' })
  }
  return list
})

const chartRef = ref<HTMLDivElement>()
let chart: EChartsInstance | null = null

function trendOption(data: DashboardTrend): EChartsOption {
  return {
    grid: { left: 44, right: 44, top: 48, bottom: 32 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增制度', '新增缺陷', '流程平均耗时'], top: 8, right: 12 },
    xAxis: { type: 'category', data: data.labels, boundaryGap: true },
    yAxis: [
      { type: 'value', name: '数量', minInterval: 1 },
      { type: 'value', name: '小时', splitLine: { show: false } }
    ],
    series: [
      {
        name: '新增制度',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: '#2f6fed', borderRadius: [4, 4, 0, 0] },
        data: data.newPolicies
      },
      {
        name: '新增缺陷',
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { color: '#f56c6c', borderRadius: [4, 4, 0, 0] },
        data: data.newDefects
      },
      {
        name: '流程平均耗时',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        itemStyle: { color: '#16a6a6' },
        data: data.avgProcessHours
      }
    ]
  }
}

function renderChart(): void {
  if (!trend.value || !chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption(trendOption(trend.value), true)
}

function resizeChart(): void {
  chart?.resize()
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [summaryData, trendData] = await Promise.all([
      dashboardApi.summary(),
      dashboardApi.trend(6)
    ])
    summary.value = summaryData
    trend.value = trendData
    await Promise.resolve()
    renderChart()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

function go(path?: string): void {
  if (path) router.push(path)
}

onMounted(() => {
  load()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div>
        <div class="page-toolbar-heading">工作台</div>
        <div class="page-toolbar-desc">内控运营关键指标与流程趋势一屏总览（数据实时来自业务库）</div>
      </div>
      <div class="page-toolbar-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" :closable="false" class="mb-12">
      <template #title>
        看板数据加载失败：{{ error }}
        <el-button link type="primary" @click="load">重试</el-button>
      </template>
    </el-alert>

    <div class="dash-hero">
      <div class="dash-hero__left">
        <div class="dash-hero__title">{{ greeting }}，{{ userStore.user?.realName || '同事' }}</div>
        <div class="dash-hero__desc">{{ roleName }} · {{ today }} · 数据范围按角色授权自动过滤</div>
      </div>
      <div class="dash-hero__actions">
        <el-button
          v-for="action in quickActions"
          :key="action.path"
          :type="action.type"
          @click="go(action.path)"
        >
          {{ action.title }}
        </el-button>
      </div>
    </div>

    <el-skeleton v-if="loading && !summary" :rows="6" animated class="dash-skeleton" />

    <template v-else>
      <el-row :gutter="16">
        <el-col v-for="card in cards" :key="card.key" :xs="12" :sm="8" :md="6" :lg="6" :xl="3">
          <div class="dash-card" :class="{ 'is-warn': card.warn }" @click="go(card.path)">
            <div class="dash-card__head">
              <span class="dash-card__title">{{ card.title }}</span>
              <el-icon :style="{ color: card.color }"><component :is="card.icon" /></el-icon>
            </div>
            <div class="dash-card__value" :style="{ color: card.color }">{{ card.value }}</div>
            <div class="dash-card__hint">{{ card.hint }}</div>
          </div>
        </el-col>
      </el-row>

      <el-card shadow="never" class="mt-16">
        <template #header>
          <div class="dash-section-head">
            <span>近 6 个月趋势</span>
            <span class="dash-section-desc">新增制度 / 新增缺陷（柱） + 流程平均耗时（折线）</span>
          </div>
        </template>
        <el-empty v-if="!trend || !trend.labels.length" description="暂无趋势数据" :image-size="90" />
        <div v-else ref="chartRef" class="dash-chart" />
      </el-card>

      <el-card shadow="never" class="mt-16">
        <template #header>
          <div class="dash-section-head">
            <span>快捷入口</span>
            <span class="dash-section-desc">按权限动态展示</span>
          </div>
        </template>
        <el-empty v-if="!quickActions.length" description="当前角色暂无可用快捷入口" :image-size="90" />
        <div v-else class="dash-quick">
          <el-button
            v-for="action in quickActions"
            :key="action.path"
            :type="action.type"
            plain
            @click="go(action.path)"
          >
            {{ action.title }}
          </el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.dash-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: linear-gradient(120deg, #eef4ff 0%, #f7fbff 60%, #eefaf8 100%);
}

.dash-hero__title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

.dash-hero__desc {
  margin-top: 6px;
  font-size: 12px;
  color: #5c6b7a;
}

.dash-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dash-card {
  padding: 16px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.dash-card:hover {
  border-color: #c6d8ff;
  box-shadow: 0 4px 14px rgb(47 111 237 / 10%);
  transform: translateY(-1px);
}

.dash-card.is-warn {
  border-color: #f8d7a3;
}

.dash-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dash-card__title {
  font-size: 13px;
  color: #5c6b7a;
}

.dash-card__value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
}

.dash-card__hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.dash-chart {
  height: 320px;
}

.dash-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.dash-section-desc {
  font-size: 12px;
  color: #909399;
}

.dash-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dash-skeleton {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
}
</style>
