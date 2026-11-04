<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { usePageList } from '@/composables/usePageList'
import { messageApi } from '@/api/collab'
import { useMessageStream } from '@/composables/useMessageStream'
import type { PageQuery } from '@/types/system'
import type { MessageRow } from '@/types/collab'

const TYPE_OPTIONS = [
  { value: 1, label: '系统消息' },
  { value: 2, label: '审批消息' },
  { value: 3, label: '任务消息' },
  { value: 4, label: '公告消息' },
  { value: 5, label: '预警消息' }
]
function typeLabel(type?: number): string {
  return TYPE_OPTIONS.find((item) => item.value === type)?.label ?? '其他消息'
}
function isTaskRemind(row: MessageRow): boolean {
  return row.type === 3 && row.bizType === 'REMIND'
}
function typeText(row: MessageRow): string {
  return isTaskRemind(row) ? '任务提醒' : typeLabel(row.type)
}
function typeTag(type?: number): 'primary' | 'warning' | 'success' | 'info' | 'danger' {
  if (type === 2) return 'primary'
  if (type === 3) return 'warning'
  if (type === 4) return 'success'
  if (type === 5) return 'danger'
  return 'info'
}

const { query, list, total, loading, load, search, handlePageChange, handleSizeChange } =
  usePageList<MessageRow>((params) => messageApi.list(params as PageQuery), {
    keyword: '',
    type: undefined,
    read: undefined
  })

const unreadCount = ref(0)
const selection = ref<MessageRow[]>([])

async function refreshUnread(): Promise<void> {
  try {
    unreadCount.value = await messageApi.unreadCount()
  } catch {
    unreadCount.value = 0
  }
}

function doSearch(): void {
  query.pageNum = 1
  search()
}

/** SSE 实时消息：接入后顶部提示并刷新列表/未读数 */
const { connected, start: startStream } = useMessageStream((payload) => {
  const title = String(payload.title ?? '新站内消息')
  const content = String(payload.content ?? '')
  ElNotification({
    title,
    message: content,
    type: 'info',
    duration: 5000
  })
  unreadCount.value += 1
  void load()
})

onMounted(() => {
  void load()
  void refreshUnread()
  // 页面级订阅；组件卸载时自动断开并静默重连由 composable 处理
  startStream()
})


/* ---------------- 已读操作 ---------------- */
async function markRead(row: MessageRow): Promise<void> {
  if (row.read) return
  try {
    await messageApi.read([row.id])
    row.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    // 已提示
  }
}

async function markSelectionRead(): Promise<void> {
  const ids = selection.value.filter((item) => !item.read).map((item) => item.id)
  if (!ids.length) {
    ElMessage.warning('请选择未读消息')
    return
  }
  try {
    await messageApi.read(ids)
    ElMessage.success(`已将 ${ids.length} 条消息标记为已读`)
    selection.value = []
    await Promise.all([load(), refreshUnread()])
  } catch {
    // 已提示
  }
}

async function markAllRead(): Promise<void> {
  try {
    await messageApi.readAll()
    ElMessage.success('全部消息已标记为已读')
    await Promise.all([load(), refreshUnread()])
  } catch {
    // 已提示
  }
}

const detailVisible = ref(false)
const detail = ref<MessageRow>()

async function openDetail(row: MessageRow): Promise<void> {
  detail.value = row
  detailVisible.value = true
  await markRead(row)
}

const connectionText = computed(() => (connected.value ? '实时连接中' : '实时通道重连中'))
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div>
          <div class="page-toolbar-heading">消息中心</div>
          <div class="page-toolbar-desc">站内信列表与未读数；SSE 实时推送（断线自动退避重连）</div>
        </div>
        <div class="page-toolbar-actions">
          <el-tag :type="connected ? 'success' : 'info'" effect="plain" size="small">{{ connectionText }}</el-tag>
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="unread-badge">
            <el-tag type="warning" effect="plain">未读 {{ unreadCount }}</el-tag>
          </el-badge>
          <el-button v-if="unreadCount > 0" v-perm="'collab:message:read'" @click="markAllRead">全部已读</el-button>
        </div>
      </div>

      <div class="page-toolbar page-toolbar--filters">
        <el-select v-model="query.type" placeholder="消息类型" clearable class="toolbar-select" @change="doSearch">
          <el-option v-for="item in TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.read" placeholder="阅读状态" clearable class="toolbar-select" @change="doSearch">
          <el-option label="未读" :value="false" />
          <el-option label="已读" :value="true" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="消息标题 / 内容" clearable class="toolbar-input" @keyup.enter="doSearch" @clear="doSearch" />
        <el-button type="primary" plain @click="doSearch">查询</el-button>
        <el-button v-perm="'collab:message:read'" :disabled="!selection.length" @click="markSelectionRead">标记已读</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" stripe @selection-change="(rows: MessageRow[]) => (selection = rows)">
        <el-table-column type="selection" width="44" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ typeText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="消息标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'msg-title--unread': !row.read }" @click="openDetail(row)">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="消息内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.read ? 'info' : 'danger'">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="!row.read" v-perm="'collab:message:read'" link type="warning" @click="markRead(row)">已读</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无站内消息" />
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

    <el-drawer v-model="detailVisible" title="消息详情" size="480px" destroy-on-close>
      <template v-if="detail">
        <h3 class="msg-detail__title">{{ detail.title }}</h3>
        <div class="msg-detail__meta text-muted">{{ typeLabel(detail.type) }} · {{ detail.createTime || '—' }}</div>
        <div class="msg-detail__content">{{ detail.content || '（无正文）' }}</div>
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
.msg-title--unread {
  font-weight: 600;
  color: #1f2d3d;
  cursor: pointer;
}
.msg-title--unread::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f56c6c;
  margin-right: 6px;
  vertical-align: middle;
}
.msg-detail__title {
  margin: 0 0 8px;
  font-size: 18px;
  color: #1f2d3d;
}
.msg-detail__meta {
  font-size: 13px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef1f6;
}
.msg-detail__content {
  margin-top: 16px;
  line-height: 1.8;
  color: #334155;
  white-space: pre-wrap;
}
</style>
