<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { rectifyApi } from '@/api/icp'
import FileUpload from '@/components/FileUpload.vue'
import { usePageList } from '@/composables/usePageList'
import type { RectifyPayload, RectifyQuery, RectifyRow } from '@/types/icp'
import type { UploadResult } from '@/types/system'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'

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
} = usePageList<RectifyRow>((params) => rectifyApi.list(params as RectifyQuery & Record<string, unknown>), {
  keyword: '',
  status: undefined
})

const statusOptions = computed(() => dictOptions('icp_rectify_status'))

const detailVisible = ref(false)
const detailRow = ref<RectifyRow | null>(null)

const editVisible = ref(false)
const editTarget = ref<RectifyRow | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive<RectifyPayload>({
  rectifyPlan: '',
  responsibleUserId: undefined,
  deadline: '',
  remark: ''
})

const editRules: FormRules = {
  rectifyPlan: [{ required: true, message: '请填写整改计划', trigger: 'blur' }]
}

const submitVisible = ref(false)
const submitTarget = ref<RectifyRow | null>(null)
const submitForm = reactive<{ evidence: number[]; remark: string; finishDate: string }>({
  evidence: [],
  remark: '',
  finishDate: new Date().toISOString().slice(0, 10)
})

const rejectVisible = ref(false)
const rejectTarget = ref<RectifyRow | null>(null)
const rejectFormRef = ref<FormInstance>()
const rejectForm = reactive<{ reviewOpinion: string }>({ reviewOpinion: '' })

const rejectRules: FormRules = {
  reviewOpinion: [{ required: true, message: '请填写驳回意见', trigger: 'blur' }]
}

function openDetail(row: RectifyRow): void {
  detailRow.value = row
  detailVisible.value = true
}

function openEdit(row: RectifyRow): void {
  editTarget.value = row
  editForm.rectifyPlan = row.rectifyPlan || ''
  editForm.responsibleUserId = row.responsibleUserId
  editForm.deadline = row.deadline || ''
  editForm.remark = row.remark || ''
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editTarget.value || !editFormRef.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await rectifyApi.update(editTarget.value.id, { ...editForm })
    ElMessage.success('整改计划已更新')
    editVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function openSubmit(row: RectifyRow): void {
  submitTarget.value = row
  submitForm.evidence = []
  submitForm.remark = ''
  submitForm.finishDate = new Date().toISOString().slice(0, 10)
  submitVisible.value = true
}

function onEvidenceSuccess(file: UploadResult): void {
  submitForm.evidence = [Number(file.id)]
}

function onEvidenceRemove(): void {
  submitForm.evidence = []
}

async function submitEvidence(): Promise<void> {
  if (!submitTarget.value) return
  if (!submitForm.evidence.length) {
    ElMessage.warning('请上传整改证据附件')
    return
  }
  try {
    await rectifyApi.submit(submitTarget.value.id, {
      evidence: submitForm.evidence,
      remark: submitForm.remark,
      finishDate: submitForm.finishDate
    })
    ElMessage.success('整改证据已提交，等待复核')
    submitVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function approve(row: RectifyRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认通过整改「${row.rectifyCode}」并完成复核？`, '通过整改', {
      type: 'warning',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await rectifyApi.approve(row.id)
    ElMessage.success('整改复核通过')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function openReject(row: RectifyRow): void {
  rejectTarget.value = row
  rejectForm.reviewOpinion = ''
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  if (!rejectTarget.value || !rejectFormRef.value) return
  const valid = await rejectFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await rectifyApi.reject(rejectTarget.value.id, { reviewOpinion: rejectForm.reviewOpinion })
    ElMessage.success('整改已驳回，退回责任人重新提交')
    rejectVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function closeRectify(row: RectifyRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认关闭整改「${row.rectifyCode}」？`, '关闭整改', {
      type: 'warning',
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await rectifyApi.close(row.id)
    ElMessage.success('整改已关闭')
    await load()
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
          <span class="page-toolbar-heading">整改跟踪</span>
          <span class="page-toolbar-desc">责任人提交整改证据 → 内控复核（通过/驳回）→ 关闭，全程留痕</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select v-model="query.status" class="toolbar-select" placeholder="整改状态" clearable @change="search">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="整改单号 / 缺陷名称"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无整改单">
        <el-table-column prop="rectifyCode" label="整改单号" width="130" />
        <el-table-column prop="defectName" label="关联缺陷" min-width="170" show-overflow-tooltip />
        <el-table-column prop="rectifyPlan" label="整改计划" min-width="190" show-overflow-tooltip />
        <el-table-column prop="deptName" label="责任部门" min-width="110" show-overflow-tooltip />
        <el-table-column prop="responsibleUserName" label="责任人" width="90" />
        <el-table-column prop="deadline" label="整改期限" width="112" align="center" />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="dictTagType('icp_rectify_status', row.status)" size="small">
              {{ dictLabel('icp_rectify_status', row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="primary" v-perm="'icp:rectify:edit'" @click="openEdit(row)">
              编辑计划
            </el-button>
            <el-button
              v-if="row.status === 0 || row.status === 3"
              link
              type="warning"
              v-perm="'icp:rectify:submit'"
              @click="openSubmit(row)"
            >
              提交证据
            </el-button>
            <el-button v-if="row.status === 1" link type="success" v-perm="'icp:rectify:approve'" @click="approve(row)">
              通过
            </el-button>
            <el-button v-if="row.status === 1" link type="danger" v-perm="'icp:rectify:reject'" @click="openReject(row)">
              驳回
            </el-button>
            <el-button v-if="row.status === 2" link type="info" v-perm="'icp:rectify:close'" @click="closeRectify(row)">
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

    <el-dialog v-model="editVisible" title="编辑整改计划" width="600px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="整改计划" prop="rectifyPlan">
          <el-input v-model="editForm.rectifyPlan" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="整改期限">
          <el-date-picker v-model="editForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="责任人 ID">
          <el-input-number
            v-model="editForm.responsibleUserId"
            :min="1"
            :controls="false"
            placeholder="可选：责任人用户 ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="submitVisible" title="提交整改证据" width="600px" destroy-on-close>
      <el-alert
        v-if="submitTarget"
        :title="`${submitTarget.rectifyCode} · ${submitTarget.defectName || ''}`"
        type="warning"
        :closable="false"
        show-icon
        class="evidence-alert"
      />
      <el-form label-width="100px">
        <el-form-item label="整改证据" required>
          <FileUpload @success="onEvidenceSuccess" @remove="onEvidenceRemove" />
        </el-form-item>
        <el-form-item label="完成日期">
          <el-date-picker v-model="submitForm.finishDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="整改说明">
          <el-input
            v-model="submitForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="说明整改完成情况与验证结果"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEvidence">提交证据</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="驳回整改" width="520px" destroy-on-close>
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="90px">
        <el-form-item label="驳回意见" prop="reviewOpinion">
          <el-input
            v-model="rejectForm.reviewOpinion"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说明整改不通过的原因与补充要求"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detailRow ? `整改详情 - ${detailRow.rectifyCode}` : '整改详情'" size="560px">
      <el-descriptions v-if="detailRow" :column="1" border>
        <el-descriptions-item label="关联缺陷">
          {{ detailRow.defectCode || '-' }} {{ detailRow.defectName || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="整改计划">{{ detailRow.rectifyPlan || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任部门">{{ detailRow.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="责任人">{{ detailRow.responsibleUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="整改期限">{{ detailRow.deadline || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成日期">{{ detailRow.finishDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="dictTagType('icp_rectify_status', detailRow.status)" size="small">
            {{ dictLabel('icp_rectify_status', detailRow.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="证据附件">{{ detailRow.evidence || '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="复核人">{{ detailRow.reviewUserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核意见">{{ detailRow.reviewOpinion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="复核时间">{{ detailRow.reviewTime || '-' }}</el-descriptions-item>
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
  width: 200px;
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

.evidence-alert {
  margin-bottom: 12px;
}
</style>
