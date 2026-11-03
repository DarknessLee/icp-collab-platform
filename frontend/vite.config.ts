import { fileURLToPath, URL } from 'node:url'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 部分 Windows 机器上系统 Temp 目录受杀软/ACL 限制，esbuild 删除临时文件会失败；
// 统一把 esbuild 临时目录重定向到项目内 node_modules/.tmp（已在 .gitignore 忽略）。
if (process.platform === 'win32') {
  const localTemp = join(fileURLToPath(new URL('.', import.meta.url)), 'node_modules', '.tmp')
  try {
    mkdirSync(localTemp, { recursive: true })
    process.env.TEMP = localTemp
    process.env.TMP = localTemp
  } catch {
    // 无法创建时回退系统 Temp，不阻塞构建
  }
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    watch: {
      // 忽略编辑器/工具链产生的临时目录，避免 Windows 上文件监听 EBUSY 导致 dev server 退出
      ignored: ['**/node_modules/**', '**/.git/**', '**/*.tmpdir/**', '**/*.tmp']
    },
    proxy: {
      // 开发环境统一由前端代理转发到后端，避免跨域
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
    strictPort: true
  },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 1600,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          return 'vendor'
        }
      }
    }
  }
})
