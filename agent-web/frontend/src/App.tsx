import { useEffect, useState } from 'react';

/**
 * T1.4 占位骨架: 健康检查 ping, 验证 build pipeline + 与后端联通.
 * T7 起会被 App.tsx (router + 三栏布局) 取代.
 */
export function App() {
  const [health, setHealth] = useState<string>('checking...');

  useEffect(() => {
    fetch('/api/health')
      .then((r) => r.json())
      .then((d) => setHealth(`${d.status} (v${d.version})`))
      .catch((e) => setHealth(`error: ${e.message}`));
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: 24, fontFamily: 'system-ui' }}>
      <h1>agent-demo v0.1 web</h1>
      <p>/api/health → <code>{health}</code></p>
      <p style={{ color: '#888' }}>T1.4 placeholder · 真实 UI 见 tasks.md T7+</p>
    </div>
  );
}