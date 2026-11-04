<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FileUpload from '@/components/FileUpload.vue'
import DeptPicker from '@/components/DeptPicker.vue'
import { usePageList } from '@/composables/usePageList'
import { docApi, docCategoryApi } from '@/api/collab'
import { useUserStore } from '@/stores/user'
import type { PageQuery } from '@/types/system'
import type {
  DocCategory,
  DocCategoryPayload,
  DocPayload,
  DocPermItem,
  DocPermRight,
  DocPermTargetType,
  DocRow
} from '@/types/collab'
import type { UploadResult } from '@/types/system'

const userStore = useUserStore()
const can = (perm: string): boolean => userStore.hasPermission(perm)

const STATUS_OPTIONS = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已发布' },
  { value: 2, label: '已归档' }
]
function statusLabel(status?: number): string {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '草稿'
}
function statusType(status?: number): 'info' | 'success' | 'warning' {
  if (status === 1) return 'success'
  if (status === 2) return 'warning'
  return 'info'
}
function formatSize(size?: number): string {
  if (!size) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

/* ---------------- 分类树 ---------------- */
const categoryTree = ref<DocCategory[]>([])
const categoryLoading = ref(false)
const selectedCategoryId = ref<DocCategory['id']>()

async function loadCategories(): Promise<void> {
  // 分类树接口需要 collab:doc-category:list；无该权限的角色（如 EMPLOYEE）跳过，避免 403 全页跳转
  if (!can('collab:doc-category:list')) {
    categoryTree.value = []
    return
  }
  categoryLoading.value = true
  try {
    categoryTree.value = await docCategoryApi.tree()
  } catch {
    categoryTree.value = []
  } finally {
    categoryLoading.value = false
  }
}

function selectCategory(id?: DocCategory['id']): void {
  selectedCategoryId.value = id
  query.categoryId = id
  doSearch()
}

function categoryLabel(node: DocCategory): string {
  return node.categoryName || node.name || '未命名分类'
}

/* ---------------- 文档列表 ---------------- */
const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<DocRow>((params) => docApi.list(params as PageQuery), {
    keyword: '',
    status: undefined,
    categoryId: undefined
  })

onMounted(async () => {
  await loadCategories()
  await load()
})

function doSearch(): void {
  query.pageNum = 1
  search()
}

/* ---------------- 上传 / 编辑文档 ---------------- */
const formVisible = ref(false)
const editingId = ref<DocRow['id']>()
const form = reactive<DocPayload & { deptNumber?: number }>({
  title: '',
  categoryId: undefined,
  fileId: undefined,
  summary: '',
  version: 'V1.0',
  deptId: undefined
})
const currentFile = ref<UploadResult | null>(null)

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, { title: '', categoryId: selectedCategoryId.value, fileId: undefined, summary: '', version: 'V1.0', deptId: undefined })
  currentFile.value = null
  formVisible.value = true
}

function openEdit(row: DocRow): void {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    categoryId: row.categoryId,
    fileId: row.fileId,
    summary: row.summary ?? '',
    version: row.version ?? 'V1.0',
    deptId: row.deptId
  })
  currentFile.value = row.fileId
    ? { id: row.fileId, originalName: row.fileName ?? '已上传附件', size: row.fileSize ?? 0 }
    : null
  formVisible.value = true
}

function onUploadSuccess(result: UploadResult): void {
  currentFile.value = result
  form.fileId = result.id
  if (!form.title) form.title = result.originalName
}

function removeUpload(): void {
  currentFile.value = null
  form.fileId = undefined
}

async function submitForm(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请输入文档标题')
    return
  }
  try {
    const payload: DocPayload = { ...form, deptId: form.deptNumber }
    if (editingId.value) {
      await docApi.update(editingId.value, payload)
      ElMessage.success('文档已更新')
    } else {
      await docApi.create(payload)
      ElMessage.success('文档已上传为草稿，发布后可在文档中心检索')
    }
    formVisible.value = false
    await load()
  } catch {
    // 已提示
  }
}

async function publishDoc(row: DocRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认发布文档「${row.title}」？发布后按文档权限开放查看。`, '发布文档', {
      confirmButtonText: '确认发布',
      cancelButtonText: '取消'
    })
    await docApi.publish(row.id)
    ElMessage.success('文档已发布')
    await load()
  } catch {
    // 取消或接口错误
  }
}

async function archiveDoc(row: DocRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认归档文档「${row.title}」？`, '归档文档', {
      confirmButtonText: '确认归档',
      cancelButtonText: '取消'
    })
    await docApi.archive(row.id)
    ElMessage.success('文档已归档')
    await load()
  } catch {
    // 取消或接口错误
  }
}

async function removeDoc(row: DocRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除文档「${row.title}」？仅草稿可删除。`, '删除文档', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await docApi.remove(row.id)
    ElMessage.success('文档已删除')
    await load()
  } catch {
    // 取消或接口错误
  }
}

async function downloadDoc(row: DocRow): Promise<void> {
  try {
    const blob = await docApi.download(row.id)
    const suffix = row.fileName && row.fileName.includes('.') ? row.fileName.slice(row.fileName.lastIndexOf('.')) : ''
    const name = `${row.title}${suffix}`
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = name
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已开始下载：${name}`)
  } catch (error) {
    const code = (error as { code?: number }).code
    if (code === 40300) {
      ElMessage.warning('无下载权限，可发起借阅后下载')
    }
    // 其余错误由拦截器提示
  }
}

/* ---------------- 文档权限（targetType + permType 原始三元组） ---------------- */
const permVisible = ref(false)
const permDoc = ref<DocRow>()
const permList = ref<DocPermItem[]>([])
const permLoading = ref(false)
const permSaving = ref(false)
const TARGET_TYPE_OPTIONS: Array<{ value: DocPermTargetType; label: string }> = [
  { value: 0, label: '全员' },
  { value: 1, label: '角色' },
  { value: 2, label: '用户' },
  { value: 3, label: '部门' }
]
const PERM_RIGHT_OPTIONS: Array<{ value: DocPermRight; label: string }> = [
  { value: 1, label: '查看' },
  { value: 2, label: '下载' },
  { value: 3, label: '编辑' }
]

function targetPlaceholder(targetType: DocPermTargetType): string {
  if (targetType === 1) return '角色 ID，如 4=EMPLOYEE'
  if (targetType === 2) return '用户 ID'
  if (targetType === 3) return '部门 ID'
  return '全部用户，无需填写'
}

async function openPerms(row: DocRow): Promise<void> {
  permDoc.value = row
  permVisible.value = true
  permLoading.value = true
  try {
    permList.value = (await docApi.getPerms(row.id)).map((item) => ({ ...item }))
  } catch {
    permList.value = []
  } finally {
    permLoading.value = false
  }
}

function addPerm(): void {
  permList.value.push({ targetType: 0, targetId: '', permType: 1 })
}

function removePerm(index: number): void {
  permList.value.splice(index, 1)
}

async function savePerms(): Promise<void> {
  if (!permDoc.value) return
  const invalid = permList.value.some((item) => item.targetType !== 0 && String(item.targetId ?? '').trim() === '')
  if (invalid) {
    ElMessage.warning('请为角色/用户/部门权限填写授权对象')
    return
  }
  permSaving.value = true
  try {
    await docApi.savePerms(permDoc.value.id, permList.value)
    ElMessage.success('文档权限已保存')
    permVisible.value = false
  } catch {
    // 已提示
  } finally {
    permSaving.value = false
  }
}

/* ---------------- 分类维护 ---------------- */
const categoryVisible = ref(false)
const categoryEditing = ref<DocCategory>()
const categoryForm = reactive<DocCategoryPayload>({ categoryName: '', parentId: undefined, sort: 10 })
const categorySaving = ref(false)

function openCategory(node?: DocCategory): void {
  categoryEditing.value = node
  Object.assign(categoryForm, {
    categoryName: node ? categoryLabel(node) : '',
    parentId: node?.parentId,
    sort: node?.sort ?? 10
  })
  categoryVisible.value = true
}

async function submitCategory(): Promise<void> {
  if (!categoryForm.categoryName.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  categorySaving.value = true
  try {
    if (categoryEditing.value) {
      await docCategoryApi.update(categoryEditing.value.id, { ...categoryForm })
      ElMessage.success('分类已更新')
    } else {
      await docCategoryApi.create({ ...categoryForm })
      ElMessage.success('分类已创建')
    }
    categoryVisible.value = false
    await loadCategories()
  } catch {
    // 已提示
  } finally {
    categorySaving.value = false
  }
}

const canManageCategory = computed(
  () => can('collab:doc-category:add') || can('collab:doc-category:edit')
)
</script>

<template>
  <div class="page-container">
    <div class="page-card page-card--split">
      <aside class="doc-category">
        <div class="doc-category__head">
          <span class="doc-category__title">文档分类</span>
          <div>
            <el-button link type="primary" size="small" @click="selectCategory(undefined)">全部</el-button>
            <el-button v-if="can('collab:doc-category:add')" link type="primary" size="small" @click="openCategory()">新增</el-button>
          </div>
        </div>
        <el-skeleton v-if="categoryLoading" :rows="5" animated />
        <el-scrollbar v-else class="doc-category__body">
          <el-tree
            :data="categoryTree"
            node-key="id"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            :props="{ label: 'categoryName', children: 'children' }"
            empty-text="暂无分类"
            @node-click="(node: DocCategory) => selectCategory(node.id)"
          >
            <template #default="{ data }">
              <span class="doc-category__node">
                <span>{{ categoryLabel(data) }}</span>
                <el-dropdown v-if="canManageCategory" trigger="click" @click.stop>
                  <span class="doc-category__more">⋯</span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click.stop="openCategory(data)">编辑分类</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </span>
            </template>
          </el-tree>
        </el-scrollbar>
      </aside>

      <section class="doc-main">
        <div class="page-toolbar">
          <div>
            <div class="page-toolbar-heading">文档中心</div>
            <div class="page-toolbar-desc">制度与协同文档的分类管理、发布归档与细粒度访问权限（全员/部门/角色/用户）</div>
          </div>
          <div class="page-toolbar-actions">
            <el-button v-if="can('collab:doc:add')" type="primary" @click="openCreate">上传文档</el-button>
          </div>
        </div>

        <div class="page-toolbar page-toolbar--filters">
          <el-select v-model="query.status" placeholder="文档状态" clearable class="toolbar-select" @change="doSearch">
            <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="文档标题 / 编号" clearable class="toolbar-input" @keyup.enter="doSearch" @clear="doSearch" />
          <el-button type="primary" plain @click="doSearch">查询</el-button>
        </div>

        <el-table v-loading="loading" :data="list" row-key="id" stripe>
          <el-table-column prop="docNo" label="文档编号" width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.docNo || row.id }}</template>
          </el-table-column>
          <el-table-column prop="title" label="文档标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.categoryName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="90" align="center">
            <template #default="{ row }">{{ row.version || 'V1.0' }}</template>
          </el-table-column>
          <el-table-column label="附件大小" width="110" align="center">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="95" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="publishTime" label="发布时间" width="170">
            <template #default="{ row }">{{ row.publishTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 0" v-perm="'collab:doc:publish'" link type="primary" @click="publishDoc(row)">发布</el-button>
              <el-button v-if="row.status === 1" v-perm="'collab:doc:archive'" link type="warning" @click="archiveDoc(row)">归档</el-button>
              <el-button v-if="row.status === 1" v-perm="'collab:doc:download'" link type="primary" @click="downloadDoc(row)">下载</el-button>
              <el-button v-perm="'collab:doc:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-perm="'collab:doc:perm'" link @click="openPerms(row)">权限</el-button>
              <el-button v-if="row.status === 0" v-perm="'collab:doc:delete'" link type="danger" @click="removeDoc(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无文档，上传制度或协同文档后发布共享" />
          </template>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </div>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑文档' : '上传文档'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="92px">
        <el-form-item label="文档标题" required>
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="如：2026 年度采购管理制度" />
        </el-form-item>
        <el-form-item label="所属分类">
          <el-tree-select
            v-model="form.categoryId"
            :data="categoryTree"
            node-key="id"
            check-strictly
            clearable
            :props="{ label: 'categoryName', children: 'children' }"
            placeholder="选择文档分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="文档版本">
          <el-input v-model="form.version" placeholder="如：V1.0" />
        </el-form-item>
        <el-form-item label="归口部门">
          <DeptPicker v-model="form.deptNumber" />
        </el-form-item>
        <el-form-item label="附件">
          <div class="upload-area">
            <el-tag v-if="currentFile" closable type="success" @close="removeUpload">
              {{ currentFile.originalName }}（{{ formatSize(currentFile.size) }}）
            </el-tag>
            <FileUpload v-else accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.zip" @success="onUploadSuccess" />
          </div>
        </el-form-item>
        <el-form-item label="文档摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="简述文档用途与适用范围" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">{{ editingId ? '保存' : '上传' }}</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="permVisible" title="文档访问权限" size="680px" destroy-on-close>
      <div class="text-muted mb-12">文档：{{ permDoc?.title }}（按目标 + 权限保存，保存后全量覆盖原有权限）</div>
      <div v-loading="permLoading">
        <el-table :data="permList" size="small" stripe>
          <el-table-column label="目标类型" width="130">
            <template #default="{ row }">
              <el-select v-model="row.targetType" size="small">
                <el-option v-for="item in TARGET_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="目标" min-width="180">
            <template #default="{ row }">
              <span v-if="row.targetType === 0" class="text-muted">全部用户</span>
              <el-input v-else v-model="row.targetId" size="small" :placeholder="targetPlaceholder(row.targetType)" />
            </template>
          </el-table-column>
          <el-table-column label="权限" width="120">
            <template #default="{ row }">
              <el-select v-model="row.permType" size="small">
                <el-option v-for="item in PERM_RIGHT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removePerm($index)">移除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="未配置权限时，仅已发布文档对审批通过的借阅人开放下载" />
          </template>
        </el-table>
      </div>
      <div class="mt-16">
        <el-button @click="addPerm">新增规则</el-button>
        <span class="text-muted ml-6">同一目标需要多个权限时，请分别新增规则（如查看 + 下载）</span>
      </div>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="savePerms">保存权限</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="categoryVisible" :title="categoryEditing ? '编辑分类' : '新增分类'" width="420px">
      <el-form :model="categoryForm" label-width="80px">
        <el-form-item label="分类名称" required>
          <el-input v-model="categoryForm.categoryName" placeholder="如：采购管理制度" />
        </el-form-item>
        <el-form-item label="上级分类">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="categoryTree"
            node-key="id"
            check-strictly
            clearable
            :props="{ label: 'categoryName', children: 'children' }"
            placeholder="留空为顶级分类"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sort" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySaving" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-card--split {
  display: flex;
  gap: 16px;
}
.doc-category {
  width: 220px;
  min-width: 220px;
  border-right: 1px solid #eef1f6;
  padding-right: 12px;
}
.doc-category__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
}
.doc-category__title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}
.doc-category__body {
  max-height: 620px;
}
.doc-category__node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 6px;
}
.doc-category__more {
  color: #909399;
}
.doc-main {
  flex: 1;
  min-width: 0;
}
.page-toolbar--filters {
  gap: 8px;
  flex-wrap: wrap;
}
.upload-area {
  width: 100%;
}
</style>
