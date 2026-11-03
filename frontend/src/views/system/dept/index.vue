<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import { deptApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import type { DeptNode, DeptPayload } from '@/types/system'

interface DeptTreeProps {
  label: string
  children: string
}

const treeProps: DeptTreeProps = { label: 'name', children: 'children' }
const treeData = ref<DeptNode[]>([])
const treeLoading = ref(false)
const flatDepts = ref<DeptNode[]>([])
const selectedDeptId = ref<string | number | ''>('')

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<DeptNode>(deptApi.list, { keyword: '', status: '', deptId: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('新增部门')
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<DeptPayload>({
  name: '',
  parentId: null,
  leader: '',
  phone: '',
  email: '',
  sort: 1,
  status: 1
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序号', trigger: 'blur' }]
}

function flatten(nodes: DeptNode[], output: DeptNode[] = []): DeptNode[] {
  nodes.forEach((node) => {
    output.push(node)
    if (node.children && node.children.length) flatten(node.children, output)
  })
  return output
}

async function loadTree(): Promise<void> {
  treeLoading.value = true
  try {
    treeData.value = (await deptApi.tree()) || []
    flatDepts.value = flatten(treeData.value)
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    treeLoading.value = false
  }
}

function handleTreeClick(node: DeptNode): void {
  selectedDeptId.value = node.id
  query.deptId = node.id
  search()
}

function clearTreeFilter(): void {
  selectedDeptId.value = ''
  query.deptId = ''
  search()
}

function resetForm(): void {
  form.name = ''
  form.parentId = null
  form.leader = ''
  form.phone = ''
  form.email = ''
  form.sort = 1
  form.status = 1
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增部门'
  resetForm()
  form.parentId = selectedDeptId.value || null
  dialogVisible.value = true
}

function openEdit(row: DeptNode): void {
  editingId.value = row.id
  dialogTitle.value = '编辑部门'
  form.name = row.name
  form.parentId = row.parentId ?? null
  form.leader = row.leader || ''
  form.phone = row.phone || ''
  form.email = row.email || ''
  form.sort = row.sort ?? 1
  form.status = row.status ?? 1
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: DeptPayload = {
    name: form.name,
    parentId: form.parentId === null || form.parentId === '' ? null : form.parentId,
    leader: form.leader,
    phone: form.phone,
    email: form.email,
    sort: Number(form.sort) || 1,
    status: Number(form.status) as 0 | 1
  }
  try {
    if (editingId.value === null) {
      await deptApi.create(payload)
      ElMessage.success('部门新增成功')
    } else {
      await deptApi.update(editingId.value, payload)
      ElMessage.success('部门修改成功')
    }
    dialogVisible.value = false
    await loadTree()
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: DeptNode): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除部门「${row.name}」后不可恢复；若存在子部门或用户，后端将拒绝删除。是否继续？`,
      '删除部门',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deptApi.remove(row.id)
    ElMessage.success('部门删除成功')
    if (selectedDeptId.value === row.id) clearTreeFilter()
    await loadTree()
    await load()
  } catch {
    // 失败原因（如「存在子部门」）由 request.ts 统一提示
  }
}

onMounted(() => {
  void loadTree()
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card dept-layout">
      <aside class="dept-tree-panel">
        <div class="panel-header">
          <span class="panel-title">组织架构</span>
          <el-button text :icon="RefreshRight" :loading="treeLoading" @click="loadTree">刷新</el-button>
        </div>
        <div v-loading="treeLoading" class="dept-tree-body">
          <el-tree
            :data="treeData"
            :props="treeProps"
            node-key="id"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            @node-click="handleTreeClick"
          />
          <el-empty v-if="!treeLoading && treeData.length === 0" :image-size="60" description="暂无部门数据" />
        </div>
      </aside>

      <section class="dept-table-panel">
        <div class="panel-header">
          <span class="panel-title">部门列表</span>
          <div class="toolbar">
            <el-input
              v-model="query.keyword"
              class="toolbar-input"
              placeholder="部门名称 / 负责人"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-select v-model="query.status" class="toolbar-select" placeholder="状态" clearable @change="search">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
            <el-button type="primary" :icon="Plus" v-perm="'system:dept:add'" @click="openCreate">新增部门</el-button>
          </div>
        </div>

        <div v-if="selectedDeptId !== ''" class="filter-tip">
          当前筛选：{{ flatDepts.find((item) => item.id === selectedDeptId)?.name || selectedDeptId }}
          <el-button link type="primary" @click="clearTreeFilter">清除</el-button>
        </div>

        <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无部门数据">
          <el-table-column prop="name" label="部门名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="leader" label="负责人" width="120" />
          <el-table-column prop="phone" label="联系电话" width="140" />
          <el-table-column prop="sort" label="排序" width="80" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" v-perm="'system:dept:edit'" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" v-perm="'system:dept:delete'" @click="removeRow(row)">删除</el-button>
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
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="form.parentId"
            :data="treeData"
            :props="treeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="不选择则为一级部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="如：内控合规部" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" maxlength="30" placeholder="部门负责人姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" maxlength="20" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="60" placeholder="部门邮箱（选填）" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
.dept-layout {
  display: flex;
  gap: 16px;
  align-items: stretch;
  padding: 16px;
}

.dept-tree-panel {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 280px;
  padding-right: 16px;
  border-right: 1px solid #eef1f6;
}

.dept-tree-body {
  min-height: 320px;
  max-height: calc(100vh - 220px);
  overflow: auto;
}

.dept-table-panel {
  flex: 1;
  min-width: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.toolbar-input {
  width: 200px;
}

.toolbar-select {
  width: 110px;
}

.filter-tip {
  margin-bottom: 10px;
  font-size: 13px;
  color: #606a7b;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

@media (max-width: 900px) {
  .dept-layout {
    flex-direction: column;
  }

  .dept-tree-panel {
    width: 100%;
    padding-right: 0;
    border-right: none;
  }
}
</style>
