import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端未配置 CORS，所有 /api、/actuator 请求必须经 dev proxy 转发到 8088
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8088', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8088', changeOrigin: true },
    },
  },
})
