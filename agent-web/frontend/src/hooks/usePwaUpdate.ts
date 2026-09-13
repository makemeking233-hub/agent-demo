/**
 * PWA 更新检测 Hook（add-pwa-support）。
 *
 * 封装 `virtual:pwa-register/react`，返回 needRefresh / offlineReady 状态 + update() 方法。
 * update() 调用 updateServiceWorker(true) → skipWaiting → 页面自动 reload。
 */

import { useRegisterSW } from "virtual:pwa-register/react";

export interface PwaUpdateState {
  needRefresh: boolean;
  offlineReady: boolean;
  update: () => Promise<void>;
}

export function usePwaUpdate(): PwaUpdateState {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    offlineReady: [offlineReady, setOfflineReady],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_swUrl, _registration) {
      // SW 注册成功；通常无需日志（避免噪音）
    },
    onRegisterError(error) {
      // SW 注册失败；记录到 console.warn，不影响应用启动
      // eslint-disable-next-line no-console
      console.warn("[pwa] service worker registration failed:", error);
    },
  });

  return {
    needRefresh,
    offlineReady,
    update: async () => {
      // skipWaiting: 立即激活新 SW（不等所有 tab 关闭）
      await updateServiceWorker(true);
    },
    // 暴露 setter（用于测试或外部 reset）
    setNeedRefresh,
    setOfflineReady,
  } as PwaUpdateState & {
    setNeedRefresh: (v: boolean) => void;
    setOfflineReady: (v: boolean) => void;
  };
}