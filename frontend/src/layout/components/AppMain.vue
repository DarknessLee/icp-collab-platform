<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <main class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="fade-slide" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </main>
</template>

<style scoped>
.app-main {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  background-color: #f2f4f7;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

@media (max-width: 767px) {
  .app-main {
    padding: 10px;
  }
}
</style>
