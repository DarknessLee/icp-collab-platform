<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import { policyApi } from '@/api/icp'
import CategoryTree from '@/components/CategoryTree.vue'
import DeptPicker from '@/components/DeptPicker.vue'
import FileUpload from '@/components/FileUpload.vue'
import { usePageList } from '@/composables/usePageList'
import type { IcpCategory, PolicyPayload, PolicyQuery, PolicyRow } from '@/types/icp'
import type { UploadResult } from '@/types/system'
import { exportCsv } from '@/utils/exportCsv'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'
import { fetchAllPages } from '@/utils/pagedAll'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<PolicyRow>(
    (params) => policyApi.list(params as PolicyQuery & Record<string, unknown>),
    { keyword: '', categoryId: undefined, status: undefined, effectiveFrom: '', effectiveTo: '' }
  )

const categories = ref<IcpCategory[]>([])
const categoryLoading = ref(false)
const dateRange = ref<[string, string] | null>(null)

const drawerVisible = ref(false)
const drawerTitle = ref('新建制度')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<PolicyPayload>({
  policyName: '',
  categoryId: undefined,
  version: 'V1.0',
  effectiveDate: '',
  expireDate: '',
  ownerDeptId: undefined,
  keywords: '',
  content: '',
  remark: ''
})
const formFiles = ref<UploadResult[]>([])
const rules: FormRules = {
  policyName: [{ required: true, message: '请输入制度名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择制度分类', trigger: 'change' }],
  version: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}

const detailVisible = ref(false)
const detail = ref<PolicyRow>()

const statusOptions = computed(() => dictOptions('icp_policy_status'))

async function loadCategories(): Promise<void> {
  categoryLoading.value = true
  try {
    categories.value = await policyApi.categoryTree()
  } catch {
    // 失败原因由 request.ts 提示
  } finally {
    categoryLoading.value = false
  }
}

function onCategorySelect(id: number | undefined): void {
  query.categoryId = id
  search()
}

function onDateChange(value: [string, string] | null): void {
  query.effectiveFrom = value?.[0] || ''
  query.effectiveTo = value?.[1] || ''
  search()
}

function resetAll(): void {
  dateRange.value = null
  reset()
}

function resetForm(): void {
  form.policyName = ''
  form.categoryId = undefined
  form.version = 'V1.0'
  form.effectiveDate = ''
  form.expireDate = ''
  form.ownerDeptId = undefined
  form.keywords = ''
  form.content = ''
  form.remark = ''
  formFiles.value = []
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  drawerTitle.value = '新建制度'
  resetForm()
  drawerVisible.value = true
}

function openEdit(row: PolicyRow): void {
  editingId.value = row.id
  drawerTitle.value = `编辑制度 - ${row.policyName}`
  form.policyName = row.policyName
  form.categoryId = row.categoryId
  form.version = row.version || 'V1.0'
  form.effectiveDate = row.effectiveDate || ''
  form.expireDate = row.expireDate || ''
  form.ownerDeptId = row.ownerDeptId
  form.keywords = row.keywords || ''
  form.content = row.content || ''
  form.remark = row.remark || ''
  formFiles.value = []
  drawerVisible.value = true
}

function onFileSuccess(result: UploadResult): void {
  formFiles.value.push(result)
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: PolicyPayload = {
    ...form,
    effectiveDate: form.effectiveDate || undefined,
    expireDate: form.expireDate || undefined,
    fileIds: formFiles.value.map((file) => Number(file.id))
  }
  try {
    if (editingId.value === null) {
      await policyApi.create(payload)
      ElMessage.success('制度创建成功，保存为草稿')
    } else {
      await policyApi.update(editingId.value, payload)
      ElMessage.success('制度修改成功')
    }
    drawerVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function openDetail(row: PolicyRow): Promise<void> {
  detail.value = row
  detailVisible.value = true
  try {
    detail.value = await policyApi.detail(row.id)
  } catch {
    // 保留列表行数据展示
  }
}

async function confirmAction(message: string, title: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}

async function submitApproval(row: PolicyRow): Promise<void> {
  if (!(await confirmAction(`制度「${row.policyName}」提交审批后进入审批流程，审批期间不可编辑。是否继续？`, '提交审批'))) return
  try {
    const result = await policyApi.submit(row.id)
    ElMessage.success(
      result?.processInstanceId ? '已提交审批，可在流程中心跟踪审批进度' : '已提交审批'
    )
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function publish(row: PolicyRow): Promise<void> {
  if (!(await confirmAction(`发布后制度「${row.policyName}」正式生效。是否继续？`, '发布制度'))) return
  try {
    await policyApi.publish(row.id)
    ElMessage.success('制度已发布生效')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function abolish(row: PolicyRow): Promise<void> {
  if (!(await confirmAction(`废止后制度「${row.policyName}」不再生效且不可恢复。是否继续？`, '废止制度'))) return
  try {
    await policyApi.abolish(row.id)
    ElMessage.success('制度已废止')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: PolicyRow): Promise<void> {
  if (!(await confirmAction(`删除草稿「${row.policyName}」后不可恢复。是否继续？`, '删除制度'))) return
  try {
    await policyApi.remove(row.id)
    ElMessage.success('制度删除成功')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function exportList(): Promise<void> {
  try {
    const rows = await fetchAllPages<PolicyRow>(
      (params) => policyApi.list(params as PolicyQuery & Record<string, unknown>),
      {
        keyword: query.keyword,
        categoryId: query.categoryId,
        status: query.status,
        effectiveFrom: query.effectiveFrom,
        effectiveTo: query.effectiveTo
      }
    )
    if (!rows.length) {
      ElMessage.warning('当前筛选结果为空，未导出')
      return
    }
    exportCsv('制度库导出', rows, [
      { title: '制度编码', get: (row) => row.policyCode },
      { title: '制度名称', get: (row) => row.policyName },
      { title: '分类', get: (row) => row.categoryName },
      { title: '版本', get: (row) => row.version },
      { title: '生效日期', get: (row) => row.effectiveDate },
      { title: '失效日期', get: (row) => row.expireDate },
      { title: '归口部门', get: (row) => row.ownerDeptName },
      { title: '状态', get: (row) => dictLabel('icp_policy_status', row.status) },
      { title: '发布日期', get: (row) => row.publishTime }
    ])
    ElMessage.success(`已导出 ${rows.length} 条制度`)
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function canEdit(row: PolicyRow): boolean {
  return row.status === 0
}

function statusText(value: number | undefined): string {
  return dictLabel('icp_policy_status', value)
}

onMounted(() => {
  void ensureIcpDicts()
  void loadCategories()
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card policy-layout">
      <CategoryTree
        :data="categories"
        :loading="categoryLoading"
        title="制度分类"
        @select="onCategorySelect"
      />

      <div class="policy-main">
        <div class="page-toolbar">
          <div class="page-toolbar-title">
            <span class="page-toolbar-heading">制度库</span>
            <span class="page-toolbar-desc">制度分类、版本与生效管理，覆盖起草、审批、发布、废止全流程</span>
          </div>
          <div class="page-toolbar-actions">
            <el-select
              v-model="query.status"
              class="toolbar-select"
              placeholder="状态"
              clearable
              @change="search"
            >
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="生效日期起"
              end-placeholder="生效日期止"
              class="toolbar-date"
              @change="onDateChange"
            />
            <el-input
              v-model="query.keyword"
              class="toolbar-input"
              placeholder="制度名称 / 编码 / 关键字"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" :icon="Plus" v-perm="'icp:policy:add'" @click="openCreate">
              新建制度
            </el-button>
            <el-button :icon="Download" v-perm="'icp:policy:export'" @click="exportList">导出 CSV</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无制度数据">
          <el-table-column prop="policyCode" label="制度编码" width="150" />
          <el-table-column label="制度名称" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.policyName }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="categoryName" label="分类" min-width="120" show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="80" align="center" />
          <el-table-column prop="effectiveDate" label="生效日期" width="110" />
          <el-table-column prop="expireDate" label="失效日期" width="110" />
          <el-table-column prop="ownerDeptName" label="归口部门" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="dictTagType('icp_policy_status', row.status)" size="small">
                {{ statusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" v-perm="'icp:policy:get'" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="canEdit(row)"
                link
                type="primary"
                v-perm="'icp:policy:edit'"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="row.status === 0"
                link
                type="warning"
                v-perm="'icp:policy:submit'"
                @click="submitApproval(row)"
              >
                提交审批
              </el-button>
              <el-button
                v-if="row.status === 1"
                link
                type="success"
                v-perm="'icp:policy:publish'"
                @click="publish(row)"
              >
                发布
              </el-button>
              <el-button
                v-if="row.status === 2"
                link
                type="danger"
                v-perm="'icp:policy:abolish'"
                @click="abolish(row)"
              >
                废止
              </el-button>
              <el-button
                v-if="row.status === 0"
                link
                type="danger"
                v-perm="'icp:policy:delete'"
                @click="removeRow(row)"
              >
                删除
              </el-button>
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
          <el-button text @click="resetAll">重置筛选</el-button>
        </div>
      </div>
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="制度名称" prop="policyName">
          <el-input v-model="form.policyName" maxlength="200" show-word-limit placeholder="如：采购管理制度" />
        </el-form-item>
        <el-form-item label="制度分类" prop="categoryId">
          <el-tree-select
            v-model="form.categoryId"
            :data="categories"
            node-key="id"
            check-strictly
            clearable
            :props="{ label: 'categoryName', children: 'children' }"
            placeholder="请选择制度分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="form.version" maxlength="20" placeholder="如：V1.0" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择生效日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker
            v-model="form.expireDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="可留空，长期有效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="归口部门">
          <DeptPicker v-model="form.ownerDeptId" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="form.keywords" maxlength="200" placeholder="多个关键字用逗号分隔" />
        </el-form-item>
        <el-form-item label="制度内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="20000"
            show-word-limit
            placeholder="请输入制度正文（M2b 阶段为纯文本编辑，富文本在 M3 接入）"
          />
        </el-form-item>
        <el-form-item label="制度附件">
          <div style="width: 100%">
            <FileUpload tip="支持 PDF/Word/Excel 等，单个不超过 20MB" @success="onFileSuccess" />
            <div v-if="formFiles.length" class="file-list">
              <el-tag
                v-for="file in formFiles"
                :key="file.id"
                closable
                type="info"
                @close="formFiles = formFiles.filter((item) => item.id !== file.id)"
              >
                {{ file.originalName }}
              </el-tag>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" title="制度详情" size="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="制度编码">{{ detail.policyCode }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="制度名称" :span="2">{{ detail.policyName }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="dictTagType('icp_policy_status', detail.status)" size="small">
              {{ statusText(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ detail.effectiveDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="失效日期">{{ detail.expireDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归口部门">{{ detail.ownerDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布时刻">{{ detail.publishTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关键字" :span="2">{{ detail.keywords || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section">
          <div class="detail-section-title">制度内容</div>
          <div class="detail-content">{{ detail.content || '暂无内容' }}</div>
        </div>
        <div v-if="detail.fileIds && detail.fileIds.length" class="detail-section">
          <div class="detail-section-title">关联附件</div>
          <div class="file-list">
            <el-tag v-for="id in detail.fileIds" :key="id" type="info">附件 ID {{ id }}</el-tag>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.policy-layout {
  display: flex;
  gap: 16px;
  min-height: 560px;
}

.policy-main {
  flex: 1;
  min-width: 0;
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
  width: 110px;
}

.toolbar-date {
  width: 250px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.detail-section {
  margin-top: 18px;
}

.detail-section-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: #1f2d3d;
}

.detail-content {
  padding: 12px;
  font-size: 13px;
  line-height: 1.8;
  color: #3f4b5b;
  white-space: pre-wrap;
  background: #f8fafc;
  border: 1px solid #eef1f6;
  border-radius: 8px;
}
</style>
