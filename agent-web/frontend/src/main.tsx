import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './index.css';
import './styles/tokens.css';
import './styles/tokens-dark.css';

/*
 * 主题初始化（shadcn-components-p2 C7）：
 * 原先这里调用 lib/theme.ts 的 initTheme()，它走 body[data-ds-dark-theme] 旧机制。
 * 现已收敛到 <html data-theme> 单一机制，由 App 内的 useThemeApplication()
 * 依据 settings store 的 general.appearance.preference 写入（system 时跟随
 * prefers-color-scheme）。lib/theme.ts 已删除。
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);