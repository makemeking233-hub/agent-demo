import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// frontend-maven-plugin: mvn package 阶段跑 `npm run build` 调到这里.
// 产物输出到 agent-web/src/main/resources/static/, 让 Spring Boot 直接托管.
// 开发期: `npm run dev` 起 vite dev server (proxy /api 到 http://localhost:8090).

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // 后端 web 端口 (application-web.yml 的 server.port); 与后端对齐
        target: 'http://localhost:8090',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  // vitest 配置: T10.3 前端单测 (jsdom + jest-dom)
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/vitest.setup.ts',
    include: ['**/*.{test,spec}.{ts,tsx}'],
  },
});
