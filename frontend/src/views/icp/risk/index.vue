<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import { riskApi } from '@/api/icp'
import CategoryTree from '@/components/CategoryTree.vue'
import DeptPicker from '@/components/DeptPicker.vue'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import type { IcpCategory, RiskPayload, RiskQuery, RiskRow } from '@/types/icp'
import { exportCsv } from '@/utils/exportCsv'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'
import { fetchAllPages } from '@/utils/pagedAll'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<RiskRow>((params) => riskApi.list(params as RiskQuery & Record<string, unknown>), {
    keyword: '',
    categoryId: undefined,
    riskLevel: undefined,
    status: undefined
  })

// 报表图表下钻：/icp/risk?riskLevel= 带入筛选条件
const route = useRoute()
if (route.query.riskLevel !== undefined) query.riskLevel = Number(route.query.riskLevel)

const userStore = useUserStore()
const showActions = computed(
  () =>
    userStore.hasPermission('icp:risk:edit') ||
    userStore.hasPermission('icp:risk:status') ||
    userStore.hasPermission('icp:risk:delete')
)

const categories = ref<IcpCategory[]>([])
const categoryLoading = ref(false)

const dialogVisible = ref(false)
const dialogTitle = ref('新增风险')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<RiskPayload>({
  riskName: '',
  categoryId: undefined,
  probability: 3,
  impact: 3,
  ownerDeptId: undefined,
  description: '',
  remark: ''
})

const rules: FormRules = {
  riskName: [{ required: true, message: '请输入风险名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择风险分类', trigger: 'change' }],
  probability: [{ required: true, message: '请选择可能性', trigger: 'change' }],
  impact: [{ required: true, message: '请选择影响程度', trigger: 'change' }]
}

const levelOptions = computed(() => dictOptions('icp_risk_level'))
const score = computed(() => Number(form.probability || 0) * Number(form.impact || 0))
const previewLevel = computed(() => (score.value >= 15 ? '高' : score.value >= 8 ? '中' : '低'))
const previewTag = computed(() => (score.value >= 15 ? 'danger' : score.value >= 8 ? 'warning' : 'success'))

async function loadCategories(): Promise<void> {
  categoryLoading.value = true
  try {
    categories.value = await riskApi.categoryTree()
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

function resetForm(): void {
  form.riskName = ''
  form.categoryId = undefined
  form.probability = 3
  form.impact = 3
  form.ownerDeptId = undefined
  form.description = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增风险'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: RiskRow): void {
  editingId.value = row.id
  dialogTitle.value = `编辑风险 - ${row.riskName}`
  form.riskName = row.riskName
  form.categoryId = row.categoryId
  form.probability = row.probability ?? 3
  form.impact = row.impact ?? 3
  form.ownerDeptId = row.ownerDeptId
  form.description = row.description || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: RiskPayload = {
    ...form,
    probability: Number(form.probability),
    impact: Number(form.impact)
  }
  try {
    if (editingId.value === null) {
      await riskApi.create(payload)
      ElMessage.success('风险新增成功')
    } else {
      await riskApi.update(editingId.value, payload)
      ElMessage.success('风险修改成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: RiskRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除风险「${row.riskName}」后不可恢复，且可能影响已关联的控制矩阵。是否继续？`, '删除风险', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await riskApi.remove(row.id)
    ElMessage.success('风险删除成功')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function toggleStatus(row: RiskRow): Promise<void> {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${action}风险「${row.riskName}」？`, `${action}风险`, {
      type: 'warning',
      confirmButtonText: `确认${action}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await riskApi.updateStatus(row.id, next)
    ElMessage.success(`风险已${action}`)
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function exportList(): Promise<void> {
  try {
    const rows = await fetchAllPages<RiskRow>(
      (params) => riskApi.list(params as RiskQuery & Record<string, unknown>),
      {
        keyword: query.keyword,
        categoryId: query.categoryId,
        riskLevel: query.riskLevel,
        status: query.status
      }
    )
    if (!rows.length) {
      ElMessage.warning('当前筛选结果为空，未导出')
      return
    }
    exportCsv('风险库导出', rows, [
      { title: '风险编码', get: (row) => row.riskCode },
      { title: '风险名称', get: (row) => row.riskName },
      { title: '分类', get: (row) => row.categoryName },
      { title: '等级', get: (row) => dictLabel('icp_risk_level', row.riskLevel) },
      { title: '可能性', get: (row) => row.probability },
      { title: '影响程度', get: (row) => row.impact },
      { title: '风险评分', get: (row) => row.riskScore },
      { title: '责任部门', get: (row) => row.ownerDeptName },
      { title: '状态', get: (row) => (row.status === 1 ? '启用' : '停用') }
    ])
    ElMessage.success(`已导出 ${rows.length} 条风险`)
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

onMounted(() => {
  void ensureIcpDicts()
  void loadCategories()
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card risk-layout">
      <CategoryTree :data="categories" :loading="categoryLoading" title="风险分类" @select="onCategorySelect" />

      <div class="risk-main">
        <div class="page-toolbar">
          <div class="page-toolbar-title">
            <span class="page-toolbar-heading">风险库</span>
            <span class="page-toolbar-desc">按影响 × 可能性评分定级（高 ≥15 / 中 8–14 / 低 ≤7），关联责任部门</span>
          </div>
          <div class="page-toolbar-actions">
            <el-select
              v-model="query.riskLevel"
              class="toolbar-select"
              placeholder="风险等级"
              clearable
              @change="search"
            >
              <el-option v-for="item in levelOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select
              v-model="query.status"
              class="toolbar-select"
              placeholder="状态"
              clearable
              @change="search"
            >
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-input
              v-model="query.keyword"
              class="toolbar-input"
              placeholder="风险名称 / 编码"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" :icon="Plus" v-perm="'icp:risk:add'" @click="openCreate">新增风险</el-button>
            <el-button :icon="Download" v-perm="'icp:risk:export'" @click="exportList">导出 CSV</el-button>
          </div>
        </div>

        <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无风险数据">
          <el-table-column prop="riskCode" label="风险编码" width="130" />
          <el-table-column prop="riskName" label="风险名称" min-width="220" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类" min-width="110" show-overflow-tooltip />
          <el-table-column label="等级" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="dictTagType('icp_risk_level', row.riskLevel)" size="small">
                {{ dictLabel('icp_risk_level', row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="影响 × 可能性" width="130" align="center">
            <template #default="{ row }">{{ row.probability ?? '-' }} × {{ row.impact ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="riskScore" label="评分" width="70" align="center" />
          <el-table-column prop="ownerDeptName" label="责任部门" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="showActions" label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" v-perm="'icp:risk:edit'" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" v-perm="'icp:risk:status'" @click="toggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" v-perm="'icp:risk:delete'" @click="removeRow(row)">删除</el-button>
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
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="风险名称" prop="riskName">
          <el-input v-model="form.riskName" maxlength="200" show-word-limit placeholder="如：采购审批越权风险" />
        </el-form-item>
        <el-form-item label="风险分类" prop="categoryId">
          <el-tree-select
            v-model="form.categoryId"
            :data="categories"
            node-key="id"
            check-strictly
            clearable
            :props="{ label: 'categoryName', children: 'children' }"
            placeholder="请选择风险分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="可能性" prop="probability">
          <el-select v-model="form.probability" placeholder="1–5" style="width: 100%">
            <el-option v-for="n in 5" :key="n" :label="`${n} 级`" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="影响程度" prop="impact">
          <el-select v-model="form.impact" placeholder="1–5" style="width: 100%">
            <el-option v-for="n in 5" :key="n" :label="`${n} 级`" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="评分预览">
          <div class="score-preview">
            <el-tag :type="previewTag" size="small">{{ previewLevel }}</el-tag>
            <span>风险评分 {{ score }}（最终以保存后后端计算为准）</span>
          </div>
        </el-form-item>
        <el-form-item label="责任部门">
          <DeptPicker v-model="form.ownerDeptId" />
        </el-form-item>
        <el-form-item label="风险描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit />
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
.risk-layout {
  display: flex;
  gap: 16px;
  min-height: 560px;
}

.risk-main {
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
  width: 120px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.score-preview {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: #5b6b82;
}
</style>
