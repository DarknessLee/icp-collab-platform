<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePageList } from '@/composables/usePageList'
import { noticeApi } from '@/api/collab'
import { useUserStore } from '@/stores/user'
import type { PageQuery } from '@/types/system'
import type { NoticePayload, NoticeRow } from '@/types/collab'

const userStore = useUserStore()
const can = (perm: string): boolean => userStore.hasPermission(perm)

const STATUS_OPTIONS = [
  { value: 0, label: '草稿' },
  { value: 1, label: '已发布' },
  { value: 2, label: '已关闭' }
]
const SCOPE_OPTIONS = [
  { value: 1, label: '全员' },
  { value: 2, label: '部门' },
  { value: 3, label: '角色' }
]
const NOTICE_TYPE_OPTIONS = [
  { value: 1, label: '通知' },
  { value: 2, label: '公告' },
  { value: 3, label: '制度宣贯' }
]
function statusLabel(status?: number): string {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '草稿'
}
function statusType(status?: number): 'info' | 'success' | 'warning' {
  if (status === 1) return 'success'
  if (status === 2) return 'warning'
  return 'info'
}
function noticeTypeLabel(type?: number): string {
  return NOTICE_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? '通知'
}

const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<NoticeRow>((params) => noticeApi.list(params as PageQuery), {
    keyword: '',
    status: undefined,
    unreadOnly: undefined
  })

const unreadCount = ref(0)

async function refreshUnread(): Promise<void> {
  try {
    unreadCount.value = await noticeApi.unreadCount()
  } catch {
    unreadCount.value = 0
  }
}

onMounted(() => {
  void load()
  void refreshUnread()
})

function doSearch(): void {
  query.pageNum = 1
  search()
}

/* ---------------- 新建 / 编辑 ---------------- */
const formVisible = ref(false)
const editingId = ref<NoticeRow['id']>()
const form = reactive<NoticePayload>({
  title: '',
  content: '',
  noticeType: 1,
  scope: 1,
  scopeDeptIds: [],
  scopeRoles: []
})
const scopeDeptIdsText = ref('')
const scopeRolesText = ref('')

function splitIds(text: string): Array<string | number> {
  return text
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, { title: '', content: '', noticeType: 1, scope: 1, scopeDeptIds: [], scopeRoles: [] })
  scopeDeptIdsText.value = ''
  scopeRolesText.value = ''
  formVisible.value = true
}

function openEdit(row: NoticeRow): void {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    content: row.content ?? '',
    noticeType: row.noticeType ?? 1,
    scope: row.scope ?? 1,
    scopeDeptIds: [],
    scopeRoles: []
  })
  scopeDeptIdsText.value = (row.scopeDeptIds ?? []).join(',')
  scopeRolesText.value = (row.scopeRoles ?? []).join(',')
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  if (form.scope === 2 && !scopeDeptIdsText.value.trim()) {
    ElMessage.warning('请填写部门 ID（多个以逗号分隔）')
    return
  }
  if (form.scope === 3 && !scopeRolesText.value.trim()) {
    ElMessage.warning('请填写角色编码（多个以逗号分隔）')
    return
  }
  const payload: NoticePayload = {
    title: form.title.trim(),
    content: form.content,
    noticeType: form.noticeType,
    scope: form.scope
  }
  if (form.scope === 2) payload.scopeDeptIds = splitIds(scopeDeptIdsText.value)
  if (form.scope === 3) payload.scopeRoles = splitIds(scopeRolesText.value).map((item) => String(item))
  try {
    if (editingId.value) {
      await noticeApi.update(editingId.value, payload)
      ElMessage.success('公告已更新')
    } else {
      await noticeApi.create(payload)
      ElMessage.success('公告已保存为草稿，发布后可见')
    }
    formVisible.value = false
    await load()
  } catch {
    // 已提示
  }
}

/* ---------------- 状态与已读 ---------------- */
async function publishNotice(row: NoticeRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认发布公告「${row.title}」？`, '发布公告', {
      confirmButtonText: '确认发布',
      cancelButtonText: '取消'
    })
    await noticeApi.publish(row.id)
    ElMessage.success('公告已发布')
    await load()
  } catch {
    // 取消或接口错误
  }
}

async function closeNotice(row: NoticeRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认关闭公告「${row.title}」？`, '关闭公告', {
      confirmButtonText: '确认关闭',
      cancelButtonText: '取消'
    })
    await noticeApi.close(row.id)
    ElMessage.success('公告已关闭')
    await load()
  } catch {
    // 取消或接口错误
  }
}

async function removeNotice(row: NoticeRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '删除公告', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await noticeApi.remove(row.id)
    ElMessage.success('公告已删除')
    await load()
  } catch {
    // 取消或接口错误
  }
}

const detailVisible = ref(false)
const detail = ref<NoticeRow>()

async function openDetail(row: NoticeRow): Promise<void> {
  detail.value = row
  detailVisible.value = true
  if (row.status === 1 && !row.read) {
    try {
      await noticeApi.read(row.id)
      row.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // 已读失败不阻断阅读
    }
  }
}

function scopeText(row: NoticeRow): string {
  if (row.scope === 1 || !row.scope) return '全员'
  if (row.scope === 2) {
    const depts = (row.scopeDeptIds ?? []).join(',')
    return `部门：${depts || '—'}`
  }
  const roles = (row.scopeRoles ?? []).join(',')
  return `角色：${roles || '—'}`
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">公告通知</div>
          <div class="page-toolbar-desc">按全员/部门/角色范围发布公告，已读状态幂等记录</div>
        </div>
        <div class="page-toolbar-actions">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="unread-badge">
            <el-tag type="warning" effect="plain">未读公告</el-tag>
          </el-badge>
          <el-button v-if="can('collab:notice:add')" type="primary" @click="openCreate">新建公告</el-button>
        </div>
      </div>

      <div class="page-toolbar page-toolbar--filters">
        <el-select v-model="query.status" placeholder="公告状态" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-checkbox v-model="query.unreadOnly" :true-value="true" :false-value="undefined" @change="doSearch">
          仅看未读
        </el-checkbox>
        <el-input v-model="query.keyword" placeholder="公告标题" clearable class="toolbar-input" @keyup.enter="doSearch" @clear="doSearch" />
        <el-button type="primary" plain @click="doSearch">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column prop="title" label="公告标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="notice-title" :class="{ 'notice-title--unread': row.status === 1 && !row.read }" @click="openDetail(row)">
              {{ row.title }}
            </span>
            <el-tag v-if="row.status === 1 && !row.read" size="small" type="danger" class="ml-6">未读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="95" align="center">
          <template #default="{ row }">{{ noticeTypeLabel(row.noticeType) }}</template>
        </el-table-column>
        <el-table-column label="发布范围" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="110">
          <template #default="{ row }">{{ row.publisherName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template #default="{ row }">{{ row.publishTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 0" v-perm="'collab:notice:publish'" link type="success" @click="publishNotice(row)">发布</el-button>
            <el-button v-if="row.status === 1" v-perm="'collab:notice:close'" link type="warning" @click="closeNotice(row)">关闭</el-button>
            <el-button v-if="row.status === 0" v-perm="'collab:notice:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" v-perm="'collab:notice:delete'" link type="danger" @click="removeNotice(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无公告" />
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

    <el-dialog v-model="formVisible" :title="editingId ? '编辑公告' : '新建公告'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="92px">
        <el-form-item label="公告标题" required>
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="如：关于开展 2026 年度内部控制自评价的通知" />
        </el-form-item>
        <el-form-item label="公告类型">
          <el-radio-group v-model="form.noticeType">
            <el-radio v-for="item in NOTICE_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发布范围">
          <el-radio-group v-model="form.scope">
            <el-radio v-for="item in SCOPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scope === 2" label="部门 ID">
          <el-input v-model="scopeDeptIdsText" placeholder="多个部门以逗号分隔，如：2,3" />
        </el-form-item>
        <el-form-item v-if="form.scope === 3" label="角色编码">
          <el-input v-model="scopeRolesText" placeholder="多个角色以逗号分隔，如：EMPLOYEE,AUDITOR" />
        </el-form-item>
        <el-form-item label="公告内容" required>
          <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="填写公告正文，发布后按范围推送并计入未读数" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="公告详情" size="560px" destroy-on-close>
      <template v-if="detail">
        <h3 class="notice-detail__title">{{ detail.title }}</h3>
        <div class="notice-detail__meta text-muted">
          {{ detail.publisherName || '系统' }} · {{ detail.publishTime || detail.createTime || '—' }} · {{ scopeText(detail) }}
        </div>
        <div class="notice-detail__content">{{ detail.content || '（无正文）' }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-toolbar--filters {
  gap: 8px;
  flex-wrap: wrap;
}
.unread-badge {
  margin-right: 8px;
}
.notice-title {
  cursor: pointer;
  color: #2f6fed;
}
.notice-title--unread {
  font-weight: 600;
}
.ml-6 {
  margin-left: 6px;
}
.notice-detail__title {
  margin: 0 0 8px;
  font-size: 18px;
  color: #1f2d3d;
}
.notice-detail__meta {
  font-size: 13px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1f6;
}
.notice-detail__content {
  margin-top: 16px;
  line-height: 1.8;
  color: #334155;
  white-space: pre-wrap;
}
</style>
