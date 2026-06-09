import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],

  // 与后端共用项目根目录 .env（VITE_AMAP_KEY 等）
  envDir: resolve(__dirname, '..'),

  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },

  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      // 开发环境代理到后端
      '/api': {
        target: process.env.VITE_API_BASE_URL || 'http://localhost:8080',
        changeOrigin: true,
        // 如果后端 API 不含 /api 前缀，取消注释下一行
        // rewrite: (path) => path.replace(/^\/api/, '')
      },
    },
  },

  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // 代码分割：按模块拆分
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          'markdown': ['marked', 'dompurify'],
          'amap': ['@amap/amap-jsapi-loader'],
        },
      },
    },
    chunkSizeWarningLimit: 1000,
  },
})
