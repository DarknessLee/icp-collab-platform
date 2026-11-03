<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import { defectApi } from '@/api/icp'
import DeptPicker from '@/components/DeptPicker.vue'
import { usePageList } from '@/composables/usePageList'
import type { DefectPayload, DefectQuery, DefectRow } from '@/types/icp'
import { exportCsv } from '@/utils/exportCsv'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'
import { fetchAllPages } from '@/utils/pagedAll'

const {
  query,
  list,
  total,
  loading,
  load,
  search,
  reset,
  handlePageChange,
  handleSizeChange
} = usePageList<DefectRow>((params) => defectApi.list(params as DefectQuery & Record<string, unknown>), {
  keyword: '',
  severity: undefined,
  status: undefined,
  sourceType: undefined
})

const dialogVisible = ref(false)
const dialogTitle = ref('登记缺陷')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<DefectPayload>({
  defectName: '',
  sourceType: 3,
  severity: 2,
  defectType: 2,
  deptId: undefined,
  findDate: new Date().toISOString().slice(0, 10),
  ownerUserId: undefined,
  description: '',
  remark: ''
})

const rules: FormRules = {
  defectName: [{ required: true, message: '请输入缺陷名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择缺陷来源', trigger: 'change' }],
  severity: [{ required: true, message: '请选择严重程度', trigger: 'change' }],
  defectType: [{ required: true, message: '请选择缺陷类型', trigger: 'change' }],
  description: [{ required: true, message: '请填写缺陷描述', trigger: 'blur' }]
}

const severityOptions = computed(() => dictOptions('icp_defect_severity'))
const sourceOptions = computed(() => dictOptions('icp_defect_source'))
const typeOptions = computed(() => dictOptions('icp_defect_type'))
const statusOptions = computed(() => dictOptions('icp_defect_status'))

const detailVisible = ref(false)
const detailRow = ref<DefectRow | null>(null)

const rectifyVisible = ref(false)
const rectifyTarget = ref<DefectRow | null>(null)
const rectifyFormRef = ref<FormInstance>()
const rectifyForm = reactive<{ rectifyPlan: string; deadline: string; responsibleUserId?: number }>({
  rectifyPlan: '',
  deadline: '',
  responsibleUserId: undefined
})

const rectifyRules: FormRules = {
  rectifyPlan: [{ required: true, message: '请填写整改计划', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择整改截止日期', trigger: 'change' }]
}

function resetForm(): void {
  form.defectName = ''
  form.sourceType = 3
  form.severity = 2
  form.defectType = 2
  form.deptId = undefined
  form.findDate = new Date().toISOString().slice(0, 10)
  form.ownerUserId = undefined
  form.description = ''
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '登记缺陷'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: DefectRow): void {
  editingId.value = row.id
  dialogTitle.value = `编辑缺陷 - ${row.defectCode}`
  form.defectName = row.defectName
  form.sourceType = row.sourceType ?? 3
  form.severity = row.severity ?? 2
  form.defectType = row.defectType ?? 2
  form.deptId = row.deptId
  form.findDate = row.findDate || ''
  form.ownerUserId = row.ownerUserId
  form.description = row.description || ''
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: DefectPayload = {
    ...form,
    sourceType: Number(form.sourceType),
    severity: Number(form.severity),
    defectType: Number(form.defectType)
  }
  try {
    if (editingId.value === null) {
      await defectApi.create(payload)
      ElMessage.success('缺陷登记成功')
    } else {
      await defectApi.update(editingId.value, payload)
      ElMessage.success('缺陷修改成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function openDetail(row: DefectRow): void {
  detailRow.value = row
  detailVisible.value = true
}

async function closeDefect(row: DefectRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确认关闭缺陷「${row.defectCode}」？关闭前应确保整改已完成并通过复核。`,
      '关闭缺陷',
      { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await defectApi.close(row.id)
    ElMessage.success('缺陷已关闭')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function openRectify(row: DefectRow): void {
  rectifyTarget.value = row
  rectifyForm.rectifyPlan = ''
  rectifyForm.deadline = ''
  rectifyForm.responsibleUserId = row.ownerUserId
  rectifyVisible.value = true
}

async function submitRectify(): Promise<void> {
  if (!rectifyTarget.value || !rectifyFormRef.value) return
  const valid = await rectifyFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await defectApi.rectify(rectifyTarget.value.id, {
      rectifyPlan: rectifyForm.rectifyPlan,
      deadline: rectifyForm.deadline,
      responsibleUserId: rectifyForm.responsibleUserId
    })
    ElMessage.success('已发起整改，等待责任人提交整改证据')
    rectifyVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function exportList(): Promise<void> {
  try {
    const rows = await fetchAllPages<DefectRow>((params) => defectApi.list(params), {
      keyword: query.keyword,
      severity: query.severity,
      status: query.status,
      sourceType: query.sourceType
    })
    if (!rows.length) {
      ElMessage.warning('当前筛选结果为空，未导出')
      return
    }
    exportCsv('缺陷台账导出', rows, [
      { title: '缺陷编码', get: (row) => row.defectCode },
      { title: '缺陷名称', get: (row) => row.defectName },
      { title: '来源', get: (row) => dictLabel('icp_defect_source', row.sourceType) },
      { title: '严重程度', get: (row) => dictLabel('icp_defect_severity', row.severity) },
      { title: '缺陷类型', get: (row) => dictLabel('icp_defect_type', row.defectType) },
      { title: '责任部门', get: (row) => row.deptName },
      { title: '发现日期', get: (row) => row.findDate },
      { title: '状态', get: (row) => dictLabel('icp_defect_status', row.status) },
      { title: '整改编码', get: (row) => row.rectifyCode }
    ])
    ElMessage.success(`已导出 ${rows.length} 条缺陷`)
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

onMounted(() => {
  void ensureIcpDicts()
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">缺陷台账</span>
          <span class="page-toolbar-desc">评价发现的缺陷登记、整改跟踪与关闭；staff 仅可见本人相关缺陷</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select v-model="query.severity" class="toolbar-select" placeholder="严重程度" clearable @change="search">
            <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.sourceType" class="toolbar-select" placeholder="缺陷来源" clearable @change="search">
            <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.status" class="toolbar-select" placeholder="缺陷状态" clearable @change="search">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="缺陷名称 / 编码"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-button type="primary" :icon="Plus" v-perm="'icp:defect:add'" @click="openCreate">登记缺陷</el-button>
          <!-- docs/05 缺陷权限未定义 export，后端暂未种该标识；任务要求缺陷可导出，暂以 list 权限作可见性门禁（数据仍受后端数据范围约束），已作为契约偏差上报 -->
          <el-button :icon="Download" v-perm="'icp:defect:list'" @click="exportList">导出 CSV</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无缺陷数据">
        <el-table-column prop="defectCode" label="缺陷编码" width="130" />
        <el-table-column prop="defectName" label="缺陷名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="严重程度" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="dictTagType('icp_defect_severity', row.severity)" size="small">
              {{ dictLabel('icp_defect_severity', row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="dictTagType('icp_defect_type', row.defectType)" size="small">
              {{ dictLabel('icp_defect_type', row.defectType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="责任部门" min-width="120" show-overflow-tooltip />
        <el-table-column prop="ownerUserName" label="责任人" width="100" />
        <el-table-column prop="findDate" label="发现日期" width="120" align="center" />
        <el-table-column prop="rectifyCode" label="整改单号" width="130" />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="dictTagType('icp_defect_status', row.status)" size="small">
              {{ dictLabel('icp_defect_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-perm="'icp:defect:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="!row.rectifyCode && row.status !== 3"
              link
              type="warning"
              v-perm="'icp:defect:rectify'"
              @click="openRectify(row)"
            >
              发起整改
            </el-button>
            <el-button v-if="row.status !== 3" link type="success" v-perm="'icp:defect:close'" @click="closeDefect(row)">
              关闭
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
        <el-button text @click="reset">重置筛选</el-button>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="缺陷名称" prop="defectName">
          <el-input v-model="form.defectName" maxlength="200" show-word-limit placeholder="如：采购验收单据缺失" />
        </el-form-item>
        <el-form-item label="缺陷来源" prop="sourceType">
          <el-select v-model="form.sourceType" style="width: 100%">
            <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度" prop="severity">
          <el-radio-group v-model="form.severity">
            <el-radio-button v-for="item in severityOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="缺陷类型" prop="defectType">
          <el-radio-group v-model="form.defectType">
            <el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="责任部门">
          <DeptPicker v-model="form.deptId" />
        </el-form-item>
        <el-form-item label="发现日期">
          <el-date-picker v-model="form.findDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="缺陷描述" prop="description">
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

    <el-dialog v-model="rectifyVisible" title="发起整改" width="560px" destroy-on-close>
      <el-alert
        v-if="rectifyTarget"
        :title="`缺陷 ${rectifyTarget.defectCode}：${rectifyTarget.defectName}`"
        type="warning"
        :closable="false"
        show-icon
        class="rectify-alert"
      />
      <el-form ref="rectifyFormRef" :model="rectifyForm" :rules="rectifyRules" label-width="100px">
        <el-form-item label="整改计划" prop="rectifyPlan">
          <el-input
            v-model="rectifyForm.rectifyPlan"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="说明整改措施、责任人与完成标准"
          />
        </el-form-item>
        <el-form-item label="整改期限" prop="deadline">
          <el-date-picker v-model="rectifyForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="责任人 ID">
          <el-input-number v-model="rectifyForm.responsibleUserId" :min="1" :controls="false" placeholder="可选：责任人用户 ID" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rectifyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRectify">发起整改</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detailRow ? `缺陷详情 - ${detailRow.defectCode}` : '缺陷详情'" size="560px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="缺陷名称">{{ detailRow.defectName }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ dictLabel('icp_defect_source', detailRow.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="严重程度">
          {{ dictLabel('icp_defect_severity', detailRow.severity) }}
        </el-descriptions-item>
        <el-descriptions-item label="缺陷类型">{{ dictLabel('icp_defect_type', detailRow.defectType) }}</el-descriptions-item>
        <el-descriptions-item label="责任部门">{{ detailRow.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ detailRow.ownerUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发现人">{{ detailRow.discoverUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发现日期">{{ detailRow.findDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="dictTagType('icp_defect_status', detailRow.status)" size="small">
            {{ dictLabel('icp_defect_status', detailRow.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="整改单号">{{ detailRow.rectifyCode || '未发起' }}</el-descriptions-item>
        <el-descriptions-item label="缺陷描述">{{ detailRow.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭时间">{{ detailRow.closeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailRow.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
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
  width: 180px;
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

.rectify-alert {
  margin-bottom: 12px;
}
</style>
