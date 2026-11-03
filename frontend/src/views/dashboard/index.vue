<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  DocumentChecked,
  FirstAidKit,
  Odometer,
  Tickets,
  TrendCharts
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { resolveRoleName } from '@/utils/roles'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'

const router = useRouter()
const userStore = useUserStore()

const DATA_SCOPE_MAP: Record<string, string> = {
  系统管理员: '全量数据',
  内控管理员: '内控域数据',
  部门负责人: '本部门及下级数据',
  普通员工: '本人数据',
  审计员: '全量只读',
  管理层: '全量只读'
}

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

const dataScope = computed(() => DATA_SCOPE_MAP[roleName.value] || '按角色授权范围')

/** 工作台统计卡片（M1 为演示数据，M2 接入 /api/v1/dashboard/statistics） */
const stats = [
  {
    key: 'approval',
    title: '待办审批',
    value: '12',
    unit: '件',
    trend: '较昨日 +3',
    trendUp: true,
    icon: Tickets,
    color: '#2f6fed',
    path: '/wf/todo'
  },
  {
    key: 'evaluation',
    title: '进行中评价',
    value: '5',
    unit: '项',
    trend: '本月新增 2 项',
    trendUp: true,
    icon: DocumentChecked,
    color: '#16a6a6',
    path: '/icp/evaluation'
  },
  {
    key: 'defect',
    title: '待整改缺陷',
    value: '7',
    unit: '项',
    trend: '其中高风险 2 项',
    trendUp: false,
    icon: FirstAidKit,
    color: '#e6a23c',
    path: '/icp/defect'
  },
  {
    key: 'policy',
    title: '制度覆盖率',
    value: '86',
    unit: '%',
    trend: '较上月 +4%',
    trendUp: true,
    icon: Odometer,
    color: '#6f5bd1',
    path: '/icp/policy'
  }
]

/** 快捷入口：无按钮权限时由 v-perm 隐藏 */
const quickActions = [
  { title: '发起内控评价', path: '/icp/evaluation', perm: 'icp:evaluation:add', type: 'primary' as const },
  { title: '登记缺陷', path: '/icp/defect', perm: 'icp:defect:add', type: 'warning' as const },
  { title: '查看待办审批', path: '/wf/todo', perm: '', type: 'default' as const }
]

const recentTodos = [
  { type: '制度审批', title: '《采购管理制度（2026 修订版）》发布审批', owner: '内控与审计部', status: '待审批', tag: 'warning' },
  { type: '评价任务', title: '2026 年度内部控制评价 —— 财务部现场评价', owner: '审计部', status: '进行中', tag: 'primary' },
  { type: '缺陷整改', title: '资金支付双人复核执行不到位问题整改复核', owner: '财务部', status: '待复核', tag: 'danger' },
  { type: '协同任务', title: '季度内控运行分析报告数据收集', owner: '综合管理部', status: '进行中', tag: 'primary' }
]

const lineRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
let lineChart: EChartsInstance | null = null
let pieChart: EChartsInstance | null = null

const lineOption: EChartsOption = {
  color: ['#2f6fed'],
  tooltip: { trigger: 'axis' },
  grid: { top: 24, left: 8, right: 16, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['6月', '7月', '8月', '9月', '10月', '11月'],
    axisLine: { lineStyle: { color: '#d7deea' } },
    axisLabel: { color: '#5d7599' }
  },
  yAxis: {
    type: 'value',
    min: 70,
    max: 100,
    axisLabel: { color: '#5d7599', formatter: '{value} 分' },
    splitLine: { lineStyle: { color: '#eef2f7' } }
  },
  series: [
    {
      name: '内控评价得分',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: [82.5, 84, 83.6, 86.2, 88.1, 90],
      areaStyle: { opacity: 0.12 },
      lineStyle: { width: 3 }
    }
  ]
}

const pieOption: EChartsOption = {
  tooltip: { trigger: 'item', formatter: '{b}：{c} 项（{d}%）' },
  legend: { bottom: 0, icon: 'circle', textStyle: { color: '#5d7599' } },
  series: [
    {
      name: '风险等级分布',
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: [
        { name: '高风险', value: 6, itemStyle: { color: '#f56c6c' } },
        { name: '中风险', value: 14, itemStyle: { color: '#e6a23c' } },
        { name: '低风险', value: 23, itemStyle: { color: '#67c23a' } }
      ]
    }
  ]
}

function initCharts(): void {
  if (lineRef.value) {
    lineChart = echarts.init(lineRef.value)
    lineChart.setOption(lineOption)
  }
  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption(pieOption)
  }
}

function resizeCharts(): void {
  lineChart?.resize()
  pieChart?.resize()
}

onMounted(() => {
  initCharts()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  lineChart?.dispose()
  pieChart?.dispose()
  lineChart = null
  pieChart = null
})

function go(path: string): void {
  if (!path) return
  // 后端按角色返回菜单：当前角色未开通的模块不跳转，避免落到 403
  if (path !== '/dashboard' && !userStore.menuPaths.has(path)) {
    ElMessage.info('当前角色未开通该功能模块')
    return
  }
  router.push(path)
}
</script>

<template>
  <div class="dashboard">
    <section class="welcome-card">
      <div class="welcome-main">
        <h2 class="welcome-title">{{ greeting }}，{{ userStore.displayName }}</h2>
        <p class="welcome-sub">
          当前角色：{{ userStore.primaryRole }} · 数据范围：{{ dataScope }} ·
          <span v-if="userStore.mockMode" class="welcome-mock">演示模式（未连接后端）</span>
          <span v-else>已连接业务后端</span>
        </p>
        <div class="welcome-actions">
          <el-button
            v-for="action in quickActions"
            :key="action.path"
            v-perm="action.perm || undefined"
            :type="action.type"
            size="small"
            @click="go(action.path)"
          >
            {{ action.title }}
          </el-button>
        </div>
      </div>
      <div class="welcome-badge">
        <el-icon :size="26"><DataAnalysis /></el-icon>
        <span>内控运行总览</span>
      </div>
    </section>

    <section class="stat-grid">
      <div
        v-for="stat in stats"
        :key="stat.key"
        class="stat-card"
        @click="go(stat.path)"
      >
        <div class="stat-icon" :style="{ backgroundColor: `${stat.color}1a`, color: stat.color }">
          <el-icon :size="22"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-title">{{ stat.title }}</div>
          <div class="stat-value">
            {{ stat.value }}<span class="stat-unit">{{ stat.unit }}</span>
          </div>
          <div class="stat-trend" :class="{ 'trend-down': !stat.trendUp }">
            <el-icon :size="12"><TrendCharts /></el-icon>
            {{ stat.trend }}
          </div>
        </div>
      </div>
    </section>

    <section class="chart-grid">
      <div class="page-card chart-card">
        <div class="flex-between card-head">
          <h3 class="card-title">内控评价得分趋势</h3>
          <el-tag type="info" effect="plain" size="small">演示数据</el-tag>
        </div>
        <div ref="lineRef" class="chart-body"></div>
      </div>

      <div class="page-card chart-card">
        <div class="flex-between card-head">
          <h3 class="card-title">风险等级分布</h3>
          <el-tag type="info" effect="plain" size="small">演示数据</el-tag>
        </div>
        <div ref="pieRef" class="chart-body"></div>
      </div>
    </section>

    <section class="page-card todo-card">
      <div class="flex-between card-head">
        <h3 class="card-title">近期待办</h3>
        <el-button text type="primary" @click="go('/wf/todo')">进入审批中心</el-button>
      </div>
      <el-table :data="recentTodos" size="small" stripe>
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="title" label="事项" min-width="280" show-overflow-tooltip />
        <el-table-column prop="owner" label="发起部门" width="140" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.tag" size="small" effect="light">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  color: #ffffff;
  background: linear-gradient(120deg, #123a63 0%, #2f6fed 100%);
  border-radius: 10px;
}

.welcome-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}

.welcome-sub {
  margin: 0 0 14px;
  font-size: 13px;
  color: #d4e2f5;
}

.welcome-mock {
  color: #ffd666;
}

.welcome-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.welcome-badge {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
  padding: 8px 14px;
  font-size: 12px;
  color: #d4e2f5;
  background-color: rgba(255, 255, 255, 0.12);
  border-radius: 8px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px;
  cursor: pointer;
  background-color: #ffffff;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(16, 42, 82, 0.06);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.stat-card:hover {
  box-shadow: 0 6px 18px rgba(16, 42, 82, 0.12);
  transform: translateY(-2px);
}

.stat-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
}

.stat-body {
  min-width: 0;
}

.stat-title {
  font-size: 13px;
  color: #5d7599;
}

.stat-value {
  margin: 4px 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 30px;
  color: #1f2d3d;
}

.stat-unit {
  margin-left: 2px;
  font-size: 13px;
  font-weight: 400;
  color: #8a94a6;
}

.stat-trend {
  display: flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: #16a6a6;
}

.stat-trend.trend-down {
  color: #e6a23c;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
}

.card-head {
  margin-bottom: 8px;
}

.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.chart-body {
  width: 100%;
  height: 280px;
}

.todo-card {
  padding-bottom: 8px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .welcome-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
  }

  .welcome-badge {
    display: none;
  }

  .stat-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .chart-body {
    height: 240px;
  }
}
</style>
