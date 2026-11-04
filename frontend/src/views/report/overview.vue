<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { reportApi } from '@/api/report'
import { deptApi } from '@/api/system'
import type { CoverageReport } from '@/types/report'
import { useUserStore } from '@/stores/user'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'

const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const report = ref<CoverageReport>()
const deptId = ref<number | string>('')
const year = ref<number>(new Date().getFullYear())
const deptOptions = ref<Array<{ id: number | string; name: string }>>([])

const canListDept = computed(() => userStore.hasPermission('system:dept:list'))

const rateText = (rate?: number): string => `${((rate ?? 0) * 100).toFixed(1)}%`

const chartRef = ref<HTMLDivElement>()
let chart: EChartsInstance | null = null

function flattenDept(list: Array<Record<string, unknown>>, depth = 0): Array<{ id: number | string; name: string }> {
  const result: Array<{ id: number | string; name: string }> = []
  for (const item of list) {
    const id = (item.id ?? '') as number | string
    const name = String(item.name ?? item.deptName ?? '未命名部门')
    result.push({ id, name: `${'　'.repeat(depth)}${name}` })
    const children = item.children
    if (Array.isArray(children) && children.length) {
      result.push(...flattenDept(children as Array<Record<string, unknown>>, depth + 1))
    }
  }
  return result
}

async function loadDepts(): Promise<void> {
  if (!canListDept.value) return
  try {
    const tree = (await deptApi.tree()) as unknown as Array<Record<string, unknown>>
    deptOptions.value = flattenDept(tree)
  } catch {
    deptOptions.value = []
  }
}

function renderChart(): void {
  const data = report.value
  if (!data || !chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const option: EChartsOption = {
    grid: { left: 44, right: 20, top: 30, bottom: 70 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: data.byCategory.map((item) => item.categoryName),
      axisLabel: { rotate: 25, interval: 0 }
    },
    yAxis: { type: 'value', name: '风险数', minInterval: 1 },
    series: [
      {
        name: '已覆盖',
        type: 'bar',
        stack: 'total',
        barMaxWidth: 28,
        itemStyle: { color: '#16a6a6' },
        data: data.byCategory.map((item) => item.covered)
      },
      {
        name: '未覆盖',
        type: 'bar',
        stack: 'total',
        barMaxWidth: 28,
        itemStyle: { color: '#e6a23c' },
        data: data.byCategory.map((item) => Math.max(0, item.total - item.covered))
      }
    ]
  }
  chart.setOption(option, true)
}

function resizeChart(): void {
  chart?.resize()
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    report.value = await reportApi.coverage({
      deptId: deptId.value === '' ? undefined : deptId.value,
      year: year.value
    })
    await Promise.resolve()
    renderChart()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '内控覆盖率加载失败'
  } finally {
    loading.value = false
  }
}

function reset(): void {
  deptId.value = ''
  year.value = new Date().getFullYear()
  load()
}

onMounted(() => {
  loadDepts()
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
        <div class="page-toolbar-heading">内控概览</div>
        <div class="page-toolbar-desc">风险与控制矩阵覆盖率总览，支持部门与年度钻取（数据按数据范围过滤）</div>
      </div>
      <div class="page-toolbar-actions">
        <el-select
          v-if="canListDept"
          v-model="deptId"
          placeholder="全部部门"
          clearable
          class="toolbar-select"
          @change="load"
        >
          <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-input-number v-model="year" :min="2000" :max="2100" controls-position="right" class="toolbar-select" />
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

    <el-card shadow="never" v-loading="loading" class="mb-12">
      <div class="coverage-hero">
        <div>
          <div class="coverage-hero__label">风险覆盖率</div>
          <div class="coverage-hero__value">{{ rateText(report?.coverageRate) }}</div>
          <div class="coverage-hero__hint">
            已覆盖 {{ report?.coveredRisks ?? 0 }} / {{ report?.totalRisks ?? 0 }} 项风险
          </div>
        </div>
        <el-progress
          type="dashboard"
          :percentage="Number(((report?.coverageRate ?? 0) * 100).toFixed(1))"
          :width="140"
          :stroke-width="12"
          color="#16a6a6"
        />
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>按部门覆盖率</span>
              <span class="card-head__desc">部门 / 已覆盖 / 覆盖率</span>
            </div>
          </template>
          <el-table :data="report?.byDept ?? []" empty-text="暂无部门覆盖数据" size="small">
            <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
            <el-table-column prop="total" label="风险数" width="90" align="center" />
            <el-table-column prop="covered" label="已覆盖" width="90" align="center" />
            <el-table-column label="覆盖率" width="110" align="center">
              <template #default="{ row }">{{ rateText(row.rate) }}</template>
            </el-table-column>
            <el-table-column label="覆盖情况" min-width="130">
              <template #default="{ row }">
                <el-progress :percentage="Number((row.rate * 100).toFixed(1))" :stroke-width="8" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :lg="12" :md="24">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>按分类覆盖率</span>
              <span class="card-head__desc">分类 / 已覆盖 / 覆盖率</span>
            </div>
          </template>
          <el-table :data="report?.byCategory ?? []" empty-text="暂无分类覆盖数据" size="small">
            <el-table-column prop="categoryName" label="风险分类" min-width="140" show-overflow-tooltip />
            <el-table-column prop="total" label="风险数" width="90" align="center" />
            <el-table-column prop="covered" label="已覆盖" width="90" align="center" />
            <el-table-column label="覆盖率" width="110" align="center">
              <template #default="{ row }">{{ rateText(row.rate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt-16">
      <template #header>
        <div class="card-head">
          <span>分类覆盖分布</span>
          <span class="card-head__desc">已覆盖 / 未覆盖风险数</span>
        </div>
      </template>
      <el-empty
        v-if="!report || !report.byCategory.length"
        description="暂无分类数据"
        :image-size="90"
      />
      <div v-else ref="chartRef" class="coverage-chart" />
    </el-card>
  </div>
</template>

<style scoped>
.coverage-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.coverage-hero__label {
  font-size: 13px;
  color: #5c6b7a;
}

.coverage-hero__value {
  margin-top: 8px;
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
  color: #16a6a6;
}

.coverage-hero__hint {
  margin-top: 10px;
  font-size: 13px;
  color: #909399;
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

.coverage-chart {
  height: 320px;
}
</style>
