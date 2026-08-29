export function ToolCallCard(props: { name: string; status: 'running' | 'ok' | 'fail'; text?: string; durationMs?: number }) {
  const color = props.status === 'running' ? '#3b82f6' : props.status === 'ok' ? '#10b981' : '#ef4444';
  const label = props.status === 'running' ? '执行中' : props.status === 'ok' ? '完成' : '失败';
  return (
    <div style={{ border: `1px solid ${color}`, borderRadius: 6, margin: '4px 0', padding: 8, background: '#fafafa' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <strong style={{ color }}>🔧 {props.name}</strong>
        <span style={{ color, fontSize: 12 }}>{label}{props.durationMs != null ? ` (${props.durationMs}ms)` : ''}</span>
      </div>
      {props.text && (
        <pre style={{ margin: '4px 0 0', fontSize: 12, whiteSpace: 'pre-wrap', color: '#374151' }}>{props.text}</pre>
      )}
    </div>
  );
}
