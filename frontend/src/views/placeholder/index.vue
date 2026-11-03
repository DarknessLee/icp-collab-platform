<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, Download, Edit, Grid, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { MODULE_DESCRIPTIONS, MODULE_PLANS } from '@/config/modules'
import { findMenuChain, permPrefixFromPath } from '@/utils/menu'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const title = computed(() => (route.meta.title as string | undefined) || '功能模块')
const menuPath = computed(
  () => (route.meta.menuPath as string | undefined) || route.path
)
const description = computed(
  () =>
    MODULE_DESCRIPTIONS[menuPath.value] ||
    '该模块将在 M2 阶段按 docs/03 接口契约接入真实业务数据。'
)
const chain = computed(() => findMenuChain(userStore.menuTree, menuPath.value))
const group = computed(() => (chain.value.length > 1 ? chain.value[0].name : ''))
const plans = computed(() => MODULE_PLANS[group.value] || [])
const permPrefix = computed(() => permPrefixFromPath(menuPath.value))

function handleAction(action: string): void {
  ElMessage.info(`「${action}」操作将在 M2 阶段接入后端接口`)
}
</script>

<template>
  <div class="page-card placeholder-page">
    <div class="placeholder-head">
      <div class="placeholder-icon">
        <el-icon :size="26"><Grid /></el-icon>
      </div>
      <div class="placeholder-text">
        <div class="placeholder-title-row">
          <h2 class="placeholder-title">{{ title }}</h2>
          <el-tag type="warning" effect="plain" size="small">M1 骨架占位</el-tag>
          <el-tag v-if="group" type="info" effect="plain" size="small">{{ group }}</el-tag>
        </div>
        <p class="placeholder-desc">{{ description }}</p>
      </div>
    </div>

    <el-divider />

    <section v-if="plans.length" class="placeholder-section">
      <h3 class="section-title">M2 规划能力</h3>
      <ul class="plan-list">
        <li v-for="plan in plans" :key="plan">
          <el-icon><CircleCheck /></el-icon>
          <span>{{ plan }}</span>
        </li>
      </ul>
    </section>

    <div class="placeholder-actions">
      <el-button @click="router.push('/dashboard')">返回工作台</el-button>
      <el-button
        v-perm="permPrefix + ':add'"
        type="primary"
        :icon="Plus"
        @click="handleAction('新增')"
      >
        新增
      </el-button>
      <el-button v-perm="permPrefix + ':edit'" :icon="Edit" @click="handleAction('编辑')">
        编辑
      </el-button>
      <el-button
        v-perm="permPrefix + ':export'"
        :icon="Download"
        @click="handleAction('导出')"
      >
        导出
      </el-button>
    </div>

    <p class="perm-hint">
      按钮由 v-perm 指令按当前登录用户的 permissions 控制显示（示例前缀 {{ permPrefix }}）；M1
      仅做前端隐藏，接口鉴权以后端为准。
    </p>
  </div>
</template>

<style scoped>
.placeholder-page {
  min-height: calc(100vh - 60px - 40px - 48px);
}

.placeholder-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.placeholder-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  color: #2f6fed;
  background-color: #eaf1ff;
  border-radius: 12px;
}

.placeholder-text {
  min-width: 0;
}

.placeholder-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.placeholder-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}

.placeholder-desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 22px;
  color: #5d7599;
}

.placeholder-section {
  margin-bottom: 24px;
}

.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2d3d;
}

.plan-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 24px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.plan-list li {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 22px;
  color: #4a5568;
}

.plan-list .el-icon {
  margin-top: 4px;
  color: #16a6a6;
  flex-shrink: 0;
}

.placeholder-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 8px;
}

.perm-hint {
  margin: 18px 0 0;
  font-size: 12px;
  color: #a3adbd;
}

@media (max-width: 767px) {
  .plan-list {
    grid-template-columns: 1fr;
  }
}
</style>
