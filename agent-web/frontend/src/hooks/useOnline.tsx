/**
 * 在线状态 Hook + Context（add-pwa-support）。
 *
 * - useOnline(): 返回当前是否在线（boolean）
 * - <OnlineProvider>: 在 App 根节点包裹，提供 Context
 * - listen global fetch errors: 监听 navigator.onLine + window 'offline'/'online' 事件
 *
 * 用法：
 *   <OnlineProvider>
 *     <App />
 *   </OnlineProvider>
 *
 *   function MyComp() {
 *     const isOnline = useOnline();
 *     ...
 *   }
 */

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

interface OnlineContextValue {
  isOnline: boolean;
  retry: () => void;
  /** 递增计数器，每次 retry 触发 +1 */
  retryCount: number;
}

const OnlineContext = createContext<OnlineContextValue | null>(null);

export interface OnlineProviderProps {
  children: ReactNode;
  /** 初始在线状态（默认 navigator.onLine） */
  initial?: boolean;
}

export function OnlineProvider({
  children,
  initial,
}: OnlineProviderProps): JSX.Element {
  const [isOnline, setIsOnline] = useState<boolean>(
    initial ?? (typeof navigator !== "undefined" ? navigator.onLine : true),
  );
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);
    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  const value = useMemo<OnlineContextValue>(
    () => ({
      isOnline,
      retry: () => {
        setRetryCount((c) => c + 1);
        // 触发一次 navigator.onLine 重读（部分浏览器不会自动更新）
        setIsOnline(navigator.onLine);
      },
      retryCount,
    }),
    [isOnline, retryCount],
  );

  return (
    <OnlineContext.Provider value={value}>{children}</OnlineContext.Provider>
  );
}

/** 读取在线状态 + 重试计数器。 */
export function useOnline(): OnlineContextValue {
  const ctx = useContext(OnlineContext);
  if (!ctx) {
    throw new Error("useOnline must be used within <OnlineProvider>");
  }
  return ctx;
}