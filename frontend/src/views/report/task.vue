<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { reportApi } from '@/api/report'
import type { ProcessDurationReport, TaskStatReport } from '@/types/report'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'

const months = ref(6)
const loading = ref(false)
const error = ref('')
const data = ref<ProcessDurationReport>()
const taskStats = ref<TaskStatReport>()

const statusCount = (status: number | string): number =>
  taskStats.value?.items.find((item) => String(item.status) === String(status))?.count ?? 0
const taskCards = computed(() => [
  { label: '任务总数', value: taskStats.value?.total ?? 0, color: '#2f6fed' },
  { label: '待开始', value: statusCount(0), color: '#909399' },
  { label: '进行中', value: statusCount(1), color: '#2f6fed' },
  { label: '已完成', value: statusCount(2), color: '#16a6a6' },
  { label: '逾期', value: taskStats.value?.overdue ?? 0, color: '#f56c6c' }
])

const chartRef = ref<HTMLDivElement>()
const monthChartRef = ref<HTMLDivElement>()
let chart: EChartsInstance | null = null
let monthChart: EChartsInstance | null = null

const hours = (value?: number): string => `${(value ?? 0).toFixed(1)} 小时`

function renderCharts(): void {
  const report = data.value
  if (!report) return
  if (chartRef.value) {
    if (!chart) chart = echarts.init(chartRef.value)
    const option: EChartsOption = {
      grid: { left: 52, right: 24, top: 30, bottom: 44 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: report.items.map((item) => item.processName || item.processKey),
        axisLabel: { interval: 0, width: 90, overflow: 'truncate' }
      },
      yAxis: { type: 'value', name: '小时' },
      series: [
        {
          name: '平均耗时',
          type: 'bar',
          barMaxWidth: 26,
          itemStyle: { color: '#2f6fed', borderRadius: [4, 4, 0, 0] },
          data: report.items.map((item) => item.avgHours)
        },
        {
          name: 'P95 耗时',
          type: 'bar',
          barMaxWidth: 26,
          itemStyle: { color: '#e6a23c', borderRadius: [4, 4, 0, 0] },
          data: report.items.map((item) => item.p95Hours)
        }
      ]
    }
    chart.setOption(option, true)
  }
  if (monthChartRef.value) {
    if (!monthChart) monthChart = echarts.init(monthChartRef.value)
    const option: EChartsOption = {
      grid: { left: 48, right: 24, top: 36, bottom: 40 },
      tooltip: { trigger: 'axis' },
      legend: { top: 6 },
      xAxis: { type: 'category', data: report.byMonth.map((item) => item.month) },
      yAxis: [
        { type: 'value', name: '小时' },
        { type: 'value', name: '实例数', minInterval: 1, splitLine: { show: false } }
      ],
      series: [
        {
          name: '平均耗时',
          type: 'line',
          smooth: true,
          itemStyle: { color: '#16a6a6' },
          data: report.byMonth.map((item) => item.avgHours)
        },
        {
          name: '完成实例数',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 22,
          itemStyle: { color: '#c6d8ff' },
          data: report.byMonth.map((item) => item.count)
        }
      ]
    }
    monthChart.setOption(option, true)
  }
}

function resizeCharts(): void {
  chart?.resize()
  monthChart?.resize()
}

async function loadTasks(): Promise<void> {
  try {
    taskStats.value = await reportApi.tasks()
  } catch {
    // 后端未提供 /reports/tasks 时降级：不展示任务分布，仅提示口径缺口
    taskStats.value = undefined
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    data.value = await reportApi.processDuration({ months: months.value })
    await loadTasks()
    await Promise.resolve()
    renderCharts()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '流程耗时加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  chart?.dispose()
  monthChart?.dispose()
  chart = null
  monthChart = null
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div>
        <div class="page-toolbar-heading">任务与流程统计</div>
        <div class="page-toolbar-desc">6 类流程平均耗时与 P95 耗时，以及月度完成趋势（仅统计已完成实例）</div>
      </div>
      <div class="page-toolbar-actions">
        <el-select v-model="months" class="toolbar-select" @change="load">
          <el-option :value="3" label="近 3 个月" />
          <el-option :value="6" label="近 6 个月" />
          <el-option :value="12" label="近 12 个月" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" :closable="false" class="mb-12">
      <template #title>
        {{ error }}
        <el-button link type="primary" @click="load">重试</el-button>
      </template>
    </el-alert>

    <el-row v-if="taskStats" :gutter="16" class="mb-16">
      <el-col v-for="card in taskCards" :key="card.label" :xs="12" :sm="8" :md="4">
        <el-card shadow="never">
          <div class="stat">
            <div class="stat__label">{{ card.label }}</div>
            <div class="stat__value" :style="{ color: card.color }">{{ card.value }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-alert
      v-else
      type="info"
      :closable="false"
      class="mb-12"
      title="任务状态分布：后端尚未提供 /reports/tasks 聚合接口（Lead 已向 backend 追加最小端点），本页暂仅展示流程耗时与月份趋势，不在前端估算造假数据。"
    />

    <el-card shadow="never" v-loading="loading" class="mb-16">
      <template #header>
        <div class="card-head">
          <span>流程平均耗时</span>
          <span class="card-head__desc">平均 / P95（小时）</span>
        </div>
      </template>
      <el-empty v-if="!data || !data.items.length" description="暂无流程耗时数据" :image-size="90" />
      <div v-else ref="chartRef" class="chart" />
    </el-card>

    <el-row :gutter="16">
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>流程耗时明细</span>
              <span class="card-head__desc">共 {{ data?.items.length ?? 0 }} 个流程</span>
            </div>
          </template>
          <el-table :data="data?.items ?? []" empty-text="暂无流程耗时数据" size="small">
            <el-table-column prop="processName" label="流程" min-width="150" show-overflow-tooltip />
            <el-table-column prop="total" label="完成数" width="90" align="center" />
            <el-table-column label="平均耗时" width="110" align="center">
              <template #default="{ row }">{{ hours(row.avgHours) }}</template>
            </el-table-column>
            <el-table-column label="P95 耗时" width="110" align="center">
              <template #default="{ row }">{{ hours(row.p95Hours) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>月度完成趋势</span>
              <span class="card-head__desc">平均耗时 / 完成实例数</span>
            </div>
          </template>
          <el-empty v-if="!data || !data.byMonth.length" description="暂无月度数据" :image-size="90" />
          <div v-else ref="monthChartRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat {
  text-align: center;
  padding: 4px 0;
}

.stat__label {
  font-size: 13px;
  color: #5c6b7a;
}

.stat__value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.card-head__desc {
  font-size: 12px;
  color: #909399;
}

.chart {
  height: 330px;
}
</style>
