<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import { menuApi } from '@/api/system'
import type { MenuNode, MenuPayload } from '@/types/system'

const loading = ref(false)
const list = ref<MenuNode[]>([])
const treeProps = { label: 'name', children: 'children' }

const dialogVisible = ref(false)
const dialogTitle = ref('新增菜单')
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<MenuPayload>({
  parentId: null,
  name: '',
  type: 'C',
  path: '',
  icon: '',
  perms: '',
  sort: 1,
  visible: 1,
  status: 1
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
}

const typeOptions = [
  { value: 'M', label: '目录', tag: 'primary' },
  { value: 'C', label: '菜单', tag: 'success' },
  { value: 'F', label: '按钮', tag: 'warning' }
]

const parentOptions = computed(() => {
  // 编辑时不允许把节点挂到自身或子孙下（后端仍会兜底校验）
  const walk = (nodes: MenuNode[]): MenuNode[] => {
    return nodes
      .filter((node) => String(node.id) !== String(editingId.value))
      .map((node) => ({
        ...node,
        children: node.children ? walk(node.children) : undefined
      }))
  }
  return walk(list.value)
})

function typeLabel(type?: string): string {
  return typeOptions.find((item) => item.value === type)?.label || type || '-'
}

function typeTag(type?: string): 'primary' | 'success' | 'warning' | 'info' {
  return (typeOptions.find((item) => item.value === type)?.tag || 'info') as
    | 'primary'
    | 'success'
    | 'warning'
    | 'info'
}

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    list.value = (await menuApi.tree()) || []
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.parentId = null
  form.name = ''
  form.type = 'C'
  form.path = ''
  form.icon = ''
  form.perms = ''
  form.sort = 1
  form.visible = 1
  form.status = 1
  formRef.value?.clearValidate()
}

function openCreate(parent?: MenuNode): void {
  editingId.value = null
  dialogTitle.value = parent ? `新增子菜单 - ${parent.name}` : '新增菜单'
  resetForm()
  if (parent) form.parentId = parent.id
  dialogVisible.value = true
}

function openEdit(row: MenuNode): void {
  editingId.value = row.id
  dialogTitle.value = `编辑菜单 - ${row.name}`
  form.parentId = row.parentId ?? null
  form.name = row.name
  form.type = row.type || 'C'
  form.path = row.path || ''
  form.icon = row.icon || ''
  form.perms = row.perms || ''
  form.sort = row.sort ?? 1
  form.visible = row.visible ?? 1
  form.status = row.status ?? 1
  dialogVisible.value = true
}

function handleTypeChange(): void {
  if (form.type === 'F') {
    form.path = ''
    form.visible = 0
  }
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: MenuPayload = {
    parentId: form.parentId === null || form.parentId === '' ? 0 : form.parentId,
    name: form.name,
    type: form.type,
    path: form.path,
    icon: form.icon,
    perms: form.perms,
    sort: Number(form.sort) || 1,
    visible: Number(form.visible) as 0 | 1,
    status: Number(form.status) as 0 | 1
  }
  try {
    if (editingId.value === null) {
      await menuApi.create(payload)
      ElMessage.success('菜单新增成功')
    } else {
      await menuApi.update(editingId.value, payload)
      ElMessage.success('菜单修改成功')
    }
    dialogVisible.value = false
    await loadTree()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeRow(row: MenuNode): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除菜单「${row.name}」后不可恢复；若存在子菜单或角色已授权，后端将拒绝删除。是否继续？`,
      '删除菜单',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await menuApi.remove(row.id)
    ElMessage.success('菜单删除成功')
    await loadTree()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function toggleExpandAll(): void {
  // el-table 树形展开由 CSS 控制，这里通过重新加载实现「全部展开/收起」的简化交互
  void loadTree()
}

onMounted(() => {
  void loadTree()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">菜单管理</span>
          <span class="page-toolbar-desc">目录 M / 菜单 C / 按钮 F，权限标识与后端 @PreAuthorize 对齐</span>
        </div>
        <div class="page-toolbar-actions">
          <el-button :icon="RefreshRight" @click="loadTree">刷新</el-button>
          <el-button :icon="RefreshRight" @click="toggleExpandAll">重置展开</el-button>
          <el-button type="primary" :icon="Plus" v-perm="'system:menu:add'" @click="openCreate()">新增菜单</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        border
        stripe
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        empty-text="暂无菜单数据"
      >
        <el-table-column prop="name" label="菜单名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="150" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="200" show-overflow-tooltip />
        <el-table-column prop="icon" label="图标" width="120" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="显示" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small">
              {{ row.visible === 1 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.type !== 'F'"
              link
              type="primary"
              v-perm="'system:menu:add'"
              @click="openCreate(row)"
            >
              新增子项
            </el-button>
            <el-button link type="primary" v-perm="'system:menu:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-perm="'system:menu:delete'" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type" @change="handleTypeChange">
            <el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="上级节点">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="treeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="不选择则为一级节点"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="如：用户管理" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'F'" label="路由路径">
          <el-input v-model="form.path" maxlength="100" placeholder="如：system/user 或 /system/user" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'F'" label="图标">
          <el-input v-model="form.icon" maxlength="40" placeholder="Element Plus 图标名，如 Setting" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'M'" label="权限标识">
          <el-input v-model="form.perms" maxlength="80" placeholder="如：system:user:list" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="form.type !== 'F'" label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
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
</style>
