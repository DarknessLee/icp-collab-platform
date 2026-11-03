<template>
  <div class="wf-history" v-loading="loading">
    <el-empty v-if="!loading && !items.length" description="暂无流转记录" :image-size="72" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="(item, index) in items"
        :key="`${item.nodeName}-${index}`"
        :timestamp="item.endTime || item.startTime || ''"
        placement="top"
        :type="actionType(item.action)"
        :hollow="index === items.length - 1"
      >
        <div class="wf-history__head">
          <span class="wf-history__node">{{ item.nodeName }}</span>
          <el-tag size="small" :type="actionType(item.action)">{{ actionText(item.action) }}</el-tag>
          <span class="wf-history__user">{{ item.assigneeName || item.assignee || '—' }}</span>
        </div>
        <div v-if="item.comment" class="wf-history__comment">{{ item.comment }}</div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import type { WfHistoryItem } from '@/types/workflow'

withDefaults(defineProps<{ items?: WfHistoryItem[]; loading?: boolean }>(), { items: () => [], loading: false })

function actionText(action?: string): string {
  const key = (action || '').toLowerCase()
  if (key.includes('reject') || key.includes('驳回')) return '驳回'
  if (key.includes('approve') || key.includes('同意') || key.includes('审批')) return '审批通过'
  if (key.includes('resubmit') || key.includes('重提')) return '重新提交'
  if (key.includes('start') || key.includes('发起')) return '发起'
  if (key.includes('urge') || key.includes('催办')) return '催办'
  return action || '审批'
}

function actionType(action?: string): 'primary' | 'success' | 'danger' | 'warning' | 'info' {
  const key = (action || '').toLowerCase()
  if (key.includes('reject') || key.includes('驳回')) return 'danger'
  if (key.includes('approve') || key.includes('同意') || key.includes('审批')) return 'success'
  if (key.includes('resubmit') || key.includes('重提')) return 'warning'
  if (key.includes('urge') || key.includes('催办')) return 'warning'
  return 'primary'
}
</script>

<style scoped>
.wf-history {
  padding: 4px 4px 0 2px;
}

.wf-history__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.wf-history__node {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.wf-history__user {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.wf-history__comment {
  margin-top: 6px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
