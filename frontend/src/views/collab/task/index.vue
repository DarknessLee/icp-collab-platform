<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePageList } from '@/composables/usePageList'
import { taskApi } from '@/api/collab'
import { useUserStore } from '@/stores/user'
import { exportCsv, type CsvColumn } from '@/utils/exportCsv'
import type { PageQuery } from '@/types/system'
import type { TaskComment, TaskPayload, TaskRow } from '@/types/collab'

const userStore = useUserStore()
const can = (perm: string): boolean => userStore.hasPermission(perm)

const STATUS_OPTIONS = [
  { value: 0, label: '待处理' },
  { value: 1, label: '进行中' },
  { value: 2, label: '已完成' },
  { value: 3, label: '已取消' }
]
const PRIORITY_OPTIONS = [
  { value: 1, label: '高' },
  { value: 2, label: '中' },
  { value: 3, label: '低' }
]
const TASK_TYPE_OPTIONS = [
  { value: 1, label: '计划' },
  { value: 2, label: '任务' },
  { value: 3, label: '子任务' }
]

function statusLabel(status?: number): string {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '待处理'
}
function statusType(status?: number): 'info' | 'primary' | 'success' | 'warning' {
  if (status === 1) return 'primary'
  if (status === 2) return 'success'
  if (status === 3) return 'warning'
  return 'info'
}
function priorityLabel(priority?: number): string {
  return PRIORITY_OPTIONS.find((item) => item.value === priority)?.label ?? '中'
}
function priorityType(priority?: number): 'info' | 'warning' | 'danger' {
  if (priority === 1) return 'danger'
  if (priority === 2) return 'warning'
  return 'info'
}
function taskTypeLabel(taskType?: number): string {
  return TASK_TYPE_OPTIONS.find((item) => item.value === taskType)?.label ?? '任务'
}
function isOverdue(row: TaskRow): boolean {
  if (row.overdue) return true
  if (!row.planEnd || (row.status ?? 0) >= 2) return false
  const end = new Date(row.planEnd.replace(/-/g, '/')).getTime()
  return Number.isFinite(end) && end < Date.now()
}

const { query, list, total, loading, load, reset, handlePageChange, handleSizeChange } =
  usePageList<TaskRow>((params) => taskApi.list(params as PageQuery), {
    keyword: '',
    status: undefined,
    priority: undefined,
    overdue: undefined,
    onlyMine: undefined
  })

onMounted(load)

function loadList(): void {
  if (query.onlyMine) {
    void taskApi
      .my({ ...query, pageNum: query.pageNum, pageSize: query.pageSize } as PageQuery)
      .then((result) => {
        list.value = result.list
        total.value = result.total
      })
      .catch(() => undefined)
    return
  }
  void load()
}

function doSearch(): void {
  query.pageNum = 1
  loadList()
}

function doReset(): void {
  reset()
  loadList()
}

/* ---------------- 新建 / 编辑 ---------------- */
const formVisible = ref(false)
const formRef = ref<{ validate: () => Promise<boolean> }>()
const editingId = ref<TaskRow['id']>()
const form = reactive<TaskPayload>({
  title: '',
  description: '',
  priority: 2,
  taskType: 2,
  assigneeId: undefined,
  planStart: '',
  planEnd: '',
  remindTime: '',
  parentId: undefined
})
const formTitle = computed(() => (editingId.value ? '编辑任务' : '新建任务'))

function isoDate(offsetDays = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function openCreate(parentId?: TaskRow['id']): void {
  editingId.value = undefined
  Object.assign(form, {
    title: '',
    description: '',
    priority: 2,
    taskType: parentId ? 3 : 2,
    // 后端 TaskCreate 要求 assigneeId / planEnd 必填；默认本人 + 一周计划
    assigneeId: userStore.user?.id,
    planStart: isoDate(0),
    planEnd: isoDate(7),
    remindTime: '',
    parentId
  })
  formVisible.value = true
}

function openEdit(row: TaskRow): void {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    description: row.description ?? '',
    priority: row.priority ?? 2,
    taskType: row.taskType ?? 2,
    assigneeId: row.assigneeId,
    planStart: row.planStart ?? '',
    planEnd: row.planEnd ?? '',
    remindTime: row.remindTime ?? '',
    parentId: row.parentId
  })
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  try {
    await formRef.value?.validate()
    if (!form.assigneeId) form.assigneeId = userStore.user?.id
    if (!form.planEnd) form.planEnd = isoDate(7)
    if (form.planStart && form.planEnd && form.planStart > form.planEnd) {
      ElMessage.warning('计划结束时间不能早于计划开始时间')
      return
    }
    if (editingId.value) {
      await taskApi.update(editingId.value, { ...form })
      ElMessage.success('任务已更新')
    } else {
      await taskApi.create({ ...form })
      ElMessage.success(form.parentId ? '子任务已创建' : '任务已创建')
    }
    formVisible.value = false
    loadList()
    if (subtaskVisible.value) void loadSubtasks()
  } catch {
    // 校验或接口错误已提示
  }
}

/* ---------------- 状态流转 ---------------- */
async function startTask(row: TaskRow): Promise<void> {
  try {
    await taskApi.start(row.id)
    ElMessage.success('任务已开始')
    loadList()
  } catch {
    // 已提示
  }
}

async function completeTask(row: TaskRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认将任务「${row.title}」标记为已完成？`, '完成任务', {
      confirmButtonText: '确认完成',
      cancelButtonText: '取消'
    })
    await taskApi.complete(row.id, {})
    ElMessage.success('任务已完成')
    loadList()
  } catch {
    // 取消或接口错误
  }
}

async function cancelTask(row: TaskRow): Promise<void> {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消任务', {
      confirmButtonText: '确认取消',
      cancelButtonText: '取消',
      inputPlaceholder: '如：需求变更，暂不执行'
    })
    if (!value || !value.trim()) {
      ElMessage.warning('取消原因不能为空')
      return
    }
    await taskApi.cancel(row.id, { reason: value.trim() })
    ElMessage.success('任务已取消')
    loadList()
  } catch {
    // 取消或接口错误
  }
}

async function remindTask(row: TaskRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`向「${row.assigneeName || '负责人'}」发送任务提醒？`, '任务提醒', {
      confirmButtonText: '发送提醒',
      cancelButtonText: '取消'
    })
    await taskApi.remind(row.id, { content: `任务「${row.title}」即将到期，请及时处理` })
    ElMessage.success('提醒已发送')
  } catch {
    // 取消或接口错误
  }
}

async function removeTask(row: TaskRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除任务「${row.title}」？删除后不可恢复。`, '删除任务', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await taskApi.remove(row.id)
    ElMessage.success('任务已删除')
    loadList()
  } catch {
    // 取消或接口错误
  }
}

/* ---------------- 子任务 ---------------- */
const subtaskVisible = ref(false)
const parentTask = ref<TaskRow>()
const subtaskList = ref<TaskRow[]>([])
const subtaskLoading = ref(false)

async function openSubtasks(row: TaskRow): Promise<void> {
  parentTask.value = row
  subtaskVisible.value = true
  await loadSubtasks()
}

async function loadSubtasks(): Promise<void> {
  if (!parentTask.value) return
  subtaskLoading.value = true
  try {
    const result = await taskApi.list({ parentId: parentTask.value.id, pageNum: 1, pageSize: 100 })
    subtaskList.value = result.list
  } catch {
    subtaskList.value = []
  } finally {
    subtaskLoading.value = false
  }
}

/* ---------------- 评论 ---------------- */
const commentVisible = ref(false)
const commentTask = ref<TaskRow>()
const comments = ref<TaskComment[]>([])
const commentLoading = ref(false)
const commentText = ref('')

async function openComments(row: TaskRow): Promise<void> {
  commentTask.value = row
  commentVisible.value = true
  commentText.value = ''
  await loadComments()
}

async function loadComments(): Promise<void> {
  if (!commentTask.value) return
  commentLoading.value = true
  try {
    comments.value = await taskApi.comments(commentTask.value.id)
  } catch {
    comments.value = []
  } finally {
    commentLoading.value = false
  }
}

async function addComment(): Promise<void> {
  if (!commentTask.value) return
  const content = commentText.value.trim()
  if (!content) {
    ElMessage.warning('请输入评论内容')
    return
  }
  try {
    await taskApi.addComment(commentTask.value.id, content)
    commentText.value = ''
    ElMessage.success('评论已提交')
    await loadComments()
  } catch {
    // 已提示
  }
}

/* ---------------- 导出 ---------------- */
const exporting = ref(false)
const EXPORT_COLUMNS: CsvColumn<TaskRow>[] = [
  { title: '任务编号', get: (row) => row.taskNo ?? String(row.id) },
  { title: '任务标题', get: (row) => row.title },
  { title: '优先级', get: (row) => priorityLabel(row.priority) },
  { title: '状态', get: (row) => statusLabel(row.status) },
  { title: '负责人', get: (row) => row.assigneeName ?? '' },
  { title: '计划开始', get: (row) => row.planStart ?? '' },
  { title: '计划结束', get: (row) => row.planEnd ?? '' },
  { title: '是否逾期', get: (row) => (isOverdue(row) ? '是' : '否') },
  { title: '创建时间', get: (row) => row.createTime ?? '' }
]

async function exportTasks(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const result = await taskApi.list({ ...query, pageNum: 1, pageSize: 500 } as PageQuery)
    if (!result.list.length) {
      ElMessage.warning('当前筛选条件下暂无可导出数据')
      return
    }
    exportCsv('任务列表', result.list, EXPORT_COLUMNS)
    ElMessage.success(`已导出 ${result.list.length} 条任务`)
  } catch {
    // 已提示
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">任务管理</div>
          <div class="page-toolbar-desc">协同任务的创建、分派、进度跟踪与逾期提醒；支持子任务与评论留痕</div>
        </div>
        <div class="page-toolbar-actions">
          <el-button v-perm="'collab:task:add'" type="primary" @click="openCreate()">新建任务</el-button>
          <el-button v-perm="'collab:task:export'" :loading="exporting" @click="exportTasks">导出 CSV</el-button>
        </div>
      </div>

      <div class="page-toolbar page-toolbar--filters">
        <el-select v-model="query.status" placeholder="任务状态" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.priority" placeholder="优先级" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in PRIORITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="query.overdue" :true-value="true" :false-value="undefined" @change="doSearch">
          仅看逾期
        </el-checkbox>
        <el-checkbox v-model="query.onlyMine" :true-value="true" :false-value="undefined" @change="doSearch">
          只看我的
        </el-checkbox>
        <el-input
          v-model="query.keyword"
          placeholder="任务标题 / 编号"
          clearable
          class="toolbar-input"
          @keyup.enter="doSearch"
          @clear="doSearch"
        />
        <el-button type="primary" plain @click="doSearch">查询</el-button>
        <el-button @click="doReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column prop="taskNo" label="任务编号" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskNo || row.id }}</template>
        </el-table-column>
        <el-table-column prop="title" label="任务标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ taskTypeLabel(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assigneeName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="所属部门" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column label="计划结束" width="170">
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row) }">{{ row.planEnd || '—' }}</span>
            <el-tag v-if="isOverdue(row)" size="small" type="danger" class="ml-6">逾期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" v-perm="'collab:task:complete'" link type="primary" @click="startTask(row)">
              开始
            </el-button>
            <el-button
              v-if="row.status === 0 || row.status === 1"
              v-perm="'collab:task:complete'"
              link
              type="success"
              @click="completeTask(row)"
            >
              完成
            </el-button>
            <el-button v-perm="'collab:task:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" v-perm="'collab:task:cancel'" link type="warning" @click="cancelTask(row)">
              取消
            </el-button>
            <el-button v-perm="'collab:task:comment'" link @click="openComments(row)">评论</el-button>
            <el-button v-perm="'collab:task:remind'" link type="warning" @click="remindTask(row)">提醒</el-button>
            <el-dropdown trigger="click">
              <el-button link type="primary">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openSubtasks(row)">子任务</el-dropdown-item>
                  <el-dropdown-item v-if="can('collab:task:add')" @click="openCreate(row.id)">新增子任务</el-dropdown-item>
                  <el-dropdown-item v-if="can('collab:task:delete')" divided @click="removeTask(row)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无任务，点击「新建任务」开始协同" />
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
    </div>

    <el-dialog v-model="formVisible" :title="formTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="96px">
        <el-form-item label="任务标题" prop="title" :rules="[{ required: true, message: '请输入任务标题' }]">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：完成 2026 年度内部控制自评价" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button v-for="item in PRIORITY_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-radio-group v-model="form.taskType">
            <el-radio-button v-for="item in TASK_TYPE_OPTIONS" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="form.assigneeId" :min="1" :controls="false" placeholder="留空则默认为本人" />
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="form.planStart" type="date" value-format="YYYY-MM-DD" placeholder="选择计划开始日期" />
        </el-form-item>
        <el-form-item label="计划结束" prop="planEnd" :rules="[{ required: true, message: '请选择计划结束日期' }]">
          <el-date-picker v-model="form.planEnd" type="date" value-format="YYYY-MM-DD" placeholder="选择计划结束日期" />
        </el-form-item>
        <el-form-item label="提醒时间">
          <el-date-picker v-model="form.remindTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="到期前提醒时间（可选）" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="补充任务目标、交付物与协作说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="subtaskVisible" title="子任务" width="720px" destroy-on-close>
      <div class="mb-12 text-muted">父任务：{{ parentTask?.title }}</div>
      <el-table v-loading="subtaskLoading" :data="subtaskList" size="small" stripe>
        <el-table-column prop="title" label="子任务" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="110" />
        <el-table-column prop="planEnd" label="计划结束" width="170" />
        <template #empty>
          <el-empty description="暂无子任务" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="subtaskVisible = false">关闭</el-button>
        <el-button v-if="can('collab:task:add')" type="primary" @click="openCreate(parentTask?.id)">新增子任务</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="commentVisible" title="任务评论" size="520px" destroy-on-close>
      <div v-loading="commentLoading" class="comment-list">
        <el-empty v-if="!comments.length" description="暂无评论，补充处理说明便于协同留痕" />
        <div v-for="item in comments" :key="item.id" class="comment-item">
          <div class="comment-head">
            <span class="comment-user">{{ item.userName || '系统用户' }}</span>
            <span class="text-muted">{{ item.createTime }}</span>
          </div>
          <div class="comment-content">{{ item.content }}</div>
        </div>
      </div>
      <div class="comment-editor">
        <el-input v-model="commentText" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="记录处理进展或补充说明" />
        <el-button v-if="can('collab:task:comment')" type="primary" class="mt-12" @click="addComment">提交评论</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-toolbar--filters {
  gap: 8px;
  padding-top: 0;
  flex-wrap: wrap;
}
.text-danger {
  color: #f56c6c;
}
.ml-6 {
  margin-left: 6px;
}
.comment-list {
  min-height: 240px;
}
.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}
.comment-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.comment-user {
  font-weight: 600;
  color: #1f2d3d;
}
.comment-content {
  margin-top: 6px;
  color: #4a5568;
  line-height: 1.6;
  white-space: pre-wrap;
}
.comment-editor {
  margin-top: 16px;
}
</style>
