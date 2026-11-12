<script setup lang="ts">
import { ref } from 'vue'
import FileUpload from '@/components/FileUpload.vue'
import type { UploadResult } from '@/types/system'

/**
 * 开发态附件上传自测页（仅 DEV 注册，不进入生产包）。
 * 用于 M2a 验收时验证 FileUpload 与 POST /files 契约（pdf 成功、txt 被拒）。
 */
const result = ref<UploadResult>()
const events = ref<string[]>([])

function onSuccess(payload: UploadResult): void {
  result.value = payload
  events.value.unshift(`success: ${payload.originalName}（${payload.size} B，附件ID ${payload.id}）`)
}

function onRemove(): void {
  result.value = undefined
  events.value.unshift('remove: 已移除当前附件')
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="flex-between">
          <span>附件上传自测（开发态）</span>
          <el-tag type="info" size="small">POST /api/v1/files</el-tag>
        </div>
      </template>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅用于 M2a 验收：白名单 pdf/png/jpg/jpeg/doc/docx/xls/xlsx/zip，单个文件≤20MB；非白名单文件前端直接提示，后端返回 41300。"
      />
      <div class="upload-preview">
        <FileUpload @success="onSuccess" @remove="onRemove" />
      </div>
      <el-descriptions v-if="result" class="result-block" :column="2" border>
        <el-descriptions-item label="附件 ID">{{ result.id }}</el-descriptions-item>
        <el-descriptions-item label="原始文件名">{{ result.originalName }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ result.size }} B</el-descriptions-item>
        <el-descriptions-item label="后缀">{{ result.suffix || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-timeline v-if="events.length" class="event-list">
        <el-timeline-item v-for="(item, index) in events" :key="index">{{ item }}</el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<style scoped>
.upload-preview {
  max-width: 560px;
  margin-top: 16px;
}

.result-block {
  margin-top: 16px;
}

.event-list {
  margin-top: 16px;
}
</style>
