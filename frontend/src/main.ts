import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router, { registerMenuRoutes } from './router'
import { perm } from './directives/perm'
import { useUserStore } from './stores/user'
import './styles/index.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(ElementPlus, { locale: zhCn, size: 'default' })
app.directive('perm', perm)

// 刷新页面时按已恢复的登录态重建动态路由，必须早于 router 安装（首次导航据此匹配）
const userStore = useUserStore(pinia)
registerMenuRoutes(userStore.menuTree)

app.use(router)
app.mount('#app')
