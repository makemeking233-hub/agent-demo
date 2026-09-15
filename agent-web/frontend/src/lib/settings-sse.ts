/**
 * settings SSE 订阅封装 (add-settings-foundation M1).
 *
 * 用原生 EventSource；变更时回调 onChange。断线后自动重连（线性退避，最大 30s）。
 */

import { SettingsApi } from '../api/settings';

export interface SettingsEvent {
  type: 'settings.changed';
  data: unknown;
}

export type SettingsEventHandler = (event: SettingsEvent) => void;

export interface SettingsSseHandle {
  close(): void;
}

export function subscribeSettingsEvents(onEvent: SettingsEventHandler): SettingsSseHandle {
  const url = new SettingsApi().eventsUrl();
  let es: EventSource | null = null;
  let retryDelay = 1000;
  let closed = false;

  function connect() {
    if (closed) return;
    es = new EventSource(url);
    es.addEventListener('settings.changed', (ev: MessageEvent) => {
      try {
        const data = ev.data ? JSON.parse(ev.data) : null;
        onEvent({ type: 'settings.changed', data });
      } catch {
        /* ignore malformed */
      }
    });
    es.onerror = () => {
      // 浏览器会自动重连；这里增加退避逻辑以避免快速重试
      es?.close();
      es = null;
      if (closed) return;
      setTimeout(connect, retryDelay);
      retryDelay = Math.min(retryDelay * 2, 30000);
    };
    es.onopen = () => {
      retryDelay = 1000;
    };
  }

  connect();

  return {
    close() {
      closed = true;
      es?.close();
      es = null;
    },
  };
}
