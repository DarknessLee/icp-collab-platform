<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail?.title || '流程详情'"
    size="760px"
    destroy-on-close
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
    @open="load"
  >
    <div v-loading="loading" class="wf-instance-drawer">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="流程类型">{{ detail.processName || workflowTypeName(detail.processKey) }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detail.businessNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">
            <el-tag size="small" :type="workflowStatusType(detail.status)">
              {{ detail.statusText || workflowStatusText(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ detail.nodeName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ detail.initiatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ detail.startTime || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="activeTab" class="wf-instance-drawer__tabs">
          <el-tab-pane label="表单信息" name="form">
            <WorkflowFormView :schema="detail.formSchema" :data="formDataMerged" />
          </el-tab-pane>
          <el-tab-pane label="流转历史" name="history">
            <WorkflowHistory :items="history" />
          </el-tab-pane>
          <el-tab-pane label="流程图" name="diagram">
            <WorkflowDiagram :process-key="detail.processKey" :instance-id="detail.processInstanceId" :height="360" />
            <div class="wf-instance-drawer__hint">蓝色为当前节点，绿色为已流转节点（只读渲染，不可编辑）。</div>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!loading" :description="errorMessage || '流程详情加载失败'" />
    </div>

    <template v-if="allowResubmit && detail && isRejected" #footer>
      <div class="wf-instance-drawer__footer">
        <el-button type="primary" :loading="submitting" @click="submitResubmit">修改后重新提交</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { instanceApi, workflowStatusText, workflowStatusType, workflowTypeName } from '@/api/workflow'
import WorkflowDiagram from '@/components/WorkflowDiagram.vue'
import WorkflowFormView from '@/components/WorkflowFormView.vue'
import WorkflowHistory from '@/components/WorkflowHistory.vue'
import type { WfHistoryItem, WorkflowInstanceDetail } from '@/types/workflow'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    instanceId?: string
    allowResubmit?: boolean
  }>(),
  { instanceId: '', allowResubmit: false }
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'resubmitted'): void
}>()

const loading = ref(false)
const errorMessage = ref('')
const detail = ref<WorkflowInstanceDetail | null>(null)
const formDataMerged = computed<Record<string, unknown>>(() => {
  const merged: Record<string, unknown> = { ...(detail.value?.businessData || {}) }
  for (const [key, value] of Object.entries(detail.value?.formData || {})) {
    if (value !== null && value !== undefined) merged[key] = value
  }
  if (!merged.title) merged.title = detail.value?.title
  return merged
})
const history = ref<WfHistoryItem[]>([])
const activeTab = ref('form')
const submitting = ref(false)

const isRejected = computed(() => {
  const status = String(detail.value?.status || '').toUpperCase()
  return status === 'REJECTED' || status === '2' || detail.value?.statusText === '已驳回'
})

async function load(): Promise<void> {
  if (!props.instanceId) return
  loading.value = true
  errorMessage.value = ''
  detail.value = null
  history.value = []
  try {
    detail.value = await instanceApi.detail(props.instanceId)
    const [historyResult] = await Promise.allSettled([instanceApi.history(props.instanceId)])
    if (historyResult.status === 'fulfilled') history.value = historyResult.value || []
  } catch (error) {
    errorMessage.value = (error as Error).message || '流程详情加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) activeTab.value = 'form'
  }
)

async function submitResubmit(): Promise<void> {
  if (!props.instanceId) return
  submitting.value = true
  try {
    await instanceApi.resubmit(props.instanceId)
    ElMessage.success('已重新提交，流程已回到审批节点')
    emit('resubmitted')
    emit('update:modelValue', false)
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.wf-instance-drawer__tabs {
  margin-top: 12px;
}

.wf-instance-drawer__hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.wf-instance-drawer__footer {
  display: flex;
  justify-content: flex-end;
}
</style>
