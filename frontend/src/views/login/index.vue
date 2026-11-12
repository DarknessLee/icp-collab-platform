<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { CircleCheck, Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { registerMenuRoutes, resetMenuRoutes } from '@/router'
import { APP_NAME, APP_VERSION, CAPTCHA_ENABLED } from '@/config/app'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaEnabled = CAPTCHA_ENABLED
const currentYear = new Date().getFullYear()

const form = reactive({
  username: '',
  password: '',
  captcha: ''
})

const rules = reactive<FormRules>({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为 6 位', trigger: 'blur' }
  ]
})

if (captchaEnabled) {
  rules.captcha = [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function handleLogin(): Promise<void> {
  if (loading.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ username: form.username.trim(), password: form.password })

    // 登录成功后重建动态路由与标签页，保证角色切换不残留旧路由
    resetMenuRoutes()
    registerMenuRoutes(userStore.menuTree)
    appStore.resetTags()
    appStore.addTag({ path: '/dashboard', title: '工作台', affix: true })

    ElMessage.success(`欢迎回来，${userStore.displayName}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const target =
      redirect && redirect.startsWith('/') && redirect !== '/login' ? redirect : '/dashboard'
    await router.replace(target)
  } catch (error) {
    const message = (error as Error)?.message || ''
    // 业务错误已由 axios 拦截器提示；网络异常与响应异常在这里补充提示
    if (message.indexOf('登录响应') >= 0 || message.indexOf('网络') >= 0) {
      ElMessage.error(message)
    }
  } finally {
    loading.value = false
  }
}

</script>

<template>
  <div class="login-page">
    <section class="login-brand">
      <div class="brand-inner">
        <div class="brand-logo">控</div>
        <h1 class="brand-title">{{ APP_NAME }}</h1>
        <p class="brand-sub">制度 · 风险 · 控制 · 评价 · 整改 —— 内控闭环与协同运营一体化</p>

        <ul class="brand-points">
          <li>
            <el-icon><CircleCheck /></el-icon>
            覆盖制度、风险、控制矩阵与内控评价整改全流程
          </li>
          <li>
            <el-icon><CircleCheck /></el-icon>
            审批中心、协同任务、文档公告与日程一站式协同
          </li>
          <li>
            <el-icon><CircleCheck /></el-icon>
            内控看板、风险预警与审计日志全程留痕可追溯
          </li>
        </ul>

        <div class="brand-foot">{{ APP_VERSION }}</div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-head">
          <h2>账号登录</h2>
          <p>请使用企业账号登录内控协同平台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="请输入用户名"
              autocomplete="username"
              clearable
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
            />
          </el-form-item>

          <el-form-item v-if="captchaEnabled" label="验证码" prop="captcha">
            <div class="captcha-row">
              <el-input v-model="form.captcha" placeholder="请输入验证码" maxlength="4" />
              <div class="captcha-box">验证码占位</div>
            </div>
          </el-form-item>

          <el-button
            class="login-button"
            type="primary"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '正在登录…' : '登 录' }}
          </el-button>
        </el-form>

        <div class="login-foot">
          © {{ currentYear }} {{ APP_NAME }} 版权所有
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  width: 100%;
  min-height: 100vh;
  background-color: #f2f4f7;
}

.login-brand {
  position: relative;
  display: flex;
  flex: 1.1;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #ffffff;
  background: linear-gradient(145deg, #0b1c33 0%, #123a63 55%, #145a5a 100%);
}

.login-brand::after {
  position: absolute;
  top: -120px;
  right: -120px;
  width: 420px;
  height: 420px;
  content: '';
  background: radial-gradient(circle, rgba(47, 111, 237, 0.35), transparent 70%);
  border-radius: 50%;
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 520px;
  padding: 48px;
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 24px;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #2f6fed, #16a6a6);
  border-radius: 14px;
}

.brand-title {
  margin: 0 0 12px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-sub {
  margin: 0 0 32px;
  font-size: 15px;
  line-height: 26px;
  color: #a9bcd6;
}

.brand-points {
  padding: 0;
  margin: 0 0 40px;
  list-style: none;
}

.brand-points li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 16px;
  font-size: 14px;
  line-height: 22px;
  color: #cfdced;
}

.brand-points .el-icon {
  margin-top: 3px;
  color: #4fd1c5;
}

.brand-foot {
  font-size: 12px;
  color: #7f93b0;
}

.login-panel {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 36px 36px 24px;
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(16, 42, 82, 0.12);
}

.login-head {
  margin-bottom: 24px;
}

.login-head h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: #1f2d3d;
}

.login-head p {
  margin: 0;
  font-size: 13px;
  color: #8a94a6;
}

.login-button {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  font-size: 15px;
  letter-spacing: 4px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-box {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 110px;
  font-size: 12px;
  color: #8a94a6;
  background-color: #f2f4f7;
  border: 1px dashed #c5ccd6;
  border-radius: 4px;
}

.login-foot {
  margin-top: 20px;
  font-size: 11px;
  color: #a3adbd;
  text-align: center;
}

@media (max-width: 900px) {
  .login-brand {
    display: none;
  }

  .login-panel {
    padding: 20px;
  }

  .login-card {
    padding: 28px 22px 20px;
  }
}
</style>
