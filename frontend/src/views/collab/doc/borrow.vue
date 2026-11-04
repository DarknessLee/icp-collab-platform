<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePageList } from '@/composables/usePageList'
import { borrowApi, docApi } from '@/api/collab'
import { useUserStore } from '@/stores/user'
import type { PageQuery } from '@/types/system'
import type { BorrowPayload, BorrowRow, DocRow } from '@/types/collab'

const userStore = useUserStore()
function canReturn(row: BorrowRow): boolean {
  if (row.status !== 1) return false
  // 归还规则：申请本人或 ADMIN（无独立权限串）
  if (userStore.roles.includes('ADMIN')) return true
  return Boolean(userStore.user && String(row.borrowerId) === String(userStore.user.id))
}

const STATUS_OPTIONS = [
  { value: 0, label: '申请中' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已归还' }
]
function statusLabel(status?: number): string {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '申请中'
}
function statusType(status?: number): 'warning' | 'success' | 'danger' | 'info' {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  if (status === 3) return 'info'
  return 'warning'
}

const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<BorrowRow>((params) => borrowApi.list(params as PageQuery), {
    keyword: '',
    status: undefined
  })

onMounted(load)

function doSearch(): void {
  query.pageNum = 1
  search()
}

/* ---------------- 借阅申请 ---------------- */
const formVisible = ref(false)
const form = reactive<BorrowPayload & { range?: [string, string] }>({
  docId: '',
  reason: '',
  startDate: '',
  endDate: ''
})
const docOptions = ref<DocRow[]>([])
const docLoading = ref(false)

async function openCreate(): Promise<void> {
  Object.assign(form, { docId: '', reason: '', startDate: '', endDate: '', range: undefined })
  formVisible.value = true
  docLoading.value = true
  try {
    const result = await docApi.list({ pageNum: 1, pageSize: 200, status: 1 })
    docOptions.value = result.list
  } catch {
    docOptions.value = []
  } finally {
    docLoading.value = false
  }
}

async function submitForm(): Promise<void> {
  if (!form.docId) {
    ElMessage.warning('请选择需要借阅的文档')
    return
  }
  if (!form.reason.trim()) {
    ElMessage.warning('请填写借阅事由')
    return
  }
  try {
    await borrowApi.create({
      docId: form.docId,
      reason: form.reason.trim(),
      startDate: form.range?.[0] ?? form.startDate,
      endDate: form.range?.[1] ?? form.endDate
    })
    ElMessage.success('借阅申请已提交审批；已生成待确认的发起任务，请在「我的待办」中确认提交')
    formVisible.value = false
    await load()
  } catch {
    // 已提示
  }
}

/* ---------------- 详情 / 下载 ---------------- */
const detailVisible = ref(false)
const detail = ref<BorrowRow>()

function openDetail(row: BorrowRow): void {
  detail.value = row
  detailVisible.value = true
}

async function downloadDoc(row: BorrowRow): Promise<void> {
  try {
    const blob = await docApi.download(row.docId)
    const name = row.docName || `借阅文档-${row.docId}`
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
      ElMessage.warning('无下载权限，请确认借阅审批已通过或联系文档管理员')
    }
    // 其余由拦截器提示
  }
}

async function returnBorrow(row: BorrowRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认归还文档「${row.docName || row.docId}」？`, '归还文档', {
      confirmButtonText: '确认归还',
      cancelButtonText: '取消'
    })
    await borrowApi.returnDoc(row.id)
    ElMessage.success('文档已归还')
    await load()
  } catch {
    // 取消或接口错误
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">文档借阅</div>
          <div class="page-toolbar-desc">按文档权限发起借阅申请、跟踪审批状态；审批通过后可下载文档</div>
        </div>
        <div class="page-toolbar-actions">
          <el-button v-perm="'collab:borrow:add'" type="primary" @click="openCreate">发起借阅</el-button>
        </div>
      </div>

      <div class="page-toolbar page-toolbar--filters">
        <el-select v-model="query.status" placeholder="借阅状态" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="借阅单号 / 文档名称" clearable class="toolbar-input" @keyup.enter="doSearch" @clear="doSearch" />
        <el-button type="primary" plain @click="doSearch">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column prop="borrowNo" label="借阅单号" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.borrowNo || row.id }}</template>
        </el-table-column>
        <el-table-column prop="docName" label="文档名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.docName || `文档 ${row.docId}` }}</template>
        </el-table-column>
        <el-table-column prop="borrowerName" label="借阅人" width="110">
          <template #default="{ row }">{{ row.borrowerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="借阅事由" min-width="200" show-overflow-tooltip />
        <el-table-column label="借阅期间" width="210">
          <template #default="{ row }">{{ row.startDate || '—' }} 至 {{ row.endDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1" v-perm="'collab:doc:download'" link type="success" @click="downloadDoc(row)">下载</el-button>
            <el-button v-if="canReturn(row)" link type="warning" @click="returnBorrow(row)">归还</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无借阅申请，选择已发布文档发起借阅" />
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

    <el-dialog v-model="formVisible" title="发起文档借阅" width="560px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item label="借阅文档" required>
          <el-select
            v-model="form.docId"
            filterable
            clearable
            :loading="docLoading"
            placeholder="选择已发布文档"
            style="width: 100%"
          >
            <el-option
              v-for="item in docOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            >
              <span>{{ item.title }}</span>
              <span class="text-muted option-meta">{{ item.categoryName || '未分类' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="借阅期间">
          <el-date-picker
            v-model="form.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="借阅事由" required>
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="说明借阅用途，便于审批人判断" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">提交申请</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="借阅详情" size="480px" destroy-on-close>
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="借阅单号">{{ detail.borrowNo || detail.id }}</el-descriptions-item>
        <el-descriptions-item label="文档">{{ detail.docName || `文档 ${detail.docId}` }}</el-descriptions-item>
        <el-descriptions-item label="借阅人">{{ detail.borrowerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="借阅事由">{{ detail.reason || '—' }}</el-descriptions-item>
        <el-descriptions-item label="借阅期间">{{ detail.startDate || '—' }} 至 {{ detail.endDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审批状态">
          <el-tag size="small" :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="流程实例">
          {{ detail.processInstanceId || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ detail.createTime || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="text-muted mt-16">
        借阅审批通过后，若文档权限未直接授权，将以「审批通过的借阅单」作为下载兜底依据。
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-toolbar--filters {
  gap: 8px;
  flex-wrap: wrap;
}
.option-meta {
  float: right;
  font-size: 12px;
  margin-left: 12px;
}
</style>
