<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deptApi } from '@/api/system'
import { useUserStore } from '@/stores/user'
import type { DeptNode } from '@/types/system'

/**
 * 归口/责任部门选择：有 system:dept:list 权限时加载部门树；无权限（如内控管理员）退回部门 ID 输入，
 * 避免因调用组织接口 403 触发全局 403 跳转。留空由后端默认当前用户部门。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    placeholder?: string
    disabled?: boolean
  }>(),
  { modelValue: undefined, placeholder: '留空默认本人部门', disabled: false }
)

const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()

const userStore = useUserStore()
const canLoad = ref(userStore.hasPermission('system:dept:list'))
const tree = ref<DeptNode[]>([])

onMounted(async () => {
  if (!canLoad.value) return
  try {
    tree.value = await deptApi.tree()
  } catch {
    // 无权限或接口失败时退回 ID 输入
    canLoad.value = false
  }
})

function update(value: number | null | undefined): void {
  emit('update:modelValue', value === null ? undefined : (value ?? undefined))
}
</script>

<template>
  <el-tree-select
    v-if="canLoad"
    :model-value="props.modelValue ?? undefined"
    :data="tree"
    node-key="id"
    check-strictly
    clearable
    :props="{ label: 'name', children: 'children' }"
    :placeholder="props.placeholder"
    :disabled="props.disabled"
    style="width: 100%"
    @update:model-value="update"
  />
  <el-input-number
    v-else
    :model-value="props.modelValue ?? undefined"
    :min="1"
    :controls="false"
    :disabled="props.disabled"
    :placeholder="props.placeholder"
    style="width: 100%"
    @update:model-value="update"
  />
</template>
