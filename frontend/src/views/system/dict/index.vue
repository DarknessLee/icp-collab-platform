<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { dictApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import type { DictItemPayload, DictItemRow, DictTypeRow } from '@/types/system'

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<DictTypeRow>(dictApi.listTypes, { keyword: '', status: '' })

const selectedType = ref<DictTypeRow | null>(null)
const items = ref<DictItemRow[]>([])
const itemsLoading = ref(false)
const itemKeyword = ref('')

const typeDialogVisible = ref(false)
const typeDialogTitle = ref('新增字典类型')
const editingTypeId = ref<string | number | null>(null)
const typeFormRef = ref<FormInstance>()
const typeForm = reactive({ name: '', type: '', status: 1, remark: '' })
const typeRules: FormRules = {
  name: [{ required: true, message: '请输入字典类型名称', trigger: 'blur' }],
  type: [{ required: true, message: '请输入字典类型编码', trigger: 'blur' }]
}

const itemDialogVisible = ref(false)
const itemDialogTitle = ref('新增字典项')
const editingItemId = ref<string | number | null>(null)
const itemFormRef = ref<FormInstance>()
const itemForm = reactive<DictItemPayload>({
  typeId: undefined,
  type: '',
  label: '',
  value: '',
  sort: 1,
  tagType: 'primary',
  status: 1,
  remark: ''
})
const itemRules: FormRules = {
  label: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  value: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
}

const tagTypeOptions = [
  { value: 'primary', label: '蓝色 primary' },
  { value: 'success', label: '绿色 success' },
  { value: 'warning', label: '橙色 warning' },
  { value: 'danger', label: '红色 danger' },
  { value: 'info', label: '灰色 info' }
]

function filteredItems(): DictItemRow[] {
  const keyword = itemKeyword.value.trim().toLowerCase()
  if (!keyword) return items.value
  return items.value.filter(
    (item) =>
      item.label.toLowerCase().includes(keyword) || String(item.value).toLowerCase().includes(keyword)
  )
}

async function loadItems(type?: DictTypeRow | null): Promise<void> {
  if (!type) {
    items.value = []
    return
  }
  itemsLoading.value = true
  try {
    const page = await dictApi.listItems(type.type, { pageNum: 1, pageSize: 200 })
    items.value = page.list
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    itemsLoading.value = false
  }
}

async function selectType(row: DictTypeRow): Promise<void> {
  selectedType.value = row
  itemKeyword.value = ''
  await loadItems(row)
}

function openTypeCreate(): void {
  editingTypeId.value = null
  typeDialogTitle.value = '新增字典类型'
  typeForm.name = ''
  typeForm.type = ''
  typeForm.status = 1
  typeForm.remark = ''
  typeDialogVisible.value = true
}

function openTypeEdit(row: DictTypeRow): void {
  editingTypeId.value = row.id
  typeDialogTitle.value = `编辑字典类型 - ${row.name}`
  typeForm.name = row.name
  typeForm.type = row.type
  typeForm.status = row.status ?? 1
  typeForm.remark = row.remark || ''
  typeDialogVisible.value = true
}

async function submitType(): Promise<void> {
  if (!typeFormRef.value) return
  const valid = await typeFormRef.value.validate().catch(() => false)
  if (!valid) return
  const payload = {
    name: typeForm.name,
    type: typeForm.type,
    status: Number(typeForm.status) as 0 | 1,
    remark: typeForm.remark
  }
  try {
    if (editingTypeId.value === null) {
      await dictApi.createType(payload)
      ElMessage.success('字典类型新增成功')
    } else {
      await dictApi.updateType(editingTypeId.value, payload)
      ElMessage.success('字典类型修改成功')
    }
    typeDialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

function openItemCreate(): void {
  if (!selectedType.value) {
    ElMessage.warning('请先在左侧选择字典类型')
    return
  }
  editingItemId.value = null
  itemDialogTitle.value = `新增字典项 - ${selectedType.value.name}`
  itemForm.typeId = selectedType.value.id
  itemForm.type = selectedType.value.type
  itemForm.label = ''
  itemForm.value = ''
  itemForm.sort = 1
  itemForm.tagType = 'primary'
  itemForm.status = 1
  itemForm.remark = ''
  itemDialogVisible.value = true
}

function openItemEdit(item: DictItemRow): void {
  if (!selectedType.value) return
  editingItemId.value = item.id
  itemDialogTitle.value = `编辑字典项 - ${item.label}`
  itemForm.typeId = item.typeId ?? selectedType.value.id
  itemForm.type = item.type ?? selectedType.value.type
  itemForm.label = item.label
  itemForm.value = item.value
  itemForm.sort = item.sort ?? 1
  itemForm.tagType = item.tagType || 'primary'
  itemForm.status = item.status ?? 1
  itemForm.remark = item.remark || ''
  itemDialogVisible.value = true
}

async function submitItem(): Promise<void> {
  if (!itemFormRef.value) return
  const valid = await itemFormRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: DictItemPayload = {
    typeId: itemForm.typeId,
    type: itemForm.type,
    label: itemForm.label,
    value: itemForm.value,
    sort: Number(itemForm.sort) || 1,
    tagType: itemForm.tagType,
    status: Number(itemForm.status) as 0 | 1,
    remark: itemForm.remark
  }
  try {
    if (editingItemId.value === null) {
      await dictApi.createItem(payload)
      ElMessage.success('字典项新增成功')
    } else {
      await dictApi.updateItem(editingItemId.value, payload)
      ElMessage.success('字典项修改成功')
    }
    itemDialogVisible.value = false
    await loadItems(selectedType.value)
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function removeItem(item: DictItemRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除字典项「${item.label}」后不可恢复，确认删除？`, '删除字典项', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await dictApi.removeItem(item.id)
    ElMessage.success('字典项删除成功')
    await loadItems(selectedType.value)
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

onMounted(async () => {
  await load()
  if (list.value.length && !selectedType.value) {
    await selectType(list.value[0])
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-card dict-layout">
      <section class="dict-types">
        <div class="panel-header">
          <span class="panel-title">字典类型</span>
          <div class="panel-actions">
            <el-input
              v-model="query.keyword"
              class="toolbar-input"
              placeholder="类型名称 / 编码"
              clearable
              @keyup.enter="search"
              @clear="search"
            />
            <el-button type="primary" :icon="Plus" v-perm="'system:dict:add'" @click="openTypeCreate">新增类型</el-button>
          </div>
        </div>
        <el-table
          v-loading="loading"
          :data="list"
          border
          stripe
          row-key="id"
          highlight-current-row
          empty-text="暂无字典类型"
          @current-change="(row: DictTypeRow) => selectType(row)"
        >
          <el-table-column prop="name" label="类型名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="type" label="类型编码" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" v-perm="'system:dict:edit'" @click.stop="openTypeEdit(row)">
                编辑
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
            layout="total, prev, pager, next"
            background
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
          <el-button text @click="reset">重置</el-button>
        </div>
      </section>

      <section class="dict-items">
        <div class="panel-header">
          <span class="panel-title">
            字典项
            <span class="panel-sub">{{ selectedType ? `· ${selectedType.name}（${selectedType.type}）` : '' }}</span>
          </span>
          <div class="panel-actions">
            <el-input v-model="itemKeyword" class="toolbar-input" placeholder="标签 / 值" clearable />
            <el-button
              type="primary"
              :icon="Plus"
              :disabled="!selectedType"
              v-perm="'system:dict:add'"
              @click="openItemCreate"
            >
              新增字典项
            </el-button>
          </div>
        </div>
        <el-empty v-if="!selectedType" description="请先在左侧选择字典类型" />
        <template v-else>
          <el-table
            v-loading="itemsLoading"
            :data="filteredItems()"
            border
            stripe
            row-key="id"
            empty-text="该类型下暂无字典项"
          >
            <el-table-column prop="label" label="标签" min-width="130">
              <template #default="{ row }">
                <el-tag :type="row.tagType || 'primary'" size="small">{{ row.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="value" label="值" min-width="120" />
            <el-table-column prop="sort" label="排序" width="80" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" v-perm="'system:dict:edit'" @click="openItemEdit(row)">编辑</el-button>
                <el-button link type="danger" v-perm="'system:dict:delete'" @click="removeItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </section>
    </div>

    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="480px" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="100px">
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="typeForm.name" maxlength="50" placeholder="如：启用状态" />
        </el-form-item>
        <el-form-item label="类型编码" prop="type">
          <el-input v-model="typeForm.type" maxlength="60" placeholder="如：sys_status" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="480px" destroy-on-close>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="字典标签" prop="label">
          <el-input v-model="itemForm.label" maxlength="50" placeholder="如：启用" />
        </el-form-item>
        <el-form-item label="字典值" prop="value">
          <el-input v-model="itemForm.value" maxlength="50" placeholder="如：1" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sort" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="itemForm.tagType" style="width: 100%">
            <el-option v-for="option in tagTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dict-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 16px;
}

.dict-types,
.dict-items {
  flex: 1;
  min-width: 0;
}

.dict-types {
  padding-right: 16px;
  border-right: 1px solid #eef1f6;
}

.panel-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.panel-sub {
  font-size: 12px;
  font-weight: 400;
  color: #8a94a6;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.toolbar-input {
  width: 190px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

@media (max-width: 1100px) {
  .dict-layout {
    flex-direction: column;
  }

  .dict-types {
    width: 100%;
    padding-right: 0;
    border-right: none;
  }
}
</style>
