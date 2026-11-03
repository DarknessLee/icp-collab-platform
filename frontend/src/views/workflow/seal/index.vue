<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { sealApi } from '@/api/workflow'
import FileUpload from '@/components/FileUpload.vue'
import WorkflowInstanceDrawer from '@/components/WorkflowInstanceDrawer.vue'
import { usePageList } from '@/composables/usePageList'
import type { PageQuery, UploadResult } from '@/types/system'
import type { SealPayload, SealRow } from '@/types/workflow'

const route = useRoute()

const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<SealRow>((params) => sealApi.list(params as PageQuery), {
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

const SEAL_TYPES = [
  { value: 1, label: '公司公章' },
  { value: 2, label: '合同专用章' },
  { value: 3, label: '财务专用章' },
  { value: 4, label: '法人章' }
]

function statusText(status: unknown): string {
  return STATUS_OPTIONS.find((item) => String(item.value) === String(status))?.label || '—'
}

function statusType(status: unknown): 'info' | 'primary' | 'success' | 'danger' {
  const value = Number(status)
  if (value === 2 || value === 4) return 'success'
  if (value === 3) return 'danger'
  if (value === 1) return 'primary'
  return 'info'
}

function sealTypeText(value: unknown): string {
  return SEAL_TYPES.find((item) => item.value === Number(value))?.label || '—'
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const detailVisible = ref(false)
const detail = ref<SealRow | null>(null)
const trackVisible = ref(false)
const trackInstanceId = ref('')
const fileResult = ref<UploadResult | null>(null)

const form = reactive<SealPayload>({
  sealType: 1,
  reason: '',
  useDate: '',
  fileId: undefined,
  submit: true
})

const rules: FormRules = {
  sealType: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
  reason: [{ required: true, message: '请输入用印事由', trigger: 'blur' }]
}

function openCreate(): void {
  form.sealType = 1
  form.reason = ''
  form.useDate = ''
  form.fileId = undefined
  form.submit = true
  fileResult.value = null
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function onUploadSuccess(result: UploadResult): void {
  fileResult.value = result
  form.fileId = result.id
}

function onUploadRemove(): void {
  fileResult.value = null
  form.fileId = undefined
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: SealPayload = {
      sealType: Number(form.sealType),
      reason: form.reason.trim(),
      useDate: form.useDate || undefined,
      fileId: form.fileId,
      submit: form.submit !== false
    }
    const result = await sealApi.create(payload)
    if (form.submit !== false) {
      ElMessage.success(
        result.processInstanceId ? '用印申请已提交审批，可在流程中心跟踪进度' : '用印申请已提交审批'
      )
    } else {
      ElMessage.success('用印申请草稿已保存')
    }
    dialogVisible.value = false
    load()
  } catch {
    // 错误提示由 request.ts 统一处理
  } finally {
    submitting.value = false
  }
}

function openDetail(row: SealRow): void {
  detail.value = row
  detailVisible.value = true
}

function track(row: SealRow): void {
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
          <div class="page-toolbar-heading">用印申请</div>
          <div class="page-toolbar-desc">印章使用申请与审批跟踪：提交后进入用印审批流程（部门负责人 → 管理层）</div>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="用印事由 / 用印单号"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="单据状态" clearable @change="search">
            <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button v-perm="'workflow:seal:add'" type="primary" :icon="Plus" @click="openCreate">
            新建用印申请
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="applyNo" label="用印单号" width="140" />
        <el-table-column label="印章类型" width="130">
          <template #default="{ row }">{{ sealTypeText(row.sealType) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="用印事由" min-width="200" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="90" />
        <el-table-column prop="deptName" label="申请部门" width="120" show-overflow-tooltip />
        <el-table-column prop="useDate" label="用印日期" width="115" />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="track(row)">流程跟踪</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用印申请" :image-size="90" />
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

    <el-dialog v-model="dialogVisible" title="新建用印申请" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="印章类型" prop="sealType">
              <el-select v-model="form.sealType" style="width: 100%">
                <el-option v-for="item in SEAL_TYPES" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用印日期" prop="useDate">
              <el-date-picker v-model="form.useDate" type="date" value-format="YYYY-MM-DD" placeholder="默认当天" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用印事由" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明用印文件、份数与用途" />
        </el-form-item>
        <el-form-item label="用印附件">
          <FileUpload
            accept=".pdf,.png,.jpg,.jpeg,.doc,.docx"
            :max-size-mb="20"
            tip="可选，支持用印文件 PDF、图片与 Word 文档"
            @success="onUploadSuccess"
            @remove="onUploadRemove"
          />
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

    <el-drawer v-model="detailVisible" title="用印申请详情" size="620px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="用印单号">{{ detail.applyNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="印章类型">{{ sealTypeText(detail.sealType) }}</el-descriptions-item>
        <el-descriptions-item label="用印日期">{{ detail.useDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="申请部门">{{ detail.deptName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="用印事由" :span="2">{{ detail.reason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="附件 ID">{{ detail.fileId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="流程实例">{{ detail.processInstanceId || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail?.processInstanceId" type="primary" @click="track(detail!)">流程跟踪</el-button>
      </template>
    </el-drawer>

    <WorkflowInstanceDrawer v-model="trackVisible" :instance-id="trackInstanceId" />
  </div>
</template>
