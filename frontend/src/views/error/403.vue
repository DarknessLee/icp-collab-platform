<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <el-result
      icon="warning"
      title="403"
      sub-title="抱歉，您当前角色没有访问该页面的权限，请联系系统管理员授权。"
    >
      <template #extra>
        <el-button type="primary" @click="router.replace('/dashboard')">返回工作台</el-button>
        <el-button @click="router.back()">返回上一页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: #f2f4f7;
}
</style>
