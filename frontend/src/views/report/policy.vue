<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { reportApi } from '@/api/report'
import type { RiskDim, RiskDistribution } from '@/types/report'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'

const DIM_OPTIONS: Array<{ label: string; value: RiskDim }> = [
  { label: '按风险等级', value: 'level' },
  { label: '按风险分类', value: 'category' },
  { label: '按部门', value: 'dept' }
]

const dim = ref<RiskDim>('level')
const loading = ref(false)
const error = ref('')
const data = ref<RiskDistribution>()

const levelCounts = computed(() => {
  const result: Record<string, number> = { 高: 0, 中: 0, 低: 0 }
  if (dim.value !== 'level') return result
  for (const item of data.value?.items ?? []) {
    if (item.name.includes('高') || item.name === '3') result['高'] += item.value
    else if (item.name.includes('中') || item.name === '2') result['中'] += item.value
    else if (item.name.includes('低') || item.name === '1') result['低'] += item.value
  }
  return result
})

const chartRef = ref<HTMLDivElement>()
let chart: EChartsInstance | null = null
const router = useRouter()

/** 风险等级名称 → 字典值（1 低 / 2 中 / 3 高），用于图表下钻 */
function levelCodeOf(name: string): number | null {
  if (/^\d+$/.test(name)) return Number(name)
  if (name.includes('高')) return 3
  if (name.includes('中')) return 2
  if (name.includes('低')) return 1
  return null
}

function renderChart(): void {
  if (!data.value || !chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const option: EChartsOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: { orient: 'vertical', left: 8, top: 'center' },
    series: [
      {
        name: '风险分布',
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['58%', '52%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b}\n{c}' },
        data: data.value.items.map((item) => ({ name: item.name, value: item.value }))
      }
    ]
  }
  chart.setOption(option, true)
  chart.off('click')
  chart.on('click', (params) => {
    if (dim.value !== 'level') return
    const code = levelCodeOf(String(params.name ?? ''))
    if (code === null) return
    router.push({ path: '/icp/risk', query: { riskLevel: String(code) } })
  })
}

function resizeChart(): void {
  chart?.resize()
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    data.value = await reportApi.riskDistribution(dim.value)
    await Promise.resolve()
    renderChart()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '风险分布加载失败'
  } finally {
    loading.value = false
  }
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
        <div class="page-toolbar-heading">制度与风险统计</div>
        <div class="page-toolbar-desc">风险分布按等级 / 分类 / 部门钻取，数据实时聚合自风险台账</div>
      </div>
      <div class="page-toolbar-actions">
        <el-radio-group v-model="dim" @change="load">
          <el-radio-button v-for="item in DIM_OPTIONS" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert v-if="error" type="error" :closable="false" class="mb-12">
      <template #title>
        {{ error }}
        <el-button link type="primary" @click="load">重试</el-button>
      </template>
    </el-alert>

    <el-row :gutter="16" class="mb-12">
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="never" v-loading="loading">
          <div class="risk-stat">
            <div class="risk-stat__label">风险总数</div>
            <div class="risk-stat__value">{{ data?.total ?? 0 }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="never" v-loading="loading">
          <div class="risk-stat">
            <div class="risk-stat__label">高风险</div>
            <div class="risk-stat__value is-high">{{ levelCounts['高'] }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="never" v-loading="loading">
          <div class="risk-stat">
            <div class="risk-stat__label">中风险</div>
            <div class="risk-stat__value is-mid">{{ levelCounts['中'] }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6" :md="6">
        <el-card shadow="never" v-loading="loading">
          <div class="risk-stat">
            <div class="risk-stat__label">低风险</div>
            <div class="risk-stat__value is-low">{{ levelCounts['低'] }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>风险分布</span>
              <span class="card-head__desc">{{ DIM_OPTIONS.find((item) => item.value === dim)?.label }}<template v-if="dim === 'level'"> · 点击扇区可下钻</template></span>
            </div>
          </template>
          <el-empty v-if="!data || !data.items.length" description="暂无风险数据" :image-size="90" />
          <div v-else ref="chartRef" class="risk-chart" />
        </el-card>
      </el-col>
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>明细</span>
              <span class="card-head__desc">合计 {{ data?.total ?? 0 }} 项</span>
            </div>
          </template>
          <el-table :data="data?.items ?? []" empty-text="暂无风险数据" size="small">
            <el-table-column prop="name" label="维度" min-width="160" show-overflow-tooltip />
            <el-table-column prop="value" label="风险数" width="110" align="center" />
            <el-table-column label="占比" width="120" align="center">
              <template #default="{ row }">
                {{ data?.total ? ((row.value / data.total) * 100).toFixed(1) + '%' : '0.0%' }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.risk-stat {
  text-align: center;
  padding: 6px 0;
}

.risk-stat__label {
  font-size: 13px;
  color: #5c6b7a;
}

.risk-stat__value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  color: #2f6fed;
}

.risk-stat__value.is-high {
  color: #f56c6c;
}

.risk-stat__value.is-mid {
  color: #e6a23c;
}

.risk-stat__value.is-low {
  color: #16a6a6;
}

.risk-chart {
  height: 340px;
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
</style>
