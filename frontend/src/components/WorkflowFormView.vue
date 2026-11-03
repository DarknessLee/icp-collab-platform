<template>
  <div class="wf-form-view">
    <el-descriptions :column="column" border size="small">
      <el-descriptions-item v-for="field in fields" :key="field.key" :label="field.label" :span="spanOf(field)">
        <template v-if="field.type === 'attachmentList' || field.type === 'attachment' || field.type === 'files'">
          <template v-if="attachmentsOf(field).length">
            <el-tag v-for="(file, index) in attachmentsOf(field)" :key="index" class="wf-form-view__file" type="info" size="small">
              {{ file }}
            </el-tag>
          </template>
          <span v-else class="text-muted">无附件</span>
        </template>
        <span v-else-if="field.type === 'textarea'">{{ textOf(field) || '—' }}</span>
        <span v-else :class="{ 'wf-form-view__amount': field.type === 'amount' }">{{ displayOf(field) }}</span>
      </el-descriptions-item>
      <el-descriptions-item v-if="!fields.length" label="表单数据">
        <template v-if="Object.keys(data || {}).length">
          <pre class="wf-form-view__json">{{ prettyData }}</pre>
        </template>
        <span v-else class="text-muted">暂无表单数据</span>
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WfFormField } from '@/types/workflow'

const props = withDefaults(
  defineProps<{
    schema?: WfFormField[]
    data?: Record<string, unknown>
    column?: number
  }>(),
  { schema: () => [], data: () => ({}), column: 2 }
)

const fields = computed(() => {
  const schema = props.schema || []
  const rows = schema.map((field) => ({ ...field }))
  const known = new Set(rows.map((field) => field.key))
  const data = props.data || {}
  for (const key of Object.keys(data)) {
    if (known.has(key) || SKIP_KEYS.has(key)) continue
    const value = data[key]
    if (value === null || value === undefined || value === '') continue
    rows.push({ key, label: EXTRA_LABELS[key] || key, type: inferType(key), value })
  }
  return rows
})

const SKIP_KEYS = new Set([
  'id',
  'tenantId',
  'deleted',
  'createBy',
  'updateBy',
  'updateTime',
  'flowInstanceId',
  'processInstanceId',
  'status'
])

const EXTRA_LABELS: Record<string, string> = {
  title: '标题',
  reason: '事由',
  applyNo: '单据编号',
  amount: '采购金额',
  budgetAmount: '预算金额',
  supplier: '供应商',
  purchaseType: '采购类型',
  purpose: '采购用途',
  applyDate: '申请日期',
  deptName: '申请部门',
  applicantName: '申请人',
  rectifyCode: '整改单号',
  defectCode: '缺陷编号',
  defectId: '缺陷 ID',
  rectifyPlan: '整改计划',
  deadline: '整改期限',
  finishDate: '完成日期',
  evidence: '整改证据',
  sealType: '印章类型',
  useDate: '用印日期',
  fileId: '附件 ID',
  docId: '文档 ID',
  startDate: '开始日期',
  endDate: '结束日期',
  borrowerName: '借阅人',
  processKey: '流程标识', createTime: '创建时间', deptId: '部门 ID', applicantId: '申请人 ID', initiatorId: '发起人 ID', businessId: '业务 ID', borrowNo: '借阅单号', responsibleUserId: '责任人 ID', remark: '备注', reviewOpinion: '复核意见', reviewTime: '复核时间'
}

const AMOUNT_KEYS = new Set(['amount', 'budgetAmount', 'totalAmount'])
const ATTACHMENT_KEYS = new Set(['evidence', 'attachmentIds', 'fileIds'])

const OPTION_MAPS: Record<string, Record<string, string>> = {
  sealType: { '1': '公司公章', '2': '合同专用章', '3': '财务专用章', '4': '法人章' },
  purchaseType: { '1': '办公用品', '2': '设备采购', '3': '服务采购', '4': '其他' }
}

function inferType(key: string): string {
  if (AMOUNT_KEYS.has(key)) return 'amount'
  if (ATTACHMENT_KEYS.has(key)) return 'attachmentList'
  return 'text'
}

const prettyData = computed(() => JSON.stringify(props.data || {}, null, 2))

function valueOf(field: WfFormField): unknown {
  if (field.value !== undefined && field.value !== null && field.value !== '') return field.value
  return (props.data || {})[field.key]
}

function textOf(field: WfFormField): string {
  const value = valueOf(field)
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function displayOf(field: WfFormField): string {
  const value = valueOf(field)
  if (value === null || value === undefined || value === '') return '—'
  const type = (field.type || '').toLowerCase()
  if (type === 'amount' || type === 'money') {
    const number = Number(value)
    return Number.isFinite(number) ? `¥ ${number.toFixed(2)}` : String(value)
  }
  if (type === 'select' || type === 'radio') {
    const found = (field.options || []).find((item) => String(item.value) === String(value))
    return found ? found.label : String(value)
  }
  const mapped = OPTION_MAPS[field.key]?.[String(value)]
  if (mapped) return mapped
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function attachmentsOf(field: WfFormField): string[] {
  const value = valueOf(field)
  const list = Array.isArray(value) ? value : typeof value === 'string' && value.includes(',') ? value.split(',') : value ? [value] : []
  return list.map((item) => {
    if (item && typeof item === 'object') {
      const o = item as Record<string, unknown>
      return String(o.originalName ?? o.name ?? o.fileName ?? o.id ?? '')
    }
    return String(item)
  })
}

function spanOf(field: WfFormField): number {
  const type = (field.type || '').toLowerCase()
  if (type === 'textarea' || type === 'attachmentlist') return props.column
  return 1
}
</script>

<style scoped>
.wf-form-view {
  width: 100%;
}

.wf-form-view__file {
  margin: 0 6px 4px 0;
}

.wf-form-view__amount {
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);
  font-weight: 600;
}

.wf-form-view__json {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
