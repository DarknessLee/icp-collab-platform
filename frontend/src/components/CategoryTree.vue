<script setup lang="ts">
import { ref } from 'vue'
import type { IcpCategory } from '@/types/icp'

/** 内控分类树（制度分类/风险分类共用），点击节点按 categoryId 过滤列表 */
const props = withDefaults(
  defineProps<{
    data: IcpCategory[]
    title?: string
    loading?: boolean
  }>(),
  { title: '分类', loading: false }
)

const emit = defineEmits<{ select: [id: number | undefined] }>()

const treeRef = ref<{ setCurrentKey: (key: number | undefined) => void }>()
const currentId = ref<number>()

function handleClick(node: IcpCategory): void {
  currentId.value = node.id
  emit('select', node.id)
}

function selectAll(): void {
  currentId.value = undefined
  treeRef.value?.setCurrentKey(undefined)
  emit('select', undefined)
}
</script>

<template>
  <div class="category-panel">
    <div class="category-head">
      <span class="category-title">{{ props.title }}</span>
      <el-button link type="primary" size="small" @click="selectAll">全部</el-button>
    </div>
    <el-skeleton v-if="props.loading" :rows="5" animated />
    <el-scrollbar v-else class="category-body">
      <el-tree
        ref="treeRef"
        :data="props.data"
        node-key="id"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        :props="{ label: 'categoryName', children: 'children' }"
        empty-text="暂无分类"
        @node-click="handleClick"
      />
    </el-scrollbar>
  </div>
</template>

<style scoped>
.category-panel {
  display: flex;
  flex-direction: column;
  width: 220px;
  min-width: 220px;
  border-right: 1px solid #eef1f6;
}

.category-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 10px;
}

.category-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.category-body {
  flex: 1;
  min-height: 280px;
  max-height: 560px;
}
</style>
