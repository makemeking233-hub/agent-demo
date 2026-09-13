import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

// frontend-maven-plugin: mvn package 阶段跑 `npm run build` 调到这里.
// 产物输出到 agent-web/src/main/resources/static/, 让 Spring Boot 直接托管.
// 开发期: `npm run dev` 起 vite dev server (proxy /api 到 http://localhost:18080).
//
// PWA: vite-plugin-pwa 自动生成 Service Worker (Workbox generateSW 模式),
// 运行时缓存策略见 workbox.runtimeCaching 配置（add-pwa-support change）.

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // 默认 autoUpdate：新 SW 安装后自动 skipWaiting 接管
      registerType: 'autoUpdate',
      // Workbox 自动生成 sw.js（默认 generateSW 模式）
      strategies: 'generateSW',
      // 自动注入 register 调用（main.tsx 无需手写 navigator.serviceWorker.register）
      injectRegister: 'auto',
      // 从 public/manifest.webmanifest 自动读取（无需在配置里重复）
      manifest: false,
      // PWA dev 行为：开发模式下禁用 SW（避免缓存干扰 HMR）
      devOptions: {
        enabled: false,
      },
      workbox: {
        // SPA fallback：所有 navigate 请求回退到 /index.html
        navigateFallback: '/index.html',
        // 排除 API + 已知动态路径
        navigateFallbackDenylist: [/^\/api\//],
        // 提高 precache 上限（vosk 模型 5.79MB 超过默认 2MB）
        maximumFileSizeToCacheInBytes: 10 * 1024 * 1024,
        runtimeCaching: [
          {
            // 静态资源：永久缓存（带版本号 hash，部署后自动失效）
            urlPattern: /\/assets\//,
            handler: 'CacheFirst',
            options: {
              cacheName: 'static-assets-v1',
              expiration: { maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 }, // 30 天
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // HTML：NetworkFirst（保证拿到新版本），3s 超时回退到缓存
            urlPattern: ({ url }) =>
              url.pathname === '/' || url.pathname === '/index.html',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'html-v1',
              networkTimeoutSeconds: 3,
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            // API：永不缓存（避免 stale token / 路径 / 凭证）
            urlPattern: /\/api\//,
            handler: 'NetworkOnly',
          },
          {
            // Google Fonts（项目未使用，但保留以备）
            urlPattern: /^https:\/\/fonts\.(googleapis|gstatic)\.com\//,
            handler: 'CacheFirst',
            options: {
              cacheName: 'google-fonts-v1',
              expiration: { maxEntries: 30, maxAgeSeconds: 60 * 60 * 24 * 365 }, // 1 年
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
      // 不在 SW 中 include sourcemaps（避免调试信息泄露）
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,png,svg,webmanifest}'],
      },
    }),
  ],
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
        target: 'http://localhost:18080',
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