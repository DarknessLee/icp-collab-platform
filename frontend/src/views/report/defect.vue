<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { reportApi } from '@/api/report'
import type { DefectStatReport, RectifyRateReport } from '@/types/report'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'

const SEVERITY_OPTIONS = [
  { value: 1, label: '重大' },
  { value: 2, label: '一般' },
  { value: 3, label: '轻微' }
]
const STATUS_OPTIONS = [
  { value: 0, label: '已登记' },
  { value: 1, label: '整改中' },
  { value: 2, label: '待复核' },
  { value: 3, label: '已关闭' }
]

const severity = ref<number | ''>('')
const status = ref<number | ''>('')
const loading = ref(false)
const error = ref('')
const defects = ref<DefectStatReport>()
const rectify = ref<RectifyRateReport>()

const severityText = (value: number | string): string =>
  SEVERITY_OPTIONS.find((item) => String(item.value) === String(value))?.label ?? `等级${value}`
const statusText = (value: number | string): string =>
  STATUS_OPTIONS.find((item) => String(item.value) === String(value))?.label ?? `状态${value}`

const STATUS_COLORS = ['#909399', '#2f6fed', '#e6a23c', '#67c23a']

const gradeSeverity = computed(() =>
  [...new Set((defects.value?.items ?? []).map((item) => Number(item.severity)))]
)
const gradeStatus = computed(() =>
  [...new Set((defects.value?.items ?? []).map((item) => Number(item.status)))]
)

const chartRef = ref<HTMLDivElement>()
const rateChartRef = ref<HTMLDivElement>()
let chart: EChartsInstance | null = null
let rateChart: EChartsInstance | null = null

const rateText = (value?: number): string => `${((value ?? 0) * 100).toFixed(1)}%`

function renderDefectChart(): void {
  if (!defects.value || !chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const statuses = gradeStatus.value
  const option: EChartsOption = {
    grid: { left: 44, right: 20, top: 62, bottom: 40, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 8, left: 'center', itemWidth: 14, itemHeight: 10 },
    xAxis: { type: 'category', data: gradeSeverity.value.map(severityText) },
    yAxis: { type: 'value', name: '缺陷数', minInterval: 1 },
    series: statuses.map((item, index) => ({
      name: statusText(item),
      type: 'bar',
      stack: 'defect',
      barMaxWidth: 40,
      itemStyle: { color: STATUS_COLORS[index % STATUS_COLORS.length] },
      data: gradeSeverity.value.map(
        (sev) =>
          defects.value?.items.find(
            (row) => Number(row.severity) === sev && Number(row.status) === item
          )?.count ?? 0
      )
    }))
  }
  chart.setOption(option, true)
}

function renderRateChart(): void {
  if (!rectify.value || !rateChartRef.value) return
  if (!rateChart) rateChart = echarts.init(rateChartRef.value)
  const option: EChartsOption = {
    grid: { left: 44, right: 44, top: 62, bottom: 36, containLabel: true },
    tooltip: { trigger: 'axis' },
    legend: { top: 8, left: 'center', itemWidth: 14, itemHeight: 10 },
    xAxis: { type: 'category', data: rectify.value.byMonth.map((item) => item.month) },
    yAxis: [
      { type: 'value', name: '数量', minInterval: 1 },
      { type: 'value', name: '完成率', max: 1, axisLabel: { formatter: (v: number) => `${(v * 100).toFixed(0)}%` } }
    ],
    series: [
      {
        name: '整改总数',
        type: 'bar',
        barMaxWidth: 24,
        itemStyle: { color: '#2f6fed', borderRadius: [4, 4, 0, 0] },
        data: rectify.value.byMonth.map((item) => item.total)
      },
      {
        name: '已通过',
        type: 'bar',
        barMaxWidth: 24,
        itemStyle: { color: '#16a6a6', borderRadius: [4, 4, 0, 0] },
        data: rectify.value.byMonth.map((item) => item.approved)
      },
      {
        name: '完成率',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        itemStyle: { color: '#e6a23c' },
        data: rectify.value.byMonth.map((item) => item.rate)
      }
    ]
  }
  rateChart.setOption(option, true)
}

function resizeCharts(): void {
  chart?.resize()
  rateChart?.resize()
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [defectData, rectifyData] = await Promise.all([
      reportApi.defects({
        severity: severity.value === '' ? undefined : severity.value,
        status: status.value === '' ? undefined : status.value
      }),
      reportApi.rectifyRate()
    ])
    defects.value = defectData
    rectify.value = rectifyData
    await Promise.resolve()
    renderDefectChart()
    renderRateChart()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '缺陷统计加载失败'
  } finally {
    loading.value = false
  }
}

function reset(): void {
  severity.value = ''
  status.value = ''
  load()
}

onMounted(() => {
  load()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  chart?.dispose()
  rateChart?.dispose()
  chart = null
  rateChart = null
})
</script>

<template>
  <div class="page-container">
    <div class="page-toolbar">
      <div>
        <div class="page-toolbar-heading">缺陷统计</div>
        <div class="page-toolbar-desc">缺陷按严重程度 / 状态分布，以及整改完成率按部门与月度趋势</div>
      </div>
      <div class="page-toolbar-actions">
        <el-select v-model="severity" placeholder="严重程度" clearable class="toolbar-select" @change="load">
          <el-option v-for="item in SEVERITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="status" placeholder="缺陷状态" clearable class="toolbar-select" @change="load">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" :closable="false" class="mb-12">
      <template #title>
        {{ error }}
        <el-button link type="primary" @click="load">重试</el-button>
      </template>
    </el-alert>

    <el-row :gutter="16" class="mb-12">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" v-loading="loading">
          <div class="stat">
            <div class="stat__label">缺陷总数</div>
            <div class="stat__value">{{ defects?.total ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" v-loading="loading">
          <div class="stat">
            <div class="stat__label">整改总数</div>
            <div class="stat__value">{{ rectify?.total ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" v-loading="loading">
          <div class="stat">
            <div class="stat__label">整改已通过</div>
            <div class="stat__value is-success">{{ rectify?.approved ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" v-loading="loading">
          <div class="stat">
            <div class="stat__label">整改完成率</div>
            <div class="stat__value is-warning">{{ rateText(rectify?.rate) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :lg="14" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>缺陷分布（严重程度 × 状态）</span>
              <span class="card-head__desc">高风险缺陷口径：严重程度 1（重大）且未关闭</span>
            </div>
          </template>
          <el-empty v-if="!defects || !defects.items.length" description="暂无缺陷数据" :image-size="90" />
          <div v-else ref="chartRef" class="chart" />
        </el-card>
      </el-col>
      <el-col :lg="10" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>明细</span>
              <span class="card-head__desc">合计 {{ defects?.total ?? 0 }} 条</span>
            </div>
          </template>
          <el-table :data="defects?.items ?? []" empty-text="暂无缺陷数据" size="small" max-height="320">
            <el-table-column label="严重程度" width="110" align="center">
              <template #default="{ row }">{{ severityText(row.severity) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">{{ statusText(row.status) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="数量" width="90" align="center" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt-16">
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>整改完成率（按部门）</span>
              <span class="card-head__desc">总数 / 通过 / 完成率</span>
            </div>
          </template>
          <el-table :data="rectify?.byDept ?? []" empty-text="暂无整改数据" size="small">
            <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
            <el-table-column prop="total" label="总数" width="80" align="center" />
            <el-table-column prop="approved" label="通过" width="80" align="center" />
            <el-table-column label="完成率" width="110" align="center">
              <template #default="{ row }">{{ rateText(row.rate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>整改月度趋势</span>
              <span class="card-head__desc">总数 / 已通过 / 完成率</span>
            </div>
          </template>
          <el-empty
            v-if="!rectify || !rectify.byMonth.length"
            description="暂无月度整改数据"
            :image-size="90"
          />
          <div v-else ref="rateChartRef" class="chart" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat {
  text-align: center;
  padding: 6px 0;
}

.stat__label {
  font-size: 13px;
  color: #5c6b7a;
}

.stat__value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 600;
  color: #2f6fed;
}

.stat__value.is-success {
  color: #16a6a6;
}

.stat__value.is-warning {
  color: #e6a23c;
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
  height: 320px;
}
</style>
