<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'
import { alertApi, dashboardApi, reportApi } from '@/api/report'
import { monitorApi } from '@/api/workflow'
import { dictLabel } from '@/utils/icpDict'
import type {
  AlertCount,
  AlertRow,
  CoverageReport,
  DashboardSummary,
  DashboardTrend,
  DefectStatReport,
  ProcessDurationReport,
  RiskDistribution
} from '@/types/report'
import type { WorkflowMonitorResult } from '@/types/workflow'

const router = useRouter()

/* ---------------- 顶部时间 ---------------- */
const now = ref(new Date())
let clockTimer: number | undefined

function pad(n: number): string {
  return String(n).padStart(2, '0')
}
const clockText = computed(() => {
  const d = now.value
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

/* ---------------- 数据 ---------------- */
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)
const trend = ref<DashboardTrend | null>(null)
const defects = ref<DefectStatReport | null>(null)
const risk = ref<RiskDistribution | null>(null)
const processDuration = ref<ProcessDurationReport | null>(null)
const coverage = ref<CoverageReport | null>(null)
const alerts = ref<AlertRow[]>([])
const alertCount = ref<AlertCount | null>(null)
const monitor = ref<WorkflowMonitorResult | null>(null)

const kpis = computed(() => {
  const s = summary.value
  if (!s) return []
  const running = monitor.value ? monitor.value.stats.running || monitor.value.total : undefined
  return [
    {
      key: 'policy',
      label: '制度总数',
      value: String(s.policies.total),
      unit: '项',
      sub: `有效 ${s.policies.effective}`,
      tone: 'blue'
    },
    {
      key: 'risk',
      label: '风险总数',
      value: String(s.risks.total),
      unit: '项',
      sub: `高风险 ${s.risks.high}`,
      tone: 'orange'
    },
    {
      key: 'coverage',
      label: '控制矩阵覆盖率',
      value: fmtPct(s.coverage.coverageRate),
      unit: '',
      sub: `覆盖 ${s.coverage.coveredRisks}/${s.coverage.totalRisks}`,
      tone: 'green'
    },
    {
      key: 'defect',
      label: '缺陷总数',
      value: String(s.defects.total),
      unit: '项',
      sub: `未关闭 ${s.defects.unclosed}`,
      tone: 'red'
    },
    {
      key: 'rectify',
      label: '整改完成率',
      value: fmtPct(s.rectify.rate),
      unit: '',
      sub: `通过 ${s.rectify.approved}/${s.rectify.total}`,
      tone: 'purple'
    },
    {
      key: 'running',
      label: '运行中流程',
      value: running === undefined ? '—' : String(running),
      unit: '条',
      sub: monitor.value
        ? `已完成 ${monitor.value.stats.completed} · 超时 ${monitor.value.stats.overdue}`
        : '监控数据暂不可用',
      tone: 'cyan'
    }
  ]
})

function fmtPct(rate: number): string {
  const n = Number(rate)
  if (!Number.isFinite(n)) return '0%'
  return `${Math.round(n * 1000) / 10}%`
}

/* ---------------- 图表 ---------------- */
const trendRef = ref<HTMLDivElement>()
const riskRef = ref<HTMLDivElement>()
const defectRef = ref<HTMLDivElement>()
const processRef = ref<HTMLDivElement>()
const coverageRef = ref<HTMLDivElement>()

let trendChart: EChartsInstance | null = null
let riskChart: EChartsInstance | null = null
let defectChart: EChartsInstance | null = null
let processChart: EChartsInstance | null = null
let coverageChart: EChartsInstance | null = null

const AXIS_COLOR = '#8fa3c8'
const SPLIT_COLOR = 'rgba(143, 163, 200, 0.16)'

function baseGrid(): EChartsOption['grid'] {
  return { left: 44, right: 24, top: 44, bottom: 28, containLabel: true }
}

function trendOption(data: DashboardTrend): EChartsOption {
  const hasHours = data.avgProcessHours.some((v) => v > 0)
  return {
    backgroundColor: 'transparent',
    grid: baseGrid(),
    tooltip: { trigger: 'axis' },
    legend: {
      data: hasHours ? ['新增制度', '新增缺陷', '流程耗时'] : ['新增制度', '新增缺陷'],
      top: 6,
      right: 20,
      itemGap: 16,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#b9c8e0', fontSize: 12 }
    },
    xAxis: {
      type: 'category',
      data: data.labels,
      axisLine: { lineStyle: { color: SPLIT_COLOR } },
      axisLabel: { color: AXIS_COLOR }
    },
    yAxis: [
      {
        type: 'value',
        name: '数量',
        minInterval: 1,
        nameTextStyle: { color: AXIS_COLOR },
        axisLabel: { color: AXIS_COLOR },
        splitLine: { lineStyle: { color: SPLIT_COLOR } }
      },
      ...(hasHours
        ? [
            {
              type: 'value' as const,
              name: '小时',
              nameTextStyle: { color: AXIS_COLOR },
              axisLabel: { color: AXIS_COLOR },
              splitLine: { show: false }
            }
          ]
        : [])
    ],
    series: [
      {
        name: '新增制度',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: '#3d8bfd', borderRadius: [3, 3, 0, 0] },
        data: data.newPolicies
      },
      {
        name: '新增缺陷',
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: '#f56c6c', borderRadius: [3, 3, 0, 0] },
        data: data.newDefects
      },
      ...(hasHours
        ? [
            {
              name: '流程耗时',
              type: 'line' as const,
              smooth: true,
              yAxisIndex: 1,
              symbolSize: 6,
              itemStyle: { color: '#22d3b8' },
              lineStyle: { width: 2 },
              data: data.avgProcessHours
            }
          ]
        : [])
    ]
  }
}

function riskOption(data: RiskDistribution): EChartsOption {
  const colors = ['#f56c6c', '#f5a623', '#22d3b8', '#3d8bfd', '#a78bfa']
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: {
      orient: 'vertical',
      right: 6,
      top: 'middle',
      textStyle: { color: '#b9c8e0', fontSize: 12 },
      itemWidth: 10,
      itemHeight: 10
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['38%', '54%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#0b1c33', borderWidth: 2 },
        label: { color: '#dbe6f7', fontSize: 12, formatter: '{b}\n{c}' },
        data: data.items.map((item, index) => ({
          name: dictLabel('icp_risk_level', item.name),
          value: item.value,
          itemStyle: { color: colors[index % colors.length] }
        }))
      }
    ]
  }
}

const SEVERITY_ORDER = ['重大', '一般', '轻微']
const DEFECT_STATUS_ORDER = ['已登记', '整改中', '待复核', '已关闭']
const DEFECT_STATUS_COLORS = ['#f5a623', '#3d8bfd', '#a78bfa', '#22d3b8']

function severityName(value: number | string): string {
  const label = dictLabel('icp_defect_severity', value)
  return label === String(value) ? `等级${value}` : label
}
function defectStatusName(value: number | string): string {
  const label = dictLabel('icp_defect_status', value)
  return label === String(value) ? `状态${value}` : label
}

function defectOption(data: DefectStatReport): EChartsOption {
  const bucket = new Map<string, Map<string, number>>()
  SEVERITY_ORDER.forEach((severity) => bucket.set(severity, new Map()))
  data.items.forEach((item) => {
    const severity = severityName(item.severity)
    const status = defectStatusName(item.status)
    if (!bucket.has(severity)) bucket.set(severity, new Map())
    const row = bucket.get(severity)!
    row.set(status, (row.get(status) ?? 0) + Number(item.count || 0))
  })
  const seriesStatuses = DEFECT_STATUS_ORDER.filter((status) =>
    Array.from(bucket.values()).some((row) => (row.get(status) ?? 0) > 0)
  )
  return {
    backgroundColor: 'transparent',
    grid: baseGrid(),
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: {
      data: seriesStatuses,
      top: 6,
      right: 8,
      itemWidth: 10,
      itemHeight: 8,
      textStyle: { color: '#b9c8e0', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: Array.from(bucket.keys()),
      axisLine: { lineStyle: { color: SPLIT_COLOR } },
      axisLabel: { color: AXIS_COLOR }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: AXIS_COLOR },
      splitLine: { lineStyle: { color: SPLIT_COLOR } }
    },
    series: seriesStatuses.map((status, index) => ({
      name: status,
      type: 'bar',
      stack: 'defect',
      barMaxWidth: 34,
      itemStyle: { color: DEFECT_STATUS_COLORS[index % DEFECT_STATUS_COLORS.length] },
      data: Array.from(bucket.values()).map((row) => row.get(status) ?? 0)
    }))
  }
}

/** 初始数据中流程办结耗时可能全部为 0：此时退化为展示办结量，避免空白图表 */
const processFallback = computed(() => {
  const items = processDuration.value?.items ?? []
  return items.length > 0 && items.every((item) => !(item.avgHours > 0))
})

function processOption(data: ProcessDurationReport): EChartsOption {
  const fallback = processFallback.value
  const items = [...data.items]
    .sort((a, b) => (fallback ? b.total - a.total : b.avgHours - a.avgHours))
    .slice(0, 6)
  const values = items.map((item) => (fallback ? item.total : Number(item.avgHours.toFixed(1))))
  return {
    backgroundColor: 'transparent',
    grid: { left: 44, right: 30, top: 24, bottom: 24, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: fallback ? '{b}<br/>办结量：{c} 件' : '{b}<br/>平均耗时：{c} 小时'
    },
    xAxis: {
      type: 'value',
      name: fallback ? '件' : '小时',
      nameTextStyle: { color: AXIS_COLOR },
      axisLabel: { color: AXIS_COLOR },
      splitLine: { lineStyle: { color: SPLIT_COLOR } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: items.map((item) => item.processName),
      axisLine: { lineStyle: { color: SPLIT_COLOR } },
      axisLabel: { color: AXIS_COLOR, fontSize: 11, width: 92, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 18,
        itemStyle: { color: '#3d8bfd', borderRadius: [0, 3, 3, 0] },
        label: { show: fallback, position: 'right', color: '#dbe6f7', fontSize: 11 },
        data: values
      }
    ]
  }
}

function coverageOption(data: CoverageReport): EChartsOption {
  const rows = [...data.byDept].sort((a, b) => b.rate - a.rate).slice(0, 8)
  return {
    backgroundColor: 'transparent',
    grid: { left: 44, right: 52, top: 20, bottom: 24, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: '{b}<br/>覆盖率：{c}%'
    },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: AXIS_COLOR, formatter: '{value}%' },
      splitLine: { lineStyle: { color: SPLIT_COLOR } }
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: rows.map((row) => row.deptName),
      axisLine: { lineStyle: { color: SPLIT_COLOR } },
      axisLabel: { color: AXIS_COLOR, fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 16,
        label: { show: true, position: 'right', color: '#dbe6f7', fontSize: 11, formatter: '{c}%' },
        itemStyle: { borderRadius: [0, 3, 3, 0], color: '#22d3b8' },
        data: rows.map((row) => Math.round(row.rate * 1000) / 10)
      }
    ]
  }
}

function renderAll(): void {
  if (trendChart && trend.value) trendChart.setOption(trendOption(trend.value), true)
  if (riskChart && risk.value) riskChart.setOption(riskOption(risk.value), true)
  if (defectChart && defects.value) defectChart.setOption(defectOption(defects.value), true)
  if (processChart && processDuration.value) processChart.setOption(processOption(processDuration.value), true)
  if (coverageChart && coverage.value) coverageChart.setOption(coverageOption(coverage.value), true)
}

async function initCharts(): Promise<void> {
  await nextTick()
  if (trendRef.value && !trendChart) trendChart = echarts.init(trendRef.value)
  if (riskRef.value && !riskChart) riskChart = echarts.init(riskRef.value)
  if (defectRef.value && !defectChart) defectChart = echarts.init(defectRef.value)
  if (processRef.value && !processChart) processChart = echarts.init(processRef.value)
  if (coverageRef.value && !coverageChart) coverageChart = echarts.init(coverageRef.value)
  renderAll()
}

function resizeAll(): void {
  trendChart?.resize()
  riskChart?.resize()
  defectChart?.resize()
  processChart?.resize()
  coverageChart?.resize()
}

/* ---------------- 预警滚动 ---------------- */
const alertTypeLabels: Record<string, string> = {
  task_overdue: '任务逾期',
  policy_expiring: '制度到期',
  defect_high_risk: '高风险缺陷',
  process_overdue: '流程超时'
}
const alertOffset = ref(0)
let alertTimer: number | undefined

const visibleAlerts = computed(() => {
  if (alerts.value.length <= 5) return alerts.value
  const result: AlertRow[] = []
  for (let i = 0; i < 5; i += 1) {
    result.push(alerts.value[(alertOffset.value + i) % alerts.value.length])
  }
  return result
})

function startAlertScroll(): void {
  alertTimer = window.setInterval(() => {
    if (alerts.value.length > 5) {
      alertOffset.value = (alertOffset.value + 1) % alerts.value.length
    }
  }, 3200)
}

/* ---------------- 加载 ---------------- */
async function load(): Promise<void> {
  loading.value = true
  const results = await Promise.allSettled([
    dashboardApi.summary(),
    dashboardApi.trend(6),
    reportApi.defects(),
    reportApi.riskDistribution('level'),
    reportApi.processDuration({ months: 6 }),
    reportApi.coverage(),
    alertApi.list({ pageSize: 20 }),
    alertApi.count(),
    monitorApi.list({ pageNum: 1, pageSize: 1 })
  ])
  if (results[0].status === 'fulfilled') summary.value = results[0].value
  if (results[1].status === 'fulfilled') trend.value = results[1].value
  if (results[2].status === 'fulfilled') defects.value = results[2].value
  if (results[3].status === 'fulfilled') risk.value = results[3].value
  if (results[4].status === 'fulfilled') processDuration.value = results[4].value
  if (results[5].status === 'fulfilled') coverage.value = results[5].value
  if (results[6].status === 'fulfilled') alerts.value = results[6].value.list
  if (results[7].status === 'fulfilled') alertCount.value = results[7].value
  if (results[8].status === 'fulfilled') monitor.value = results[8].value
  renderAll()
  loading.value = false
}

let refreshTimer: number | undefined

function back(): void {
  router.push('/dashboard')
}

onMounted(() => {
  void load()
  void initCharts()
  clockTimer = window.setInterval(() => {
    now.value = new Date()
  }, 1000)
  refreshTimer = window.setInterval(() => {
    void load()
  }, 60_000)
  startAlertScroll()
  window.addEventListener('resize', resizeAll)
})

onBeforeUnmount(() => {
  if (clockTimer) window.clearInterval(clockTimer)
  if (refreshTimer) window.clearInterval(refreshTimer)
  if (alertTimer) window.clearInterval(alertTimer)
  window.removeEventListener('resize', resizeAll)
  trendChart?.dispose()
  riskChart?.dispose()
  defectChart?.dispose()
  processChart?.dispose()
  coverageChart?.dispose()
  trendChart = riskChart = defectChart = processChart = coverageChart = null
})
</script>

<template>
  <div class="screen">
    <div class="screen-glow screen-glow--a" />
    <div class="screen-glow screen-glow--b" />

    <header class="screen-header">
      <div class="screen-header__side">
        <el-button class="screen-back" text @click="back">← 返回工作台</el-button>
      </div>
      <div class="screen-header__title">
        <span class="screen-header__name">企业内控与协同运营管理平台 · 运营驾驶舱</span>
        <span class="screen-header__sub">INTERNAL CONTROL &amp; COLLABORATION OPERATION CENTER</span>
      </div>
      <div class="screen-header__side screen-header__side--right">
        <span class="screen-header__clock">{{ clockText }}</span>
      </div>
    </header>

    <main v-loading="loading" class="screen-body">
      <section class="kpi-row">
        <div v-for="item in kpis" :key="item.key" class="kpi-card" :class="`kpi-card--${item.tone}`">
          <div class="kpi-card__label">{{ item.label }}</div>
          <div class="kpi-card__value">
            {{ item.value }}
            <span v-if="item.unit" class="kpi-card__unit">{{ item.unit }}</span>
          </div>
          <div class="kpi-card__sub">{{ item.sub }}</div>
        </div>
        <div v-if="!kpis.length" class="kpi-row__empty">数据加载中…</div>
      </section>

      <section class="panel-grid">
        <div class="screen-panel screen-panel--trend">
          <div class="screen-panel__title">近 6 个月内控运行趋势</div>
          <div ref="trendRef" class="screen-chart" />
        </div>
        <div class="screen-panel screen-panel--risk">
          <div class="screen-panel__title">风险等级分布</div>
          <div ref="riskRef" class="screen-chart" />
        </div>
        <div class="screen-panel screen-panel--defect">
          <div class="screen-panel__title">缺陷分布（严重程度 × 状态）</div>
          <div ref="defectRef" class="screen-chart" />
        </div>
        <div class="screen-panel screen-panel--process">
          <div class="screen-panel__head">
            <span class="screen-panel__title">
              {{ processFallback ? '流程办结量 TOP6（件）' : '流程平均耗时 TOP6（小时）' }}
            </span>
            <span v-if="processFallback" class="screen-panel__badge">按办结数量统计</span>
          </div>
          <div ref="processRef" class="screen-chart" />
        </div>
        <div class="screen-panel screen-panel--coverage">
          <div class="screen-panel__title">部门控制矩阵覆盖率</div>
          <div ref="coverageRef" class="screen-chart" />
        </div>
        <div class="screen-panel screen-panel--alert">
          <div class="screen-panel__head">
            <span class="screen-panel__title">实时预警</span>
            <span class="screen-panel__badge" v-if="alertCount">
              共 {{ alertCount.total }} 条 · 任务 {{ alertCount.taskOverdue }} / 制度 {{ alertCount.policyExpiring }} / 缺陷 {{ alertCount.defectHighRisk }} / 流程 {{ alertCount.processOverdue }}
            </span>
          </div>
          <ul v-if="visibleAlerts.length" class="alert-list">
            <li v-for="item in visibleAlerts" :key="`${item.type}-${item.id}`" class="alert-item">
              <el-tag size="small" :type="item.level === 1 || item.level === '1' ? 'danger' : 'warning'" effect="dark">
                {{ alertTypeLabels[item.type] ?? item.type }}
              </el-tag>
              <span class="alert-item__title">{{ item.title }}</span>
              <span class="alert-item__time">
                <template v-if="item.overdueDays !== undefined && item.overdueDays > 0">已逾期 {{ item.overdueDays }} 天</template>
                <template v-else-if="item.daysLeft !== undefined">剩余 {{ item.daysLeft }} 天</template>
                <template v-else>{{ item.dueTime || '' }}</template>
              </span>
            </li>
          </ul>
          <el-empty v-else description="暂无预警" :image-size="48" class="alert-empty" />
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.screen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  padding: 0 22px 18px;
  overflow: hidden;
  color: #e6efff;
  background:
    radial-gradient(circle at 15% 0%, rgba(61, 139, 253, 0.28), transparent 42%),
    radial-gradient(circle at 88% 8%, rgba(34, 211, 184, 0.18), transparent 38%),
    linear-gradient(160deg, #071426 0%, #0a1c33 48%, #06101f 100%);
}
.screen-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}
.screen-glow--a {
  top: -120px;
  left: 12%;
  width: 420px;
  height: 300px;
  background: rgba(61, 139, 253, 0.16);
}
.screen-glow--b {
  right: 4%;
  bottom: -160px;
  width: 460px;
  height: 340px;
  background: rgba(34, 211, 184, 0.12);
}
.screen-header {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 2.4fr 1fr;
  align-items: center;
  height: 74px;
  flex: 0 0 74px;
}
.screen-header__side {
  display: flex;
  align-items: center;
}
.screen-header__side--right {
  justify-content: flex-end;
}
.screen-back {
  color: #9fb6d8 !important;
  font-size: 13px;
}
.screen-back:hover {
  color: #dbe6f7 !important;
}
.screen-header__title {
  text-align: center;
  line-height: 1.2;
}
.screen-header__name {
  display: block;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 4px;
  text-shadow: 0 0 22px rgba(61, 139, 253, 0.55);
}
.screen-header__sub {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  letter-spacing: 2.6px;
  color: #6f89ad;
}
.screen-header__clock {
  font-size: 14px;
  letter-spacing: 1.4px;
  color: #9fb6d8;
  font-variant-numeric: tabular-nums;
}
.screen-body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 14px;
}
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
  flex: 0 0 96px;
}
.kpi-row__empty {
  grid-column: span 6;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6f89ad;
}
.kpi-card {
  position: relative;
  padding: 14px 18px;
  border: 1px solid rgba(87, 129, 190, 0.32);
  border-radius: 10px;
  background: linear-gradient(150deg, rgba(18, 42, 74, 0.92), rgba(9, 24, 44, 0.86));
  box-shadow: inset 0 0 26px rgba(61, 139, 253, 0.08);
  overflow: hidden;
}
.kpi-card::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 3px;
  height: 100%;
  opacity: 0.9;
}
.kpi-card--blue::after {
  background: #3d8bfd;
}
.kpi-card--orange::after {
  background: #f5a623;
}
.kpi-card--green::after {
  background: #22d3b8;
}
.kpi-card--red::after {
  background: #f56c6c;
}
.kpi-card--purple::after {
  background: #a78bfa;
}
.kpi-card--cyan::after {
  background: #22d3ee;
}
.kpi-card__label {
  font-size: 12px;
  letter-spacing: 1px;
  color: #8fa3c8;
}
.kpi-card__value {
  margin-top: 6px;
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  color: #f2f7ff;
  font-variant-numeric: tabular-nums;
}
.kpi-card__unit {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #8fa3c8;
}
.kpi-card__sub {
  margin-top: 8px;
  font-size: 11px;
  color: #6f89ad;
}
.panel-grid {
  display: grid;
  flex: 1;
  min-height: 0;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-template-rows: 1.15fr 1fr 0.78fr;
  gap: 14px;
}
.screen-panel {
  position: relative;
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 10px 14px 8px;
  border: 1px solid rgba(87, 129, 190, 0.28);
  border-radius: 10px;
  background: linear-gradient(160deg, rgba(15, 34, 60, 0.9), rgba(8, 20, 38, 0.82));
  box-shadow: inset 0 0 34px rgba(61, 139, 253, 0.06);
}
.screen-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: #b9c8e0;
  letter-spacing: 0.6px;
}
.screen-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.screen-panel__badge {
  font-size: 11px;
  color: #6f89ad;
}
.screen-panel--trend {
  grid-column: span 4;
  grid-row: 1;
}
.screen-panel--risk {
  grid-column: span 2;
  grid-row: 1;
}
.screen-panel--defect {
  grid-column: span 2;
  grid-row: 2;
}
.screen-panel--process {
  grid-column: span 2;
  grid-row: 2;
}
.screen-panel--coverage {
  grid-column: span 2;
  grid-row: 2;
}
.screen-panel--alert {
  grid-column: span 6;
  grid-row: 3;
}
.screen-chart {
  flex: 1;
  min-height: 0;
  width: 100%;
}
.alert-list {
  flex: 1;
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
  overflow: hidden;
}
.alert-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 24px;
  line-height: 24px;
  font-size: 12px;
  border-bottom: 1px dashed rgba(143, 163, 200, 0.12);
}
.alert-item__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #dbe6f7;
}
.alert-item__time {
  color: #8fa3c8;
  font-variant-numeric: tabular-nums;
}
.alert-empty {
  --el-empty-description-color: #6f89ad;
  flex: 1;
}
</style>
