import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LogsPanel } from './LogsPanel';

const sessions = [
  { id: '2026-08-29T10-23-45-abc12345', hasEvents: true, hasChat: true, hasTools: true },
];

const events = [
  { type: 'turn/start', seq: 0 },
  { type: 'context/snapshot', seq: 1, turn: 0, messageCount: 1, toolNames: ['ReadFile'] },
  { type: 'user/message', seq: 2, content: '你好' },
  { type: 'assistant/message', seq: 3, content: '你好！' },
  { type: 'tool/call', seq: 4, name: 'ReadFile', arguments: '{"path":"a.txt"}' },
  { type: 'tool/result', seq: 5, isError: false, result: '文件内容' },
  { type: 'turn/end', seq: 6 },
];

describe('LogsPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  it('加载并渲染会话列表，点击后显示事件流', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ json: async () => sessions })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ events, total: events.length }) });

    render(<LogsPanel onBack={() => {}} />);

    await waitFor(() => expect(screen.getByText('2026-08-29T10-23-45-abc12345')).toBeTruthy());
    fireEvent.click(screen.getByText('2026-08-29T10-23-45-abc12345'));
    await waitFor(() => expect(screen.getByText('共 7 条')).toBeTruthy());
  });

  it('工具视图只显示工具事件', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ json: async () => sessions })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ events, total: events.length }) });

    render(<LogsPanel onBack={() => {}} />);
    await waitFor(() => screen.getByText('2026-08-29T10-23-45-abc12345'));
    fireEvent.click(screen.getByText('2026-08-29T10-23-45-abc12345'));
    await waitFor(() => screen.getByText('共 7 条'));

    fireEvent.click(screen.getByText('工具'));
    expect(screen.getByText('共 2 条')).toBeTruthy();
    expect(screen.getByText(/tool\/call/)).toBeTruthy();
    expect(screen.getByText(/tool\/result/)).toBeTruthy();
    expect(screen.queryByText(/user\/message/)).toBeNull();
  });

  it('空会话显示空态', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ json: async () => sessions })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ events: [], total: 0 }) });

    render(<LogsPanel onBack={() => {}} />);
    await waitFor(() => screen.getByText('2026-08-29T10-23-45-abc12345'));
    fireEvent.click(screen.getByText('2026-08-29T10-23-45-abc12345'));
    await waitFor(() => expect(screen.getByText('(空)')).toBeTruthy());
  });
});
