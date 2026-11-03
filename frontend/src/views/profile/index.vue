<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getMeApi } from '@/api/auth'
import { accountApi } from '@/api/system'
import { useUserStore } from '@/stores/user'
import { displayRoleName } from '@/utils/roles'
import { resetMenuRoutes } from '@/router'

const router = useRouter()
const userStore = useUserStore()

const profile = computed(() => ({
  username: userStore.user?.username || '-',
  realName: userStore.user?.realName || '-',
  deptName: userStore.user?.deptName || '-',
  roles: userStore.roles.map((role) => displayRoleName(role)).join('、') || '-',
  mobile: userStore.user?.mobile || '-',
  email: userStore.user?.email || '-'
}))

/* ------------------------------ 修改资料 ------------------------------ */
const profileDialogVisible = ref(false)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({ realName: '', mobile: '', email: '' })
const profileRules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  mobile: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}

function openProfileDialog(): void {
  profileForm.realName = userStore.user?.realName || ''
  profileForm.mobile = userStore.user?.mobile || ''
  profileForm.email = userStore.user?.email || ''
  profileDialogVisible.value = true
  profileFormRef.value?.clearValidate()
}

async function submitProfile(): Promise<void> {
  if (!profileFormRef.value) return
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return
  await accountApi.updateProfile({
    realName: profileForm.realName,
    mobile: profileForm.mobile,
    email: profileForm.email
  })
  try {
    const me = await getMeApi()
    if (me.user) userStore.applyUserProfile(me.user)
  } catch {
    if (userStore.user) {
      userStore.applyUserProfile({
        ...userStore.user,
        realName: profileForm.realName,
        mobile: profileForm.mobile,
        email: profileForm.email
      })
    }
  }
  ElMessage.success('个人资料已更新')
  profileDialogVisible.value = false
}

/* ------------------------------ 修改密码 ------------------------------ */
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度需为 8-20 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '密码需同时包含字母与数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

async function submitPassword(): Promise<void> {
  if (!passwordFormRef.value) return
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  await accountApi.changePassword(passwordForm.oldPassword, passwordForm.newPassword)
  ElMessage.success('密码修改成功，请使用新密码重新登录')
  await userStore.logout()
  resetMenuRoutes()
  await router.replace('/login')
}
</script>

<template>
  <div class="page-container">
    <div class="page-card profile-header">
      <el-avatar :size="64" class="profile-avatar">{{ profile.realName.slice(0, 1) }}</el-avatar>
      <div class="profile-meta">
        <div class="profile-name">
          {{ profile.realName }}
          <el-tag v-if="userStore.primaryRole" size="small" type="primary" effect="plain">
            {{ userStore.primaryRole }}
          </el-tag>
        </div>
        <div class="profile-sub">账号：{{ profile.username }} · 部门：{{ profile.deptName }}</div>
        <div class="profile-sub">角色：{{ profile.roles }}</div>
      </div>
      <div class="profile-header-actions">
        <el-button @click="openProfileDialog">编辑资料</el-button>
      </div>
    </div>

    <div class="profile-grid">
      <div class="page-card">
        <div class="card-title">账号信息</div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="登录账号">{{ profile.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ profile.realName }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ profile.deptName }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ profile.roles }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile.mobile }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ profile.email }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="page-card">
        <div class="card-title">修改密码</div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="为保障账号安全，修改密码后需要重新登录"
          class="password-tip"
        />
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="110px">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitPassword">保存新密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-dialog v-model="profileDialogVisible" title="编辑个人资料" width="480px" destroy-on-close>
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px">
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="profileForm.realName" maxlength="30" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="profileForm.mobile" maxlength="11" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" maxlength="60" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-header {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

.profile-avatar {
  color: #fff;
  font-size: 24px;
  background: #2f6fed;
}

.profile-meta {
  flex: 1;
  min-width: 0;
}

.profile-name {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

.profile-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7686;
}

.profile-header-actions {
  flex-shrink: 0;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.card-title {
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.password-tip {
  margin-bottom: 14px;
}

@media (max-width: 1000px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
