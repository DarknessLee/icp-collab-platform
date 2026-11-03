import { defineStore } from 'pinia'
import type { TagView } from '@/types'

const HOME_TAG: TagView = { path: '/dashboard', title: '工作台', affix: true }

interface AppState {
  sidebarCollapsed: boolean
  mobileMenuOpen: boolean
  tags: TagView[]
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    sidebarCollapsed: false,
    mobileMenuOpen: false,
    tags: [{ ...HOME_TAG }]
  }),

  actions: {
    toggleSidebar(): void {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    setSidebar(collapsed: boolean): void {
      this.sidebarCollapsed = collapsed
    },
    toggleMobileMenu(open?: boolean): void {
      this.mobileMenuOpen = typeof open === 'boolean' ? open : !this.mobileMenuOpen
    },
    addTag(tag: TagView): void {
      if (!tag.path) return
      const existing = this.tags.find((item) => item.path === tag.path)
      if (existing) {
        if (tag.title) existing.title = tag.title
        return
      }
      this.tags.push({ path: tag.path, title: tag.title })
    },
    removeTag(path: string): void {
      this.tags = this.tags.filter((item) => item.path !== path || item.affix)
    },
    closeOthers(path: string): void {
      this.tags = this.tags.filter((item) => item.affix || item.path === path)
    },
    resetTags(): void {
      this.tags = [{ ...HOME_TAG }]
    }
  }
})
