import { useEffect, useState } from 'react';
import { ChatPanel } from './components/ChatPanel';
import { LogsPanel } from './components/LogsPanel';

/** 极简 hash 路由：#/logs → 日志面板；其余 → 聊天面板。 */
export function App() {
  const [route, setRoute] = useState(window.location.hash);

  useEffect(() => {
    const onHash = () => setRoute(window.location.hash);
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  if (route.startsWith('#/logs')) {
    return <LogsPanel onBack={() => { window.location.hash = ''; }} />;
  }
  return <ChatPanel />;
}
