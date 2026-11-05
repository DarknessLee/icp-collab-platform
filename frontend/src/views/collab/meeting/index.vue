<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePageList } from '@/composables/usePageList'
import { meetingApi } from '@/api/collab'
import { useUserStore } from '@/stores/user'
import type { MeetingPayload, MeetingRow } from '@/types/collab'

const userStore = useUserStore()
const can = (perm: string): boolean => userStore.hasPermission(perm)

/** 会议类型：与后端字典 icp_meeting_type 一致 */
const TYPE_OPTIONS = [
  { value: 0, label: '周例会' },
  { value: 1, label: '专题会' },
  { value: 2, label: '评审会' },
  { value: 3, label: '培训会' }
]
const STATUS_OPTIONS = [
  { value: 0, label: '待召开', tag: 'info' as const },
  { value: 1, label: '已召开', tag: 'success' as const },
  { value: 2, label: '已取消', tag: 'danger' as const }
]

function typeLabel(type?: number): string {
  return TYPE_OPTIONS.find((item) => item.value === type)?.label ?? '—'
}
function statusLabel(status?: number): string {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '待召开'
}
function statusTag(status?: number): 'info' | 'success' | 'danger' {
  return STATUS_OPTIONS.find((item) => item.value === status)?.tag ?? 'info'
}

/** 日期范围筛选：独立于 usePageList 的 query，避免 reset 丢字段 */
const dateRange = ref<[string, string] | []>([])

function rangeParams(): Record<string, string> {
  if (dateRange.value.length !== 2) return {}
  return { dateFrom: dateRange.value[0], dateTo: dateRange.value[1] }
}

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<MeetingRow>(
    (params) => meetingApi.list({ ...params, ...rangeParams() }),
    { keyword: '', meetingType: undefined, status: undefined }
  )

onMounted(() => {
  void load()
})

function doSearch(): void {
  query.pageNum = 1
  search()
}

function doReset(): void {
  dateRange.value = []
  reset()
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<MeetingRow | null>(null)

async function openDetail(row: MeetingRow): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = row
  try {
    detail.value = await meetingApi.detail(row.id)
  } catch {
    // 详情失败保留列表行数据，错误已由请求层提示
  } finally {
    detailLoading.value = false
  }
}

/* ---------------- 新建 / 编辑 ---------------- */
const formVisible = ref(false)
const editingId = ref<MeetingRow['id']>()
const form = reactive<MeetingPayload>({
  title: '',
  meetingType: 0,
  startTime: '',
  endTime: '',
  location: '',
  attendeeNames: '',
  remark: ''
})

function resetForm(): void {
  Object.assign(form, {
    title: '',
    meetingType: 0,
    startTime: '',
    endTime: '',
    location: '',
    attendeeNames: '',
    remark: ''
  })
}

function openCreate(): void {
  editingId.value = undefined
  resetForm()
  formVisible.value = true
}

function openEdit(row: MeetingRow): void {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    meetingType: row.meetingType ?? 0,
    startTime: row.startTime ?? '',
    endTime: row.endTime ?? '',
    location: row.location ?? '',
    attendeeNames: row.attendeeNames ?? '',
    remark: row.remark ?? ''
  })
  formVisible.value = true
}

function validate(): boolean {
  if (!form.title.trim()) {
    ElMessage.warning('请输入会议主题')
    return false
  }
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请选择会议起止时间')
    return false
  }
  if (form.endTime <= form.startTime) {
    ElMessage.warning('结束时间需晚于开始时间')
    return false
  }
  return true
}

async function submitForm(): Promise<void> {
  if (!validate()) return
  const payload: MeetingPayload = {
    title: form.title.trim(),
    meetingType: String(form.meetingType),
    startTime: form.startTime,
    endTime: form.endTime,
    location: form.location?.trim() || undefined,
    attendeeNames: form.attendeeNames?.trim() || undefined,
    remark: form.remark?.trim() || undefined
  }
  try {
    if (editingId.value) {
      await meetingApi.update(editingId.value, payload)
      ElMessage.success('会议已更新')
    } else {
      await meetingApi.create(payload)
      ElMessage.success('会议已创建')
    }
    formVisible.value = false
    await load()
  } catch {
    // 错误已由请求层提示
  }
}

/* ---------------- 写纪要 ---------------- */
const minutesVisible = ref(false)
const minutesTarget = ref<MeetingRow | null>(null)
const minutesText = ref('')
const minutesSaving = ref(false)

function openMinutes(row: MeetingRow): void {
  minutesTarget.value = row
  minutesText.value = row.minutes ?? ''
  minutesVisible.value = true
}

async function saveMinutes(): Promise<void> {
  if (!minutesTarget.value) return
  if (!minutesText.value.trim()) {
    ElMessage.warning('请输入会议纪要')
    return
  }
  minutesSaving.value = true
  try {
    await meetingApi.saveMinutes(minutesTarget.value.id, minutesText.value.trim())
    ElMessage.success('会议纪要已保存')
    minutesVisible.value = false
    if (detail.value && detail.value.id === minutesTarget.value.id) {
      detail.value = { ...detail.value, minutes: minutesText.value.trim() }
    }
    await load()
  } catch {
    // 错误已由请求层提示
  } finally {
    minutesSaving.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function removeMeeting(row: MeetingRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除会议「${row.title}」？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await meetingApi.remove(row.id)
    ElMessage.success('会议已删除')
    if (detail.value?.id === row.id) detailVisible.value = false
    await load()
  } catch {
    // 错误已由请求层提示
  }
}

const detailMinutes = computed(() => (detail.value?.minutes || '').trim())
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">会议纪要</div>
          <div class="page-toolbar-desc">会议排期、参会人记录与纪要在线上归档，支持按类型/状态/日期检索</div>
        </div>
        <div class="page-toolbar-actions">
          <el-button v-if="can('collab:meeting:add')" type="primary" @click="openCreate">+ 新建会议</el-button>
        </div>
      </div>

      <div class="page-toolbar page-toolbar--filters">
        <el-select v-model="query.meetingType" placeholder="会议类型" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="会议状态" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="toolbar-date"
          @change="doSearch"
        />
        <el-input
          v-model="query.keyword"
          placeholder="会议主题 / 地点"
          clearable
          class="toolbar-input"
          @keyup.enter="doSearch"
          @clear="doSearch"
        />
        <el-button type="primary" plain @click="doSearch">查询</el-button>
        <el-button @click="doReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column prop="meetingNo" label="会议编号" width="118">
          <template #default="{ row }">{{ row.meetingNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="title" label="会议主题" min-width="190">
          <template #default="{ row }">
            <span class="meeting-title" @click="openDetail(row)">{{ row.title }}</span>
            <div class="meeting-place">{{ row.location || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="72" align="center">
          <template #default="{ row }">{{ typeLabel(row.meetingType) }}</template>
        </el-table-column>
        <el-table-column label="会议时间" width="160">
          <template #default="{ row }">
            <div>{{ (row.startTime || '').slice(0, 10) || '—' }}</div>
            <div class="meeting-time">
              {{ (row.startTime || '').slice(11, 16) }}<template v-if="row.endTime"> ~ {{ row.endTime.slice(11, 16) }}</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="organizerName" label="组织者" width="84">
          <template #default="{ row }">{{ row.organizerName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.deptName || '—' }}</template>
        </el-table-column>
        <el-table-column label="参会人数" width="84" align="center">
          <template #default="{ row }">{{ row.attendeeCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="76" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="216" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'collab:meeting:minutes'" link type="success" @click="openMinutes(row)">写纪要</el-button>
            <el-button v-perm="'collab:meeting:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'collab:meeting:remove'" link type="danger" @click="removeMeeting(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无会议" />
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

    <!-- 详情 -->
    <el-drawer v-model="detailVisible" title="会议详情" size="620px" destroy-on-close>
      <div v-loading="detailLoading">
        <template v-if="detail">
          <h3 class="meeting-detail__title">{{ detail.title }}</h3>
          <div class="meeting-detail__meta text-muted">
            {{ detail.meetingNo || '—' }} · {{ typeLabel(detail.meetingType) }} ·
            <el-tag size="small" :type="statusTag(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </div>
          <el-descriptions :column="2" border class="meeting-desc">
            <el-descriptions-item label="会议时间" :span="2">
              {{ detail.startTime || '—' }}<template v-if="detail.endTime"> ~ {{ detail.endTime }}</template>
            </el-descriptions-item>
            <el-descriptions-item label="地点">{{ detail.location || '—' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ detail.deptName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="组织者">{{ detail.organizerName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="参会人数">{{ detail.attendeeCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="参会人" :span="2">{{ detail.attendeeNames || '—' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div class="meeting-minutes">
            <div class="meeting-minutes__header">
              <span class="meeting-minutes__label">会议纪要</span>
              <el-button v-if="can('collab:meeting:minutes')" link type="primary" @click="openMinutes(detail!)">
                写纪要
              </el-button>
            </div>
            <pre v-if="detailMinutes" class="meeting-minutes__content">{{ detail.minutes }}</pre>
            <el-empty v-else description="暂无纪要" :image-size="72" />
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑会议' : '新建会议'" width="620px" destroy-on-close>
      <el-form :model="form" label-width="92px">
        <el-form-item label="会议主题" required>
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="如：2026 年第三季度内部控制评价推进会" />
        </el-form-item>
        <el-form-item label="会议类型">
          <el-select v-model="form.meetingType" class="w-full">
            <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择开始时间"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择结束时间"
            class="w-full"
          />
        </el-form-item>
        <el-form-item label="会议地点">
          <el-input v-model="form.location" maxlength="120" placeholder="如：总部 3F 第一会议室 / 线上腾讯会议" />
        </el-form-item>
        <el-form-item label="参会人">
          <el-input v-model="form.attendeeNames" maxlength="500" placeholder="多人以逗号分隔，如：李静,王敏,张伟" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="会议背景、议题范围等（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 写纪要 -->
    <el-dialog v-model="minutesVisible" title="会议纪要" width="680px" destroy-on-close>
      <div class="meeting-minutes__tip text-muted">{{ minutesTarget?.title }}</div>
      <el-input
        v-model="minutesText"
        type="textarea"
        :rows="12"
        maxlength="5000"
        show-word-limit
        placeholder="记录会议讨论要点、决议事项、责任人与完成时限"
      />
      <template #footer>
        <el-button @click="minutesVisible = false">取消</el-button>
        <el-button type="primary" :loading="minutesSaving" @click="saveMinutes">保存纪要</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-toolbar--filters {
  gap: 8px;
  flex-wrap: wrap;
}
.toolbar-date {
  max-width: 260px;
}
.meeting-title {
  cursor: pointer;
  color: #2f6fed;
}
.meeting-place,
.meeting-time {
  margin-top: 2px;
  font-size: 12px;
  color: #8a94a6;
}
.meeting-detail__title {
  margin: 0 0 8px;
  font-size: 18px;
  color: #1f2d3d;
}
.meeting-detail__meta {
  font-size: 13px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1f6;
}
.meeting-desc {
  margin-top: 16px;
}
.meeting-minutes {
  margin-top: 20px;
}
.meeting-minutes__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #eef1f6;
}
.meeting-minutes__label {
  font-weight: 600;
  color: #1f2d3d;
}
.meeting-minutes__content {
  margin: 12px 0 0;
  padding: 12px 14px;
  background: #f7f9fc;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.8;
  color: #334155;
}
.meeting-minutes__tip {
  margin-bottom: 10px;
  font-size: 13px;
}
.w-full {
  width: 100%;
}
</style>
