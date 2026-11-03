<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { evaluationApi, evaluationTaskApi } from '@/api/icp'
import DeptPicker from '@/components/DeptPicker.vue'
import FileUpload from '@/components/FileUpload.vue'
import { usePageList } from '@/composables/usePageList'
import type {
  EvaluationPayload,
  EvaluationQuery,
  EvaluationRow,
  EvaluationSubmitPayload,
  EvaluationTaskRow,
  WorkpaperRow
} from '@/types/icp'
import type { UploadResult } from '@/types/system'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'
import { fetchAllPages } from '@/utils/pagedAll'

const activeTab = ref<'plan' | 'task'>('plan')

/* ------------------------------ 评价计划 ------------------------------ */
const {
  query: planQuery,
  list: plans,
  total: planTotal,
  loading: planLoading,
  load: loadPlans,
  search: searchPlans,
  reset: resetPlans,
  handlePageChange: handlePlanPage,
  handleSizeChange: handlePlanSize
} = usePageList<EvaluationRow>((params) => evaluationApi.list(params as EvaluationQuery & Record<string, unknown>), {
  keyword: '',
  status: undefined,
  planYear: undefined
})

const planDialogVisible = ref(false)
const planDialogTitle = ref('新增评价计划')
const editingPlanId = ref<number | null>(null)
const planFormRef = ref<FormInstance>()
const planForm = reactive<EvaluationPayload>({
  planName: '',
  planYear: new Date().getFullYear(),
  planType: 1,
  startDate: '',
  endDate: '',
  deptScope: '全部部门',
  ownerDeptId: undefined,
  description: '',
  remark: ''
})

const planRules: FormRules = {
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  planYear: [{ required: true, message: '请选择计划年度', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

const evalStatusOptions = computed(() => dictOptions('icp_eval_status'))
const evalTypeOptions = computed(() => dictOptions('icp_eval_type'))
const taskStatusOptions = computed(() => dictOptions('icp_eval_task_status'))
const conclusionOptions = computed(() => dictOptions('icp_eval_conclusion'))
const workResultOptions = computed(() => dictOptions('icp_workpaper_result'))

/* ------------------------------ 评价任务 ------------------------------ */
const {
  query: taskQuery,
  list: tasks,
  total: taskTotal,
  loading: taskLoading,
  load: loadTasks,
  search: searchTasks,
  reset: resetTasks,
  handlePageChange: handleTaskPage,
  handleSizeChange: handleTaskSize
} = usePageList<EvaluationTaskRow>(
  (params) => evaluationTaskApi.list(params as { planId?: number; status?: number } & Record<string, unknown>),
  { planId: undefined, status: undefined }
)

const planOptions = ref<EvaluationRow[]>([])

const submitVisible = ref(false)
const submitTask = ref<EvaluationTaskRow | null>(null)
const submitFormRef = ref<FormInstance>()
const submitForm = reactive<EvaluationSubmitPayload>({
  score: 80,
  conclusion: 1,
  workpaper: {
    sampleDesc: '',
    testDesc: '',
    testResult: 1,
    evidence: [],
    conclusion: ''
  }
})
const evidenceFiles = ref<UploadResult[]>([])

const submitRules: FormRules = {
  score: [{ required: true, message: '请输入评分', trigger: 'blur' }],
  conclusion: [{ required: true, message: '请选择评价结论', trigger: 'change' }]
}

const workpaperVisible = ref(false)
const workpaperLoading = ref(false)
const workpaperTask = ref<EvaluationTaskRow | null>(null)
const workpapers = ref<WorkpaperRow[]>([])

function planName(planId?: number): string {
  if (!planId) return '-'
  const plan = planOptions.value.find((item) => item.id === planId)
  return plan ? plan.planName : `#${planId}`
}

async function loadPlanOptions(): Promise<void> {
  try {
    planOptions.value = await fetchAllPages<EvaluationRow>((params) => evaluationApi.list(params))
  } catch {
    planOptions.value = []
  }
}

function resetPlanForm(): void {
  planForm.planName = ''
  planForm.planYear = new Date().getFullYear()
  planForm.planType = 1
  planForm.startDate = ''
  planForm.endDate = ''
  planForm.deptScope = '全部部门'
  planForm.ownerDeptId = undefined
  planForm.description = ''
  planForm.remark = ''
  planFormRef.value?.clearValidate()
}

function openCreatePlan(): void {
  editingPlanId.value = null
  planDialogTitle.value = '新增评价计划'
  resetPlanForm()
  planDialogVisible.value = true
}

function openEditPlan(row: EvaluationRow): void {
  editingPlanId.value = row.id
  planDialogTitle.value = `编辑评价计划 - ${row.planName}`
  planForm.planName = row.planName
  planForm.planYear = row.planYear ?? new Date().getFullYear()
  planForm.planType = row.planType ?? 1
  planForm.startDate = row.startDate || ''
  planForm.endDate = row.endDate || ''
  planForm.deptScope = row.deptScope || ''
  planForm.ownerDeptId = row.ownerDeptId
  planForm.description = row.description || ''
  planForm.remark = row.remark || ''
  planDialogVisible.value = true
}

async function submitPlan(): Promise<void> {
  if (!planFormRef.value) return
  const valid = await planFormRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: EvaluationPayload = { ...planForm, planYear: Number(planForm.planYear), planType: Number(planForm.planType) }
  try {
    if (editingPlanId.value === null) {
      await evaluationApi.create(payload)
      ElMessage.success('评价计划新增成功')
    } else {
      await evaluationApi.update(editingPlanId.value, payload)
      ElMessage.success('评价计划修改成功')
    }
    planDialogVisible.value = false
    await Promise.all([loadPlans(), loadPlanOptions()])
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removePlan(row: EvaluationRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除评价计划「${row.planName}」？`, '删除评价计划', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await evaluationApi.remove(row.id)
    ElMessage.success('评价计划删除成功')
    await Promise.all([loadPlans(), loadPlanOptions()])
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function planAction(row: EvaluationRow, action: 'submit' | 'start' | 'finish' | 'close'): Promise<void> {
  const labels: Record<'submit' | 'start' | 'finish' | 'close', string> = {
    submit: '提交审批',
    start: '开始评价',
    finish: '完成评价',
    close: '关闭计划'
  }
  try {
    await ElMessageBox.confirm(`确认对计划「${row.planName}」执行「${labels[action]}」？`, labels[action], {
      type: 'warning',
      confirmButtonText: `确认${labels[action]}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    let successText = `已${labels[action]}`
    if (action === 'submit') {
      const result = await evaluationApi.submit(row.id)
      if (result?.processInstanceId) successText = '已提交审批，可在流程中心跟踪审批进度'
    } else if (action === 'start') {
      await evaluationApi.start(row.id)
    } else if (action === 'finish') {
      await evaluationApi.finish(row.id)
    } else {
      await evaluationApi.close(row.id)
    }
    ElMessage.success(successText)
    await loadPlans()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function startTask(row: EvaluationTaskRow): Promise<void> {
  try {
    await evaluationTaskApi.start(row.id)
    ElMessage.success('评价任务已开始')
    await loadTasks()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function openSubmit(row: EvaluationTaskRow): void {
  submitTask.value = row
  submitForm.score = row.score ?? 80
  submitForm.conclusion = row.conclusion ?? 1
  submitForm.workpaper.sampleDesc = ''
  submitForm.workpaper.testDesc = ''
  submitForm.workpaper.testResult = 1
  submitForm.workpaper.evidence = []
  submitForm.workpaper.conclusion = ''
  evidenceFiles.value = []
  submitVisible.value = true
}

function onEvidenceSuccess(file: UploadResult): void {
  evidenceFiles.value = [file]
  submitForm.workpaper.evidence = [Number(file.id)]
}

function onEvidenceRemove(): void {
  evidenceFiles.value = []
  submitForm.workpaper.evidence = []
}

async function submitWorkpaper(): Promise<void> {
  if (!submitTask.value || !submitFormRef.value) return
  const valid = await submitFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await evaluationTaskApi.submit(submitTask.value.id, {
      score: Number(submitForm.score),
      conclusion: Number(submitForm.conclusion),
      workpaper: { ...submitForm.workpaper }
    })
    ElMessage.success('评价底稿已提交')
    submitVisible.value = false
    await loadTasks()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function openWorkpapers(row: EvaluationTaskRow): Promise<void> {
  workpaperTask.value = row
  workpaperVisible.value = true
  workpaperLoading.value = true
  try {
    const data = await evaluationTaskApi.workpapers(row.id)
    workpapers.value = Array.isArray(data) ? data : data?.list || []
  } catch {
    workpapers.value = []
  } finally {
    workpaperLoading.value = false
  }
}

function switchTab(tab: string | number): void {
  if (tab === 'task' && !tasks.value.length) void loadTasks()
}

onMounted(() => {
  void ensureIcpDicts()
  void loadPlanOptions()
  void loadPlans()
  void loadTasks()
})
</script>

<template>
  <div class="page-container">
    <el-tabs v-model="activeTab" class="icp-tabs" @tab-change="switchTab">
      <el-tab-pane label="评价计划" name="plan">
        <div class="page-card">
          <div class="page-toolbar">
            <div class="page-toolbar-title">
              <span class="page-toolbar-heading">评价计划</span>
              <span class="page-toolbar-desc">年度/专项内控评价计划，提交审批后开始执行并生成评价任务</span>
            </div>
            <div class="page-toolbar-actions">
              <el-select v-model="planQuery.status" class="toolbar-select" placeholder="计划状态" clearable @change="searchPlans">
                <el-option
                  v-for="item in evalStatusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <el-select v-model="planQuery.planYear" class="toolbar-select" placeholder="计划年度" clearable @change="searchPlans">
                <el-option v-for="year in [2024, 2025, 2026, 2027]" :key="year" :label="`${year} 年`" :value="year" />
              </el-select>
              <el-input
                v-model="planQuery.keyword"
                class="toolbar-input"
                placeholder="计划名称 / 编码"
                clearable
                @keyup.enter="searchPlans"
                @clear="searchPlans"
              />
              <el-button type="primary" :icon="Plus" v-perm="'icp:evaluation:add'" @click="openCreatePlan">
                新增计划
              </el-button>
            </div>
          </div>

          <el-table v-loading="planLoading" :data="plans" border stripe row-key="id" empty-text="暂无评价计划">
            <el-table-column prop="planCode" label="计划编码" width="130" />
            <el-table-column prop="planName" label="计划名称" min-width="220" show-overflow-tooltip />
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="dictTagType('icp_eval_type', row.planType)" size="small">
                  {{ dictLabel('icp_eval_type', row.planType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="planYear" label="年度" width="80" align="center" />
            <el-table-column label="评价期间" min-width="190" align="center">
              <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="deptScope" label="评价范围" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="dictTagType('icp_eval_status', row.status)" size="small">
                  {{ dictLabel('icp_eval_status', row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" v-perm="'icp:evaluation:edit'" @click="openEditPlan(row)">编辑</el-button>
                <el-button
                  v-if="row.status === 0"
                  link
                  type="success"
                  v-perm="'icp:evaluation:submit'"
                  @click="planAction(row, 'submit')"
                >
                  提交审批
                </el-button>
                <el-button
                  v-if="row.status === 1"
                  link
                  type="success"
                  v-perm="'icp:evaluation:start'"
                  @click="planAction(row, 'start')"
                >
                  开始评价
                </el-button>
                <el-button
                  v-if="row.status === 2"
                  link
                  type="success"
                  v-perm="'icp:evaluation:finish'"
                  @click="planAction(row, 'finish')"
                >
                  完成评价
                </el-button>
                <el-button link type="warning" v-perm="'icp:evaluation:close'" @click="planAction(row, 'close')">
                  关闭
                </el-button>
                <el-button
                  link
                  type="danger"
                  v-perm="'icp:evaluation:delete'"
                  :disabled="row.status !== 0"
                  @click="removePlan(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="planQuery.pageNum"
              :page-size="planQuery.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="planTotal"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="handlePlanPage"
              @size-change="handlePlanSize"
            />
            <el-button text @click="resetPlans">重置筛选</el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="评价任务" name="task">
        <div class="page-card">
          <div class="page-toolbar">
            <div class="page-toolbar-title">
              <span class="page-toolbar-heading">评价任务</span>
              <span class="page-toolbar-desc">按计划分派的现场评价任务；处理时填写评价底稿（抽样/测试/证据/结论）</span>
            </div>
            <div class="page-toolbar-actions">
              <el-select v-model="taskQuery.planId" class="toolbar-plan-select" placeholder="所属计划" clearable filterable @change="searchTasks">
                <el-option v-for="item in planOptions" :key="item.id" :label="item.planName" :value="item.id" />
              </el-select>
              <el-select v-model="taskQuery.status" class="toolbar-select" placeholder="任务状态" clearable @change="searchTasks">
                <el-option
                  v-for="item in taskStatusOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </div>
          </div>

          <el-table v-loading="taskLoading" :data="tasks" border stripe row-key="id" empty-text="暂无评价任务">
            <el-table-column prop="taskCode" label="任务编码" width="130" />
            <el-table-column prop="taskName" label="任务名称" min-width="220" show-overflow-tooltip />
            <el-table-column label="所属计划" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ planName(row.planId) }}</template>
            </el-table-column>
            <el-table-column prop="evaluateDeptName" label="被评价部门" min-width="120" show-overflow-tooltip />
            <el-table-column prop="evaluatorName" label="评价人" width="100" />
            <el-table-column prop="dueDate" label="截止日期" width="120" align="center" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="dictTagType('icp_eval_task_status', row.status)" size="small">
                  {{ dictLabel('icp_eval_task_status', row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="得分" width="70" align="center">
              <template #default="{ row }">{{ row.score ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" link type="success" v-perm="'icp:evaluation-task:start'" @click="startTask(row)">
                  开始
                </el-button>
                <el-button
                  v-if="row.status === 0 || row.status === 1"
                  link
                  type="primary"
                  v-perm="'icp:evaluation-task:submit'"
                  @click="openSubmit(row)"
                >
                  底稿处理
                </el-button>
                <el-button link type="info" @click="openWorkpapers(row)">查看底稿</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              :current-page="taskQuery.pageNum"
              :page-size="taskQuery.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="taskTotal"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="handleTaskPage"
              @size-change="handleTaskSize"
            />
            <el-button text @click="resetTasks">重置筛选</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="planDialogVisible" :title="planDialogTitle" width="620px" destroy-on-close>
      <el-form ref="planFormRef" :model="planForm" :rules="planRules" label-width="100px">
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="planForm.planName" maxlength="200" show-word-limit placeholder="如：2026 年度内部控制评价计划" />
        </el-form-item>
        <el-form-item label="计划年度" prop="planYear">
          <el-select v-model="planForm.planYear" style="width: 100%">
            <el-option v-for="year in [2024, 2025, 2026, 2027]" :key="year" :label="`${year} 年`" :value="year" />
          </el-select>
        </el-form-item>
        <el-form-item label="评价类型">
          <el-radio-group v-model="planForm.planType">
            <el-radio v-for="item in evalTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评价期间" prop="startDate">
          <div class="date-range">
            <el-date-picker
              v-model="planForm.startDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
              style="width: 100%"
            />
            <span class="date-sep">至</span>
            <el-date-picker
              v-model="planForm.endDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
              style="width: 100%"
            />
          </div>
        </el-form-item>
        <el-form-item label="评价范围">
          <el-input v-model="planForm.deptScope" maxlength="100" placeholder="如：全部部门 / 财务部、内控与审计部" />
        </el-form-item>
        <el-form-item label="责任部门">
          <DeptPicker v-model="planForm.ownerDeptId" />
        </el-form-item>
        <el-form-item label="计划说明">
          <el-input v-model="planForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="planForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPlan">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="submitVisible"
      :title="submitTask ? `评价底稿处理 - ${submitTask.taskName}` : '评价底稿处理'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-width="100px">
        <el-form-item label="评价评分" prop="score">
          <el-input-number v-model="submitForm.score" :min="0" :max="100" :precision="1" style="width: 200px" />
          <span class="form-tip">满分 100 分</span>
        </el-form-item>
        <el-form-item label="评价结论" prop="conclusion">
          <el-radio-group v-model="submitForm.conclusion">
            <el-radio v-for="item in conclusionOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽样说明">
          <el-input v-model="submitForm.workpaper.sampleDesc" type="textarea" :rows="2" placeholder="如：抽取 2026 年 1–6 月采购合同 20 份" />
        </el-form-item>
        <el-form-item label="测试过程">
          <el-input v-model="submitForm.workpaper.testDesc" type="textarea" :rows="3" placeholder="记录测试步骤、样本检查情况与发现" />
        </el-form-item>
        <el-form-item label="测试结果">
          <el-radio-group v-model="submitForm.workpaper.testResult">
            <el-radio-button v-for="item in workResultOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评价证据">
          <FileUpload @success="onEvidenceSuccess" @remove="onEvidenceRemove" />
        </el-form-item>
        <el-form-item label="底稿结论">
          <el-input v-model="submitForm.workpaper.conclusion" type="textarea" :rows="2" placeholder="对控制设计与执行有效性的结论" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWorkpaper">提交底稿</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="workpaperVisible" :title="workpaperTask ? `评价底稿 - ${workpaperTask.taskName}` : '评价底稿'" size="640px">
      <el-table v-loading="workpaperLoading" :data="workpapers" border empty-text="暂无底稿记录">
        <el-table-column prop="sampleDesc" label="抽样说明" min-width="150" show-overflow-tooltip />
        <el-table-column prop="testDesc" label="测试过程" min-width="180" show-overflow-tooltip />
        <el-table-column label="测试结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="dictTagType('icp_workpaper_result', row.testResult)" size="small">
              {{ dictLabel('icp_workpaper_result', row.testResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="conclusion" label="结论" min-width="140" show-overflow-tooltip />
        <el-table-column prop="workUserName" label="评价人" width="100" />
        <el-table-column prop="createTime" label="提交时间" width="165" />
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.icp-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
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

.toolbar-plan-select {
  width: 200px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.date-range {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.date-sep {
  color: #8a94a6;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #8a94a6;
}
</style>
