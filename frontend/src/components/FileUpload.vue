<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, UploadFilled } from '@element-plus/icons-vue'
import { uploadFile } from '@/api/system'
import type { UploadResult } from '@/types/system'

/**
 * 通用附件上传组件（docs/03 §1.7）。
 * - multipart POST /files，字段名 file
 * - 白名单 pdf/png/jpg/jpeg/doc/docx/xls/xlsx/zip，默认 ≤20MB
 * - 展示上传进度、文件名/大小，成功抛出 UploadResult（id/originalName/size）
 */
const props = withDefaults(
  defineProps<{
    accept?: string
    maxSizeMb?: number
    disabled?: boolean
    tip?: string
  }>(),
  {
    accept: '.pdf,.png,.jpg,.jpeg,.doc,.docx,.xls,.xlsx,.zip',
    maxSizeMb: 20,
    disabled: false,
    tip: ''
  }
)

const emit = defineEmits<{
  success: [result: UploadResult]
  remove: []
}>()

const inputRef = ref<HTMLInputElement>()
const uploading = ref(false)
const percent = ref(0)
const currentFile = ref<File>()
const result = ref<UploadResult>()

const acceptList = computed(() =>
  props.accept
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
)

const tipText = computed(
  () =>
    props.tip ||
    `支持 ${acceptList.value.join(' / ')}，单个文件不超过 ${props.maxSizeMb}MB`
)

function trigger(): void {
  if (props.disabled || uploading.value) return
  inputRef.value?.click()
}

function handleDrop(event: DragEvent): void {
  if (props.disabled || uploading.value) return
  const file = event.dataTransfer?.files?.[0]
  if (file) void validateAndUpload(file)
}

function handleChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void validateAndUpload(file)
}

function formatSize(bytes: number): string {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

async function validateAndUpload(file: File): Promise<void> {
  const name = file.name.toLowerCase()
  const suffix = name.includes('.') ? `.${name.split('.').pop()}` : ''
  if (acceptList.value.length && !acceptList.value.includes(suffix)) {
    ElMessage.warning(`文件类型不支持，仅允许 ${acceptList.value.join('/')}`)
    return
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.warning(`文件大小超过 ${props.maxSizeMb}MB 限制`)
    return
  }

  uploading.value = true
  percent.value = 0
  currentFile.value = file
  try {
    const uploaded = await uploadFile(file, (p) => {
      percent.value = p
    })
    result.value = uploaded
    emit('success', uploaded)
    ElMessage.success(`上传成功：${uploaded.originalName}`)
  } catch {
    // 统一错误提示由 request.ts 拦截器完成
    currentFile.value = undefined
  } finally {
    uploading.value = false
  }
}

function clear(): void {
  currentFile.value = undefined
  result.value = undefined
  percent.value = 0
  emit('remove')
}

defineExpose({ clear, result })
</script>

<template>
  <div class="file-upload">
    <input
      ref="inputRef"
      class="file-upload-input"
      type="file"
      :accept="accept"
      :disabled="disabled"
      @change="handleChange"
    />

    <div
      v-if="!currentFile"
      class="file-upload-drop"
      :class="{ 'is-disabled': disabled }"
      @click="trigger"
      @dragover.prevent
      @drop.prevent="handleDrop"
    >
      <el-icon :size="28" class="file-upload-icon"><UploadFilled /></el-icon>
      <p class="file-upload-title">点击选择文件，或将文件拖拽到此处</p>
      <p class="file-upload-tip">{{ tipText }}</p>
    </div>

    <div v-else class="file-upload-selected">
      <el-icon :size="20" class="file-upload-file-icon"><Document /></el-icon>
      <div class="file-upload-meta">
        <p class="file-upload-name" :title="currentFile.name">{{ currentFile.name }}</p>
        <p class="file-upload-size">
          {{ formatSize(currentFile.size) }}
          <span v-if="result?.id">· 附件ID {{ result.id }}</span>
        </p>
        <el-progress v-if="uploading" :percentage="percent" :stroke-width="6" />
      </div>
      <el-button v-if="!uploading" size="small" text type="danger" @click="clear">移除</el-button>
      <el-tag v-else size="small" type="info">上传中 {{ percent }}%</el-tag>
    </div>
  </div>
</template>

<style scoped>
.file-upload {
  width: 100%;
}

.file-upload-input {
  display: none;
}

.file-upload-drop {
  padding: 20px 16px;
  text-align: center;
  cursor: pointer;
  background-color: #f8fafc;
  border: 1px dashed #c5d0e0;
  border-radius: 8px;
  transition: border-color 0.2s, background-color 0.2s;
}

.file-upload-drop:hover {
  background-color: #f2f6ff;
  border-color: #2f6fed;
}

.file-upload-drop.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.file-upload-icon {
  color: #2f6fed;
}

.file-upload-title {
  margin: 8px 0 4px;
  font-size: 14px;
  color: #1f2d3d;
}

.file-upload-tip {
  margin: 0;
  font-size: 12px;
  color: #8a94a6;
}

.file-upload-selected {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px;
  background-color: #f8fafc;
  border: 1px solid #e4e9f2;
  border-radius: 8px;
}

.file-upload-file-icon {
  color: #2f6fed;
}

.file-upload-meta {
  flex: 1;
  min-width: 0;
}

.file-upload-name {
  margin: 0 0 4px;
  overflow: hidden;
  font-size: 14px;
  color: #1f2d3d;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-upload-size {
  margin: 0 0 6px;
  font-size: 12px;
  color: #8a94a6;
}
</style>
