<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { controlApi } from '@/api/icp'
import DeptPicker from '@/components/DeptPicker.vue'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import type { ControlPayload, ControlQuery, ControlRow } from '@/types/icp'
import { dictLabel, dictOptions, dictTagType, ensureIcpDicts } from '@/utils/icpDict'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<ControlRow>((params) => controlApi.list(params as ControlQuery & Record<string, unknown>), {
    keyword: '',
    controlType: undefined,
    status: undefined
  })

const userStore = useUserStore()
const showActions = computed(
  () => userStore.hasPermission('icp:control:edit') || userStore.hasPermission('icp:control:delete')
)

const dialogVisible = ref(false)
const dialogTitle = ref('新增控制点')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<ControlPayload>({
  controlName: '',
  controlType: 1,
  controlDesc: '',
  freq: '每季度',
  ownerDeptId: undefined,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  controlName: [{ required: true, message: '请输入控制点名称', trigger: 'blur' }],
  controlType: [{ required: true, message: '请选择控制类型', trigger: 'change' }]
}

const typeOptions = computed(() => dictOptions('icp_control_type'))
const freqOptions = ['每次', '每日', '每周', '每月', '每季度', '每半年', '每年']

function resetForm(): void {
  form.controlName = ''
  form.controlType = 1
  form.controlDesc = ''
  form.freq = '每季度'
  form.ownerDeptId = undefined
  form.status = 1
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增控制点'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: ControlRow): void {
  editingId.value = row.id
  dialogTitle.value = `编辑控制点 - ${row.controlName}`
  form.controlName = row.controlName
  form.controlType = row.controlType ?? 1
  form.controlDesc = row.controlDesc || ''
  form.freq = row.freq || '每季度'
  form.ownerDeptId = row.ownerDeptId
  form.status = row.status ?? 1
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: ControlPayload = {
    ...form,
    controlType: Number(form.controlType),
    status: Number(form.status)
  }
  try {
    if (editingId.value === null) {
      await controlApi.create(payload)
      ElMessage.success('控制点新增成功')
    } else {
      await controlApi.update(editingId.value, payload)
      ElMessage.success('控制点修改成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: ControlRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除控制点「${row.controlName}」后不可恢复，且可能影响控制矩阵。是否继续？`, '删除控制点', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await controlApi.remove(row.id)
    ElMessage.success('控制点删除成功')
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
          <span class="page-toolbar-heading">控制点</span>
          <span class="page-toolbar-desc">维护预防/发现/纠正类控制点与执行频率，供控制矩阵关联</span>
        </div>
        <div class="page-toolbar-actions">
          <el-select
            v-model="query.controlType"
            class="toolbar-select"
            placeholder="控制类型"
            clearable
            @change="search"
          >
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.status" class="toolbar-select" placeholder="状态" clearable @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="控制点名称 / 编码"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-button type="primary" :icon="Plus" v-perm="'icp:control:add'" @click="openCreate">
            新增控制点
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无控制点数据">
        <el-table-column prop="controlCode" label="控制点编码" width="140" />
        <el-table-column prop="controlName" label="控制点名称" min-width="220" show-overflow-tooltip />
        <el-table-column label="控制类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="dictTagType('icp_control_type', row.controlType)" size="small">
              {{ dictLabel('icp_control_type', row.controlType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="freq" label="频率" width="100" align="center" />
        <el-table-column prop="ownerDeptName" label="责任部门" min-width="120" show-overflow-tooltip />
        <el-table-column prop="controlDesc" label="控制描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="showActions" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-perm="'icp:control:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-perm="'icp:control:delete'" @click="removeRow(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="控制点名称" prop="controlName">
          <el-input v-model="form.controlName" maxlength="200" show-word-limit placeholder="如：采购合同双人复核" />
        </el-form-item>
        <el-form-item label="控制类型" prop="controlType">
          <el-radio-group v-model="form.controlType">
            <el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="执行频率">
          <el-select v-model="form.freq" placeholder="选择频率" style="width: 100%">
            <el-option v-for="item in freqOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任部门">
          <DeptPicker v-model="form.ownerDeptId" />
        </el-form-item>
        <el-form-item label="控制描述">
          <el-input v-model="form.controlDesc" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
</style>
