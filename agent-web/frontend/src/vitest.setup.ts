import '@testing-library/jest-dom';
import 'vitest-axe/extend-expect';

/**
 * jsdom 没有 EventSource（rewrite-permission-mode-dsh T10.2）。
 *
 * ChatPanel 现在依赖 useSettingsStore → subscribeSettingsEvents → new EventSource(url)，
 * jsdom 环境下会抛 ReferenceError。这里提供一个最小 no-op stub：
 * 只记录监听器、永不触发事件（测试不依赖 SSE 推送）。
 */
if (typeof globalThis.EventSource === 'undefined') {
  class NoopEventSource {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSED = 2;

    readonly url: string;
    readonly withCredentials = false;
    readyState = 0;
    onerror: ((ev: Event) => void) | null = null;
    onmessage: ((ev: MessageEvent) => void) | null = null;
    onopen: ((ev: Event) => void) | null = null;

    constructor(url: string | URL) {
      this.url = String(url);
      this.readyState = NoopEventSource.CONNECTING;
    }

    addEventListener(): void {
      /* no-op */
    }

    removeEventListener(): void {
      /* no-op */
    }

    close(): void {
      this.readyState = NoopEventSource.CLOSED;
    }

    dispatchEvent(): boolean {
      return true;
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).EventSource = NoopEventSource;
}
