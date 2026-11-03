<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import type { TagView } from '@/types'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const tags = computed(() => appStore.tags)

function handleClick(tag: TagView): void {
  if (tag.path !== route.path) router.push(tag.path)
}

function handleClose(tag: TagView): void {
  if (tag.affix) return
  const wasActive = tag.path === route.path
  appStore.removeTag(tag.path)
  if (wasActive) {
    const last = appStore.tags[appStore.tags.length - 1]
    router.push(last ? last.path : '/dashboard')
  }
}
</script>

<template>
  <div class="tags-view">
    <el-scrollbar class="tags-scroll">
      <div class="tags-inner">
        <el-tag
          v-for="tag in tags"
          :key="tag.path"
          class="tag-item"
          :closable="!tag.affix"
          :effect="tag.path === route.path ? 'dark' : 'plain'"
          :type="tag.path === route.path ? 'primary' : 'info'"
          size="small"
          @click="handleClick(tag)"
          @close="handleClose(tag)"
        >
          {{ tag.title }}
        </el-tag>
      </div>
    </el-scrollbar>
  </div>
</template>

<style scoped>
.tags-view {
  flex-shrink: 0;
  height: 40px;
  padding: 0 16px;
  overflow: hidden;
  background-color: #ffffff;
  border-top: 1px solid #edf0f5;
  border-bottom: 1px solid #edf0f5;
}

.tags-scroll {
  height: 40px;
}

.tags-inner {
  display: flex;
  gap: 8px;
  align-items: center;
  height: 40px;
}

.tag-item {
  cursor: pointer;
  user-select: none;
}

@media (max-width: 767px) {
  .tags-view {
    padding: 0 10px;
  }
}
</style>
