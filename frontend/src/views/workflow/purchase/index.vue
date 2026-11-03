<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { purchaseApi } from '@/api/workflow'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'
import { usePageList } from '@/composables/usePageList'
import type { PageQuery } from '@/types/system'
import type { PurchasePayload, PurchaseRow } from '@/types/workflow'

const route = useRoute()

const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<PurchaseRow>((params) => purchaseApi.list(params as PageQuery), {
    keyword: '',
    status: undefined
  })

/** 采购/用印单据状态：0 草稿 1 审批中 2 已通过 3 已驳回 4 已完成 */
const STATUS_OPTIONS = [
  { value: 0, label: '草稿' },
  { value: 1, label: '审批中' },
  { value: 2, label: '已通过' },
  { value: 3, label: '已驳回' },
  { value: 4, label: '已完成' }
]

function statusText(status: unknown): string {
  const found = STATUS_OPTIONS.find((item) => String(item.value) === String(status))
  return found ? found.label : '—'
}

function statusType(status: unknown): 'info' | 'primary' | 'success' | 'danger' | 'warning' {
  const value = Number(status)
  if (value === 2 || value === 4) return 'success'
  if (value === 3) return 'danger'
  if (value === 1) return 'primary'
  return 'info'
}

const PURCHASE_TYPES = [
  { value: 1, label: '办公用品' },
  { value: 2, label: '设备采购' },
  { value: 3, label: '服务采购' },
  { value: 4, label: '其他' }
]

function purchaseTypeText(value: unknown): string {
  return PURCHASE_TYPES.find((item) => item.value === Number(value))?.label || '—'
}

function money(value: unknown): string {
  const number = Number(value)
  return Number.isFinite(number) ? `¥ ${number.toFixed(2)}` : '—'
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const detailVisible = ref(false)
const detail = ref<PurchaseRow | null>(null)
const trackVisible = ref(false)
const trackInstanceId = ref('')

const form = reactive<PurchasePayload>({
  title: '',
  purchaseType: 1,
  supplier: '',
  amount: 0,
  budgetAmount: undefined,
  purpose: '',
  applyDate: '',
  submit: true
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入采购申请标题', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入采购金额', trigger: 'blur' }]
}

function openCreate(): void {
  form.title = ''
  form.purchaseType = 1
  form.supplier = ''
  form.amount = 0
  form.budgetAmount = undefined
  form.purpose = ''
  form.applyDate = ''
  form.submit = true
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!Number(form.amount)) {
    ElMessage.warning('采购金额必须大于 0')
    return
  }
  if (form.budgetAmount !== undefined && form.budgetAmount !== null && Number(form.amount) > Number(form.budgetAmount)) {
    ElMessage.warning('采购金额不能超过预算金额')
    return
  }
  submitting.value = true
  try {
    const payload: PurchasePayload = {
      title: form.title.trim(),
      purchaseType: form.purchaseType,
      supplier: form.supplier?.trim() || undefined,
      amount: Number(form.amount),
      budgetAmount: form.budgetAmount === undefined || form.budgetAmount === null ? undefined : Number(form.budgetAmount),
      purpose: form.purpose?.trim() || undefined,
      applyDate: form.applyDate || undefined,
      submit: form.submit !== false
    }
    const result = await purchaseApi.create(payload)
    if (form.submit !== false) {
      ElMessage.success(
        result.processInstanceId
          ? '采购申请已提交审批，可在流程中心跟踪进度'
          : '采购申请已提交审批'
      )
    } else {
      ElMessage.success('采购申请草稿已保存')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 错误提示由 request.ts 统一处理
  } finally {
    submitting.value = false
  }
}

function openDetail(row: PurchaseRow): void {
  detail.value = row
  detailVisible.value = true
}

function track(row: PurchaseRow): void {
  if (!row.processInstanceId) {
    ElMessage.info('该单据尚未发起审批流程')
    return
  }
  trackInstanceId.value = row.processInstanceId
  trackVisible.value = true
}

onMounted(() => {
  load()
  if (route.query.new === '1') openCreate()
})

watch(
  () => route.query.new,
  (value) => {
    if (value === '1') openCreate()
  }
)
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">采购申请</div>
          <div class="page-toolbar-desc">采购申请发起与审批跟踪：提交后进入采购审批流程（部门负责人 → 管理层）</div>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="标题 / 采购单号"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="单据状态" clearable @change="search">
            <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button v-perm="'workflow:purchase:add'" type="primary" :icon="Plus" @click="openCreate">
            新建采购申请
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="applyNo" label="采购单号" width="140" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="90" />
        <el-table-column prop="deptName" label="申请部门" width="120" show-overflow-tooltip />
        <el-table-column label="采购金额" width="120" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="预算金额" width="120" align="right">
          <template #default="{ row }">{{ row.budgetAmount == null ? '—' : money(row.budgetAmount) }}</template>
        </el-table-column>
        <el-table-column label="采购类型" width="110">
          <template #default="{ row }">{{ purchaseTypeText(row.purchaseType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyDate" label="申请日期" width="115" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="track(row)">流程跟踪</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无采购申请" :image-size="90" />
        </template>
      </el-table>

      <el-pagination
        v-if="total > 0"
        class="pager"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="新建采购申请" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="申请标题" prop="title">
              <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：采购办公电脑 20 台" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="采购类型" prop="purchaseType">
              <el-select v-model="form.purchaseType" style="width: 100%">
                <el-option v-for="item in PURCHASE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="供应商" prop="supplier">
              <el-input v-model="form.supplier" maxlength="100" placeholder="如：华信智控供应商" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="申请日期" prop="applyDate">
              <el-date-picker v-model="form.applyDate" type="date" value-format="YYYY-MM-DD" placeholder="默认当天" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="采购金额" prop="amount">
              <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预算金额" prop="budgetAmount">
              <el-input-number v-model="form.budgetAmount" :min="0" :precision="2" :step="100" style="width: 100%" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="采购用途" prop="purpose">
          <el-input v-model="form.purpose" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明采购背景与用途" />
        </el-form-item>
        <el-form-item label="提交审批">
          <el-switch v-model="form.submit" active-text="创建后立即提交审批" inactive-text="仅保存草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="采购申请详情" size="640px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="采购单号">{{ detail.applyNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请部门">{{ detail.deptName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="采购金额">{{ money(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="预算金额">{{ detail.budgetAmount == null ? '—' : money(detail.budgetAmount) }}</el-descriptions-item>
        <el-descriptions-item label="采购类型">{{ purchaseTypeText(detail.purchaseType) }}</el-descriptions-item>
        <el-descriptions-item label="申请日期">{{ detail.applyDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplier || '—' }}</el-descriptions-item>
        <el-descriptions-item label="流程实例">{{ detail.processInstanceId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="采购用途" :span="2">{{ detail.purpose || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail?.processInstanceId" type="primary" @click="track(detail!)">流程跟踪</el-button>
      </template>
    </el-drawer>

    <WorkflowInstanceDrawer v-model="trackVisible" :instance-id="trackInstanceId" />
  </div>
</template>
