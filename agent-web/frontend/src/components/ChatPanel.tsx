import { useEffect, useRef, useState } from 'react';
import { ChatApi } from '../api/chat';
import { SseClient } from '../lib/sse-client';
import { SseEvent } from '../lib/event-types';
import { MessageBubble } from './MessageBubble';
import { ToolCallCard } from './ToolCallCard';
import { PermissionCard } from './PermissionCard';

type Item =
  | { kind: 'text'; id: string; role: 'user' | 'assistant'; text: string }
  | { kind: 'tool'; id: string; name: string; toolCallId: string; status: 'running' | 'ok' | 'fail'; text?: string; durationMs?: number }
  | { kind: 'perm'; id: string; toolName: string; reason: string; permissionId: string; choices: ('yes' | 'no' | 'always')[]; toolCallId: string };

export function ChatPanel() {
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [items, setItems] = useState<Item[]>([]);
  const [streamId, setStreamId] = useState<string | null>(null);
  const clientRef = useRef<SseClient | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [items]);

  const api = new ChatApi();

  function appendItem(it: Item) {
    setItems((prev) => [...prev, it]);
  }
  function updateItem(id: string, patch: Partial<Item>) {
    setItems((prev) => prev.map((it) => (it.id === id ? { ...it, ...patch } as Item : it)));
  }

  async function startStream(content: string) {
    if (busy) return;
    setBusy(true);
    appendItem({ kind: 'text', id: 'u-' + Date.now(), role: 'user', text: content });
    try {
      const resp = await api.send({ content });
      setStreamId(resp.streamId);
      appendItem({ kind: 'text', id: 'a-' + Date.now(), role: 'assistant', text: '' });
      const client = new SseClient({
        url: api.streamUrl(resp.streamId),
        onEvent: (ev: SseEvent) => handleEvent(ev, resp.streamId),
        onComplete: () => setBusy(false),
        onError: () => setBusy(false),
      });
      clientRef.current = client;
      client.start();
    } catch (e) {
      setBusy(false);
      appendItem({ kind: 'text', id: 'e-' + Date.now(), role: 'assistant', text: 'err: ' + (e as Error).message });
    }
  }

  function handleEvent(ev: SseEvent, sid: string) {
    const last = items[items.length - 1];
    if (ev.type === 'message_delta' && ev.delta_type === 'text' && last?.kind === 'text' && last.role === 'assistant') {
      updateItem(last.id, { text: (last as any).text + ev.content });
    } else if (ev.type === 'message_stop') {
      setBusy(false);
      setStreamId(null);
    } else if (ev.type === 'tool_call_start') {
      appendItem({ kind: 'tool', id: ev.tool_call_id, name: ev.name, toolCallId: ev.tool_call_id, status: 'running' });
    } else if (ev.type === 'tool_call_end') {
      const text = typeof ev.result === 'string' ? ev.result : JSON.stringify(ev.result);
      updateItem(ev.tool_call_id, { status: ev.ok ? 'ok' : 'fail', text, durationMs: ev.duration_ms });
    } else if (ev.type === 'permission_request') {
      appendItem({ kind: 'perm', id: ev.permission_id, toolName: ev.tool_name, reason: ev.reason, permissionId: ev.permission_id, choices: ev.choices, toolCallId: ev.tool_call_id });
    }
  }

  async function submitPermission(permissionId: string, decision: 'yes' | 'no' | 'always', itemId: string) {
    const sid = streamId;
    if (!sid) return;
    await api.submitDecision(sid, permissionId, decision);
    updateItem(itemId, { choices: [] } as any); // 视觉上隐藏按钮
  }

  async function abortStream() {
    if (streamId) await api.abort(streamId);
  }

  async function submit() {
    const content = input.trim();
    if (!content) return;
    setInput('');
    await startStream(content);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <div ref={listRef} style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
        {items.map((it) => {
          if (it.kind === 'text') return <MessageBubble key={it.id} role={it.role} text={it.text} />;
          if (it.kind === 'tool') return <ToolCallCard key={it.id} name={it.name} status={it.status} text={it.text} durationMs={it.durationMs} />;
          if (it.kind === 'perm') return <PermissionCard key={it.id} toolName={it.toolName} reason={it.reason} choices={it.choices} onChoose={(d) => submitPermission(it.permissionId, d, it.id)} />;
          return null;
        })}
      </div>
      <div style={{ display: 'flex', gap: 8, padding: 12, borderTop: '1px solid #ddd' }}>
        <input
          style={{ flex: 1, padding: 8, fontSize: 14 }}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) submit(); }}
          placeholder="输入消息或 /help..."
          disabled={busy}
        />
        {busy ? (
          <button onClick={abortStream}>Abort</button>
        ) : (
          <button onClick={submit} disabled={!input.trim()}>Send</button>
        )}
      </div>
    </div>
  );
}
