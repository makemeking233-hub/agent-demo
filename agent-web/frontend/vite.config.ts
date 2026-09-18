import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

// frontend-maven-plugin: mvn package 阶段跑 `npm run build` 调到这里.
// 产物输出到 agent-web/src/main/resources/static/, 让 Spring Boot 直接托管.
// 开发期: `npm run dev` 起 vite dev server (proxy /api 到 http://localhost:18080).
//
// PWA: vite-plugin-pwa 自动生成 Service Worker (Workbox generateSW 模式),
// 运行时缓存策略见 workbox.runtimeCaching 配置（add-pwa-support change）.

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
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
        // 预缓存白名单（add-mermaid-diagrams）。
        //
        // 为什么改成白名单而不是用默认的 `**/*.{js,css,html}` 再 globIgnores 拉黑：
        // mermaid 12 把每种图型拆成按需 chunk，实测一次构建产出 63 个，名字五花八门
        // （chunk / diagram / elk / dagre / cytoscape.esm / arc / graph / *Diagram …），
        // **没有共同前缀**，黑名单会又长又脆且随版本失效。白名单则把"必须离线可用"的东西
        // 显式列出来，安装体积从此有上界、与引了多少按需库无关。
        //
        // 实测代价对照：默认 glob 下预缓存 70 entries / 11609 KiB（每次安装或更新多下约 5MB）；
        // 白名单下回到 7 entries / 约 6530 KiB（与引入 mermaid 之前一致）。
        //
        // 取舍：**新增需要离线可用的顶层资源时，必须往这里加一条**，否则它只会在运行时
        // 按 /assets/ 的 CacheFirst 缓存，离线首次打开会缺。大体积的可选能力（mermaid、
        // 以及后续任何按需库）刻意不进白名单。
        globPatterns: [
          'index.html',
          'assets/index-*.js',
          'assets/index-*.css',
          'assets/katex-*.js',
          'assets/katex-*.css',
          'assets/vosk-*.js',
          'assets/workbox-window*.js',
        ],
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
  resolve: {
    alias: {
      '@': path.resolve(process.cwd(), 'src'),
    },
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
    // add-pwa-support: 排除 Playwright e2e 测试（需单独跑 pnpm playwright test + Chrome 环境）
    exclude: ['**/node_modules/**', 'tests/e2e/**'],
  },
});