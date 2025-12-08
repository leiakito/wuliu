import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    allowedHosts: [
      '268258sh1ac9.vicp.fun',
      'localhost',
      '.localhost'
    ],
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  build: {
    // 生产构建去掉 console/debugger，减少体积与运行时开销
    esbuild: {
      drop: ['console', 'debugger']
    }
  }
});
