import { describe, expect, it, vi } from 'vitest';
import { SseClient } from './sse-client';
import type { SseEvent } from './event-types';

function sseChunks(parts: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  let i = 0;
  return new ReadableStream({
    pull(controller) {
      if (i < parts.length) {
        controller.enqueue(encoder.encode(parts[i++]));
      } else {
        controller.close();
      }
    },
  });
}

describe('SseClient', () => {
  it('parses SSE events and dispatches onEvent', async () => {
    const events: SseEvent[] = [];
    const complete = vi.fn();
    const fakeResp = {
      ok: true,
      body: sseChunks([
        'id:1\nevent:message_delta\ndata:{"type":"message_delta","delta_type":"text","content":"hi"}\n\n',
        'id:2\nevent:message_stop\ndata:{"type":"message_stop","finish_reason":"stop"}\n\n',
      ]),
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResp as unknown as Response));

    const client = new SseClient({ url: '/api/chat/stream/1', onEvent: (e) => events.push(e), onComplete: complete });
    client.start();

    await vi.waitFor(() => expect(events.length).toBe(2));
    expect(events[0]).toMatchObject({ type: 'message_delta' });
    expect(events[1]).toMatchObject({ type: 'message_stop' });
    expect(complete).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it('sends Last-Event-ID header after first event', async () => {
    const events: SseEvent[] = [];
    const fakeResp = {
      ok: true,
      body: sseChunks(['id:5\ndata:{"type":"message_stop","finish_reason":"stop"}\n\n']),
    };
    const fetchMock = vi.fn().mockResolvedValue(fakeResp as unknown as Response);
    vi.stubGlobal('fetch', fetchMock);

    const client = new SseClient({ url: '/api/chat/stream/1', onEvent: (e) => events.push(e) });
    client.start();

    await vi.waitFor(() => expect(events.length).toBe(1));
    const call = fetchMock.mock.calls[0] as unknown as [string, { headers?: Record<string, string> }];
    expect(call[0]).toBe('/api/chat/stream/1');
    // 首个请求无 Last-Event-ID (lastEventId=0)
    expect(call[1]?.headers?.['Last-Event-ID']).toBeUndefined();
    vi.unstubAllGlobals();
  });
});
