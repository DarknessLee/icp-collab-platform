<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold, HomeFilled, SwitchButton, User } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { findMenuChain } from '@/utils/menu'
import { resetMenuRoutes } from '@/router'

defineProps<{ mobile: boolean }>()

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const isDashboard = computed(() => route.path === '/dashboard')
const chain = computed(() => findMenuChain(userStore.menuTree, route.path))
const breadcrumbs = computed(() => {
  const titles = chain.value.map((item) => item.name).filter(Boolean)
  if (titles.length > 0) return titles
  const fallback = route.meta.title as string | undefined
  return fallback ? [fallback] : []
})
const userInitial = computed(() => userStore.displayName.slice(0, 1))

function toggleSidebar(): void {
  if (window.innerWidth < 768) {
    appStore.toggleMobileMenu()
    return
  }
  appStore.toggleSidebar()
}

async function handleCommand(command: string): Promise<void> {
  if (command === 'profile') {
    await router.push('/profile')
    return
  }
  if (command !== 'logout') return

  try {
    await ElMessageBox.confirm('退出后需重新登录，确认退出当前账号？', '退出登录', {
      confirmButtonText: '退出登录',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  await userStore.logout()
  appStore.resetTags()
  resetMenuRoutes()
  router.replace('/login')
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-left">
      <button class="navbar-icon-button" type="button" title="展开/收起菜单" @click="toggleSidebar">
        <el-icon :size="18">
          <Expand v-if="(appStore.sidebarCollapsed && !mobile) || (mobile && !appStore.mobileMenuOpen)" />
          <Fold v-else />
        </el-icon>
      </button>

      <el-breadcrumb v-if="!mobile" separator="/" class="navbar-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">
          <el-icon class="breadcrumb-home"><HomeFilled /></el-icon>
          首页
        </el-breadcrumb-item>
        <template v-if="!isDashboard">
          <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">
            {{ item }}
          </el-breadcrumb-item>
        </template>
      </el-breadcrumb>

      <span v-if="mobile" class="navbar-mobile-title">
        {{ breadcrumbs[breadcrumbs.length - 1] || '工作台' }}
      </span>
    </div>

    <div class="navbar-right">
      <el-tag v-if="!mobile" type="info" effect="plain" size="small">
        {{ userStore.primaryRole }}
      </el-tag>
      <el-dropdown trigger="click" @command="handleCommand">
        <span class="navbar-user">
          <el-avatar :size="30" class="navbar-avatar">{{ userInitial }}</el-avatar>
          <span v-if="!mobile" class="navbar-user-name">{{ userStore.displayName }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.navbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 16px;
  background-color: #ffffff;
  box-shadow: 0 1px 4px rgba(16, 42, 82, 0.08);
}

.navbar-left,
.navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.navbar-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: #4a5568;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 6px;
}

.navbar-icon-button:hover {
  background-color: #f2f4f7;
}

.navbar-breadcrumb {
  font-size: 14px;
}

.breadcrumb-home {
  margin-right: 4px;
  vertical-align: -2px;
}

.navbar-mobile-title {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navbar-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px;
  cursor: pointer;
  border-radius: 6px;
}

.navbar-user:hover {
  background-color: #f2f4f7;
}

.navbar-avatar {
  color: #ffffff;
  background: linear-gradient(135deg, #2f6fed, #16a6a6);
}

.navbar-user-name {
  max-width: 120px;
  overflow: hidden;
  font-size: 14px;
  color: #1f2d3d;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .navbar {
    padding: 0 10px;
  }

  .navbar-right {
    gap: 8px;
  }
}
</style>
