<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { postApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import type { PostPayload, PostRow } from '@/types/system'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<PostRow>(postApi.list, { keyword: '', status: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('新增岗位')
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<PostPayload>({ code: '', name: '', sort: 1, status: 1, remark: '' })
const rules: FormRules = {
  code: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }]
}

function resetForm(): void {
  form.code = ''
  form.name = ''
  form.sort = 1
  form.status = 1
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增岗位'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: PostRow): void {
  editingId.value = row.id
  dialogTitle.value = '编辑岗位'
  form.code = row.code
  form.name = row.name
  form.sort = row.sort ?? 1
  form.status = row.status ?? 1
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: PostPayload = {
    code: form.code,
    name: form.name,
    sort: Number(form.sort) || 1,
    status: Number(form.status) as 0 | 1,
    remark: form.remark
  }
  try {
    if (editingId.value === null) {
      await postApi.create(payload)
      ElMessage.success('岗位新增成功')
    } else {
      await postApi.update(editingId.value, payload)
      ElMessage.success('岗位修改成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: PostRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除岗位「${row.name}」后不可恢复；若已分配用户，后端将拒绝删除。是否继续？`,
      '删除岗位',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await postApi.remove(row.id)
    ElMessage.success('岗位删除成功')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">岗位管理</span>
          <span class="page-toolbar-desc">维护组织岗位与排序，用户可关联岗位</span>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="岗位名称 / 编码"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="状态" clearable @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Plus" v-perm="'system:post:add'" @click="openCreate">新增岗位</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无岗位数据">
        <el-table-column prop="code" label="岗位编码" width="140" />
        <el-table-column prop="name" label="岗位名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-perm="'system:post:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-perm="'system:post:delete'" @click="removeRow(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="岗位编码" prop="code">
          <el-input v-model="form.code" maxlength="40" placeholder="如：ICP_MANAGER" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="如：内控管理员" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
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
  width: 200px;
}

.toolbar-select {
  width: 110px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}
</style>
