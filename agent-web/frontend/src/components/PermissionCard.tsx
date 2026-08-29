export function PermissionCard(props: {
  toolName: string;
  reason: string;
  choices: ('yes' | 'no' | 'always')[];
  onChoose: (decision: 'yes' | 'no' | 'always') => void;
}) {
  if (props.choices.length === 0) {
    return (
      <div style={{ background: '#f3f4f6', padding: 8, borderRadius: 6, margin: '4px 0', color: '#6b7280', fontStyle: 'italic' }}>
        ✓ {props.toolName} 权限已处理
      </div>
    );
  }
  return (
    <div style={{ background: '#fef3c7', border: '1px solid #f59e0b', borderRadius: 6, margin: '4px 0', padding: 12 }}>
      <div><strong>⚠️ 权限请求: {props.toolName}</strong></div>
      <div style={{ fontSize: 13, color: '#374151', margin: '4px 0' }}>{props.reason}</div>
      <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
        {props.choices.map((c) => (
          <button key={c} onClick={() => props.onChoose(c)} style={{ padding: '4px 12px' }}>{c}</button>
        ))}
      </div>
    </div>
  );
}
