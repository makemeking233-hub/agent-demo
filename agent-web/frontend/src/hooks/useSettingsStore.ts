/**
 * settings store hook (add-settings-foundation M1).
 *
 * 模块级单例 + useSyncExternalStore，避免引 Zustand。
 * 初次调用时 GET /api/settings 初始化 + 订阅 SSE。
 */

import { useSyncExternalStore } from 'react';
import { SettingsApi, SettingsError, SettingsView } from '../api/settings';
import { subscribeSettingsEvents } from '../lib/settings-sse';

export type SettingsStatus = 'idle' | 'loading' | 'ready' | 'error';

interface SettingsStore {
  snapshot: SettingsView | null;
  status: SettingsStatus;
  error: SettingsError | null;
  /** PATCH 单字段；返回 Promise<SettingsView> */
  patch: (path: string, value: unknown, revision?: number) => Promise<SettingsView>;
  /** 强制重新拉取 */
  refresh: () => Promise<void>;
}

let store: SettingsStore | null = null;
const listeners = new Set<() => void>();
const api = new SettingsApi();

function emit() {
  for (const l of listeners) l();
}

function createStore(): SettingsStore {
  let snapshot: SettingsView | null = null;
  let status: SettingsStatus = 'idle';
  let error: SettingsError | null = null;
  let initPromise: Promise<void> | null = null;

  async function init() {
    if (initPromise) return initPromise;
    status = 'loading';
    emit();
    initPromise = (async () => {
      try {
        snapshot = await api.getSettings();
        status = 'ready';
        error = null;
      } catch (e) {
        status = 'error';
        error = (e as SettingsError) ?? { error: 'unknown', status: 0 };
      }
      emit();
      // 订阅 SSE；变更触发 refresh
      subscribeSettingsEvents(async () => {
        try {
          snapshot = await api.getSettings();
          status = 'ready';
          error = null;
          emit();
        } catch {
          /* 静默失败 */
        }
      });
    })();
    return initPromise;
  }

  async function patch(path: string, value: unknown, revision?: number): Promise<SettingsView> {
    try {
      const view = await api.patch(path, value, revision);
      snapshot = view;
      status = 'ready';
      error = null;
      emit();
      return view;
    } catch (e) {
      const err = e as SettingsError;
      error = err;
      // 409 冲突：自动 refresh 后重试一次
      if (err.status === 409) {
        try {
          snapshot = await api.getSettings();
          emit();
          if (snapshot) {
            return await api.patch(path, value, snapshot.revision);
          }
        } catch {
          /* fall through */
        }
      }
      emit();
      throw err;
    }
  }

  async function refresh() {
    try {
      snapshot = await api.getSettings();
      status = 'ready';
      error = null;
    } catch (e) {
      error = (e as SettingsError) ?? { error: 'unknown', status: 0 };
    }
    emit();
  }

  // 启动初始化（fire-and-forget；组件首次读取 snapshot 时已能看到 loading 状态）
  init();

  return {
    get snapshot() { return snapshot; },
    get status() { return status; },
    get error() { return error; },
    patch,
    refresh,
  };
}

function getStore(): SettingsStore {
  if (!store) store = createStore();
  return store;
}

/** 选择 store 的某个字段；变更时自动 re-render */
export function useSettingsStore<T>(selector: (s: SettingsStore) => T): T {
  const s = getStore();
  return useSyncExternalStore(
    cb => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    },
    () => selector(s),
  );
}

/** 测试钩子：替换 store（仅测试用） */
export function __resetSettingsStoreForTest(): void {
  store = null;
}
