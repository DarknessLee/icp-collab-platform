<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { controlApi, matrixApi, policyApi, riskApi } from '@/api/icp'
import DeptPicker from '@/components/DeptPicker.vue'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import type {
  ControlRow,
  MatrixCoverage,
  MatrixPayload,
  MatrixQuery,
  MatrixRow,
  PolicyRow,
  RiskRow
} from '@/types/icp'
import echarts, { type EChartsInstance, type EChartsOption } from '@/utils/echarts'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'
import { fetchAllPages } from '@/utils/pagedAll'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<MatrixRow>((params) => matrixApi.list(params as MatrixQuery & Record<string, unknown>), {
    keyword: '',
    coverage: undefined
  })

const userStore = useUserStore()
const showActions = computed(
  () => userStore.hasPermission('icp:matrix:edit') || userStore.hasPermission('icp:matrix:delete')
)

const coverageData = ref<MatrixCoverage>({})
const coverageLoading = ref(false)
const risks = ref<RiskRow[]>([])
const controls = ref<ControlRow[]>([])
const policies = ref<PolicyRow[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增矩阵关联')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<MatrixPayload>({
  riskId: 0,
  controlId: 0,
  policyId: undefined,
  coverage: 1,
  evidenceDesc: '',
  status: 1,
  ownerDeptId: undefined,
  remark: ''
})

const rules: FormRules = {
  riskId: [{ required: true, message: '请选择风险', trigger: 'change' }],
  controlId: [{ required: true, message: '请选择控制点', trigger: 'change' }],
  coverage: [{ required: true, message: '请选择覆盖程度', trigger: 'change' }]
}

const coverageOptions = computed(() => dictOptions('icp_matrix_coverage'))
const chartRef = ref<HTMLDivElement>()
let chart: EChartsInstance | null = null

const totalCount = computed(() => coverageData.value.total ?? total.value)
const coveredCount = computed(() => coverageData.value.covered ?? list.value.filter((item) => item.coverage === 1).length)
const rateText = computed(() => {
  const rate = coverageData.value.rate
  if (typeof rate === 'number') return `${Math.round(rate * 10) / 10}%`
  if (!totalCount.value) return '-'
  return `${Math.round((coveredCount.value / totalCount.value) * 1000) / 10}%`
})
const uncoveredCount = computed(() => {
  if (coverageData.value.total !== undefined) {
    return Math.max(0, (coverageData.value.total || 0) - (coverageData.value.covered || 0))
  }
  return list.value.filter((item) => item.coverage === 3).length
})

function coverageDistribution(): Array<{ label: string; count: number }> {
  const byLevel = coverageData.value.byLevel
  if (Array.isArray(byLevel) && byLevel.length) {
    return byLevel.map((item) => ({
      label: item.label || dictLabel('icp_matrix_coverage', item.level) || String(item.level),
      count: item.count
    }))
  }
  return coverageOptions.value.map((option) => ({
    label: option.label,
    count: list.value.filter((item) => item.coverage === option.value).length
  }))
}

function renderChart(): void {
  const el = chartRef.value
  if (!el) return
  if (!chart) chart = echarts.init(el)
  const data = coverageDistribution()
  const option: EChartsOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 16, top: 24, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: data.map((item) => item.label), axisTick: { alignWithLabel: true } },
    yAxis: { type: 'value', name: '风险数', minInterval: 1, splitLine: { lineStyle: { type: 'dashed' } } },
    series: [
      {
        name: '风险数',
        type: 'bar',
        barWidth: '42%',
        data: data.map((item, index) => ({
          value: item.count,
          itemStyle: { color: ['#2f6fed', '#f0a020', '#f56c6c'][index % 3] }
        }))
      }
    ]
  }
  chart.setOption(option, true)
}

function resizeChart(): void {
  chart?.resize()
}

async function loadCoverage(): Promise<void> {
  coverageLoading.value = true
  try {
    coverageData.value = await matrixApi.coverage()
  } catch {
    coverageData.value = {}
  } finally {
    coverageLoading.value = false
    await nextTick()
    renderChart()
  }
}

async function loadOptions(): Promise<void> {
  try {
    const [riskRows, controlRows, policyRows] = await Promise.all([
      fetchAllPages<RiskRow>((params) => riskApi.list(params)),
      fetchAllPages<ControlRow>((params) => controlApi.list(params)),
      fetchAllPages<PolicyRow>((params) => policyApi.list(params))
    ])
    risks.value = riskRows
    controls.value = controlRows
    policies.value = policyRows
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([load(), loadCoverage()])
}

function resetForm(): void {
  form.riskId = 0
  form.controlId = 0
  form.policyId = undefined
  form.coverage = 1
  form.evidenceDesc = ''
  form.status = 1
  form.ownerDeptId = undefined
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增矩阵关联'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: MatrixRow): void {
  editingId.value = row.id
  dialogTitle.value = `编辑矩阵关联 - ${row.matrixCode}`
  form.riskId = row.riskId
  form.controlId = row.controlId
  form.policyId = row.policyId
  form.coverage = row.coverage ?? 1
  form.evidenceDesc = row.evidenceDesc || ''
  form.status = row.status ?? 1
  form.ownerDeptId = row.ownerDeptId
  form.remark = row.remark || ''
  dialogVisible.value = true
}

/** 提交前做 risk_id + control_id 唯一性预检，给出明确冲突提示 */
async function findDuplicate(riskId: number, controlId: number): Promise<MatrixRow | undefined> {
  try {
    const rows = await fetchAllPages<MatrixRow>((params) => matrixApi.list(params))
    return rows.find((item) => item.id !== editingId.value && item.riskId === riskId && item.controlId === controlId)
  } catch {
    return undefined
  }
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!form.riskId || !form.controlId) {
    ElMessage.warning('请选择风险与控制点')
    return
  }
  const duplicate = await findDuplicate(Number(form.riskId), Number(form.controlId))
  if (duplicate) {
    ElMessage.warning(`该风险与控制点的关联已存在（${duplicate.matrixCode}），同一风险 + 控制点只能建立一条矩阵`)
    return
  }
  const payload: MatrixPayload = {
    ...form,
    riskId: Number(form.riskId),
    controlId: Number(form.controlId),
    coverage: Number(form.coverage),
    status: Number(form.status)
  }
  try {
    if (editingId.value === null) {
      await matrixApi.create(payload)
      ElMessage.success('矩阵关联新增成功')
    } else {
      await matrixApi.update(editingId.value, payload)
      ElMessage.success('矩阵关联修改成功')
    }
    dialogVisible.value = false
    await refreshAll()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: MatrixRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除矩阵「${row.matrixCode}」？删除后风险与控制点将解除关联。`, '删除矩阵关联', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await matrixApi.remove(row.id)
    ElMessage.success('矩阵关联删除成功')
    await refreshAll()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

watch(dialogVisible, (visible) => {
  if (visible) {
    void ensureIcpDicts()
    if (!risks.value.length || !controls.value.length) void loadOptions()
  }
})

onMounted(() => {
  void ensureIcpDicts()
  void loadOptions()
  void refreshAll()
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
    <div class="coverage-cards">
      <el-card v-loading="coverageLoading" shadow="never" class="coverage-card">
        <div class="coverage-label">风险总数</div>
        <div class="coverage-value">{{ totalCount }}<span class="coverage-unit">项</span></div>
        <div class="coverage-hint">纳入控制矩阵的风险</div>
      </el-card>
      <el-card shadow="never" class="coverage-card">
        <div class="coverage-label">已覆盖风险</div>
        <div class="coverage-value success">{{ coveredCount }}<span class="coverage-unit">项</span></div>
        <div class="coverage-hint">至少关联 1 个控制点</div>
      </el-card>
      <el-card shadow="never" class="coverage-card">
        <div class="coverage-label">未覆盖风险</div>
        <div class="coverage-value danger">{{ uncoveredCount }}<span class="coverage-unit">项</span></div>
        <div class="coverage-hint">需优先补充控制措施</div>
      </el-card>
      <el-card shadow="never" class="coverage-card">
        <div class="coverage-label">风险覆盖率</div>
        <div class="coverage-value primary">{{ rateText }}</div>
        <div class="coverage-hint">已覆盖风险 / 风险总数</div>
      </el-card>
    </div>

    <div class="matrix-body">
      <div class="page-card matrix-table-card">
        <div class="page-toolbar">
          <div class="page-toolbar-title">
            <span class="page-toolbar-heading">控制矩阵</span>
            <span class="page-toolbar-desc">风险 → 控制点 → 制度关联；同一风险 + 控制点只能建立一条矩阵</span>
          </div>
          <div class="page-toolbar-actions">
            <el-select v-model="query.coverage" class="toolbar-select" placeholder="覆盖程度" clearable @change="search">
              <el-option
                v-for="item in coverageOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input
              v-model="query.keyword"
              class="toolbar-input"
              placeholder="矩阵编码 / 风险名称"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" :icon="Plus" v-perm="'icp:matrix:add'" @click="openCreate">
              新增关联
            </el-button>
            <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无矩阵数据">
          <el-table-column prop="matrixCode" label="矩阵编码" width="130" />
          <el-table-column prop="riskName" label="风险" min-width="180" show-overflow-tooltip />
          <el-table-column prop="controlName" label="控制点" min-width="180" show-overflow-tooltip />
          <el-table-column prop="policyName" label="关联制度" min-width="160" show-overflow-tooltip />
          <el-table-column label="覆盖程度" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="dictTagType('icp_matrix_coverage', row.coverage)" size="small">
                {{ dictLabel('icp_matrix_coverage', row.coverage) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ownerDeptName" label="责任部门" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="showActions" label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" v-perm="'icp:matrix:edit'" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" v-perm="'icp:matrix:delete'" @click="removeRow(row)">删除</el-button>
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

      <el-card shadow="never" class="page-card matrix-chart-card">
        <template #header>
          <div class="chart-header">
            <span>风险覆盖分布</span>
            <el-tag size="small" type="info">数据来自 /matrices/coverage</el-tag>
          </div>
        </template>
        <div ref="chartRef" class="coverage-chart" />
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="风险" prop="riskId">
          <el-select
            v-model="form.riskId"
            filterable
            placeholder="请选择风险"
            style="width: 100%"
            :disabled="editingId !== null"
          >
            <el-option
              v-for="item in risks"
              :key="item.id"
              :label="`${item.riskCode} ${item.riskName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="控制点" prop="controlId">
          <el-select
            v-model="form.controlId"
            filterable
            placeholder="请选择控制点"
            style="width: 100%"
            :disabled="editingId !== null"
          >
            <el-option
              v-for="item in controls"
              :key="item.id"
              :label="`${item.controlCode} ${item.controlName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联制度">
          <el-select v-model="form.policyId" filterable clearable placeholder="可选：选择制度" style="width: 100%">
            <el-option
              v-for="item in policies"
              :key="item.id"
              :label="`${item.policyCode} ${item.policyName}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖程度" prop="coverage">
          <el-radio-group v-model="form.coverage">
            <el-radio-button v-for="item in coverageOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="责任部门">
          <DeptPicker v-model="form.ownerDeptId" />
        </el-form-item>
        <el-form-item label="证据说明">
          <el-input v-model="form.evidenceDesc" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.coverage-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.coverage-card {
  border: 1px solid #e6eaf2;
}

.coverage-label {
  font-size: 13px;
  color: #6b7a90;
}

.coverage-value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 600;
  color: #1f2d3d;
}

.coverage-value.success {
  color: #2f9e6a;
}

.coverage-value.danger {
  color: #d94a4a;
}

.coverage-value.primary {
  color: #2f6fed;
}

.coverage-unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: #8a94a6;
}

.coverage-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #9aa5b5;
}

.matrix-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 12px;
  align-items: start;
}

.matrix-chart-card {
  min-height: 320px;
}

.coverage-chart {
  height: 280px;
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
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

.page-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.toolbar-input {
  width: 190px;
}

.toolbar-select {
  width: 120px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

@media (max-width: 1200px) {
  .matrix-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .coverage-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
