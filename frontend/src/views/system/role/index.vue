<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { menuApi, roleApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import type { MenuNode, RolePayload, RoleRow } from '@/types/system'

const BUILTIN_ROLE_CODES = ['ADMIN', 'ICP_MANAGER', 'DEPT_MANAGER', 'EMPLOYEE', 'AUDITOR', 'MANAGEMENT']
const DATA_SCOPE_OPTIONS = [
  { value: 1, label: '本人' },
  { value: 2, label: '本部门' },
  { value: 3, label: '本部门及下级' },
  { value: 4, label: '全部' },
  { value: 5, label: '自定义（二期）', disabled: true }
]

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<RoleRow>(roleApi.list, { keyword: '', status: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<RolePayload>({ name: '', code: '', dataScope: 1, sort: 1, status: 1, remark: '' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  dataScope: [{ required: true, message: '请选择数据范围', trigger: 'change' }]
}

const menuDrawerVisible = ref(false)
const menuTreeLoading = ref(false)
const menuTreeData = ref<MenuNode[]>([])
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const menuRole = ref<RoleRow | null>(null)
const menuSaving = ref(false)
const menuTreeProps = { label: 'name', children: 'children' }

function dataScopeLabel(value?: number): string {
  return DATA_SCOPE_OPTIONS.find((item) => item.value === value)?.label || '-'
}

function isBuiltin(row: RoleRow): boolean {
  return row.builtin === true || BUILTIN_ROLE_CODES.includes(row.code)
}

function resetForm(): void {
  form.name = ''
  form.code = ''
  form.dataScope = 1
  form.sort = 1
  form.status = 1
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增角色'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: RoleRow): void {
  editingId.value = row.id
  dialogTitle.value = `编辑角色 - ${row.name}`
  form.name = row.name
  form.code = row.code
  form.dataScope = row.dataScope ?? 1
  form.sort = row.sort ?? 1
  form.status = row.status ?? 1
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: RolePayload = {
    name: form.name,
    code: form.code,
    dataScope: Number(form.dataScope),
    sort: Number(form.sort) || 1,
    status: Number(form.status) as 0 | 1,
    remark: form.remark
  }
  try {
    if (editingId.value === null) {
      await roleApi.create(payload)
      ElMessage.success('角色新增成功')
    } else {
      await roleApi.update(editingId.value, payload)
      ElMessage.success('角色修改成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: RoleRow): Promise<void> {
  if (isBuiltin(row)) {
    ElMessage.warning('内置角色不允许删除')
    return
  }
  try {
    await ElMessageBox.confirm(`删除角色「${row.name}」后不可恢复，确认删除？`, '删除角色', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await roleApi.remove(row.id)
    ElMessage.success('角色删除成功')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function normalizeMenuIds(data: unknown): Array<string | number> {
  if (Array.isArray(data)) {
    return data.map((item) =>
      typeof item === 'object' && item !== null ? ((item as { id: string | number }).id as string | number) : item
    ) as Array<string | number>
  }
  const obj = (data || {}) as { menuIds?: Array<string | number> }
  return Array.isArray(obj.menuIds) ? obj.menuIds : []
}

/** 收集叶子菜单 id：后端返回的授权集合可能含祖先节点，直接 setCheckedKeys 会误选整棵子树 */
function collectLeafIds(nodes: MenuNode[], output: Set<string> = new Set()): Set<string> {
  nodes.forEach((node) => {
    if (node.children && node.children.length) {
      collectLeafIds(node.children, output)
    } else {
      output.add(String(node.id))
    }
  })
  return output
}

async function openMenuAssign(row: RoleRow): Promise<void> {
  menuRole.value = row
  menuDrawerVisible.value = true
  menuTreeLoading.value = true
  try {
    const [tree, assigned] = await Promise.all([menuApi.tree(), roleApi.getMenus(row.id)])
    menuTreeData.value = tree || []
    const leafIds = collectLeafIds(menuTreeData.value)
    const checkedIds = normalizeMenuIds(assigned).filter((id) => leafIds.has(String(id)))
    await nextTick()
    // 只设置叶子为选中，父节点由 el-tree 自动计算半选
    menuTreeRef.value?.setCheckedKeys(checkedIds, false)
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    menuTreeLoading.value = false
  }
}

async function saveMenuAssign(): Promise<void> {
  if (!menuRole.value || !menuTreeRef.value) return
  menuSaving.value = true
  try {
    const checked = menuTreeRef.value.getCheckedKeys(false) as Array<string | number>
    const halfChecked = menuTreeRef.value.getHalfCheckedKeys() as Array<string | number>
    const menuIds = Array.from(new Set([...halfChecked, ...checked]))
    await roleApi.assignMenus(menuRole.value.id, menuIds)
    ElMessage.success(`已保存「${menuRole.value.name}」的菜单授权（共 ${menuIds.length} 个节点）`)
    menuDrawerVisible.value = false
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    menuSaving.value = false
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
          <span class="page-toolbar-heading">角色管理</span>
          <span class="page-toolbar-desc">维护角色、数据范围与菜单授权，内置角色不可删除</span>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="角色名称 / 编码"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="状态" clearable @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Plus" v-perm="'system:role:add'" @click="openCreate">新增角色</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无角色数据">
        <el-table-column prop="name" label="角色名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="code" label="角色编码" width="160" />
        <el-table-column label="数据范围" width="130">
          <template #default="{ row }">{{ dataScopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-perm="'system:role:menu'" @click="openMenuAssign(row)">
              菜单授权
            </el-button>
            <el-button link type="primary" v-perm="'system:role:edit'" @click="openEdit(row)">编辑</el-button>
            <el-tooltip :disabled="!isBuiltin(row)" content="内置角色不允许删除" placement="top">
              <span>
                <el-button
                  link
                  type="danger"
                  :disabled="isBuiltin(row)"
                  v-perm="'system:role:delete'"
                  @click="removeRow(row)"
                >
                  删除
                </el-button>
              </span>
            </el-tooltip>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="如：内控管理员" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" maxlength="40" placeholder="如：ICP_MANAGER（大写字母与下划线）" />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScope">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option
              v-for="item in DATA_SCOPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="item.disabled"
            />
          </el-select>
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

    <el-drawer v-model="menuDrawerVisible" :title="`菜单授权 - ${menuRole?.name || ''}`" size="480px" destroy-on-close>
      <div v-loading="menuTreeLoading" class="menu-tree-wrap">
        <div class="menu-tree-tip">
          勾选菜单后保存，授权立即写回角色；父子联动，父节点半选也会提交（包含祖先节点）。
        </div>
        <el-tree
          ref="menuTreeRef"
          :data="menuTreeData"
          :props="menuTreeProps"
          node-key="id"
          show-checkbox
          default-expand-all
          :check-strictly="false"
        />
      </div>
      <template #footer>
        <el-button @click="menuDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="menuSaving" @click="saveMenuAssign">保存授权</el-button>
      </template>
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
  width: 110px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.menu-tree-wrap {
  min-height: 300px;
}

.menu-tree-tip {
  padding: 8px 10px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #606a7b;
  background-color: #f6f8fc;
  border-radius: 6px;
}
</style>
