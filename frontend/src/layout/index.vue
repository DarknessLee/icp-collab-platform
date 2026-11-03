<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import TagsView from './components/TagsView.vue'
import AppMain from './components/AppMain.vue'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const appStore = useAppStore()

const isMobile = ref(window.innerWidth < 768)

function syncViewport(): void {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    appStore.toggleMobileMenu(false)
  }
}

onMounted(() => {
  window.addEventListener('resize', syncViewport)
  appStore.setSidebar(window.innerWidth < 1200)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
})

// 路由变化自动记录标签页
watch(
  () => route.path,
  () => {
    const title = (route.meta.title as string | undefined) || ''
    if (title && route.path !== '/login' && route.path !== '/403' && route.path !== '/404') {
      appStore.addTag({ path: route.path, title })
    }
    if (isMobile.value) appStore.toggleMobileMenu(false)
  },
  { immediate: true }
)

const layoutClass = computed(() => ({
  'is-collapsed': appStore.sidebarCollapsed,
  'is-mobile': isMobile.value
}))
</script>

<template>
  <div class="app-layout" :class="layoutClass">
    <Sidebar :mobile="isMobile" />
    <div
      v-if="isMobile && appStore.mobileMenuOpen"
      class="sidebar-mask"
      @click="appStore.toggleMobileMenu(false)"
    />
    <div class="app-body">
      <Navbar :mobile="isMobile" />
      <TagsView />
      <AppMain />
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: #f2f4f7;
}

.app-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  height: 100%;
}

.sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  background-color: rgba(15, 36, 64, 0.45);
}
</style>
