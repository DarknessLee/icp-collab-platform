<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePageList } from '@/composables/usePageList'
import { scheduleApi } from '@/api/collab'
import { useUserStore } from '@/stores/user'
import type { PageQuery } from '@/types/system'
import type { CalendarItem, SchedulePayload, ScheduleRow } from '@/types/collab'

const userStore = useUserStore()
const can = (perm: string): boolean => userStore.hasPermission(perm)

const TYPE_OPTIONS = [
  { value: 1, label: '会议' },
  { value: 2, label: '提醒' },
  { value: 3, label: '个人' }
]
const STATUS_OPTIONS = [
  { value: 0, label: '待开始' },
  { value: 1, label: '进行中' },
  { value: 2, label: '已结束' },
  { value: 3, label: '已取消' }
]
function typeLabel(type?: number): string {
  return TYPE_OPTIONS.find((item) => item.value === type)?.label ?? '个人'
}
function typeTag(type?: number): 'primary' | 'warning' | 'info' {
  if (type === 1) return 'primary'
  if (type === 2) return 'warning'
  return 'info'
}
function statusLabel(status?: number): string {
  return STATUS_OPTIONS.find((item) => item.value === status)?.label ?? '待开始'
}
function statusTag(status?: number): 'success' | 'primary' | 'info' | 'danger' {
  if (status === 1) return 'primary'
  if (status === 2) return 'info'
  if (status === 3) return 'danger'
  return 'success'
}

/* ---------------- 月历 ---------------- */
const currentMonth = ref(formatMonth(new Date()))
const calendarItems = ref<CalendarItem[]>([])
const calendarLoading = ref(false)

function formatMonth(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

async function loadCalendar(): Promise<void> {
  calendarLoading.value = true
  try {
    calendarItems.value = await scheduleApi.calendar(currentMonth.value)
  } catch {
    calendarItems.value = []
  } finally {
    calendarLoading.value = false
  }
}

interface CalendarCell {
  date: string
  day: number
  inMonth: boolean
  today: boolean
  items: CalendarItem[]
}

const calendarCells = computed<CalendarCell[]>(() => {
  const [yearText, monthText] = currentMonth.value.split('-')
  const year = Number(yearText)
  const month = Number(monthText)
  const first = new Date(year, month - 1, 1)
  const startOffset = first.getDay()
  const daysInMonth = new Date(year, month, 0).getDate()
  const prevMonthDays = new Date(year, month - 1, 0).getDate()
  const today = new Date()
  const todayText = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  const cells: CalendarCell[] = []

  for (let i = 0; i < 42; i += 1) {
    const offset = i - startOffset + 1
    let cellYear = year
    let cellMonth = month
    let day = offset
    let inMonth = true
    if (offset <= 0) {
      day = prevMonthDays + offset
      cellMonth = month - 1
      inMonth = false
    } else if (offset > daysInMonth) {
      day = offset - daysInMonth
      cellMonth = month + 1
      inMonth = false
    }
    if (cellMonth <= 0) {
      cellMonth = 12
      cellYear -= 1
    } else if (cellMonth > 12) {
      cellMonth = 1
      cellYear += 1
    }
    const date = `${cellYear}-${String(cellMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    cells.push({
      date,
      day,
      inMonth,
      today: date === todayText,
      items: calendarItems.value.filter((item) => (item.start || '').slice(0, 10) === date)
    })
  }
  return cells
})

const WEEK_DAYS = ['日', '一', '二', '三', '四', '五', '六']

function shiftMonth(delta: number): void {
  const [yearText, monthText] = currentMonth.value.split('-')
  const date = new Date(Number(yearText), Number(monthText) - 1 + delta, 1)
  currentMonth.value = formatMonth(date)
  void loadCalendar()
}

/* ---------------- 列表 ---------------- */
const { query, list, total, loading, load, search } = usePageList<ScheduleRow>(
  (params) => scheduleApi.list(params as PageQuery),
  { keyword: '', status: undefined, month: currentMonth.value }
)

onMounted(() => {
  void loadCalendar()
  void load()
})

function doSearch(): void {
  query.pageNum = 1
  search()
}

/* ---------------- 新建 / 编辑 / 取消 ---------------- */
const formVisible = ref(false)
const editingId = ref<ScheduleRow['id']>()
const form = reactive<SchedulePayload & { range?: [string, string] }>({
  title: '',
  type: 1,
  description: '',
  location: '',
  startTime: '',
  endTime: ''
})

function openCreate(): void {
  editingId.value = undefined
  Object.assign(form, { title: '', type: 1, description: '', location: '', startTime: '', endTime: '', range: undefined })
  formVisible.value = true
}

function openEdit(row: ScheduleRow): void {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    type: row.type ?? 1,
    description: row.description ?? '',
    location: row.location ?? '',
    startTime: row.startTime ?? '',
    endTime: row.endTime ?? '',
    range: undefined
  })
  formVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请输入日程主题')
    return
  }
  const start = form.range?.[0] ?? form.startTime
  const end = form.range?.[1] ?? form.endTime
  if (start && end && new Date(start.replace(/-/g, '/')) > new Date(end.replace(/-/g, '/'))) {
    ElMessage.warning('结束时间不能早于开始时间')
    return
  }
  try {
    const payload: SchedulePayload = {
      title: form.title.trim(),
      type: form.type,
      description: form.description,
      location: form.location,
      startTime: start,
      endTime: end
    }
    if (editingId.value) {
      await scheduleApi.update(editingId.value, payload)
      ElMessage.success('日程已更新')
    } else {
      await scheduleApi.create(payload)
      ElMessage.success('日程已创建')
    }
    formVisible.value = false
    await Promise.all([load(), loadCalendar()])
  } catch {
    // 已提示
  }
}

async function cancelSchedule(row: ScheduleRow): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认取消日程「${row.title}」？`, '取消日程', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回'
    })
    await scheduleApi.cancel(row.id)
    ElMessage.success('日程已取消')
    await Promise.all([load(), loadCalendar()])
  } catch {
    // 取消或接口错误
  }
}

function cellItemClass(item: CalendarItem): string {
  return item.status === 3 ? 'cal-item cal-item--cancelled' : 'cal-item'
}

function openFromCalendar(item: CalendarItem): void {
  const row = list.value.find((entry) => entry.id === item.id)
  if (row) openEdit(row)
}
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">日程会议</div>
          <div class="page-toolbar-desc">按月查看会议、提醒与个人日程；支持新建、编辑与取消</div>
        </div>
        <div class="page-toolbar-actions">
          <el-button v-if="can('collab:schedule:add')" type="primary" @click="openCreate">新建日程</el-button>
        </div>
      </div>

      <div class="calendar-head">
        <el-button link @click="shiftMonth(-1)">‹ 上一月</el-button>
        <span class="calendar-month">{{ currentMonth }}</span>
        <el-button link @click="shiftMonth(1)">下一月 ›</el-button>
        <el-date-picker
          v-model="currentMonth"
          type="month"
          value-format="YYYY-MM"
          :clearable="false"
          class="ml-12"
          style="width: 150px"
          @change="loadCalendar"
        />
      </div>

      <div v-loading="calendarLoading" class="calendar">
        <div v-for="day in WEEK_DAYS" :key="day" class="calendar-week">{{ day }}</div>
        <div
          v-for="cell in calendarCells"
          :key="cell.date"
          class="calendar-cell"
          :class="{ 'calendar-cell--muted': !cell.inMonth, 'calendar-cell--today': cell.today }"
        >
          <div class="calendar-day">{{ cell.day }}</div>
          <div class="calendar-items">
            <span v-for="item in cell.items.slice(0, 3)" :key="item.id" :class="cellItemClass(item)" @click="openFromCalendar(item)">
              {{ item.title }}
            </span>
            <span v-if="cell.items.length > 3" class="cal-more">+{{ cell.items.length - 3 }}</span>
          </div>
        </div>
      </div>

      <div class="page-toolbar page-toolbar--filters mt-16">
        <el-select v-model="query.status" placeholder="日程状态" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="日程主题" clearable class="toolbar-input" @keyup.enter="doSearch" @clear="doSearch" />
        <el-button type="primary" plain @click="doSearch">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column prop="title" label="日程主题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170">
          <template #default="{ row }">{{ row.startTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="170">
          <template #default="{ row }">{{ row.endTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="location" label="地点" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.location || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 3" v-perm="'collab:schedule:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 3 && row.status !== 2" v-perm="'collab:schedule:edit'" link type="danger" @click="cancelSchedule(row)">取消</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无日程，新建会议或提醒开始安排" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑日程' : '新建日程'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="92px">
        <el-form-item label="日程主题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：季度内控工作会议" />
        </el-form-item>
        <el-form-item label="日程类型">
          <el-radio-group v-model="form.type">
            <el-radio-button v-for="item in TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择开始时间" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择结束时间" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" maxlength="100" placeholder="如：3 号会议室 / 线上会议" />
        </el-form-item>
        <el-form-item label="日程说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="补充议题、参会人等信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-toolbar--filters {
  gap: 8px;
  flex-wrap: wrap;
}
.calendar-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0 12px;
}
.calendar-month {
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}
.ml-12 {
  margin-left: 12px;
}
.calendar {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  border-top: 1px solid #eef1f6;
  border-left: 1px solid #eef1f6;
}
.calendar-week {
  padding: 8px 0;
  text-align: center;
  font-size: 12px;
  color: #909399;
  background: #f8fafc;
  border-right: 1px solid #eef1f6;
  border-bottom: 1px solid #eef1f6;
}
.calendar-cell {
  min-height: 92px;
  padding: 6px;
  border-right: 1px solid #eef1f6;
  border-bottom: 1px solid #eef1f6;
  background: #fff;
}
.calendar-cell--muted {
  background: #fbfcfe;
  color: #c0c4cc;
}
.calendar-cell--today {
  background: #eef4ff;
}
.calendar-day {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
}
.calendar-items {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.cal-item {
  display: block;
  font-size: 12px;
  color: #2f6fed;
  background: #eef4ff;
  border-radius: 3px;
  padding: 1px 4px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-item--cancelled {
  color: #909399;
  background: #f4f4f5;
  text-decoration: line-through;
}
.cal-more {
  font-size: 11px;
  color: #909399;
}
</style>
