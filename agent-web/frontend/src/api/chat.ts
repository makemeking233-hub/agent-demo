/**
 * 后端 HTTP API 客户端 (T7.3).
 * 配合 lib/sse-client.ts 跑流.
 */

export interface SendRequest {
  content: string;
  session_id?: string;
}

export interface SendResponse {
  stream_id: string;
  session_id: string;
  model: string;
}

export interface SlashResult {
  consumed: boolean;
  command?: string;
  output?: string;
  closeStream: boolean;
}

export interface CurrentSession {
  session_id: string | null;
  started_at?: number;
  turn_count?: number;
  tokens_in?: number;
  tokens_out?: number;
  model?: string;
}

export interface HistoryToolCall {
  id: string;
  name: string;
  argumentsJson?: string;
}

export interface HistoryMessage {
  role: string;
  content: string;
  toolCalls?: HistoryToolCall[];
  toolCallId?: string;
  isError?: boolean;
}

export interface HistoryResponse {
  session_id: string;
  messages: HistoryMessage[];
}

export interface SessionSummary {
  id: string;
  title: string;
  preview: string;
  workspace: string;
  time: number;
}

export class ChatApi {
  constructor(private base: string = '') {}

  async send(req: SendRequest): Promise<SendResponse> {
    const r = await fetch(this.base + '/api/chat/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    });
    if (!r.ok) {
      const body = await r.text();
      throw new Error(`send ${r.status}: ${body}`);
    }
    return (await r.json()) as SendResponse;
  }

  streamUrl(streamId: string): string {
    return this.base + `/api/chat/stream/${streamId}`;
  }

  async abort(streamId: string): Promise<void> {
    await fetch(this.base + `/api/chat/abort/${streamId}`, { method: 'POST' });
  }

  async submitDecision(streamId: string, permissionId: string, decision: 'yes' | 'no' | 'always'): Promise<boolean> {
    const r = await fetch(this.base + `/api/chat/decision/${streamId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ permission_id: permissionId, decision }),
    });
    if (r.status === 404) return false;
    if (!r.ok) throw new Error(`submitDecision ${r.status}`);
    const body = (await r.json()) as { ok: boolean };
    return body.ok;
  }

  async slash(streamId: string, content: string): Promise<SlashResult> {
    const r = await fetch(this.base + `/api/chat/slash/${streamId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    });
    if (r.status === 400) {
      return (await r.json()) as SlashResult;
    }
    if (!r.ok) throw new Error(`slash ${r.status}`);
    return (await r.json()) as SlashResult;
  }

  async currentSession(): Promise<CurrentSession> {
    const r = await fetch(this.base + '/api/sessions/current');
    if (!r.ok) throw new Error(`currentSession ${r.status}`);
    return (await r.json()) as CurrentSession;
  }

  /**
   * 拉取某会话的消息历史（v0.3 会话重进恢复）。
   * @param sessionId 会话 id
   * @returns 会话消息；未知会话 / 无存档时 messages 为空或抛错
   */
  async history(sessionId: string): Promise<HistoryResponse> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}/messages`);
    if (r.status === 404) return { session_id: sessionId, messages: [] };
    if (!r.ok) throw new Error(`history ${r.status}`);
    return (await r.json()) as HistoryResponse;
  }

  // 列出现实会话（add-session-switch）
  async listSessions(): Promise<SessionSummary[]> {
    const r = await fetch(this.base + '/api/sessions');
    if (!r.ok) throw new Error(`listSessions ${r.status}`);
    return (await r.json()) as SessionSummary[];
  }

  // 列出归档会话（add-session-management）
  async listArchived(): Promise<SessionSummary[]> {
    const r = await fetch(this.base + '/api/sessions?archived=true');
    if (!r.ok) throw new Error(`listArchived ${r.status}`);
    return (await r.json()) as SessionSummary[];
  }

  // 归档（软删除）会话（add-session-management）
  async archiveSession(sessionId: string): Promise<boolean> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });
    if (r.status === 404) return false;
    if (!r.ok) throw new Error(`archiveSession ${r.status}`);
    return true;
  }

  // 恢复归档会话（add-session-management）
  async restoreSession(sessionId: string): Promise<boolean> {
    const r = await fetch(this.base + `/api/sessions/${encodeURIComponent(sessionId)}/restore`, { method: 'POST' });
    if (r.status === 404) return false;
    if (!r.ok) throw new Error(`restoreSession ${r.status}`);
    return true;
  }

  async health(): Promise<{ status: string; version: string; uptime_s: number }> {
    const r = await fetch(this.base + '/api/health');
    if (!r.ok) throw new Error(`health ${r.status}`);
    return await r.json();
  }
}
