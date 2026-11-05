<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail?.title || '流程任务详情'"
    size="760px"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @open="load"
  >
    <div v-loading="loading" class="wf-task-drawer">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="流程类型">{{ detail.processName || workflowTypeName(detail.processKey) }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detail.businessNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">
            <el-tag size="small" type="primary">{{ detail.nodeName || '—' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detail.initiatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ detail.createTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ detail.taskId }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab" class="wf-task-drawer__tabs">
          <el-tab-pane label="表单信息" name="form">
            <WorkflowFormView :schema="detail.formSchema" :data="formDataMerged" />
          </el-tab-pane>
          <el-tab-pane label="流转历史" name="history">
            <WorkflowHistory :items="history" />
          </el-tab-pane>
          <el-tab-pane label="流程图" name="diagram">
            <WorkflowDiagram
              :process-key="detail.processKey"
              :instance-id="detail.processInstanceId"
              :height="360"
            />
            <div class="wf-task-drawer__hint">蓝色为当前节点，绿色为已流转节点（只读渲染，不可编辑）。</div>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!loading" :description="errorMessage || '任务详情加载失败'" />
    </div>

    <template v-if="showActions && detail" #footer>
      <div class="wf-task-drawer__footer">
        <el-button v-perm="'workflow:task:urge'" @click="openUrge">催 办</el-button>
        <el-button v-perm="'workflow:task:reject'" type="danger" plain @click="openReject">驳 回</el-button>
        <el-button v-perm="'workflow:task:approve'" type="primary" @click="openApprove">同 意</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- 同意 -->
  <el-dialog v-model="approveVisible" title="审批同意" width="560px" append-to-body>
    <el-form label-width="80px">
      <el-form-item label="审批意见">
        <el-input v-model="approveForm.comment" type="textarea" :rows="3" placeholder="可选，填写审批意见" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="附件">
        <div class="wf-attachments">
          <FileUpload tip="可选，支持 PDF/图片/Office 文档，单个不超过 20MB" @success="onAttachmentSuccess" />
          <div v-if="approveForm.attachments.length" class="wf-attachments__list">
            <el-tag
              v-for="item in approveForm.attachments"
              :key="item.id"
              closable
              size="small"
              type="info"
              @close="removeAttachment(item.id)"
            >
              {{ item.name }}
            </el-tag>
          </div>
        </div>
      </el-form-item>
      <el-form-item label="抄送人">
        <el-select
          v-model="approveForm.ccUserIds"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="可选，审批完成后抄送通知"
          class="wf-cc-select"
        >
          <el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="approveVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitApprove">确认同意</el-button>
    </template>
  </el-dialog>

  <!-- 驳回 -->
  <el-dialog v-model="rejectVisible" title="审批驳回" width="520px" append-to-body>
    <el-alert type="warning" :closable="false" show-icon title="驳回后流程回到发起人节点，发起人可修改后重新提交。" class="mb-12" />
    <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
      <el-form-item label="驳回意见" prop="comment">
        <el-input v-model="rejectForm.comment" type="textarea" :rows="4" placeholder="请填写驳回原因（必填）" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="抄送人">
        <el-select
          v-model="rejectForm.ccUserIds"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="可选，驳回后抄送通知"
          class="wf-cc-select"
        >
          <el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="rejectVisible = false">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submitReject">确认驳回</el-button>
    </template>
  </el-dialog>

  <!-- 催办 -->
  <el-dialog v-model="urgeVisible" title="催办提醒" width="520px" append-to-body>
    <el-form ref="urgeFormRef" :model="urgeForm" :rules="urgeRules" label-width="80px">
      <el-form-item label="催办内容" prop="content">
        <el-input v-model="urgeForm.content" type="textarea" :rows="4" placeholder="请填写催办内容，将通知当前办理人" maxlength="200" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="urgeVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitUrge">发送催办</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { taskApi, workflowTypeName } from '@/api/workflow'
import { userApi } from '@/api/system'
import FileUpload from '@/components/FileUpload.vue'
import WorkflowDiagram from '@/components/WorkflowDiagram.vue'
import WorkflowFormView from '@/components/WorkflowFormView.vue'
import WorkflowHistory from '@/components/WorkflowHistory.vue'
import type { WfDiagramHighlight, WfHistoryItem, WorkflowTaskDetail } from '@/types/workflow'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    taskId?: string
    showActions?: boolean
  }>(),
  { taskId: '', showActions: true }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'handled'): void
}>()

const loading = ref(false)
const errorMessage = ref('')
const detail = ref<WorkflowTaskDetail | null>(null)
const formDataMerged = computed<Record<string, unknown>>(() => {
  const merged: Record<string, unknown> = { ...(detail.value?.businessData || {}) }
  for (const [key, value] of Object.entries(detail.value?.formData || {})) {
    if (value !== null && value !== undefined) merged[key] = value
  }
  if (!merged.title) merged.title = detail.value?.title
  return merged
})
const history = ref<WfHistoryItem[]>([])
const highlight = ref<WfDiagramHighlight | null>(null)
const activeTab = ref('form')
const submitting = ref(false)

const approveVisible = ref(false)
const approveForm = reactive<{ comment: string; attachments: { id: string | number; name: string }[]; ccUserIds: (string | number)[] }>({
  comment: '',
  attachments: [],
  ccUserIds: []
})
const rejectVisible = ref(false)
const rejectFormRef = ref<FormInstance>()
const rejectForm = reactive<{ comment: string; ccUserIds: (string | number)[] }>({ comment: '', ccUserIds: [] })
const rejectRules: FormRules = {
  comment: [{ required: true, message: '驳回意见不能为空', trigger: 'blur' }]
}
const urgeVisible = ref(false)
const urgeFormRef = ref<FormInstance>()
const urgeForm = reactive({ content: '' })
const urgeRules: FormRules = {
  content: [{ required: true, message: '催办内容不能为空', trigger: 'blur' }]
}

// 抄送人候选：首次打开审批/驳回弹窗时加载一次用户列表（排除停用账号）
const userOptions = ref<{ id: string | number; label: string }[]>([])
let usersLoaded = false
async function loadUsers(): Promise<void> {
  if (usersLoaded) return
  usersLoaded = true
  try {
    const page = await userApi.list({ pageNum: 1, pageSize: 200 })
    userOptions.value = page.list
      .filter((item) => String(item.status) !== '0')
      .map((item) => ({
        id: item.id,
        label: item.realName ? `${item.realName}（${item.username}）` : item.username
      }))
  } catch {
    usersLoaded = false
  }
}

async function load(): Promise<void> {
  if (!props.taskId) return
  loading.value = true
  errorMessage.value = ''
  detail.value = null
  history.value = []
  highlight.value = null
  try {
    detail.value = await taskApi.detail(props.taskId)
    const instanceId = detail.value.processInstanceId
    const results = await Promise.allSettled([
      instanceId ? taskApi.detail(props.taskId).then((d) => d) : Promise.resolve(detail.value),
      instanceId ? import('@/api/workflow').then((m) => m.instanceApi.history(instanceId)) : Promise.resolve([]),
      instanceId ? import('@/api/workflow').then((m) => m.instanceApi.highlight(instanceId)) : Promise.resolve(null)
    ])
    if (results[1]?.status === 'fulfilled') history.value = (results[1].value as WfHistoryItem[]) || []
    if (results[2]?.status === 'fulfilled') highlight.value = (results[2].value as WfDiagramHighlight | null) || null
  } catch (error) {
    errorMessage.value = (error as Error).message || '任务详情加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.taskId],
  ([visible]) => {
    if (visible) activeTab.value = 'form'
  }
)

function openApprove(): void {
  approveForm.comment = ''
  approveForm.attachments = []
  approveForm.ccUserIds = []
  void loadUsers()
  approveVisible.value = true
}

function onAttachmentSuccess(result: { id?: string | number; originalName?: string }): void {
  if (result?.id === undefined) return
  approveForm.attachments.push({ id: result.id, name: result.originalName || `附件 ${result.id}` })
}

function removeAttachment(id: string | number): void {
  approveForm.attachments = approveForm.attachments.filter((item) => item.id !== id)
}

function openReject(): void {
  rejectForm.comment = ''
  rejectForm.ccUserIds = []
  void loadUsers()
  rejectVisible.value = true
}

function openUrge(): void {
  urgeForm.content = ''
  urgeVisible.value = true
}

async function submitApprove(): Promise<void> {
  if (!props.taskId) return
  submitting.value = true
  try {
    await taskApi.approve(props.taskId, {
      comment: approveForm.comment || undefined,
      attachmentIds: approveForm.attachments.length ? approveForm.attachments.map((item) => item.id) : undefined,
      ccUserIds: approveForm.ccUserIds.length ? approveForm.ccUserIds.map((id) => Number(id)) : undefined
    })
    ElMessage.success('审批已通过，流程已流转')
    approveVisible.value = false
    emit('handled')
    emit('update:modelValue', false)
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    submitting.value = false
  }
}

async function submitReject(): Promise<void> {
  const valid = await rejectFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await taskApi.reject(props.taskId, {
      comment: rejectForm.comment,
      ccUserIds: rejectForm.ccUserIds.length ? rejectForm.ccUserIds.map((id) => Number(id)) : undefined
    })
    ElMessage.success('已驳回至发起人')
    rejectVisible.value = false
    emit('handled')
    emit('update:modelValue', false)
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    submitting.value = false
  }
}

async function submitUrge(): Promise<void> {
  const valid = await urgeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await taskApi.urge(props.taskId, { content: urgeForm.content })
    ElMessage.success('催办已发送，已通知当前办理人')
    urgeVisible.value = false
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.wf-task-drawer__tabs {
  margin-top: 12px;
}

.wf-task-drawer__hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.wf-task-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.wf-attachments {
  width: 100%;
}

.wf-attachments__list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.mb-12 {
  margin-bottom: 12px;
}

.wf-cc-select {
  width: 100%;
}
</style>
