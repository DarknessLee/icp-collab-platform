<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { borrowApi } from '@/api/workflow'
import { usePageList } from '@/composables/usePageList'
import type { PageQuery } from '@/types/system'
import type { BorrowPayload, BorrowRow } from '@/types/workflow'

const route = useRoute()

const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<BorrowRow>((params) => borrowApi.list(params as PageQuery), {
    keyword: '',
    status: undefined
  })

/** 借阅单据状态：0 申请中 1 已通过 2 已驳回 3 已归还 */
const STATUS_OPTIONS = [
  { value: 0, label: '申请中' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已归还' }
]

function statusText(status: unknown): string {
  return STATUS_OPTIONS.find((item) => String(item.value) === String(status))?.label || '—'
}

function statusType(status: unknown): 'info' | 'primary' | 'success' | 'danger' {
  const value = Number(status)
  if (value === 1) return 'success'
  if (value === 2) return 'danger'
  if (value === 0) return 'primary'
  return 'info'
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive<BorrowPayload>({
  docId: undefined as unknown as number,
  reason: '',
  startDate: '',
  endDate: ''
})

const rules: FormRules = {
  docId: [{ required: true, message: '请输入文档 ID', trigger: 'blur' }],
  reason: [{ required: true, message: '请填写借阅事由', trigger: 'blur' }]
}

function openCreate(): void {
  form.docId = undefined as unknown as number
  form.reason = ''
  form.startDate = ''
  form.endDate = ''
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    ElMessage.warning('借阅结束日期不能早于开始日期')
    return
  }
  submitting.value = true
  try {
    const result = await borrowApi.create({
      docId: Number(form.docId),
      reason: form.reason.trim(),
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined
    })
    ElMessage.success(
      result.processInstanceId ? '借阅申请已提交审批，可在流程中心跟踪进度' : '借阅申请已提交审批'
    )
    dialogVisible.value = false
    load()
  } catch {
    // 错误提示由 request.ts 统一处理
  } finally {
    submitting.value = false
  }
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
          <div class="page-toolbar-heading">文档借阅</div>
          <div class="page-toolbar-desc">文档借阅申请与审批跟踪：提交后进入借阅审批流程（部门负责人 → 内控管理员）</div>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="借阅事由"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="单据状态" clearable @change="search">
            <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button v-perm="'workflow:borrow:add'" type="primary" :icon="Plus" @click="openCreate">
            新建借阅申请
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="docId" label="文档 ID" width="100" />
        <el-table-column prop="reason" label="借阅事由" min-width="220" show-overflow-tooltip />
        <el-table-column prop="borrowerName" label="借阅人" width="100" />
        <el-table-column prop="startDate" label="开始日期" width="115" />
        <el-table-column prop="endDate" label="结束日期" width="115" />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="流程实例" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">{{ row.processInstanceId || '—' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无借阅申请" :image-size="90" />
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

    <el-dialog v-model="dialogVisible" title="新建借阅申请" width="620px" destroy-on-close>
      <el-alert
        class="mb-12"
        type="info"
        :closable="false"
        title="文档中心将在 M3 阶段开放；当前请填写文档中心内已有文档的 ID 发起借阅审批。"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="文档 ID" prop="docId">
          <el-input-number v-model="form.docId" :min="1" :step="1" :precision="0" style="width: 100%" placeholder="请输入文档 ID" />
        </el-form-item>
        <el-form-item label="借阅事由" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明借阅用途与范围" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="默认当天" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="默认 +7 天" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
