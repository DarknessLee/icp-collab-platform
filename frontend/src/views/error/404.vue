<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <el-result
      icon="info"
      title="404"
      sub-title="您访问的页面不存在或已下线，请检查地址是否正确。"
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
