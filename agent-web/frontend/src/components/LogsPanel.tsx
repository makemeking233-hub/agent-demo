import { useEffect, useState } from 'react';

type SessionInfo = {
  id: string;
  hasEvents: boolean;
  hasChat: boolean;
  hasTools: boolean;
};

type LogEvent = Record<string, unknown> & { type: string };

const VIEWS = ['events', 'chat', 'tools'] as const;
type View = (typeof VIEWS)[number];

/** 日志查看面板：会话列表 + 事件流（事件/聊天/工具三视图）。 */
export function LogsPanel({ onBack }: { onBack: () => void }) {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [events, setEvents] = useState<LogEvent[]>([]);
  const [view, setView] = useState<View>('events');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/logs/sessions')
      .then((r) => r.json())
      .then((data: SessionInfo[]) => setSessions(data))
      .catch((e) => setError(String(e)));
  }, []);

  async function openSession(id: string) {
    setSelectedId(id);
    setLoading(true);
    setError(null);
    try {
      const resp = await fetch(`/api/logs/sessions/${id}/events?offset=0&limit=1000`);
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      const data = (await resp.json()) as { events: LogEvent[] };
      setEvents(data.events);
    } catch (e) {
      setError(String(e));
      setEvents([]);
    } finally {
      setLoading(false);
    }
  }

  const filtered = events.filter((ev) => {
    if (view === 'chat') return ev.type === 'user/message' || ev.type === 'assistant/message';
    if (view === 'tools') return ev.type === 'tool/call' || ev.type === 'tool/result';
    return true;
  });

  return (
    <div className="logs-panel" style={{ display: 'flex', height: '100vh', fontFamily: 'monospace' }}>
      <aside style={{ width: 280, borderRight: '1px solid #ddd', padding: 12, overflowY: 'auto' }}>
        <h3 style={{ marginTop: 0 }}>会话日志</h3>
        <button onClick={onBack} style={{ marginBottom: 8 }}>← 返回聊天</button>
        {sessions.length === 0 && !loading && <p>(暂无日志会话)</p>}
        {sessions.map((s) => (
          <div
            key={s.id}
            onClick={() => openSession(s.id)}
            style={{
              padding: 8,
              marginBottom: 4,
              cursor: 'pointer',
              border: selectedId === s.id ? '1px solid #1890ff' : '1px solid #eee',
              borderRadius: 4,
              wordBreak: 'break-all',
            }}
          >
            {s.id}
          </div>
        ))}
      </aside>
      <main style={{ flex: 1, padding: 12, overflowY: 'auto' }}>
        {!selectedId && <p>选择左侧会话查看事件流</p>}
        {error && <p style={{ color: 'red' }}>加载失败: {error}</p>}
        {selectedId && (
          <>
            <div style={{ marginBottom: 8 }}>
              {VIEWS.map((v) => (
                <button key={v} onClick={() => setView(v)} style={{ fontWeight: view === v ? 'bold' : 'normal', marginRight: 4 }}>
                  {v === 'events' ? '事件' : v === 'chat' ? '聊天' : '工具'}
                </button>
              ))}
              <span style={{ marginLeft: 8, color: '#888' }}>共 {filtered.length} 条</span>
            </div>
            {loading && <p>加载中…</p>}
            {filtered.length === 0 && !loading && <p>(空)</p>}
            {filtered.map((ev, i) => (
              <EventRow key={i} ev={ev} />
            ))}
          </>
        )}
      </main>
    </div>
  );
}

function EventRow({ ev }: { ev: LogEvent }) {
  const summary = summarize(ev);
  return (
    <div style={{ borderBottom: '1px solid #f0f0f0', padding: '4px 0' }}>
      <span style={{ color: '#888', marginRight: 8 }}>{String(ev.type)}</span>
      <span>{summary}</span>
    </div>
  );
}

function summarize(ev: LogEvent): string {
  if (ev.type === 'user/message' || ev.type === 'assistant/message') return String(ev.content ?? '');
  if (ev.type === 'tool/call') return `${String(ev.name ?? '')} args=${String(ev.arguments ?? '')}`;
  if (ev.type === 'tool/result') return `ok=${String(ev.isError)} ${String(ev.result ?? '')}`;
  if (ev.type === 'context/snapshot') return `turn=${String(ev.turn)} msgs=${String(ev.messageCount)} tools=${String(ev.toolNames ?? '')}`;
  return JSON.stringify(Object.entries(ev).filter(([k]) => k !== 'type' && k !== 'seq' && k !== 'timestamp').slice(0, 4));
}
