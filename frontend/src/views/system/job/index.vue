<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { jobApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import type { JobRow } from '@/types/system'

const userStore = useUserStore()
function can(perm: string): boolean {
  return userStore.hasPermission(perm)
}

const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<JobRow>(jobApi.list, { keyword: '', status: '' })

async function changeStatus(row: JobRow, next: 0 | 1): Promise<void> {
  const prev: 0 | 1 = row.status ?? 0
  const actionText = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确定${actionText}定时任务「${row.name}」吗？`, `${actionText}定时任务`, {
      type: 'warning',
      confirmButtonText: `确认${actionText}`,
      cancelButtonText: '取消'
    })
  } catch {
    row.status = prev
    return
  }
  try {
    await jobApi.updateStatus(row.id, next)
    row.status = next
    ElMessage.success(`定时任务已${actionText}`)
  } catch {
    row.status = prev
  }
}

function runNow(row: JobRow): void {
  ElMessage.info(`「${row.name}」立即执行将在 M2e 阶段接入调度中心后开放`)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">定时任务</span>
          <span class="page-toolbar-desc">系统后台任务（如超时提醒、日志归档）；执行器与立即执行能力在 M2e 接入</span>
        </div>
        <div class="page-toolbar-actions">
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="任务名称"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="启用状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无定时任务">
        <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="group" label="任务分组" width="130" />
        <el-table-column prop="cron" label="Cron 表达式" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status ?? 0"
              :active-value="1"
              :inactive-value="0"
              :disabled="!can('system:job:edit')"
              @change="(value: string | number | boolean) => changeStatus(row, Number(value) as 0 | 1)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastRunTime" label="上次执行" width="170">
          <template #default="{ row }">{{ row.lastRunTime || '尚未执行' }}</template>
        </el-table-column>
        <el-table-column prop="nextRunTime" label="下次执行" width="170">
          <template #default="{ row }">{{ row.nextRunTime || '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-perm="'system:job:run'" @click="runNow(row)">立即执行</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
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

.toolbar-input {
  width: 220px;
}

.toolbar-select {
  width: 130px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
