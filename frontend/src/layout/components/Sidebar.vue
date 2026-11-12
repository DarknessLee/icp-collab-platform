<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { resolveIcon } from '@/utils/icons'
import { isDirectoryMenu, normalizePath } from '@/utils/menu'
import { APP_NAME, APP_SHORT_NAME, APP_VERSION } from '@/config/app'

defineProps<{ mobile: boolean }>()

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const menus = computed(() => userStore.menuTree)
const activeMenu = computed(() => route.path)
const collapsed = computed(() => appStore.sidebarCollapsed && !appStore.mobileMenuOpen)

function handleSelect(path: string): void {
  if (path && path !== route.path) {
    router.push(path)
  }
}
</script>

<template>
  <aside
    class="sidebar"
    :class="{ collapsed, 'mobile-open': mobile && appStore.mobileMenuOpen }"
  >
    <div class="sidebar-logo" @click="router.push('/dashboard')">
      <div class="logo-mark">控</div>
      <div v-show="!collapsed" class="logo-text">
        <div class="logo-title" :title="APP_NAME">{{ APP_SHORT_NAME }}</div>
        <div class="logo-sub">{{ APP_VERSION }}</div>
      </div>
    </div>

    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        unique-opened
        class="sidebar-menu"
        background-color="#0f2440"
        text-color="#c3cfe2"
        active-text-color="#ffffff"
        @select="handleSelect"
      >
        <template v-for="item in menus" :key="item.id">
          <el-sub-menu v-if="isDirectoryMenu(item)" :index="`group-${item.id}`">
            <template #title>
              <el-icon class="menu-icon"><component :is="resolveIcon(item.icon)" /></el-icon>
              <span>{{ item.name }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.id"
              :index="normalizePath(child.path)"
            >
              <el-icon class="menu-icon"><component :is="resolveIcon(child.icon)" /></el-icon>
              <template #title>{{ child.name }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="normalizePath(item.path)">
            <el-icon class="menu-icon"><component :is="resolveIcon(item.icon)" /></el-icon>
            <template #title>{{ item.name }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>

    <div class="sidebar-footer">
      <span v-if="!collapsed">V1.0 · 企业内控协同平台</span>
      <span v-else>V1.0</span>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: 232px;
  height: 100%;
  overflow: hidden;
  background-color: #0f2440;
  transition: width 0.2s ease;
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 14px;
  cursor: pointer;
  background-color: #0b1c33;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.logo-mark {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #2f6fed, #16a6a6);
  border-radius: 8px;
}

.logo-text {
  min-width: 0;
  overflow: hidden;
}

.logo-title {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  color: #ffffff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.logo-sub {
  font-size: 11px;
  line-height: 16px;
  color: #7f93b0;
  white-space: nowrap;
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
}

.sidebar-menu {
  border-right: none;
}

/* 菜单图标：显式给定盒模型，避免主题/浏览器差异下 el-icon 尺寸塌陷导致“图标不显示” */
.sidebar-menu :deep(.el-icon.menu-icon) {
  display: inline-flex;
  flex: 0 0 18px;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-right: 10px;
  font-size: 18px;
  line-height: 18px;
  color: inherit;
}

.sidebar-menu :deep(.el-icon.menu-icon svg) {
  width: 18px;
  height: 18px;
  fill: currentColor;
}

.sidebar-menu.el-menu--collapse :deep(.el-icon.menu-icon) {
  margin-right: 0;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 232px;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 46px;
  line-height: 46px;
  font-size: 14px;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: #2f6fed;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: #18345a;
}

.sidebar-menu :deep(.el-sub-menu .el-menu) {
  background-color: #0b1c33;
}

.sidebar-footer {
  height: 34px;
  font-size: 11px;
  line-height: 34px;
  color: #5d7599;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

@media (max-width: 767px) {
  .sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 1000;
    width: 232px;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .sidebar.mobile-open {
    transform: translateX(0);
  }

  .sidebar.collapsed {
    width: 232px;
  }
}
</style>
