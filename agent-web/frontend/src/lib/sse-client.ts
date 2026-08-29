import { SseEvent, SseEventType } from './event-types';

export type SseHandler = (event: SseEvent) => void;

/**
 * 原生 EventSource 包装 + 自动重连 (Last-Event-ID 透传).
 *
 * v0.1 简化: 用 fetch + ReadableStream 而不是 EventSource, 因为 EventSource 不支持
 * 自定义 header (X-Last-Event-ID 透传), 也不容易拿 raw data. v0.2 再优化.
 */

export interface SseClientOptions {
  url: string;
  onEvent: SseHandler;
  onError?: (err: Error) => void;
  onComplete?: () => void;
  abortSignal?: AbortSignal;
}

export class SseClient {
  private controller: AbortController | null = null;
  private lastEventId = 0;
  private retryDelay = 1000;

  constructor(private opts: SseClientOptions) {}

  start(): void {
    this.controller = new AbortController();
    if (this.opts.abortSignal) {
      this.opts.abortSignal.addEventListener('abort', () => this.stop());
    }
    this.connect();
  }

  stop(): void {
    this.controller?.abort();
    this.controller = null;
  }

  private async connect(): Promise<void> {
    if (!this.controller) return;
    const headers: Record<string, string> = {
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
    };
    if (this.lastEventId > 0) {
      headers['Last-Event-ID'] = String(this.lastEventId);
    }
    try {
      const resp = await fetch(this.opts.url, {
        method: 'GET',
        headers,
        signal: this.controller.signal,
      });
      if (!resp.ok || !resp.body) {
        throw new Error(`SSE HTTP ${resp.status}`);
      }
      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const events = this.parse(buffer);
        buffer = events.rest;
        for (const ev of events.complete) {
          try {
            this.opts.onEvent(JSON.parse(ev.data) as SseEvent);
          } catch (e) {
            this.opts.onError?.(e as Error);
          }
          if (ev.id) this.lastEventId = parseInt(ev.id, 10);
        }
      }
      this.opts.onComplete?.();
    } catch (e) {
      if ((e as Error).name === 'AbortError') return;
      this.opts.onError?.(e as Error);
      // 简单线性重连 (v0.1 简化)
      if (this.controller && this.retryDelay < 30000) {
        await new Promise((r) => setTimeout(r, this.retryDelay));
        this.retryDelay = Math.min(this.retryDelay * 2, 30000);
        this.connect();
      }
    }
  }

  private parse(text: string): { complete: { id: string; data: string }[]; rest: string } {
    const events: { id: string; data: string }[] = [];
    const lines = text.split('\n');
    let curId = '';
    let curData: string[] = [];
    let curEvent: SseEventType | null = null;
    for (const line of lines) {
      if (line === '') {
        if (curData.length > 0) {
          events.push({ id: curId, data: curData.join('\n') });
          curId = '';
          curData = [];
          curEvent = null;
        }
        continue;
      }
      if (line.startsWith(':')) continue; // 注释
      const idx = line.indexOf(':');
      const field = idx < 0 ? line : line.substring(0, idx);
      const value = idx < 0 ? '' : line.substring(idx + 1).trim();
      if (field === 'id') curId = value;
      else if (field === 'event') curEvent = value as SseEventType;
      else if (field === 'data') curData.push(value);
    }
    return { complete: events, rest: '' };
  }
}
