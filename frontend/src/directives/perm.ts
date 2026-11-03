import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'
import { hasPermission } from '@/utils/permission'

/**
 * 按钮权限指令：v-perm="'system:user:add'" 或 v-perm="['a:b:c','d:e:f']"（需同时具备）
 * 无权限时不渲染该元素（安全兜底仍由后端接口校验）。
 */
function removeElement(el: HTMLElement): void {
  el.parentNode?.removeChild(el)
}

export const perm: Directive<HTMLElement, string | string[]> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const userStore = useUserStore()
    const required = binding.value
    if (!required) return
    const allowed = hasPermission(userStore.permissions, required)
    if (!allowed) {
      removeElement(el)
    }
  }
}

export default perm
