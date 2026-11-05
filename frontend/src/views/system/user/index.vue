<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import { deptApi, onlineApi, postApi, roleApi, userApi } from '@/api/system'
import { usePageList } from '@/composables/usePageList'
import { useUserStore } from '@/stores/user'
import type { DeptNode, OnlineSession, PostRow, RoleRow, UserPayload, UserRow } from '@/types/system'
import { maskEmail, maskMobile } from '@/utils/mask'

const userStore = useUserStore()
const { query, list, total, loading, load, search, reset, handlePageChange, handleSizeChange } =
  usePageList<UserRow>(userApi.list, { keyword: '', status: '', deptId: '' })

function can(perm: string): boolean {
  return userStore.hasPermission(perm)
}

const deptTree = ref<DeptNode[]>([])
const postOptions = ref<PostRow[]>([])
const roleOptions = ref<RoleRow[]>([])
const treeProps = { label: 'name', children: 'children' }

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<UserPayload>({
  username: '',
  realName: '',
  deptId: null,
  postId: null,
  mobile: '',
  email: '',
  password: '',
  roleIds: [],
  status: 1,
  remark: ''
})
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }]
}

const resetVisible = ref(false)
const resetTarget = ref<UserRow | null>(null)
const resetFormData = reactive({ password: '' })
const resetFormRef = ref<FormInstance>()
const resetRules: FormRules = {
  password: [{ required: true, message: '请输入新密码', trigger: 'blur' }]
}

const onlineVisible = ref(false)
const onlineLoading = ref(false)
const onlineSessions = ref<OnlineSession[]>([])

async function loadOptions(): Promise<void> {
  const [deptData, postData, roleData] = await Promise.all([
    deptApi.tree(),
    postApi.list({ pageNum: 1, pageSize: 100 }),
    roleApi.list({ pageNum: 1, pageSize: 100 })
  ])
  deptTree.value = deptData || []
  postOptions.value = postData.list
  roleOptions.value = roleData.list
}

function roleNames(row: UserRow): string {
  if (Array.isArray(row.roleNames) && row.roleNames.length) return row.roleNames.join('、')
  if (Array.isArray(row.roles) && row.roles.length) {
    const names = row.roles
      .map((item) => (typeof item === 'object' ? item.name : String(item)))
      .filter(Boolean)
    return names.join('、')
  }
  if (Array.isArray(row.roleIds) && row.roleIds.length) {
    return row.roleIds
      .map((id) => roleOptions.value.find((role) => String(role.id) === String(id))?.name)
      .filter(Boolean)
      .join('、')
  }
  return '-'
}

function resetForm(): void {
  form.username = ''
  form.realName = ''
  form.deptId = null
  form.postId = null
  form.mobile = ''
  form.email = ''
  form.password = ''
  form.roleIds = []
  form.status = 1
  form.remark = ''
  formRef.value?.clearValidate()
}

function openCreate(): void {
  editingId.value = null
  dialogTitle.value = '新增用户'
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: UserRow): Promise<void> {
  editingId.value = row.id
  dialogTitle.value = `编辑用户 - ${row.username}`
  resetForm()
  form.username = row.username
  form.realName = row.realName
  form.deptId = row.deptId ?? null
  form.postId = row.postId ?? null
  form.mobile = row.mobile || ''
  form.email = row.email || ''
  form.status = row.status ?? 1
  form.remark = row.remark || ''
  form.roleIds = Array.isArray(row.roleIds) ? [...row.roleIds] : []
  dialogVisible.value = true
  try {
    const detail = await userApi.detail(row.id)
    if (detail) {
      form.deptId = detail.deptId ?? form.deptId
      form.postId = detail.postId ?? form.postId
      form.roleIds = Array.isArray(detail.roleIds) ? [...detail.roleIds] : form.roleIds
      form.mobile = detail.mobile ?? form.mobile
      form.email = detail.email ?? form.email
    }
  } catch {
    // 详情接口失败不阻塞编辑（使用列表行数据）
  }
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: UserPayload = {
    realName: form.realName,
    deptId: form.deptId ?? null,
    postId: form.postId ?? null,
    mobile: form.mobile,
    email: form.email,
    roleIds: form.roleIds,
    status: Number(form.status) as 0 | 1,
    remark: form.remark
  }
  try {
    if (editingId.value === null) {
      await userApi.create({ ...payload, username: form.username, password: form.password })
      ElMessage.success('用户新增成功')
    } else {
      await userApi.update(editingId.value, payload)
      ElMessage.success('用户修改成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function changeStatus(row: UserRow, nextStatus: number): Promise<void> {
  const actionText = nextStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${actionText}用户「${row.realName || row.username}」？`, `${actionText}用户`, {
      type: 'warning',
      confirmButtonText: `确认${actionText}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await userApi.updateStatus(row.id, nextStatus)
    ElMessage.success(`用户已${actionText}`)
    await load()
  } catch {
    await load()
  }
}

function openResetPassword(row: UserRow): void {
  resetTarget.value = row
  resetFormData.password = ''
  resetVisible.value = true
  resetFormRef.value?.clearValidate()
}

function generatePassword(): void {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
  let value = 'Icp@'
  for (let index = 0; index < 6; index += 1) {
    value += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  resetFormData.password = value
}

async function submitResetPassword(): Promise<void> {
  if (!resetFormRef.value || !resetTarget.value) return
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return
  const password = resetFormData.password
  try {
    await userApi.resetPassword(resetTarget.value.id, password)
  } catch {
    return
  }
  resetVisible.value = false
  ElMessage.success('密码重置成功，请将一次性密码告知用户')
  await ElMessageBox.alert(
    `用户「${resetTarget.value.username}」的新密码为：${password}（仅本次展示，请及时转交并提醒修改）`,
    '密码重置结果',
    { confirmButtonText: '我已记录' }
  ).catch(() => undefined)
}

async function removeRow(row: UserRow): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除用户「${row.realName || row.username}」后不可恢复，确认删除？`,
      '删除用户',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await userApi.remove(row.id)
    ElMessage.success('用户删除成功')
    await load()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

async function openOnline(): Promise<void> {
  onlineVisible.value = true
  await loadOnline()
}

async function loadOnline(): Promise<void> {
  onlineLoading.value = true
  try {
    const page = await onlineApi.list({ pageNum: 1, pageSize: 100 })
    onlineSessions.value = page.list
  } catch {
    // 失败原因由 request.ts 统一提示
  } finally {
    onlineLoading.value = false
  }
}

async function kickSession(session: OnlineSession): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `强制下线后用户「${session.username}」需重新登录，确认继续？`,
      '强制下线',
      { type: 'warning', confirmButtonText: '强制下线', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await onlineApi.kick(session.jti)
    ElMessage.success('该会话已强制下线')
    await loadOnline()
  } catch {
    // 失败原因由 request.ts 统一提示
  }
}

onMounted(async () => {
  await loadOptions().catch(() => undefined)
  await load()
})
</script>

<template>
  <div class="page-container">
    <div class="page-card">
      <div class="page-toolbar">
        <div class="page-toolbar-title">
          <span class="page-toolbar-heading">用户管理</span>
          <span class="page-toolbar-desc">维护账号、部门岗位、角色与登录状态</span>
        </div>
        <div class="page-toolbar-actions">
          <el-tree-select
            v-model="query.deptId"
            class="toolbar-tree"
            :data="deptTree"
            :props="treeProps"
            node-key="id"
            check-strictly
            clearable
            placeholder="所属部门"
            @change="search"
          />
          <el-input
            v-model="query.keyword"
            class="toolbar-input"
            placeholder="用户名 / 姓名 / 手机号"
            clearable
            @keyup.enter="search"
            @clear="search"
          />
          <el-select v-model="query.status" class="toolbar-select" placeholder="状态" clearable @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Plus" v-perm="'system:user:add'" @click="openCreate">新增用户</el-button>
          <el-button :icon="RefreshRight" @click="openOnline">在线会话</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="list" border stripe row-key="id" empty-text="暂无用户数据">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column prop="deptName" label="部门" min-width="140" show-overflow-tooltip />
        <el-table-column prop="postName" label="岗位" min-width="120" show-overflow-tooltip />
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ maskMobile(row.mobile) || '-' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ maskEmail(row.email) || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ roleNames(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="!can('system:user:status')"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="(value: string | number | boolean) => changeStatus(row, value ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-perm="'system:user:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" v-perm="'system:user:reset'" @click="openResetPassword(row)">
              重置密码
            </el-button>
            <el-button link type="danger" v-perm="'system:user:delete'" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
        <el-button text @click="reset">重置筛选</el-button>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="editingId !== null" maxlength="40" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="realName">
              <el-input v-model="form.realName" maxlength="30" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属部门">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                :props="treeProps"
                node-key="id"
                check-strictly
                clearable
                placeholder="选择部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="form.postId" clearable placeholder="选择岗位" style="width: 100%">
                <el-option v-for="post in postOptions" :key="post.id" :label="post.name" :value="post.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="form.mobile" maxlength="20" placeholder="手机号（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" maxlength="60" placeholder="邮箱（选填）" />
            </el-form-item>
          </el-col>
          <el-col v-if="editingId === null" :span="12">
            <el-form-item label="初始密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色">
              <el-select v-model="form.roleIds" multiple collapse-tags placeholder="选择角色" style="width: 100%">
                <el-option v-for="role in roleOptions" :key="role.id" :label="role.name" :value="role.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="440px" destroy-on-close>
      <el-form ref="resetFormRef" :model="resetFormData" :rules="resetRules" label-width="80px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetFormData.password" maxlength="40" show-password placeholder="输入或生成新密码" />
        </el-form-item>
      </el-form>
      <el-button text type="primary" @click="generatePassword">生成随机强密码</el-button>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPassword">确认重置</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="onlineVisible" title="在线会话" size="720px" destroy-on-close>
      <div class="drawer-toolbar">
        <span class="drawer-desc">展示当前登录会话，可强制下线异常会话（权限 system:user:reset）</span>
        <el-button text :loading="onlineLoading" @click="loadOnline">刷新</el-button>
      </div>
      <el-table v-loading="onlineLoading" :data="onlineSessions" border stripe empty-text="暂无在线会话">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="ip" label="登录 IP" width="140" />
        <el-table-column prop="loginTime" label="登录时间" width="165" />
        <el-table-column prop="lastActive" label="最近活动" width="165" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" v-perm="'system:user:reset'" @click="kickSession(row)">强制下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
}

.page-toolbar-heading {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.page-toolbar-desc {
  font-size: 12px;
  color: #8a94a6;
}

.page-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.toolbar-input {
  width: 190px;
}

.toolbar-select {
  width: 105px;
}

.toolbar-tree {
  width: 150px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.drawer-desc {
  font-size: 13px;
  color: #606a7b;
}
</style>
