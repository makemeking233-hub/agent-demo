import ReactMarkdown from 'react-markdown';

export function MessageBubble(props: { role: 'user' | 'assistant'; text: string }) {
  const bg = props.role === 'user' ? '#dbeafe' : '#f3f4f6';
  const align = props.role === 'user' ? 'flex-end' : 'flex-start';
  return (
    <div style={{ display: 'flex', justifyContent: align, margin: '4px 0' }}>
      <div style={{ maxWidth: '80%', background: bg, padding: '8px 12px', borderRadius: 8 }}>
        {props.text ? (
          <ReactMarkdown>{props.text}</ReactMarkdown>
        ) : (
          <span style={{ color: '#888', fontStyle: 'italic' }}>…</span>
        )}
      </div>
    </div>
  );
}
